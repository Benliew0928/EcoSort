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
import com.example.ecosort.data.model.CommunityComment
import java.text.SimpleDateFormat
import java.util.*

class CommunityCommentsManagementAdapter(
    private val listener: OnCommentActionListener
) : ListAdapter<CommunityComment, CommunityCommentsManagementAdapter.CommentViewHolder>(CommentDiffCallback()) {

    interface OnCommentActionListener {
        fun onDeleteComment(comment: CommunityComment)
        fun onViewCommentDetails(comment: CommunityComment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_community_comment_management, parent, false)
        return CommentViewHolder(view, listener)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommentViewHolder(
        itemView: View,
        private val listener: OnCommentActionListener
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvAuthor: TextView = itemView.findViewById(R.id.tvCommentAuthor)
        private val tvContent: TextView = itemView.findViewById(R.id.tvCommentContent)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvCommentTimestamp)
        private val tvLikes: TextView = itemView.findViewById(R.id.tvCommentLikes)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDeleteComment)
        private val btnViewDetails: Button = itemView.findViewById(R.id.btnViewCommentDetails)

        fun bind(comment: CommunityComment) {
            tvAuthor.text = "By: ${comment.authorName}"
            
            // Truncate content if too long
            val content = if (comment.content.length > 150) {
                "${comment.content.substring(0, 150)}..."
            } else {
                comment.content
            }
            tvContent.text = content

            // Format timestamp
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvTimestamp.text = dateFormat.format(Date(comment.postedAt))

            tvLikes.text = "❤️ 0" // CommunityComment doesn't have likesCount field

            // Set button listeners
            btnDelete.setOnClickListener {
                listener.onDeleteComment(comment)
            }

            btnViewDetails.setOnClickListener {
                listener.onViewCommentDetails(comment)
            }
        }
    }

    private class CommentDiffCallback : DiffUtil.ItemCallback<CommunityComment>() {
        override fun areItemsTheSame(oldItem: CommunityComment, newItem: CommunityComment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CommunityComment, newItem: CommunityComment): Boolean {
            return oldItem == newItem
        }
    }
}
