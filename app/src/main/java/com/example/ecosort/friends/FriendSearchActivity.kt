package com.example.ecosort.friends

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.data.repository.FriendRepository
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.model.Result
import com.example.ecosort.databinding.ActivityFriendSearchBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FriendSearchActivity : AppCompatActivity() {

    @Inject
    lateinit var friendRepository: FriendRepository
    
    @Inject
    lateinit var userRepository: UserRepository

    private lateinit var binding: ActivityFriendSearchBinding
    private lateinit var searchAdapter: FriendSearchAdapter
    private var currentUserId: Long = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFriendSearchBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Get current user ID from session
        lifecycleScope.launch {
            try {
                val currentUserResult = userRepository.getCurrentUser()
                when (currentUserResult) {
                    is Result.Success<*> -> {
                        currentUserId = (currentUserResult.data as User).id
                        android.util.Log.d("FriendSearchActivity", "Current user ID set to: $currentUserId")
                    }
                    is Result.Error -> {
                        android.util.Log.e("FriendSearchActivity", "Error getting current user", currentUserResult.exception)
                        Toast.makeText(this@FriendSearchActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                    is Result.Loading -> {
                        // Show loading state if needed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendSearchActivity", "Exception getting current user", e)
                Toast.makeText(this@FriendSearchActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }

        setupUI()
        setupRecyclerView()
        setupSearchListener()
    }

    private fun setupUI() {
        binding.toolbar.setNavigationOnClickListener {
            finish()
        }

        // Add debug button to show all users
        binding.toolbar.menu.add("Show All Users").setOnMenuItemClickListener {
            showAllUsers()
            true
        }
    }

    private fun setupRecyclerView() {
        searchAdapter = FriendSearchAdapter(
            onAddFriendClick = { user ->
                sendFriendRequest(user)
            },
            onCancelRequestClick = { user ->
                // TODO: Implement cancel request
                Toast.makeText(this, "Cancel request functionality coming soon", Toast.LENGTH_SHORT).show()
            }
        )

        binding.recyclerViewSearchResults.apply {
            layoutManager = LinearLayoutManager(this@FriendSearchActivity)
            adapter = searchAdapter
        }
    }

    private fun setupSearchListener() {
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val query = s.toString().trim()
                if (query.length >= 2) {
                    searchUsers(query)
                } else {
                    searchAdapter.submitList(emptyList())
                    binding.recyclerViewSearchResults.visibility = View.GONE
                    binding.textViewNoResults.visibility = View.GONE
                }
            }
        })
    }

    private fun searchUsers(query: String) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("FriendSearchActivity", "Searching for: '$query'")
                binding.progressBar.visibility = View.VISIBLE
                binding.recyclerViewSearchResults.visibility = View.GONE
                binding.textViewNoResults.visibility = View.GONE

                // Use simple search like chat - no complex friend status checking
                when (val result = friendRepository.searchUsers(query, currentUserId)) {
                    is Result.Success -> {
                        binding.progressBar.visibility = View.GONE
                        android.util.Log.d("FriendSearchActivity", "Search returned ${result.data.size} users")
                        result.data.forEach { user ->
                            android.util.Log.d("FriendSearchActivity", "Found user: ${user.username} (ID: ${user.id})")
                        }
                        
                        if (result.data.isNotEmpty()) {
                            // Convert User list to UserWithFriendStatus list with default values
                            val usersWithStatus = result.data.map { user ->
                                FriendRepository.UserWithFriendStatus(
                                    user = user,
                                    isFriend = false, // Will be updated by adapter if needed
                                    hasPendingRequest = false,
                                    hasReceivedRequest = false
                                )
                            }
                            searchAdapter.submitList(usersWithStatus)
                            binding.recyclerViewSearchResults.visibility = View.VISIBLE
                            binding.textViewNoResults.visibility = View.GONE
                        } else {
                            binding.recyclerViewSearchResults.visibility = View.GONE
                            binding.textViewNoResults.visibility = View.VISIBLE
                            binding.textViewNoResults.text = "No users found for \"$query\""
                            android.util.Log.d("FriendSearchActivity", "No users found for query: '$query'")
                        }
                    }
                    is Result.Error -> {
                        binding.progressBar.visibility = View.GONE
                        binding.recyclerViewSearchResults.visibility = View.GONE
                        binding.textViewNoResults.visibility = View.VISIBLE
                        binding.textViewNoResults.text = "Error searching users: ${result.exception.message}"
                        android.util.Log.e("FriendSearchActivity", "Search error: ${result.exception.message}")
                    }
                    is Result.Loading -> {
                        // Already showing progress bar
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendSearchActivity", "Exception in searchUsers", e)
                binding.progressBar.visibility = View.GONE
                binding.recyclerViewSearchResults.visibility = View.GONE
                binding.textViewNoResults.visibility = View.VISIBLE
                binding.textViewNoResults.text = "Error: ${e.message}"
            }
        }
    }

    private fun sendFriendRequest(user: User) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("FriendSearchActivity", "Sending friend request from $currentUserId to ${user.id} (${user.username})")
                when (val result = friendRepository.sendFriendRequest(currentUserId, user.id)) {
                    is Result.Success -> {
                        android.util.Log.d("FriendSearchActivity", "Friend request sent successfully")
                        Toast.makeText(this@FriendSearchActivity, "Friend request sent to ${user.username}", Toast.LENGTH_SHORT).show()
                        // Refresh the search results
                        val query = binding.editTextSearch.text.toString().trim()
                        if (query.isNotEmpty()) {
                            searchUsers(query)
                        }
                    }
                    is Result.Error -> {
                        android.util.Log.e("FriendSearchActivity", "Error sending friend request: ${result.exception.message}")
                        Toast.makeText(this@FriendSearchActivity, "Error: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    is Result.Loading -> {
                        // Could show loading state
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("FriendSearchActivity", "Exception in sendFriendRequest", e)
                Toast.makeText(this@FriendSearchActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAllUsers() {
        lifecycleScope.launch {
            try {
                when (val result = friendRepository.getAllUsersForTesting()) {
                    is Result.Success -> {
                        val users = result.data
                        val message = "All users in database:\n${users.joinToString("\n") { "${it.username} (ID: ${it.id})" }}"
                        Toast.makeText(this@FriendSearchActivity, message, Toast.LENGTH_LONG).show()
                        android.util.Log.d("FriendSearchActivity", "All users: $message")
                    }
                    is Result.Error -> {
                        Toast.makeText(this@FriendSearchActivity, "Error: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    is Result.Loading -> {
                        // Could show loading state
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(this@FriendSearchActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
