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
import com.example.ecosort.data.model.Admin
import java.text.SimpleDateFormat
import java.util.*

class AdminManagementAdapter(
    private val listener: OnAdminActionListener
) : ListAdapter<Admin, AdminManagementAdapter.AdminViewHolder>(AdminDiffCallback()) {

    interface OnAdminActionListener {
        fun onDeleteAdmin(admin: Admin)
        fun onViewAdminDetails(admin: Admin)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_management, parent, false)
        return AdminViewHolder(view, listener)
    }

    override fun onBindViewHolder(holder: AdminViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class AdminViewHolder(
        itemView: View,
        private val listener: OnAdminActionListener
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvUsername: TextView = itemView.findViewById(R.id.tvAdminUsername)
        private val tvEmail: TextView = itemView.findViewById(R.id.tvAdminEmail)
        private val tvPermissions: TextView = itemView.findViewById(R.id.tvAdminPermissions)
        private val tvCreatedAt: TextView = itemView.findViewById(R.id.tvAdminCreatedAt)
        private val tvLastLogin: TextView = itemView.findViewById(R.id.tvAdminLastLogin)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvAdminStatus)
        private val btnViewDetails: Button = itemView.findViewById(R.id.btnAdminViewDetails)
        private val btnDelete: Button = itemView.findViewById(R.id.btnAdminDelete)

        fun bind(admin: Admin) {
            tvUsername.text = admin.username
            tvEmail.text = admin.email
            tvPermissions.text = admin.permissions
            
            // Format dates
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvCreatedAt.text = "Created: ${dateFormat.format(Date(admin.createdAt))}"
            tvLastLogin.text = "Last Login: ${dateFormat.format(Date(admin.lastLogin))}"
            
            // Status
            tvStatus.text = if (admin.isActive) "Active" else "Inactive"
            tvStatus.setTextColor(if (admin.isActive) {
                itemView.context.getColor(android.R.color.holo_green_dark)
            } else {
                itemView.context.getColor(android.R.color.holo_red_dark)
            })

            // Set button listeners
            btnViewDetails.setOnClickListener {
                listener.onViewAdminDetails(admin)
            }

            btnDelete.setOnClickListener {
                listener.onDeleteAdmin(admin)
            }
        }
    }

    private class AdminDiffCallback : DiffUtil.ItemCallback<Admin>() {
        override fun areItemsTheSame(oldItem: Admin, newItem: Admin): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Admin, newItem: Admin): Boolean {
            return oldItem == newItem
        }
    }
}
