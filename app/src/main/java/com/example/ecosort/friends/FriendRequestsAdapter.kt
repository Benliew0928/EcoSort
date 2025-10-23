package com.example.ecosort.friends

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.FriendRequest
import com.example.ecosort.databinding.ItemFriendRequestBinding
import java.text.SimpleDateFormat
import java.util.*

class FriendRequestsAdapter(
    private val onAcceptClick: (FriendRequest) -> Unit,
    private val onDeclineClick: (FriendRequest) -> Unit
) : ListAdapter<FriendRequestsActivity.FriendRequestWithUser, FriendRequestsAdapter.FriendRequestViewHolder>(FriendRequestDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FriendRequestViewHolder {
        val binding = ItemFriendRequestBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FriendRequestViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FriendRequestViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FriendRequestViewHolder(
        private val binding: ItemFriendRequestBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(requestWithUser: FriendRequestsActivity.FriendRequestWithUser) {
            val request = requestWithUser.request
            val sender = requestWithUser.sender

            if (sender != null) {
                // Set user info
                binding.textViewUsername.text = sender.username
                binding.textViewUserType.text = sender.userType.name
                binding.textViewLocation.text = sender.location ?: "Location not set"

                // Load profile image
                if (!sender.profileImageUrl.isNullOrEmpty()) {
                    Glide.with(binding.root.context)
                        .load(sender.profileImageUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .into(binding.imageViewProfile)
                } else {
                    binding.imageViewProfile.setImageResource(R.drawable.ic_user_placeholder)
                }
            } else {
                binding.textViewUsername.text = "Unknown User"
                binding.textViewUserType.text = ""
                binding.textViewLocation.text = ""
                binding.imageViewProfile.setImageResource(R.drawable.ic_user_placeholder)
            }

            // Set request message
            if (!request.message.isNullOrEmpty()) {
                binding.textViewMessage.text = request.message
                binding.textViewMessage.visibility = android.view.View.VISIBLE
            } else {
                binding.textViewMessage.visibility = android.view.View.GONE
            }

            // Set request time
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            binding.textViewTime.text = dateFormat.format(Date(request.createdAt))

            // Set button click listeners
            binding.buttonAccept.setOnClickListener {
                onAcceptClick(request)
            }

            binding.buttonDecline.setOnClickListener {
                onDeclineClick(request)
            }
        }
    }

    class FriendRequestDiffCallback : DiffUtil.ItemCallback<FriendRequestsActivity.FriendRequestWithUser>() {
        override fun areItemsTheSame(
            oldItem: FriendRequestsActivity.FriendRequestWithUser,
            newItem: FriendRequestsActivity.FriendRequestWithUser
        ): Boolean {
            return oldItem.request.id == newItem.request.id
        }

        override fun areContentsTheSame(
            oldItem: FriendRequestsActivity.FriendRequestWithUser,
            newItem: FriendRequestsActivity.FriendRequestWithUser
        ): Boolean {
            return oldItem == newItem
        }
    }
}
