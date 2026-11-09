package com.example.repository

import com.example.model.CommunityPost
import com.example.model.CreatorCommunity
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

interface ICommunityRepository {
    fun observeCommunities(): Flow<List<CreatorCommunity>>
    fun observeCommunityPosts(communityId: String): Flow<List<CommunityPost>>
    suspend fun createCommunity(name: String, description: String, iconUrl: String = ""): Result<String>
    suspend fun joinCommunity(communityId: String): Result<Unit>
    suspend fun leaveCommunity(communityId: String): Result<Unit>
    suspend fun createCommunityPost(communityId: String, content: String): Result<String>
}

class CommunityRepository(private val db: FirebaseFirestore) : ICommunityRepository {

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing communities.")
    }

    override fun observeCommunities(): Flow<List<CreatorCommunity>> = callbackFlow {
        val uid = requireUserId()
        val path = "communities"
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
                val communities = snapshot.toObjects(CreatorCommunity::class.java)
                trySend(communities)
            }
        }
        awaitClose { registration.remove() }
    }

    override fun observeCommunityPosts(communityId: String): Flow<List<CommunityPost>> = callbackFlow {
        val uid = requireUserId()
        val path = "communities/$communityId/posts"
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
                val posts = snapshot.toObjects(CommunityPost::class.java)
                trySend(posts)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun createCommunity(name: String, description: String, iconUrl: String): Result<String> {
        val uid = requireUserId()
        val communityId = UUID.randomUUID().toString()
        val communityRef = db.collection("communities").document(communityId)

        val payload = mapOf(
            "communityId" to communityId,
            "creatorUid" to uid,
            "name" to name,
            "description" to description,
            "iconUrl" to iconUrl,
            "membersCount" to 1L,
            "isPrivate" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            communityRef.set(payload).await()
            // Add creator as Admin member
            val memberRef = communityRef.collection("members").document(uid)
            memberRef.set(
                mapOf(
                    "userId" to uid,
                    "role" to "ADMIN",
                    "isBanned" to false,
                    "joinedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(communityId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, communityRef.path)
            Result.failure(e)
        }
    }

    override suspend fun joinCommunity(communityId: String): Result<Unit> {
        val uid = requireUserId()
        val memberRef = db.collection("communities")
            .document(communityId)
            .collection("members")
            .document(uid)

        val payload = mapOf(
            "userId" to uid,
            "role" to "MEMBER",
            "isBanned" to false,
            "joinedAt" to FieldValue.serverTimestamp()
        )

        return try {
            memberRef.set(payload).await()
            db.collection("communities").document(communityId)
                .update("membersCount", FieldValue.increment(1))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, memberRef.path)
            Result.failure(e)
        }
    }

    override suspend fun leaveCommunity(communityId: String): Result<Unit> {
        val uid = requireUserId()
        val memberRef = db.collection("communities")
            .document(communityId)
            .collection("members")
            .document(uid)

        return try {
            memberRef.delete().await()
            db.collection("communities").document(communityId)
                .update("membersCount", FieldValue.increment(-1))
                .await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, memberRef.path)
            Result.failure(e)
        }
    }

    override suspend fun createCommunityPost(communityId: String, content: String): Result<String> {
        val uid = requireUserId()
        val postId = UUID.randomUUID().toString()
        val postRef = db.collection("communities")
            .document(communityId)
            .collection("posts")
            .document(postId)

        val authorName = auth.currentUser?.displayName ?: "Member"
        val payload = mapOf(
            "postId" to postId,
            "communityId" to communityId,
            "authorUid" to uid,
            "authorUsername" to authorName,
            "content" to content,
            "mediaUrls" to emptyList<String>(),
            "likesCount" to 0L,
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
