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
}
