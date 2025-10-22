package com.example.ecosort.data.repository

import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserFollow
import com.example.ecosort.data.model.UserFriend
import com.example.ecosort.data.model.FriendStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val database: EcoSortDatabase
) {
    private val userFollowDao = database.userFollowDao()
    private val userFriendDao = database.userFriendDao()
    private val userDao = database.userDao()

    // ==================== FOLLOW OPERATIONS ====================

    suspend fun followUser(followerId: Long, followingId: Long): Result<Unit> {
        return try {
            // Check if already following
            val existingFollow = userFollowDao.getFollow(followerId, followingId)
            if (existingFollow != null) {
                return Result.Error(Exception("Already following this user"))
            }

            // Check if trying to follow self
            if (followerId == followingId) {
                return Result.Error(Exception("Cannot follow yourself"))
            }

            // Create follow relationship
            val follow = UserFollow(
                followerId = followerId,
                followingId = followingId,
                followedAt = System.currentTimeMillis()
            )
            
            userFollowDao.insertFollow(follow)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun unfollowUser(followerId: Long, followingId: Long): Result<Unit> {
        return try {
            userFollowDao.removeFollow(followerId, followingId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun isFollowing(followerId: Long, followingId: Long): Boolean {
        return try {
            userFollowDao.isFollowing(followerId, followingId)
        } catch (e: Exception) {
            false
        }
    }

    fun getUserFollowing(userId: Long): Flow<List<UserFollow>> {
        return userFollowDao.getUserFollowing(userId)
    }

    fun getUserFollowers(userId: Long): Flow<List<UserFollow>> {
        return userFollowDao.getUserFollowers(userId)
    }

    suspend fun getFollowingCount(userId: Long): Int {
        return try {
            userFollowDao.getFollowingCount(userId)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getFollowersCount(userId: Long): Int {
        return try {
            userFollowDao.getFollowersCount(userId)
        } catch (e: Exception) {
            0
        }
    }

    // ==================== FRIEND OPERATIONS ====================

    suspend fun sendFriendRequest(userId: Long, friendId: Long): Result<Unit> {
        return try {
            // Check if already friends or request exists
            val existingFriendship = userFriendDao.getFriendship(userId, friendId)
            if (existingFriendship != null) {
                return when (existingFriendship.status) {
                    FriendStatus.ACCEPTED -> Result.Error(Exception("Already friends"))
                    FriendStatus.PENDING -> Result.Error(Exception("Friend request already sent"))
                    FriendStatus.BLOCKED -> Result.Error(Exception("Cannot send friend request"))
                }
            }

            // Check if trying to friend self
            if (userId == friendId) {
                return Result.Error(Exception("Cannot send friend request to yourself"))
            }

            // Create friend request
            val friendRequest = UserFriend(
                userId = userId,
                friendId = friendId,
                status = FriendStatus.PENDING,
                createdAt = System.currentTimeMillis()
            )
            
            userFriendDao.insertFriend(friendRequest)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun acceptFriendRequest(friendshipId: Long): Result<Unit> {
        return try {
            userFriendDao.acceptFriendRequest(friendshipId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun removeFriend(userId: Long, friendId: Long): Result<Unit> {
        return try {
            userFriendDao.removeFriendship(userId, friendId)
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun areFriends(userId: Long, friendId: Long): Boolean {
        return try {
            userFriendDao.areFriends(userId, friendId)
        } catch (e: Exception) {
            false
        }
    }

    fun getUserFriends(userId: Long): Flow<List<UserFriend>> {
        return userFriendDao.getUserFriends(userId)
    }

    fun getPendingFriendRequests(userId: Long): Flow<List<UserFriend>> {
        return userFriendDao.getPendingFriendRequests(userId)
    }

    fun getSentFriendRequests(userId: Long): Flow<List<UserFriend>> {
        return userFriendDao.getSentFriendRequests(userId)
    }

    suspend fun getFriendsCount(userId: Long): Int {
        return try {
            userFriendDao.getFriendsCount(userId)
        } catch (e: Exception) {
            0
        }
    }

    suspend fun getPendingRequestsCount(userId: Long): Int {
        return try {
            userFriendDao.getPendingRequestsCount(userId)
        } catch (e: Exception) {
            0
        }
    }

    // ==================== USER SEARCH ====================

    suspend fun searchUsers(query: String): Result<List<User>> {
        return try {
            val users = userDao.searchUsers("%$query%")
            Result.Success(users)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUsersByIds(userIds: List<Long>): Result<List<User>> {
        return try {
            val users = mutableListOf<User>()
            for (userId in userIds) {
                val user = userDao.getUserById(userId)
                if (user != null) {
                    users.add(user)
                }
            }
            Result.Success(users)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }
}
