package com.example.ecosort.data.repository

import com.example.ecosort.data.local.AdminDao
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.Admin
import com.example.ecosort.data.model.AdminAction
import com.example.ecosort.data.model.AdminSession
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.FirebaseAuthService
import com.example.ecosort.utils.SecurityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class AdminRepository @Inject constructor(
    private val database: EcoSortDatabase,
    private val firestoreService: FirestoreService,
    private val firebaseAuthService: FirebaseAuthService
) {
    private val adminDao: AdminDao = database.adminDao()

    // ==================== ADMIN AUTHENTICATION ====================
    
    suspend fun authenticateAdmin(username: String, password: String, context: Context): Result<AdminSession> {
        return try {
            withContext(Dispatchers.IO) {
                // First, try Firebase authentication (primary method)
                val firebaseResult = firebaseAuthService.authenticateUser(username, password, context)
                
                if (firebaseResult is com.example.ecosort.data.model.Result.Success) {
                    val firebaseSession = firebaseResult.data
                    
                    // Check if this is an admin user
                    if (firebaseSession.userType != com.example.ecosort.data.model.UserType.ADMIN) {
                        return@withContext Result.Error(Exception("User is not an admin"))
                    }
                    
                    // Get Firebase user data to extract Firebase UID
                    val userResult = firebaseAuthService.getUserFromFirebase(username, context)
                    val firebaseUser = if (userResult is com.example.ecosort.data.model.Result.Success) userResult.data else null
                    val firebaseUid = firebaseUser?.firebaseUid
                    
                    // Get or create local admin
                    val localAdmin = adminDao.getAdminByUsername(username)
                    val adminId = if (localAdmin != null) {
                        // Update existing local admin with Firebase UID if missing
                        if (localAdmin.firebaseUid.isNullOrEmpty() && firebaseUid != null) {
                            adminDao.updateAdmin(localAdmin.copy(firebaseUid = firebaseUid))
                            android.util.Log.d("AdminRepository", "Updated admin with Firebase UID: $firebaseUid")
                        }
                        adminDao.updateLastLogin(localAdmin.id, System.currentTimeMillis())
                        localAdmin.id
                    } else {
                        // Create local admin from Firebase data
                        if (firebaseUser != null) {
                            val admin = Admin(
                                firebaseUid = firebaseUser.firebaseUid,
                                username = firebaseUser.username,
                                email = firebaseUser.email,
                                passwordHash = firebaseUser.passwordHash,
                                profileImageUrl = null
                            )
                            adminDao.insertAdmin(admin)
                        } else {
                            // Fallback: create minimal admin
                            val fallbackAdmin = Admin(
                                firebaseUid = null,
                                username = username,
                                email = "",
                                passwordHash = "",
                                profileImageUrl = null
                            )
                            adminDao.insertAdmin(fallbackAdmin)
                        }
                    }
                    
                    val session = AdminSession(
                        adminId = adminId,
                        username = username,
                        email = firebaseSession.username // Use username as email fallback
                    )
                    
                    android.util.Log.d("AdminRepository", "Admin authenticated successfully via Firebase: $username")
                    return@withContext Result.Success(session)
                } else {
                    // Firebase authentication failed, try local fallback
                    android.util.Log.w("AdminRepository", "Firebase authentication failed, trying local fallback: ${(firebaseResult as com.example.ecosort.data.model.Result.Error).exception.message}")
                    
                    val admin = adminDao.getAdminByUsername(username)
                    android.util.Log.d("AdminRepository", "Local admin lookup for '$username': ${if (admin != null) "Found (ID: ${admin.id}, Active: ${admin.isActive})" else "Not found"}")
                    
                    if (admin != null && admin.isActive) {
                        val passwordValid = SecurityManager.verifyPassword(password, admin.passwordHash)
                        android.util.Log.d("AdminRepository", "Password verification for '$username': ${if (passwordValid) "Valid" else "Invalid"}")
                        
                        if (passwordValid) {
                            // Update last login
                            adminDao.updateLastLogin(admin.id, System.currentTimeMillis())
                            
                            val session = AdminSession(
                                adminId = admin.id,
                                username = admin.username,
                                email = admin.email
                            )
                            
                            // Try to migrate admin to Firebase in background
                            try {
                                val user = com.example.ecosort.data.model.User(
                                    username = admin.username,
                                    email = admin.email,
                                    passwordHash = admin.passwordHash,
                                    userType = com.example.ecosort.data.model.UserType.ADMIN
                                )
                                firebaseAuthService.migrateUserToFirebase(user, context)
                                android.util.Log.d("AdminRepository", "Admin migrated to Firebase: $username")
                            } catch (e: Exception) {
                                android.util.Log.w("AdminRepository", "Failed to migrate admin to Firebase: ${e.message}")
                            }
                            
                            android.util.Log.d("AdminRepository", "Admin authenticated via local fallback: $username")
                            return@withContext Result.Success(session)
                        } else {
                            android.util.Log.w("AdminRepository", "Invalid password for admin: $username")
                            return@withContext Result.Error(Exception("Invalid admin credentials"))
                        }
                    } else {
                        android.util.Log.w("AdminRepository", "Admin not found or inactive: $username")
                        return@withContext Result.Error(Exception("Invalid admin credentials"))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Failed to authenticate admin", e)
            Result.Error(e)
        }
    }

    suspend fun createAdmin(username: String, email: String, password: String, adminPasskey: String, context: Context): Result<AdminSession> {
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

                // Register admin with Firebase authentication FIRST to get Firebase UID
                var firebaseUid: String? = null
                try {
                    val firebaseResult = firebaseAuthService.registerUser(username, email, password, com.example.ecosort.data.model.UserType.ADMIN, context)
                    if (firebaseResult is com.example.ecosort.data.model.Result.Success) {
                        firebaseUid = firebaseResult.data.firebaseUid
                        android.util.Log.d("AdminRepository", "Admin registered with Firebase authentication: ${username}, UID: $firebaseUid")
                    } else {
                        android.util.Log.w("AdminRepository", "Failed to register admin with Firebase: ${(firebaseResult as com.example.ecosort.data.model.Result.Error).exception.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AdminRepository", "Failed to register admin with Firebase: ${e.message}")
                    // Continue with null firebaseUid - admin will still work locally
                }
                
                // Create new admin with Firebase UID
                val passwordHash = SecurityManager.hashPassword(password)
                val admin = Admin(
                    firebaseUid = firebaseUid, // Store Firebase UID for cross-device sync
                    username = username,
                    email = email,
                    passwordHash = passwordHash,
                    profileImageUrl = null
                )

                val adminId = adminDao.insertAdmin(admin)
                val createdAdmin = admin.copy(id = adminId)
                
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

    // ==================== ADMIN PROFILE MANAGEMENT ====================
    
    suspend fun updateAdminProfileImage(adminId: Long, imageUrl: String?): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                adminDao.updateProfileImage(adminId, imageUrl)
                
                // Sync to Firebase
                try {
                    val admin = adminDao.getAdminById(adminId)
                    if (admin != null) {
                        syncAdminProfileToFirebase(admin)
                    }
                } catch (e: Exception) {
                    android.util.Log.w("AdminRepository", "Failed to sync admin profile image to Firebase: ${e.message}")
                    // Don't fail the operation if Firebase sync fails
                }
                
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Error updating admin profile image", e)
            Result.Error(e)
        }
    }
    
    suspend fun getAdminProfileImage(adminId: Long): Result<String?> {
        return try {
            withContext(Dispatchers.IO) {
                val admin = adminDao.getAdminById(adminId)
                if (admin != null) {
                    Result.Success(admin.profileImageUrl)
                } else {
                    Result.Error(Exception("Admin not found"))
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Error getting admin profile image", e)
            Result.Error(e)
        }
    }
    
    suspend fun getAdminById(adminId: Long): Result<com.example.ecosort.data.model.Admin?> {
        return try {
            withContext(Dispatchers.IO) {
                val admin = adminDao.getAdminById(adminId)
                Result.Success(admin)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Error getting admin by ID", e)
            Result.Error(e)
        }
    }
    
    suspend fun updateAdminBio(adminId: Long, bio: String?): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                adminDao.updateBio(adminId, bio)
                
                // Get the updated admin and sync to Firebase
                val admin = adminDao.getAdminById(adminId)
                if (admin != null) {
                    try {
                        updateAdminInFirebase(admin)
                        android.util.Log.d("AdminRepository", "Admin bio updated in Firebase: ${admin.username} (adminId: ${adminId})")
                    } catch (e: Exception) {
                        android.util.Log.w("AdminRepository", "Failed to update admin bio in Firebase: ${e.message}")
                        // Don't fail the operation if Firebase sync fails
                    }
                }
                
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Error updating admin bio", e)
            Result.Error(e)
        }
    }
    
    suspend fun updateAdminLocation(adminId: Long, location: String?): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                adminDao.updateLocation(adminId, location)
                
                // Get the updated admin and sync to Firebase
                val admin = adminDao.getAdminById(adminId)
                if (admin != null) {
                    try {
                        updateAdminInFirebase(admin)
                        android.util.Log.d("AdminRepository", "Admin location updated in Firebase: ${admin.username} (adminId: ${adminId})")
                    } catch (e: Exception) {
                        android.util.Log.w("AdminRepository", "Failed to update admin location in Firebase: ${e.message}")
                        // Don't fail the operation if Firebase sync fails
                    }
                }
                
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("AdminRepository", "Error updating admin location", e)
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

    suspend fun syncAllUsersFromFirebase(): Result<Int> {
        return try {
            val result = firestoreService.getAllUserProfiles()
            if (result.isSuccess) {
                val firebaseUsers = result.getOrNull() ?: emptyList()
                var syncedCount = 0
                
                for (userData in firebaseUsers) {
                    try {
                        val username = userData["username"] as? String ?: continue
                        val syncResult = syncUserFromFirebase(username)
                        if (syncResult is Result.Success) {
                            syncedCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("AdminRepository", "Failed to sync user: ${e.message}")
                    }
                }
                
                android.util.Log.d("AdminRepository", "Synced $syncedCount users from Firebase")
                Result.Success(syncedCount)
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get users from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private suspend fun syncUserFromFirebase(username: String): Result<Unit> {
        return try {
            val result = firestoreService.getUserProfileByUsername(username)
            if (result.isSuccess) {
                val userData = result.getOrNull()
                if (userData != null) {
                    val user = convertFirebaseUserToUser(userData)
                    val existingUser = database.userDao().getUserByUsername(username)
                    
                    if (existingUser != null) {
                        // Update existing user
                        database.userDao().updateUser(user)
                    } else {
                        // Insert new user
                        database.userDao().insertUser(user)
                    }
                    Result.Success(Unit)
                } else {
                    Result.Error(Exception("User data is null"))
                }
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get user from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    private fun convertFirebaseUserToUser(userData: HashMap<String, Any>): com.example.ecosort.data.model.User {
        return com.example.ecosort.data.model.User(
            id = (userData["id"] as? Number)?.toLong() ?: 0L,
            firebaseUid = userData["firebaseUid"] as? String,
            username = userData["username"] as? String ?: "",
            email = userData["email"] as? String ?: "",
            passwordHash = "", // Don't sync password hash
            userType = com.example.ecosort.data.model.UserType.valueOf(userData["userType"] as? String ?: "USER"),
            createdAt = (userData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            itemsRecycled = (userData["itemsRecycled"] as? Number)?.toInt() ?: 0,
            totalPoints = (userData["totalPoints"] as? Number)?.toInt() ?: 0,
            profileImageUrl = userData["profileImageUrl"] as? String,
            bio = userData["bio"] as? String,
            location = userData["location"] as? String,
            joinDate = (userData["joinDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            lastActive = (userData["lastActive"] as? Number)?.toLong() ?: System.currentTimeMillis(),
            profileCompletion = (userData["profileCompletion"] as? Number)?.toInt() ?: 0,
            privacySettings = userData["privacySettings"] as? String,
            achievements = userData["achievements"] as? String,
            socialLinks = userData["socialLinks"] as? String,
            preferences = userData["preferences"] as? String
        )
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

    // ==================== COMMUNITY MANAGEMENT ====================

    suspend fun deleteCommunityPost(adminId: Long, postId: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                // First, log the action
                logAdminAction(adminId, "DELETE_COMMUNITY_POST", null, "Deleted community post: $postId")
                
                // Then delete the post from the database
                val communityPostDao = database.communityPostDao()
                val communityCommentDao = database.communityCommentDao()
                val postIdLong = postId.toLongOrNull() ?: 0L
                val post = communityPostDao.getPostById(postIdLong)
                if (post != null) {
                    // Delete all comments for this post first
                    communityCommentDao.deleteCommentsForPost(postIdLong)
                    // Then delete the post
                    communityPostDao.deletePost(postIdLong)
                    Result.Success(Unit)
                } else {
                    Result.Error(Exception("Community post not found"))
                }
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun deleteCommunityComment(adminId: Long, commentId: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                // First, log the action
                logAdminAction(adminId, "DELETE_COMMUNITY_COMMENT", null, "Deleted community comment: $commentId")
                
                // Then delete the comment from the database
                val communityCommentDao = database.communityCommentDao()
                val commentIdLong = commentId.toLongOrNull() ?: 0L
                val comment = communityCommentDao.getCommentById(commentIdLong)
                if (comment != null) {
                    communityCommentDao.deleteComment(commentIdLong)
                    Result.Success(Unit)
                } else {
                    Result.Error(Exception("Community comment not found"))
                }
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
     * Sync admin profile to Firebase (alias for syncAdminToFirebase)
     */
    private suspend fun syncAdminProfileToFirebase(admin: Admin) {
        syncAdminToFirebase(admin)
    }
    
    /**
     * Sync admin profile to Firebase
     */
    private suspend fun syncAdminToFirebase(admin: Admin) {
        val adminData = hashMapOf<String, Any>(
            "id" to admin.id,
            "username" to admin.username,
            "email" to admin.email,
            "userType" to "ADMIN", // Mark as admin type
            "profileImageUrl" to (admin.profileImageUrl ?: ""),
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
            "profileImageUrl" to (admin.profileImageUrl ?: ""),
            "bio" to (admin.bio ?: ""),
            "location" to (admin.location ?: ""),
            "itemsRecycled" to admin.itemsRecycled,
            "totalPoints" to admin.totalPoints,
            "createdAt" to admin.createdAt,
            "lastLogin" to admin.lastLogin,
            "isActive" to admin.isActive,
            "permissions" to admin.permissions
        )

        android.util.Log.d("AdminRepository", "Updating admin in Firebase: ${admin.username}, bio: '${admin.bio}', location: '${admin.location}'")

        val result = firestoreService.updateUserProfile(admin.id.toString(), adminData)
        if (result.isFailure) {
            android.util.Log.w("AdminRepository", "Failed to update admin in Firebase: ${result.exceptionOrNull()?.message}")
        } else {
            android.util.Log.d("AdminRepository", "Successfully updated admin in Firebase: ${admin.username}")
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
                        profileImageUrl = userData["profileImageUrl"] as? String,
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
