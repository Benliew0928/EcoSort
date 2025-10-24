package com.example.ecosort.social

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FollowingListAdapter(private val listener: OnFollowingActionListener) :
    ListAdapter<User, FollowingListAdapter.FollowingViewHolder>(FollowingDiffCallback()) {

    interface OnFollowingActionListener {
        fun onUnfollowUser(user: User)
        fun onViewUserProfile(user: User)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FollowingViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_following_list, parent, false)
        return FollowingViewHolder(view)
    }

    override fun onBindViewHolder(holder: FollowingViewHolder, position: Int) {
        val user = getItem(position)
        holder.bind(user, listener)
    }

    class FollowingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvUsername: TextView = itemView.findViewById(R.id.tvFollowingUsername)
        private val tvEmail: TextView = itemView.findViewById(R.id.tvFollowingEmail)
        private val tvBio: TextView = itemView.findViewById(R.id.tvFollowingBio)
        private val tvJoinedDate: TextView = itemView.findViewById(R.id.tvFollowingJoinedDate)
        private val btnViewProfile: Button = itemView.findViewById(R.id.btnViewFollowingProfile)
        private val btnUnfollow: Button = itemView.findViewById(R.id.btnUnfollowUser)

        fun bind(user: User, listener: OnFollowingActionListener) {
            tvUsername.text = user.username
            tvEmail.text = user.email
            tvBio.text = user.bio ?: "No bio available"
            
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            tvJoinedDate.text = "Joined: ${dateFormat.format(Date(user.createdAt))}"

            btnViewProfile.setOnClickListener { listener.onViewUserProfile(user) }
            btnUnfollow.setOnClickListener { listener.onUnfollowUser(user) }
        }
    }

    private class FollowingDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}
