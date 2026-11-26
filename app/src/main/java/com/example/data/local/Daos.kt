package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalUserDao {
    @Query("SELECT * FROM local_users WHERE id = :id")
    fun observeUser(id: Long): Flow<LocalUserEntity?>

    @Query("SELECT * FROM local_users WHERE id = :id")
    suspend fun getUserById(id: Long): LocalUserEntity?

    @Query("SELECT * FROM local_users WHERE LOWER(nickname) = LOWER(:nickname) LIMIT 1")
    suspend fun getUserByNickname(nickname: String): LocalUserEntity?

    @Query("SELECT * FROM local_users ORDER BY createdAt DESC")
    fun observeAllUsers(): Flow<List<LocalUserEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: LocalUserEntity): Long

    @Update
    suspend fun updateUser(user: LocalUserEntity)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chats ORDER BY isPinned DESC, updatedAt DESC")
    fun observeChats(): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE title LIKE '%' || :query || '%' ORDER BY isPinned DESC, updatedAt DESC")
    fun searchChats(query: String): Flow<List<ChatEntity>>

    @Query("SELECT * FROM chats WHERE id = :chatId")
    suspend fun getChatById(chatId: Long): ChatEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: ChatEntity): Long

    @Update
    suspend fun updateChat(chat: ChatEntity)

    @Query("DELETE FROM chats WHERE id = :chatId")
    suspend fun deleteChat(chatId: Long)

    @Query("DELETE FROM chats")
    suspend fun clearAllChats()
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC, id ASC")
    fun observeMessagesForChat(chatId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC, id ASC")
    suspend fun getMessagesForChatOnce(chatId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 50")
    fun searchMessages(query: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    @Update
    suspend fun updateMessage(message: MessageEntity)

    @Query("UPDATE messages SET content = :content, modelTag = :modelTag, providerName = :providerName WHERE id = :id")
    suspend fun updateMessageContent(id: Long, content: String, modelTag: String, providerName: String)

    @Query("UPDATE messages SET actionStatus = :status WHERE id = :id")
    suspend fun updateActionStatus(id: Long, status: String)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessageById(id: Long)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteMessagesForChat(chatId: Long)
}

@Dao
interface ArtifactDao {
    @Query("SELECT * FROM artifacts ORDER BY isPinned DESC, createdAt DESC")
    fun observeArtifacts(): Flow<List<ArtifactEntity>>

    @Query("SELECT * FROM artifacts WHERE title LIKE '%' || :query || '%' OR previewSummary LIKE '%' || :query || '%' OR tagsCsv LIKE '%' || :query || '%' ORDER BY isPinned DESC, createdAt DESC")
    fun searchArtifacts(query: String): Flow<List<ArtifactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: ArtifactEntity): Long

    @Update
    suspend fun updateArtifact(artifact: ArtifactEntity)

    @Query("DELETE FROM artifacts WHERE id = :id")
    suspend fun deleteArtifact(id: Long)
}

@Dao
interface PluginDao {
    @Query("SELECT * FROM plugins ORDER BY isBuiltIn DESC, name ASC")
    fun observePlugins(): Flow<List<PluginEntity>>

    @Query("SELECT * FROM plugins WHERE isConnected = 1 AND isEnabled = 1")
    suspend fun getActivePluginsOnce(): List<PluginEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugin(plugin: PluginEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(plugins: List<PluginEntity>)

    @Update
    suspend fun updatePlugin(plugin: PluginEntity)

    @Query("DELETE FROM plugins WHERE id = :id")
    suspend fun deletePlugin(id: Long)

    @Query("SELECT COUNT(*) FROM plugins")
    suspend fun count(): Int
}

@Dao
interface TokenUsageDao {
    @Query("SELECT * FROM token_usage ORDER BY timestamp DESC")
    fun observeAllUsage(): Flow<List<TokenUsageEntity>>

    @Query("SELECT * FROM token_usage WHERE dateKey = :dateKey ORDER BY timestamp DESC")
    fun observeUsageForDay(dateKey: String): Flow<List<TokenUsageEntity>>

    @Query("SELECT COALESCE(SUM(promptTokens + completionTokens), 0) FROM token_usage WHERE dateKey = :dateKey")
    suspend fun getDailyTokensOnce(dateKey: String): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsage(usage: TokenUsageEntity): Long

    @Query("DELETE FROM token_usage")
    suspend fun clearAllUsage()
}

@Dao
interface GemModeDao {
    @Query("SELECT * FROM gem_modes ORDER BY isBuiltIn DESC, id ASC")
    fun observeGemModes(): Flow<List<GemModeEntity>>

    @Query("SELECT * FROM gem_modes WHERE id = :id")
    suspend fun getModeById(id: Long): GemModeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMode(mode: GemModeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(modes: List<GemModeEntity>)

    @Update
    suspend fun updateMode(mode: GemModeEntity)

    @Query("DELETE FROM gem_modes WHERE id = :id AND isBuiltIn = 0")
    suspend fun deleteCustomMode(id: Long)

    @Query("SELECT COUNT(*) FROM gem_modes")
    suspend fun count(): Int
}

@Dao
interface ProviderSlotDao {
    @Query("SELECT * FROM provider_slots ORDER BY priorityOrder ASC")
    fun observeSlots(): Flow<List<ProviderSlotEntity>>

    @Query("SELECT * FROM provider_slots ORDER BY priorityOrder ASC")
    suspend fun getSlotsOnce(): List<ProviderSlotEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSlot(slot: ProviderSlotEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(slots: List<ProviderSlotEntity>)

    @Update
    suspend fun updateSlot(slot: ProviderSlotEntity)

    @Query("UPDATE provider_slots SET cooldownUntilMs = :untilMs, lastError = :error WHERE id = :id")
    suspend fun setCooldown(id: Long, untilMs: Long, error: String?)

    @Query("UPDATE provider_slots SET cooldownUntilMs = 0, lastError = NULL")
    suspend fun resetAllCooldowns()

    @Query("DELETE FROM provider_slots WHERE providerId IN (:providerIds)")
    suspend fun deleteSlotsByProviderIds(providerIds: List<String>)

    @Query("SELECT COUNT(*) FROM provider_slots")
    suspend fun count(): Int
}

@Dao
interface ScheduledActionDao {
    @Query("SELECT * FROM scheduled_actions ORDER BY createdAt DESC")
    fun observeActions(): Flow<List<ScheduledActionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAction(action: ScheduledActionEntity): Long

    @Update
    suspend fun updateAction(action: ScheduledActionEntity)

    @Query("DELETE FROM scheduled_actions WHERE id = :id")
    suspend fun deleteAction(id: Long)
}

@Dao
interface DevPostDao {
    @Query("SELECT * FROM dev_posts ORDER BY createdAt DESC")
    fun observePosts(): Flow<List<DevPostEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: DevPostEntity): Long

    @Update
    suspend fun updatePost(post: DevPostEntity)

    @Query("DELETE FROM dev_posts WHERE id = :id")
    suspend fun deletePost(id: Long)

    @Query("SELECT COUNT(*) FROM dev_posts")
    suspend fun count(): Int
}
