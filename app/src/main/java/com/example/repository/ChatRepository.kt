package com.example.repository

import com.example.model.ChatMessage
import com.example.model.Conversation
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface IChatRepository {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<ChatMessage>>
    suspend fun sendMessage(conversationId: String, content: String, mediaUrl: String = "", mediaType: String = "NONE"): Result<String>
    suspend fun createDirectConversation(targetUid: String, targetName: String): Result<String>
    suspend fun createGroupConversation(title: String, memberUids: List<String>): Result<String>
    suspend fun markMessageRead(conversationId: String, messageId: String): Result<Unit>
}

class ChatRepository(private val db: FirebaseFirestore) : IChatRepository {

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing chat.")
    }

    override fun observeConversations(): Flow<List<Conversation>> = callbackFlow {
        val uid = requireUserId()
        val path = "conversations"
        val query = db.collection(path)
            .whereArrayContains("memberUids", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val conversations = snapshot.toObjects(Conversation::class.java)
                    .sortedByDescending { it.lastMessageTimestamp?.seconds ?: it.createdAt?.seconds ?: 0L }
                trySend(conversations)
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeMessages(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val uid = requireUserId()
        val path = "conversations/$conversationId/messages"
        val query = db.collection(path)
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .limit(100)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val messages = snapshot.toObjects(ChatMessage::class.java)
                trySend(messages)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        mediaUrl: String,
        mediaType: String
    ): Result<String> {
        val uid = requireUserId()
        val messageId = UUID.randomUUID().toString()
        val messageRef = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(messageId)

        val messagePayload = mapOf(
            "messageId" to messageId,
            "conversationId" to conversationId,
            "senderUid" to uid,
            "content" to content,
            "mediaUrl" to mediaUrl,
            "mediaType" to mediaType,
            "status" to "SENT",
            "readBy" to listOf(uid),
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            // Write message document
            messageRef.set(messagePayload).await()

            // Update conversation lastMessage denormalized summary
            val convoRef = db.collection("conversations").document(conversationId)
            convoRef.update(
                mapOf(
                    "lastMessageText" to content,
                    "lastMessageSenderId" to uid,
                    "lastMessageTimestamp" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(messageId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, messageRef.path)
            Result.failure(e)
        }
    }

    override suspend fun createDirectConversation(targetUid: String, targetName: String): Result<String> {
        val uid = requireUserId()
        val memberUids = listOf(uid, targetUid).sorted()
        val convoId = "${memberUids[0]}_${memberUids[1]}"
        val convoRef = db.collection("conversations").document(convoId)

        return try {
            val payload = mapOf(
                "conversationId" to convoId,
                "type" to "DIRECT",
                "memberUids" to memberUids,
                "adminUids" to listOf(uid),
                "title" to targetName,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convoRef.set(payload).await()
            Result.success(convoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convoRef.path)
            Result.failure(e)
        }
    }

    override suspend fun createGroupConversation(title: String, memberUids: List<String>): Result<String> {
        val uid = requireUserId()
        val allMembers = (memberUids + uid).distinct()
        val convoId = "group_${UUID.randomUUID()}"
        val convoRef = db.collection("conversations").document(convoId)

        return try {
            val payload = mapOf(
                "conversationId" to convoId,
                "type" to "GROUP",
                "memberUids" to allMembers,
                "adminUids" to listOf(uid),
                "title" to title,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            convoRef.set(payload).await()
            Result.success(convoId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, convoRef.path)
            Result.failure(e)
        }
    }

    override suspend fun markMessageRead(conversationId: String, messageId: String): Result<Unit> {
        val uid = requireUserId()
        val messageRef = db.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(messageId)

        return try {
            messageRef.update(
                mapOf(
                    "status" to "READ",
                    "readBy" to FieldValue.arrayUnion(uid)
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, messageRef.path)
            Result.failure(e)
        }
    }
}
