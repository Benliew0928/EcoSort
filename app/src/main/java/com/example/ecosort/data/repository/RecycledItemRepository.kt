package com.example.ecosort.data.repository

import android.content.Context
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.local.RecycledItemDao
import com.example.ecosort.data.model.RecycledItem
import com.example.ecosort.data.model.RecycledItemStats
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.firebase.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RecycledItemRepository @Inject constructor(
    private val database: EcoSortDatabase,
    private val firestoreService: FirestoreService
) {
    private val recycledItemDao: RecycledItemDao = database.recycledItemDao()

    // ==================== BASIC CRUD OPERATIONS ====================
    
    suspend fun getUserRecycledItems(userId: Long): Result<List<RecycledItem>> {
        return try {
            withContext(Dispatchers.IO) {
                val items = recycledItemDao.getUserRecycledItems(userId)
                Result.Success(items)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error getting user recycled items", e)
            Result.Error(e)
        }
    }
    
    fun getUserRecycledItemsFlow(userId: Long): Flow<List<RecycledItem>> {
        return recycledItemDao.getUserRecycledItemsFlow(userId)
    }
    
    suspend fun getRecycledItemById(itemId: Long): Result<RecycledItem?> {
        return try {
            withContext(Dispatchers.IO) {
                val item = recycledItemDao.getRecycledItemById(itemId)
                Result.Success(item)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error getting recycled item by ID", e)
            Result.Error(e)
        }
    }
    
    suspend fun addRecycledItem(item: RecycledItem): Result<Long> {
        return try {
            withContext(Dispatchers.IO) {
                val itemId = recycledItemDao.insertRecycledItem(item)
                
                // Sync to Firebase
                try {
                    syncRecycledItemToFirebase(item.copy(id = itemId))
                } catch (e: Exception) {
                    android.util.Log.w("RecycledItemRepository", "Failed to sync recycled item to Firebase: ${e.message}")
                    // Don't fail the operation if Firebase sync fails
                }
                
                Result.Success(itemId)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error adding recycled item", e)
            Result.Error(e)
        }
    }
    
    suspend fun updateRecycledItem(item: RecycledItem): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                recycledItemDao.updateRecycledItem(item)
                
                // Sync to Firebase
                try {
                    syncRecycledItemToFirebase(item)
                } catch (e: Exception) {
                    android.util.Log.w("RecycledItemRepository", "Failed to sync updated recycled item to Firebase: ${e.message}")
                    // Don't fail the operation if Firebase sync fails
                }
                
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error updating recycled item", e)
            Result.Error(e)
        }
    }
    
    suspend fun deleteRecycledItem(itemId: Long): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val item = recycledItemDao.getRecycledItemById(itemId)
                recycledItemDao.deleteRecycledItemById(itemId)
                
                // Sync deletion to Firebase
                if (item != null) {
                    try {
                        deleteRecycledItemFromFirebase(item)
                    } catch (e: Exception) {
                        android.util.Log.w("RecycledItemRepository", "Failed to sync recycled item deletion to Firebase: ${e.message}")
                        // Don't fail the operation if Firebase sync fails
                    }
                }
                
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error deleting recycled item", e)
            Result.Error(e)
        }
    }

    // ==================== STATISTICS ====================
    
    suspend fun getRecycledItemStats(userId: Long): Result<RecycledItemStats> {
        return try {
            withContext(Dispatchers.IO) {
                val calendar = Calendar.getInstance()
                
                // Get current time boundaries
                val now = System.currentTimeMillis()
                val startOfDay = calendar.apply { 
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val startOfWeek = calendar.apply {
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                val startOfMonth = calendar.apply {
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                
                // Get statistics
                val totalItems = recycledItemDao.getTotalItemsCount(userId)
                val totalPoints = recycledItemDao.getTotalPoints(userId)
                val itemsThisMonth = recycledItemDao.getItemsThisMonthCount(userId, startOfMonth)
                val itemsThisWeek = recycledItemDao.getItemsThisWeekCount(userId, startOfWeek)
                val itemsToday = recycledItemDao.getItemsTodayCount(userId, startOfDay)
                val mostRecycledType = recycledItemDao.getMostRecycledType(userId)
                val lastRecycledDate = recycledItemDao.getLastRecycledDate(userId)
                
                val stats = RecycledItemStats(
                    totalItems = totalItems,
                    totalPoints = totalPoints,
                    itemsThisMonth = itemsThisMonth,
                    itemsThisWeek = itemsThisWeek,
                    itemsToday = itemsToday,
                    mostRecycledType = mostRecycledType,
                    lastRecycledDate = lastRecycledDate
                )
                
                Result.Success(stats)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error getting recycled item stats", e)
            Result.Error(e)
        }
    }
    
    fun getRecycledItemStatsFlow(userId: Long): Flow<RecycledItemStats> {
        return getUserRecycledItemsFlow(userId).map { items ->
            val calendar = Calendar.getInstance()
            val now = System.currentTimeMillis()
            val startOfDay = calendar.apply { 
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val startOfWeek = calendar.apply {
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val startOfMonth = calendar.apply {
                set(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            
            val totalItems = items.size
            val totalPoints = items.sumOf { it.pointsEarned }
            val itemsThisMonth = items.count { it.recycledDate >= startOfMonth }
            val itemsThisWeek = items.count { it.recycledDate >= startOfWeek }
            val itemsToday = items.count { it.recycledDate >= startOfDay }
            val mostRecycledType = items.groupBy { it.itemType }
                .maxByOrNull { it.value.size }?.key
            val lastRecycledDate = items.maxOfOrNull { it.recycledDate }
            
            RecycledItemStats(
                totalItems = totalItems,
                totalPoints = totalPoints,
                itemsThisMonth = itemsThisMonth,
                itemsThisWeek = itemsThisWeek,
                itemsToday = itemsToday,
                mostRecycledType = mostRecycledType,
                lastRecycledDate = lastRecycledDate
            )
        }
    }

    // ==================== FILTERING AND SEARCH ====================
    
    suspend fun getItemsByType(userId: Long, itemType: String): Result<List<RecycledItem>> {
        return try {
            withContext(Dispatchers.IO) {
                val items = recycledItemDao.getItemsByType(userId, itemType)
                Result.Success(items)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error getting items by type", e)
            Result.Error(e)
        }
    }
    
    suspend fun searchItems(userId: Long, searchQuery: String): Result<List<RecycledItem>> {
        return try {
            withContext(Dispatchers.IO) {
                val items = recycledItemDao.searchItems(userId, searchQuery)
                Result.Success(items)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error searching items", e)
            Result.Error(e)
        }
    }
    
    suspend fun getItemsByDateRange(userId: Long, startDate: Long, endDate: Long): Result<List<RecycledItem>> {
        return try {
            withContext(Dispatchers.IO) {
                val items = recycledItemDao.getItemsByDateRange(userId, startDate, endDate)
                Result.Success(items)
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error getting items by date range", e)
            Result.Error(e)
        }
    }

    // ==================== FIREBASE SYNC ====================
    
    private suspend fun syncRecycledItemToFirebase(item: RecycledItem) {
        val itemData = hashMapOf<String, Any>(
            "id" to item.id,
            "userId" to item.userId,
            "itemName" to item.itemName,
            "itemType" to item.itemType,
            "pointsEarned" to item.pointsEarned,
            "recycledDate" to item.recycledDate,
            "imageUrl" to (item.imageUrl ?: ""),
            "notes" to (item.notes ?: ""),
            "weight" to (item.weight ?: 0.0),
            "location" to (item.location ?: ""),
            "createdAt" to item.createdAt,
            "updatedAt" to item.updatedAt
        )
        
        val result = firestoreService.saveRecycledItem(itemData)
        if (result is com.example.ecosort.data.model.Result.Error) {
            throw result.exception
        }
    }
    
    private suspend fun deleteRecycledItemFromFirebase(item: RecycledItem) {
        val result = firestoreService.deleteRecycledItem(item.id)
        if (result is com.example.ecosort.data.model.Result.Error) {
            throw result.exception
        }
    }
    
    suspend fun syncRecycledItemsFromFirebase(userId: Long): Result<Int> {
        return try {
            withContext(Dispatchers.IO) {
                val result = firestoreService.getUserRecycledItems(userId)
                if (result is com.example.ecosort.data.model.Result.Success) {
                    val firebaseItems = result.data
                    var syncedCount = 0
                    
                    for (itemData in firebaseItems) {
                        try {
                            val item = RecycledItem(
                                id = (itemData["id"] as? Number)?.toLong() ?: 0L,
                                userId = (itemData["userId"] as? Number)?.toLong() ?: userId,
                                itemName = itemData["itemName"] as? String ?: "",
                                itemType = itemData["itemType"] as? String ?: "",
                                pointsEarned = (itemData["pointsEarned"] as? Number)?.toInt() ?: 0,
                                recycledDate = (itemData["recycledDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                imageUrl = itemData["imageUrl"] as? String,
                                notes = itemData["notes"] as? String,
                                weight = (itemData["weight"] as? Number)?.toDouble(),
                                location = itemData["location"] as? String,
                                createdAt = (itemData["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                updatedAt = (itemData["updatedAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                            )
                            
                            // Check if item already exists locally
                            val existingItem = recycledItemDao.getRecycledItemById(item.id)
                            if (existingItem == null) {
                                recycledItemDao.insertRecycledItem(item)
                                syncedCount++
                            } else if (existingItem.updatedAt < item.updatedAt) {
                                recycledItemDao.updateRecycledItem(item)
                                syncedCount++
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("RecycledItemRepository", "Failed to sync item: ${e.message}")
                        }
                    }
                    
                    android.util.Log.d("RecycledItemRepository", "Synced $syncedCount recycled items from Firebase")
                    Result.Success(syncedCount)
                } else {
                    Result.Error((result as com.example.ecosort.data.model.Result.Error).exception)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemRepository", "Error syncing recycled items from Firebase", e)
            Result.Error(e)
        }
    }
}
