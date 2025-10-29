package com.example.ecosort.data.repository

import com.example.ecosort.data.local.UserDao
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.PrivacySettings
import com.example.ecosort.data.model.Achievement
import com.example.ecosort.data.model.SocialLinks
import com.example.ecosort.data.model.UserPreferences
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.FirebaseAuthService
import com.example.ecosort.utils.SecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context

@Singleton
class UserRepository @Inject constructor(
    private val userDao: UserDao,
    private val database: EcoSortDatabase,
    private val preferencesManager: UserPreferencesManager,
    private val securityManager: SecurityManager,
    private val firestoreService: FirestoreService,
    private val firebaseAuthService: FirebaseAuthService
) {

    // ==================== USER SESSION ====================

    val userSession: Flow<UserSession?> = preferencesManager.userSession

    suspend fun isLoggedIn(): Boolean {
        return preferencesManager.isLoggedIn.first()
    }

    suspend fun getCurrentUser(): Result<User> {
        return try {
            val session = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))

            // Handle admin IDs (negative) vs regular user IDs (positive)
            val user = if (session.userId < 0 || session.userType == UserType.ADMIN) {
                // Admin account - convert negative session ID back to positive admin ID
                val adminId = kotlin.math.abs(session.userId)
                val adminDao = database.adminDao()
                val admin = adminDao.getAdminById(adminId)
                
                if (admin != null) {
                    // Convert Admin to User for uniform access
                    User(
                        id = session.userId,  // Keep negative ID for session consistency
                        firebaseUid = admin.firebaseUid,
                        username = admin.username,
                        email = admin.email,
                        passwordHash = admin.passwordHash,
                        userType = UserType.ADMIN,
                        profileImageUrl = admin.profileImageUrl,
                        bio = admin.bio,
                        location = admin.location,
                        itemsRecycled = admin.itemsRecycled,
                        totalPoints = admin.totalPoints,
                        createdAt = admin.createdAt,
                        lastActive = admin.lastLogin
                    )
                } else {
                    return Result.Error(Exception("Admin not found"))
                }
            } else {
                // Regular user account
                userDao.getUserById(session.userId)
                    ?: return Result.Error(Exception("User not found"))
            }

            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== AUTHENTICATION ====================

    suspend fun registerUser(
        username: String,
        email: String,
        password: String,
        userType: UserType,
        context: Context
    ): Result<User> {
        return try {
            // Additional safety check: verify username doesn't exist locally
            val existingLocalUser = userDao.getUserByUsername(username)
            if (existingLocalUser != null) {
                android.util.Log.w("UserRepository", "Username '$username' already exists in local database")
                return Result.Error(Exception("Username already taken. Please choose a different username."))
            }
            
            // First, try to register with Firebase (primary authentication)
            val firebaseResult = firebaseAuthService.registerUser(username, email, password, userType, context)
            
            if (firebaseResult is Result.Success) {
                val firebaseUser = firebaseResult.data
                
                // Check if user already exists locally
                val localUser = userDao.getUserByUsername(username)
                if (localUser == null) {
                    // Insert user to local database
                    val userId = userDao.insertUser(firebaseUser)
                    val createdUser = firebaseUser.copy(id = userId)
                    
                    android.util.Log.d("UserRepository", "User registered successfully with Firebase: $username")
                    return Result.Success(createdUser)
                } else {
                    // Update existing local user
                    userDao.updateUser(firebaseUser.copy(id = localUser.id))
                    android.util.Log.d("UserRepository", "Updated existing local user from Firebase: $username")
                    return Result.Success(firebaseUser.copy(id = localUser.id))
                }
            } else {
                // Firebase registration failed - no local fallback for security
                val error = firebaseResult as Result.Error
                android.util.Log.w("UserRepository", "Firebase registration failed: ${error.exception.message}")
                
                // Provide more specific error messages based on the Firebase error
                val errorMessage = when {
                    error.exception.message?.contains("network", ignoreCase = true) == true -> 
                        "Network error. Please check your internet connection and try again."
                    error.exception.message?.contains("email", ignoreCase = true) == true -> 
                        "Email address is already in use. Please use a different email."
                    error.exception.message?.contains("password", ignoreCase = true) == true -> 
                        "Password is too weak. Please choose a stronger password."
                    error.exception.message?.contains("invalid", ignoreCase = true) == true -> 
                        "Invalid email format. Please enter a valid email address."
                    else -> 
                        "Registration failed: ${error.exception.message ?: "Unknown error occurred"}"
                }
                
                Result.Error(Exception(errorMessage))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to register user", e)
            Result.Error(e)
        }
    }

    suspend fun loginUser(username: String, password: String, context: Context): Result<UserSession> {
        return try {
            android.util.Log.d("UserRepository", "Attempting login for username: $username")
            // Use Firebase authentication as the primary and only method
            val firebaseResult = firebaseAuthService.authenticateUser(username, password, context)
            
            if (firebaseResult is Result.Success) {
                val firebaseSession = firebaseResult.data
                
                // Get or create local user - check by username first, then by firebaseUid
                val localUser = userDao.getUserByUsername(username) ?: 
                    if (!firebaseSession.token.isNullOrBlank()) {
                        userDao.getUserByFirebaseUid(firebaseSession.token)
                    } else {
                        null
                    }
                val userId = if (localUser != null) {
                    // Update existing local user with firebaseUid if missing
                    val updatedUser = localUser.copy(
                        firebaseUid = firebaseSession.token, // Firebase UID is stored in token
                        lastActive = System.currentTimeMillis()
                    )
                    userDao.updateUser(updatedUser)
                    localUser.id
                } else {
                    // Get user data from Firebase and create locally
                    val userResult = firebaseAuthService.getUserFromFirebase(username, context)
                    if (userResult is Result.Success && userResult.data != null) {
                        val firebaseUser = userResult.data
                        val insertedId = userDao.insertUser(firebaseUser)
                        insertedId
                    } else {
                        // Fallback: create minimal user with the session data
                        val fallbackUser = User(
                            firebaseUid = firebaseSession.token, // Store Firebase UID
                            username = username,
                            email = firebaseSession.username, // Use username as email fallback
                            passwordHash = "", // No password hash needed
                            userType = firebaseSession.userType
                        )
                        userDao.insertUser(fallbackUser)
                    }
                }
                
                android.util.Log.d("UserRepository", "Login result - Username: $username, Local User ID: $userId, Firebase UID: ${firebaseSession.token}")
                
                // Create session with local user ID
                val session = firebaseSession.copy(userId = userId)
                preferencesManager.saveUserSession(session)
                
                android.util.Log.d("UserRepository", "User logged in successfully via Firebase: $username")
                return Result.Success(session)
            } else {
                // Firebase authentication failed - no fallback for security
                android.util.Log.w("UserRepository", "Firebase authentication failed: ${(firebaseResult as Result.Error).exception.message}")
                Result.Error(Exception("Authentication failed. Please check your credentials."))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Failed to login user", e)
            Result.Error(e)
        }
    }

    suspend fun checkGoogleUserExists(email: String): Result<User?> {
        return try {
            android.util.Log.d("UserRepository", "🔍 === Checking if user exists (ANY type) ===")
            android.util.Log.d("UserRepository", "📧 Email: $email")
            
            // 🔑 CRITICAL: Check Firebase Firestore for ANY user with this email
            // This ensures cross-device functionality AND prevents duplicate accounts
            val firebaseUser = firebaseAuthService.getUserByEmail(email)
            
            if (firebaseUser != null) {
                val isSocialAccount = !firebaseUser.passwordHash.isEmpty()
                val accountType = if (isSocialAccount) "Social Login" else "Email/Password"
                
                android.util.Log.d("UserRepository", "✅ User found in Firebase!")
                android.util.Log.d("UserRepository", "  - Account Type: $accountType")
                android.util.Log.d("UserRepository", "  - Username: ${firebaseUser.username}")
                android.util.Log.d("UserRepository", "  - Firebase UID: ${firebaseUser.firebaseUid}")
                android.util.Log.d("UserRepository", "  - Social Account ID (passwordHash): ${firebaseUser.passwordHash}")
                android.util.Log.d("UserRepository", "  - Profile Image: ${firebaseUser.profileImageUrl}")
                
                // ⚠️ CRITICAL: Check if this is an email/password account being accessed via social login
                if (!isSocialAccount) {
                    android.util.Log.w("UserRepository", "⚠️ WARNING: This email is registered as Email/Password account!")
                    android.util.Log.w("UserRepository", "  User should sign in with their password, not social login.")
                    android.util.Log.w("UserRepository", "  Alternatively, we could link this social account to existing account...")
                    // For now, return the existing user to prevent duplicate account creation
                }
                
                // Sync Firebase user to local database
                val localUser = userDao.getUserByEmail(email)
                if (localUser == null) {
                    // User exists in Firebase but not locally - sync it
                    android.util.Log.d("UserRepository", "💾 Syncing Firebase user to local database (NEW device)")
                    val userId = userDao.insertUser(firebaseUser)
                    val syncedUser = firebaseUser.copy(id = userId)
                    android.util.Log.d("UserRepository", "✅ User synced to local DB with ID: $userId")
                    Result.Success(syncedUser)
                } else {
                    // User exists locally, update with latest Firebase data
                    android.util.Log.d("UserRepository", "🔄 User exists locally (ID: ${localUser.id}), updating with Firebase data")
                    android.util.Log.d("UserRepository", "  Before update:")
                    android.util.Log.d("UserRepository", "    - Local passwordHash: ${localUser.passwordHash}")
                    android.util.Log.d("UserRepository", "    - Local profile image: ${localUser.profileImageUrl}")
                    
                    val updatedUser = localUser.copy(
                        firebaseUid = firebaseUser.firebaseUid,
                        passwordHash = firebaseUser.passwordHash, // 🔑 CRITICAL: Update social account ID
                        profileImageUrl = firebaseUser.profileImageUrl,
                        bio = firebaseUser.bio,
                        itemsRecycled = firebaseUser.itemsRecycled,
                        totalPoints = firebaseUser.totalPoints
                    )
                    userDao.updateUser(updatedUser)
                    
                    android.util.Log.d("UserRepository", "  After update:")
                    android.util.Log.d("UserRepository", "    - Local passwordHash: ${updatedUser.passwordHash}")
                    android.util.Log.d("UserRepository", "    - Local profile image: ${updatedUser.profileImageUrl}")
                    android.util.Log.d("UserRepository", "✅ Local user updated successfully")
                    
                    Result.Success(updatedUser)
                }
            } else {
                android.util.Log.d("UserRepository", "❌ User NOT found in Firebase - New user!")
                Result.Success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "💥 Error checking if user exists", e)
            Result.Error(e)
        }
    }

    suspend fun loginGoogleUser(email: String, googleId: String): Result<UserSession> {
        return try {
            android.util.Log.d("UserRepository", "🔐 === Logging in social user ===")
            android.util.Log.d("UserRepository", "📧 Email: $email")
            android.util.Log.d("UserRepository", "🆔 Provided Google ID: $googleId")
            
            // Get user from database by email
            val user = userDao.getUserByEmail(email)
            if (user == null) {
                android.util.Log.e("UserRepository", "❌ Social user NOT found in local database!")
                return Result.Error(Exception("Social user not found"))
            }
            
            android.util.Log.d("UserRepository", "✅ User found in local DB:")
            android.util.Log.d("UserRepository", "  - Local ID: ${user.id}")
            android.util.Log.d("UserRepository", "  - Username: ${user.username}")
            android.util.Log.d("UserRepository", "  - Firebase UID: ${user.firebaseUid}")
            android.util.Log.d("UserRepository", "  - Stored passwordHash (Social ID): ${user.passwordHash}")

            // Verify social account ID matches (for social users, passwordHash contains social ID)
            android.util.Log.d("UserRepository", "🔍 Verifying social account IDs:")
            android.util.Log.d("UserRepository", "  - Stored ID: ${user.passwordHash}")
            android.util.Log.d("UserRepository", "  - Provided ID: $googleId")
            android.util.Log.d("UserRepository", "  - Match: ${user.passwordHash == googleId}")
            
            // 🔥 CRITICAL FIX: Handle missing social account ID (old accounts)
            if (user.passwordHash.isEmpty() && !googleId.isEmpty()) {
                android.util.Log.w("UserRepository", "⚠️ Social account ID is missing in database! Updating it now...")
                android.util.Log.d("UserRepository", "  - Updating with Google ID: $googleId")
                
                // Update local database with social account ID
                val updatedUser = user.copy(passwordHash = googleId)
                userDao.updateUser(updatedUser)
                
                // Update Firebase as well
                try {
                    if (!user.firebaseUid.isNullOrEmpty()) {
                        val updateData = hashMapOf<String, Any>("socialAccountId" to googleId)
                        firebaseAuthService.updateUserProfile(user.firebaseUid, updateData)
                        android.util.Log.d("UserRepository", "✅ Social account ID updated in both local DB and Firebase")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Failed to update Firebase: ${e.message}")
                }
            } else if (user.passwordHash != googleId) {
                android.util.Log.e("UserRepository", "❌ MISMATCH! Social account ID verification failed!")
                android.util.Log.e("UserRepository", "  Expected: ${user.passwordHash}")
                android.util.Log.e("UserRepository", "  Got: $googleId")
                return Result.Error(Exception("Invalid social account"))
            }
            
            android.util.Log.d("UserRepository", "✅ Social account ID verified successfully!")

            // 🔑 CRITICAL: Sign in to Firebase Auth for Firestore access
            android.util.Log.d("UserRepository", "🔐 Signing into Firebase Auth for social user...")
            if (!user.firebaseUid.isNullOrEmpty() && !googleId.isEmpty()) {
                try {
                    // Social users use their social account ID as their Firebase password
                    firebaseAuthService.signInWithEmailPassword(email, googleId)
                    android.util.Log.d("UserRepository", "✅ Firebase Auth sign-in successful!")
                } catch (e: Exception) {
                    android.util.Log.e("UserRepository", "⚠️ Firebase Auth sign-in failed (may need migration): ${e.message}")
                    // Continue with login even if Firebase sign-in fails (offline mode or migration needed)
                }
            } else {
                android.util.Log.w("UserRepository", "⚠️ Cannot sign into Firebase Auth - missing UID or social ID")
            }

            // 🔑 CRITICAL: Check if user has Firebase UID
            val firebaseUid = if (user.firebaseUid.isNullOrEmpty()) {
                android.util.Log.w("UserRepository", "⚠️ Social user missing Firebase UID - using temp token")
                securityManager.generateSessionToken()
            } else {
                android.util.Log.d("UserRepository", "✅ User has Firebase UID: ${user.firebaseUid}")
                user.firebaseUid
            }

            // Create session with Firebase UID as token
            val session = UserSession(
                userId = user.id,
                username = user.username,
                userType = user.userType,
                token = firebaseUid, // ✅ Use Firebase UID as token
                isLoggedIn = true
            )
            
            android.util.Log.d("UserRepository", "📝 Created session:")
            android.util.Log.d("UserRepository", "  - User ID: ${session.userId}")
            android.util.Log.d("UserRepository", "  - Username: ${session.username}")
            android.util.Log.d("UserRepository", "  - Token (Firebase UID): ${session.token}")
            android.util.Log.d("UserRepository", "  - User Type: ${session.userType}")

            // Update user's last active timestamp
            val updatedUser = user.copy(lastActive = System.currentTimeMillis())
            userDao.updateUser(updatedUser)

            // Save session
            preferencesManager.saveUserSession(session)
            android.util.Log.d("UserRepository", "✅ Session saved successfully!")

            Result.Success(session)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "💥 Failed to login social user", e)
            Result.Error(e)
        }
    }

    suspend fun createGoogleUser(
        username: String,
        email: String,
        displayName: String,
        photoUrl: String,
        googleId: String,
        userType: UserType,
        context: Context
    ): Result<UserSession> {
        return try {
            android.util.Log.d("UserRepository", "👤 === Creating NEW social user account ===")
            android.util.Log.d("UserRepository", "📧 Email: $email")
            android.util.Log.d("UserRepository", "👤 Username: $username")
            android.util.Log.d("UserRepository", "🆔 Social Account ID: $googleId")
            android.util.Log.d("UserRepository", "🖼️ Photo URL: $photoUrl")
            android.util.Log.d("UserRepository", "📝 Display Name: $displayName")
            
            // Validate inputs
            if (!securityManager.isValidUsername(username)) {
                return Result.Error(Exception("Username must be 3-20 characters, alphanumeric only"))
            }

            if (!securityManager.isValidEmail(email)) {
                return Result.Error(Exception("Invalid email format"))
            }

            // Check if user already exists locally
            val existingUsername = userDao.getUserByUsername(username)
            val existingEmail = userDao.getUserByEmail(email)
            
            android.util.Log.d("UserRepository", "🔍 Checking local database:")
            android.util.Log.d("UserRepository", "  - Username '$username' exists: ${existingUsername != null}")
            android.util.Log.d("UserRepository", "  - Email '$email' exists: ${existingEmail != null}")
            
            if (existingUsername != null) {
                android.util.Log.e("UserRepository", "❌ Username already exists: $username")
                return Result.Error(Exception("Username already exists"))
            }

            if (existingEmail != null) {
                android.util.Log.e("UserRepository", "❌ Email already registered: $email")
                return Result.Error(Exception("Email already registered"))
            }

            // 🔑 CRITICAL: Get or create Firebase Authentication account for social sign-in users
            // Social sign-in (Google/Huawei) may have already created a Firebase Auth account
            val firebaseUid: String
            val needsFirestoreProfile: Boolean
            
            try {
                // First, try to get the current Firebase user (might already exist from social sign-in)
                val currentFirebaseUser = firebaseAuthService.getCurrentFirebaseUser()
                if (currentFirebaseUser != null && currentFirebaseUser.email == email) {
                    // Firebase Auth account exists from social sign-in
                    android.util.Log.d("UserRepository", "Using existing Firebase account from social sign-in: ${currentFirebaseUser.uid}")
                    firebaseUid = currentFirebaseUser.uid
                    needsFirestoreProfile = true // Need to create Firestore profile
                    
                    // 🔑 Set password for the existing Firebase Auth account so we can sign in with email/password later
                    try {
                        android.util.Log.d("UserRepository", "Setting password for existing Firebase Auth account")
                        firebaseAuthService.updateCurrentUserPassword(googleId)
                        android.util.Log.d("UserRepository", "✅ Password set successfully!")
                    } catch (e: Exception) {
                        android.util.Log.w("UserRepository", "⚠️ Failed to set password: ${e.message}")
                        // Continue anyway - user can still access the account
                    }
                } else {
                    // Firebase Auth account doesn't exist yet, create one (will also create Firestore profile)
                    android.util.Log.d("UserRepository", "Creating new Firebase account for social user")
                    // 🔑 Use social account ID as password for consistent authentication
                    val firebaseResult = firebaseAuthService.registerUser(
                        username = username,
                        email = email,
                        password = googleId, // Use social ID as password for consistent login
                        userType = userType,
                        context = context
                    )
                    
                    if (firebaseResult !is Result.Success) {
                        val errorMsg = if (firebaseResult is Result.Error) firebaseResult.exception.message else "Unknown error"
                        android.util.Log.e("UserRepository", "Failed to create Firebase account: $errorMsg")
                        return Result.Error(Exception("Failed to create account. Please try again."))
                    }
                    
                    val firebaseUser = firebaseResult.data
                    val uid = firebaseUser.firebaseUid
                    
                    if (uid.isNullOrEmpty()) {
                        android.util.Log.e("UserRepository", "Firebase UID is null or empty after registration")
                        return Result.Error(Exception("Failed to get Firebase UID. Please try again."))
                    }
                    firebaseUid = uid
                    needsFirestoreProfile = false // Firestore profile already created by registerUser
                }
            } catch (e: Exception) {
                android.util.Log.e("UserRepository", "Error getting/creating Firebase account: ${e.message}", e)
                return Result.Error(Exception("Failed to create account: ${e.message}"))
            }
            
            if (firebaseUid.isEmpty()) {
                return Result.Error(Exception("Failed to get Firebase UID. Please try again."))
            }
            
            android.util.Log.d("UserRepository", "✅ Firebase UID obtained: $firebaseUid")

            // Create local user with Firebase UID
            android.util.Log.d("UserRepository", "💾 Creating local User object:")
            android.util.Log.d("UserRepository", "  - Username: $username")
            android.util.Log.d("UserRepository", "  - Email: $email")
            android.util.Log.d("UserRepository", "  - Password Hash (Social ID): $googleId")
            android.util.Log.d("UserRepository", "  - Firebase UID: $firebaseUid")
            android.util.Log.d("UserRepository", "  - Profile Image URL: $photoUrl")
            android.util.Log.d("UserRepository", "  - Bio: $displayName")
            
            val user = User(
                username = username,
                email = email,
                passwordHash = googleId, // Store social account ID for reference
                userType = userType,
                profileImageUrl = photoUrl,
                bio = displayName,
                firebaseUid = firebaseUid // ✅ CRITICAL: Store Firebase UID
            )

            val userId = userDao.insertUser(user)
            val createdUser = user.copy(id = userId)
            android.util.Log.d("UserRepository", "✅ User saved to local DB with ID: $userId")
            
            // Create or update Firestore profile with social info
            try {
                if (needsFirestoreProfile) {
                    // Create complete Firestore profile (Firebase Auth exists but Firestore profile doesn't)
                    android.util.Log.d("UserRepository", "📝 Creating Firestore profile for existing Firebase Auth account")
                    val fullProfileData = hashMapOf<String, Any>(
                        "firebaseUid" to firebaseUid,
                        "username" to username,
                        "email" to email,
                        "userType" to userType.name,
                        "profileImageUrl" to photoUrl,
                        "bio" to displayName,
                        "socialAccountId" to googleId,
                        "createdAt" to System.currentTimeMillis(),
                        "itemsRecycled" to 0,
                        "totalPoints" to 0,
                        "location" to "",
                        "joinDate" to System.currentTimeMillis(),
                        "lastActive" to System.currentTimeMillis(),
                        "profileCompletion" to 0,
                        "privacySettings" to "",
                        "achievements" to "",
                        "socialLinks" to "",
                        "preferences" to ""
                    )
                    android.util.Log.d("UserRepository", "  - Profile Image URL to save: $photoUrl")
                    android.util.Log.d("UserRepository", "  - Social Account ID to save: $googleId")
                    firebaseAuthService.createFirestoreProfile(firebaseUid, fullProfileData)
                } else {
                    // Update existing Firestore profile with social info
                    android.util.Log.d("UserRepository", "🔄 Updating Firestore profile with social info")
                    val socialInfoData = hashMapOf<String, Any>(
                        "profileImageUrl" to photoUrl,
                        "bio" to displayName,
                        "socialAccountId" to googleId
                    )
                    android.util.Log.d("UserRepository", "  - Profile Image URL to update: $photoUrl")
                    android.util.Log.d("UserRepository", "  - Social Account ID to update: $googleId")
                    firebaseAuthService.updateUserProfile(firebaseUid, socialInfoData)
                }
                android.util.Log.d("UserRepository", "✅ Firestore profile synchronized successfully")
            } catch (e: Exception) {
                android.util.Log.w("UserRepository", "⚠️ Failed to sync Firestore profile: ${e.message}")
                // Continue anyway, profile can be updated later
            }

            // Generate session token
            val token = firebaseUid // Use Firebase UID as token for consistency

            android.util.Log.d("UserRepository", "📝 Creating user session:")
            android.util.Log.d("UserRepository", "  - User ID: $userId")
            android.util.Log.d("UserRepository", "  - Username: $username")
            android.util.Log.d("UserRepository", "  - Token (Firebase UID): $token")
            android.util.Log.d("UserRepository", "  - User Type: $userType")

            // Create session with Firebase UID
            val session = UserSession(
                userId = createdUser.id,
                username = createdUser.username,
                userType = createdUser.userType,
                token = token, // Firebase UID stored in token
                isLoggedIn = true
            )

            // Save session
            preferencesManager.saveUserSession(session)
            
            android.util.Log.d("UserRepository", "Social user created successfully with Firebase sync enabled")

            Result.Success(session)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error creating social user: ${e.message}", e)
            Result.Error(e)
        }
    }

    suspend fun logoutUser(): Result<Unit> {
        return try {
            preferencesManager.clearUserSession()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== USER MANAGEMENT ====================

    /**
     * Get user or admin by ID - handles negative IDs for admins
     * This is the CORRECT way to look up users/admins throughout the app
     */
    suspend fun getUserOrAdmin(userId: Long): User? {
        return if (userId < 0) {
            // Admin account - convert negative ID to positive
            val adminId = kotlin.math.abs(userId)
            val adminDao = database.adminDao()
            val admin = adminDao.getAdminById(adminId)
            
            admin?.let {
                User(
                    id = userId,  // Keep negative ID for consistency
                    firebaseUid = it.firebaseUid,
                    username = it.username,
                    email = it.email,
                    passwordHash = it.passwordHash,
                    userType = UserType.ADMIN,
                    profileImageUrl = it.profileImageUrl,
                    bio = it.bio,
                    location = it.location,
                    itemsRecycled = it.itemsRecycled,
                    totalPoints = it.totalPoints,
                    createdAt = it.createdAt,
                    lastActive = it.lastLogin
                )
            }
        } else {
            // Regular user account
            userDao.getUserById(userId)
        }
    }

    suspend fun getUserById(userId: Long): Result<User> {
        return try {
            val user = getUserOrAdmin(userId)
                ?: return Result.Error(Exception("User not found"))
            Result.Success(user)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            val currentSession = preferencesManager.userSession.first()
                ?: return Result.Error(Exception("No active session"))
            
            // Determine actual admin ID if current user is admin (negative session ID)
            val currentAdminId = if (currentSession.userId < 0) kotlin.math.abs(currentSession.userId) else null
            
            // Search regular users (excluding current user if they're a regular user)
            val users = userDao.searchUsersByUsername(query, currentSession.userId).toMutableList()
            
            // Also search admins and convert them to User objects
            val adminDao = database.adminDao()
            val admins = adminDao.getAllActiveAdmins()
            val matchingAdmins = admins.filter { admin ->
                // Match query AND exclude current admin if logged in as admin
                admin.username.contains(query, ignoreCase = true) && 
                admin.id != currentAdminId  // Exclude current admin by actual admin ID
            }
            
            // Convert admins to User objects for uniform display
            val adminUsers = matchingAdmins.map { admin ->
                User(
                    id = -admin.id,  // ✅ Use NEGATIVE ID to avoid collision with regular users
                    firebaseUid = admin.firebaseUid,
                    username = admin.username,
                    email = admin.email,
                    passwordHash = admin.passwordHash,
                    userType = UserType.ADMIN,
                    profileImageUrl = admin.profileImageUrl,
                    bio = admin.bio,
                    location = admin.location,
                    itemsRecycled = admin.itemsRecycled,
                    totalPoints = admin.totalPoints,
                    createdAt = admin.createdAt,
                    lastActive = admin.lastLogin
                )
            }
            
            users.addAll(adminUsers)
            android.util.Log.d("UserRepository", "Search results: ${users.size} total (${users.size - adminUsers.size} users, ${adminUsers.size} admins)")
            Result.Success(users)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Search error", e)
            Result.Error(e)
        }
    }

    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            userDao.updateUser(user)
            
            // Sync user profile to Firebase
            try {
                updateUserInFirebase(user)
                android.util.Log.d("UserRepository", "User profile updated in Firebase: ${user.username}")
            } catch (e: Exception) {
                android.util.Log.w("UserRepository", "Failed to update user in Firebase: ${e.message}")
                // Don't fail update if Firebase sync fails
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun incrementRecycledItems(userId: Long): Result<Unit> {
        return try {
            userDao.incrementItemsRecycled(userId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== PROFILE MANAGEMENT ====================

    suspend fun updateProfileBio(userId: Long, bio: String?): Result<Unit> {
        return try {
            android.util.Log.d("UserRepository", "=== UPDATING BIO ===")
            android.util.Log.d("UserRepository", "User ID: $userId, Bio: '$bio'")
            
            // Get user before update
            val userBefore = userDao.getUserById(userId)
            android.util.Log.d("UserRepository", "User before update - bio: '${userBefore?.bio}', location: '${userBefore?.location}', firebaseUid: '${userBefore?.firebaseUid}'")
            android.util.Log.d("UserRepository", "Full user object before update: $userBefore")
            
            // Update local database
            userDao.updateBio(userId, bio)
            android.util.Log.d("UserRepository", "Database update completed")
            
            // Get the updated user to sync to Firebase
            val user = userDao.getUserById(userId)
            if (user != null) {
                android.util.Log.d("UserRepository", "User after bio update - bio: '${user.bio}', location: '${user.location}', firebaseUid: '${user.firebaseUid}'")
                android.util.Log.d("UserRepository", "Full user object after bio update: $user")
                
                // Try to get firebaseUid from current session if user doesn't have one
                var firebaseUid = user.firebaseUid
                if (firebaseUid.isNullOrBlank()) {
                    val session = preferencesManager.userSession.first()
                    firebaseUid = session?.token
                    android.util.Log.d("UserRepository", "No firebaseUid in user, trying session token: '$firebaseUid'")
                    
                    // Update user with firebaseUid if we found one
                    if (!firebaseUid.isNullOrBlank()) {
                        val updatedUser = user.copy(firebaseUid = firebaseUid)
                        userDao.updateUser(updatedUser)
                        android.util.Log.d("UserRepository", "Updated user with firebaseUid: '$firebaseUid'")
                    }
                }
                
                if (!firebaseUid.isNullOrBlank()) {
                    // Sync updated user profile to Firebase using firebaseUid
                    try {
                        val userToSync = user.copy(firebaseUid = firebaseUid)
                        updateUserInFirebase(userToSync)
                        android.util.Log.d("UserRepository", "User bio updated in Firebase: ${user.username} (firebaseUid: ${firebaseUid})")
                    } catch (e: Exception) {
                        android.util.Log.w("UserRepository", "Failed to update user bio in Firebase: ${e.message}")
                        // Don't fail the operation if Firebase sync fails
                    }
                } else {
                    android.util.Log.w("UserRepository", "User has no firebaseUid and no session token, skipping Firebase sync for bio update")
                }
            } else {
                android.util.Log.e("UserRepository", "User not found after bio update")
            }
            
            android.util.Log.d("UserRepository", "=== END BIO UPDATE ===")
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error updating bio", e)
            Result.Error(e)
        }
    }

    suspend fun updateProfileLocation(userId: Long, location: String?): Result<Unit> {
        return try {
            android.util.Log.d("UserRepository", "=== UPDATING LOCATION ===")
            android.util.Log.d("UserRepository", "User ID: $userId, Location: '$location'")
            
            // Get user before update
            val userBefore = userDao.getUserById(userId)
            android.util.Log.d("UserRepository", "User before update - bio: '${userBefore?.bio}', location: '${userBefore?.location}', firebaseUid: '${userBefore?.firebaseUid}'")
            android.util.Log.d("UserRepository", "Full user object before update: $userBefore")
            
            // Update local database
            userDao.updateLocation(userId, location)
            android.util.Log.d("UserRepository", "Database update completed")
            
            // Get the updated user to sync to Firebase
            val user = userDao.getUserById(userId)
            if (user != null) {
                android.util.Log.d("UserRepository", "User after location update - bio: '${user.bio}', location: '${user.location}', firebaseUid: '${user.firebaseUid}'")
                android.util.Log.d("UserRepository", "Full user object after location update: $user")
                
                // Try to get firebaseUid from current session if user doesn't have one
                var firebaseUid = user.firebaseUid
                if (firebaseUid.isNullOrBlank()) {
                    val session = preferencesManager.userSession.first()
                    firebaseUid = session?.token
                    android.util.Log.d("UserRepository", "No firebaseUid in user, trying session token: '$firebaseUid'")
                    
                    // Update user with firebaseUid if we found one
                    if (!firebaseUid.isNullOrBlank()) {
                        val updatedUser = user.copy(firebaseUid = firebaseUid)
                        userDao.updateUser(updatedUser)
                        android.util.Log.d("UserRepository", "Updated user with firebaseUid: '$firebaseUid'")
                    }
                }
                
                if (!firebaseUid.isNullOrBlank()) {
                    // Sync updated user profile to Firebase using firebaseUid
                    try {
                        val userToSync = user.copy(firebaseUid = firebaseUid)
                        updateUserInFirebase(userToSync)
                        android.util.Log.d("UserRepository", "User location updated in Firebase: ${user.username} (firebaseUid: ${firebaseUid})")
                    } catch (e: Exception) {
                        android.util.Log.w("UserRepository", "Failed to update user location in Firebase: ${e.message}")
                        // Don't fail the operation if Firebase sync fails
                    }
                } else {
                    android.util.Log.w("UserRepository", "User has no firebaseUid and no session token, skipping Firebase sync for location update")
                }
            } else {
                android.util.Log.e("UserRepository", "User not found after location update")
            }
            
            android.util.Log.d("UserRepository", "=== END LOCATION UPDATE ===")
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error updating location", e)
            Result.Error(e)
        }
    }

    suspend fun updateProfileImage(userId: Long, imageUrl: String?): Result<Unit> {
        return try {
            // Update local database
            userDao.updateProfileImage(userId, imageUrl)
            
            // Get the updated user to sync to Firebase
            val user = userDao.getUserById(userId)
            if (user != null) {
                // Sync updated user profile to Firebase
                try {
                    updateUserInFirebase(user)
                    android.util.Log.d("UserRepository", "User profile image updated in Firebase: ${user.username}")
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Failed to update user profile image in Firebase: ${e.message}")
                    // Don't fail the operation if Firebase sync fails
                }
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateLastActive(userId: Long): Result<Unit> {
        return try {
            userDao.updateLastActive(userId, System.currentTimeMillis())
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun calculateProfileCompletion(userId: Long): Result<Int> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            
            var completion = 0
            if (user.username.isNotEmpty()) completion += 20
            if (!user.email.isBlank()) completion += 20
            if (!user.bio.isNullOrBlank()) completion += 20
            if (!user.location.isNullOrBlank()) completion += 20
            if (!user.profileImageUrl.isNullOrBlank()) completion += 20
            
            userDao.updateProfileCompletion(userId, completion)
            Result.Success(completion)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updatePrivacySettings(userId: Long, privacySettings: PrivacySettings): Result<Unit> {
        return try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(privacySettings)
            userDao.updatePrivacySettings(userId, json)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getPrivacySettings(userId: Long): Result<PrivacySettings> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            if (user.privacySettings.isNullOrBlank()) {
                Result.Success(PrivacySettings()) // Return default settings
            } else {
                val gson = com.google.gson.Gson()
                val settings = gson.fromJson(user.privacySettings, PrivacySettings::class.java)
                Result.Success(settings)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun addAchievement(userId: Long, achievement: Achievement): Result<Unit> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            val gson = com.google.gson.Gson()
            val type = object : com.google.gson.reflect.TypeToken<List<Achievement>>() {}.type
            
            val currentAchievements = if (user.achievements.isNullOrBlank()) {
                emptyList<Achievement>()
            } else {
                gson.fromJson(user.achievements, type)
            }
            
            val updatedAchievements = currentAchievements + achievement
            val json = gson.toJson(updatedAchievements)
            userDao.updateAchievements(userId, json)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getAchievements(userId: Long): Result<List<Achievement>> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            if (user.achievements.isNullOrBlank()) {
                Result.Success(emptyList())
            } else {
                val gson = com.google.gson.Gson()
                val type = object : com.google.gson.reflect.TypeToken<List<Achievement>>() {}.type
                val achievements = gson.fromJson<List<Achievement>>(user.achievements, type)
                Result.Success(achievements)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateSocialLinks(userId: Long, socialLinks: SocialLinks): Result<Unit> {
        return try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(socialLinks)
            
            // Update local database
            userDao.updateSocialLinks(userId, json)
            
            // Get the updated user to sync to Firebase
            val user = userDao.getUserById(userId)
            if (user != null && !user.firebaseUid.isNullOrBlank()) {
                // Sync updated user profile to Firebase using firebaseUid
                try {
                    updateUserInFirebase(user)
                    android.util.Log.d("UserRepository", "User social links updated in Firebase: ${user.username} (firebaseUid: ${user.firebaseUid})")
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Failed to update user social links in Firebase: ${e.message}")
                    // Don't fail the operation if Firebase sync fails
                }
            } else {
                android.util.Log.w("UserRepository", "User has no firebaseUid, skipping Firebase sync for social links update")
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getSocialLinks(userId: Long): Result<SocialLinks> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            if (user.socialLinks.isNullOrBlank()) {
                Result.Success(SocialLinks()) // Return default social links
            } else {
                val gson = com.google.gson.Gson()
                val socialLinks = gson.fromJson(user.socialLinks, SocialLinks::class.java)
                Result.Success(socialLinks)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun updateUserPreferences(userId: Long, preferences: UserPreferences): Result<Unit> {
        return try {
            val gson = com.google.gson.Gson()
            val json = gson.toJson(preferences)
            userDao.updatePreferences(userId, json)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUserPreferences(userId: Long): Result<UserPreferences> {
        return try {
            val user = userDao.getUserById(userId) ?: return Result.Error(Exception("User not found"))
            if (user.preferences.isNullOrBlank()) {
                Result.Success(UserPreferences()) // Return default preferences
            } else {
                val gson = com.google.gson.Gson()
                val preferences = gson.fromJson(user.preferences, UserPreferences::class.java)
                Result.Success(preferences)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun addPoints(userId: Long, points: Int): Result<Unit> {
        return try {
            userDao.addPoints(userId, points)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun changePassword(
        userId: Long,
        oldPassword: String,
        newPassword: String
    ): Result<Unit> {
        return try {
            val user = userDao.getUserById(userId)
                ?: return Result.Error(Exception("User not found"))

            // Verify old password
            if (!securityManager.verifyPassword(oldPassword, user.passwordHash)) {
                return Result.Error(Exception("Invalid current password"))
            }

            // Validate new password
            if (!securityManager.isValidPassword(newPassword)) {
                return Result.Error(Exception("New password does not meet requirements"))
            }

            // Hash new password and update
            val newPasswordHash = securityManager.hashPassword(newPassword)
            val updatedUser = user.copy(passwordHash = newPasswordHash)
            userDao.updateUser(updatedUser)

            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== FIREBASE SYNC ====================

    /**
     * Sync user profile to Firebase
     */
    private suspend fun syncUserToFirebase(user: User) {
        val userData = hashMapOf<String, Any>(
            "id" to user.id,
            "username" to user.username,
            "email" to user.email,
            "userType" to user.userType.name,
            "createdAt" to user.createdAt,
            "itemsRecycled" to user.itemsRecycled,
            "totalPoints" to user.totalPoints,
            "joinDate" to user.joinDate,
            "lastActive" to user.lastActive,
            "profileCompletion" to user.profileCompletion
        )

        // Only add fields that have actual values (not null or empty)
        if (!user.firebaseUid.isNullOrBlank()) {
            userData["firebaseUid"] = user.firebaseUid
        }
        if (!user.profileImageUrl.isNullOrBlank()) {
            userData["profileImageUrl"] = user.profileImageUrl
        }
        if (!user.bio.isNullOrBlank()) {
            userData["bio"] = user.bio
        }
        if (!user.location.isNullOrBlank()) {
            userData["location"] = user.location
        }
        if (!user.privacySettings.isNullOrBlank()) {
            userData["privacySettings"] = user.privacySettings
        }
        if (!user.achievements.isNullOrBlank()) {
            userData["achievements"] = user.achievements
        }
        if (!user.socialLinks.isNullOrBlank()) {
            userData["socialLinks"] = user.socialLinks
        }
        if (!user.preferences.isNullOrBlank()) {
            userData["preferences"] = user.preferences
        }

        val result = firestoreService.saveUserProfile(userData)
        if (result.isFailure) {
            throw result.exceptionOrNull() ?: Exception("Failed to sync user to Firebase")
        }
    }

    /**
     * Update user profile in Firebase
     */
    private suspend fun updateUserInFirebase(user: User) {
        android.util.Log.d("UserRepository", "=== UPDATING USER IN FIREBASE ===")
        android.util.Log.d("UserRepository", "User object - bio: '${user.bio}', location: '${user.location}', profileImageUrl: '${user.profileImageUrl}'")
        android.util.Log.d("UserRepository", "User object - firebaseUid: '${user.firebaseUid}', username: '${user.username}'")
        
        val userData = hashMapOf<String, Any>(
            "id" to user.id,
            "username" to user.username,
            "email" to user.email,
            "userType" to user.userType.name,
            "createdAt" to user.createdAt,
            "itemsRecycled" to user.itemsRecycled,
            "totalPoints" to user.totalPoints,
            "joinDate" to user.joinDate,
            "lastActive" to user.lastActive,
            "profileCompletion" to user.profileCompletion
        )

        // Only add fields that have actual values (not null or empty)
        if (!user.firebaseUid.isNullOrBlank()) {
            userData["firebaseUid"] = user.firebaseUid
        }
        if (!user.profileImageUrl.isNullOrBlank()) {
            userData["profileImageUrl"] = user.profileImageUrl
        }
        if (!user.bio.isNullOrBlank()) {
            userData["bio"] = user.bio
        }
        if (!user.location.isNullOrBlank()) {
            userData["location"] = user.location
        }
        if (!user.privacySettings.isNullOrBlank()) {
            userData["privacySettings"] = user.privacySettings
        }
        if (!user.achievements.isNullOrBlank()) {
            userData["achievements"] = user.achievements
        }
        if (!user.socialLinks.isNullOrBlank()) {
            userData["socialLinks"] = user.socialLinks
        }
        if (!user.preferences.isNullOrBlank()) {
            userData["preferences"] = user.preferences
        }

        // Use firebaseUid as the document ID for Firebase updates
        val documentId = user.firebaseUid ?: user.id.toString()
        android.util.Log.d("UserRepository", "Document ID: $documentId")
        android.util.Log.d("UserRepository", "UserData HashMap - bio: '${userData["bio"]}', location: '${userData["location"]}', profileImageUrl: '${userData["profileImageUrl"]}'")
        
        val result = firestoreService.updateUserProfile(documentId, userData)
        if (result.isFailure) {
            android.util.Log.w("UserRepository", "Failed to update user in Firebase: ${result.exceptionOrNull()?.message}")
        } else {
            android.util.Log.d("UserRepository", "Successfully updated user in Firebase")
        }
        android.util.Log.d("UserRepository", "=== END FIREBASE UPDATE ===")
    }

    /**
     * Get user profile from Firebase by username
     */
    suspend fun getUserProfileFromFirebase(username: String): Result<User?> {
        return try {
            val result = firestoreService.getUserProfileByUsername(username)
            if (result.isSuccess) {
                val userData = result.getOrNull()
                if (userData != null) {
                    val user = User(
                        id = (userData["id"] as? Number)?.toLong() ?: 0L,
                        username = userData["username"] as? String ?: "",
                        email = userData["email"] as? String ?: "",
                        passwordHash = "", // Don't sync password hash
                        userType = UserType.valueOf(userData["userType"] as? String ?: "USER"),
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
                    Result.Success(user)
                } else {
                    Result.Success(null)
                }
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get user from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Get user profile from Firebase by firebaseUid
     */
    suspend fun getUserProfileFromFirebaseByUid(firebaseUid: String): Result<User?> {
        return try {
            val result = firestoreService.getUserProfileByFirebaseUid(firebaseUid)
            if (result.isSuccess) {
                val userData = result.getOrNull()
                if (userData != null) {
                    val user = User(
                        id = (userData["id"] as? Number)?.toLong() ?: 0L,
                        firebaseUid = userData["firebaseUid"] as? String,
                        username = userData["username"] as? String ?: "",
                        email = userData["email"] as? String ?: "",
                        passwordHash = "", // Don't sync password hash
                        userType = UserType.valueOf(userData["userType"] as? String ?: "USER"),
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
                    Result.Success(user)
                } else {
                    Result.Success(null)
                }
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get user from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Sync user profile from Firebase to local database
     */
    suspend fun syncUserFromFirebase(username: String): Result<User> {
        return try {
            // First try to get user by username
            val firebaseResult = getUserProfileFromFirebase(username)
            if (firebaseResult is Result.Success) {
                val firebaseUser = firebaseResult.data
                if (firebaseUser != null) {
                    // Check if user already exists locally by username OR firebaseUid
                    val localUser = userDao.getUserByUsername(username) ?: 
                        if (!firebaseUser.firebaseUid.isNullOrBlank()) {
                            userDao.getUserByFirebaseUid(firebaseUser.firebaseUid)
                        } else {
                            null
                        }
                    
                    if (localUser == null) {
                        // Insert new user to local database
                        val userId = userDao.insertUser(firebaseUser)
                        val syncedUser = firebaseUser.copy(id = userId)
                        android.util.Log.d("UserRepository", "Synced user from Firebase to local: $username")
                        Result.Success(syncedUser)
                        } else {
                            // Update existing user - merge Firebase data with local data
                            // Prefer non-empty local data over empty Firebase data
                            val mergedUser = localUser.copy(
                                firebaseUid = firebaseUser.firebaseUid ?: localUser.firebaseUid,
                                username = firebaseUser.username,
                                email = firebaseUser.email,
                                userType = firebaseUser.userType,
                                createdAt = firebaseUser.createdAt,
                                itemsRecycled = firebaseUser.itemsRecycled,
                                totalPoints = firebaseUser.totalPoints,
                                profileImageUrl = if (!firebaseUser.profileImageUrl.isNullOrBlank()) firebaseUser.profileImageUrl else localUser.profileImageUrl,
                                bio = if (!firebaseUser.bio.isNullOrBlank()) firebaseUser.bio else localUser.bio,
                                location = if (!firebaseUser.location.isNullOrBlank()) firebaseUser.location else localUser.location,
                                joinDate = firebaseUser.joinDate,
                                lastActive = firebaseUser.lastActive,
                                profileCompletion = firebaseUser.profileCompletion,
                                privacySettings = if (!firebaseUser.privacySettings.isNullOrBlank()) firebaseUser.privacySettings else localUser.privacySettings,
                                achievements = if (!firebaseUser.achievements.isNullOrBlank()) firebaseUser.achievements else localUser.achievements,
                                socialLinks = if (!firebaseUser.socialLinks.isNullOrBlank()) firebaseUser.socialLinks else localUser.socialLinks,
                                preferences = if (!firebaseUser.preferences.isNullOrBlank()) firebaseUser.preferences else localUser.preferences
                            )
                        userDao.updateUser(mergedUser)
                        android.util.Log.d("UserRepository", "Updated local user from Firebase: $username")
                        android.util.Log.d("UserRepository", "Merged user bio: '${mergedUser.bio}', location: '${mergedUser.location}'")
                        Result.Success(mergedUser)
                    }
                } else {
                    Result.Error(Exception("User not found in Firebase"))
                }
            } else {
                Result.Error((firebaseResult as Result.Error).exception)
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * Clean up duplicate users in local database
     * Keeps the user with the most recent lastActive timestamp
     */
    suspend fun cleanupDuplicateUsers(): Result<Int> {
        return try {
            android.util.Log.d("UserRepository", "Starting cleanup of duplicate users")
            
            // Get all users grouped by username
            val allUsers = userDao.getAllUsers()
            val usersByUsername = allUsers.groupBy { user -> user.username }
            
            var cleanedCount = 0
            
            for ((username, users) in usersByUsername) {
                if (users.size > 1) {
                    android.util.Log.w("UserRepository", "Found ${users.size} duplicate users for username: $username")
                    
                    // Sort by lastActive (most recent first) and keep the first one
                    val sortedUsers = users.sortedByDescending { user -> user.lastActive }
                    val usersToDelete = sortedUsers.drop(1)
                    
                    // Delete duplicate users
                    for (userToDelete in usersToDelete) {
                        userDao.deleteUser(userToDelete)
                        android.util.Log.d("UserRepository", "Deleted duplicate user: ${userToDelete.id} for username: $username")
                        cleanedCount++
                    }
                }
            }
            
            android.util.Log.d("UserRepository", "Cleanup completed. Removed $cleanedCount duplicate users")
            Result.Success(cleanedCount)
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error cleaning up duplicate users", e)
            Result.Error(e)
        }
    }

    /**
     * Force refresh user data from Firebase with consolidation
     */
    suspend fun forceRefreshUserData(username: String): com.example.ecosort.data.model.Result<User> {
        return try {
            android.util.Log.d("UserRepository", "Force refreshing user data for: $username")
            
            // First cleanup Firebase duplicates
            val cleanupResult = cleanupFirebaseDuplicates()
            if (cleanupResult is com.example.ecosort.data.model.Result.Success) {
                android.util.Log.d("UserRepository", "Cleaned up ${cleanupResult.data} Firebase duplicates")
            }
            
            // Then sync the user data
            val syncResult = syncUserFromFirebase(username)
            if (syncResult is com.example.ecosort.data.model.Result.Success) {
                android.util.Log.d("UserRepository", "Successfully refreshed user data for: $username")
                syncResult
            } else {
                android.util.Log.w("UserRepository", "Failed to refresh user data: ${(syncResult as com.example.ecosort.data.model.Result.Error).exception.message}")
                syncResult
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error force refreshing user data", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }

    /**
     * Clean up duplicate users in Firebase
     */
    suspend fun cleanupFirebaseDuplicates(): com.example.ecosort.data.model.Result<Int> {
        return try {
            android.util.Log.d("UserRepository", "Starting Firebase duplicate cleanup")
            val result = firestoreService.cleanupAllDuplicateUsers()
            if (result.isSuccess) {
                val cleanedCount = result.getOrNull() ?: 0
                android.util.Log.d("UserRepository", "Firebase cleanup completed: $cleanedCount duplicates removed")
                com.example.ecosort.data.model.Result.Success(cleanedCount)
            } else {
                android.util.Log.w("UserRepository", "Firebase cleanup failed: ${result.exceptionOrNull()?.message}")
                com.example.ecosort.data.model.Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Firebase cleanup failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "Error during Firebase cleanup", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }

    /**
     * Sync all user profiles from Firebase to local database
     */
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
                        android.util.Log.w("UserRepository", "Failed to sync user: ${e.message}")
                    }
                }
                
                // Clean up duplicate users in Firebase first
                try {
                    val firebaseCleanupResult = cleanupFirebaseDuplicates()
                    if (firebaseCleanupResult is Result.Success) {
                        android.util.Log.d("UserRepository", "Cleaned up ${firebaseCleanupResult.data} duplicate users in Firebase")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Failed to cleanup Firebase duplicates: ${e.message}")
                }
                
                // Clean up duplicate users in local database after syncing
                try {
                    val cleanupResult = cleanupDuplicateUsers()
                    if (cleanupResult is Result.Success) {
                        android.util.Log.d("UserRepository", "Cleaned up ${cleanupResult.data} duplicate users in local database")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("UserRepository", "Failed to cleanup local duplicate users: ${e.message}")
                }
                
                android.util.Log.d("UserRepository", "Synced $syncedCount users from Firebase")
                Result.Success(syncedCount)
            } else {
                Result.Error(result.exceptionOrNull() as? Exception ?: Exception("Failed to get users from Firebase"))
            }
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    /**
     * 🔑 Get current user's Firebase UID from session
     * CRITICAL: Used for cross-device sync - always use Firebase UID, never local ID
     */
    suspend fun getCurrentUserFirebaseUid(): String? {
        return try {
            val session = preferencesManager.userSession.first()
            if (session == null || !session.isLoggedIn) {
                android.util.Log.w("UserRepository", "⚠️ No active session found")
                return null
            }
            
            // Token contains Firebase UID for all users (email/password and social)
            val firebaseUid = session.token
            
            if (firebaseUid.isNullOrEmpty()) {
                android.util.Log.e("UserRepository", "❌ Session token (Firebase UID) is null or empty!")
                return null
            }
            
            android.util.Log.d("UserRepository", "✅ Current user Firebase UID: $firebaseUid")
            firebaseUid
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "💥 Error getting current user Firebase UID", e)
            null
        }
    }

    /**
     * 🔑 Get another user's Firebase UID by their local ID (for migration compatibility)
     */
    suspend fun getFirebaseUidByLocalId(localUserId: Long): String? {
        return try {
            val user = userDao.getUserById(localUserId)
            if (user?.firebaseUid.isNullOrEmpty()) {
                android.util.Log.w("UserRepository", "⚠️ User $localUserId has no Firebase UID!")
                return null
            }
            user?.firebaseUid
        } catch (e: Exception) {
            android.util.Log.e("UserRepository", "💥 Error getting Firebase UID for user $localUserId", e)
            null
        }
    }
}