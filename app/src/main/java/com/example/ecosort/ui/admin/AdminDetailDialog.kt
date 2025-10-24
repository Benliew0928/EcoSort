package com.example.ecosort.ui.admin

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.ecosort.R
import com.example.ecosort.data.model.Admin
import java.text.SimpleDateFormat
import java.util.*

class AdminDetailDialog : DialogFragment() {

    companion object {
        private const val ARG_ADMIN = "admin"

        fun newInstance(admin: Admin): AdminDetailDialog {
            val dialog = AdminDetailDialog()
            val args = Bundle()
            args.putSerializable(ARG_ADMIN, admin as java.io.Serializable)
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val admin = arguments?.getSerializable(ARG_ADMIN) as? Admin ?: return super.onCreateDialog(savedInstanceState)

        val builder = AlertDialog.Builder(requireActivity())
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_admin_detail, null)

        setupAdminDetails(view, admin)

        builder.setView(view)
            .setTitle("Admin Details")
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }

    private fun setupAdminDetails(view: View, admin: Admin) {
        val tvUsername: TextView = view.findViewById(R.id.tvDetailAdminUsername)
        val tvEmail: TextView = view.findViewById(R.id.tvDetailAdminEmail)
        val tvPermissions: TextView = view.findViewById(R.id.tvDetailAdminPermissions)
        val tvCreatedAt: TextView = view.findViewById(R.id.tvDetailAdminCreatedAt)
        val tvLastLogin: TextView = view.findViewById(R.id.tvDetailAdminLastLogin)
        val tvStatus: TextView = view.findViewById(R.id.tvDetailAdminStatus)

        tvUsername.text = admin.username
        tvEmail.text = admin.email
        tvPermissions.text = admin.permissions

        // Format dates
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        tvCreatedAt.text = dateFormat.format(Date(admin.createdAt))
        tvLastLogin.text = dateFormat.format(Date(admin.lastLogin))

        // Status
        tvStatus.text = if (admin.isActive) "Active" else "Inactive"
        tvStatus.setTextColor(if (admin.isActive) {
            requireContext().getColor(android.R.color.holo_green_dark)
        } else {
            requireContext().getColor(android.R.color.holo_red_dark)
        })
    }
}
