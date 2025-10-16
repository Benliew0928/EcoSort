package com.example.ecosort.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

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