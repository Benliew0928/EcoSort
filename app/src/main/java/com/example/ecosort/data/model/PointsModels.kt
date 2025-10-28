package com.example.ecosort.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

// ==================== POINTS MODELS ====================

@Entity(tableName = "user_points")
data class UserPoints(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long, // Foreign key to user
    val totalPoints: Int = 0,
    val pointsEarned: Int = 0, // Points earned from recycling
    val pointsSpent: Int = 0, // Points spent on rewards
    val lastUpdated: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

// Points transaction history
@Entity(tableName = "points_transactions")
data class PointsTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val userId: Long,
    val amount: Int, // Positive for earned, negative for spent
    val type: PointsTransactionType,
    val description: String,
    val source: String, // e.g., "recycling", "reward_purchase"
    val timestamp: Long = System.currentTimeMillis()
) : Serializable

enum class PointsTransactionType {
    EARNED, SPENT, BONUS, REFUND
}

// UI state for PointsActivity
data class PointsUiState(
    val isLoading: Boolean = false,
    val totalPoints: Int = 0,
    val pointsEarned: Int = 0,
    val pointsSpent: Int = 0,
    val transactions: List<PointsTransaction> = emptyList(),
    val errorMessage: String? = null
)
