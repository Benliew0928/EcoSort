package com.example.ecosort.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.databinding.ItemChatBinding

/**
 * Chat List Adapter - Temporarily disabled due to Stream Chat dependency issues
 */
class ChatListAdapter(
    private val onChatClick: (Any) -> Unit
) : ListAdapter<Any, ChatListAdapter.ChatViewHolder>(ChatDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val binding = ItemChatBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ChatViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ChatViewHolder(
        private val binding: ItemChatBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: Any) {
            binding.apply {
                textViewChatName.text = "Chat feature coming soon"
                textViewLastMessage.text = "Stream Chat integration in progress"
                textViewLastMessageTime.text = ""
                textViewUnreadCount.visibility = android.view.View.GONE
                
                root.setOnClickListener {
                    onChatClick(item)
                }
            }
        }
    }

    class ChatDiffCallback : DiffUtil.ItemCallback<Any>() {
        override fun areItemsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Any, newItem: Any): Boolean {
            return oldItem == newItem
        }
    }
}