package com.example.ecosort.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.Conversation
import com.example.ecosort.data.repository.UserRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.*
import javax.inject.Inject

class ConversationAdapter(
    private val currentUserId: Long,
    private val onConversationClick: (Conversation) -> Unit,
    private val onDeleteClick: (Conversation) -> Unit,
    private val userRepository: UserRepository? = null,
    private val lifecycleScope: CoroutineScope? = null
) : ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    inner class ConversationViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val imageUserAvatar: android.widget.ImageView = itemView.findViewById(R.id.image_user_avatar)
        val textOtherUsername: TextView = itemView.findViewById(R.id.text_other_username)
        val textLastMessage: TextView = itemView.findViewById(R.id.text_last_message)
        val textLastMessageTime: TextView = itemView.findViewById(R.id.text_last_message_time)
        val textUnreadCount: TextView = itemView.findViewById(R.id.text_unread_count)
        val btnDeleteConversation: android.widget.ImageButton = itemView.findViewById(R.id.btn_delete_conversation)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConversationViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_conversation, parent, false)
        return ConversationViewHolder(view)
    }

    override fun onBindViewHolder(holder: ConversationViewHolder, position: Int) {
        try {
            val conversation = getItem(position)
            
            // Determine the other participant
            val otherUserId = if (conversation.participant1Id == currentUserId) {
                conversation.participant2Id
            } else {
                conversation.participant1Id
            }
            
            val otherUsername = if (conversation.participant1Id == currentUserId) {
                conversation.participant2Username
            } else {
                conversation.participant1Username
            }
            
            holder.textOtherUsername.text = otherUsername
            holder.textLastMessage.text = conversation.lastMessageText ?: "No messages yet"
            
            // Load profile picture
            loadProfilePicture(holder, otherUserId)
            
            // Format timestamp
            if (conversation.lastMessageTimestamp > 0) {
                val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
                val timeString = timeFormat.format(Date(conversation.lastMessageTimestamp))
                holder.textLastMessageTime.text = timeString
                holder.textLastMessageTime.visibility = android.view.View.VISIBLE
            } else {
                holder.textLastMessageTime.visibility = android.view.View.GONE
            }
            
            // Show unread count
            if (conversation.unreadCount > 0) {
                holder.textUnreadCount.text = conversation.unreadCount.toString()
                holder.textUnreadCount.visibility = android.view.View.VISIBLE
            } else {
                holder.textUnreadCount.visibility = android.view.View.GONE
            }
            
            // Set click listeners
            holder.itemView.setOnClickListener {
                onConversationClick(conversation)
            }
            
            holder.btnDeleteConversation.setOnClickListener {
                onDeleteClick(conversation)
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ConversationAdapter", "Error binding view", e)
        }
    }
    
    private fun loadProfilePicture(holder: ConversationViewHolder, userId: Long) {
        // Set default first
        holder.imageUserAvatar.setImageResource(R.drawable.ic_person)
        
        // Load actual profile picture if UserRepository and lifecycleScope are available
        userRepository?.let { repository ->
            lifecycleScope?.let { scope ->
                scope.launch(Dispatchers.IO) {
                    try {
                        val userResult = repository.getUserById(userId)
                        if (userResult is com.example.ecosort.data.model.Result.Success) {
                            val user = userResult.data
                            withContext(Dispatchers.Main) {
                                if (!user.profileImageUrl.isNullOrBlank()) {
                                    Glide.with(holder.itemView.context)
                                        .load(user.profileImageUrl)
                                        .circleCrop()
                                        .placeholder(R.drawable.ic_person)
                                        .error(R.drawable.ic_person)
                                        .into(holder.imageUserAvatar)
                                } else {
                                    holder.imageUserAvatar.setImageResource(R.drawable.ic_person)
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Only log if it's not a cancellation exception (which is normal when activity is destroyed)
                        if (e !is kotlinx.coroutines.CancellationException) {
                            android.util.Log.e("ConversationAdapter", "Error loading profile picture for user $userId", e)
                        }
                    }
                }
            }
        }
    }

    class ConversationDiffCallback : DiffUtil.ItemCallback<Conversation>() {
        override fun areItemsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem.channelId == newItem.channelId
        }

        override fun areContentsTheSame(oldItem: Conversation, newItem: Conversation): Boolean {
            return oldItem == newItem
        }
    }
}
