package com.example.ecosort.data.repository

import com.example.ecosort.data.local.AdminDao
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.Admin
import com.example.ecosort.data.model.AdminAction
import com.example.ecosort.data.model.AdminSession
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.utils.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepository @Inject constructor(
    private val database: EcoSortDatabase,
    private val firestoreService: FirestoreService
) {
    private val adminDao: AdminDao = database.adminDao()

    // ==================== ADMIN AUTHENTICATION ====================
    
    suspend fun authenticateAdmin(username: String, password: String): Result<AdminSession> {
        return try {
            withContext(Dispatchers.IO) {
                val admin = adminDao.getAdminByUsername(username)
                if (admin != null && admin.isActive && SecurityManager.verifyPassword(password, admin.passwordHash)) {
                    // Update last login
                    adminDao.updateLastLogin(admin.id, System.currentTimeMillis())
                    
                    val session = AdminSession(
                        adminId = admin.id,
                        username = admin.username,
                        email = admin.email
                    )
                    Result.Success(session)
                } else {
                    Result.Error(Exception("Invalid admin credentials"))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun createAdmin(username: String, email: String, password: String, adminPasskey: String): Result<AdminSession> {
        return try {
            withContext(Dispatchers.IO) {
                // Verify admin passkey first
                if (!SecurityManager.verifyAdminPasskey(adminPasskey)) {
                    return@withContext Result.Error(Exception("Invalid admin passkey"))
                }

                // Check if admin already exists
                val existingAdmin = adminDao.getAdminByUsername(username)
                if (existingAdmin != null) {
                    return@withContext Result.Error(Exception("Admin username already exists"))
                }

                val existingEmail = adminDao.getAdminByEmail(email)
                if (existingEmail != null) {
                    return@withContext Result.Error(Exception("Admin email already exists"))
                }

                // Create new admin
                val passwordHash = SecurityManager.hashPassword(password)
                val admin = Admin(
                    username = username,
                    email = email,
                    passwordHash = passwordHash
                )

                val adminId = adminDao.insertAdmin(admin)
                val createdAdmin = admin.copy(id = adminId)
                
                // Sync admin profile to Firebase
                try {
                    syncAdminToFirebase(createdAdmin)
                    android.util.Log.d("AdminRepository", "Admin profile synced to Firebase: ${createdAdmin.username}")
                } catch (e: Exception) {
                    android.util.Log.w("AdminRepository", "Failed to sync admin to Firebase: ${e.message}")
                    // Don't fail admin creation if Firebase sync fails
                }
                
                // Log admin creation
                adminDao.logAdminAction(AdminAction(
                    adminId = adminId,
                    action = "CREATE_ADMIN",
                    details = "New admin account created: $username"
                ))

                val session = AdminSession(
                    adminId = adminId,
                    username = admin.username,
                    email = admin.email
                )
                Result.Success(session)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== ADMIN MANAGEMENT ====================
    
    suspend fun getAllAdmins(): Result<List<Admin>> {
        return try {
            withContext(Dispatchers.IO) {
                val admins = adminDao.getAllActiveAdmins()
                Result.Success(admins)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateAdminStatus(adminId: Long, isActive: Boolean, currentAdminId: Long): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                adminDao.updateAdminStatus(adminId, isActive)
                
                // Log the action
                adminDao.logAdminAction(AdminAction(
                    adminId = currentAdminId,
                    action = if (isActive) "ACTIVATE_ADMIN" else "DEACTIVATE_ADMIN",
                    targetUserId = adminId,
                    details = "Admin status changed to ${if (isActive) "active" else "inactive"}"
                ))
                
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== USER MANAGEMENT (READ-ONLY) ====================
    
    suspend fun getAllUsers(): Result<List<com.example.ecosort.data.model.User>> {
        return try {
            withContext(Dispatchers.IO) {
                val users = database.userDao().getAllUsers()
                Result.Success(users)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<com.example.ecosort.data.model.User>> {
        return try {
            withContext(Dispatchers.IO) {
                val users = database.userDao().searchUsers(query, 0L) // Pass currentUserId as 0 for admin search
                Result.Success(users)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUserById(userId: Long): Result<com.example.ecosort.data.model.User?> {
        return try {
            withContext(Dispatchers.IO) {
                val user = database.userDao().getUserById(userId)
                Result.Success(user)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== ADMIN ACTION LOGGING ====================
    
    suspend fun logAdminAction(adminId: Long, action: String, targetUserId: Long? = null, details: String? = null) {
        try {
            withContext(Dispatchers.IO) {
                adminDao.logAdminAction(AdminAction(
                    adminId = adminId,
                    action = action,
                    targetUserId = targetUserId,
                    details = details
                ))
            }
        } catch (e: Exception) {
            // Log error but don't fail the operation
            android.util.Log.e("AdminRepository", "Failed to log admin action: ${e.message}")
        }
    }

    suspend fun getAdminActions(adminId: Long, limit: Int = 50): Result<List<AdminAction>> {
        return try {
            withContext(Dispatchers.IO) {
                val actions = adminDao.getAdminActions(adminId, limit)
                Result.Success(actions)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getAllAdminActions(limit: Int = 100): Result<List<AdminAction>> {
        return try {
            withContext(Dispatchers.IO) {
                val actions = adminDao.getAllAdminActions(limit)
                Result.Success(actions)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== USER & ADMIN DELETION ====================

    suspend fun deleteUser(adminId: Long, userId: Long): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                // First, log the action
                logAdminAction(adminId, "DELETE_USER", userId)
                
                // Then actually delete the user from the database
                val userDao = database.userDao()
                val user = userDao.getUserById(userId)
                if (user != null) {
                    userDao.deleteUser(user)
                    Result.Success(Unit)
                } else {
                    Result.Error(Exception("User not found"))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteAdmin(adminId: Long, targetAdminId: Long): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                // First, log the action
                logAdminAction(adminId, "DELETE_ADMIN", targetAdminId)
                
                // Then actually delete the admin from the database
                val admin = adminDao.getAdminById(targetAdminId)
                if (admin != null) {
                    adminDao.deleteAdmin(admin)
                    Result.Success(Unit)
                } else {
                    Result.Error(Exception("Admin not found"))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== FIREBASE SYNC ====================

    /**
     * Sync admin profile to Firebase
     */
    private suspend fun syncAdminToFirebase(admin: Admin) {
        val adminData = hashMapOf<String, Any>(
            "id" to admin.id,
            "username" to admin.username,
            "email" to admin.email,
            "userType" to "ADMIN", // Mark as admin type
            "createdAt" to admin.createdAt,
            "lastLogin" to admin.lastLogin,
            "isActive" to admin.isActive,
            "permissions" to admin.permissions
        )

        val result = firestoreService.saveUserProfile(adminData)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to sync admin to Firebase")
        }
    }

    /**
     * Update admin profile in Firebase
     */
    private suspend fun updateAdminInFirebase(admin: Admin) {
        val adminData = hashMapOf<String, Any>(
            "id" to admin.id,
            "username" to admin.username,
            "email" to admin.email,
            "userType" to "ADMIN", // Mark as admin type
            "createdAt" to admin.createdAt,
            "lastLogin" to admin.lastLogin,
            "isActive" to admin.isActive,
            "permissions" to admin.permissions
        )

        val result = firestoreService.updateUserProfile(admin.id.toString(), adminData)
        if (result.isFailure) {
            android.util.Log.w("AdminRepository", "Failed to update admin in Firebase: ${result.exceptionOrNull()?.message}")
        }
    }

    /**
     * Get admin profile from Firebase by username
     */
    suspend fun getAdminProfileFromFirebase(username: String): Result<Admin?> {
        return try {
            val result = firestoreService.getUserProfileByUsername(username)
            if (result.isSuccess) {
                val userData = result.getOrNull()
                if (userData != null && userData["userType"] == "ADMIN") {
                    val admin = Admin(
                        id = (userData["id"] as? Number)?.toLong() ?: 0L,
                        username = userData["username"] as? String ?: "",
                        email = userData["email"] as? String ?: "",
                        passwordHash = "", // Don't sync password hash
                        createdAt = (userData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        lastLogin = (userData["lastLogin"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        isActive = (userData["isActive"] as? Boolean) ?: true,
                        permissions = userData["permissions"] as? String ?: "FULL_ACCESS"
                    )
                    Result.Success(admin)
                } else {
                    Result.Success(null)
                }
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get admin from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Sync admin profile from Firebase to local database
     */
    suspend fun syncAdminFromFirebase(username: String): Result<Admin> {
        return try {
            val firebaseResult = getAdminProfileFromFirebase(username)
            if (firebaseResult is Result.Success) {
                val firebaseAdmin = firebaseResult.data
                if (firebaseAdmin != null) {
                    // Check if admin already exists locally
                    val localAdmin = adminDao.getAdminByUsername(username)
                    if (localAdmin == null) {
                        // Insert new admin to local database
                        val adminId = adminDao.insertAdmin(firebaseAdmin)
                        val syncedAdmin = firebaseAdmin.copy(id = adminId)
                        android.util.Log.d("AdminRepository", "Synced admin from Firebase to local: $username")
                        Result.Success(syncedAdmin)
                    } else {
                        // Update existing admin
                        adminDao.updateAdmin(firebaseAdmin.copy(id = localAdmin.id))
                        android.util.Log.d("AdminRepository", "Updated local admin from Firebase: $username")
                        Result.Success(firebaseAdmin.copy(id = localAdmin.id))
                    }
                } else {
                    Result.Error(Exception("Admin not found in Firebase"))
                }
            } else {
                Result.Error((firebaseResult as Result.Error).exception)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Sync all admin profiles from Firebase to local database
     */
    suspend fun syncAllAdminsFromFirebase(): Result<Int> {
        return try {
            val result = firestoreService.getAllUserProfiles()
            if (result.isSuccess) {
                val firebaseUsers = result.getOrNull() ?: emptyList()
                var syncedCount = 0
                
                for (userData in firebaseUsers) {
                    try {
                        val userType = userData["userType"] as? String
                        if (userType == "ADMIN") {
                            val username = userData["username"] as? String ?: continue
                            val syncResult = syncAdminFromFirebase(username)
                            if (syncResult is Result.Success) {
                                syncedCount++
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AdminRepository", "Failed to sync admin: ${e.message}")
                    }
                }
                
                android.util.Log.d("AdminRepository", "Synced $syncedCount admins from Firebase")
                Result.Success(syncedCount)
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get users from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
