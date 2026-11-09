package com.example.repository

import com.example.model.SocialPost
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

interface ISocialFeedRepository {
    fun observeFeed(): Flow<List<SocialPost>>
    suspend fun createPost(caption: String, mediaUrls: List<String> = emptyList()): Result<String>
    suspend fun toggleLike(postId: String, currentLikes: Long, liked: Boolean): Result<Unit>
    suspend fun followUser(targetUid: String): Result<Unit>
    suspend fun unfollowUser(targetUid: String): Result<Unit>
}

class SocialFeedRepository(private val db: FirebaseFirestore) : ISocialFeedRepository {

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing feed.")
    }

    override fun observeFeed(): Flow<List<SocialPost>> = callbackFlow {
        val uid = requireUserId()
        val path = "posts"
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
                val posts = snapshot.toObjects(SocialPost::class.java)
                trySend(posts)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun createPost(caption: String, mediaUrls: List<String>): Result<String> {
        val uid = requireUserId()
        val postId = UUID.randomUUID().toString()
        val postRef = db.collection("posts").document(postId)

        val authorName = auth.currentUser?.displayName ?: "User"
        val payload = mapOf(
            "postId" to postId,
            "authorUid" to uid,
            "authorUsername" to authorName,
            "authorAvatarUrl" to (auth.currentUser?.photoUrl?.toString() ?: ""),
            "caption" to caption,
            "mediaUrls" to mediaUrls,
            "likesCount" to 0L,
            "commentsCount" to 0L,
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

    override suspend fun toggleLike(postId: String, currentLikes: Long, liked: Boolean): Result<Unit> {
        val postRef = db.collection("posts").document(postId)
        val delta = if (liked) 1L else -1L

        return try {
            postRef.update("likesCount", FieldValue.increment(delta)).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, postRef.path)
            Result.failure(e)
        }
    }

    override suspend fun followUser(targetUid: String): Result<Unit> {
        val uid = requireUserId()
        val followId = "${uid}_${targetUid}"
        val followRef = db.collection("follows").document(followId)

        val payload = mapOf(
            "followerUid" to uid,
            "targetUid" to targetUid,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            followRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, followRef.path)
            Result.failure(e)
        }
    }

    override suspend fun unfollowUser(targetUid: String): Result<Unit> {
        val uid = requireUserId()
        val followId = "${uid}_${targetUid}"
        val followRef = db.collection("follows").document(followId)

        return try {
            followRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, followRef.path)
            Result.failure(e)
        }
    }
}
