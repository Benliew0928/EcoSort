package com.example.ecosort.ui.admin

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserType
import java.text.SimpleDateFormat
import java.util.*

class UserManagementAdapter(
    private val listener: OnUserActionListener
) : ListAdapter<User, UserManagementAdapter.UserViewHolder>(UserDiffCallback()) {

    interface OnUserActionListener {
        fun onDeleteUser(user: User)
        fun onViewUserDetails(user: User)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user_management, parent, false)
        return UserViewHolder(view, listener)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class UserViewHolder(
        itemView: View,
        private val listener: OnUserActionListener
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvUsername: TextView = itemView.findViewById(R.id.tvUsername)
        private val tvEmail: TextView = itemView.findViewById(R.id.tvEmail)
        private val tvUserType: TextView = itemView.findViewById(R.id.tvUserType)
        private val tvCreatedAt: TextView = itemView.findViewById(R.id.tvCreatedAt)
        private val tvLastActive: TextView = itemView.findViewById(R.id.tvLastActive)
        private val btnViewDetails: Button = itemView.findViewById(R.id.btnViewDetails)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDelete)

        fun bind(user: User) {
            tvUsername.text = user.username
            tvEmail.text = user.email
            tvUserType.text = when (user.userType) {
                UserType.USER -> "Regular User"
                UserType.ADMIN -> "Admin"
            }
            
            // Format dates
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvCreatedAt.text = "Created: ${dateFormat.format(Date(user.createdAt))}"
            tvLastActive.text = "Last Active: ${dateFormat.format(Date(user.lastActive))}"

            // Set button listeners
            btnViewDetails.setOnClickListener {
                listener.onViewUserDetails(user)
            }

            btnDelete.setOnClickListener {
                listener.onDeleteUser(user)
            }
        }
    }

    private class UserDiffCallback : DiffUtil.ItemCallback<User>() {
        override fun areItemsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: User, newItem: User): Boolean {
            return oldItem == newItem
        }
    }
}
