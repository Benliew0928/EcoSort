package com.example.ecosort.data.local

import androidx.room.*
import com.example.ecosort.data.model.PointsTransaction
import com.example.ecosort.data.model.UserPoints
import kotlinx.coroutines.flow.Flow

@Dao
interface PointsDao {
    
    // ==================== USER POINTS OPERATIONS ====================
    
    @Query("SELECT * FROM user_points WHERE userId = :userId LIMIT 1")
    suspend fun getUserPoints(userId: Long): UserPoints?
    
    @Query("SELECT * FROM user_points WHERE userId = :userId LIMIT 1")
    fun getUserPointsFlow(userId: Long): Flow<UserPoints?>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUserPoints(userPoints: UserPoints): Long
    
    @Update
    suspend fun updateUserPoints(userPoints: UserPoints)
    
    @Query("UPDATE user_points SET totalPoints = :totalPoints, lastUpdated = :timestamp WHERE userId = :userId")
    suspend fun updateTotalPoints(userId: Long, totalPoints: Int, timestamp: Long)
    
    @Query("UPDATE user_points SET pointsEarned = :pointsEarned, lastUpdated = :timestamp WHERE userId = :userId")
    suspend fun updatePointsEarned(userId: Long, pointsEarned: Int, timestamp: Long)
    
    @Query("UPDATE user_points SET pointsSpent = :pointsSpent, lastUpdated = :timestamp WHERE userId = :userId")
    suspend fun updatePointsSpent(userId: Long, pointsSpent: Int, timestamp: Long)
    
    // ==================== POINTS TRANSACTIONS ====================
    
    @Query("SELECT * FROM points_transactions WHERE userId = :userId ORDER BY timestamp DESC")
    suspend fun getUserTransactions(userId: Long): List<PointsTransaction>
    
    @Query("SELECT * FROM points_transactions WHERE userId = :userId ORDER BY timestamp DESC")
    fun getUserTransactionsFlow(userId: Long): Flow<List<PointsTransaction>>
    
    @Query("SELECT * FROM points_transactions WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentTransactions(userId: Long, limit: Int): List<PointsTransaction>
    
    @Insert
    suspend fun insertTransaction(transaction: PointsTransaction): Long
    
    @Query("SELECT * FROM points_transactions WHERE userId = :userId AND type = :type ORDER BY timestamp DESC")
    suspend fun getTransactionsByType(userId: Long, type: com.example.ecosort.data.model.PointsTransactionType): List<PointsTransaction>
    
    @Query("SELECT * FROM points_transactions WHERE userId = :userId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getTransactionsByDateRange(userId: Long, startTime: Long, endTime: Long): List<PointsTransaction>
    
    // ==================== STATISTICS ====================
    
    @Query("SELECT COALESCE(SUM(amount), 0) FROM points_transactions WHERE userId = :userId AND type = 'EARNED'")
    suspend fun getTotalEarned(userId: Long): Int
    
    @Query("SELECT COALESCE(SUM(amount), 0) FROM points_transactions WHERE userId = :userId AND type = 'SPENT'")
    suspend fun getTotalSpent(userId: Long): Int
    
    @Query("SELECT COUNT(*) FROM points_transactions WHERE userId = :userId AND type = 'EARNED'")
    suspend fun getEarnedTransactionsCount(userId: Long): Int
    
    @Query("SELECT COUNT(*) FROM points_transactions WHERE userId = :userId AND type = 'SPENT'")
    suspend fun getSpentTransactionsCount(userId: Long): Int
    
    // ==================== CLEANUP ====================
    
    @Query("DELETE FROM user_points WHERE userId = :userId")
    suspend fun deleteUserPoints(userId: Long)
    
    @Query("DELETE FROM points_transactions WHERE userId = :userId")
    suspend fun deleteUserTransactions(userId: Long)
}
