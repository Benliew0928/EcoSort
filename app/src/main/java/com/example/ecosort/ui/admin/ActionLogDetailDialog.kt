package com.example.ecosort.ui.admin

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.ecosort.R
import com.example.ecosort.data.model.AdminAction
import java.text.SimpleDateFormat
import java.util.*

class ActionLogDetailDialog : DialogFragment() {

    companion object {
        private const val ARG_ACTION = "action"

        fun newInstance(action: AdminAction): ActionLogDetailDialog {
            val dialog = ActionLogDetailDialog()
            val args = Bundle()
            args.putSerializable(ARG_ACTION, action as java.io.Serializable)
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val action = arguments?.getSerializable(ARG_ACTION) as? AdminAction ?: return super.onCreateDialog(savedInstanceState)

        val builder = AlertDialog.Builder(requireActivity())
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_action_log_detail, null)

        setupActionDetails(view, action)

        builder.setView(view)
            .setTitle("Action Log Details")
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }

    private fun setupActionDetails(view: View, action: AdminAction) {
        val tvActionId: TextView = view.findViewById(R.id.tvDetailActionId)
        val tvActionType: TextView = view.findViewById(R.id.tvDetailActionType)
        val tvAdminId: TextView = view.findViewById(R.id.tvDetailAdminId)
        val tvTargetId: TextView = view.findViewById(R.id.tvDetailTargetId)
        val tvTimestamp: TextView = view.findViewById(R.id.tvDetailTimestamp)
        val tvDetails: TextView = view.findViewById(R.id.tvDetailActionDetails)

        tvActionId.text = action.id.toString()
        
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
        tvActionType.text = actionText

        tvAdminId.text = action.adminId.toString()
        tvTargetId.text = action.targetUserId?.toString() ?: "N/A"

        // Format timestamp
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        tvTimestamp.text = dateFormat.format(Date(action.timestamp))

        tvDetails.text = action.details ?: "No additional details available"
    }
}
