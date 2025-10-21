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
    private val marketplaceCollection by lazy { firestore.collection("marketplace_items") }
    
    // Community collections
    private val communityPostsCollection by lazy { firestore.collection("community_posts") }
    private val communityCommentsCollection by lazy { firestore.collection("community_comments") }
    private val communityLikesCollection by lazy { firestore.collection("community_likes") }

    private val recycleBinsCollection by lazy { firestore.collection("recycleBins") }

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

    /**
     * Add a new marketplace item to Firestore
     * @param item The marketplace item to add
     * @return Result with the document ID or error
     */
    suspend fun addMarketplaceItem(item: FirebaseMarketplaceItem): Result<String> {
        return try {
            android.util.Log.d("FirestoreService", "Adding marketplace item: ${item.title}")
            val docRef = marketplaceCollection.add(item).await()
            android.util.Log.d("FirestoreService", "Item added with ID: ${docRef.id}")
            Result.success(docRef.id)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to add marketplace item", e)
            Result.failure(e)
        }
    }
    
    /**
     * Update an existing marketplace item
     * @param itemId The document ID of the item to update
     * @param item The updated marketplace item
     * @return Success or failure result
     */
    suspend fun updateMarketplaceItem(itemId: String, item: FirebaseMarketplaceItem): Result<Unit> {
        return try {
            marketplaceCollection.document(itemId).set(item).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Delete a marketplace item
     * @param itemId The document ID of the item to delete
     * @return Success or failure result
     */
    suspend fun deleteMarketplaceItem(itemId: String): Result<Unit> {
        return try {
            marketplaceCollection.document(itemId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get a single marketplace item by ID
     * @param itemId The document ID of the item
     * @return Result with the item or error
     */
    suspend fun getMarketplaceItem(itemId: String): Result<FirebaseMarketplaceItem?> {
        return try {
            val document = marketplaceCollection.document(itemId).get().await()
            val item = document.toObject<FirebaseMarketplaceItem>()
            Result.success(item)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get all marketplace items as a Flow for real-time updates
     * @param limit Maximum number of items to return (default: 50)
     * @return Flow of list of marketplace items
     */
    fun getAllMarketplaceItems(limit: Int = 50): Flow<List<FirebaseMarketplaceItem>> = callbackFlow {
        try {
            val listener = marketplaceCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("FirestoreService", "Error getting marketplace items", error)
                        trySend(emptyList()) // Send empty list instead of closing
                        return@addSnapshotListener
                    }
                    
                    val items = snapshot?.documents?.mapNotNull { document ->
                        try {
                            document.toObject<FirebaseMarketplaceItem>()?.copy(id = document.id)
                        } catch (e: Exception) {
                            android.util.Log.e("FirestoreService", "Error parsing document ${document.id}", e)
                            null
                        }
                    } ?: emptyList()
                    
                    trySend(items)
                }
            
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error setting up marketplace items listener", e)
            trySend(emptyList())
            close()
        }
    }
    
    /**
     * Get featured marketplace items (top 3 most recent)
     * @return Flow of list of featured marketplace items
     */
    fun getFeaturedMarketplaceItems(): Flow<List<FirebaseMarketplaceItem>> = callbackFlow {
        try {
            val listener = marketplaceCollection
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(3)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        android.util.Log.e("FirestoreService", "Error getting featured items", error)
                        trySend(emptyList()) // Send empty list instead of closing
                        return@addSnapshotListener
                    }
                    
                    val items = snapshot?.documents?.mapNotNull { document ->
                        try {
                            document.toObject<FirebaseMarketplaceItem>()?.copy(id = document.id)
                        } catch (e: Exception) {
                            android.util.Log.e("FirestoreService", "Error parsing featured document ${document.id}", e)
                            null
                        }
                    } ?: emptyList()
                    
                    trySend(items)
                }
            
            awaitClose { listener.remove() }
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Error setting up featured items listener", e)
            trySend(emptyList())
            close()
        }
    }
    
    /**
     * Get marketplace items by owner ID
     * @param ownerId The owner ID to filter by
     * @return Flow of list of marketplace items
     */
    fun getMarketplaceItemsByOwner(ownerId: String): Flow<List<FirebaseMarketplaceItem>> = callbackFlow {
        val listener = marketplaceCollection
            .whereEqualTo("ownerId", ownerId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val items = snapshot?.documents?.mapNotNull { document ->
                    document.toObject<FirebaseMarketplaceItem>()?.copy(id = document.id)
                } ?: emptyList()
                
                trySend(items)
            }
        
        awaitClose { listener.remove() }
    }
    
    /**
     * Search marketplace items by title
     * @param query The search query
     * @return Flow of list of matching marketplace items
     */
    fun searchMarketplaceItems(query: String): Flow<List<FirebaseMarketplaceItem>> = callbackFlow {
        val listener = marketplaceCollection
            .orderBy("title")
            .startAt(query)
            .endAt(query + "\uf8ff")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                
                val items = snapshot?.documents?.mapNotNull { document ->
                    document.toObject<FirebaseMarketplaceItem>()?.copy(id = document.id)
                } ?: emptyList()
                
                trySend(items)
            }
        
        awaitClose { listener.remove() }
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
            communityPostsCollection.document(postId).delete().await()
            android.util.Log.d("FirestoreService", "Community post deleted: $postId")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("FirestoreService", "Failed to delete community post", e)
            Result.failure(e)
        }
    }

}
