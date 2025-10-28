package com.example.ecosort.data.repository

import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserFollow
import com.example.ecosort.data.model.UserFriend
import com.example.ecosort.data.model.FriendStatus
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.FirebaseUserFollow
import com.google.firebase.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SocialRepository @Inject constructor(
    private val database: EcoSortDatabase,
    private val userRepository: UserRepository,
    private val firestoreService: FirestoreService
) {
    private val userFollowDao = database.userFollowDao()
    private val userFriendDao = database.userFriendDao()
    private val userDao = database.userDao()

    // ==================== FOLLOW OPERATIONS ====================

    suspend fun followUser(followerId: Long, followingId: Long): Result<Unit> {
        return try {
            android.util.Log.d("SocialRepository", "followUser: followerId=$followerId, followingId=$followingId")
            
            // Check if already following
            val existingFollow = userFollowDao.getFollow(followerId, followingId)
            if (existingFollow != null) {
                return Result.Error(Exception("Already following this user"))
            }

            // Check if trying to follow self
            if (followerId == followingId) {
                return Result.Error(Exception("Cannot follow yourself"))
            }

            // Get follower and following users (handles admin negative IDs)
            val follower = userRepository.getUserOrAdmin(followerId)
            val following = userRepository.getUserOrAdmin(followingId)
            
            if (follower == null || following == null) {
                return Result.Error(Exception("User not found"))
            }

            // Create follow relationship locally
            val follow = UserFollow(
                followerId = followerId,
                followingId = followingId,
                followedAt = System.currentTimeMillis()
            )
            
            userFollowDao.insertFollow(follow)
            android.util.Log.d("SocialRepository", "Follow relationship created locally: ${follower.username} → ${following.username}")

            // Sync to Firebase
            try {
                val followerFirebaseUid = follower.firebaseUid
                val followingFirebaseUid = following.firebaseUid
                
                if (!followerFirebaseUid.isNullOrEmpty() && !followingFirebaseUid.isNullOrEmpty()) {
                    val firebaseFollow = FirebaseUserFollow(
                        id = "", // Auto-generated
                        followerId = followerFirebaseUid,
                        followingId = followingFirebaseUid,
                        followedAt = Timestamp.now()
                    )
                    
                    val firebaseResult = firestoreService.followUser(firebaseFollow)
                    when (firebaseResult) {
                        is Result.Success -> {
                            android.util.Log.d("SocialRepository", "Follow synced to Firebase: ${firebaseResult.data}")
                        }
                        is Result.Error -> {
                            android.util.Log.e("SocialRepository", "Failed to sync follow to Firebase", firebaseResult.exception)
                        }
                        else -> {}
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialRepository", "Error syncing follow to Firebase", e)
                // Continue - local follow was successful
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Error following user", e)
            Result.Error(e)
        }
    }

    suspend fun unfollowUser(followerId: Long, followingId: Long): Result<Unit> {
        return try {
            android.util.Log.d("SocialRepository", "unfollowUser: followerId=$followerId, followingId=$followingId")
            
            // Get follower and following users (handles admin negative IDs)
            val follower = userRepository.getUserOrAdmin(followerId)
            val following = userRepository.getUserOrAdmin(followingId)
            
            // Remove follow relationship locally
            userFollowDao.removeFollow(followerId, followingId)
            android.util.Log.d("SocialRepository", "Follow relationship removed locally")

            // Sync to Firebase
            try {
                if (follower != null && following != null) {
                    val followerFirebaseUid = follower.firebaseUid
                    val followingFirebaseUid = following.firebaseUid
                    
                    if (!followerFirebaseUid.isNullOrEmpty() && !followingFirebaseUid.isNullOrEmpty()) {
                        firestoreService.unfollowUser(followerFirebaseUid, followingFirebaseUid)
                        android.util.Log.d("SocialRepository", "Unfollow synced to Firebase")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SocialRepository", "Error syncing unfollow to Firebase", e)
                // Continue - local unfollow was successful
            }

            Result.Success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Error unfollowing user", e)
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

    suspend fun searchUsers(query: String, currentUserId: Long): Result<List<User>> {
        return try {
            val users = userDao.searchUsers(query, currentUserId)
            Result.Success(users)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    suspend fun getUsersByIds(userIds: List<Long>): Result<List<User>> {
        return try {
            val users = mutableListOf<User>()
            for (userId in userIds) {
                val user = userRepository.getUserOrAdmin(userId)
                if (user != null) {
                    users.add(user)
                }
            }
            Result.Success(users)
        } catch (e: Exception) {
            Result.Error(e)
        }
    }

    // ==================== FIREBASE SYNC ====================

    suspend fun syncFollowersFromFirebase(userId: Long): Result<Int> {
        return try {
            android.util.Log.d("SocialRepository", "Syncing followers from Firebase for user: $userId")
            
            // Get user's Firebase UID (handles admin negative IDs)
            val user = userRepository.getUserOrAdmin(userId) ?: return Result.Error(Exception("User not found"))
            val userFirebaseUid = user.firebaseUid
            
            if (userFirebaseUid.isNullOrEmpty()) {
                android.util.Log.w("SocialRepository", "User has no Firebase UID, skipping sync")
                return Result.Success(0)
            }

            // Get followers from Firebase
            val firebaseFollowersFlow = firestoreService.getUserFollowers(userFirebaseUid)
            val firebaseFollowers = firebaseFollowersFlow.first()
            
            android.util.Log.d("SocialRepository", "Retrieved ${firebaseFollowers.size} followers from Firebase")
            
            var syncedCount = 0
            val allUsers = userDao.getAllUsers()
            
            for (firebaseFollow in firebaseFollowers) {
                try {
                    // Find follower and following by Firebase UID
                    val follower = allUsers.find { it.firebaseUid == firebaseFollow.followerId }
                    val following = allUsers.find { it.firebaseUid == firebaseFollow.followingId }
                    
                    if (follower == null || following == null) {
                        android.util.Log.w("SocialRepository", "User not found locally for Firebase follow: ${firebaseFollow.id}")
                        continue
                    }
                    
                    // Check if follow already exists locally
                    val existingFollow = userFollowDao.getFollow(follower.id, following.id)
                    
                    if (existingFollow == null) {
                        // Insert new follow
                        val localFollow = UserFollow(
                            followerId = follower.id,
                            followingId = following.id,
                            followedAt = firebaseFollow.followedAt?.toDate()?.time ?: System.currentTimeMillis()
                        )
                        userFollowDao.insertFollow(localFollow)
                        syncedCount++
                        android.util.Log.d("SocialRepository", "Synced new follow: ${follower.username} → ${following.username}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SocialRepository", "Error syncing individual follow: ${firebaseFollow.id}", e)
                }
            }
            
            android.util.Log.d("SocialRepository", "Followers sync completed. Synced $syncedCount follows")
            Result.Success(syncedCount)
            
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Error syncing followers from Firebase", e)
            Result.Error(e)
        }
    }

    suspend fun syncFollowingFromFirebase(userId: Long): Result<Int> {
        return try {
            android.util.Log.d("SocialRepository", "Syncing following from Firebase for user: $userId")
            
            // Get user's Firebase UID (handles admin negative IDs)
            val user = userRepository.getUserOrAdmin(userId) ?: return Result.Error(Exception("User not found"))
            val userFirebaseUid = user.firebaseUid
            
            if (userFirebaseUid.isNullOrEmpty()) {
                android.util.Log.w("SocialRepository", "User has no Firebase UID, skipping sync")
                return Result.Success(0)
            }

            // Get following from Firebase
            val firebaseFollowingFlow = firestoreService.getUserFollowing(userFirebaseUid)
            val firebaseFollowing = firebaseFollowingFlow.first()
            
            android.util.Log.d("SocialRepository", "Retrieved ${firebaseFollowing.size} following from Firebase")
            
            var syncedCount = 0
            val allUsers = userDao.getAllUsers()
            
            for (firebaseFollow in firebaseFollowing) {
                try {
                    // Find follower and following by Firebase UID
                    val follower = allUsers.find { it.firebaseUid == firebaseFollow.followerId }
                    val following = allUsers.find { it.firebaseUid == firebaseFollow.followingId }
                    
                    if (follower == null || following == null) {
                        android.util.Log.w("SocialRepository", "User not found locally for Firebase follow: ${firebaseFollow.id}")
                        continue
                    }
                    
                    // Check if follow already exists locally
                    val existingFollow = userFollowDao.getFollow(follower.id, following.id)
                    
                    if (existingFollow == null) {
                        // Insert new follow
                        val localFollow = UserFollow(
                            followerId = follower.id,
                            followingId = following.id,
                            followedAt = firebaseFollow.followedAt?.toDate()?.time ?: System.currentTimeMillis()
                        )
                        userFollowDao.insertFollow(localFollow)
                        syncedCount++
                        android.util.Log.d("SocialRepository", "Synced new follow: ${follower.username} → ${following.username}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SocialRepository", "Error syncing individual follow: ${firebaseFollow.id}", e)
                }
            }
            
            android.util.Log.d("SocialRepository", "Following sync completed. Synced $syncedCount follows")
            Result.Success(syncedCount)
            
        } catch (e: Exception) {
            android.util.Log.e("SocialRepository", "Error syncing following from Firebase", e)
            Result.Error(e)
        }
    }
}
