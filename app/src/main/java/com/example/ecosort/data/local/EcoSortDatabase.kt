package com.example.ecosort.data.local

import android.content.Context
import androidx.room.*
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.ecosort.data.model.*
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow

// ==================== TYPE CONVERTERS ====================
class Converters {
    private val gson = Gson()

    @TypeConverter
    fun fromWasteCategoryList(value: List<WasteCategory>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toWasteCategoryList(value: String): List<WasteCategory> {
        val type = object : TypeToken<List<WasteCategory>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toStringList(value: String): List<String> {
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromUserType(value: UserType): String = value.name

    @TypeConverter
    fun toUserType(value: String): UserType = UserType.valueOf(value)

    @TypeConverter
    fun fromWasteCategory(value: WasteCategory): String = value.name

    @TypeConverter
    fun toWasteCategory(value: String): WasteCategory = WasteCategory.valueOf(value)

    @TypeConverter
    fun fromItemCondition(value: ItemCondition): String = value.name

    @TypeConverter
    fun toItemCondition(value: String): ItemCondition = ItemCondition.valueOf(value)

    @TypeConverter
    fun fromItemStatus(value: ItemStatus): String = value.name

    @TypeConverter
    fun toItemStatus(value: String): ItemStatus = ItemStatus.valueOf(value)

    @TypeConverter
    fun fromItemAction(value: ItemAction?): String? = value?.name

    @TypeConverter
    fun toItemAction(value: String?): ItemAction? = value?.let { ItemAction.valueOf(it) }

    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)
}

// ==================== USER DAO ====================
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%' AND username != :currentUsername LIMIT 20")
    suspend fun searchUsersByUsername(query: String, currentUsername: String): List<User>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET itemsRecycled = itemsRecycled + 1 WHERE id = :userId")
    suspend fun incrementItemsRecycled(userId: Long)

    @Query("UPDATE users SET totalEarnings = totalEarnings + :amount WHERE id = :userId")
    suspend fun addEarnings(userId: Long, amount: Double)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Long)
}

// ==================== SCANNED ITEMS DAO ====================
@Dao
interface ScannedItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScannedItem(item: ScannedItem): Long

    @Query("SELECT * FROM scanned_items WHERE userId = :userId ORDER BY scannedAt DESC")
    fun getUserScannedItems(userId: Long): Flow<List<ScannedItem>>

    @Query("SELECT * FROM scanned_items WHERE userId = :userId ORDER BY scannedAt DESC LIMIT 1")
    suspend fun getLastScannedItem(userId: Long): ScannedItem?

    @Query("SELECT * FROM scanned_items WHERE id = :itemId LIMIT 1")
    suspend fun getScannedItemById(itemId: Long): ScannedItem?

    @Update
    suspend fun updateScannedItem(item: ScannedItem)

    @Query("UPDATE scanned_items SET action = :action WHERE id = :itemId")
    suspend fun updateItemAction(itemId: Long, action: ItemAction)

    @Query("SELECT COUNT(*) FROM scanned_items WHERE userId = :userId AND isRecyclable = 1")
    suspend fun getRecyclableCount(userId: Long): Int

    @Query("DELETE FROM scanned_items WHERE id = :itemId")
    suspend fun deleteScannedItem(itemId: Long)
}

// ==================== RECYCLING STATION DAO ====================
@Dao
interface RecyclingStationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStation(station: RecyclingStation): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStations(stations: List<RecyclingStation>)

    @Query("SELECT * FROM recycling_stations")
    fun getAllStations(): Flow<List<RecyclingStation>>

    @Query("SELECT * FROM recycling_stations WHERE id = :stationId LIMIT 1")
    suspend fun getStationById(stationId: Long): RecyclingStation?

    @Update
    suspend fun updateStation(station: RecyclingStation)

    @Query("DELETE FROM recycling_stations WHERE id = :stationId")
    suspend fun deleteStation(stationId: Long)

    @Query("DELETE FROM recycling_stations")
    suspend fun deleteAllStations()
}

// ==================== MARKETPLACE ITEM DAO ====================
@Dao
interface MarketplaceItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: MarketplaceItem): Long

    @Query("SELECT * FROM marketplace_items WHERE status = 'AVAILABLE' ORDER BY postedAt DESC")
    fun getAvailableItems(): Flow<List<MarketplaceItem>>

    @Query("SELECT * FROM marketplace_items WHERE sellerId = :userId ORDER BY postedAt DESC")
    fun getUserItems(userId: Long): Flow<List<MarketplaceItem>>

    @Query("SELECT * FROM marketplace_items WHERE id = :itemId LIMIT 1")
    suspend fun getItemById(itemId: Long): MarketplaceItem?

    @Query("SELECT * FROM marketplace_items WHERE title = :title LIMIT 1")
    suspend fun getItemByTitle(title: String): MarketplaceItem?

    @Query("DELETE FROM marketplace_items WHERE title IN (:titles)")
    suspend fun deleteItemsByTitles(titles: List<String>)

    @Query("SELECT * FROM marketplace_items WHERE category = :category AND status = 'AVAILABLE' ORDER BY postedAt DESC")
    fun getItemsByCategory(category: WasteCategory): Flow<List<MarketplaceItem>>

    @Update
    suspend fun updateItem(item: MarketplaceItem)

    @Query("UPDATE marketplace_items SET status = :status WHERE id = :itemId")
    suspend fun updateItemStatus(itemId: Long, status: ItemStatus)

    @Query("UPDATE marketplace_items SET views = views + 1 WHERE id = :itemId")
    suspend fun incrementViews(itemId: Long)

    @Query("DELETE FROM marketplace_items WHERE id = :itemId")
    suspend fun deleteItem(itemId: Long)
}

// ==================== CHAT MESSAGE DAO ====================
@Dao
interface ChatMessageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessage): Long

    @Query("SELECT * FROM chat_messages WHERE channelId = :channelId ORDER BY timestamp ASC")
    fun getMessagesForChannel(channelId: String): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages WHERE channelId = :channelId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageForChannel(channelId: String): ChatMessage?

    @Query("UPDATE chat_messages SET isRead = 1 WHERE channelId = :channelId AND senderId != :currentUserId")
    suspend fun markMessagesAsRead(channelId: String, currentUserId: Long)

    @Query("UPDATE chat_messages SET messageStatus = 'SEEN' WHERE channelId = :channelId AND senderId != :currentUserId")
    suspend fun updateMessageStatusToSeen(channelId: String, currentUserId: Long)

    @Query("UPDATE chat_messages SET messageStatus = :status WHERE id = :messageId")
    suspend fun updateMessageStatus(messageId: Long, status: MessageStatus)

    @Query("SELECT COUNT(*) FROM chat_messages WHERE channelId = :channelId AND senderId != :currentUserId AND isRead = 0")
    suspend fun getUnreadCount(channelId: String, currentUserId: Long): Int

    @Query("DELETE FROM chat_messages WHERE channelId = :channelId")
    suspend fun deleteChannelMessages(channelId: String)
}

// ==================== CONVERSATION DAO ====================
@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: Conversation)

    @Query("""
        SELECT c.*, 
               CASE 
                   WHEN c.participant1Id = :userId THEN 
                       (SELECT COUNT(*) FROM chat_messages 
                        WHERE channelId = c.channelId 
                        AND senderId != :userId 
                        AND isRead = 0)
                   ELSE 
                       (SELECT COUNT(*) FROM chat_messages 
                        WHERE channelId = c.channelId 
                        AND senderId != :userId 
                        AND isRead = 0)
               END as unreadCount
        FROM conversations c 
        WHERE c.participant1Id = :userId OR c.participant2Id = :userId 
        ORDER BY c.lastMessageTimestamp DESC
    """)
    fun getConversationsForUser(userId: Long): Flow<List<Conversation>>

    @Query("SELECT * FROM conversations WHERE channelId = :channelId")
    suspend fun getConversationByChannelId(channelId: String): Conversation?

    @Query("UPDATE conversations SET lastMessageText = :messageText, lastMessageTimestamp = :timestamp, lastMessageSenderId = :senderId WHERE channelId = :channelId")
    suspend fun updateLastMessage(channelId: String, messageText: String, timestamp: Long, senderId: Long)


    @Query("DELETE FROM conversations WHERE channelId = :channelId")
    suspend fun deleteConversation(channelId: String)
}

// ==================== MAIN DATABASE ====================
@Database(
    entities = [
        User::class,
        ScannedItem::class,
        RecyclingStation::class,
        MarketplaceItem::class,
        ChatMessage::class,
        Conversation::class
    ],
    version = 4,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class EcoSortDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun scannedItemDao(): ScannedItemDao
    abstract fun recyclingStationDao(): RecyclingStationDao
    abstract fun marketplaceItemDao(): MarketplaceItemDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun conversationDao(): ConversationDao

    companion object {
        @Volatile
        private var INSTANCE: EcoSortDatabase? = null

        fun getDatabase(context: Context): EcoSortDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    EcoSortDatabase::class.java,
                    "ecosort_database"
                )
                    .addMigrations(
                        MIGRATION_1_2,
                        MIGRATION_2_3,
                        MIGRATION_3_4
                    )
                    .allowMainThreadQueries() // Temporary for debugging
                    .build()
                INSTANCE = instance
                
                // Log database creation for debugging
                android.util.Log.d("EcoSortDatabase", "Database created/opened successfully")
                android.util.Log.d("EcoSortDatabase", "Database path: ${context.applicationContext.getDatabasePath("ecosort_database")}")
                
                instance
            }
        }
        
        // Database migrations to preserve data
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration from version 1 to 2 - add chat tables
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS chat_messages (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        channelId TEXT NOT NULL,
                        senderId INTEGER NOT NULL,
                        senderUsername TEXT NOT NULL,
                        messageText TEXT NOT NULL,
                        messageType TEXT NOT NULL DEFAULT 'TEXT',
                        attachmentUrl TEXT,
                        attachmentType TEXT,
                        attachmentDuration INTEGER,
                        timestamp INTEGER NOT NULL,
                        isRead INTEGER NOT NULL DEFAULT 0,
                        messageStatus TEXT NOT NULL DEFAULT 'SENDING'
                    )
                """.trimIndent())
            }
        }
        
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration from version 2 to 3 - add conversation table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS conversations (
                        channelId TEXT PRIMARY KEY NOT NULL,
                        participant1Id INTEGER NOT NULL,
                        participant1Username TEXT NOT NULL,
                        participant2Id INTEGER NOT NULL,
                        participant2Username TEXT NOT NULL,
                        lastMessageText TEXT,
                        lastMessageTimestamp INTEGER NOT NULL DEFAULT 0,
                        lastMessageSenderId INTEGER,
                        unreadCount INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Migration from version 3 to 4 - add new fields to chat_messages
                try {
                    // Add new columns if they don't exist
                    database.execSQL("ALTER TABLE chat_messages ADD COLUMN attachmentDuration INTEGER")
                    database.execSQL("ALTER TABLE chat_messages ADD COLUMN messageStatus TEXT NOT NULL DEFAULT 'SENDING'")
                } catch (e: Exception) {
                    // Columns might already exist, ignore the error
                    android.util.Log.d("Migration", "Columns might already exist: ${e.message}")
                }
            }
        }
    }
}