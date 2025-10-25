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
import com.example.ecosort.data.model.AdminAction
import java.text.SimpleDateFormat
import java.util.*

class AdminActionLogsAdapter(
    private val listener: OnActionLogListener
) : ListAdapter<AdminAction, AdminActionLogsAdapter.ActionLogViewHolder>(ActionLogDiffCallback()) {

    interface OnActionLogListener {
        fun onViewActionDetails(action: AdminAction)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ActionLogViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_action_log, parent, false)
        return ActionLogViewHolder(view, listener)
    }

    override fun onBindViewHolder(holder: ActionLogViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ActionLogViewHolder(
        itemView: View,
        private val listener: OnActionLogListener
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvAction: TextView = itemView.findViewById(R.id.tvActionType)
        private val tvAdminId: TextView = itemView.findViewById(R.id.tvAdminId)
        private val tvTargetId: TextView = itemView.findViewById(R.id.tvTargetId)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvDetails: TextView = itemView.findViewById(R.id.tvActionDetails)
        private val btnViewDetails: Button = itemView.findViewById(R.id.btnViewActionDetails)

        fun bind(action: AdminAction) {
            // Set action type with appropriate emoji
            val actionText = when (action.action) {
                "DELETE_USER" -> "🗑️ Delete User"
                "DELETE_ADMIN" -> "🗑️ Delete Admin"
                "SUSPEND_USER" -> "⏸️ Suspend User"
                "UNSUSPEND_USER" -> "▶️ Unsuspend User"
                "CHANGE_PASSKEY" -> "🔐 Change Passkey"
                "CREATE_ADMIN" -> "👨‍💼 Create Admin"
                "DELETE_COMMUNITY_POST" -> "🗑️ Delete Community Post"
                "DELETE_COMMUNITY_COMMENT" -> "🗑️ Delete Community Comment"
                else -> "📝 ${action.action}"
            }
            tvAction.text = actionText

            // Set admin ID
            tvAdminId.text = "Admin ID: ${action.adminId}"

            // Set target ID if available
            tvTargetId.text = if (action.targetUserId != null) {
                "Target ID: ${action.targetUserId}"
            } else {
                "Target: N/A"
            }

            // Format timestamp
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            tvTimestamp.text = dateFormat.format(Date(action.timestamp))

            // Set details
            tvDetails.text = action.details ?: "No additional details"

            // Set button listener
            btnViewDetails.setOnClickListener {
                listener.onViewActionDetails(action)
            }
        }
    }

    private class ActionLogDiffCallback : DiffUtil.ItemCallback<AdminAction>() {
        override fun areItemsTheSame(oldItem: AdminAction, newItem: AdminAction): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: AdminAction, newItem: AdminAction): Boolean {
            return oldItem == newItem
        }
    }
}
