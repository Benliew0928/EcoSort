package com.example.ecosort.chat

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecosort.databinding.ActivityChatListBinding
import com.example.ecosort.data.repository.ChatRepository
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.model.Result
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ChatListActivity : AppCompatActivity() {
    
    @Inject
    lateinit var chatRepository: ChatRepository
    
    @Inject
    lateinit var userRepository: UserRepository
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.d("ChatListActivity", "onCreate started")
            val binding = ActivityChatListBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            android.util.Log.d("ChatListActivity", "Setting up UI")
            
            // Set up toolbar
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "Messages"
            
            // Set up FAB for new chat
            binding.fabNewChat.setOnClickListener {
                try {
                    startActivity(Intent(this, UserSearchActivity::class.java))
                } catch (e: Exception) {
                    android.util.Log.e("ChatListActivity", "Error starting UserSearchActivity", e)
                    Toast.makeText(this, "Chat feature coming soon!", Toast.LENGTH_SHORT).show()
                }
            }
            
            // Setup RecyclerView
            setupRecyclerView(binding)
            
            // Load conversations
            loadConversations(binding)
            
            android.util.Log.d("ChatListActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("ChatListActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error initializing chat: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupRecyclerView(binding: ActivityChatListBinding) {
        try {
            val adapter = ConversationAdapter(0L) { conversation ->
                // Handle conversation click
                val intent = Intent(this, SimpleChatActivity::class.java).apply {
                    putExtra("channel_id", conversation.channelId)
                    putExtra("channel_name", if (conversation.participant1Id == 0L) conversation.participant2Username else conversation.participant1Username)
                }
                startActivity(intent)
            }
            
            binding.recyclerViewChats.apply {
                layoutManager = LinearLayoutManager(this@ChatListActivity)
                this.adapter = adapter
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatListActivity", "Error setting up RecyclerView", e)
        }
    }
    
    private fun loadConversations(binding: ActivityChatListBinding) {
        lifecycleScope.launch {
            try {
                // Get current user first
                val currentUserResult = userRepository.getCurrentUser()
                when (currentUserResult) {
                    is Result.Success -> {
                        val currentUser = currentUserResult.data
                        
                        // Update adapter with current user ID
                        val adapter = ConversationAdapter(currentUser.id) { conversation ->
                            // Handle conversation click
                            val intent = Intent(this@ChatListActivity, SimpleChatActivity::class.java).apply {
                                putExtra("channel_id", conversation.channelId)
                                putExtra("channel_name", if (conversation.participant1Id == currentUser.id) conversation.participant2Username else conversation.participant1Username)
                            }
                            startActivity(intent)
                        }
                        
                        binding.recyclerViewChats.adapter = adapter
                        
                        // Load conversations
                        chatRepository.getConversationsForUser(currentUser.id).collect { conversations ->
                            if (conversations.isNotEmpty()) {
                                adapter.submitList(conversations)
                                binding.recyclerViewChats.visibility = View.VISIBLE
                                binding.textViewEmpty.visibility = View.GONE
                            } else {
                                binding.recyclerViewChats.visibility = View.GONE
                                binding.textViewEmpty.visibility = View.VISIBLE
                                binding.textViewEmpty.text = "No conversations yet.\nTap the + button to start a new chat!"
                            }
                        }
                    }
                    is Result.Error -> {
                        android.util.Log.e("ChatListActivity", "Error getting current user", currentUserResult.exception)
                        binding.textViewEmpty.text = "Please login first"
                        binding.textViewEmpty.visibility = View.VISIBLE
                    }
                    is Result.Loading -> {
                        // Show loading state if needed
                    }
                }
            } catch (e: Exception) {
                // Check if it's a cancellation exception (normal when activity is destroyed)
                if (e is kotlinx.coroutines.CancellationException) {
                    android.util.Log.d("ChatListActivity", "Conversation loading cancelled (normal)")
                    return@launch
                }
                android.util.Log.e("ChatListActivity", "Error loading conversations", e)
                binding.textViewEmpty.text = "Error loading conversations: ${e.message}"
                binding.textViewEmpty.visibility = View.VISIBLE
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}