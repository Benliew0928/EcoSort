package com.example.ecosort.ui.admin

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.ecosort.R
import com.example.ecosort.data.model.CommunityPost
import java.text.SimpleDateFormat
import java.util.*

class CommunityPostDetailDialog : DialogFragment() {

    companion object {
        private const val ARG_POST = "post"

        fun newInstance(post: CommunityPost): CommunityPostDetailDialog {
            val dialog = CommunityPostDetailDialog()
            val args = Bundle()
            args.putSerializable(ARG_POST, post as java.io.Serializable)
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val post = arguments?.getSerializable(ARG_POST) as? CommunityPost ?: return super.onCreateDialog(savedInstanceState)

        val builder = AlertDialog.Builder(requireActivity())
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_community_post_detail, null)

        setupPostDetails(view, post)

        builder.setView(view)
            .setTitle("Community Post Details")
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }

    private fun setupPostDetails(view: View, post: CommunityPost) {
        val tvPostId: TextView = view.findViewById(R.id.tvDetailPostId)
        val tvPostTitle: TextView = view.findViewById(R.id.tvDetailPostTitle)
        val tvPostAuthor: TextView = view.findViewById(R.id.tvDetailPostAuthor)
        val tvPostContent: TextView = view.findViewById(R.id.tvDetailPostContent)
        val tvPostTimestamp: TextView = view.findViewById(R.id.tvDetailPostTimestamp)
        val tvPostLikes: TextView = view.findViewById(R.id.tvDetailPostLikes)
        val tvPostComments: TextView = view.findViewById(R.id.tvDetailPostComments)

        tvPostId.text = post.id.toString()
        tvPostTitle.text = post.title
        tvPostAuthor.text = post.authorName
        tvPostContent.text = post.content

        // Format timestamp
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        tvPostTimestamp.text = dateFormat.format(Date(post.postedAt))

        tvPostLikes.text = post.likesCount.toString()
        tvPostComments.text = post.commentsCount.toString()
    }
}
