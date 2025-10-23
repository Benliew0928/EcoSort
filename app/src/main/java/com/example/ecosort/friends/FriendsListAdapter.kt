package com.example.ecosort.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.databinding.ItemFriendBinding
import java.text.SimpleDateFormat
import java.util.*

class FriendsListAdapter(
    private val onFriendClick: (User) -> Unit,
    private val onRemoveFriendClick: (User) -> Unit,
    private val onBlockUserClick: (User) -> Unit
) : ListAdapter<User, FriendsListAdapter.FriendViewHolder>(FriendDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendViewHolder {
        val binding = ItemFriendBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FriendViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendViewHolder(
        private val binding: ItemFriendBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(user: User) {
            // Set user info
            binding.textViewUsername.text = user.username
            binding.textViewUserType.text = user.userType.name
            binding.textViewLocation.text = user.location ?: "Location not set"
            binding.textViewBio.text = user.bio ?: "No bio available"

            // Set last active time
            val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
            binding.textViewLastActive.text = "Last active: ${dateFormat.format(Date(user.lastActive))}"

            // Load profile image
            if (!user.profileImageUrl.isNullOrEmpty()) {
                Glide.with(binding.root.context)
                    .load(user.profileImageUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .into(binding.imageViewProfile)
            } else {
                binding.imageViewProfile.setImageResource(R.drawable.ic_user_placeholder)
            }

            // Set click listeners
            binding.root.setOnClickListener {
                onFriendClick(user)
            }

            binding.buttonRemove.setOnClickListener {
                onRemoveFriendClick(user)
            }

            binding.buttonBlock.setOnClickListener {
                onBlockUserClick(user)
            }
        }
    }

    class FriendDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}
