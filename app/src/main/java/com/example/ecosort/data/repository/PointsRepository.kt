package com.example.ecosort.data.repository

import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.local.PointsDao
import com.example.ecosort.data.model.PointsTransaction
import com.example.ecosort.data.model.PointsTransactionType
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.UserPoints
import com.example.ecosort.data.firebase.FirestoreService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PointsRepository @Inject constructor(
    private val database: EcoSortDatabase,
    private val firestoreService: FirestoreService
) {
    private val pointsDao: PointsDao = database.pointsDao()

    // ==================== USER POINTS OPERATIONS ====================
    
    suspend fun getUserPoints(userId: Long): Result<UserPoints> {
        return try {
            withContext(Dispatchers.IO) {
                val userPoints = pointsDao.getUserPoints(userId)
                if (userPoints != null) {
                    Result.Success(userPoints)
                } else {
                    // Create new user points record if it doesn't exist
                    val newUserPoints = UserPoints(
                        userId = userId,
                        totalPoints = 0,
                        pointsEarned = 0,
                        pointsSpent = 0
                    )
                    val id = pointsDao.insertUserPoints(newUserPoints)
                    val createdPoints = newUserPoints.copy(id = id)
                    
                    // Sync to Firebase
                    try {
                        syncUserPointsToFirebase(createdPoints)
                    } catch (e: Exception) {
                        android.util.Log.w("PointsRepository", "Failed to sync user points to Firebase: ${e.message}")
                    }
                    
                    Result.Success(createdPoints)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PointsRepository", "Error getting user points", e)
            Result.Error(e)
        }
    }
    
    fun getUserPointsFlow(userId: Long): Flow<UserPoints?> {
        return pointsDao.getUserPointsFlow(userId)
    }
    
    suspend fun addPoints(userId: Long, points: Int, source: String, description: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val currentPoints = pointsDao.getUserPoints(userId)
                val newTotalPoints = (currentPoints?.totalPoints ?: 0) + points
                val newPointsEarned = (currentPoints?.pointsEarned ?: 0) + points
                
                // Update or create user points
                if (currentPoints != null) {
                    val updatedPoints = currentPoints.copy(
                        totalPoints = newTotalPoints,
                        pointsEarned = newPointsEarned,
                        lastUpdated = System.currentTimeMillis()
                    )
                    pointsDao.updateUserPoints(updatedPoints)
                    
                    // Sync to Firebase
                    try {
                        syncUserPointsToFirebase(updatedPoints)
                    } catch (e: Exception) {
                        android.util.Log.w("PointsRepository", "Failed to sync updated user points to Firebase: ${e.message}")
                    }
                } else {
                    val newUserPoints = UserPoints(
                        userId = userId,
                        totalPoints = newTotalPoints,
                        pointsEarned = newPointsEarned,
                        pointsSpent = 0
                    )
                    val id = pointsDao.insertUserPoints(newUserPoints)
                    val createdPoints = newUserPoints.copy(id = id)
                    
                    // Sync to Firebase
                    try {
                        syncUserPointsToFirebase(createdPoints)
                    } catch (e: Exception) {
                        android.util.Log.w("PointsRepository", "Failed to sync new user points to Firebase: ${e.message}")
                    }
                }
                
                // Create transaction record
                val transaction = PointsTransaction(
                    userId = userId,
                    amount = points,
                    type = PointsTransactionType.EARNED,
                    description = description,
                    source = source
                )
                pointsDao.insertTransaction(transaction)
                
                // Sync transaction to Firebase
                try {
                    syncTransactionToFirebase(transaction)
                } catch (e: Exception) {
                    android.util.Log.w("PointsRepository", "Failed to sync transaction to Firebase: ${e.message}")
                }
                
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("PointsRepository", "Error adding points", e)
            Result.Error(e)
        }
    }
    
    suspend fun spendPoints(userId: Long, points: Int, source: String, description: String): Result<Unit> {
        return try {
            withContext(Dispatchers.IO) {
                val currentPoints = pointsDao.getUserPoints(userId)
                if (currentPoints == null || currentPoints.totalPoints < points) {
                    return@withContext Result.Error(Exception("Insufficient points"))
                }
                
                val newTotalPoints = currentPoints.totalPoints - points
                val newPointsSpent = currentPoints.pointsSpent + points
                
                val updatedPoints = currentPoints.copy(
                    totalPoints = newTotalPoints,
                    pointsSpent = newPointsSpent,
                    lastUpdated = System.currentTimeMillis()
                )
                pointsDao.updateUserPoints(updatedPoints)
                
                // Sync to Firebase
                try {
                    syncUserPointsToFirebase(updatedPoints)
                } catch (e: Exception) {
                    android.util.Log.w("PointsRepository", "Failed to sync updated user points to Firebase: ${e.message}")
                }
                
                // Create transaction record
                val transaction = PointsTransaction(
                    userId = userId,
                    amount = -points, // Negative for spent
                    type = PointsTransactionType.SPENT,
                    description = description,
                    source = source
                )
                pointsDao.insertTransaction(transaction)
                
                // Sync transaction to Firebase
                try {
                    syncTransactionToFirebase(transaction)
                } catch (e: Exception) {
                    android.util.Log.w("PointsRepository", "Failed to sync transaction to Firebase: ${e.message}")
                }
                
                Result.Success(Unit)
            }
        } catch (e: Exception) {
            android.util.Log.e("PointsRepository", "Error spending points", e)
            Result.Error(e)
        }
    }

    // ==================== TRANSACTIONS ====================
    
    suspend fun getUserTransactions(userId: Long): Result<List<PointsTransaction>> {
        return try {
            withContext(Dispatchers.IO) {
                val transactions = pointsDao.getUserTransactions(userId)
                Result.Success(transactions)
            }
        } catch (e: Exception) {
            android.util.Log.e("PointsRepository", "Error getting user transactions", e)
            Result.Error(e)
        }
    }
    
    fun getUserTransactionsFlow(userId: Long): Flow<List<PointsTransaction>> {
        return pointsDao.getUserTransactionsFlow(userId)
    }
    
    suspend fun getRecentTransactions(userId: Long, limit: Int = 10): Result<List<PointsTransaction>> {
        return try {
            withContext(Dispatchers.IO) {
                val transactions = pointsDao.getRecentTransactions(userId, limit)
                Result.Success(transactions)
            }
        } catch (e: Exception) {
            android.util.Log.e("PointsRepository", "Error getting recent transactions", e)
            Result.Error(e)
        }
    }

    // ==================== FIREBASE SYNC ====================
    
    private suspend fun syncUserPointsToFirebase(userPoints: UserPoints) {
        val pointsData = hashMapOf<String, Any>(
            "id" to userPoints.id,
            "userId" to userPoints.userId,
            "totalPoints" to userPoints.totalPoints,
            "pointsEarned" to userPoints.pointsEarned,
            "pointsSpent" to userPoints.pointsSpent,
            "lastUpdated" to userPoints.lastUpdated,
            "createdAt" to userPoints.createdAt
        )
        
        val result = firestoreService.saveUserPoints(pointsData)
        if (result is com.example.ecosort.data.model.Result.Error) {
            throw result.exception
        }
    }
    
    private suspend fun syncTransactionToFirebase(transaction: PointsTransaction) {
        val transactionData = hashMapOf<String, Any>(
            "id" to transaction.id,
            "userId" to transaction.userId,
            "amount" to transaction.amount,
            "type" to transaction.type.name,
            "description" to transaction.description,
            "source" to transaction.source,
            "timestamp" to transaction.timestamp
        )
        
        val result = firestoreService.savePointsTransaction(transactionData)
        if (result is com.example.ecosort.data.model.Result.Error) {
            throw result.exception
        }
    }
    
    suspend fun syncPointsFromFirebase(userId: Long): Result<Int> {
        return try {
            withContext(Dispatchers.IO) {
                val pointsResult = firestoreService.getUserPoints(userId.toString())
                val transactionsResult = firestoreService.getUserPointsTransactions(userId.toString())
                
                var syncedCount = 0
                
                // Sync user points
                if (pointsResult is com.example.ecosort.data.model.Result.Success) {
                    val firebasePoints = pointsResult.data
                    if (firebasePoints != null) {
                        val userPoints = UserPoints(
                            id = firebasePoints["id"] as? Long ?: 0L,
                            userId = firebasePoints["userId"] as? Long ?: userId,
                            totalPoints = firebasePoints["totalPoints"] as? Int ?: 0,
                            pointsEarned = firebasePoints["pointsEarned"] as? Int ?: 0,
                            pointsSpent = firebasePoints["pointsSpent"] as? Int ?: 0,
                            lastUpdated = firebasePoints["lastUpdated"] as? Long ?: System.currentTimeMillis(),
                            createdAt = firebasePoints["createdAt"] as? Long ?: System.currentTimeMillis()
                        )
                        
                        val existingPoints = pointsDao.getUserPoints(userId)
                        if (existingPoints == null || existingPoints.lastUpdated < userPoints.lastUpdated) {
                            if (existingPoints == null) {
                                pointsDao.insertUserPoints(userPoints)
                            } else {
                                pointsDao.updateUserPoints(userPoints)
                            }
                            syncedCount++
                        }
                    }
                }
                
                // Sync transactions
                if (transactionsResult is com.example.ecosort.data.model.Result.Success) {
                    val firebaseTransactions = transactionsResult.data
                    for (transactionData in firebaseTransactions) {
                        try {
                            val transaction = PointsTransaction(
                                id = transactionData["id"] as? Long ?: 0L,
                                userId = transactionData["userId"] as? Long ?: userId,
                                amount = transactionData["amount"] as? Int ?: 0,
                                type = PointsTransactionType.valueOf(transactionData["type"] as? String ?: "EARNED"),
                                description = transactionData["description"] as? String ?: "",
                                source = transactionData["source"] as? String ?: "",
                                timestamp = transactionData["timestamp"] as? Long ?: System.currentTimeMillis()
                            )
                            
                            val existingTransactions = pointsDao.getUserTransactions(userId)
                            val existingTransaction = existingTransactions.firstOrNull { it.id == transaction.id }
                            
                            if (existingTransaction == null) {
                                pointsDao.insertTransaction(transaction)
                                syncedCount++
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("PointsRepository", "Failed to sync transaction: ${e.message}")
                        }
                    }
                }
                
                android.util.Log.d("PointsRepository", "Synced $syncedCount points data from Firebase")
                Result.Success(syncedCount)
            }
        } catch (e: Exception) {
            android.util.Log.e("PointsRepository", "Error syncing points from Firebase", e)
            Result.Error(e)
        }
    }
}
