package com.example.repository

import com.example.model.UserBlock
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
import java.util.UUID

interface ISafetyRepository {
    fun observeBlockedUsers(): Flow<List<UserBlock>>
    suspend fun blockUser(targetUid: String): Result<Unit>
    suspend fun unblockUser(targetUid: String): Result<Unit>
    suspend fun reportContent(targetType: String, targetId: String, reason: String): Result<String>
}

class SafetyRepository(private val db: FirebaseFirestore) : ISafetyRepository {

    private val auth = Firebase.auth

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing safety actions.")
    }

    override fun observeBlockedUsers(): Flow<List<UserBlock>> = callbackFlow {
        val uid = requireUserId()
        val path = "blocks"
        val query = db.collection(path).whereEqualTo("blockerUid", uid)

        val registration = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                if (auth.currentUser?.uid == uid) {
                    handleFirestoreError(error, OperationType.LIST, path)
                }
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null) {
                val blocks = snapshot.toObjects(UserBlock::class.java)
                trySend(blocks)
            }
        }
        awaitClose { registration.remove() }
    }

    override suspend fun blockUser(targetUid: String): Result<Unit> {
        val uid = requireUserId()
        val blockId = "${uid}_${targetUid}"
        val blockRef = db.collection("blocks").document(blockId)

        val payload = mapOf(
            "blockerUid" to uid,
            "blockedUid" to targetUid,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            blockRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, blockRef.path)
            Result.failure(e)
        }
    }

    override suspend fun unblockUser(targetUid: String): Result<Unit> {
        val uid = requireUserId()
        val blockId = "${uid}_${targetUid}"
        val blockRef = db.collection("blocks").document(blockId)

        return try {
            blockRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, blockRef.path)
            Result.failure(e)
        }
    }

    override suspend fun reportContent(targetType: String, targetId: String, reason: String): Result<String> {
        val uid = requireUserId()
        val reportId = UUID.randomUUID().toString()
        val reportRef = db.collection("reports").document(reportId)

        val payload = mapOf(
            "reportId" to reportId,
            "reporterUid" to uid,
            "targetType" to targetType,
            "targetId" to targetId,
            "reason" to reason,
            "createdAt" to FieldValue.serverTimestamp()
        )

        return try {
            reportRef.set(payload).await()
            Result.success(reportId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, reportRef.path)
            Result.failure(e)
        }
    }
}
