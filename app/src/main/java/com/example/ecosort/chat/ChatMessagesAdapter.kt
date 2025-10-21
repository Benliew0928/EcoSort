package com.example.ecosort.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.ChatMessage
import com.example.ecosort.utils.VoicePlayer
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ChatMessagesAdapter(
    private val currentUserId: Long,
    private val context: android.content.Context
) : ListAdapter<ChatMessage, ChatMessagesAdapter.MessageViewHolder>(MessageDiffCallback()) {
    
    private val voicePlayer = VoicePlayer(context)
    private val fallbackHandlers = mutableMapOf<String, android.os.Handler>()

    inner class MessageViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val textMessage: TextView = itemView.findViewById(R.id.text_message)
        val textTime: TextView = itemView.findViewById(R.id.text_time)
        val messageContainer: android.widget.LinearLayout = itemView.findViewById(R.id.message_container)
        val messageContentContainer: android.widget.LinearLayout = itemView.findViewById(R.id.message_content_container)

        // Different message type views
        val imageMessage: android.widget.ImageView = itemView.findViewById(R.id.image_message)
        val voiceMessageContainer: android.widget.LinearLayout = itemView.findViewById(R.id.voice_message_container)
        val voicePlayButton: android.widget.ImageView = itemView.findViewById(R.id.voice_play_button)
        val voiceDuration: TextView = itemView.findViewById(R.id.voice_duration)
        val voiceProgressBar: android.widget.ProgressBar = itemView.findViewById(R.id.voice_progress_bar)
        val fileMessageContainer: android.widget.LinearLayout = itemView.findViewById(R.id.file_message_container)
        val fileName: TextView = itemView.findViewById(R.id.file_name)

        // Message status
        val messageStatus: android.widget.ImageView = itemView.findViewById(R.id.message_status)
        val textStatusLabel: TextView = itemView.findViewById(R.id.text_status_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        try {
            val message = getItem(position)
            val isMine = message.senderId == currentUserId
            
            // Format timestamp
            val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
            val timeString = timeFormat.format(Date(message.timestamp))
            
            // Hide all message type views first
            holder.textMessage.visibility = android.view.View.GONE
            holder.imageMessage.visibility = android.view.View.GONE
            holder.voiceMessageContainer.visibility = android.view.View.GONE
            holder.fileMessageContainer.visibility = android.view.View.GONE
            
            // Show appropriate message type
            when (message.messageType) {
                com.example.ecosort.data.model.MessageType.TEXT -> {
                    holder.textMessage.visibility = android.view.View.VISIBLE
                    holder.textMessage.text = message.messageText
                }
                com.example.ecosort.data.model.MessageType.IMAGE -> {
                    holder.imageMessage.visibility = android.view.View.VISIBLE
                    // Load image from file path
                    loadImageFromFile(holder.imageMessage, message.attachmentUrl)
                }
                com.example.ecosort.data.model.MessageType.VOICE -> {
                    holder.voiceMessageContainer.visibility = android.view.View.VISIBLE
                    val duration = message.attachmentDuration ?: 0L
                    val formattedDuration = formatDuration(duration)
                    holder.voiceDuration.text = formattedDuration
                    
                    // Only reset progress bar if not currently playing this specific message
                    if (!voicePlayer.isPlayingFile(File(message.attachmentUrl ?: ""))) {
                        holder.voiceProgressBar.visibility = android.view.View.GONE
                        holder.voiceProgressBar.progress = 0
                        holder.voicePlayButton.setImageResource(R.drawable.ic_play_arrow)
                    } else {
                        // If currently playing, show progress bar
                        holder.voiceProgressBar.visibility = android.view.View.VISIBLE
                        holder.voicePlayButton.setImageResource(R.drawable.ic_pause)
                    }
                    
                    
                    android.util.Log.d("ChatMessagesAdapter", "Voice message duration: ${duration}ms, formatted: ${formattedDuration}")
                            // Set up voice play button click listener
                            holder.voicePlayButton.setOnClickListener {
                                try {
                                    if (message.attachmentUrl != null) {
                                        val voiceFile = File(message.attachmentUrl)
                                        android.util.Log.d("ChatMessagesAdapter", "Voice file path: ${voiceFile.absolutePath}")
                                        android.util.Log.d("ChatMessagesAdapter", "Voice file exists: ${voiceFile.exists()}")
                                        android.util.Log.d("ChatMessagesAdapter", "Voice file size: ${voiceFile.length()} bytes")
                                        
                                        if (voiceFile.exists() && voiceFile.length() > 0) {
                                            // Check if this specific file is currently playing
                                            if (voicePlayer.isPlayingFile(voiceFile)) {
                                                // Currently playing this file - stop it
                                                android.util.Log.d("ChatMessagesAdapter", "Stopping voice message")
                                                stopFallbackProgressAnimation(voiceFile)
                                                voicePlayer.stopPlaying()
                                                holder.voicePlayButton.setImageResource(R.drawable.ic_play_arrow)
                                                holder.voiceProgressBar.visibility = android.view.View.GONE
                                                holder.voiceProgressBar.progress = 0
                                            } else {
                                                // Not playing - start playback
                                                android.util.Log.d("ChatMessagesAdapter", "Starting voice message playback")
                                                
                                                // Update button to show playing state
                                                holder.voicePlayButton.setImageResource(R.drawable.ic_pause)
                                                
                                                // Show progress bar and ensure it's visible
                                                holder.voiceProgressBar.visibility = android.view.View.VISIBLE
                                                holder.voiceProgressBar.progress = 0
                                                holder.voiceProgressBar.max = 100
                                                holder.voiceProgressBar.invalidate() // Force redraw
                                                android.util.Log.d("ChatMessagesAdapter", "Progress bar made visible, progress set to 0")
                                                
                                                
                                                // Start fallback progress animation based on actual audio duration
                                                val audioDuration = message.attachmentDuration ?: 5000L // Default to 5 seconds if duration not available
                                                startFallbackProgressAnimation(holder.voiceProgressBar, voiceFile, audioDuration)
                                                
                                                // Play the voice message with progress tracking
                                                voicePlayer.playVoiceMessage(voiceFile, 
                                                    onCompletion = {
                                                        // Playback completed - reset button and hide progress
                                                        android.util.Log.d("ChatMessagesAdapter", "Playback completed, hiding progress bar")
                                                        stopFallbackProgressAnimation(voiceFile)
                                                        holder.voicePlayButton.setImageResource(R.drawable.ic_play_arrow)
                                                        holder.voiceProgressBar.visibility = android.view.View.GONE
                                                        holder.voiceProgressBar.progress = 0
                                                        android.util.Log.d("ChatMessagesAdapter", "MediaPlayer voice playback completed")
                                                    },
                                                    onProgress = { currentPosition, totalDuration ->
                                                        // Update progress bar on main thread
                                                        android.util.Log.d("ChatMessagesAdapter", "*** PROGRESS CALLBACK RECEIVED: $currentPosition/$totalDuration ***")
                                                        if (totalDuration > 0) {
                                                            val progress = (currentPosition * 100) / totalDuration
                                                            android.util.Log.d("ChatMessagesAdapter", "*** CALCULATED PROGRESS: $progress% ***")
                                                            
                                                            // Always post to main thread to ensure UI updates
                                                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                                                holder.voiceProgressBar.visibility = android.view.View.VISIBLE
                                                                holder.voiceProgressBar.progress = progress
                                                                holder.voiceProgressBar.invalidate() // Force redraw
                                                                android.util.Log.d("ChatMessagesAdapter", "*** PROGRESS BAR UPDATED TO: $progress% ***")
                                                            }
                                                        } else {
                                                            android.util.Log.w("ChatMessagesAdapter", "*** TOTAL DURATION IS 0! ***")
                                                        }
                                                    }
                                                )
                                            }
                                            
                                        } else {
                                            android.util.Log.e("ChatMessagesAdapter", "Voice file not found or empty: ${message.attachmentUrl}")
                                            android.util.Log.e("ChatMessagesAdapter", "File path: ${voiceFile.absolutePath}")
                                            android.util.Log.e("ChatMessagesAdapter", "File exists: ${voiceFile.exists()}")
                                            android.util.Log.e("ChatMessagesAdapter", "File size: ${voiceFile.length()}")
                                            
                                            // Show error feedback
                                            android.widget.Toast.makeText(context, "Voice file not available - may have been deleted", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        android.util.Log.e("ChatMessagesAdapter", "No attachment URL for voice message")
                                        android.widget.Toast.makeText(context, "Voice message not available", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    android.util.Log.e("ChatMessagesAdapter", "Error playing voice message", e)
                                    android.widget.Toast.makeText(context, "Error playing voice message: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    // Reset UI on error
                                    holder.voicePlayButton.setImageResource(R.drawable.ic_play_arrow)
                                    holder.voiceProgressBar.visibility = android.view.View.GONE
                                    holder.voiceProgressBar.progress = 0
                                    if (message.attachmentUrl != null) {
                                        stopFallbackProgressAnimation(File(message.attachmentUrl))
                                    }
                                }
                            }
                }
                com.example.ecosort.data.model.MessageType.FILE -> {
                    holder.fileMessageContainer.visibility = android.view.View.VISIBLE
                    holder.fileName.text = message.messageText
                }
            }
            
            // Set time and status
            holder.textTime.text = timeString
            setMessageStatus(holder.messageStatus, holder.textStatusLabel, message.messageStatus, isMine)

            // Set background and alignment based on message sender
            if (isMine) {
                holder.messageContentContainer.setBackgroundResource(R.drawable.message_bubble_my)
                holder.messageContainer.gravity = android.view.Gravity.END
                holder.textTime.gravity = android.view.Gravity.END
                holder.messageStatus.visibility = android.view.View.VISIBLE
            } else {
                holder.messageContentContainer.setBackgroundResource(R.drawable.message_bubble_other)
                holder.messageContainer.gravity = android.view.Gravity.START
                holder.textTime.gravity = android.view.Gravity.START
                holder.messageStatus.visibility = android.view.View.GONE // Don't show status for other's messages
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ChatMessagesAdapter", "Error binding view", e)
        }
    }
    
    private fun loadImageFromFile(imageView: android.widget.ImageView, filePath: String?) {
        try {
            if (filePath != null) {
                android.util.Log.d("ChatMessagesAdapter", "Loading image from: $filePath")
                
                // Check if it's a URI or file path
                if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
                    // Handle URI
                    val uri = android.net.Uri.parse(filePath)
                    imageView.setImageURI(uri)
                    android.util.Log.d("ChatMessagesAdapter", "Loaded image from URI: $uri")
                } else {
                    // Handle file path
                    val file = java.io.File(filePath)
                    android.util.Log.d("ChatMessagesAdapter", "File exists: ${file.exists()}, size: ${file.length()} bytes")
                    
                    if (file.exists() && file.length() > 0) {
                        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath)
                        if (bitmap != null) {
                            imageView.setImageBitmap(bitmap)
                            android.util.Log.d("ChatMessagesAdapter", "Loaded image from file: ${file.name}")
                        } else {
                            android.util.Log.e("ChatMessagesAdapter", "Failed to decode bitmap from file")
                            imageView.setImageResource(R.drawable.ic_image)
                        }
                    } else {
                        android.util.Log.e("ChatMessagesAdapter", "Image file not found or empty: $filePath")
                        imageView.setImageResource(R.drawable.ic_image)
                    }
                }
            } else {
                android.util.Log.e("ChatMessagesAdapter", "No file path provided for image")
                imageView.setImageResource(R.drawable.ic_image)
            }
        } catch (e: Exception) {
            android.util.Log.e("ChatMessagesAdapter", "Error loading image", e)
            imageView.setImageResource(R.drawable.ic_image)
        }
    }
    
    private fun formatDuration(durationMs: Long): String {
        val seconds = durationMs / 1000
        val minutes = seconds / 60
        val remainingSeconds = seconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
    
    /**
     * Start a fallback progress animation to ensure progress bar is visible during playback
     */
    private fun startFallbackProgressAnimation(progressBar: android.widget.ProgressBar, voiceFile: File, audioDuration: Long = 10000L) {
        android.util.Log.d("ChatMessagesAdapter", "Starting fallback progress animation for: ${voiceFile.name}")
        
        // Stop any existing animation for this file
        stopFallbackProgressAnimation(voiceFile)
        
        val handler = android.os.Handler(android.os.Looper.getMainLooper())
        fallbackHandlers[voiceFile.absolutePath] = handler
        
        // Ensure progress bar is visible and properly configured
        progressBar.visibility = android.view.View.VISIBLE
        progressBar.progress = 0
        progressBar.max = 100
        progressBar.invalidate() // Force redraw
        
        var progress = 0
        val totalDuration = audioDuration // Use actual audio duration
        val updateInterval = 100L // Update every 100ms
        val progressIncrement = 100.0 / (totalDuration / updateInterval) // Calculate increment based on actual duration
        
        android.util.Log.d("ChatMessagesAdapter", "Fallback animation: increment=$progressIncrement, interval=${updateInterval}ms, total=${totalDuration}ms (actual audio duration)")
        
        val runnable = object : Runnable {
            override fun run() {
                // Check if this specific file is still playing
                if (voicePlayer.isPlayingFile(voiceFile) && progress < 100) {
                    progress += progressIncrement.toInt()
                    if (progress > 100) progress = 100
                    
                    // Update progress bar directly (we're already on main thread)
                    progressBar.progress = progress
                    progressBar.visibility = android.view.View.VISIBLE
                    progressBar.invalidate() // Force redraw
                    android.util.Log.d("ChatMessagesAdapter", "Fallback progress: $progress% (visible=${progressBar.visibility}, max=${progressBar.max})")
                    
                    if (progress < 100) {
                        handler.postDelayed(this, updateInterval)
                    } else {
                        android.util.Log.d("ChatMessagesAdapter", "Fallback progress completed")
                        fallbackHandlers.remove(voiceFile.absolutePath)
                    }
                } else if (!voicePlayer.isPlayingFile(voiceFile)) {
                    android.util.Log.d("ChatMessagesAdapter", "Fallback animation stopped - not playing")
                    fallbackHandlers.remove(voiceFile.absolutePath)
                } else {
                    // Progress completed, but keep animation running if still playing
                    android.util.Log.d("ChatMessagesAdapter", "Fallback animation at 100%, keeping visible")
                    handler.postDelayed(this, updateInterval)
                }
            }
        }
        
        handler.post(runnable)
    }
    
    /**
     * Stop fallback progress animation for a specific file
     */
    private fun stopFallbackProgressAnimation(voiceFile: File) {
        fallbackHandlers[voiceFile.absolutePath]?.let { handler ->
            handler.removeCallbacksAndMessages(null)
            fallbackHandlers.remove(voiceFile.absolutePath)
            android.util.Log.d("ChatMessagesAdapter", "Stopped fallback progress animation for: ${voiceFile.name}")
        }
    }
    
    
    private fun setMessageStatus(statusView: android.widget.ImageView, statusLabelView: TextView, status: com.example.ecosort.data.model.MessageStatus, isMine: Boolean) {
        if (!isMine) {
            statusView.visibility = android.view.View.GONE
            statusLabelView.visibility = android.view.View.GONE
            return
        }
        
        statusView.visibility = android.view.View.VISIBLE
        statusLabelView.visibility = android.view.View.VISIBLE
        
        when (status) {
            com.example.ecosort.data.model.MessageStatus.SENDING -> {
                statusView.setImageResource(R.drawable.ic_done)
                statusView.alpha = 0.5f
                statusLabelView.text = "sending"
                statusLabelView.alpha = 0.5f
            }
            com.example.ecosort.data.model.MessageStatus.SENT -> {
                statusView.setImageResource(R.drawable.ic_done)
                statusView.alpha = 1.0f
                statusLabelView.text = "sent"
                statusLabelView.alpha = 1.0f
            }
            com.example.ecosort.data.model.MessageStatus.DELIVERED -> {
                statusView.setImageResource(R.drawable.ic_done_all)
                statusView.alpha = 0.5f
                statusLabelView.text = "delivered"
                statusLabelView.alpha = 0.5f
            }
            com.example.ecosort.data.model.MessageStatus.SEEN -> {
                statusView.setImageResource(R.drawable.ic_done_all)
                statusView.alpha = 1.0f
                statusLabelView.text = "seen"
                statusLabelView.alpha = 1.0f
            }
            com.example.ecosort.data.model.MessageStatus.READ -> {
                statusView.setImageResource(R.drawable.ic_done_all)
                statusView.alpha = 1.0f
                statusLabelView.text = "read"
                statusLabelView.alpha = 1.0f
            }
        }
    }

    class MessageDiffCallback : DiffUtil.ItemCallback<ChatMessage>() {
        override fun areItemsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: ChatMessage, newItem: ChatMessage): Boolean {
            return oldItem == newItem
        }
    }
    
}


