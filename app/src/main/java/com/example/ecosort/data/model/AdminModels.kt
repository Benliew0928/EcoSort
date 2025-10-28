package com.example.ecosort.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

// ==================== ADMIN MODELS ====================

@Entity(tableName = "admins")
data class Admin(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val username: String,
    val email: String,
    val passwordHash: String,
    val profileImageUrl: String? = null,
    val bio: String? = null,
    val location: String? = null,
    val itemsRecycled: Int = 0,
    val totalPoints: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastLogin: Long = System.currentTimeMillis(),
    val isActive: Boolean = true,
    val permissions: String = "FULL_ACCESS" // JSON string for future permission system
) : java.io.Serializable

// Admin session for tracking admin login state
data class AdminSession(
    val adminId: Long,
    val username: String,
    val email: String,
    val loginTime: Long = System.currentTimeMillis()
)

// Admin action log for audit trail
@Entity(tableName = "admin_actions")
data class AdminAction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val adminId: Long,
    val action: String, // e.g., "SUSPEND_USER", "DELETE_USER", "CHANGE_PASSKEY"
    val targetUserId: Long? = null, // If action affects a specific user
    val details: String? = null, // Additional details about the action
    val timestamp: Long = System.currentTimeMillis()
) : java.io.Serializable
