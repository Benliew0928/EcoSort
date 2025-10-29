package com.example.ecosort.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.io.Serializable

// ==================== USER MODEL ====================
@Entity(
    tableName = "users",
    indices = [
        Index(value = ["username"], unique = true),
        Index(value = ["email"], unique = true)
    ]
)
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val firebaseUid: String? = null, // Firebase Authentication UID
    val username: String,
    val email: String,
    val passwordHash: String,
    val userType: UserType,
    val createdAt: Long = System.currentTimeMillis(),
    val itemsRecycled: Int = 0,
    val totalPoints: Int = 0,
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val joinDate: Long = System.currentTimeMillis(),
    val lastActive: Long = System.currentTimeMillis(),
    val profileCompletion: Int = 0, // 0-100%
    val privacySettings: String? = null, // JSON string for PrivacySettings
    val achievements: String? = null, // JSON string for List<Achievement>
    val socialLinks: String? = null, // JSON string for SocialLinks
    val preferences: String? = null // JSON string for UserPreferences
) : Serializable

enum class UserType : Serializable {
    USER, ADMIN
}

// ==================== USER PROFILE SUPPORTING MODELS ====================

data class PrivacySettings(
    val profileVisibility: ProfileVisibility = ProfileVisibility.PUBLIC,
    val showEmail: Boolean = false,
    val showLocation: Boolean = true,
    val showStats: Boolean = true,
    val allowMessages: Boolean = true
) : Serializable

enum class ProfileVisibility {
    PUBLIC, FRIENDS_ONLY, PRIVATE
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlockedAt: Long,
    val category: AchievementCategory
) : Serializable

enum class AchievementCategory {
    RECYCLING, COMMUNITY, CONSISTENCY, MILESTONE
}

data class SocialLinks(
    val website: String? = null,
    val instagram: String? = null,
    val twitter: String? = null,
    val linkedin: String? = null
) : Serializable

data class UserPreferences(
    // ==================== APPEARANCE SETTINGS ====================
    val theme: AppTheme = AppTheme.SYSTEM,
    val language: String = "en",
    val fontSize: FontSize = FontSize.MEDIUM,

    // ==================== NOTIFICATION SETTINGS ====================
    val notifications: NotificationPreferences = NotificationPreferences(),

    // ==================== PRIVACY SETTINGS ====================
    val privacy: PrivacyPreferences = PrivacyPreferences()
) : Serializable

data class NotificationPreferences(
    val pushNotifications: Boolean = true,
    val communityUpdates: Boolean = true,
    val friendRequests: Boolean = true,
    val messageNotifications: Boolean = true
) : Serializable

data class AccessibilitySettings(
    val highContrast: Boolean = false,
    val reduceMotion: Boolean = false,
    val screenReader: Boolean = false,
    val largeText: Boolean = false,
    val boldText: Boolean = false,
    val colorBlindSupport: Boolean = false
) : Serializable

data class PrivacyPreferences(
    val showOnlineStatus: Boolean = true,
    val allowFriendRequests: Boolean = true,
    val showLastSeen: Boolean = true,
    val allowProfileViews: Boolean = true,
    val shareRecyclingStats: Boolean = true,
    val allowDataCollection: Boolean = true
) : Serializable

data class RecyclingPreferences(
    val favoriteCategories: List<String> = emptyList(),
    val reminderFrequency: ReminderFrequency = ReminderFrequency.WEEKLY,
    val autoCategorize: Boolean = true,
    val shareStats: Boolean = true,
    val showTips: Boolean = true,
    val enableGamification: Boolean = true
) : Serializable

enum class AppTheme {
    LIGHT, DARK, SYSTEM
}

enum class FontSize {
    SMALL, MEDIUM, LARGE
}

enum class ReminderFrequency {
    DAILY, WEEKLY, MONTHLY, NEVER
}

enum class MeasurementUnits {
    METRIC, IMPERIAL
}

// ==================== FRIEND SYSTEM MODELS ====================

@Entity(tableName = "friend_requests")
data class FriendRequest(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val senderFirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val receiverFirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val status: FriendRequestStatus = FriendRequestStatus.PENDING,
    val message: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Keep for backward compatibility during migration
    @Deprecated("Use senderFirebaseUid instead") val senderId: Long = 0,
    @Deprecated("Use receiverFirebaseUid instead") val receiverId: Long = 0
)

enum class FriendRequestStatus {
    PENDING, ACCEPTED, DECLINED, CANCELLED
}

@Entity(tableName = "friendships")
data class Friendship(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val user1FirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val user2FirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val createdAt: Long = System.currentTimeMillis(),
    val lastInteraction: Long = System.currentTimeMillis(),
    // Keep for backward compatibility during migration
    @Deprecated("Use user1FirebaseUid instead") val userId1: Long = 0,
    @Deprecated("Use user2FirebaseUid instead") val userId2: Long = 0
)

@Entity(tableName = "blocked_users")
data class BlockedUser(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val blockerId: Long,
    val blockedId: Long,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

data class FriendActivity(
    val userId: Long,
    val username: String,
    val profileImageUrl: String?,
    val activityType: FriendActivityType,
    val description: String,
    val timestamp: Long,
    val points: Int? = null,
    val itemType: String? = null
) : Serializable

enum class FriendActivityType {
    RECYCLED_ITEM, ACHIEVEMENT_UNLOCKED, LEVEL_UP, POST_CREATED, COMMENT_ADDED
}

data class FriendRecommendation(
    val userId: Long,
    val username: String,
    val profileImageUrl: String?,
    val mutualFriends: Int,
    val commonInterests: List<String>,
    val location: String?,
    val reason: String
) : Serializable

data class FriendLeaderboard(
    val userId: Long,
    val username: String,
    val profileImageUrl: String?,
    val rank: Int,
    val points: Int,
    val itemsRecycled: Int,
    val isCurrentUser: Boolean = false
) : Serializable

// ==================== SCANNED ITEM MODEL ====================
@Entity(tableName = "scanned_items")
data class ScannedItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val itemName: String,
    val category: WasteCategory,
    val isRecyclable: Boolean,
    val confidence: Float,
    val imageUrl: String?,
    val scannedAt: Long = System.currentTimeMillis(),
    val action: ItemAction? = null
)

enum class WasteCategory {
    PLASTIC,
    PAPER,
    GLASS,
    METAL,
    ELECTRONIC,
    ORGANIC,
    HAZARDOUS,
    OTHER
}

enum class ItemAction {
    RECYCLED,
    SOLD,
    PENDING
}

// ==================== RECYCLING STATION MODEL ====================
@Entity(tableName = "recycling_stations")
data class RecyclingStation(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val phoneNumber: String?,
    val openingHours: String,
    val acceptedMaterials: List<WasteCategory>,
    val rating: Float = 0f,
    val isOpen: Boolean = false,
    val distance: Float? = null
)


// ==================== COMMUNITY POST MODEL ====================
@Entity(tableName = "community_posts")
data class CommunityPost(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val firebaseId: String = "", // Store the original Firebase document ID
    val authorFirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val authorName: String,
    val authorAvatar: String? = null,
    val title: String,
    val content: String,
    val postType: PostType,
    val inputType: InputType = InputType.TEXT,
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val location: String? = null,
    val tags: List<String> = emptyList(),
    val postedAt: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val isLikedByUser: Boolean = false,
    val status: PostStatus = PostStatus.PUBLISHED,
    // Keep for backward compatibility during migration
    @Deprecated("Use authorFirebaseUid instead") val authorId: Long = 0
) : Serializable

enum class PostType : Serializable {
    TIP,            // Recycling tips and advice
    ACHIEVEMENT,    // User achievements and milestones
    QUESTION,       // Ask community questions
    EVENT           // Community events and activities
}

enum class InputType : Serializable {
    TEXT,           // Text-only post
    IMAGE,          // Image with optional text
    VIDEO           // Video with optional text
}

enum class PostStatus : Serializable {
    PUBLISHED,
    DRAFT,
    HIDDEN,
    DELETED
}

// ==================== COMMUNITY COMMENT MODEL ====================
@Entity(tableName = "community_comments")
data class CommunityComment(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val firebaseId: String = "", // Store the original Firebase document ID
    val postId: Long,
    val authorFirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val authorName: String,
    val authorAvatar: String? = null,
    val content: String,
    val parentCommentId: Long? = null, // For replies
    val postedAt: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val isLikedByUser: Boolean = false,
    // Keep for backward compatibility during migration
    @Deprecated("Use authorFirebaseUid instead") val authorId: Long = 0
)

// ==================== COMMUNITY LIKE MODEL ====================
@Entity(tableName = "community_likes")
data class CommunityLike(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val postId: Long,
    val userId: Long,
    val likedAt: Long = System.currentTimeMillis()
)

// ==================== HELPER CLASSES ====================

// Scan Result (for ML model output)
data class ScanResult(
    val category: WasteCategory,
    val itemName: String,
    val isRecyclable: Boolean,
    val confidence: Float,
    val recyclingTips: List<String>,
    val estimatedValue: Double? = null
)

// User Session
data class UserSession(
    val userId: Long,
    val username: String,
    val userType: UserType,
    val token: String? = null,
    val isLoggedIn: Boolean = true
)

// API Response wrapper
sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Exception) : Result<Nothing>()
    object Loading : Result<Nothing>()
}

// ==================== CHAT MESSAGE MODEL ====================
@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelId: String,
    val senderFirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val senderUsername: String,
    val messageText: String,
    val messageType: MessageType = MessageType.TEXT,
    val attachmentUrl: String? = null,
    val attachmentType: String? = null,
    val attachmentDuration: Long? = null, // For voice messages
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val messageStatus: MessageStatus = MessageStatus.SENDING,
    // Keep for backward compatibility during migration
    @Deprecated("Use senderFirebaseUid instead") val senderId: Long = 0
)

enum class MessageType {
    TEXT, IMAGE, VOICE, FILE
}

enum class MessageStatus {
    SENDING, SENT, DELIVERED, SEEN, READ
}

// ==================== CONVERSATION MODEL ====================
@Entity(tableName = "conversations")
data class Conversation(
    @PrimaryKey
    val channelId: String,
    val participant1FirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val participant1Username: String,
    val participant2FirebaseUid: String, // 🔑 Changed from Long to Firebase UID
    val participant2Username: String,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderFirebaseUid: String? = null, // 🔑 Changed from Long to Firebase UID
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    // Keep for backward compatibility during migration
    @Deprecated("Use participant1FirebaseUid instead") val participant1Id: Long = 0,
    @Deprecated("Use participant2FirebaseUid instead") val participant2Id: Long = 0,
    @Deprecated("Use lastMessageSenderFirebaseUid instead") val lastMessageSenderId: Long? = null
)

// ==================== SOCIAL FEATURES MODELS ====================

@Entity(tableName = "user_follows")
data class UserFollow(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val followerFirebaseUid: String, // 🔑 Changed from Long to Firebase UID - User who is following
    val followingFirebaseUid: String, // 🔑 Changed from Long to Firebase UID - User being followed
    val followedAt: Long = System.currentTimeMillis(),
    // Keep for backward compatibility during migration
    @Deprecated("Use followerFirebaseUid instead") val followerId: Long = 0,
    @Deprecated("Use followingFirebaseUid instead") val followingId: Long = 0
)

@Entity(tableName = "user_friends")
data class UserFriend(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userFirebaseUid: String, // 🔑 Changed from Long to Firebase UID - First user in the friendship
    val friendFirebaseUid: String, // 🔑 Changed from Long to Firebase UID - Second user in the friendship
    val status: FriendStatus = FriendStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val acceptedAt: Long? = null,
    // Keep for backward compatibility during migration
    @Deprecated("Use userFirebaseUid instead") val userId: Long = 0,
    @Deprecated("Use friendFirebaseUid instead") val friendId: Long = 0
)

enum class FriendStatus {
    PENDING, ACCEPTED, BLOCKED
}

// UI State for ViewModels
data class UiState<T>(
    val isLoading: Boolean = false,
    val data: T? = null,
    val error: String? = null
)