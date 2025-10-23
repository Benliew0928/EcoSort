package com.example.ecosort.friends

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecosort.R
import com.example.ecosort.data.model.FriendRequest
import com.example.ecosort.data.model.User
import com.example.ecosort.data.repository.FriendRepository
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.model.Result
import com.example.ecosort.databinding.ActivityFriendRequestsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FriendRequestsActivity : AppCompatActivity() {

    @Inject
    lateinit var friendRepository: FriendRepository
    
    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var binding: ActivityFriendRequestsBinding
    private lateinit var requestsAdapter: FriendRequestsAdapter
    private var currentUserId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendRequestsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get current user ID from session
        lifecycleScope.launch {
            try {
                val currentUserResult = userRepository.getCurrentUser()
                when (currentUserResult) {
                    is Result.Success<*> -> {
                        currentUserId = (currentUserResult.data as User).id
                        android.util.Log.d("FriendRequestsActivity", "Current user ID set to: $currentUserId")
                        loadFriendRequests()
                    }
                    is Result.Error -> {
                        android.util.Log.e("FriendRequestsActivity", "Error getting current user", currentUserResult.exception)
                        Toast.makeText(this@FriendRequestsActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is Result.Loading -> {
                        // Show loading state if needed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendRequestsActivity", "Exception getting current user", e)
                Toast.makeText(this@FriendRequestsActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        setupUI()
        setupRecyclerView()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupRecyclerView() {
        requestsAdapter = FriendRequestsAdapter(
            onAcceptClick = { request ->
                acceptFriendRequest(request)
            },
            onDeclineClick = { request ->
                declineFriendRequest(request)
            }
        )

        binding.recyclerViewRequests.apply {
            layoutManager = LinearLayoutManager(this@FriendRequestsActivity)
            adapter = requestsAdapter
        }
    }

    private fun loadFriendRequests() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("FriendRequestsActivity", "Loading friend requests for user: $currentUserId")
                friendRepository.getPendingFriendRequests(currentUserId).collect { requests ->
                    if (!isFinishing && !isDestroyed) {
                        android.util.Log.d("FriendRequestsActivity", "Received ${requests.size} friend requests")
                        if (requests.isNotEmpty()) {
                            // Get user details for each request
                            val requestsWithUsers = requests.map { request ->
                                android.util.Log.d("FriendRequestsActivity", "Processing request from sender: ${request.senderId}")
                                FriendRequestWithUser(
                                    request = request,
                                    sender = getUserById(request.senderId)
                                )
                            }
                            requestsAdapter.submitList(requestsWithUsers)
                            binding.recyclerViewRequests.visibility = android.view.View.VISIBLE
                            binding.textViewNoRequests.visibility = android.view.View.GONE
                        } else {
                            android.util.Log.d("FriendRequestsActivity", "No friend requests found")
                            binding.recyclerViewRequests.visibility = android.view.View.GONE
                            binding.textViewNoRequests.visibility = android.view.View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendRequestsActivity", "Error loading friend requests", e)
                if (!isFinishing && !isDestroyed) {
                    Toast.makeText(this@FriendRequestsActivity, "Error loading friend requests: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun acceptFriendRequest(request: FriendRequest) {
        lifecycleScope.launch {
            when (val result = friendRepository.acceptFriendRequest(request.id, currentUserId)) {
                is Result.Success -> {
                    Toast.makeText(this@FriendRequestsActivity, "Friend request accepted!", Toast.LENGTH_SHORT).show()
                    loadFriendRequests() // Refresh the list
                }
                is Result.Error -> {
                    Toast.makeText(this@FriendRequestsActivity, "Error: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                }
                is Result.Loading -> {
                    // Could show loading state
                }
            }
        }
    }

    private fun declineFriendRequest(request: FriendRequest) {
        lifecycleScope.launch {
            when (val result = friendRepository.declineFriendRequest(request.id, currentUserId)) {
                is Result.Success -> {
                    Toast.makeText(this@FriendRequestsActivity, "Friend request declined", Toast.LENGTH_SHORT).show()
                    loadFriendRequests() // Refresh the list
                }
                is Result.Error -> {
                    Toast.makeText(this@FriendRequestsActivity, "Error: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                }
                is Result.Loading -> {
                    // Could show loading state
                }
            }
        }
    }

    private suspend fun getUserById(userId: Long): User? {
        return try {
            // Get user from database
            val database = com.example.ecosort.data.local.EcoSortDatabase.getDatabase(this)
            database.userDao().getUserById(userId)
        } catch (e: Exception) {
            android.util.Log.e("FriendRequestsActivity", "Error getting user by ID: $userId", e)
            null
        }
    }

    data class FriendRequestWithUser(
        val request: FriendRequest,
        val sender: User?
    )
}
