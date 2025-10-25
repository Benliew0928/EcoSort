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
            usersCollection.document(userId).set(userData).await()
            android.util.Log.d("FirestoreService", "User profile updated successfully")
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
                val userData = querySnapshot.documents.first().data as HashMap<String, Any>
                android.util.Log.d("FirestoreService", "Found user profile: $username")
                Result.success(userData)
            }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to get user profile by username", e)
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

// ====================================================================

}
