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
    fun fromItemAction(value: ItemAction?): String? = value?.name

    @TypeConverter
    fun toItemAction(value: String?): ItemAction? = value?.let { ItemAction.valueOf(it) }

    @TypeConverter
    fun fromMessageType(value: MessageType): String = value.name

    @TypeConverter
    fun toMessageType(value: String): MessageType = MessageType.valueOf(value)

    @TypeConverter
    fun fromPostType(value: PostType): String = value.name

    @TypeConverter
    fun toPostType(value: String): PostType = PostType.valueOf(value)

    @TypeConverter
    fun fromPostStatus(value: PostStatus): String = value.name

    @TypeConverter
    fun toPostStatus(value: String): PostStatus = PostStatus.valueOf(value)

    @TypeConverter
    fun fromInputType(value: InputType): String = value.name

    @TypeConverter
    fun toInputType(value: String): InputType = InputType.valueOf(value)

    // User Profile Type Converters
    @TypeConverter
    fun fromPrivacySettings(value: PrivacySettings): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toPrivacySettings(value: String): PrivacySettings {
        return gson.fromJson(value, PrivacySettings::class.java)
    }

    @TypeConverter
    fun fromAchievementList(value: List<Achievement>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toAchievementList(value: String): List<Achievement> {
        val type = object : TypeToken<List<Achievement>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromSocialLinks(value: SocialLinks): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toSocialLinks(value: String): SocialLinks {
        return gson.fromJson(value, SocialLinks::class.java)
    }

    @TypeConverter
    fun fromUserPreferences(value: UserPreferences): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toUserPreferences(value: String): UserPreferences {
        return gson.fromJson(value, UserPreferences::class.java)
    }

    @TypeConverter
    fun fromFriendRequestStatus(value: FriendRequestStatus): String = value.name

    @TypeConverter
    fun toFriendRequestStatus(value: String): FriendRequestStatus = FriendRequestStatus.valueOf(value)
}

// ==================== USER DAO ====================
@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE username LIKE '%' || :query || '%' AND id != :currentUserId LIMIT 20")
    suspend fun searchUsersByUsername(query: String, currentUserId: Long): List<User>

    @Query("SELECT * FROM users WHERE (username LIKE '%' || :query || '%' OR email LIKE '%' || :query || '%') AND id != :currentUserId LIMIT 20")
    suspend fun searchUsers(query: String, currentUserId: Long): List<User>

    @Query("SELECT * FROM users")
    suspend fun getAllUsers(): List<User>

    @Query("SELECT * FROM users")
    fun getAllUsersFlow(): Flow<List<User>>

    @Query("SELECT COUNT(*) FROM users")
    suspend fun getUserCount(): Int

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUserByEmail(email: String): User?

    @Query("SELECT * FROM users WHERE id = :userId LIMIT 1")
    suspend fun getUserById(userId: Long): User?

    @Update
    suspend fun updateUser(user: User)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("UPDATE users SET itemsRecycled = itemsRecycled + 1 WHERE id = :userId")
    suspend fun incrementItemsRecycled(userId: Long)

    @Query("UPDATE users SET totalPoints = totalPoints + :points WHERE id = :userId")
    suspend fun addPoints(userId: Long, points: Int)

    @Query("DELETE FROM users WHERE id = :userId")
    suspend fun deleteUser(userId: Long)

    // Profile management methods
    @Query("UPDATE users SET bio = :bio WHERE id = :userId")
    suspend fun updateBio(userId: Long, bio: String?)

    @Query("UPDATE users SET location = :location WHERE id = :userId")
    suspend fun updateLocation(userId: Long, location: String?)

    @Query("UPDATE users SET profileImageUrl = :imageUrl WHERE id = :userId")
    suspend fun updateProfileImage(userId: Long, imageUrl: String?)

    @Query("UPDATE users SET lastActive = :timestamp WHERE id = :userId")
    suspend fun updateLastActive(userId: Long, timestamp: Long)

    @Query("UPDATE users SET profileCompletion = :completion WHERE id = :userId")
    suspend fun updateProfileCompletion(userId: Long, completion: Int)

    @Query("UPDATE users SET privacySettings = :privacySettings WHERE id = :userId")
    suspend fun updatePrivacySettings(userId: Long, privacySettings: String?)

    @Query("UPDATE users SET achievements = :achievements WHERE id = :userId")
    suspend fun updateAchievements(userId: Long, achievements: String?)

    @Query("UPDATE users SET socialLinks = :socialLinks WHERE id = :userId")
    suspend fun updateSocialLinks(userId: Long, socialLinks: String?)

    @Query("UPDATE users SET preferences = :preferences WHERE id = :userId")
    suspend fun updatePreferences(userId: Long, preferences: String?)
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
    
    @Query("UPDATE chat_messages SET channelId = :newChannelId WHERE channelId = :oldChannelId")
    suspend fun updateChannelId(oldChannelId: String, newChannelId: String)
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

// ==================== COMMUNITY POST DAO ====================
@Dao
interface CommunityPostDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPost(post: CommunityPost): Long

    @Query("SELECT * FROM community_posts WHERE status = 'PUBLISHED' ORDER BY postedAt DESC")
    fun getAllPosts(): Flow<List<CommunityPost>>

    @Query("SELECT * FROM community_posts WHERE authorId = :userId ORDER BY postedAt DESC")
    fun getUserPosts(userId: Long): Flow<List<CommunityPost>>

    @Query("SELECT * FROM community_posts WHERE id = :postId LIMIT 1")
    suspend fun getPostById(postId: Long): CommunityPost?

    @Query("SELECT * FROM community_posts WHERE postType = :postType AND status = 'PUBLISHED' ORDER BY postedAt DESC")
    fun getPostsByType(postType: PostType): Flow<List<CommunityPost>>

    @Query("SELECT * FROM community_posts WHERE tags LIKE '%' || :tag || '%' AND status = 'PUBLISHED' ORDER BY postedAt DESC")
    fun getPostsByTag(tag: String): Flow<List<CommunityPost>>

    @Query("""
        SELECT cp.* FROM community_posts cp 
        INNER JOIN user_follows uf ON cp.authorId = uf.followingId 
        WHERE uf.followerId = :userId AND cp.status = 'PUBLISHED' 
        ORDER BY cp.postedAt DESC
    """)
    fun getFollowingPosts(userId: Long): Flow<List<CommunityPost>>

    @Update
    suspend fun updatePost(post: CommunityPost)

    @Query("UPDATE community_posts SET likesCount = likesCount + 1 WHERE id = :postId")
    suspend fun incrementLikes(postId: Long)

    @Query("UPDATE community_posts SET likesCount = likesCount - 1 WHERE id = :postId")
    suspend fun decrementLikes(postId: Long)

    @Query("UPDATE community_posts SET commentsCount = commentsCount + 1 WHERE id = :postId")
    suspend fun incrementComments(postId: Long)

    @Query("UPDATE community_posts SET commentsCount = commentsCount - 1 WHERE id = :postId")
    suspend fun decrementComments(postId: Long)

    @Query("UPDATE community_posts SET sharesCount = sharesCount + 1 WHERE id = :postId")
    suspend fun incrementShares(postId: Long)

    @Query("UPDATE community_posts SET status = :status WHERE id = :postId")
    suspend fun updatePostStatus(postId: Long, status: PostStatus)

    @Query("DELETE FROM community_posts WHERE id = :postId")
    suspend fun deletePost(postId: Long)
}

// ==================== COMMUNITY COMMENT DAO ====================
@Dao
interface CommunityCommentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: CommunityComment): Long

    @Query("SELECT * FROM community_comments WHERE postId = :postId ORDER BY postedAt ASC")
    fun getCommentsForPost(postId: Long): Flow<List<CommunityComment>>

    @Query("SELECT * FROM community_comments WHERE id = :commentId LIMIT 1")
    suspend fun getCommentById(commentId: Long): CommunityComment?

    @Query("SELECT * FROM community_comments WHERE parentCommentId = :parentId ORDER BY postedAt ASC")
    fun getRepliesForComment(parentId: Long): Flow<List<CommunityComment>>

    @Update
    suspend fun updateComment(comment: CommunityComment)

    @Query("UPDATE community_comments SET likesCount = likesCount + 1 WHERE id = :commentId")
    suspend fun incrementCommentLikes(commentId: Long)

    @Query("UPDATE community_comments SET likesCount = likesCount - 1 WHERE id = :commentId")
    suspend fun decrementCommentLikes(commentId: Long)

    @Query("DELETE FROM community_comments WHERE id = :commentId")
    suspend fun deleteComment(commentId: Long)

    @Query("DELETE FROM community_comments WHERE postId = :postId")
    suspend fun deleteCommentsForPost(postId: Long)
}

// ==================== COMMUNITY LIKE DAO ====================
@Dao
interface CommunityLikeDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLike(like: CommunityLike): Long

    @Query("SELECT * FROM community_likes WHERE postId = :postId AND userId = :userId LIMIT 1")
    suspend fun getUserLikeForPost(postId: Long, userId: Long): CommunityLike?

    @Query("SELECT COUNT(*) FROM community_likes WHERE postId = :postId")
    suspend fun getLikeCountForPost(postId: Long): Int

    @Query("DELETE FROM community_likes WHERE postId = :postId AND userId = :userId")
    suspend fun removeLike(postId: Long, userId: Long)

    @Query("DELETE FROM community_likes WHERE postId = :postId")
    suspend fun deleteLikesForPost(postId: Long)
}

// ==================== USER FOLLOW DAO ====================
@Dao
interface UserFollowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFollow(follow: UserFollow): Long

    @Query("SELECT * FROM user_follows WHERE followerId = :followerId AND followingId = :followingId LIMIT 1")
    suspend fun getFollow(followerId: Long, followingId: Long): UserFollow?

    @Query("SELECT * FROM user_follows WHERE followerId = :userId ORDER BY followedAt DESC")
    fun getUserFollowing(userId: Long): Flow<List<UserFollow>>

    @Query("SELECT * FROM user_follows WHERE followingId = :userId ORDER BY followedAt DESC")
    fun getUserFollowers(userId: Long): Flow<List<UserFollow>>

    @Query("SELECT COUNT(*) FROM user_follows WHERE followerId = :userId")
    suspend fun getFollowingCount(userId: Long): Int

    @Query("SELECT COUNT(*) FROM user_follows WHERE followingId = :userId")
    suspend fun getFollowersCount(userId: Long): Int

    @Query("DELETE FROM user_follows WHERE followerId = :followerId AND followingId = :followingId")
    suspend fun removeFollow(followerId: Long, followingId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM user_follows WHERE followerId = :followerId AND followingId = :followingId)")
    suspend fun isFollowing(followerId: Long, followingId: Long): Boolean
}

// ==================== USER FRIEND DAO ====================
@Dao
interface UserFriendDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriend(friend: UserFriend): Long

    @Query("SELECT * FROM user_friends WHERE (userId = :userId AND friendId = :friendId) OR (userId = :friendId AND friendId = :userId) LIMIT 1")
    suspend fun getFriendship(userId: Long, friendId: Long): UserFriend?

    @Query("SELECT * FROM user_friends WHERE userId = :userId AND status = 'ACCEPTED' ORDER BY acceptedAt DESC")
    fun getUserFriends(userId: Long): Flow<List<UserFriend>>

    @Query("SELECT * FROM user_friends WHERE friendId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingFriendRequests(userId: Long): Flow<List<UserFriend>>

    @Query("SELECT * FROM user_friends WHERE userId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getSentFriendRequests(userId: Long): Flow<List<UserFriend>>

    @Query("SELECT COUNT(*) FROM user_friends WHERE (userId = :userId OR friendId = :userId) AND status = 'ACCEPTED'")
    suspend fun getFriendsCount(userId: Long): Int

    @Query("SELECT COUNT(*) FROM user_friends WHERE friendId = :userId AND status = 'PENDING'")
    suspend fun getPendingRequestsCount(userId: Long): Int

    @Query("UPDATE user_friends SET status = 'ACCEPTED', acceptedAt = :acceptedAt WHERE id = :friendshipId")
    suspend fun acceptFriendRequest(friendshipId: Long, acceptedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_friends WHERE id = :friendshipId")
    suspend fun removeFriendship(friendshipId: Long)

    @Query("DELETE FROM user_friends WHERE (userId = :userId AND friendId = :friendId) OR (userId = :friendId AND friendId = :userId)")
    suspend fun removeFriendship(userId: Long, friendId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM user_friends WHERE (userId = :userId AND friendId = :friendId) OR (userId = :friendId AND friendId = :userId) AND status = 'ACCEPTED')")
    suspend fun areFriends(userId: Long, friendId: Long): Boolean
}

// ==================== FRIEND REQUEST DAO ====================
@Dao
interface FriendRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendRequest(friendRequest: FriendRequest): Long

    @Update
    suspend fun updateFriendRequest(friendRequest: FriendRequest)

    @Delete
    suspend fun deleteFriendRequest(friendRequest: FriendRequest)

    @Query("SELECT * FROM friend_requests WHERE receiverId = :userId AND status = 'PENDING' ORDER BY createdAt DESC")
    fun getPendingFriendRequests(userId: Long): Flow<List<FriendRequest>>

    @Query("SELECT * FROM friend_requests WHERE senderId = :userId ORDER BY createdAt DESC")
    fun getSentFriendRequests(userId: Long): Flow<List<FriendRequest>>

    @Query("SELECT * FROM friend_requests WHERE id = :requestId")
    suspend fun getFriendRequestById(requestId: Long): FriendRequest?

    @Query("SELECT * FROM friend_requests WHERE senderId = :senderId AND receiverId = :receiverId AND status = 'PENDING'")
    suspend fun getPendingRequestBetweenUsers(senderId: Long, receiverId: Long): FriendRequest?

    @Query("UPDATE friend_requests SET status = :status, updatedAt = :updatedAt WHERE id = :requestId")
    suspend fun updateFriendRequestStatus(requestId: Long, status: FriendRequestStatus, updatedAt: Long)

    @Query("DELETE FROM friend_requests WHERE senderId = :userId OR receiverId = :userId")
    suspend fun deleteAllFriendRequestsForUser(userId: Long)
}

// ==================== FRIENDSHIP DAO ====================
@Dao
interface FriendshipDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFriendship(friendship: Friendship): Long

    @Delete
    suspend fun deleteFriendship(friendship: Friendship)

    @Query("SELECT * FROM friendships WHERE userId1 = :userId OR userId2 = :userId ORDER BY lastInteraction DESC")
    fun getFriendships(userId: Long): Flow<List<Friendship>>

    @Query("SELECT * FROM friendships WHERE (userId1 = :userId1 AND userId2 = :userId2) OR (userId1 = :userId2 AND userId2 = :userId1)")
    suspend fun getFriendshipBetweenUsers(userId1: Long, userId2: Long): Friendship?

    @Query("DELETE FROM friendships WHERE (userId1 = :userId1 AND userId2 = :userId2) OR (userId1 = :userId2 AND userId2 = :userId1)")
    suspend fun removeFriendship(userId1: Long, userId2: Long)

    @Query("UPDATE friendships SET lastInteraction = :timestamp WHERE (userId1 = :userId1 AND userId2 = :userId2) OR (userId1 = :userId2 AND userId2 = :userId1)")
    suspend fun updateLastInteraction(userId1: Long, userId2: Long, timestamp: Long)

    @Query("SELECT COUNT(*) FROM friendships WHERE userId1 = :userId OR userId2 = :userId")
    suspend fun getFriendCount(userId: Long): Int
}

// ==================== BLOCKED USER DAO ====================
@Dao
interface BlockedUserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedUser(blockedUser: BlockedUser): Long

    @Delete
    suspend fun deleteBlockedUser(blockedUser: BlockedUser)

    @Query("SELECT * FROM blocked_users WHERE blockerId = :userId ORDER BY createdAt DESC")
    fun getBlockedUsers(userId: Long): Flow<List<BlockedUser>>

    @Query("SELECT * FROM blocked_users WHERE blockerId = :blockerId AND blockedId = :blockedId")
    suspend fun getBlockedUser(blockerId: Long, blockedId: Long): BlockedUser?

    @Query("DELETE FROM blocked_users WHERE blockerId = :blockerId AND blockedId = :blockedId")
    suspend fun unblockUser(blockerId: Long, blockedId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_users WHERE (blockerId = :userId1 AND blockedId = :userId2) OR (blockerId = :userId2 AND blockedId = :userId1))")
    suspend fun isBlocked(userId1: Long, userId2: Long): Boolean
}

// ==================== MAIN DATABASE ====================
@Database(
    entities = [
        User::class,
        ScannedItem::class,
        RecyclingStation::class,
        ChatMessage::class,
        Conversation::class,
        CommunityPost::class,
        CommunityComment::class,
        CommunityLike::class,
        UserFollow::class,
        UserFriend::class,
        FriendRequest::class,
        Friendship::class,
        BlockedUser::class,
        Admin::class,
        AdminAction::class
    ],
    version = 18,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class EcoSortDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun scannedItemDao(): ScannedItemDao
    abstract fun recyclingStationDao(): RecyclingStationDao
    abstract fun chatMessageDao(): ChatMessageDao
    abstract fun conversationDao(): ConversationDao
    abstract fun communityPostDao(): CommunityPostDao
    abstract fun communityCommentDao(): CommunityCommentDao
    abstract fun communityLikeDao(): CommunityLikeDao
    abstract fun userFollowDao(): UserFollowDao
    abstract fun userFriendDao(): UserFriendDao
    abstract fun friendRequestDao(): FriendRequestDao
    abstract fun friendshipDao(): FriendshipDao
    abstract fun blockedUserDao(): BlockedUserDao
    abstract fun adminDao(): AdminDao

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
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10,
                MIGRATION_10_11,
                MIGRATION_11_12,
                MIGRATION_12_13,
                MIGRATION_13_14,
                MIGRATION_14_15,
                MIGRATION_15_16,
                MIGRATION_16_17,
                MIGRATION_17_18
            )
                    .allowMainThreadQueries() // Temporary for debugging
                    .fallbackToDestructiveMigration() // For development only
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

// Migration from version 4 to 5 - add community tables
internal val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            try {
                // Create community_posts table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS community_posts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        authorId INTEGER NOT NULL,
                        authorName TEXT NOT NULL,
                        authorAvatar TEXT,
                        title TEXT NOT NULL,
                        content TEXT NOT NULL,
                        postType TEXT NOT NULL,
                        imageUrls TEXT NOT NULL,
                        videoUrl TEXT,
                        location TEXT,
                        tags TEXT NOT NULL,
                        postedAt INTEGER NOT NULL,
                        likesCount INTEGER NOT NULL,
                        commentsCount INTEGER NOT NULL,
                        sharesCount INTEGER NOT NULL,
                        isLikedByUser INTEGER NOT NULL,
                        status TEXT NOT NULL
                    )
                """.trimIndent())

                // Create community_comments table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS community_comments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        postId INTEGER NOT NULL,
                        authorId INTEGER NOT NULL,
                        authorName TEXT NOT NULL,
                        authorAvatar TEXT,
                        content TEXT NOT NULL,
                        parentCommentId INTEGER,
                        postedAt INTEGER NOT NULL,
                        likesCount INTEGER NOT NULL,
                        isLikedByUser INTEGER NOT NULL
                    )
                """.trimIndent())

                // Create community_likes table
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS community_likes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        postId INTEGER NOT NULL,
                        userId INTEGER NOT NULL,
                        likedAt INTEGER NOT NULL
                    )
                """.trimIndent())

                android.util.Log.d("Migration", "Community tables created successfully")
            } catch (e: Exception) {
                android.util.Log.e("Migration", "Error creating community tables: ${e.message}")
            }
        }
    }

internal val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Add inputType column to community_posts table
            database.execSQL("ALTER TABLE community_posts ADD COLUMN inputType TEXT NOT NULL DEFAULT 'TEXT'")
            android.util.Log.d("Migration", "Added inputType column to community_posts table")
            
            // Update old post type values to new enum values
            database.execSQL("UPDATE community_posts SET postType = 'TIP' WHERE postType = 'TEXT'")
            database.execSQL("UPDATE community_posts SET postType = 'TIP' WHERE postType = 'IMAGE'")
            database.execSQL("UPDATE community_posts SET postType = 'TIP' WHERE postType = 'VIDEO'")
            android.util.Log.d("Migration", "Updated old post type values to new enum values")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error in migration 5_6: ${e.message}")
        }
    }
}

internal val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Update old post type values to new enum values
            database.execSQL("UPDATE community_posts SET postType = 'TIP' WHERE postType = 'TEXT'")
            database.execSQL("UPDATE community_posts SET postType = 'TIP' WHERE postType = 'IMAGE'")
            database.execSQL("UPDATE community_posts SET postType = 'TIP' WHERE postType = 'VIDEO'")
            android.util.Log.d("Migration", "Updated old post type values to new enum values in migration 6_7")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error in migration 6_7: ${e.message}")
        }
    }
}

internal val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Add demo images to existing posts that don't have images
            database.execSQL("UPDATE community_posts SET imageUrls = '[\"demo_black_image\"]' WHERE imageUrls = '[]' OR imageUrls IS NULL")
            android.util.Log.d("Migration", "Added demo images to existing posts in migration 7_8")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error in migration 7_8: ${e.message}")
        }
    }
}

internal val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Remove marketplace tables that are no longer needed
            database.execSQL("DROP TABLE IF EXISTS marketplace_items")
            android.util.Log.d("Migration", "Removed marketplace_items table in migration 8_9")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error in migration 8_9: ${e.message}")
        }
    }
}

internal val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Add firebaseId column to community_posts table
            database.execSQL("ALTER TABLE community_posts ADD COLUMN firebaseId TEXT NOT NULL DEFAULT ''")
            android.util.Log.d("Migration", "Added firebaseId column to community_posts table in migration 9_10")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error in migration 9_10: ${e.message}")
        }
    }
}

internal val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Add firebaseId column to community_comments table
            database.execSQL("ALTER TABLE community_comments ADD COLUMN firebaseId TEXT NOT NULL DEFAULT ''")
            android.util.Log.d("Migration", "Added firebaseId column to community_comments table in migration 10_11")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error in migration 10_11: ${e.message}")
        }
    }
}

internal val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Add new user profile columns
            database.execSQL("ALTER TABLE users ADD COLUMN bio TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN location TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN joinDate INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
            database.execSQL("ALTER TABLE users ADD COLUMN lastActive INTEGER NOT NULL DEFAULT ${System.currentTimeMillis()}")
            database.execSQL("ALTER TABLE users ADD COLUMN profileCompletion INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE users ADD COLUMN privacySettings TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN achievements TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN socialLinks TEXT")
            database.execSQL("ALTER TABLE users ADD COLUMN preferences TEXT")
            android.util.Log.d("Migration", "Added user profile columns in migration 11_12")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error in migration 11_12: ${e.message}")
        }
    }
}

internal val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Create user_follows table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS user_follows (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    followerId INTEGER NOT NULL,
                    followingId INTEGER NOT NULL,
                    followedAt INTEGER NOT NULL
                )
            """.trimIndent())
            
            // Create user_friends table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS user_friends (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId INTEGER NOT NULL,
                    friendId INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    createdAt INTEGER NOT NULL,
                    acceptedAt INTEGER
                )
            """.trimIndent())
            
            android.util.Log.d("Migration", "Created social features tables in migration 12_13")
        } catch (e: Exception) {
            android.util.Log.e("Migration", "Error in migration 12_13: ${e.message}")
        }
    }
}

internal val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Add totalPoints column and migrate data from totalEarnings
            database.execSQL("""
                ALTER TABLE users ADD COLUMN totalPoints INTEGER NOT NULL DEFAULT 0
            """.trimIndent())
            
            // Migrate existing totalEarnings data to totalPoints (convert Double to Int)
            database.execSQL("""
                UPDATE users SET totalPoints = CAST(totalEarnings AS INTEGER) WHERE totalEarnings IS NOT NULL
            """.trimIndent())
            
            // Drop the old totalEarnings column
            database.execSQL("""
                CREATE TABLE users_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    username TEXT NOT NULL,
                    email TEXT NOT NULL,
                    passwordHash TEXT NOT NULL,
                    userType TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    itemsRecycled INTEGER NOT NULL,
                    totalPoints INTEGER NOT NULL,
                    profileImageUrl TEXT,
                    bio TEXT,
                    location TEXT,
                    joinDate INTEGER NOT NULL,
                    lastActive INTEGER NOT NULL,
                    profileCompletion INTEGER NOT NULL,
                    privacySettings TEXT,
                    achievements TEXT,
                    socialLinks TEXT,
                    preferences TEXT
                )
            """.trimIndent())
            
            // Copy data from old table to new table
            database.execSQL("""
                INSERT INTO users_new (
                    id, username, email, passwordHash, userType, createdAt, itemsRecycled,
                    totalPoints, profileImageUrl, bio, location, joinDate, lastActive,
                    profileCompletion, privacySettings, achievements, socialLinks, preferences
                )
                SELECT 
                    id, username, email, passwordHash, userType, createdAt, itemsRecycled,
                    totalPoints, profileImageUrl, bio, location, joinDate, lastActive,
                    profileCompletion, privacySettings, achievements, socialLinks, preferences
                FROM users
            """.trimIndent())
            
            // Drop old table and rename new table
            database.execSQL("DROP TABLE users")
            database.execSQL("ALTER TABLE users_new RENAME TO users")
            
        } catch (e: Exception) {
            android.util.Log.e("Migration_13_14", "Migration failed: ${e.message}")
            throw e
        }
    }
}

internal val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Create friend_requests table
            database.execSQL("""
                CREATE TABLE friend_requests (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    senderId INTEGER NOT NULL,
                    receiverId INTEGER NOT NULL,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    message TEXT,
                    createdAt INTEGER NOT NULL,
                    updatedAt INTEGER NOT NULL
                )
            """.trimIndent())

            // Create friendships table
            database.execSQL("""
                CREATE TABLE friendships (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    userId1 INTEGER NOT NULL,
                    userId2 INTEGER NOT NULL,
                    createdAt INTEGER NOT NULL,
                    lastInteraction INTEGER NOT NULL
                )
            """.trimIndent())

            // Create blocked_users table
            database.execSQL("""
                CREATE TABLE blocked_users (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    blockerId INTEGER NOT NULL,
                    blockedId INTEGER NOT NULL,
                    reason TEXT,
                    createdAt INTEGER NOT NULL
                )
            """.trimIndent())

            android.util.Log.d("Migration_14_15", "Created friend system tables successfully")

        } catch (e: Exception) {
            android.util.Log.e("Migration_14_15", "Migration failed: ${e.message}")
            throw e
        }
    }
}

internal val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Create admins table
            database.execSQL("""
                CREATE TABLE admins (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    username TEXT NOT NULL,
                    email TEXT NOT NULL,
                    passwordHash TEXT NOT NULL,
                    createdAt INTEGER NOT NULL,
                    lastLogin INTEGER NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    permissions TEXT NOT NULL DEFAULT 'FULL_ACCESS'
                )
            """.trimIndent())
            // Create admin_actions table
            database.execSQL("""
                CREATE TABLE admin_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    adminId INTEGER NOT NULL,
                    action TEXT NOT NULL,
                    targetUserId INTEGER,
                    details TEXT,
                    timestamp INTEGER NOT NULL
                )
            """.trimIndent())

            android.util.Log.d("Migration_15_16", "Created admin tables successfully")

        } catch (e: Exception) {
            android.util.Log.e("Migration_15_16", "Migration failed: ${e.message}")
            throw e
        }
    }
}

internal val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Add firebaseUid column to users table
            database.execSQL("ALTER TABLE users ADD COLUMN firebaseUid TEXT")
            android.util.Log.d("Migration_16_17", "Added firebaseUid column to users table successfully")
        } catch (e: Exception) {
            android.util.Log.e("Migration_16_17", "Migration failed: ${e.message}")
            throw e
        }
    }
}

internal val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
    override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
        try {
            // Add unique constraints for username and email
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_username ON users (username)")
            database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_users_email ON users (email)")
            android.util.Log.d("Migration_17_18", "Added unique constraints for username and email successfully")
        } catch (e: Exception) {
            android.util.Log.e("Migration_17_18", "Migration failed: ${e.message}")
            throw e
        }
    }
}
