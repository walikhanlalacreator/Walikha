package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.model.SignalRecord
import com.example.data.model.UserAlert
import com.example.data.model.UserProfile
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun saveUserProfile(profile: UserProfile): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val payload = profile.toMap().toMutableMap()
            payload["userId"] = uid
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeUserProfile(): Flow<UserProfile?> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(null)
            close()
            return@callbackFlow
        }
        val docRef = db.collection("users").document(uid)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.GET, docRef.path)
                close(error)
                return@addSnapshotListener
            }
            if (snapshot != null && snapshot.exists()) {
                val profile = snapshot.toObject(UserProfile::class.java)
                trySend(profile)
            } else {
                trySend(null)
            }
        }
        awaitClose { listener.remove() }
    }

    suspend fun saveSignal(record: SignalRecord): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("signals")
        return try {
            val payload = record.toMap().toMutableMap()
            payload["userId"] = uid
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["updatedAt"] = FieldValue.serverTimestamp()
            val doc = colRef.add(payload).await()
            Result.success(doc.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    fun observeSignals(): Flow<List<SignalRecord>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val colRef = db.collection("users").document(uid).collection("signals")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val listener = colRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$uid/signals")
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.toObject(SignalRecord::class.java) } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }

    suspend fun updateSignalOutcome(signalId: String, outcome: String, notes: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("signals").document(signalId)
        return try {
            docRef.update(
                mapOf(
                    "outcome" to outcome,
                    "journalNotes" to notes,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun saveAlert(alert: UserAlert): Result<String> {
        val uid = requireUserId()
        val colRef = db.collection("users").document(uid).collection("alerts")
        return try {
            val payload = alert.toMap().toMutableMap()
            payload["userId"] = uid
            payload["createdAt"] = FieldValue.serverTimestamp()
            val doc = colRef.add(payload).await()
            Result.success(doc.id)
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, colRef.path)
            Result.failure(e)
        }
    }

    fun observeAlerts(): Flow<List<UserAlert>> = callbackFlow {
        val uid = auth.currentUser?.uid
        if (uid == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }
        val colRef = db.collection("users").document(uid).collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING)

        val listener = colRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                handleFirestoreError(error, OperationType.LIST, "users/$uid/alerts")
                close(error)
                return@addSnapshotListener
            }
            val list = snapshot?.documents?.mapNotNull { it.toObject(UserAlert::class.java) } ?: emptyList()
            trySend(list)
        }
        awaitClose { listener.remove() }
    }
}
