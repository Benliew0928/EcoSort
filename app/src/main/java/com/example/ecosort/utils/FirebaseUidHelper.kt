package com.example.ecosort.utils

import android.util.Log
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.Result

/**
 * Helper class to ensure consistent Firebase UID usage across the app
 * This prevents duplicate users and data inconsistency issues
 */
object FirebaseUidHelper {
    
    private const val TAG = "FirebaseUidHelper"
    
    /**
     * Safely extract Firebase UID from user object
     * Throws exception if UID is null or empty to prevent data corruption
     */
    fun getFirebaseUid(user: User): String {
        val firebaseUid = user.firebaseUid
        if (firebaseUid.isNullOrEmpty()) {
            val error = "Firebase UID is null or empty for user: ${user.username} (ID: ${user.id})"
            Log.e(TAG, error)
            throw IllegalStateException(error)
        }
        return firebaseUid
    }
    
    /**
     * Safely extract Firebase UID from user result
     * Handles all result types and provides proper error messages
     */
    fun getFirebaseUidFromResult(userResult: Result<User>): String {
        return when (userResult) {
            is Result.Success -> getFirebaseUid(userResult.data)
            is Result.Error -> {
                Log.e(TAG, "Failed to get user: ${userResult.exception.message}")
                throw userResult.exception
            }
            is Result.Loading -> {
                val error = "User data is still loading"
                Log.e(TAG, error)
                throw IllegalStateException(error)
            }
        }
    }
    
    /**
     * Validate that a Firebase UID is properly formatted
     * Firebase UIDs are typically 28 characters long and contain alphanumeric characters
     */
    fun isValidFirebaseUid(uid: String?): Boolean {
        return !uid.isNullOrEmpty() && uid.length >= 20 && uid.matches(Regex("[a-zA-Z0-9]+"))
    }
    
    /**
     * Log user identification for debugging
     */
    fun logUserIdentification(user: User) {
        Log.d(TAG, "User identified - Local ID: ${user.id}, Firebase UID: ${user.firebaseUid}, Username: ${user.username}")
    }
}

