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
