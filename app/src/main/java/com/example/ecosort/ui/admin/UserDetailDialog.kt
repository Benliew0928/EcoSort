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
        val user = arguments?.getSerializable(ARG_USER) as? User ?: return super.onCreateDialog(savedInstanceState)

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
    }

    private fun setupUserDetails(view: View, user: User) {
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

        // Format dates
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        tvCreatedAt.text = dateFormat.format(Date(user.createdAt))
        tvLastActive.text = dateFormat.format(Date(user.lastActive))

        // Handle optional fields
        tvBio.text = user.bio ?: "No bio available"
        tvProfileImageUrl.text = user.profileImageUrl ?: "No profile image"
    }
}
