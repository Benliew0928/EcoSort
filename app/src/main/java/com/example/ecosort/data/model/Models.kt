package com.example.ecosort.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

// ==================== USER MODEL ====================
@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val email: String,
    val passwordHash: String,
    val userType: UserType,
    val createdAt: Long = System.currentTimeMillis(),
    val itemsRecycled: Int = 0,
    val totalEarnings: Double = 0.0,
    val profileImageUrl: String? = null
)

enum class UserType {
    USER, ADMIN
}

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

// ==================== MARKETPLACE ITEM MODEL ====================
@Entity(tableName = "marketplace_items")
data class MarketplaceItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sellerId: Long,
    val sellerName: String,
    val title: String,
    val description: String,
    val price: Double,
    val category: WasteCategory,
    val condition: ItemCondition,
    val imageUrls: List<String>,
    val postedAt: Long = System.currentTimeMillis(),
    val status: ItemStatus = ItemStatus.AVAILABLE,
    val views: Int = 0
)

enum class ItemCondition {
    NEW,
    LIKE_NEW,
    GOOD,
    FAIR,
    POOR
}

enum class ItemStatus {
    AVAILABLE,
    SOLD,
    RESERVED,
    REMOVED
}

// ==================== COMMUNITY POST MODEL ====================
@Entity(tableName = "community_posts")
data class CommunityPost(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val authorId: Long,
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
    val status: PostStatus = PostStatus.PUBLISHED
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
    val postId: Long,
    val authorId: Long,
    val authorName: String,
    val authorAvatar: String? = null,
    val content: String,
    val parentCommentId: Long? = null, // For replies
    val postedAt: Long = System.currentTimeMillis(),
    val likesCount: Int = 0,
    val isLikedByUser: Boolean = false
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
    val senderId: Long,
    val senderUsername: String,
    val messageText: String,
    val messageType: MessageType = MessageType.TEXT,
    val attachmentUrl: String? = null,
    val attachmentType: String? = null,
    val attachmentDuration: Long? = null, // For voice messages
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val messageStatus: MessageStatus = MessageStatus.SENDING
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
    val participant1Id: Long,
    val participant1Username: String,
    val participant2Id: Long,
    val participant2Username: String,
    val lastMessageText: String? = null,
    val lastMessageTimestamp: Long = 0L,
    val lastMessageSenderId: Long? = null,
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

// UI State for ViewModels
data class UiState<T>(
    val isLoading: Boolean = false,
    val data: T? = null,
    val error: String? = null
)