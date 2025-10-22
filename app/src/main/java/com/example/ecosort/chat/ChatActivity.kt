package com.example.ecosort.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecosort.databinding.ActivityChatBinding
import com.example.ecosort.data.model.ChatMessage
import com.example.ecosort.data.repository.ChatRepository
import com.example.ecosort.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ChatActivity : AppCompatActivity() {
    
    @Inject
    lateinit var chatRepository: ChatRepository
    
    @Inject
    lateinit var userRepository: UserRepository
    
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatMessagesAdapter
    private var channelId: String? = null
    private var channelName: String? = null
    private var currentUserId: Long = 0L
    private var targetUserId: Long = 0L
    private var targetUsername: String? = null
    
    // Image picker launcher
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val imageUri: Uri? = data?.data
            if (imageUri != null) {
                sendImageMessage(imageUri)
            }
        }
    }
    
    // File picker launcher
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data: Intent? = result.data
            val fileUri: Uri? = data?.data
            if (fileUri != null) {
                sendFileMessage(fileUri)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.d("ChatActivity", "onCreate started")
            binding = ActivityChatBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Get channel info from intent
            channelId = intent.getStringExtra("channel_id")
            channelName = intent.getStringExtra("channel_name")
            targetUserId = intent.getLongExtra("target_user_id", 0L)
            targetUsername = intent.getStringExtra("target_username")
            
            if (channelId == null) {
                Toast.makeText(this, "Invalid chat channel", Toast.LENGTH_SHORT).show()
                // Don't call finish() - let user navigate back manually
                return
            }
            
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = "" // Remove title since we have custom username display
            
            // Load target user profile picture
            loadTargetUserProfilePicture()

            setupClickListeners()
            setupCurrentUser()
            
            android.util.Log.d("ChatActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error initializing chat: ${e.message}", Toast.LENGTH_LONG).show()
            // Don't call finish() - let user navigate back manually
        }
    }
    
    private fun setupCurrentUser() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = userRepository.getCurrentUser()
                when (result) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        currentUserId = result.data.id
                        android.util.Log.d("ChatActivity", "Current user ID: $currentUserId")
                        
                        withContext(Dispatchers.Main) {
                            // Re-setup adapter with correct user ID
                            setupRecyclerView()
                            loadMessages()
                        }
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        android.util.Log.e("ChatActivity", "Error getting current user", result.exception)
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@ChatActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        }
                        // Don't call finish() - let user navigate back manually
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Handle loading state if needed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "Error in setupCurrentUser", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@ChatActivity, "Error getting user info", Toast.LENGTH_SHORT).show()
                }
                // Don't call finish() - let user navigate back manually
            }
        }
    }
    
    private fun loadTargetUserProfilePicture() {
        // Set default values immediately to prevent UI blocking
        binding.textUserName.text = targetUsername ?: "User"
        binding.imageUserProfile.setImageResource(com.example.ecosort.R.drawable.ic_person)
        
        if (targetUserId > 0) {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    val userResult = userRepository.getUserById(targetUserId)
                    if (userResult is com.example.ecosort.data.model.Result.Success) {
                        val user = userResult.data
                        
                        withContext(Dispatchers.Main) {
                            // Update username in toolbar
                            binding.textUserName.text = user.username
                            
                            // Load profile picture
                            if (!user.profileImageUrl.isNullOrBlank()) {
                                com.bumptech.glide.Glide.with(this@ChatActivity)
                                    .load(user.profileImageUrl)
                                    .circleCrop()
                                    .placeholder(com.example.ecosort.R.drawable.ic_person)
                                    .error(com.example.ecosort.R.drawable.ic_person)
                                    .into(binding.imageUserProfile)
                            } else {
                                binding.imageUserProfile.setImageResource(com.example.ecosort.R.drawable.ic_person)
                            }
                        }
                    } else {
                        android.util.Log.e("ChatActivity", "Failed to get target user info")
                        withContext(Dispatchers.Main) {
                            binding.textUserName.text = targetUsername ?: "User"
                            binding.imageUserProfile.setImageResource(com.example.ecosort.R.drawable.ic_person)
                        }
                    }
                } catch (e: Exception) {
                    // Only log if it's not a cancellation exception (which is normal when activity is destroyed)
                    if (e !is kotlinx.coroutines.CancellationException) {
                        android.util.Log.e("ChatActivity", "Error loading target user profile", e)
                    }
                    withContext(Dispatchers.Main) {
                        binding.textUserName.text = targetUsername ?: "User"
                        binding.imageUserProfile.setImageResource(com.example.ecosort.R.drawable.ic_person)
                    }
                }
            }
        }
    }
    
    private fun setupRecyclerView() {
        adapter = ChatMessagesAdapter(currentUserId, this)
        binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.recyclerViewMessages.adapter = adapter
    }
    
    private fun setupClickListeners() {
        binding.buttonSend.setOnClickListener {
            sendTextMessage()
        }
        
        binding.buttonAttachImage.setOnClickListener {
            openImagePicker()
        }
        
        binding.buttonAttachFile.setOnClickListener {
            openFilePicker()
        }
        
        binding.editTextMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendTextMessage()
                true
            } else false
        }
    }
    
    private fun loadMessages() {
        if (currentUserId == 0L) {
            android.util.Log.w("ChatActivity", "Cannot load messages: currentUserId is 0")
            return
        }
        
        lifecycleScope.launch {
            try {
                android.util.Log.d("ChatActivity", "Loading messages for channel: $channelId")
                chatRepository.getMessagesForChannel(channelId!!).collect { messages ->
                    android.util.Log.d("ChatActivity", "Received ${messages.size} messages")
                    adapter.submitList(messages) {
                        // Scroll to bottom after submitting list
                        if (messages.isNotEmpty()) {
                            binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                        }
                    }
                }
            } catch (e: Exception) {
                // Only log if it's not a cancellation exception (which is normal when activity is destroyed)
                if (e !is kotlinx.coroutines.CancellationException) {
                    android.util.Log.e("ChatActivity", "Error loading messages", e)
                    Toast.makeText(this@ChatActivity, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun sendTextMessage() {
        if (currentUserId == 0L) {
            Toast.makeText(this, "Please wait, loading user info...", Toast.LENGTH_SHORT).show()
            return
        }
        
        lifecycleScope.launch {
            try {
                val text = binding.editTextMessage.text?.toString().orEmpty()
                if (text.isBlank()) return@launch
                
                android.util.Log.d("ChatActivity", "Sending message: $text to channel: $channelId")
                
                val result = chatRepository.sendTextMessage(channelId!!, text.trim())
                
                when (result) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        binding.editTextMessage.setText("")
                        android.util.Log.d("ChatActivity", "Message sent successfully")
                        // Message will be automatically added to the list via Flow
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        android.util.Log.e("ChatActivity", "Error sending message", result.exception)
                        Toast.makeText(this@ChatActivity, "Error sending message: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        android.util.Log.d("ChatActivity", "Sending message...")
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "Error in sendTextMessage", e)
                Toast.makeText(this@ChatActivity, "Error sending message: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sendImageMessage(imageUri: Uri) {
        lifecycleScope.launch {
            try {
                val result = chatRepository.sendImageMessage(channelId!!, imageUri.toString())
                
                when (result) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        Toast.makeText(this@ChatActivity, "Image sent", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        android.util.Log.e("ChatActivity", "Error sending image", result.exception)
                        Toast.makeText(this@ChatActivity, "Error sending image", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Handle loading state if needed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "Error in sendImageMessage", e)
                Toast.makeText(this@ChatActivity, "Error sending image", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun sendFileMessage(fileUri: Uri) {
        lifecycleScope.launch {
            try {
                val fileName = getFileName(fileUri) ?: "Unknown file"
                val result = chatRepository.sendFileMessage(channelId!!, fileUri.toString(), fileName)
                
                when (result) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        Toast.makeText(this@ChatActivity, "File sent", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        android.util.Log.e("ChatActivity", "Error sending file", result.exception)
                        Toast.makeText(this@ChatActivity, "Error sending file", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Handle loading state if needed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatActivity", "Error in sendFileMessage", e)
                Toast.makeText(this@ChatActivity, "Error sending file", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        return try {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                cursor.getString(nameIndex)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "Error getting file name", e)
            null
        }
    }
    
    private fun openImagePicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            imagePickerLauncher.launch(intent)
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "Error opening image picker", e)
            Toast.makeText(this, "Error opening image picker", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun openFilePicker() {
        try {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "*/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            }
            filePickerLauncher.launch(intent)
        } catch (e: Exception) {
            android.util.Log.e("ChatActivity", "Error opening file picker", e)
            Toast.makeText(this, "Error opening file picker", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
}