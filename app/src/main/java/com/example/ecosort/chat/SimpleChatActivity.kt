package com.example.ecosort.chat

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecosort.databinding.ActivityChatBinding
import com.example.ecosort.data.repository.ChatRepository
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.model.Result
import com.example.ecosort.utils.VoiceRecorder
import com.example.ecosort.utils.VoicePlayer
import com.example.ecosort.utils.CameraHelper
import com.example.ecosort.utils.FileManager
import java.io.File
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SimpleChatActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityChatBinding
    private lateinit var adapter: ChatMessagesAdapter
    
    @Inject
    lateinit var chatRepository: ChatRepository
    
    @Inject
    lateinit var userRepository: UserRepository
    
    private var channelId: String? = null
    private var channelName: String? = null
    private var currentUserId: Long = 0L
    
    // Voice recording and camera utilities
    private lateinit var voiceRecorder: VoiceRecorder
    private lateinit var voicePlayer: VoicePlayer
    private lateinit var cameraHelper: CameraHelper
    private lateinit var fileManager: FileManager
    private var isRecording = false
    
    // Image and file picker contracts
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                sendImageMessage(uri)
            }
        }
    }
    
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraHelper.currentPhotoPath?.let { path ->
                // Send the actual file path, not URI
                sendImageMessage(path)
            }
        } else {
            Toast.makeText(this, "Failed to take photo", Toast.LENGTH_SHORT).show()
        }
    }
    
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                sendFileMessage(uri)
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            android.util.Log.d("SimpleChatActivity", "onCreate started")
            
            // Initialize binding
            binding = ActivityChatBinding.inflate(layoutInflater)
            setContentView(binding.root)
            
            // Get channel info
            channelId = intent.getStringExtra("channel_id")
            channelName = intent.getStringExtra("channel_name")
            android.util.Log.d("SimpleChatActivity", "Channel ID: $channelId, Channel name: $channelName")
            
            if (channelId == null) {
                Toast.makeText(this, "Invalid chat channel", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Setup toolbar
            setSupportActionBar(binding.toolbar)
            supportActionBar?.setDisplayHomeAsUpEnabled(true)
            supportActionBar?.title = channelName ?: "Chat"

            // Setup RecyclerView
            setupRecyclerView()
            
            // Initialize utilities
            voiceRecorder = VoiceRecorder(this)
            voicePlayer = VoicePlayer(this)
            cameraHelper = CameraHelper(this)
            fileManager = FileManager(this)
            
            // Setup click listeners
            setupClickListeners()
            
                    // Load current user and messages
                    setupCurrentUser()
                    
                    // Check microphone availability
                    checkMicrophoneAvailability()
                    
                    // Check audio system
                    checkAudioSystem()

                    android.util.Log.d("SimpleChatActivity", "onCreate completed successfully")
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error in onCreate", e)
            Toast.makeText(this, "Error initializing chat: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun setupRecyclerView() {
        try {
            android.util.Log.d("SimpleChatActivity", "Setting up RecyclerView")
            adapter = ChatMessagesAdapter(currentUserId, this)
            binding.recyclerViewMessages.layoutManager = LinearLayoutManager(this).apply {
                stackFromEnd = true
            }
            binding.recyclerViewMessages.adapter = adapter
            android.util.Log.d("SimpleChatActivity", "RecyclerView setup completed")
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error setting up RecyclerView", e)
            Toast.makeText(this, "Error setting up chat", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupClickListeners() {
        try {
            binding.buttonSend.setOnClickListener {
                sendTextMessage()
            }
            
            binding.buttonCamera.setOnClickListener {
                openCamera()
            }
            
            binding.buttonAttachImage.setOnClickListener {
                openImagePicker()
            }
            
            binding.buttonVoiceRecord.setOnClickListener {
                toggleVoiceRecording()
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
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error setting up click listeners", e)
        }
    }
    
    private fun setupCurrentUser() {
        lifecycleScope.launch {
            try {
                val result = userRepository.getCurrentUser()
                when (result) {
                    is Result.Success -> {
                        currentUserId = result.data.id
                        android.util.Log.d("SimpleChatActivity", "Current user ID: $currentUserId")
                        
                        // Update adapter with current user ID
                        adapter = ChatMessagesAdapter(currentUserId, this@SimpleChatActivity)
                        binding.recyclerViewMessages.adapter = adapter
                        
                        // Load messages for this channel
                        loadMessages()
                    }
                    is Result.Error -> {
                        android.util.Log.e("SimpleChatActivity", "Error getting current user", result.exception)
                        Toast.makeText(this@SimpleChatActivity, "Please login first", Toast.LENGTH_SHORT).show()
                    }
                    is Result.Loading -> {
                        // Show loading state if needed
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SimpleChatActivity", "Error in setupCurrentUser", e)
                Toast.makeText(this@SimpleChatActivity, "Error getting user info", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadMessages() {
        try {
            lifecycleScope.launch {
                try {
                    android.util.Log.d("SimpleChatActivity", "Loading messages for channel: $channelId")
                    chatRepository.getMessagesForChannel(channelId!!).collect { messages ->
                        android.util.Log.d("SimpleChatActivity", "Received ${messages.size} messages")
                        adapter.submitList(messages) {
                            // Scroll to bottom after updating
                            if (messages.isNotEmpty()) {
                                binding.recyclerViewMessages.scrollToPosition(messages.size - 1)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Check if it's a cancellation exception (normal when activity is destroyed)
                    if (e is kotlinx.coroutines.CancellationException) {
                        android.util.Log.d("SimpleChatActivity", "Message loading cancelled (normal)")
                        return@launch
                    }
                    android.util.Log.e("SimpleChatActivity", "Error loading messages", e)
                    // Don't crash the app, just show error message
                    runOnUiThread {
                        Toast.makeText(this@SimpleChatActivity, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error in loadMessages", e)
            // Don't crash the app, just show error message
            Toast.makeText(this, "Error loading messages: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendTextMessage() {
        try {
            val text = binding.editTextMessage.text?.toString().orEmpty()
            if (text.isBlank()) return
            
            android.util.Log.d("SimpleChatActivity", "Sending message: $text to channel: $channelId")
            
            // Clear text first
            binding.editTextMessage.setText("")
            
            // Send message using repository
            lifecycleScope.launch {
                try {
                    val result = chatRepository.sendTextMessage(channelId!!, text.trim())
                    when (result) {
                        is Result.Success -> {
                            android.util.Log.d("SimpleChatActivity", "Message sent successfully")
                            // Message will be added to the list via the Flow in loadMessages()
                        }
                        is Result.Error -> {
                            android.util.Log.e("SimpleChatActivity", "Error sending message", result.exception)
                            Toast.makeText(this@SimpleChatActivity, "Error sending message: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                            // Restore text if sending failed
                            binding.editTextMessage.setText(text)
                        }
                        is Result.Loading -> {
                            // Show loading state if needed
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SimpleChatActivity", "Error in sendTextMessage", e)
                    Toast.makeText(this@SimpleChatActivity, "Error sending message", Toast.LENGTH_SHORT).show()
                    // Restore text if sending failed
                    binding.editTextMessage.setText(text)
                }
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error sending text message", e)
            Toast.makeText(this, "Error sending message: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendImageMessage(imageUri: Uri) {
        try {
            android.util.Log.d("SimpleChatActivity", "Sending image: $imageUri to channel: $channelId")
            
            lifecycleScope.launch {
                try {
                    // For gallery images, copy to persistent storage
                    val fileName = "gallery_image_${System.currentTimeMillis()}.jpg"
                    val persistentFile = fileManager.copyUriToPersistentStorage(imageUri, fileName, "chat_images")
                    val imagePathToSend = persistentFile?.absolutePath ?: imageUri.toString()
                    
                    android.util.Log.d("SimpleChatActivity", "Using image path for sending: $imagePathToSend")
                    
                    val result = chatRepository.sendImageMessage(channelId!!, imagePathToSend)
                    when (result) {
                        is Result.Success -> {
                            android.util.Log.d("SimpleChatActivity", "Image sent successfully")
                        }
                        is Result.Error -> {
                            android.util.Log.e("SimpleChatActivity", "Error sending image", result.exception)
                            Toast.makeText(this@SimpleChatActivity, "Error sending image: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                        }
                        is Result.Loading -> {
                            // Show loading state if needed
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SimpleChatActivity", "Error in sendImageMessage", e)
                    Toast.makeText(this@SimpleChatActivity, "Error sending image", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error sending image message", e)
            Toast.makeText(this, "Error sending image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendImageMessage(filePath: String) {
        try {
            android.util.Log.d("SimpleChatActivity", "Sending image from path: $filePath to channel: $channelId")
            
            lifecycleScope.launch {
                try {
                    // For camera images, copy to persistent storage
                    val sourceFile = File(filePath)
                    val persistentFile = fileManager.copyToPersistentStorage(sourceFile, "chat_images")
                    val imagePathToSend = persistentFile?.absolutePath ?: filePath
                    
                    android.util.Log.d("SimpleChatActivity", "Using image path for sending: $imagePathToSend")
                    
                    val result = chatRepository.sendImageMessage(channelId!!, imagePathToSend)
                    when (result) {
                        is Result.Success -> {
                            android.util.Log.d("SimpleChatActivity", "Image sent successfully")
                        }
                        is Result.Error -> {
                            android.util.Log.e("SimpleChatActivity", "Error sending image", result.exception)
                            Toast.makeText(this@SimpleChatActivity, "Error sending image: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                        }
                        is Result.Loading -> {
                            // Show loading state if needed
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SimpleChatActivity", "Error in sendImageMessage", e)
                    Toast.makeText(this@SimpleChatActivity, "Error sending image", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error sending image message", e)
            Toast.makeText(this, "Error sending image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendFileMessage(fileUri: Uri) {
        try {
            val fileName = getFileName(fileUri)
            android.util.Log.d("SimpleChatActivity", "Sending file: $fileName to channel: $channelId")
            
            lifecycleScope.launch {
                try {
                    val result = chatRepository.sendFileMessage(channelId!!, fileUri.toString(), fileName)
                    when (result) {
                        is Result.Success -> {
                            android.util.Log.d("SimpleChatActivity", "File sent successfully")
                        }
                        is Result.Error -> {
                            android.util.Log.e("SimpleChatActivity", "Error sending file", result.exception)
                            Toast.makeText(this@SimpleChatActivity, "Error sending file: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                        }
                        is Result.Loading -> {
                            // Show loading state if needed
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SimpleChatActivity", "Error in sendFileMessage", e)
                    Toast.makeText(this@SimpleChatActivity, "Error sending file", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error sending file message", e)
            Toast.makeText(this, "Error sending file: ${e.message}", Toast.LENGTH_SHORT).show()
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
            android.util.Log.e("SimpleChatActivity", "Error opening image picker", e)
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
            android.util.Log.e("SimpleChatActivity", "Error opening file picker", e)
            Toast.makeText(this, "Error opening file picker", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun getFileName(uri: Uri): String {
        return try {
            val cursor = contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        it.getString(nameIndex) ?: "Unknown file"
                    } else {
                        "Unknown file"
                    }
                } else {
                    "Unknown file"
                }
            } ?: "Unknown file"
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error getting file name", e)
            "Unknown file"
        }
    }
    
    private fun openCamera() {
        try {
            if (checkCameraPermission()) {
                val imageFile = cameraHelper.createImageFile()
                if (imageFile != null) {
                    // Use FileProvider for Android 7+ compatibility
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this,
                        "${packageName}.fileprovider",
                        imageFile
                    )
                    cameraLauncher.launch(uri)
                } else {
                    Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show()
                }
            } else {
                requestCameraPermission()
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error opening camera", e)
            Toast.makeText(this, "Error opening camera: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun toggleVoiceRecording() {
        try {
            android.util.Log.d("SimpleChatActivity", "Toggle voice recording - isRecording: $isRecording")
            
            if (checkAudioPermission()) {
                android.util.Log.d("SimpleChatActivity", "Audio permission granted")
                if (isRecording) {
                    stopVoiceRecording()
                } else {
                    startVoiceRecording()
                }
            } else {
                android.util.Log.w("SimpleChatActivity", "Audio permission not granted, requesting...")
                requestAudioPermission()
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error toggling voice recording", e)
            Toast.makeText(this, "Error with voice recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun startVoiceRecording() {
        try {
            android.util.Log.d("SimpleChatActivity", "Starting voice recording...")
            val result = voiceRecorder.startRecording()
            if (result.isSuccess) {
                isRecording = true
                binding.buttonVoiceRecord.setImageResource(com.example.ecosort.R.drawable.ic_mic)
                binding.buttonVoiceRecord.alpha = 0.5f
                android.util.Log.d("SimpleChatActivity", "Voice recording started successfully")
                Toast.makeText(this, "Recording... Tap to stop", Toast.LENGTH_SHORT).show()
            } else {
                val error = result.exceptionOrNull()
                android.util.Log.e("SimpleChatActivity", "Error starting recording: ${error?.message}", error)
                Toast.makeText(this, "Error starting recording: ${error?.message}", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error starting voice recording", e)
            Toast.makeText(this, "Error starting recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun stopVoiceRecording() {
        try {
            android.util.Log.d("SimpleChatActivity", "Stopping voice recording...")
            val duration = voiceRecorder.stopRecording()
            isRecording = false
            binding.buttonVoiceRecord.setImageResource(com.example.ecosort.R.drawable.ic_mic)
            binding.buttonVoiceRecord.alpha = 1.0f
            
            android.util.Log.d("SimpleChatActivity", "Recording stopped. Duration: ${duration}ms")
            
            if (duration > 0) {
                val voiceFile = voiceRecorder.getCurrentOutputFile()
                if (voiceFile != null) {
                    android.util.Log.d("SimpleChatActivity", "Voice file created: ${voiceFile.absolutePath}, size: ${voiceFile.length()} bytes")
                    sendVoiceMessage(voiceFile, duration)
                    Toast.makeText(this, "Recording stopped. Duration: ${duration / 1000}s", Toast.LENGTH_SHORT).show()
                } else {
                    android.util.Log.e("SimpleChatActivity", "Voice file is null after recording")
                    Toast.makeText(this, "Error getting voice file", Toast.LENGTH_SHORT).show()
                }
            } else {
                android.util.Log.w("SimpleChatActivity", "Recording duration is 0 or negative")
                Toast.makeText(this, "Recording too short or failed", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error stopping voice recording", e)
            Toast.makeText(this, "Error stopping recording: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun sendVoiceMessage(voiceFile: java.io.File, duration: Long) {
        try {
            android.util.Log.d("SimpleChatActivity", "Sending voice message: ${voiceFile.absolutePath}, duration: ${duration}ms")
            
            lifecycleScope.launch {
                try {
                    // Copy voice file to persistent storage to prevent deletion
                    val persistentFile = fileManager.copyToPersistentStorage(voiceFile, "voice_messages")
                    val filePathToSend = persistentFile?.absolutePath ?: voiceFile.absolutePath
                    
                    android.util.Log.d("SimpleChatActivity", "Using file path for sending: $filePathToSend")
                    
                    val result = chatRepository.sendVoiceMessage(channelId!!, filePathToSend, duration)
                    when (result) {
                        is Result.Success -> {
                            android.util.Log.d("SimpleChatActivity", "Voice message sent successfully")
                        }
                        is Result.Error -> {
                            android.util.Log.e("SimpleChatActivity", "Error sending voice message", result.exception)
                            Toast.makeText(this@SimpleChatActivity, "Error sending voice message: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                        }
                        is Result.Loading -> {
                            // Show loading state if needed
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SimpleChatActivity", "Error in sendVoiceMessage", e)
                    Toast.makeText(this@SimpleChatActivity, "Error sending voice message", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error sending voice message", e)
            Toast.makeText(this, "Error sending voice message: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun checkCameraPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun checkMicrophoneAvailability() {
        try {
            val hasMicrophone = packageManager.hasSystemFeature(PackageManager.FEATURE_MICROPHONE)
            android.util.Log.d("SimpleChatActivity", "Device has microphone: $hasMicrophone")
            
            val audioPermission = checkAudioPermission()
            android.util.Log.d("SimpleChatActivity", "Audio permission granted: $audioPermission")
            
            if (!hasMicrophone) {
                Toast.makeText(this, "This device doesn't have a microphone", Toast.LENGTH_LONG).show()
            } else if (!audioPermission) {
                android.util.Log.w("SimpleChatActivity", "Microphone permission not granted")
                Toast.makeText(this, "Microphone permission required for voice messages", Toast.LENGTH_LONG).show()
            } else {
                android.util.Log.d("SimpleChatActivity", "Microphone is available and permission granted")
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error checking microphone availability", e)
        }
    }
    
    private fun checkAudioSystem() {
        try {
            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            
            android.util.Log.d("SimpleChatActivity", "System Audio - Volume: $currentVolume/$maxVolume")
            
            if (currentVolume == 0) {
                Toast.makeText(this, "Device volume is muted! Please turn up volume.", Toast.LENGTH_LONG).show()
            } else if (currentVolume < maxVolume / 2) {
                Toast.makeText(this, "Device volume is low. Please turn up volume for voice messages.", Toast.LENGTH_SHORT).show()
            }
            
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error checking audio system", e)
        }
    }
    
    private fun checkAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestCameraPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 100)
    }
    
    private fun requestAudioPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
    }
    
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            100 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
                }
            }
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startVoiceRecording()
                } else {
                    Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }
    
    override fun onBackPressed() {
        super.onBackPressed()
    }
    
    override fun onResume() {
        super.onResume()
        // Mark messages as read when the chat is opened/viewed
        channelId?.let { id ->
            lifecycleScope.launch {
                try {
                    chatRepository.markMessagesAsRead(id)
                    android.util.Log.d("SimpleChatActivity", "Messages marked as read for channel: $id")
                } catch (e: Exception) {
                    android.util.Log.e("SimpleChatActivity", "Error marking messages as read", e)
                }
            }
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        try {
            // Cleanup voice recording
            if (isRecording) {
                voiceRecorder.stopRecording()
            }
            voiceRecorder.cleanup()
            voicePlayer.stopPlaying()
            
            // Cleanup old files
            cameraHelper.cleanupOldFiles()
            
            android.util.Log.d("SimpleChatActivity", "Activity destroyed, cleaned up resources")
        } catch (e: Exception) {
            android.util.Log.e("SimpleChatActivity", "Error in onDestroy", e)
        }
    }
}