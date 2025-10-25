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
import com.example.ecosort.data.model.CommunityPost
import java.text.SimpleDateFormat
import java.util.*

class CommunityManagementAdapter(
    private val listener: OnCommunityActionListener
) : ListAdapter<CommunityPost, CommunityManagementAdapter.CommunityPostViewHolder>(CommunityPostDiffCallback()) {

    interface OnCommunityActionListener {
        fun onDeletePost(post: CommunityPost)
        fun onViewPostDetails(post: CommunityPost)
        fun onManageComments(post: CommunityPost)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommunityPostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_community_management, parent, false)
        return CommunityPostViewHolder(view, listener)
    }

    override fun onBindViewHolder(holder: CommunityPostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class CommunityPostViewHolder(
        itemView: View,
        private val listener: OnCommunityActionListener
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvTitle: TextView = itemView.findViewById(R.id.tvPostTitle)
        private val tvAuthor: TextView = itemView.findViewById(R.id.tvPostAuthor)
        private val tvContent: TextView = itemView.findViewById(R.id.tvPostContent)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvPostTimestamp)
        private val tvLikes: TextView = itemView.findViewById(R.id.tvPostLikes)
        private val btnDelete: Button = itemView.findViewById(R.id.btnDeletePost)
        private val btnViewDetails: Button = itemView.findViewById(R.id.btnViewPostDetails)
        private val btnManageComments: Button = itemView.findViewById(R.id.btnManageComments)

        fun bind(post: CommunityPost) {
            tvTitle.text = post.title
            tvAuthor.text = "By: ${post.authorName}"
            
            // Truncate content if too long
            val content = if (post.content.length > 100) {
                "${post.content.substring(0, 100)}..."
            } else {
                post.content
            }
            tvContent.text = content

            // Format timestamp
            val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
            tvTimestamp.text = dateFormat.format(Date(post.postedAt))

            tvLikes.text = "❤️ ${post.likesCount}"

            // Set button listeners
            btnDelete.setOnClickListener {
                listener.onDeletePost(post)
            }

            btnViewDetails.setOnClickListener {
                listener.onViewPostDetails(post)
            }

            btnManageComments.setOnClickListener {
                listener.onManageComments(post)
            }
        }
    }

    private class CommunityPostDiffCallback : DiffUtil.ItemCallback<CommunityPost>() {
        override fun areItemsTheSame(oldItem: CommunityPost, newItem: CommunityPost): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CommunityPost, newItem: CommunityPost): Boolean {
            return oldItem == newItem
        }
    }
}
