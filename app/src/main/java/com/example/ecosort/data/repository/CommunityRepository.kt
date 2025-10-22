package com.example.ecosort.data.repository

import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.FirebaseStorageService
import com.example.ecosort.data.firebase.toFirebaseModel
import com.example.ecosort.data.firebase.toLocalModel
import com.example.ecosort.data.local.CommunityCommentDao
import com.example.ecosort.data.local.CommunityLikeDao
import com.example.ecosort.data.local.CommunityPostDao
import com.example.ecosort.data.model.CommunityComment
import com.example.ecosort.data.model.CommunityLike
import com.example.ecosort.data.model.CommunityPost
import com.example.ecosort.data.model.InputType
import com.example.ecosort.data.model.PostType
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.preferences.UserPreferencesManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityRepository @Inject constructor(
    private val communityPostDao: CommunityPostDao,
    private val communityCommentDao: CommunityCommentDao,
    private val communityLikeDao: CommunityLikeDao,
    private val firestoreService: FirestoreService,
    private val firebaseStorageService: FirebaseStorageService,
    private val userPreferencesManager: UserPreferencesManager
) {

    /**
     * Get all community posts - combines local and Firebase data
     */
    fun getAllCommunityPosts(): Flow<List<CommunityPost>> {
        return communityPostDao.getAllPosts()
    }

    /**
     * Get community posts by type - combines local and Firebase data
     */
    fun getCommunityPostsByType(postType: PostType): Flow<List<CommunityPost>> {
        return communityPostDao.getPostsByType(postType)
    }

    /**
     * Add a new community post
     */
    suspend fun addCommunityPost(
        title: String,
        content: String,
        postType: PostType,
        inputType: InputType = InputType.TEXT,
        imageUrls: List<String> = emptyList(),
        videoUrl: String? = null,
        location: String? = null,
        tags: List<String> = emptyList()
    ): Result<CommunityPost> {
        return try {
            // Get current user session
            val userSession = userPreferencesManager.getCurrentUser()
            val authorId = userSession?.userId ?: 1L
            val authorName = userSession?.username ?: "Anonymous User"
            
            val newPost = CommunityPost(
                authorId = authorId,
                authorName = authorName,
                authorAvatar = null,
                title = title,
                content = content,
                postType = postType,
                inputType = inputType,
                imageUrls = imageUrls,
                videoUrl = videoUrl,
                location = location,
                tags = tags
            )

            // Save to local database
            val localPostId = communityPostDao.insertPost(newPost)
            val localPost = newPost.copy(id = localPostId)

            // Sync to Firebase for real-time updates
            try {
                firestoreService.addCommunityPost(localPost.toFirebaseModel())
                android.util.Log.d("CommunityRepository", "Post synced to Firebase successfully")
            } catch (e: Exception) {
                android.util.Log.e("CommunityRepository", "Error syncing post to Firebase", e)
                // Continue with local success even if Firebase fails
            }

            Result.Success(localPost)
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error adding community post", e)
            Result.Error(e)
        }
    }

    /**
     * Toggle like status for a post
     */
    suspend fun togglePostLike(postId: Long): Result<Boolean> {
        return try {
            val currentUserId = 1L // Default user ID

            val existingLike = communityLikeDao.getUserLikeForPost(postId, currentUserId)
            val isCurrentlyLiked = existingLike != null

            if (isCurrentlyLiked) {
                // Remove like
                communityLikeDao.removeLike(postId, currentUserId)
                communityPostDao.decrementLikes(postId)
            } else {
                // Add like
                communityLikeDao.insertLike(CommunityLike(postId = postId, userId = currentUserId))
                communityPostDao.incrementLikes(postId)
            }

            Result.Success(!isCurrentlyLiked)
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error toggling post like", e)
            Result.Error(e)
        }
    }

    /**
     * Check if the current user has liked a specific post
     */
    suspend fun hasUserLikedPost(postId: Long): Boolean {
        return try {
            val currentUserId = 1L // Default user ID
            communityLikeDao.getUserLikeForPost(postId, currentUserId) != null
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error checking if user liked post", e)
            false
        }
    }

    /**
     * Get comments for a specific post
     */
    fun getCommentsForPost(postId: Long): Flow<List<CommunityComment>> {
        return communityCommentDao.getCommentsForPost(postId)
    }

    /**
     * Add a new comment to a post
     */
    suspend fun addComment(
        postId: Long, 
        authorId: Long, 
        authorName: String, 
        content: String, 
        parentCommentId: Long? = null
    ): Result<CommunityComment> {
        return try {
            // Get current user session for validation
            val userSession = userPreferencesManager.getCurrentUser()
            val actualAuthorId = userSession?.userId ?: authorId
            val actualAuthorName = userSession?.username ?: authorName
            
            val newComment = CommunityComment(
                postId = postId,
                authorId = actualAuthorId,
                authorName = actualAuthorName,
                authorAvatar = null,
                content = content,
                parentCommentId = parentCommentId
            )

            val localCommentId = communityCommentDao.insertComment(newComment)
            val localComment = newComment.copy(id = localCommentId)

            // Update post comment count locally
            communityPostDao.incrementComments(postId)

            // Sync to Firebase
            try {
                val firebaseComment = localComment.toFirebaseModel()
                val firebaseResult = firestoreService.addCommunityComment(firebaseComment)
                if (firebaseResult.isSuccess) {
                    // Update the local comment with the Firebase ID
                    val updatedComment = localComment.copy(firebaseId = firebaseResult.getOrNull() ?: "")
                    communityCommentDao.updateComment(updatedComment)
                    android.util.Log.d("CommunityRepository", "Comment synced to Firebase successfully")
                } else {
                    android.util.Log.e("CommunityRepository", "Error syncing comment to Firebase", firebaseResult.exceptionOrNull())
                }
            } catch (e: Exception) {
                android.util.Log.e("CommunityRepository", "Error syncing comment to Firebase", e)
                // Continue with local success even if Firebase fails
            }

            Result.Success(localComment)
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error adding comment", e)
            Result.Error(e)
        }
    }

    /**
     * Delete a comment
     */
    suspend fun deleteComment(commentId: Long): Result<Unit> {
        return try {
            // Get the comment to find the post ID
            val comment = communityCommentDao.getCommentById(commentId)
            if (comment != null) {
                // Delete the comment
                communityCommentDao.deleteComment(commentId)
                // Decrement post comment count
                communityPostDao.decrementComments(comment.postId)

                // Sync to Firebase for real-time updates
                try {
                    if (comment.firebaseId.isNotEmpty()) {
                        firestoreService.deleteCommunityComment(comment.firebaseId)
                        android.util.Log.d("CommunityRepository", "Comment deletion synced to Firebase successfully")
                    } else {
                        android.util.Log.w("CommunityRepository", "Comment has no Firebase ID, skipping Firebase deletion")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CommunityRepository", "Error syncing comment deletion to Firebase", e)
                    // Continue with local success even if Firebase fails
                }
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error deleting comment", e)
            Result.Error(e)
        }
    }

    /**
     * Delete a post
     */
    suspend fun deletePost(postId: Long): Result<Unit> {
        return try {
            // Get the post first to access image URLs
            val post = communityPostDao.getPostById(postId)
            
            if (post == null) {
                android.util.Log.w("CommunityRepository", "Post with ID $postId not found")
                return Result.Error(Exception("Post not found"))
            }
            
            // Check if current user is the author of the post
            val currentUser = userPreferencesManager.getCurrentUser()
            val currentUserId = currentUser?.userId ?: 0L
            
            if (currentUserId != post.authorId) {
                android.util.Log.w("CommunityRepository", "User $currentUserId is not authorized to delete post ${post.id} (author: ${post.authorId})")
                return Result.Error(Exception("You can only delete your own posts"))
            }
            
            android.util.Log.d("CommunityRepository", "Deleting post: ${post.title} (ID: $postId, Firebase ID: ${post.firebaseId})")
            
            // Delete images from Firebase Storage
            post.imageUrls.forEach { imageUrl ->
                if (imageUrl.startsWith("https://firebasestorage.googleapis.com/")) {
                    try {
                        deleteImageFromFirebase(imageUrl)
                        android.util.Log.d("CommunityRepository", "Deleted image: $imageUrl")
                    } catch (e: Exception) {
                        android.util.Log.e("CommunityRepository", "Error deleting image: $imageUrl", e)
                    }
                }
            }
            
            // Delete from local database
            communityPostDao.deletePost(postId)
            communityCommentDao.deleteCommentsForPost(postId)
            communityLikeDao.deleteLikesForPost(postId)
            android.util.Log.d("CommunityRepository", "Post deleted from local database successfully")

            // Delete from Firebase
            try {
                if (post.firebaseId.isNotEmpty()) {
                    firestoreService.deleteCommunityPost(post.firebaseId)
                    android.util.Log.d("CommunityRepository", "Post deleted from Firebase successfully")
                } else {
                    android.util.Log.w("CommunityRepository", "Post has no Firebase ID, skipping Firebase deletion")
                }
            } catch (e: Exception) {
                android.util.Log.e("CommunityRepository", "Error deleting post from Firebase", e)
                // Continue with local success even if Firebase fails
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error deleting post", e)
            Result.Error(e)
        }
    }

    /**
     * Get posts by a specific user
     */
    fun getUserPosts(userId: Long): Flow<List<CommunityPost>> {
        return communityPostDao.getUserPosts(userId)
    }

    /**
     * Search posts by tag - combines local and Firebase data
     */
    fun searchPostsByTag(tag: String): Flow<List<CommunityPost>> {
        // For tag search, we'll get all posts and filter by tag
        // This is because Firestore doesn't support array-contains queries easily
        return getAllCommunityPosts().map { allPosts ->
            allPosts.filter { post ->
                post.tags.any { it.contains(tag, ignoreCase = true) }
            }
        }
    }

    /**
     * Upload image to Firebase Storage
     */
    suspend fun uploadImageToFirebase(imageUri: android.net.Uri, fileName: String): com.example.ecosort.data.model.Result<String> {
        return try {
            val result = firebaseStorageService.uploadCommunityImage(imageUri, fileName)
            if (result.isSuccess) {
                com.example.ecosort.data.model.Result.Success(result.getOrNull() ?: "")
            } else {
                com.example.ecosort.data.model.Result.Error((result.exceptionOrNull() as? Exception) ?: Exception("Upload failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error uploading image to Firebase", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }

    /**
     * Upload video to Firebase Storage
     */
    suspend fun uploadVideoToFirebase(videoUri: android.net.Uri, fileName: String): com.example.ecosort.data.model.Result<String> {
        return try {
            val result = firebaseStorageService.uploadCommunityVideo(videoUri, fileName)
            if (result.isSuccess) {
                com.example.ecosort.data.model.Result.Success(result.getOrNull() ?: "")
            } else {
                com.example.ecosort.data.model.Result.Error((result.exceptionOrNull() as? Exception) ?: Exception("Upload failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error uploading video to Firebase", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }

    /**
     * Delete image from Firebase Storage
     */
    suspend fun deleteImageFromFirebase(imageUrl: String): com.example.ecosort.data.model.Result<Unit> {
        return try {
            val result = firebaseStorageService.deleteCommunityImage(imageUrl)
            if (result.isSuccess) {
                com.example.ecosort.data.model.Result.Success(Unit)
            } else {
                com.example.ecosort.data.model.Result.Error((result.exceptionOrNull() as? Exception) ?: Exception("Delete failed"))
            }
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error deleting image from Firebase", e)
            com.example.ecosort.data.model.Result.Error(e)
        }
    }


    /**
     * Clear all demo/sample posts from the database
     */
    suspend fun clearAllPosts(): Result<Unit> {
        return try {
            // Get all posts first
            val allPosts = communityPostDao.getAllPosts().first()
            
            // Delete all posts and related data
            allPosts.forEach { post ->
                communityPostDao.deletePost(post.id)
                communityCommentDao.deleteCommentsForPost(post.id)
                communityLikeDao.deleteLikesForPost(post.id)
            }
            
            android.util.Log.d("CommunityRepository", "Cleared ${allPosts.size} demo posts from database")
            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("CommunityRepository", "Error clearing posts", e)
            Result.Error(e)
        }
    }
}
