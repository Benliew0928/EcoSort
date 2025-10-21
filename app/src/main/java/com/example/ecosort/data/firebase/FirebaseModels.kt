package com.example.ecosort.data.firebase

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp

/**
 * Firestore data model for marketplace items
 */
data class FirebaseMarketplaceItem(
    @DocumentId
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val price: Double = 0.0,
    val imageUrl: String = "",
    val ownerId: String = "",
    val ownerName: String = "",
    val category: String = "General",
    val condition: String = "Good",
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {
    // No-argument constructor for Firestore
    constructor() : this(
        id = "",
        title = "",
        description = "",
        price = 0.0,
        imageUrl = "",
        ownerId = "",
        ownerName = "",
        category = "General",
        condition = "Good",
        createdAt = null,
        updatedAt = null
    )
}

/**
 * Extension function to convert Firebase model to local Room model
 */
fun FirebaseMarketplaceItem.toLocalModel(): com.example.ecosort.data.model.MarketplaceItem {
    return com.example.ecosort.data.model.MarketplaceItem(
        id = this.id.hashCode().toLong(), // Convert string ID to long for Room
        title = this.title,
        description = this.description,
        price = this.price,
        imageUrls = if (this.imageUrl.isNotEmpty()) listOf(this.imageUrl) else emptyList(),
        sellerId = this.ownerId.toLongOrNull() ?: 0L,
        sellerName = this.ownerName,
        category = try { com.example.ecosort.data.model.WasteCategory.valueOf(this.category) } catch (e: Exception) { com.example.ecosort.data.model.WasteCategory.OTHER },
        condition = try { com.example.ecosort.data.model.ItemCondition.valueOf(this.condition) } catch (e: Exception) { com.example.ecosort.data.model.ItemCondition.GOOD },
        postedAt = this.createdAt?.toDate()?.time ?: System.currentTimeMillis()
    )
}

/**
 * Extension function to convert local Room model to Firebase model
 */
fun com.example.ecosort.data.model.MarketplaceItem.toFirebaseModel(): FirebaseMarketplaceItem {
    return FirebaseMarketplaceItem(
        id = this.id.toString(), // Convert long ID to string for Firestore
        title = this.title,
        description = this.description,
        price = this.price,
        imageUrl = this.imageUrls.firstOrNull() ?: "",
        ownerId = this.sellerId.toString(),
        ownerName = this.sellerName,
        category = this.category.name,
        condition = this.condition.name,
        createdAt = Timestamp(java.util.Date(this.postedAt))
    )
}

// ==================== COMMUNITY FIREBASE MODELS ====================

/**
 * Firestore data model for community posts
 */
data class FirebaseCommunityPost(
    @DocumentId
    val id: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val title: String = "",
    val content: String = "",
    val postType: String = "TIP",
    val inputType: String = "TEXT",
    val imageUrls: List<String> = emptyList(),
    val videoUrl: String? = null,
    val location: String? = null,
    val tags: List<String> = emptyList(),
    @ServerTimestamp
    val postedAt: Timestamp? = null,
    val likesCount: Int = 0,
    val commentsCount: Int = 0,
    val sharesCount: Int = 0,
    val status: String = "PUBLISHED"
) {
    // No-argument constructor for Firestore
    constructor() : this(
        id = "",
        authorId = "",
        authorName = "",
        authorAvatar = null,
        title = "",
        content = "",
        postType = "TEXT",
        imageUrls = emptyList(),
        videoUrl = null,
        location = null,
        tags = emptyList(),
        postedAt = null,
        likesCount = 0,
        commentsCount = 0,
        sharesCount = 0,
        status = "PUBLISHED"
    )
}

/**
 * Firestore data model for community comments
 */
data class FirebaseCommunityComment(
    @DocumentId
    val id: String = "",
    val postId: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val authorAvatar: String? = null,
    val content: String = "",
    val parentCommentId: String? = null,
    @ServerTimestamp
    val postedAt: Timestamp? = null,
    val likesCount: Int = 0
) {
    constructor() : this(
        id = "",
        postId = "",
        authorId = "",
        authorName = "",
        authorAvatar = null,
        content = "",
        parentCommentId = null,
        postedAt = null,
        likesCount = 0
    )
}

/**
 * Firestore data model for community likes
 */
data class FirebaseCommunityLike(
    @DocumentId
    val id: String = "",
    val postId: String = "",
    val userId: String = "",
    @ServerTimestamp
    val likedAt: Timestamp? = null
) {
    constructor() : this(
        id = "",
        postId = "",
        userId = "",
        likedAt = null
    )
}

// ==================== CONVERSION EXTENSIONS ====================

/**
 * Extension function to convert Firebase community post to local Room model
 */
fun FirebaseCommunityPost.toLocalModel(): com.example.ecosort.data.model.CommunityPost {
    return com.example.ecosort.data.model.CommunityPost(
        id = this.id.hashCode().toLong(), // Convert string ID to long for Room
        authorId = this.authorId.toLongOrNull() ?: 0L,
        authorName = this.authorName,
        authorAvatar = this.authorAvatar,
        title = this.title,
        content = this.content,
        postType = try { com.example.ecosort.data.model.PostType.valueOf(this.postType) } catch (e: Exception) { com.example.ecosort.data.model.PostType.TIP },
        inputType = try { com.example.ecosort.data.model.InputType.valueOf(this.inputType) } catch (e: Exception) { com.example.ecosort.data.model.InputType.TEXT },
        imageUrls = this.imageUrls,
        videoUrl = this.videoUrl,
        location = this.location,
        tags = this.tags,
        postedAt = this.postedAt?.toDate()?.time ?: System.currentTimeMillis(),
        likesCount = this.likesCount,
        commentsCount = this.commentsCount,
        sharesCount = this.sharesCount,
        isLikedByUser = false, // Will be updated separately
        status = try { com.example.ecosort.data.model.PostStatus.valueOf(this.status) } catch (e: Exception) { com.example.ecosort.data.model.PostStatus.PUBLISHED }
    )
}

/**
 * Extension function to convert local Room model to Firebase community post
 */
fun com.example.ecosort.data.model.CommunityPost.toFirebaseModel(): FirebaseCommunityPost {
    return FirebaseCommunityPost(
        id = this.id.toString(),
        authorId = this.authorId.toString(),
        authorName = this.authorName,
        authorAvatar = this.authorAvatar,
        title = this.title,
        content = this.content,
        postType = this.postType.name,
        inputType = this.inputType.name,
        imageUrls = this.imageUrls,
        videoUrl = this.videoUrl,
        location = this.location,
        tags = this.tags,
        postedAt = Timestamp(java.util.Date(this.postedAt)),
        likesCount = this.likesCount,
        commentsCount = this.commentsCount,
        sharesCount = this.sharesCount,
        status = this.status.name
    )
}

/**
 * Extension function to convert local Room model to Firebase community comment
 */
fun com.example.ecosort.data.model.CommunityComment.toFirebaseModel(): FirebaseCommunityComment {
    return FirebaseCommunityComment(
        id = this.id.toString(),
        postId = this.postId.toString(),
        authorId = this.authorId.toString(),
        authorName = this.authorName,
        authorAvatar = this.authorAvatar,
        content = this.content,
        parentCommentId = this.parentCommentId?.toString(),
        postedAt = Timestamp(java.util.Date(this.postedAt)),
        likesCount = this.likesCount
    )
}