package com.example.repository

import com.example.model.BroadcastChannel
import com.example.model.ChannelBroadcastPost
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.util.UUID

interface IChannelRepository {
    fun observeChannels(): Flow<List<BroadcastChannel>>
    fun observeChannelPosts(channelId: String): Flow<List<ChannelBroadcastPost>>
    suspend fun createChannel(name: String, description: String, iconUrl: String = ""): Result<String>
    suspend fun subscribeChannel(channelId: String): Result<Unit>
    suspend fun unsubscribeChannel(channelId: String): Result<Unit>
    suspend fun publishChannelPost(channelId: String, content: String): Result<String>
}

class ChannelRepository(private val db: FirebaseFirestore) : IChannelRepository {

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing channels.")
    }

    override fun observeChannels(): Flow<List<BroadcastChannel>> = callbackFlow {
        val uid = requireUserId()
        val path = "channels"
        val query = db.collection(path).limit(50)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val channels = snapshot.toObjects(BroadcastChannel::class.java)
                trySend(channels)
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeChannelPosts(channelId: String): Flow<List<ChannelBroadcastPost>> = callbackFlow {
        val uid = requireUserId()
        val path = "channels/$channelId/posts"
        val query = db.collection(path)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(50)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val posts = snapshot.toObjects(ChannelBroadcastPost::class.java)
                trySend(posts)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun createChannel(name: String, description: String, iconUrl: String): Result<String> {
        val uid = requireUserId()
        val channelId = UUID.randomUUID().toString()
        val channelRef = db.collection("channels").document(channelId)

        val payload = mapOf(
            "channelId" to channelId,
            "ownerUid" to uid,
            "name" to name,
            "description" to description,
            "iconUrl" to iconUrl,
            "subscribersCount" to 1L,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            channelRef.set(payload).await()
            val subRef = channelRef.collection("subscribers").document(uid)
            subRef.set(
                mapOf(
                    "userId" to uid,
                    "subscribedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(channelId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, channelRef.path)
            Result.failure(e)
        }
    }

    override suspend fun subscribeChannel(channelId: String): Result<Unit> {
        val uid = requireUserId()
        val subRef = db.collection("channels")
            .document(channelId)
            .collection("subscribers")
            .document(uid)

        val payload = mapOf(
            "userId" to uid,
            "subscribedAt" to FieldValue.serverTimestamp()
        )

        return try {
            subRef.set(payload).await()
            db.collection("channels").document(channelId)
                .update("subscribersCount", FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, subRef.path)
            Result.failure(e)
        }
    }

    override suspend fun unsubscribeChannel(channelId: String): Result<Unit> {
        val uid = requireUserId()
        val subRef = db.collection("channels")
            .document(channelId)
            .collection("subscribers")
            .document(uid)

        return try {
            subRef.delete().await()
            db.collection("channels").document(channelId)
                .update("subscribersCount", FieldValue.increment(-1))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, subRef.path)
            Result.failure(e)
        }
    }

    override suspend fun publishChannelPost(channelId: String, content: String): Result<String> {
        val uid = requireUserId()
        val postId = UUID.randomUUID().toString()
        val postRef = db.collection("channels")
            .document(channelId)
            .collection("posts")
            .document(postId)

        val payload = mapOf(
            "postId" to postId,
            "channelId" to channelId,
            "authorUid" to uid,
            "content" to content,
            "mediaUrls" to emptyList<String>(),
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            postRef.set(payload).await()
            Result.success(postId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, postRef.path)
            Result.failure(e)
        }
    }
}
