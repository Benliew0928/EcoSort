package com.example.ecosort.data.repository

import com.example.ecosort.data.local.*
import com.example.ecosort.data.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FriendRepository @Inject constructor(
    private val userDao: UserDao,
    private val friendRequestDao: FriendRequestDao,
    private val friendshipDao: FriendshipDao,
    private val blockedUserDao: BlockedUserDao
) {

    // ==================== FRIEND REQUESTS ====================

    suspend fun sendFriendRequest(senderId: Long, receiverId: Long, message: String? = null): Result<Long> {
        return try {
            // Check if users are already friends
            val existingFriendship = friendshipDao.getFriendshipBetweenUsers(senderId, receiverId)
            if (existingFriendship != null) {
                return Result.Error(Exception("Users are already friends"))
            }

            // Check if there's already a pending request
            val existingRequest = friendRequestDao.getPendingRequestBetweenUsers(senderId, receiverId)
            if (existingRequest != null) {
                return Result.Error(Exception("Friend request already sent"))
            }

            // Check if users are blocked
            val isBlocked = blockedUserDao.isBlocked(senderId, receiverId)
            if (isBlocked) {
                return Result.Error(Exception("Cannot send friend request to blocked user"))
            }

            val friendRequest = FriendRequest(
                senderId = senderId,
                receiverId = receiverId,
                message = message
            )

            val requestId = friendRequestDao.insertFriendRequest(friendRequest)
            Result.Success(requestId)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    fun getPendingFriendRequests(userId: Long): Flow<List<FriendRequest>> {
        return friendRequestDao.getPendingFriendRequests(userId)
    }

    fun getSentFriendRequests(userId: Long): Flow<List<FriendRequest>> {
        return friendRequestDao.getSentFriendRequests(userId)
    }

    suspend fun acceptFriendRequest(requestId: Long, userId: Long): Result<Unit> {
        return try {
            val request = friendRequestDao.getFriendRequestById(requestId)
                ?: return Result.Error(Exception("Friend request not found"))

            // Security check: Only the receiver can accept the request
            if (request.receiverId != userId) {
                return Result.Error(SecurityException("Unauthorized to accept this request"))
            }

            // Update request status
            friendRequestDao.updateFriendRequestStatus(
                requestId, 
                FriendRequestStatus.ACCEPTED, 
                System.currentTimeMillis()
            )

            // Create friendship
            val friendship = Friendship(
                userId1 = request.senderId,
                userId2 = request.receiverId,
                createdAt = System.currentTimeMillis(),
                lastInteraction = System.currentTimeMillis()
            )
            friendshipDao.insertFriendship(friendship)

            Result.Success(Unit)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun declineFriendRequest(requestId: Long, userId: Long): Result<Unit> {
        return try {
            val request = friendRequestDao.getFriendRequestById(requestId)
                ?: return Result.Error(Exception("Friend request not found"))

            // Security check: Only the receiver can decline the request
            if (request.receiverId != userId) {
                return Result.Error(SecurityException("Unauthorized to decline this request"))
            }

            friendRequestDao.updateFriendRequestStatus(
                requestId, 
                FriendRequestStatus.DECLINED, 
                System.currentTimeMillis()
            )
            Result.Success(Unit)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun cancelFriendRequest(requestId: Long): Result<Unit> {
        return try {
            friendRequestDao.updateFriendRequestStatus(
                requestId, 
                FriendRequestStatus.CANCELLED, 
                System.currentTimeMillis()
            )
            Result.Success(Unit)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== FRIEND MANAGEMENT ====================

    fun getFriends(userId: Long): Flow<List<User>> {
        return friendshipDao.getFriendships(userId).combine(
            userDao.getAllUsersFlow()
        ) { friendships, users ->
            friendships.mapNotNull { friendship ->
                val friendId = if (friendship.userId1 == userId) friendship.userId2 else friendship.userId1
                users.find { it.id == friendId }
            }
        }
    }

    suspend fun removeFriend(userId: Long, friendId: Long): Result<Unit> {
        return try {
            friendshipDao.removeFriendship(userId, friendId)
            Result.Success(Unit)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun blockUser(blockerId: Long, blockedId: Long, reason: String? = null): Result<Unit> {
        return try {
            // Remove friendship if exists
            friendshipDao.removeFriendship(blockerId, blockedId)

            // Cancel any pending friend requests
            val pendingRequest = friendRequestDao.getPendingRequestBetweenUsers(blockerId, blockedId)
            if (pendingRequest != null) {
                friendRequestDao.updateFriendRequestStatus(
                    pendingRequest.id,
                    FriendRequestStatus.CANCELLED,
                    System.currentTimeMillis()
                )
            }

            // Add to blocked users
            val blockedUser = BlockedUser(
                blockerId = blockerId,
                blockedId = blockedId,
                reason = reason
            )
            blockedUserDao.insertBlockedUser(blockedUser)

            Result.Success(Unit)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun unblockUser(blockerId: Long, blockedId: Long): Result<Unit> {
        return try {
            blockedUserDao.unblockUser(blockerId, blockedId)
            Result.Success(Unit)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    fun getBlockedUsers(userId: Long): Flow<List<User>> {
        return blockedUserDao.getBlockedUsers(userId).combine(
            userDao.getAllUsersFlow()
        ) { blockedUsers, users ->
            blockedUsers.mapNotNull { blockedUser ->
                users.find { it.id == blockedUser.blockedId }
            }
        }
    }

    suspend fun isBlocked(userId1: Long, userId2: Long): Boolean {
        return blockedUserDao.isBlocked(userId1, userId2)
    }

    suspend fun areFriends(userId1: Long, userId2: Long): Boolean {
        return friendshipDao.getFriendshipBetweenUsers(userId1, userId2) != null
    }

    suspend fun getFriendCount(userId: Long): Int {
        return friendshipDao.getFriendCount(userId)
    }

    // ==================== FRIEND SEARCH ====================

    suspend fun searchUsers(query: String, currentUserId: Long): Result<List<User>> {
        return try {
            // Use searchUsersByUsername for precise username matching (like chat search)
            val users = userDao.searchUsersByUsername(query, currentUserId)
            
            // Filter out blocked users
            val filteredUsers = users.filter { user ->
                !blockedUserDao.isBlocked(currentUserId, user.id)
            }

            Result.Success(filteredUsers)

        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUsersWithFriendStatus(query: String, currentUserId: Long): Result<List<UserWithFriendStatus>> {
        return try {
            android.util.Log.d("FriendRepository", "getUsersWithFriendStatus called with query: '$query', currentUserId: $currentUserId")
            
            // Get current user for logging
            val currentUser = userDao.getUserById(currentUserId)
            android.util.Log.d("FriendRepository", "Current user: ${currentUser?.username} (ID: ${currentUser?.id})")
            
            // Use searchUsersByUsername for precise username matching (like chat search)
            val users = userDao.searchUsersByUsername(query, currentUserId)
            android.util.Log.d("FriendRepository", "searchUsersByUsername returned ${users.size} users")
            users.forEach { user ->
                android.util.Log.d("FriendRepository", "Found user: ${user.username} (ID: ${user.id})")
            }
            
            val usersWithStatus = users.filter { user ->
                val isBlocked = blockedUserDao.isBlocked(currentUserId, user.id)
                android.util.Log.d("FriendRepository", "User ${user.username} blocked: $isBlocked")
                !isBlocked
            }.map { user ->
                val isFriend = areFriends(currentUserId, user.id)
                val hasPendingRequest = friendRequestDao.getPendingRequestBetweenUsers(currentUserId, user.id) != null
                val hasReceivedRequest = friendRequestDao.getPendingRequestBetweenUsers(user.id, currentUserId) != null
                
                android.util.Log.d("FriendRepository", "User ${user.username}: isFriend=$isFriend, hasPendingRequest=$hasPendingRequest, hasReceivedRequest=$hasReceivedRequest")
                
                UserWithFriendStatus(
                    user = user,
                    isFriend = isFriend,
                    hasPendingRequest = hasPendingRequest,
                    hasReceivedRequest = hasReceivedRequest
                )
            }

            android.util.Log.d("FriendRepository", "Returning ${usersWithStatus.size} users with status")
            Result.Success(usersWithStatus)

        } catch (e: Exception) {
            android.util.Log.e("FriendRepository", "Error in getUsersWithFriendStatus", e)
            Result.Error(e)
        }
    }

    // ==================== TEST HELPER METHODS ====================

    suspend fun getAllUsersForTesting(): Result<List<User>> {
        return try {
            val users = userDao.getAllUsers()
            android.util.Log.d("FriendRepository", "All users in database: ${users.size}")
            users.forEach { user ->
                android.util.Log.d("FriendRepository", "User: ${user.username} (ID: ${user.id})")
            }
            Result.Success(users)
        } catch (e: Exception) {
            android.util.Log.e("FriendRepository", "Error getting all users", e)
            Result.Error(e)
        }
    }

    // ==================== HELPER DATA CLASS ====================

    data class UserWithFriendStatus(
        val user: User,
        val isFriend: Boolean,
        val hasPendingRequest: Boolean,
        val hasReceivedRequest: Boolean
    )
}
