package com.example.ecosort.ui.admin

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserType
import java.text.SimpleDateFormat
import java.util.*

class UserDetailDialog : DialogFragment() {

    companion object {
        private const val ARG_USER = "user"

        fun newInstance(user: User): UserDetailDialog {
            val dialog = UserDetailDialog()
            val args = Bundle()
            args.putSerializable(ARG_USER, user as java.io.Serializable)
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        try {
            val user = arguments?.getSerializable(ARG_USER) as? User
            if (user == null) {
                android.util.Log.e("UserDetailDialog", "User data is null")
                return AlertDialog.Builder(requireActivity())
                    .setTitle("Error")
                    .setMessage("Unable to load user details")
                    .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                    .create()
            }

            val builder = AlertDialog.Builder(requireActivity())
            val inflater = LayoutInflater.from(context)
            val view = inflater.inflate(R.layout.dialog_user_detail, null)

            setupUserDetails(view, user)

            builder.setView(view)
                .setTitle("User Details")
                .setPositiveButton("Close") { dialog, _ ->
                    dialog.dismiss()
                }

            return builder.create()
        } catch (e: Exception) {
            android.util.Log.e("UserDetailDialog", "Error creating dialog", e)
            return AlertDialog.Builder(requireActivity())
                .setTitle("Error")
                .setMessage("Failed to load user details: ${e.message}")
                .setPositiveButton("Close") { dialog, _ -> dialog.dismiss() }
                .create()
        }
    }

    private fun setupUserDetails(view: View, user: User) {
        try {
            val tvUsername: TextView = view.findViewById(R.id.tvDetailUsername)
            val tvEmail: TextView = view.findViewById(R.id.tvDetailEmail)
            val tvUserType: TextView = view.findViewById(R.id.tvDetailUserType)
            val tvCreatedAt: TextView = view.findViewById(R.id.tvDetailCreatedAt)
            val tvLastActive: TextView = view.findViewById(R.id.tvDetailLastActive)
            val tvBio: TextView = view.findViewById(R.id.tvDetailBio)
            val tvProfileImageUrl: TextView = view.findViewById(R.id.tvDetailProfileImageUrl)

            tvUsername.text = user.username
            tvEmail.text = user.email
            tvUserType.text = when (user.userType) {
                UserType.USER -> "Regular User"
                UserType.ADMIN -> "Admin"
            }

            // Format dates safely
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
            try {
                tvCreatedAt.text = dateFormat.format(Date(user.createdAt))
            } catch (e: Exception) {
                tvCreatedAt.text = "Invalid date"
            }
            
            try {
                tvLastActive.text = dateFormat.format(Date(user.lastActive))
            } catch (e: Exception) {
                tvLastActive.text = "Invalid date"
            }

            // Handle optional fields
            tvBio.text = user.bio?.takeIf { it.isNotBlank() } ?: "No bio available"
            tvProfileImageUrl.text = user.profileImageUrl?.takeIf { it.isNotBlank() } ?: "No profile image"
        } catch (e: Exception) {
            android.util.Log.e("UserDetailDialog", "Error setting up user details", e)
        }
    }
}
