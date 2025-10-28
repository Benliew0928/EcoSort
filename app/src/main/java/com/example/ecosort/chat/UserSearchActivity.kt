package com.example.ecosort.chat

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecosort.databinding.ActivityUserSearchBinding
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.User
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.repository.ChatRepository
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.utils.SecurityManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserSearchActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityUserSearchBinding
    private lateinit var userSearchAdapter: UserSearchAdapter
    
    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var chatRepository: ChatRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.d("UserSearchActivity", "onCreate started")
            binding = ActivityUserSearchBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            setupUI()
            setupRecyclerView()
            setupSearchFunctionality()
            
            android.util.Log.d("UserSearchActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("UserSearchActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error initializing user search: ${e.message}", Toast.LENGTH_LONG).show()
            // Don't call finish() - let user navigate back manually
        }
    }
    
    private fun setupUI() {
        // Set up toolbar
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "New Chat"
        
        // Set up search functionality
        
            // Show empty state initially
            binding.textViewEmpty.text = "Search for users to start a conversation!"
            binding.textViewEmpty.visibility = View.VISIBLE
    }
    
    private fun setupRecyclerView() {
        userSearchAdapter = UserSearchAdapter { user ->
            // Handle user click - start chat
            startChatWithUser(user)
        }
        
        binding.recyclerViewUsers.apply {
            layoutManager = LinearLayoutManager(this@UserSearchActivity)
            adapter = userSearchAdapter
        }
    }
    
    private fun setupSearchFunctionality() {
        binding.editTextSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                if (query.length >= 1) {  // Changed from 2 to 1 for better UX
                    searchUsers(query)
                } else if (query.isEmpty()) {
                    clearSearchResults()
                }
            }
            
            override fun afterTextChanged(s: Editable?) {}
        })
    }
    
    private fun searchUsers(query: String) {
        lifecycleScope.launch {
            try {
                binding.progressBar.visibility = View.VISIBLE
                binding.textViewEmpty.visibility = View.GONE
                binding.recyclerViewUsers.visibility = View.GONE
                
                val result = userRepository.searchUsers(query)
                
                binding.progressBar.visibility = View.GONE
                
                when (result) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        val users = result.data
                        if (users.isNotEmpty()) {
                            userSearchAdapter.submitList(users)
                            binding.recyclerViewUsers.visibility = View.VISIBLE
                            binding.textViewEmpty.visibility = View.GONE
                        } else {
                            binding.textViewEmpty.text = "No users found for '$query'"
                            binding.textViewEmpty.visibility = View.VISIBLE
                            binding.recyclerViewUsers.visibility = View.GONE
                        }
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        binding.textViewEmpty.text = "Error searching users: ${result.exception.message}"
                        binding.textViewEmpty.visibility = View.VISIBLE
                        binding.recyclerViewUsers.visibility = View.GONE
                        Toast.makeText(this@UserSearchActivity, "Search failed", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // This shouldn't happen since we're handling loading state manually
                        binding.progressBar.visibility = View.VISIBLE
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserSearchActivity", "Error in searchUsers", e)
                binding.progressBar.visibility = View.GONE
                binding.textViewEmpty.text = "Error searching users: ${e.message}"
                binding.textViewEmpty.visibility = View.VISIBLE
                binding.recyclerViewUsers.visibility = View.GONE
            }
        }
    }
    
    private fun clearSearchResults() {
        userSearchAdapter.submitList(emptyList())
        binding.recyclerViewUsers.visibility = View.GONE
        binding.textViewEmpty.text = "Search for users to start a conversation!"
        binding.textViewEmpty.visibility = View.VISIBLE
    }
    
    
    
    private fun startChatWithUser(user: User) {
        lifecycleScope.launch {
            try {
                // Get current user
                val currentUserResult = userRepository.getCurrentUser()
                when (currentUserResult) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        val currentUser = currentUserResult.data
                        
                        // Create or get conversation
                        val conversationResult = chatRepository.createOrGetConversation(
                            currentUser.id, currentUser.username,
                            user.id, user.username
                        )
                        
                        when (conversationResult) {
                            is com.example.ecosort.data.model.Result.Success -> {
                                val conversation = conversationResult.data
                                
                                // Navigate to chat activity
                                val intent = Intent(this@UserSearchActivity, ChatActivity::class.java).apply {
                                    putExtra("channel_id", conversation.channelId)
                                    putExtra("channel_name", user.username)
                                    putExtra("target_user_id", user.id)
                                    putExtra("target_username", user.username)
                                }
                                startActivity(intent)
                            }
                            is com.example.ecosort.data.model.Result.Error -> {
                                android.util.Log.e("UserSearchActivity", "Error creating conversation", conversationResult.exception)
                                Toast.makeText(this@UserSearchActivity, "Error creating conversation: ${conversationResult.exception.message}", Toast.LENGTH_SHORT).show()
                            }
                            is com.example.ecosort.data.model.Result.Loading -> {
                                // Show loading state if needed
                            }
                        }
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        android.util.Log.e("UserSearchActivity", "Error getting current user", currentUserResult.exception)
                        Toast.makeText(this@UserSearchActivity, "Please login first", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Show loading state if needed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserSearchActivity", "Error starting chat", e)
                Toast.makeText(this@UserSearchActivity, "Error starting chat: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}