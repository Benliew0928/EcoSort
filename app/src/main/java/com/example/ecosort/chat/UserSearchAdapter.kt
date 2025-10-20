package com.example.ecosort.chat

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.databinding.ItemUserSearchBinding
import com.example.ecosort.data.model.User

/**
 * User Search Adapter for displaying search results
 */
class UserSearchAdapter(
    private val onUserClick: (User) -> Unit
) : ListAdapter<User, UserSearchAdapter.UserViewHolder>(UserDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val binding = ItemUserSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return UserViewHolder(binding)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class UserViewHolder(
        private val binding: ItemUserSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            binding.apply {
                textViewUserName.text = user.username
                textViewUserId.text = "${user.itemsRecycled} items recycled • ${user.userType.name}"
                imageViewUserAvatar.setImageResource(com.example.ecosort.R.drawable.ic_person)
                
                root.setOnClickListener {
                    onUserClick(user)
                }
            }
        }
    }

    class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}