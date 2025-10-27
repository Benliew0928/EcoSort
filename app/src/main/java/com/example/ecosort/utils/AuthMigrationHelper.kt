package com.example.ecosort.utils

import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.repository.UserRepository
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await

/**
 * Helper class to migrate existing users to the new Firebase-only authentication system
 */
object AuthMigrationHelper {
    
    private val firebaseAuth = FirebaseAuth.getInstance()
    
    /**
     * Migrate an existing user to Firebase Authentication
     * This requires the user to re-enter their password for security
     */
    suspend fun migrateUserToFirebase(
        user: User,
        password: String,
        userRepository: UserRepository
    ): Result<User> {
        return try {
            // 1. Create user in Firebase Authentication with the provided password
            val authResult = firebaseAuth.createUserWithEmailAndPassword(user.email, password).await()
            val firebaseUid = authResult.user?.uid ?: throw Exception("Firebase UID not found")
            
            // 2. Update the local user with Firebase UID
            val updatedUser = user.copy(firebaseUid = firebaseUid, passwordHash = "")
            val updateResult = userRepository.updateUser(updatedUser)
            
            when (updateResult) {
                is Result.Success -> {
                    android.util.Log.d("AuthMigrationHelper", "User ${user.username} successfully migrated to Firebase")
                    Result.Success(updatedUser)
                }
                is Result.Error -> {
                    android.util.Log.e("AuthMigrationHelper", "Failed to update user in local database")
                    Result.Error(Exception("Failed to update user in local database"))
                }
                else -> {
                    android.util.Log.e("AuthMigrationHelper", "Unexpected result type")
                    Result.Error(Exception("Unexpected result type"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthMigrationHelper", "Failed to migrate user ${user.username}: ${e.message}", e)
            Result.Error(e)
        }
    }
    
    /**
     * Check if a user needs migration (has no Firebase UID)
     */
    fun needsMigration(user: User): Boolean {
        return user.firebaseUid.isNullOrBlank()
    }
    
    /**
     * Get migration message for user
     */
    fun getMigrationMessage(username: String): String {
        return "User $username needs to be migrated to the new authentication system. " +
                "Please re-enter your password to complete the migration."
    }
}
