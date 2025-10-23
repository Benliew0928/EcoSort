package com.example.ecosort.community

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.CommunityComment
import com.example.ecosort.data.model.CommunityPost
import com.example.ecosort.data.model.PostType
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.repository.CommunityRepository
import com.example.ecosort.utils.VideoThumbnailGenerator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.*

@AndroidEntryPoint
class PostDetailActivity : AppCompatActivity() {
    
    @Inject
    lateinit var communityRepository: CommunityRepository
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    private lateinit var toolbar: Toolbar
    private lateinit var btnDeletePost: ImageButton
    private lateinit var authorProfileImage: ImageView
    private lateinit var authorName: TextView
    private lateinit var postTime: TextView
    private lateinit var postTitle: TextView
    private lateinit var postContent: TextView
    private lateinit var postImage: ImageView
    private lateinit var postType: TextView
    private lateinit var likeCount: TextView
    private lateinit var commentCount: TextView
    private lateinit var recyclerViewComments: RecyclerView
    private lateinit var textViewEmpty: TextView
    private lateinit var tagsContainer: LinearLayout
    
    private lateinit var commentAdapter: CommentAdapter
    private lateinit var editTextComment: EditText
    private lateinit var buttonSendComment: ImageButton
    private var currentPost: CommunityPost? = null
    private var currentUserId: Long = 0L
    private var currentUsername: String = "User"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_post_detail)
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupClickListeners()
        loadCurrentUser()
        
        // Get post data from intent
        currentPost = intent.getSerializableExtra("post") as? CommunityPost
        if (currentPost != null) {
            displayPost(currentPost!!)
            loadComments()
        } else {
            finish()
        }
    }
    
    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Post Details"
    }
    
    private fun setupViews() {
        btnDeletePost = findViewById(R.id.btnDeletePost)
        authorProfileImage = findViewById(R.id.imageAuthorProfile)
        authorName = findViewById(R.id.textAuthorName)
        postTime = findViewById(R.id.textPostTime)
        postTitle = findViewById(R.id.textPostTitle)
        postContent = findViewById(R.id.textPostContent)
        postImage = findViewById(R.id.imagePost)
        postType = findViewById(R.id.textPostType)
        likeCount = findViewById(R.id.textLikeCount)
        commentCount = findViewById(R.id.textCommentCount)
        recyclerViewComments = findViewById(R.id.recyclerViewComments)
        textViewEmpty = findViewById(R.id.textViewEmpty)
        tagsContainer = findViewById(R.id.containerTags)
        editTextComment = findViewById(R.id.editTextComment)
        buttonSendComment = findViewById(R.id.buttonSendComment)
    }
    
    private fun setupRecyclerView() {
        commentAdapter = CommentAdapter(
            onDeleteClick = { comment -> deleteComment(comment) }
        )
        recyclerViewComments.layoutManager = LinearLayoutManager(this)
        recyclerViewComments.adapter = commentAdapter
    }
    
    private fun setupClickListeners() {
        buttonSendComment.setOnClickListener {
            sendComment()
        }
        
        btnDeletePost.setOnClickListener {
            deletePost()
        }
    }
    
    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                val userSession = userPreferencesManager.getCurrentUser()
                currentUserId = userSession?.userId ?: 1L
                currentUsername = userSession?.username ?: "Anonymous User"
                android.util.Log.d("PostDetailActivity", "Current user loaded: $currentUsername (ID: $currentUserId)")
            } catch (e: Exception) {
                android.util.Log.e("PostDetailActivity", "Error loading current user", e)
                // Use fallback values
                currentUserId = 1L
                currentUsername = "Anonymous User"
            }
        }
    }
    
    private fun displayPost(post: CommunityPost) {
        authorName.text = post.authorName
        postTime.text = formatTime(post.postedAt)
        
        // Load author profile picture
        loadAuthorProfilePicture(post.authorAvatar)
        postTitle.text = post.title
        postContent.text = post.content
        // Display post type with better formatting
        val displayText = when (post.postType) {
            com.example.ecosort.data.model.PostType.TIP -> "TIP"
            com.example.ecosort.data.model.PostType.ACHIEVEMENT -> "ACHIEVE"
            com.example.ecosort.data.model.PostType.QUESTION -> "Q&A"
            com.example.ecosort.data.model.PostType.EVENT -> "EVENT"
        }
        android.util.Log.d("PostDetailActivity", "Post ${post.id}: ${post.postType} -> $displayText")
        postType.text = displayText
        postType.setTextColor(getColor(R.color.white))
        
        // Show delete button only if current user is the author
        if (currentUserId == post.authorId) {
            btnDeletePost.visibility = View.VISIBLE
        } else {
            btnDeletePost.visibility = View.GONE
        }
        
        // Smart media display - show images or videos
        setupMediaDisplay(post.imageUrls, post.videoUrl, post.inputType)
        
        // Display tags
        setupTags(post.tags)
        
        likeCount.text = post.likesCount.toString()
        commentCount.text = post.commentsCount.toString()
    }
    
    private fun loadComments() {
        currentPost?.let { post ->
            lifecycleScope.launch {
                try {
                    repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                        communityRepository.getCommentsForPost(post.id).collectLatest { comments ->
                            commentAdapter.submitList(comments)
                            updateEmptyState(comments.isEmpty())
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PostDetailActivity", "Error loading comments", e)
                }
            }
        }
    }
    
    private fun deleteComment(comment: CommunityComment) {
        lifecycleScope.launch {
            try {
                val result = communityRepository.deleteComment(comment.id)
                when (result) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        // Comment will be removed automatically via Flow
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        android.util.Log.e("PostDetailActivity", "Error deleting comment", result.exception)
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Should not happen for suspend fun
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("PostDetailActivity", "Error deleting comment", e)
            }
        }
    }
    
    private fun sendComment() {
        val commentText = editTextComment.text.toString().trim()
        if (commentText.isEmpty()) {
            android.util.Log.w("PostDetailActivity", "Attempted to send empty comment")
            return
        }
        
        currentPost?.let { post ->
            lifecycleScope.launch {
                try {
                    android.util.Log.d("PostDetailActivity", "Sending comment for post: ${post.id}")
                    val result = communityRepository.addComment(
                        postId = post.id,
                        authorId = currentUserId,
                        authorName = currentUsername,
                        content = commentText
                    )
                    
                    when (result) {
                        is com.example.ecosort.data.model.Result.Success -> {
                            android.util.Log.d("PostDetailActivity", "Comment added successfully")
                            editTextComment.text.clear()
                            // Comments will be updated automatically via Flow
                        }
                        is com.example.ecosort.data.model.Result.Error -> {
                            android.util.Log.e("PostDetailActivity", "Error adding comment", result.exception)
                            android.widget.Toast.makeText(this@PostDetailActivity, "Error adding comment: ${result.exception.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is com.example.ecosort.data.model.Result.Loading -> {
                            android.util.Log.d("PostDetailActivity", "Comment operation in progress...")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("PostDetailActivity", "Error sending comment", e)
                    android.widget.Toast.makeText(this@PostDetailActivity, "Error sending comment: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            android.util.Log.e("PostDetailActivity", "Cannot send comment: currentPost is null")
            android.widget.Toast.makeText(this, "Error: Post not found", android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        textViewEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerViewComments.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }
    
    private fun setupMediaDisplay(imageUrls: List<String>, videoUrl: String?, inputType: com.example.ecosort.data.model.InputType) {
        when (inputType) {
            com.example.ecosort.data.model.InputType.IMAGE -> {
                // Show image if there's a real image (not demo or empty)
                val hasRealImage = imageUrls.isNotEmpty() && 
                                  imageUrls.first() != "demo_black_image" && 
                                  imageUrls.first().isNotEmpty()
                
                if (hasRealImage) {
                    postImage.visibility = View.VISIBLE
                    val imageUrl = imageUrls.first()
                    android.util.Log.d("PostDetailActivity", "Loading image: $imageUrl")
                    
                    try {
                        when {
                            imageUrl.startsWith("content://") || imageUrl.startsWith("file://") -> {
                                // Parse as URI
                                val uri = android.net.Uri.parse(imageUrl)
                                android.util.Log.d("PostDetailActivity", "Loading URI: $uri")
                                try {
                                    Glide.with(this)
                                        .load(uri)
                                        .placeholder(R.drawable.ic_image_placeholder)
                                        .error(R.drawable.ic_image_placeholder)
                                        .into(postImage)
                                    android.util.Log.d("PostDetailActivity", "Successfully loaded image from URI")
                                } catch (e: Exception) {
                                    android.util.Log.e("PostDetailActivity", "Error loading URI: $imageUrl", e)
                                    postImage.setImageResource(R.drawable.ic_image_placeholder)
                                }
                            }
                            else -> {
                                // Load as string URL (Firebase Storage URL)
                                android.util.Log.d("PostDetailActivity", "Loading Firebase Storage URL: $imageUrl")
                                Glide.with(this)
                                    .load(imageUrl)
                                    .placeholder(R.drawable.ic_image_placeholder)
                                    .error(R.drawable.ic_image_placeholder)
                                    .into(postImage)
                                android.util.Log.d("PostDetailActivity", "Successfully loaded image from Firebase URL")
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("PostDetailActivity", "Error loading image: $imageUrl", e)
                        postImage.setImageResource(R.drawable.ic_image_placeholder)
                    }
                } else {
                    postImage.visibility = View.GONE
                }
            }
            com.example.ecosort.data.model.InputType.VIDEO -> {
                // Show video thumbnail if there's a video URL
                if (!videoUrl.isNullOrEmpty()) {
                    postImage.visibility = View.VISIBLE
                    
                    // Make video clickable
                    postImage.setOnClickListener {
                        openVideoPlayer(videoUrl)
                    }
                    
                    // Generate and load video thumbnail
                    lifecycleScope.launch(Dispatchers.Main) {
                        try {
                            android.util.Log.d("PostDetailActivity", "Processing video for post detail: $videoUrl")
                            
                            // First, try to load the video URL directly with Glide
                            android.util.Log.d("PostDetailActivity", "Attempting to load video URL directly with Glide")
                            Glide.with(this@PostDetailActivity)
                                .asBitmap()
                                .load(videoUrl)
                                .placeholder(R.drawable.ic_video)
                                .error(R.drawable.ic_video)
                                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                                    override fun onResourceReady(
                                        resource: android.graphics.Bitmap,
                                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?
                                    ) {
                                        android.util.Log.d("PostDetailActivity", "Successfully loaded video thumbnail from URL")
                                        postImage.setImageBitmap(resource)
                                    }
                                    
                                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                        android.util.Log.w("PostDetailActivity", "Failed to load video thumbnail from URL, trying thumbnail generation")
                                        
                                        // If direct loading fails, try thumbnail generation
                                        lifecycleScope.launch(Dispatchers.IO) {
                                            try {
                                                val thumbnailUri = VideoThumbnailGenerator.generateThumbnailFromUrl(
                                                    this@PostDetailActivity, 
                                                    videoUrl
                                                )
                                                
                                                lifecycleScope.launch(Dispatchers.Main) {
                                                    if (thumbnailUri != null) {
                                                        android.util.Log.d("PostDetailActivity", "Loading generated thumbnail: $thumbnailUri")
                                                        Glide.with(this@PostDetailActivity)
                                                            .load(thumbnailUri)
                                                            .placeholder(R.drawable.ic_video)
                                                            .error(R.drawable.ic_video)
                                                            .into(postImage)
                                                    } else {
                                                        android.util.Log.w("PostDetailActivity", "Thumbnail generation failed, using video icon")
                                                        Glide.with(this@PostDetailActivity)
                                                            .load(R.drawable.ic_video)
                                                            .placeholder(R.drawable.ic_video)
                                                            .error(R.drawable.ic_video)
                                                            .into(postImage)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("PostDetailActivity", "Error in thumbnail generation fallback", e)
                                                lifecycleScope.launch(Dispatchers.Main) {
                                                    Glide.with(this@PostDetailActivity)
                                                        .load(R.drawable.ic_video)
                                                        .placeholder(R.drawable.ic_video)
                                                        .error(R.drawable.ic_video)
                                                        .into(postImage)
                                                }
                                            }
                                        }
                                    }
                                    
                                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                        // Called when the image is cleared
                                    }
                                })
                                
                        } catch (e: Exception) {
                            android.util.Log.e("PostDetailActivity", "Error in video thumbnail loading", e)
                            // Final fallback to video icon
                            Glide.with(this@PostDetailActivity)
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
            tags.take(5).forEach { tag -> // Show up to 5 tags in detail view
                val tagView = LayoutInflater.from(this)
                    .inflate(R.layout.item_tag, tagsContainer, false)
                val tagText = tagView.findViewById<TextView>(R.id.textTag)
                tagText.text = "#$tag"
                tagsContainer.addView(tagView)
            }
        } else {
            tagsContainer.visibility = View.GONE
        }
    }

    private fun openVideoPlayer(videoUrl: String) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("video_url", videoUrl)
        startActivity(intent)
    }

    private fun formatTime(timestamp: Long): String {
        val date = Date(timestamp)
        val format = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm", Locale.getDefault())
        return format.format(date)
    }
    
    private fun deletePost() {
        currentPost?.let { post ->
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Delete Post")
                .setMessage("Are you sure you want to delete this post? This action cannot be undone.")
                .setPositiveButton("Delete") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            val result = communityRepository.deletePost(post.id)
                            if (result is com.example.ecosort.data.model.Result.Success) {
                                android.widget.Toast.makeText(this@PostDetailActivity, "Post deleted successfully", android.widget.Toast.LENGTH_SHORT).show()
                                setResult(RESULT_OK)
                                finish()
                            } else {
                                android.widget.Toast.makeText(this@PostDetailActivity, "Error deleting post", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("PostDetailActivity", "Error deleting post", e)
                            android.widget.Toast.makeText(this@PostDetailActivity, "Error deleting post", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun loadAuthorProfilePicture(profileImageUrl: String?) {
        android.util.Log.d("PostDetailActivity", "Loading author profile picture: $profileImageUrl")
        
        if (!profileImageUrl.isNullOrBlank()) {
            android.util.Log.d("PostDetailActivity", "Loading profile image with Glide: $profileImageUrl")
            Glide.with(this)
                .load(profileImageUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_person_24)
                .error(R.drawable.ic_person_24)
                .into(authorProfileImage)
        } else {
            android.util.Log.d("PostDetailActivity", "No profile image URL, showing default placeholder")
            authorProfileImage.setImageResource(R.drawable.ic_person_24)
        }
    }
}
