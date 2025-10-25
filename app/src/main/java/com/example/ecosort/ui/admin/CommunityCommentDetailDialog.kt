package com.example.ecosort.ui.admin

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.example.ecosort.R
import com.example.ecosort.data.model.CommunityComment
import java.text.SimpleDateFormat
import java.util.*

class CommunityCommentDetailDialog : DialogFragment() {

    companion object {
        private const val ARG_COMMENT = "comment"

        fun newInstance(comment: CommunityComment): CommunityCommentDetailDialog {
            val dialog = CommunityCommentDetailDialog()
            val args = Bundle()
            args.putSerializable(ARG_COMMENT, comment as java.io.Serializable)
            dialog.arguments = args
            return dialog
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val comment = arguments?.getSerializable(ARG_COMMENT) as? CommunityComment ?: return super.onCreateDialog(savedInstanceState)

        val builder = AlertDialog.Builder(requireActivity())
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_community_comment_detail, null)

        setupCommentDetails(view, comment)

        builder.setView(view)
            .setTitle("Comment Details")
            .setPositiveButton("Close") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }

    private fun setupCommentDetails(view: View, comment: CommunityComment) {
        val tvCommentId: TextView = view.findViewById(R.id.tvDetailCommentId)
        val tvCommentAuthor: TextView = view.findViewById(R.id.tvDetailCommentAuthor)
        val tvCommentContent: TextView = view.findViewById(R.id.tvDetailCommentContent)
        val tvCommentTimestamp: TextView = view.findViewById(R.id.tvDetailCommentTimestamp)
        val tvCommentLikes: TextView = view.findViewById(R.id.tvDetailCommentLikes)
        val tvPostId: TextView = view.findViewById(R.id.tvDetailPostId)

        tvCommentId.text = comment.id.toString()
        tvCommentAuthor.text = comment.authorName
        tvCommentContent.text = comment.content
        tvPostId.text = comment.postId.toString()

        // Format timestamp
        val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm:ss", Locale.getDefault())
        tvCommentTimestamp.text = dateFormat.format(Date(comment.postedAt))

        tvCommentLikes.text = "0" // CommunityComment doesn't have likesCount field
    }
}
