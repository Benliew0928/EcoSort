package com.example.ecosort.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.data.repository.FriendRepository
import com.example.ecosort.databinding.ItemFriendSearchBinding

class FriendSearchAdapter(
    private val onAddFriendClick: (User) -> Unit,
    private val onCancelRequestClick: (User) -> Unit
) : ListAdapter<FriendRepository.UserWithFriendStatus, FriendSearchAdapter.FriendSearchViewHolder>(FriendSearchDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendSearchViewHolder {
        val binding = ItemFriendSearchBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FriendSearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendSearchViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendSearchViewHolder(
        private val binding: ItemFriendSearchBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(userWithStatus: FriendRepository.UserWithFriendStatus) {
            val user = userWithStatus.user

            // Set user info
            binding.textViewUsername.text = user.username
            binding.textViewUserType.text = user.userType.name
            binding.textViewLocation.text = user.location ?: "Location not set"
            binding.textViewBio.text = user.bio ?: "No bio available"

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

            // Set button state based on friend status
            when {
                userWithStatus.isFriend -> {
                    binding.buttonAction.text = "Friends"
                    binding.buttonAction.isEnabled = false
                    binding.buttonAction.setBackgroundResource(R.drawable.button_secondary)
                }
                userWithStatus.hasPendingRequest -> {
                    binding.buttonAction.text = "Request Sent"
                    binding.buttonAction.isEnabled = true
                    binding.buttonAction.setBackgroundResource(R.drawable.button_secondary)
                    binding.buttonAction.setOnClickListener {
                        onCancelRequestClick(user)
                    }
                }
                userWithStatus.hasReceivedRequest -> {
                    binding.buttonAction.text = "Respond to Request"
                    binding.buttonAction.isEnabled = true
                    binding.buttonAction.setBackgroundResource(R.drawable.button_accent)
                    binding.buttonAction.setOnClickListener {
                        // TODO: Navigate to friend requests screen
                    }
                }
                else -> {
                    binding.buttonAction.text = "Add Friend"
                    binding.buttonAction.isEnabled = true
                    binding.buttonAction.setBackgroundResource(R.drawable.button_primary)
                    binding.buttonAction.setOnClickListener {
                        onAddFriendClick(user)
                    }
                }
            }
        }
    }

    class FriendSearchDiffCallback : DiffUtil.ItemCallback<FriendRepository.UserWithFriendStatus>() {
        override fun areItemsTheSame(
            oldItem: FriendRepository.UserWithFriendStatus,
            newItem: FriendRepository.UserWithFriendStatus
        ): Boolean {
            return oldItem.user.id == newItem.user.id
        }

        override fun areContentsTheSame(
            oldItem: FriendRepository.UserWithFriendStatus,
            newItem: FriendRepository.UserWithFriendStatus
        ): Boolean {
            return oldItem == newItem
        }
    }
}
