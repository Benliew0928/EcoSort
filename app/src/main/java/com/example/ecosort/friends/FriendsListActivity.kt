package com.example.ecosort.friends

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.data.repository.FriendRepository
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.model.Result
import com.example.ecosort.utils.BottomNavigationHelper
import com.example.ecosort.databinding.ActivityFriendsListBinding
import com.example.ecosort.profile.UserProfileViewActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class FriendsListActivity : AppCompatActivity() {

    @Inject
    lateinit var friendRepository: FriendRepository
    
    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var binding: ActivityFriendsListBinding
    private lateinit var friendsAdapter: FriendsListAdapter
    private var currentUserId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendsListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get current user ID from session
        lifecycleScope.launch {
            try {
                val currentUserResult = userRepository.getCurrentUser()
                when (currentUserResult) {
                    is Result.Success<*> -> {
                        currentUserId = (currentUserResult.data as User).id
                        android.util.Log.d("FriendsListActivity", "Current user ID set to: $currentUserId")
                        
                        // Sync friend requests and friendships from Firebase in background
                        lifecycleScope.launch(Dispatchers.IO) {
                            try {
                                android.util.Log.d("FriendsListActivity", "Syncing friend requests from Firebase...")
                                val syncResult = friendRepository.syncFriendRequestsFromFirebase(currentUserId)
                                when (syncResult) {
                                    is Result.Success -> {
                                        android.util.Log.d("FriendsListActivity", "Synced ${syncResult.data} friend requests from Firebase")
                                    }
                                    is Result.Error -> {
                                        android.util.Log.e("FriendsListActivity", "Failed to sync friend requests", syncResult.exception)
                                    }
                                    else -> {}
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FriendsListActivity", "Error syncing friend requests", e)
                            }

                            // Also sync friendships
                            try {
                                android.util.Log.d("FriendsListActivity", "Syncing friendships from Firebase...")
                                val friendshipsResult = friendRepository.syncFriendshipsFromFirebase(currentUserId)
                                when (friendshipsResult) {
                                    is Result.Success -> {
                                        android.util.Log.d("FriendsListActivity", "Synced ${friendshipsResult.data} friendships from Firebase")
                                    }
                                    is Result.Error -> {
                                        android.util.Log.e("FriendsListActivity", "Failed to sync friendships", friendshipsResult.exception)
                                    }
                                    else -> {}
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("FriendsListActivity", "Error syncing friendships", e)
                            }
                        }
                        
                        loadFriends()
                    }
                    is Result.Error -> {
                        android.util.Log.e("FriendsListActivity", "Error getting current user", currentUserResult.exception)
                        Toast.makeText(this@FriendsListActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is Result.Loading -> {
                        // Show loading state if needed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendsListActivity", "Exception getting current user", e)
                Toast.makeText(this@FriendsListActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        setupUI()
        setupRecyclerView()
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)
        
        // Raise FAB programmatically to ensure visibility above overlays
        val raiseByDp = 160
        val raiseByPx = (raiseByDp * resources.displayMetrics.density).toInt()
        binding.fabAddFriend.post {
            binding.fabAddFriend.translationY = -raiseByPx.toFloat()
            binding.fabAddFriend.bringToFront()
        }
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        binding.fabAddFriend.setOnClickListener {
            val intent = Intent(this, FriendSearchActivity::class.java)
            startActivity(intent)
        }
    }

    private fun setupRecyclerView() {
        friendsAdapter = FriendsListAdapter(
            onFriendClick = { user ->
                openUserProfile(user)
            },
            onRemoveFriendClick = { user ->
                showRemoveFriendDialog(user)
            },
            onBlockUserClick = { user ->
                showBlockUserDialog(user)
            }
        )

        binding.recyclerViewFriends.apply {
            layoutManager = LinearLayoutManager(this@FriendsListActivity)
            adapter = friendsAdapter
        }
    }

    private fun loadFriends() {
        lifecycleScope.launch {
            friendRepository.getFriends(currentUserId).collect { friends ->
                if (friends.isNotEmpty()) {
                    friendsAdapter.submitList(friends)
                    binding.recyclerViewFriends.visibility = android.view.View.VISIBLE
                    binding.textViewNoFriends.visibility = android.view.View.GONE
                } else {
                    binding.recyclerViewFriends.visibility = android.view.View.GONE
                    binding.textViewNoFriends.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    private fun openUserProfile(user: User) {
        val intent = Intent(this, UserProfileViewActivity::class.java)
        intent.putExtra("user_id", user.id)
        startActivity(intent)
    }

    private fun showRemoveFriendDialog(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Remove Friend")
            .setMessage("Are you sure you want to remove ${user.username} from your friends?")
            .setPositiveButton("Remove") { _, _ ->
                removeFriend(user)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showBlockUserDialog(user: User) {
        AlertDialog.Builder(this)
            .setTitle("Block User")
            .setMessage("Are you sure you want to block ${user.username}? This will remove them from your friends and prevent them from contacting you.")
            .setPositiveButton("Block") { _, _ ->
                blockUser(user)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun removeFriend(user: User) {
        lifecycleScope.launch {
            when (val result = friendRepository.removeFriend(currentUserId, user.id)) {
                is Result.Success -> {
                    Toast.makeText(this@FriendsListActivity, "${user.username} removed from friends", Toast.LENGTH_SHORT).show()
                    loadFriends() // Refresh the list
                }
                is Result.Error -> {
                    Toast.makeText(this@FriendsListActivity, "Error: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                }
                is Result.Loading -> {
                    // Could show loading state
                }
            }
        }
    }

    private fun blockUser(user: User) {
        lifecycleScope.launch {
            when (val result = friendRepository.blockUser(currentUserId, user.id)) {
                is Result.Success -> {
                    Toast.makeText(this@FriendsListActivity, "${user.username} has been blocked", Toast.LENGTH_SHORT).show()
                    loadFriends() // Refresh the list
                }
                is Result.Error -> {
                    Toast.makeText(this@FriendsListActivity, "Error: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                }
                is Result.Loading -> {
                    // Could show loading state
                }
            }
        }
    }
}
