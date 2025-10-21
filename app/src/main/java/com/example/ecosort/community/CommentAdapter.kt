package com.example.ecosort.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.CommunityComment
import java.text.SimpleDateFormat
import java.util.*

class CommentAdapter(
    private val onDeleteClick: (CommunityComment) -> Unit
) : ListAdapter<CommunityComment, CommentAdapter.CommentViewHolder>(CommentDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_comment, parent, false)
        return CommentViewHolder(view)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class CommentViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val authorName: TextView = itemView.findViewById(R.id.textCommentAuthor)
        private val commentTime: TextView = itemView.findViewById(R.id.textCommentTime)
        private val commentContent: TextView = itemView.findViewById(R.id.textCommentContent)
        private val deleteButton: ImageButton = itemView.findViewById(R.id.buttonDeleteComment)

        fun bind(comment: CommunityComment) {
            authorName.text = comment.authorName
            commentTime.text = formatTime(comment.postedAt)
            commentContent.text = comment.content

            // Show delete button only for comments by current user (for now, show for all)
            deleteButton.visibility = View.VISIBLE
            deleteButton.setOnClickListener {
                onDeleteClick(comment)
            }
        }

        private fun formatTime(timestamp: Long): String {
            val date = Date(timestamp)
            val format = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            return format.format(date)
        }
    }

    class CommentDiffCallback : DiffUtil.ItemCallback<CommunityComment>() {
        override fun areItemsTheSame(oldItem: CommunityComment, newItem: CommunityComment): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CommunityComment, newItem: CommunityComment): Boolean {
            return oldItem == newItem
        }
    }
}
