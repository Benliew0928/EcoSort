package com.example.ecosort.data.firebase

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.toObject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirestoreService @Inject constructor() {
    
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }
    
    // Community collections
    private val communityPostsCollection by lazy { firestore.collection("community_posts") }
    private val communityCommentsCollection by lazy { firestore.collection("community_comments") }
    private val communityLikesCollection by lazy { firestore.collection("community_likes") }

    private val recycleBinsCollection by lazy { firestore.collection("recycleBins") }
    
    // User profiles collection
    private val usersCollection by lazy { firestore.collection("users") }
    
    // Recycled items collection
    private val recycledItemsCollection by lazy { firestore.collection("recycled_items") }
    
    // Points collections
    private val userPointsCollection by lazy { firestore.collection("user_points") }
    private val pointsTransactionsCollection by lazy { firestore.collection("points_transactions") }

    /**
     * NEW: Save a document to a specific collection. Used for RecycleBins.
     * @param collectionPath The name of the Firestore collection (e.g., "recycleBins")
     * @param data The HashMap data representing the bin.
     * @return Result with the document ID or error.
     */
    suspend fun saveDocument(collectionPath: String, data: HashMap<String, Any>): Result<String> {
        return try {
            android.util.Log.d("FirestoreService", "Saving document to $collectionPath")
            // Use the general firestore instance to access the collection path dynamically
            val docRef = firestore.collection(collectionPath).add(data).await()
            android.util.Log.d("FirestoreService", "Document saved with ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to save document to $collectionPath", e)
            Result.failure(e)
        }
    }

    /**
     * NEW: Get all recycle bins. Used for MapActivity.
     * Fetches all documents from the 'recycleBins' collection.
     */
    suspend fun getRecycleBins(): Result<List<Map<String, Any>>> {
        return try {
            // Fetch all documents in the collection
            val snapshot = recycleBinsCollection.get().await()

            // Convert the documents to a list of simple Maps for easy parsing in MapActivity
            val binList = snapshot.documents.map { document ->
                document.data ?: emptyMap<String, Any>()
            }
            Result.success(binList)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to get recycle bins", e)
            Result.failure(e)
        }
    }


    // ==================== COMMUNITY OPERATIONS ====================

    /**
     * Add a new community post to Firestore
     */
    suspend fun addCommunityPost(post: FirebaseCommunityPost): Result<String> {
        return try {
            android.util.Log.d("FirestoreService", "Adding community post: ${post.title}")
            // Use the local ID as the document ID to ensure consistency
            communityPostsCollection.document(post.id).set(post).await()
            android.util.Log.d("FirestoreService", "Community post added with ID: ${post.id}")
            Result.success(post.id)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to add community post", e)
            Result.failure(e)
        }
    }

    /**
     * Save user profile to Firebase
     */
    suspend fun saveUserProfile(userData: HashMap<String, Any>): Result<String> {
        return try {
            android.util.Log.d("FirestoreService", "Saving user profile to Firebase")
            val docRef = usersCollection.add(userData).await()
            android.util.Log.d("FirestoreService", "User profile saved with ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to save user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Update user profile in Firebase
     */
    suspend fun updateUserProfile(userId: String, userData: HashMap<String, Any>): Result<Unit> {
        return try {
            android.util.Log.d("FirestoreService", "Updating user profile in Firebase: $userId")
            
            // First try to update the document
            try {
                usersCollection.document(userId).update(userData).await()
                android.util.Log.d("FirestoreService", "User profile updated successfully")
            } catch (updateException: Exception) {
                // If update fails (document doesn't exist), try to set with merge
                android.util.Log.w("FirestoreService", "Update failed, trying to set with merge: ${updateException.message}")
                usersCollection.document(userId).set(userData, com.google.firebase.firestore.SetOptions.merge()).await()
                android.util.Log.d("FirestoreService", "User profile set with merge successfully")
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to update user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Get user profile from Firebase by username
     */
    suspend fun getUserProfileByUsername(username: String): Result<HashMap<String, Any>?> {
        return try {
            android.util.Log.d("FirestoreService", "Getting user profile by username: $username")
            val querySnapshot = usersCollection.whereEqualTo("username", username).get().await()
            
            if (querySnapshot.isEmpty) {
                android.util.Log.d("FirestoreService", "No user found with username: $username")
                Result.success(null)
            } else {
                val documents = querySnapshot.documents
                android.util.Log.d("FirestoreService", "Found ${documents.size} documents for username: $username")
                
                if (documents.size > 1) {
                    android.util.Log.w("FirestoreService", "Multiple documents found for username: $username, consolidating...")
                    return consolidateDuplicateUsers(username, documents)
                }
                
                val userData = documents.first().data as HashMap<String, Any>
                android.util.Log.d("FirestoreService", "Found user profile: $username")
                Result.success(userData)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to get user profile by username", e)
            Result.failure(e)
        }
    }

    /**
     * Consolidate duplicate user documents in Firebase
     * Keeps the document with the most recent lastActive timestamp
     */
    private suspend fun consolidateDuplicateUsers(username: String, documents: List<com.google.firebase.firestore.DocumentSnapshot>): Result<HashMap<String, Any>?> {
        return try {
            android.util.Log.d("FirestoreService", "Consolidating ${documents.size} duplicate documents for username: $username")
            
            // Sort documents by lastActive timestamp (most recent first)
            val sortedDocuments = documents.sortedByDescending { doc ->
                val lastActive = doc.data?.get("lastActive") as? Number
                lastActive?.toLong() ?: 0L
            }
            
            val primaryDocument = sortedDocuments.first()
            val duplicateDocuments = sortedDocuments.drop(1)
            
            android.util.Log.d("FirestoreService", "Primary document ID: ${primaryDocument.id}, lastActive: ${primaryDocument.data?.get("lastActive")}")
            
            // Merge data from all documents, prioritizing non-empty values
            val mergedData = mergeUserDocuments(sortedDocuments)
            
            // Update the primary document with merged data
            try {
                primaryDocument.reference.update(mergedData).await()
                android.util.Log.d("FirestoreService", "Updated primary document with merged data")
            } catch (e: Exception) {
                android.util.Log.w("FirestoreService", "Failed to update primary document, trying set with merge: ${e.message}")
                primaryDocument.reference.set(mergedData, com.google.firebase.firestore.SetOptions.merge()).await()
            }
            
            // Delete duplicate documents
            for (duplicateDoc in duplicateDocuments) {
                try {
                    android.util.Log.d("FirestoreService", "Deleting duplicate document: ${duplicateDoc.id}")
                    duplicateDoc.reference.delete().await()
                } catch (e: Exception) {
                    android.util.Log.w("FirestoreService", "Failed to delete duplicate document ${duplicateDoc.id}: ${e.message}")
                }
            }
            
            android.util.Log.d("FirestoreService", "Consolidated ${duplicateDocuments.size} duplicate documents for username: $username")
            Result.success(mergedData)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to consolidate duplicate users", e)
            Result.failure(e)
        }
    }

    /**
     * Merge data from multiple user documents, prioritizing non-empty values
     */
    private fun mergeUserDocuments(documents: List<com.google.firebase.firestore.DocumentSnapshot>): HashMap<String, Any> {
        val mergedData = HashMap<String, Any>()
        
        // Define the fields we want to merge
        val fieldsToMerge = listOf(
            "id", "firebaseUid", "username", "email", "userType", "createdAt",
            "itemsRecycled", "totalPoints", "profileImageUrl", "bio", "location",
            "joinDate", "lastActive", "profileCompletion", "privacySettings",
            "achievements", "socialLinks", "preferences"
        )
        
        // For each field, find the best value across all documents
        for (field in fieldsToMerge) {
            var bestValue: Any? = null
            
            // Priority order: non-empty string, non-zero number, non-null value
            for (doc in documents) {
                val value = doc.data?.get(field)
                if (value != null) {
                    when (field) {
                        "bio", "location", "profileImageUrl", "privacySettings", 
                        "achievements", "socialLinks", "preferences" -> {
                            // For string fields, prefer non-empty values
                            if (value is String && value.isNotBlank()) {
                                bestValue = value
                                break
                            } else if (bestValue == null) {
                                bestValue = value
                            }
                        }
                        "itemsRecycled", "totalPoints", "profileCompletion" -> {
                            // For number fields, prefer non-zero values
                            if (value is Number && value.toInt() > 0) {
                                bestValue = value
                                break
                            } else if (bestValue == null) {
                                bestValue = value
                            }
                        }
                        "lastActive" -> {
                            // For lastActive, prefer the most recent (highest value)
                            if (value is Number) {
                                if (bestValue == null || (bestValue as Number).toLong() < value.toLong()) {
                                    bestValue = value
                                }
                            }
                        }
                        else -> {
                            // For other fields, prefer non-null values
                            if (bestValue == null) {
                                bestValue = value
                            }
                        }
                    }
                }
            }
            
            // Set the best value found, or empty string for string fields if nothing found
            mergedData[field] = when (field) {
                "bio", "location", "profileImageUrl", "privacySettings", 
                "achievements", "socialLinks", "preferences" -> bestValue ?: ""
                "itemsRecycled", "totalPoints", "profileCompletion" -> bestValue ?: 0
                "lastActive" -> bestValue ?: System.currentTimeMillis()
                else -> bestValue ?: ""
            }
        }
        
        android.util.Log.d("FirestoreService", "Merged data: bio='${mergedData["bio"]}', location='${mergedData["location"]}', profileImageUrl='${mergedData["profileImageUrl"]}'")
        return mergedData
    }

    /**
     * Clean up all duplicate user documents in Firebase
     * This method should be called periodically to consolidate duplicates
     */
    suspend fun cleanupAllDuplicateUsers(): Result<Int> {
        return try {
            android.util.Log.d("FirestoreService", "Starting cleanup of all duplicate users in Firebase")
            
            // Get all user documents
            val querySnapshot = usersCollection.get().await()
            val allDocuments = querySnapshot.documents
            
            // Group documents by username
            val documentsByUsername = allDocuments.groupBy { doc ->
                doc.data?.get("username") as? String ?: "unknown"
            }
            
            var cleanedCount = 0
            
            for ((username, documents) in documentsByUsername) {
                if (documents.size > 1) {
                    android.util.Log.w("FirestoreService", "Found ${documents.size} duplicate documents for username: $username")
                    
                    // Sort documents by lastActive timestamp (most recent first)
                    val sortedDocuments = documents.sortedByDescending { doc ->
                        val lastActive = doc.data?.get("lastActive") as? Number
                        lastActive?.toLong() ?: 0L
                    }
                    
                    val primaryDocument = sortedDocuments.first()
                    val duplicateDocuments = sortedDocuments.drop(1)
                    
                    android.util.Log.d("FirestoreService", "Keeping primary document: ${primaryDocument.id} for username: $username")
                    
                    // Merge data from all documents, prioritizing non-empty values
                    val mergedData = mergeUserDocuments(sortedDocuments)
                    
                    // Update the primary document with merged data
                    try {
                        primaryDocument.reference.update(mergedData).await()
                        android.util.Log.d("FirestoreService", "Updated primary document with merged data for username: $username")
                    } catch (e: Exception) {
                        android.util.Log.w("FirestoreService", "Failed to update primary document, trying set with merge: ${e.message}")
                        primaryDocument.reference.set(mergedData, com.google.firebase.firestore.SetOptions.merge()).await()
                    }
                    
                    // Delete duplicate documents
                    for (duplicateDoc in duplicateDocuments) {
                        try {
                            android.util.Log.d("FirestoreService", "Deleting duplicate document: ${duplicateDoc.id}")
                            duplicateDoc.reference.delete().await()
                            cleanedCount++
                        } catch (e: Exception) {
                            android.util.Log.w("FirestoreService", "Failed to delete duplicate document ${duplicateDoc.id}: ${e.message}")
                        }
                    }
                }
            }
            
            android.util.Log.d("FirestoreService", "Firebase cleanup completed. Removed $cleanedCount duplicate documents")
            Result.success(cleanedCount)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to cleanup duplicate users in Firebase", e)
            Result.failure(e)
        }
    }

    suspend fun getUserProfileByFirebaseUid(firebaseUid: String): Result<HashMap<String, Any>?> {
        return try {
            android.util.Log.d("FirestoreService", "Getting user profile by firebaseUid: $firebaseUid")
            val querySnapshot = usersCollection.whereEqualTo("firebaseUid", firebaseUid).get().await()
            
            if (querySnapshot.isEmpty) {
                android.util.Log.d("FirestoreService", "No user found with firebaseUid: $firebaseUid")
                Result.success(null)
            } else {
                val userData = querySnapshot.documents.first().data as HashMap<String, Any>
                android.util.Log.d("FirestoreService", "Found user profile by firebaseUid: $firebaseUid")
                Result.success(userData)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to get user profile by firebaseUid", e)
            Result.failure(e)
        }
    }

    /**
     * Get user profile from Firebase by ID
     */
    suspend fun getUserProfileById(userId: String): Result<HashMap<String, Any>?> {
        return try {
            android.util.Log.d("FirestoreService", "Getting user profile by ID: $userId")
            val document = usersCollection.document(userId).get().await()
            
            if (!document.exists()) {
                android.util.Log.d("FirestoreService", "No user found with ID: $userId")
                Result.success(null)
            } else {
                val userData = document.data as HashMap<String, Any>
                android.util.Log.d("FirestoreService", "Found user profile with ID: $userId")
                Result.success(userData)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to get user profile by ID", e)
            Result.failure(e)
        }
    }

    /**
     * Get all user profiles from Firebase
     */
    suspend fun getAllUserProfiles(): Result<List<HashMap<String, Any>>> {
        return try {
            android.util.Log.d("FirestoreService", "Getting all user profiles from Firebase")
            val querySnapshot = usersCollection.get().await()
            val userProfiles = querySnapshot.documents.map { it.data as HashMap<String, Any> }
            android.util.Log.d("FirestoreService", "Retrieved ${userProfiles.size} user profiles")
            Result.success(userProfiles)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to get all user profiles", e)
            Result.failure(e)
        }
    }

    /**
     * Delete user profile from Firebase
     */
    suspend fun deleteUserProfile(userId: String): Result<Unit> {
        return try {
            android.util.Log.d("FirestoreService", "Deleting user profile from Firebase: $userId")
            usersCollection.document(userId).delete().await()
            android.util.Log.d("FirestoreService", "User profile deleted successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to delete user profile", e)
            Result.failure(e)
        }
    }

    /**
     * Get all community posts with real-time updates
     */
    fun getAllCommunityPosts(): Flow<List<FirebaseCommunityPost>> = callbackFlow {
        val listener = communityPostsCollection
            .orderBy("postedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreService", "Error listening to community posts", error)
                    close(error)
                    return@addSnapshotListener
                }

                val posts = snapshot?.documents?.mapNotNull { document ->
                    document.toObject<FirebaseCommunityPost>()?.copy(id = document.id)
                } ?: emptyList()

                trySend(posts)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Get community posts by type
     */
    fun getCommunityPostsByType(postType: String): Flow<List<FirebaseCommunityPost>> = callbackFlow {
        val listener = communityPostsCollection
            .whereEqualTo("postType", postType)
            .orderBy("postedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreService", "Error listening to community posts by type", error)
                    close(error)
                    return@addSnapshotListener
                }

                val posts = snapshot?.documents?.mapNotNull { document ->
                    document.toObject<FirebaseCommunityPost>()?.copy(id = document.id)
                } ?: emptyList()

                trySend(posts)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Update community post likes count
     */
    suspend fun updatePostLikes(postId: String, likesCount: Int): Result<Unit> {
        return try {
            communityPostsCollection.document(postId)
                .update("likesCount", likesCount)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to update post likes", e)
            Result.failure(e)
        }
    }

    /**
     * Update community post comments count
     */
    suspend fun updatePostComments(postId: String, commentsCount: Int): Result<Unit> {
        return try {
            communityPostsCollection.document(postId)
                .update("commentsCount", commentsCount)
                .await()
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to update post comments", e)
            Result.failure(e)
        }
    }

    /**
     * Add a community comment
     */
    suspend fun addCommunityComment(comment: FirebaseCommunityComment): Result<String> {
        return try {
            android.util.Log.d("FirestoreService", "Adding community comment to post: ${comment.postId}")
            // Use the local ID as the document ID to ensure consistency
            communityCommentsCollection.document(comment.id).set(comment).await()
            android.util.Log.d("FirestoreService", "Community comment added with ID: ${comment.id}")
            Result.success(comment.id)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to add community comment", e)
            Result.failure(e)
        }
    }

    /**
     * Get comments for a specific post
     */
    fun getCommentsForPost(postId: String): Flow<List<FirebaseCommunityComment>> = callbackFlow {
        val listener = communityCommentsCollection
            .whereEqualTo("postId", postId)
            .orderBy("postedAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("FirestoreService", "Error listening to comments", error)
                    close(error)
                    return@addSnapshotListener
                }

                val comments = snapshot?.documents?.mapNotNull { document ->
                    document.toObject<FirebaseCommunityComment>()?.copy(id = document.id)
                } ?: emptyList()

                trySend(comments)
            }

        awaitClose { listener.remove() }
    }

    /**
     * Add or remove a like for a post
     */
    suspend fun togglePostLike(postId: String, userId: String): Result<Boolean> {
        return try {
            val likeQuery = communityLikesCollection
                .whereEqualTo("postId", postId)
                .whereEqualTo("userId", userId)
                .get()
                .await()

            if (likeQuery.isEmpty) {
                // Add like
                val like = FirebaseCommunityLike(
                    postId = postId,
                    userId = userId
                )
                communityLikesCollection.add(like).await()
                Result.success(true)
            } else {
                // Remove like
                likeQuery.documents.first().reference.delete().await()
                Result.success(false)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to toggle post like", e)
            Result.failure(e)
        }
    }

    /**
     * Get like count for a post
     */
    suspend fun getPostLikeCount(postId: String): Result<Int> {
        return try {
            val likeQuery = communityLikesCollection
                .whereEqualTo("postId", postId)
                .get()
                .await()
            Result.success(likeQuery.size())
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to get post like count", e)
            Result.failure(e)
        }
    }

    /**
     * Check if user has liked a post
     */
    suspend fun hasUserLikedPost(postId: String, userId: String): Result<Boolean> {
        return try {
            val likeQuery = communityLikesCollection
                .whereEqualTo("postId", postId)
                .whereEqualTo("userId", userId)
                .get()
                .await()
            Result.success(!likeQuery.isEmpty)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to check if user liked post", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a community comment from Firebase
     */
    suspend fun deleteCommunityComment(commentId: String): Result<Unit> {
        return try {
            communityCommentsCollection.document(commentId).delete().await()
            android.util.Log.d("FirestoreService", "Community comment deleted: $commentId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to delete community comment", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a community post from Firebase
     */
    suspend fun deleteCommunityPost(postId: String): Result<Unit> {
        return try {
            // First delete related comments
            try {
                val commentsSnap = communityCommentsCollection
                    .whereEqualTo("postId", postId)
                    .get()
                    .await()
                for (doc in commentsSnap.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.e("FirestoreService", "Failed to delete comments for post $postId", e)
            }

            // Then delete related likes
            try {
                val likesSnap = communityLikesCollection
                    .whereEqualTo("postId", postId)
                    .get()
                    .await()
                for (doc in likesSnap.documents) {
                    doc.reference.delete().await()
                }
            } catch (e: Exception) {
                android.util.Log.e("FirestoreService", "Failed to delete likes for post $postId", e)
            }

            communityPostsCollection.document(postId).delete().await()
            android.util.Log.d("FirestoreService", "Community post deleted: $postId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to delete community post", e)
            Result.failure(e)
        }
    }

    // FirestoreService.kt (Add this block inside the FirestoreService class)

// ==================== RECYCLE BIN ADMIN OPERATIONS ====================

    /**
     * Admin: Updates the verification status of a recycling bin.
     */
    suspend fun updateBinVerification(documentId: String, isVerified: Boolean): Boolean {
        return try {
            android.util.Log.d("FirestoreService", "Updating verification for bin $documentId to $isVerified")

            // NOTE: The getRecycleBins function uses the collection name "recycleBins"
            // Ensure this is the correct collection name for writing/deleting.

            recycleBinsCollection.document(documentId)
                .update("isVerified", isVerified)
                .await() // Waits for the Firestore operation to complete

            android.util.Log.d("FirestoreService", "Bin verification updated successfully.")
            true
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error updating verification for $documentId", e)
            false
        }
    }

    /**
     * Admin: Deletes a specific recycling bin document.
     */
    suspend fun deleteBin(documentId: String): Boolean {
        return try {
            android.util.Log.d("FirestoreService", "Deleting bin document: $documentId")

            // NOTE: Ensure 'recycleBinsCollection' is the correct collection name.

            recycleBinsCollection.document(documentId)
                .delete()
                .await() // Waits for the Firestore operation to complete

            android.util.Log.d("FirestoreService", "Bin document deleted successfully.")
            true
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error deleting bin $documentId", e)
            false
        }
    }

// ==================== RECYCLED ITEMS ====================

    /**
     * Save recycled item to Firebase
     */
    suspend fun saveRecycledItem(itemData: HashMap<String, Any>): com.example.ecosort.data.model.Result<Unit> {
        return try {
            val itemId = itemData["id"] as? Long ?: 0L
            recycledItemsCollection.document(itemId.toString())
                .set(itemData)
                .await()
            
            android.util.Log.d("FirestoreService", "Recycled item saved successfully: $itemId")
            com.example.ecosort.data.model.Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error saving recycled item", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }
    
    /**
     * Get user's recycled items from Firebase
     */
    suspend fun getUserRecycledItems(userId: String): com.example.ecosort.data.model.Result<List<HashMap<String, Any>>> {
        return try {
            val querySnapshot = recycledItemsCollection
                .whereEqualTo("userId", userId)
                .orderBy("recycledDate", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val items = querySnapshot.documents.map { document ->
                val data = document.data as HashMap<String, Any>
                data["id"] = document.id.toLongOrNull() ?: 0L
                data
            }
            
            android.util.Log.d("FirestoreService", "Retrieved ${items.size} recycled items for user: $userId")
            com.example.ecosort.data.model.Result.Success(items)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error getting user recycled items", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }
    
    /**
     * Delete recycled item from Firebase
     */
    suspend fun deleteRecycledItem(itemId: Long): com.example.ecosort.data.model.Result<Unit> {
        return try {
            recycledItemsCollection.document(itemId.toString())
                .delete()
                .await()
            
            android.util.Log.d("FirestoreService", "Recycled item deleted successfully: $itemId")
            com.example.ecosort.data.model.Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error deleting recycled item", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }
    
    /**
     * Get recycled item by ID from Firebase
     */
    suspend fun getRecycledItemById(itemId: Long): com.example.ecosort.data.model.Result<HashMap<String, Any>?> {
        return try {
            val document = recycledItemsCollection.document(itemId.toString())
                .get()
                .await()
            
            if (document.exists()) {
                val data = document.data as HashMap<String, Any>
                data["id"] = itemId
                android.util.Log.d("FirestoreService", "Retrieved recycled item: $itemId")
                com.example.ecosort.data.model.Result.Success(data)
            } else {
                android.util.Log.d("FirestoreService", "Recycled item not found: $itemId")
                com.example.ecosort.data.model.Result.Success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error getting recycled item by ID", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }

    // ==================== USER POINTS ====================

    /**
     * Save user points to Firebase
     */
    suspend fun saveUserPoints(pointsData: HashMap<String, Any>): com.example.ecosort.data.model.Result<Unit> {
        return try {
            val userId = pointsData["userId"] as? String ?: throw Exception("User ID not found")
            userPointsCollection.document(userId)
                .set(pointsData)
                .await()
            
            android.util.Log.d("FirestoreService", "User points saved successfully: $userId")
            com.example.ecosort.data.model.Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error saving user points", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }
    
    /**
     * Get user points from Firebase
     */
    suspend fun getUserPoints(userId: String): com.example.ecosort.data.model.Result<HashMap<String, Any>?> {
        return try {
            val document = userPointsCollection.document(userId)
                .get()
                .await()
            
            if (document.exists()) {
                val data = document.data as HashMap<String, Any>
                data["id"] = document.id.toLongOrNull() ?: 0L
                android.util.Log.d("FirestoreService", "Retrieved user points: $userId")
                com.example.ecosort.data.model.Result.Success(data)
            } else {
                android.util.Log.d("FirestoreService", "User points not found: $userId")
                com.example.ecosort.data.model.Result.Success(null)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error getting user points", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }

    // ==================== POINTS TRANSACTIONS ====================

    /**
     * Save points transaction to Firebase
     */
    suspend fun savePointsTransaction(transactionData: HashMap<String, Any>): com.example.ecosort.data.model.Result<Unit> {
        return try {
            val transactionId = transactionData["id"] as? Long ?: 0L
            pointsTransactionsCollection.document(transactionId.toString())
                .set(transactionData)
                .await()
            
            android.util.Log.d("FirestoreService", "Points transaction saved successfully: $transactionId")
            com.example.ecosort.data.model.Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error saving points transaction", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }
    
    /**
     * Get user points transactions from Firebase
     */
    suspend fun getUserPointsTransactions(userId: String): com.example.ecosort.data.model.Result<List<HashMap<String, Any>>> {
        return try {
            val querySnapshot = pointsTransactionsCollection
                .whereEqualTo("userId", userId)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .await()
            
            val transactions = querySnapshot.documents.map { document ->
                val data = document.data as HashMap<String, Any>
                data["id"] = document.id.toLongOrNull() ?: 0L
                data
            }
            
            android.util.Log.d("FirestoreService", "Retrieved ${transactions.size} points transactions for user: $userId")
            com.example.ecosort.data.model.Result.Success(transactions)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error getting user points transactions", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }
    
    /**
     * Delete points transaction from Firebase
     */
    suspend fun deletePointsTransaction(transactionId: Long): com.example.ecosort.data.model.Result<Unit> {
        return try {
            pointsTransactionsCollection.document(transactionId.toString())
                .delete()
                .await()
            
            android.util.Log.d("FirestoreService", "Points transaction deleted successfully: $transactionId")
            com.example.ecosort.data.model.Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error deleting points transaction", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }

// ====================================================================

}
