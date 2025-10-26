package com.example.ecosort.data.firebase

import android.content.Context
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserSession
import com.example.ecosort.data.model.UserType
import com.example.ecosort.utils.SecurityManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthService @Inject constructor() {

    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    private val usersCollection by lazy { firestore.collection("users") }

    // ==================== AUTHENTICATION ====================
    
    /**
     * Register a new user with Firebase Authentication and Firestore
     */
    suspend fun registerUser(
        username: String,
        email: String,
        password: String,
        userType: UserType,
        context: Context
    ): Result<User> {
        return try {
            // 1. Register with Firebase Authentication
            val authResult = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
            val firebaseUid = authResult.user?.uid ?: throw Exception("Firebase UID not found")

            // 2. Hash and encrypt password for Firestore storage
            val passwordHash = SecurityManager.hashPassword(password)
            val encryptedPasswordHash = SecurityManager.encryptForFirebase(passwordHash, context)

            // 3. Create User object
            val user = User(
                id = 0, // Will be set by local DB, Firebase uses firebaseUid
                firebaseUid = firebaseUid,
                username = username,
                email = email,
                passwordHash = passwordHash, // Store unencrypted hash locally
                userType = userType
            )

            // 4. Save user profile to Firestore
            val userData = hashMapOf(
                "firebaseUid" to firebaseUid,
                "username" to username,
                "email" to email,
                "passwordHash" to encryptedPasswordHash, // Store encrypted hash
                "userType" to userType.name,
                "createdAt" to System.currentTimeMillis(),
                "itemsRecycled" to 0,
                "totalPoints" to 0,
                "profileImageUrl" to "",
                "bio" to "",
                "location" to "",
                "joinDate" to System.currentTimeMillis(),
                "lastActive" to System.currentTimeMillis(),
                "profileCompletion" to 0,
                "privacySettings" to "",
                "achievements" to "",
                "socialLinks" to "",
                "preferences" to ""
            )
            usersCollection.document(firebaseUid).set(userData).await()
            android.util.Log.d("FirebaseAuthService", "User registered and profile saved to Firestore: $username")

            Result.Success(user)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Firebase registration failed: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Authenticate user with Firebase Authentication
     */
    suspend fun authenticateUser(usernameOrEmail: String, password: String, context: Context): Result<UserSession> {
        return try {
            // 1. First, try to authenticate with Firebase Authentication using the input as email
            var authResult: com.google.firebase.auth.AuthResult? = null
            var firebaseUid: String? = null
            
            try {
                // Try as email first
                authResult = firebaseAuth.signInWithEmailAndPassword(usernameOrEmail, password).await()
                firebaseUid = authResult.user?.uid
            } catch (e: Exception) {
                // If that fails, try to find user by username in Firestore first
                android.util.Log.d("FirebaseAuthService", "Email authentication failed, trying username lookup: ${e.message}")
                
                // Search for user by username in Firestore
                val querySnapshot = usersCollection.whereEqualTo("username", usernameOrEmail).get().await()
                if (!querySnapshot.isEmpty) {
                    val userDoc = querySnapshot.documents.first()
                    val userData = userDoc.data
                    val email = userData?.get("email") as? String
                    
                    if (email != null) {
                        // Try authentication with the found email
                        authResult = firebaseAuth.signInWithEmailAndPassword(email, password).await()
                        firebaseUid = authResult.user?.uid
                    }
                }
            }
            
            if (firebaseUid == null) {
                throw Exception("Firebase authentication failed - user not found or invalid credentials")
            }

            // 2. Get user profile from Firestore
            val userDoc = usersCollection.document(firebaseUid).get().await()
            val userData = userDoc.data ?: throw Exception("User profile not found in Firestore")

            val username = userData["username"] as? String ?: throw Exception("Username not found in Firestore")
            val email = userData["email"] as? String ?: throw Exception("Email not found in Firestore")
            val userType = UserType.valueOf(userData["userType"] as? String ?: "USER")
            val encryptedPasswordHash = userData["passwordHash"] as? String ?: ""

            // 3. Decrypt password hash and verify (optional, for extra layer of security/migration check)
            if (encryptedPasswordHash.isNotEmpty()) {
                try {
                    val decryptedPasswordHash = SecurityManager.decryptFromFirebase(encryptedPasswordHash, context)
                    if (!SecurityManager.verifyPassword(password, decryptedPasswordHash)) {
                        // This should ideally not happen if Firebase Auth succeeded, but good for integrity check
                        android.util.Log.w("FirebaseAuthService", "Password hash mismatch after Firebase Auth for $username")
                        // Optionally, re-encrypt and update if the hash format changed
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FirebaseAuthService", "Failed to verify password hash for $username: ${e.message}")
                    // Continue with authentication even if hash verification fails
                }
            }

            // 4. Create UserSession
            val session = UserSession(
                userId = 0, // Will be set by local DB
                username = username,
                userType = userType,
                token = firebaseUid, // Using Firebase UID as session token for simplicity
                isLoggedIn = true
            )
            android.util.Log.d("FirebaseAuthService", "User authenticated via Firebase: $username")
            Result.Success(session)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Firebase authentication failed: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Migrate existing local user to Firebase Authentication
     */
    suspend fun migrateUserToFirebase(user: User, context: Context): Result<Unit> {
        return try {
            // Check if user already has a Firebase UID
            if (!user.firebaseUid.isNullOrBlank()) {
                android.util.Log.d("FirebaseAuthService", "User ${user.username} already has Firebase UID. Skipping migration.")
                return Result.Success(Unit)
            }

            // 1. Create user in Firebase Authentication
            val authResult = firebaseAuth.createUserWithEmailAndPassword(user.email, "temp_password_for_migration").await()
            val firebaseUid = authResult.user?.uid ?: throw Exception("Firebase UID not found during migration")

            // 2. Update Firebase Auth password to actual password (if available and not Google)
            // This part is tricky as we don't have the plain password.
            // For now, we'll store the encrypted hash in Firestore and rely on local verification for first login.
            // A better approach would be to prompt user for password on first Firebase login.
            // For this implementation, we assume the user will re-enter password if needed.

            // 3. Hash and encrypt password for Firestore storage
            val passwordHash = user.passwordHash // This is the local PBKDF2 hash
            val encryptedPasswordHash = SecurityManager.encryptForFirebase(passwordHash, context)

            // 4. Save user profile to Firestore with Firebase UID
            val userData = hashMapOf<String, Any>(
                "firebaseUid" to firebaseUid,
                "username" to user.username,
                "email" to user.email,
                "passwordHash" to encryptedPasswordHash, // Store encrypted hash
                "userType" to user.userType.name,
                "createdAt" to user.createdAt,
                "itemsRecycled" to user.itemsRecycled,
                "totalPoints" to user.totalPoints,
                "profileImageUrl" to (user.profileImageUrl ?: ""),
                "bio" to (user.bio ?: ""),
                "location" to (user.location ?: ""),
                "joinDate" to user.joinDate,
                "lastActive" to user.lastActive,
                "profileCompletion" to user.profileCompletion,
                "privacySettings" to (user.privacySettings ?: ""),
                "achievements" to (user.achievements ?: ""),
                "socialLinks" to (user.socialLinks ?: ""),
                "preferences" to (user.preferences ?: "")
            )
            usersCollection.document(firebaseUid).set(userData).await()
            android.util.Log.d("FirebaseAuthService", "User ${user.username} migrated to Firebase with UID: $firebaseUid")

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to migrate user ${user.username} to Firebase: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Get user data from Firebase Firestore by username or email
     */
    suspend fun getUserFromFirebase(usernameOrEmail: String, context: Context): Result<User?> {
        return try {
            // First try to find by username
            var querySnapshot = usersCollection.whereEqualTo("username", usernameOrEmail).get().await()
            var userDoc = querySnapshot.documents.firstOrNull()
            
            // If not found by username, try by email
            if (userDoc == null) {
                querySnapshot = usersCollection.whereEqualTo("email", usernameOrEmail).get().await()
                userDoc = querySnapshot.documents.firstOrNull()
            }

            if (userDoc != null) {
                val userData = userDoc.data ?: return Result.Success(null)
                val firebaseUid = userData.get("firebaseUid") as? String ?: userDoc.id
                val encryptedPasswordHash = userData.get("passwordHash") as? String ?: ""
                
                // Try to decrypt password hash, but don't fail if it doesn't work
                val decryptedPasswordHash = try {
                    if (encryptedPasswordHash.isNotEmpty()) {
                        SecurityManager.decryptFromFirebase(encryptedPasswordHash, context)
                    } else {
                        ""
                    }
                } catch (e: Exception) {
                    android.util.Log.w("FirebaseAuthService", "Failed to decrypt password hash for ${usernameOrEmail}: ${e.message}")
                    ""
                }

                val user = User(
                    id = 0, // Local ID will be set by Room
                    firebaseUid = firebaseUid,
                    username = userData["username"] as? String ?: "",
                    email = userData["email"] as? String ?: "",
                    passwordHash = decryptedPasswordHash, // Decrypted hash for local storage
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
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to get user from Firebase: ${e.message}", e)
            Result.Error(e)
        }
    }

    /**
     * Check if user is currently authenticated with Firebase
     */
    fun isUserAuthenticated(): Boolean {
        return firebaseAuth.currentUser != null
    }

    /**
     * Clear Firebase authentication state (useful for app startup)
     */
    suspend fun clearAuthenticationState() {
        try {
            if (firebaseAuth.currentUser != null) {
                firebaseAuth.signOut()
                android.util.Log.d("FirebaseAuthService", "Cleared Firebase authentication state")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to clear Firebase authentication state: ${e.message}", e)
        }
    }

    /**
     * Get current Firebase user
     */
    fun getCurrentFirebaseUser() = firebaseAuth.currentUser

    /**
     * Sign out from Firebase
     */
    suspend fun signOut() {
        try {
            // Clear any existing authentication state
            if (firebaseAuth.currentUser != null) {
                firebaseAuth.signOut()
                android.util.Log.d("FirebaseAuthService", "User signed out from Firebase")
            } else {
                android.util.Log.d("FirebaseAuthService", "No user to sign out from Firebase")
            }
        } catch (e: Exception) {
            android.util.Log.e("FirebaseAuthService", "Failed to sign out from Firebase: ${e.message}", e)
        }
    }
}