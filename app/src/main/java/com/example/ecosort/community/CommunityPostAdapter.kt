package com.example.ecosort.community

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.CommunityPost
import com.example.ecosort.data.model.PostType
import com.example.ecosort.utils.VideoThumbnailGenerator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class CommunityPostAdapter(
    private val onLikeClick: (CommunityPost) -> Unit,
    private val onCommentClick: (CommunityPost) -> Unit,
    private val onShareClick: (CommunityPost) -> Unit,
    private val onPostClick: (CommunityPost) -> Unit,
    private val onTagClick: (String) -> Unit = {},
    private val onVideoClick: (String) -> Unit = {}
) : ListAdapter<CommunityPost, CommunityPostAdapter.PostViewHolder>(PostDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PostViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_community_post, parent, false)
        return PostViewHolder(view)
    }

    override fun onBindViewHolder(holder: PostViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val authorName: TextView = itemView.findViewById(R.id.textAuthorName)
        private val postTime: TextView = itemView.findViewById(R.id.textPostTime)
        private val postTitle: TextView = itemView.findViewById(R.id.textPostTitle)
        private val postContent: TextView = itemView.findViewById(R.id.textPostContent)
        private val postImage: ImageView = itemView.findViewById(R.id.imagePost)
        private val postType: TextView = itemView.findViewById(R.id.textPostType)
        private val likeButton: ImageView = itemView.findViewById(R.id.buttonLike)
        private val likeCount: TextView = itemView.findViewById(R.id.textLikeCount)
        private val commentButton: ImageView = itemView.findViewById(R.id.buttonComment)
        private val commentCount: TextView = itemView.findViewById(R.id.textCommentCount)
        private val shareButton: ImageView = itemView.findViewById(R.id.buttonShare)
        private val tagsContainer: ViewGroup = itemView.findViewById(R.id.containerTags)
        
        init {
            // Ensure UI elements are visible by default
            likeButton.visibility = View.VISIBLE
            likeCount.visibility = View.VISIBLE
            commentButton.visibility = View.VISIBLE
            commentCount.visibility = View.VISIBLE
            shareButton.visibility = View.VISIBLE
        }

        fun bind(post: CommunityPost) {
            authorName.text = post.authorName
            postTime.text = formatTime(post.postedAt)
            postTitle.text = post.title
            
            // Smart content display with preview
            setupContentPreview(post.content)
            
            // Display post type with better formatting
            val displayText = when (post.postType) {
                com.example.ecosort.data.model.PostType.TIP -> "TIP"
                com.example.ecosort.data.model.PostType.ACHIEVEMENT -> "ACHIEVE"
                com.example.ecosort.data.model.PostType.QUESTION -> "Q&A"
                com.example.ecosort.data.model.PostType.EVENT -> "EVENT"
            }
            android.util.Log.d("CommunityPostAdapter", "Post ${post.id}: ${post.postType} -> $displayText")
            postType.text = displayText
            postType.setTextColor(itemView.context.getColor(R.color.white))

            // Smart media display - show images or videos
            setupMediaDisplay(post.imageUrls, post.videoUrl, post.inputType, post)

            // Set like button state
            likeButton.setImageResource(
                if (post.isLikedByUser) R.drawable.ic_favorite_filled else R.drawable.ic_favorite_border
            )
            likeCount.text = post.likesCount.toString()
            commentCount.text = post.commentsCount.toString()
            
            // Ensure UI elements are visible
            likeButton.visibility = View.VISIBLE
            likeCount.visibility = View.VISIBLE
            commentButton.visibility = View.VISIBLE
            commentCount.visibility = View.VISIBLE
            shareButton.visibility = View.VISIBLE

            // Set click listeners
            likeButton.setOnClickListener { 
                android.util.Log.d("CommunityPostAdapter", "Like button clicked for post: ${post.id}")
                onLikeClick(post) 
            }
            commentButton.setOnClickListener { 
                android.util.Log.d("CommunityPostAdapter", "Comment button clicked for post: ${post.id}")
                onCommentClick(post) 
            }
            shareButton.setOnClickListener { 
                android.util.Log.d("CommunityPostAdapter", "Share button clicked for post: ${post.id}")
                onShareClick(post) 
            }
            
            // Make entire post card clickable (but not the image area for videos)
            itemView.setOnClickListener { 
                android.util.Log.d("CommunityPostAdapter", "Post card clicked for post: ${post.id}")
                onPostClick(post) 
            }

            // Set tags
            setupTags(post.tags)
        }

        private fun setupContentPreview(content: String) {
            if (content.length > 150) {
                // Show truncated content with "Read more" indicator
                val truncatedContent = content.substring(0, 147) + "..."
                postContent.text = truncatedContent
                postContent.maxLines = 3
            } else {
                // Show full content
                postContent.text = content
                postContent.maxLines = Integer.MAX_VALUE
            }
        }

        private fun setupMediaDisplay(imageUrls: List<String>, videoUrl: String?, inputType: com.example.ecosort.data.model.InputType, post: CommunityPost) {
            when (inputType) {
                com.example.ecosort.data.model.InputType.IMAGE -> {
                    // Show image if there's a real image (not demo or empty)
                    val hasRealImage = imageUrls.isNotEmpty() && 
                                      imageUrls.first() != "demo_black_image" && 
                                      imageUrls.first().isNotEmpty()
                    
                    if (hasRealImage) {
                        postImage.visibility = View.VISIBLE
                        val imageUrl = imageUrls.first()
                        try {
                            when {
                                imageUrl.startsWith("content://") || imageUrl.startsWith("file://") -> {
                                    // Parse as URI
                                    val uri = android.net.Uri.parse(imageUrl)
                                    try {
                                        Glide.with(itemView.context)
                                            .load(uri)
                                            .placeholder(R.drawable.ic_image_placeholder)
                                            .error(R.drawable.ic_image_placeholder)
                                            .into(postImage)
                                    } catch (e: Exception) {
                                        android.util.Log.e("CommunityPostAdapter", "Error loading URI: $imageUrl", e)
                                        postImage.visibility = View.GONE
                                    }
                                }
                                else -> {
                                    // Load as string URL (Firebase Storage URL)
                                    Glide.with(itemView.context)
                                        .load(imageUrl)
                                        .placeholder(R.drawable.ic_image_placeholder)
                                        .error(R.drawable.ic_image_placeholder)
                                        .into(postImage)
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("CommunityPostAdapter", "Error loading image: $imageUrl", e)
                            postImage.visibility = View.GONE
                        }
                    } else {
                        postImage.visibility = View.GONE
                    }
                }
                com.example.ecosort.data.model.InputType.VIDEO -> {
                    // Show video thumbnail if there's a video URL
                    if (!videoUrl.isNullOrEmpty()) {
                        postImage.visibility = View.VISIBLE
                        
                        // Make video clickable to open post detail (not video player)
                        postImage.setOnClickListener { _ ->
                            android.util.Log.d("CommunityPostAdapter", "Video thumbnail clicked for post: ${post.id}")
                            onPostClick(post)
                        }
                        
                        // Generate and load video thumbnail
                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                val thumbnailUri = VideoThumbnailGenerator.generateThumbnailFromUrl(
                                    itemView.context, 
                                    videoUrl
                                )
                                
                                if (thumbnailUri != null) {
                                    Glide.with(itemView.context)
                                        .load(thumbnailUri)
                                        .placeholder(R.drawable.ic_video)
                                        .error(R.drawable.ic_video)
                                        .into(postImage)
                                } else {
                                    // Fallback to video icon
                                    Glide.with(itemView.context)
                                        .load(R.drawable.ic_video)
                                        .placeholder(R.drawable.ic_video)
                                        .error(R.drawable.ic_video)
                                        .into(postImage)
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("CommunityPostAdapter", "Error generating video thumbnail", e)
                                // Fallback to video icon
                                Glide.with(itemView.context)
                                    .load(R.drawable.ic_video)
                                    .placeholder(R.drawable.ic_video)
                                    .error(R.drawable.ic_video)
                                    .into(postImage)
                            }
                        }
                    } else {
                        postImage.visibility = View.GONE
                    }
                }
                else -> {
                    // Hide media container for text-only posts
                    postImage.visibility = View.GONE
                }
            }
        }

        private fun setupTags(tags: List<String>) {
            tagsContainer.removeAllViews()
            if (tags.isNotEmpty()) {
                tagsContainer.visibility = View.VISIBLE
                tags.take(3).forEach { tag ->
                    val tagView = LayoutInflater.from(itemView.context)
                        .inflate(R.layout.item_tag, tagsContainer, false)
                    val tagText = tagView.findViewById<TextView>(R.id.textTag)
                    tagText.text = "#$tag"
                    
                    // Make tag clickable
                    tagView.setOnClickListener {
                        onTagClick(tag)
                    }
                    
                    tagsContainer.addView(tagView)
                }
            } else {
                tagsContainer.visibility = View.GONE
            }
        }

        private fun formatTime(timestamp: Long): String {
            val now = System.currentTimeMillis()
            val diff = now - timestamp

            return when {
                diff < 60_000 -> "Just now"
                diff < 3600_000 -> "${diff / 60_000}m ago"
                diff < 86400_000 -> "${diff / 3600_000}h ago"
                else -> {
                    val date = Date(timestamp)
                    SimpleDateFormat("MMM dd", Locale.getDefault()).format(date)
                }
            }
        }
    }

    class PostDiffCallback : DiffUtil.ItemCallback<CommunityPost>() {
        override fun areItemsTheSame(oldItem: CommunityPost, newItem: CommunityPost): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: CommunityPost, newItem: CommunityPost): Boolean {
            return oldItem == newItem
        }
    }
}
