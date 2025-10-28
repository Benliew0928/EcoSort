package com.example.ecosort.data.local

import androidx.room.*
import com.example.ecosort.data.model.RecycledItem
import kotlinx.coroutines.flow.Flow

@Dao
interface RecycledItemDao {
    
    // ==================== BASIC CRUD OPERATIONS ====================
    
    @Query("SELECT * FROM recycled_items WHERE userId = :userId ORDER BY recycledDate DESC")
    suspend fun getUserRecycledItems(userId: Long): List<RecycledItem>
    
    @Query("SELECT * FROM recycled_items WHERE userId = :userId ORDER BY recycledDate DESC")
    fun getUserRecycledItemsFlow(userId: Long): Flow<List<RecycledItem>>
    
    @Query("SELECT * FROM recycled_items WHERE id = :itemId")
    suspend fun getRecycledItemById(itemId: Long): RecycledItem?
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecycledItem(item: RecycledItem): Long
    
    @Update
    suspend fun updateRecycledItem(item: RecycledItem)
    
    @Delete
    suspend fun deleteRecycledItem(item: RecycledItem)
    
    @Query("DELETE FROM recycled_items WHERE id = :itemId")
    suspend fun deleteRecycledItemById(itemId: Long)
    
    // ==================== STATISTICS QUERIES ====================
    
    @Query("SELECT COUNT(*) FROM recycled_items WHERE userId = :userId")
    suspend fun getTotalItemsCount(userId: Long): Int
    
    @Query("SELECT COALESCE(SUM(pointsEarned), 0) FROM recycled_items WHERE userId = :userId")
    suspend fun getTotalPoints(userId: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM recycled_items 
        WHERE userId = :userId 
        AND recycledDate >= :startOfMonth
    """)
    suspend fun getItemsThisMonthCount(userId: Long, startOfMonth: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM recycled_items 
        WHERE userId = :userId 
        AND recycledDate >= :startOfWeek
    """)
    suspend fun getItemsThisWeekCount(userId: Long, startOfWeek: Long): Int
    
    @Query("""
        SELECT COUNT(*) FROM recycled_items 
        WHERE userId = :userId 
        AND recycledDate >= :startOfDay
    """)
    suspend fun getItemsTodayCount(userId: Long, startOfDay: Long): Int
    
    @Query("""
        SELECT itemType 
        FROM recycled_items 
        WHERE userId = :userId 
        GROUP BY itemType 
        ORDER BY COUNT(*) DESC 
        LIMIT 1
    """)
    suspend fun getMostRecycledType(userId: Long): String?
    
    @Query("""
        SELECT MAX(recycledDate) 
        FROM recycled_items 
        WHERE userId = :userId
    """)
    suspend fun getLastRecycledDate(userId: Long): Long?
    
    // ==================== FILTERING AND SEARCH ====================
    
    @Query("""
        SELECT * FROM recycled_items 
        WHERE userId = :userId 
        AND itemType = :itemType 
        ORDER BY recycledDate DESC
    """)
    suspend fun getItemsByType(userId: Long, itemType: String): List<RecycledItem>
    
    @Query("""
        SELECT * FROM recycled_items 
        WHERE userId = :userId 
        AND itemName LIKE '%' || :searchQuery || '%' 
        ORDER BY recycledDate DESC
    """)
    suspend fun searchItems(userId: Long, searchQuery: String): List<RecycledItem>
    
    @Query("""
        SELECT * FROM recycled_items 
        WHERE userId = :userId 
        AND recycledDate BETWEEN :startDate AND :endDate 
        ORDER BY recycledDate DESC
    """)
    suspend fun getItemsByDateRange(userId: Long, startDate: Long, endDate: Long): List<RecycledItem>
    
    // ==================== BULK OPERATIONS ====================
    
    @Query("DELETE FROM recycled_items WHERE userId = :userId")
    suspend fun deleteAllUserItems(userId: Long)
    
    @Query("SELECT * FROM recycled_items WHERE userId = :userId AND id = :itemId")
    suspend fun getUserItemById(userId: Long, itemId: Long): RecycledItem?
    
    // ==================== SYNC OPERATIONS ====================
    
    @Query("SELECT * FROM recycled_items WHERE userId = :userId ORDER BY updatedAt DESC")
    suspend fun getItemsForSync(userId: Long): List<RecycledItem>
    
    @Query("UPDATE recycled_items SET updatedAt = :timestamp WHERE id = :itemId")
    suspend fun updateItemTimestamp(itemId: Long, timestamp: Long)
}
