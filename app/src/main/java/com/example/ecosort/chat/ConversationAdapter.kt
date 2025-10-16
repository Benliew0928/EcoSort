package com.example.ecosort.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.Conversation
import java.text.SimpleDateFormat
import java.util.*

class ConversationAdapter(
    private val currentUserId: Long,
    private val onConversationClick: (Conversation) -> Unit
) : ListAdapter<Conversation, ConversationAdapter.ConversationViewHolder>(ConversationDiffCallback()) {

    inner class ConversationViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val textOtherUsername: TextView = itemView.findViewById(R.id.text_other_username)
        val textLastMessage: TextView = itemView.findViewById(R.id.text_last_message)
        val textLastMessageTime: TextView = itemView.findViewById(R.id.text_last_message_time)
        val textUnreadCount: TextView = itemView.findViewById(R.id.text_unread_count)
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
            val otherUsername = if (conversation.participant1Id == currentUserId) {
                conversation.participant2Username
            } else {
                conversation.participant1Username
            }
            
            holder.textOtherUsername.text = otherUsername
            holder.textLastMessage.text = conversation.lastMessageText ?: "No messages yet"
            
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
            
            // Set click listener
            holder.itemView.setOnClickListener {
                onConversationClick(conversation)
            }
            
        } catch (e: Exception) {
            android.util.Log.e("ConversationAdapter", "Error binding view", e)
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
