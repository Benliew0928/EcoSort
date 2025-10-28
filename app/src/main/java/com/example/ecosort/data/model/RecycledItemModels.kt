package com.example.ecosort.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

// ==================== RECYCLED ITEM MODELS ====================

@Entity(tableName = "recycled_items")
data class RecycledItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long, // Foreign key to user
    val itemName: String,
    val itemType: String, // e.g., "Plastic", "Paper", "Metal", "Glass", "Organic"
    val pointsEarned: Int,
    val recycledDate: Long,
    val imageUrl: String? = null,
    val notes: String? = null,
    val weight: Double? = null, // Weight in kg (optional)
    val location: String? = null, // Where it was recycled (optional)
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) : Serializable

// Statistics summary for recycled items
data class RecycledItemStats(
    val totalItems: Int,
    val totalPoints: Int,
    val itemsThisMonth: Int,
    val itemsThisWeek: Int,
    val itemsToday: Int,
    val mostRecycledType: String?,
    val lastRecycledDate: Long?
) : Serializable

// UI state for RecycledItemActivity
data class RecycledItemUiState(
    val isLoading: Boolean = false,
    val items: List<RecycledItem> = emptyList(),
    val stats: RecycledItemStats? = null,
    val errorMessage: String? = null
)
