package com.example.repository

import com.example.model.UserProfile
import com.example.util.OperationType
import com.example.util.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

interface IAuthRepository {
    fun observeCurrentUserProfile(): Flow<UserProfile?>
    suspend fun checkUsernameAvailable(username: String): Boolean
    suspend fun saveUserProfile(username: String, displayName: String, bio: String, avatarUrl: String = ""): Result<Unit>
}

class AuthRepository(private val db: FirebaseFirestore) : IAuthRepository {

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing profile.")
    }

    override fun observeCurrentUserProfile(): Flow<UserProfile?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }

        val docRef = db.collection("users").document(uid)
        val registration = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.GET, docRef.path)
                }
                close(error)
                return@addSnapshotListener
            }
            val profile = snapshot?.toObject(UserProfile::class.java)
            trySend(profile)
        }
        awaitClose { registration.remove() }
    }

    override suspend fun checkUsernameAvailable(username: String): Boolean {
        val clean = username.trim().lowercase()
        return try {
            val doc = db.collection("usernames").document(clean).get().await()
            !doc.exists()
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun saveUserProfile(
        username: String,
        displayName: String,
        bio: String,
        avatarUrl: String
    ): Result<Unit> {
        val uid = requireUserId()
        val cleanUsername = username.trim().lowercase()
        val userRef = db.collection("users").document(uid)
        val usernameRef = db.collection("usernames").document(cleanUsername)

        val userPayload = mapOf(
            "uid" to uid,
            "username" to cleanUsername,
            "displayName" to displayName,
            "bio" to bio,
            "avatarUrl" to avatarUrl,
            "followersCount" to 0L,
            "followingCount" to 0L,
            "postsCount" to 0L,
            "isPrivate" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
        )

        val usernamePayload = mapOf(
            "uid" to uid,
            "claimedAt" to FieldValue.serverTimestamp()
        )

        return try {
            // Reserve username
            usernameRef.set(usernamePayload).await()
            // Set user profile
            userRef.set(userPayload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userRef.path)
            Result.failure(e)
        }
    }
}
