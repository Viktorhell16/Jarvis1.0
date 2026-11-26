package com.example.firebase

import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import com.example.R
import com.example.data.local.ArtifactEntity
import com.example.data.local.ChatEntity
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val value: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    LIST("list"),
    GET("get"),
    WRITE("write"),
}

fun handleFirestoreError(exception: Throwable, operationType: OperationType, path: String?): String {
    val causes = generateSequence(exception) { it.cause }
    val rootError = causes.firstOrNull { it is FirebaseFirestoreException }
        ?: causes.firstOrNull { it !is CancellationException }
        ?: exception

    val auth = FirebaseAuth.getInstance()
    val currentUser = auth.currentUser

    val providerInfoList = currentUser?.providerData?.map { provider ->
        JSONObject().apply {
            put("providerId", provider.providerId)
            put("email", provider.email)
        }
    } ?: emptyList()

    val authInfoJson = JSONObject().apply {
        put("userId", currentUser?.uid)
        put("email", currentUser?.email)
        put("emailVerified", currentUser?.isEmailVerified)
        put("tenantId", currentUser?.tenantId)
        put("providerInfo", JSONArray(providerInfoList))
    }

    val errorInfoJson = JSONObject().apply {
        put("error", rootError.message ?: rootError.toString())
        put("operationType", operationType.value)
        put("path", path)
        put("authInfo", authInfoJson)
    }

    val jsonString = errorInfoJson.toString()
    Log.e("FirestoreError", "Firestore Error: $jsonString")
    return jsonString
}

@Keep
@IgnoreExtraProperties
data class UserProfileDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val nickname: String = "",
    val languageCode: String = "ru",
    val paletteId: String = "neon_sapphire",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudNoteDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val title: String = "",
    val artifactType: String = "PAGE",
    val category: String = "All",
    val content: String = "",
    val pinned: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class CloudChatDoc(
    @DocumentId val docId: String = "",
    val userId: String = "",
    val title: String = "",
    val pinned: Boolean = false,
    val lastMessagePreview: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

private val ALLOWED_ARTIFACT_TYPES = setOf("FILE", "IMAGE", "PAGE", "DOCUMENT", "PRESENTATION", "BOOK")
private val ALLOWED_LANGUAGES = setOf("ru", "uk", "en")

class FirestoreSyncRepository(
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

    fun observeCloudNotes(userId: String): Flow<List<CloudNoteDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for observeCloudNotes" }
        val path = "users/$uid/notes"
        emitAll(
            db.collection("users").document(uid).collection("notes")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudNoteDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudChats(userId: String): Flow<List<CloudChatDoc>> = flow {
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user mismatch for observeCloudChats" }
        val path = "users/$uid/chats"
        emitAll(
            db.collection("users").document(uid).collection("chats")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudChatDoc::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun syncUserProfile(
        nickname: String,
        languageCode: String,
        paletteId: String
    ): Result<Unit> {
        var docPath = "users/unauthenticated"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid"
            val cleanNick = nickname.trim().ifBlank { "JARVIS User" }.take(60)
            val safeLang = if (languageCode in ALLOWED_LANGUAGES) languageCode else "ru"
            val safePalette = paletteId.trim().ifBlank { "neon_sapphire" }.take(50)

            val docRef = db.collection("users").document(uid)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "nickname" to cleanNick,
                        "languageCode" to safeLang,
                        "paletteId" to safePalette,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "nickname" to cleanNick,
                        "languageCode" to safeLang,
                        "paletteId" to safePalette,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertCloudNote(artifact: ArtifactEntity): Result<Unit> {
        val docId = "note_${artifact.id}"
        var docPath = "users/unauthenticated/notes/$docId"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid/notes/$docId"
            val cleanTitle = artifact.title.trim().ifBlank { "Заметка" }.take(160)
            val safeType = if (artifact.type in ALLOWED_ARTIFACT_TYPES) artifact.type else "PAGE"
            val safeCategory = artifact.category.trim().ifBlank { "All" }.take(40)
            val safeContent = artifact.contentOrUri.take(20000)

            val docRef = db.collection("users").document(uid).collection("notes").document(docId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "artifactType" to safeType,
                        "category" to safeCategory,
                        "content" to safeContent,
                        "pinned" to artifact.isPinned,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to cleanTitle,
                        "artifactType" to safeType,
                        "category" to safeCategory,
                        "content" to safeContent,
                        "pinned" to artifact.isPinned,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudNote(localNoteId: Long): Result<Unit> {
        val docId = "note_$localNoteId"
        var docPath = "users/unauthenticated/notes/$docId"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid/notes/$docId"
            db.collection("users").document(uid).collection("notes").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun upsertCloudChat(chat: ChatEntity, lastMessagePreview: String): Result<Unit> {
        val docId = "chat_${chat.id}"
        var docPath = "users/unauthenticated/chats/$docId"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid/chats/$docId"
            val cleanTitle = chat.title.trim().ifBlank { "Новый чат" }.take(160)
            val safePreview = lastMessagePreview.replace("\n", " ").take(500)

            val docRef = db.collection("users").document(uid).collection("chats").document(docId)
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(
                    mapOf(
                        "title" to cleanTitle,
                        "pinned" to chat.isPinned,
                        "lastMessagePreview" to safePreview,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                docRef.set(
                    mapOf(
                        "userId" to uid,
                        "title" to cleanTitle,
                        "pinned" to chat.isPinned,
                        "lastMessagePreview" to safePreview,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteCloudChat(localChatId: Long): Result<Unit> {
        val docId = "chat_$localChatId"
        var docPath = "users/unauthenticated/chats/$docId"
        return try {
            val uid = requireUserId()
            docPath = "users/$uid/chats/$docId"
            db.collection("users").document(uid).collection("chats").document(docId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
