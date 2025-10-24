package com.example.ecosort.social

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserFollow
import com.example.ecosort.data.repository.SocialRepository
import com.example.ecosort.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FollowingListActivity : AppCompatActivity(), FollowingListAdapter.OnFollowingActionListener {

    @Inject
    lateinit var socialRepository: SocialRepository

    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FollowingListAdapter
    private var followingList: List<UserFollow> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_following_list)

        setupUI()
        setupRecyclerView()
        setupListeners()
        loadFollowingList()
    }

    override fun onResume() {
        super.onResume()
        loadFollowingList() // Refresh following list when returning to this activity
    }

    private fun setupUI() {
        btnBack = findViewById(R.id.btnBackFollowingList)
        tvTitle = findViewById(R.id.tvFollowingListTitle)
        recyclerView = findViewById(R.id.recyclerViewFollowing)
    }

    private fun setupRecyclerView() {
        adapter = FollowingListAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
    }

    private fun loadFollowingList() {
        lifecycleScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser is com.example.ecosort.data.model.Result.Success) {
                    socialRepository.getUserFollowing(currentUser.data.id).collect { following ->
                        followingList = following
                        // Convert UserFollow to User objects for display
                        val userIds = following.map { it.followingId }
                        val users = mutableListOf<User>()
                        
                        for (userId in userIds) {
                            val userResult = userRepository.getUserById(userId)
                            if (userResult is com.example.ecosort.data.model.Result.Success) {
                                users.add(userResult.data)
                            }
                        }
                        
                        adapter.submitList(users)
                    }
                } else {
                    Toast.makeText(this@FollowingListActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation gracefully - this is expected when activity is destroyed
                android.util.Log.d("FollowingListActivity", "Following list loading cancelled")
            } catch (e: Exception) {
                Toast.makeText(this@FollowingListActivity, "Error loading following list: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onUnfollowUser(user: User) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Unfollow User")
            .setMessage("Are you sure you want to unfollow '${user.username}'?")
            .setPositiveButton("Unfollow") { _, _ ->
                unfollowUser(user)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onViewUserProfile(user: User) {
        // Navigate to user profile
        val intent = android.content.Intent(this, com.example.ecosort.profile.UserProfileViewActivity::class.java)
        intent.putExtra("user_id", user.id)
        startActivity(intent)
    }

    private fun unfollowUser(user: User) {
        lifecycleScope.launch {
            try {
                val currentUser = userRepository.getCurrentUser()
                if (currentUser is com.example.ecosort.data.model.Result.Success) {
                    val result = socialRepository.unfollowUser(currentUser.data.id, user.id)
                    if (result is com.example.ecosort.data.model.Result.Success) {
                        Toast.makeText(this@FollowingListActivity, "Unfollowed '${user.username}'", Toast.LENGTH_SHORT).show()
                        loadFollowingList() // Refresh the list
                    } else {
                        Toast.makeText(this@FollowingListActivity, "Error unfollowing user: ${(result as com.example.ecosort.data.model.Result.Error).exception.message}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this@FollowingListActivity, "Error loading user data", Toast.LENGTH_SHORT).show()
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation gracefully - this is expected when activity is destroyed
                android.util.Log.d("FollowingListActivity", "Unfollow operation cancelled")
            } catch (e: Exception) {
                Toast.makeText(this@FollowingListActivity, "Error unfollowing user: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
