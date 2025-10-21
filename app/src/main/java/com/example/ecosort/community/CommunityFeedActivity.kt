package com.example.ecosort.community

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.ecosort.R
import com.example.ecosort.data.model.CommunityPost
import com.example.ecosort.data.model.PostType
import com.example.ecosort.data.repository.CommunityRepository
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.databinding.ActivityCommunityFeedBinding
import com.example.ecosort.utils.ResponsiveUtils
import com.example.ecosort.utils.setResponsiveLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CommunityFeedActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_CODE_CREATE_POST = 1001
    }

    private lateinit var binding: ActivityCommunityFeedBinding
    private lateinit var adapter: CommunityPostAdapter

    @Inject
    lateinit var communityRepository: CommunityRepository

    @Inject
    lateinit var userRepository: UserRepository

    private var currentUserId: Long = 0L
    private var currentTagFilter: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        android.util.Log.d("CommunityFeedActivity", "=== ACTIVITY CREATED ===")
        binding = ActivityCommunityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadCurrentUser()
        loadPosts(null) // Load all posts initially
        android.util.Log.d("CommunityFeedActivity", "=== ACTIVITY SETUP COMPLETE ===")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Community Feed"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        adapter = CommunityPostAdapter(
            onLikeClick = { post -> toggleLike(post) },
            onCommentClick = { post -> openComments(post) },
            onShareClick = { post -> sharePost(post) },
            onPostClick = { post -> openPostDetail(post) },
            onTagClick = { tag -> filterByTag(tag) },
            onVideoClick = { videoUrl -> openVideoPlayer(videoUrl) }
        )
        
        // Use responsive layout manager
        binding.recyclerViewPosts.setResponsiveLayoutManager(
            context = this,
            baseColumns = 1,
            useStaggered = false
        )
        binding.recyclerViewPosts.adapter = adapter
    }

    private fun setupClickListeners() {
        binding.fabAddPost.setOnClickListener {
            startActivityForResult(Intent(this, CreatePostActivity::class.java), REQUEST_CODE_CREATE_POST)
        }

        binding.btnFilterAll.setOnClickListener { 
            android.util.Log.d("CommunityFeedActivity", "All filter clicked")
            loadPosts(null) 
        }
        binding.btnFilterTips.setOnClickListener { 
            android.util.Log.d("CommunityFeedActivity", "Tips filter clicked")
            loadPosts(PostType.TIP) 
        }
        binding.btnFilterAchievements.setOnClickListener { 
            android.util.Log.d("CommunityFeedActivity", "Achievements filter clicked")
            loadPosts(PostType.ACHIEVEMENT) 
        }
        binding.btnFilterQuestions.setOnClickListener { 
            android.util.Log.d("CommunityFeedActivity", "Questions filter clicked")
            loadPosts(PostType.QUESTION) 
        }
        
        // Add long press on toolbar to force refresh
        binding.toolbar.setOnLongClickListener {
            android.util.Log.d("CommunityFeedActivity", "Toolbar long pressed - forcing refresh")
            loadPosts(null)
            Toast.makeText(this, "Refreshing posts...", Toast.LENGTH_SHORT).show()
            true
        }
        
        // Add double tap on toolbar to add sample data
        var lastTapTime = 0L
        binding.toolbar.setOnClickListener {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastTapTime < 500) {
                // Double tap detected
                android.util.Log.d("CommunityFeedActivity", "Double tap detected - adding sample data")
                addSamplePosts()
            }
            lastTapTime = currentTime
        }
    }

    private fun loadCurrentUser() {
        // For now, use a simple approach
        currentUserId = 1L
        android.util.Log.d("CommunityFeedActivity", "Current user ID: $currentUserId")
        
        // Sync data from Firebase first
        lifecycleScope.launch {
            try {
                android.util.Log.d("CommunityFeedActivity", "Starting Firebase sync...")
                communityRepository.syncPostsFromFirebase()
                
                val testPosts = communityRepository.getAllCommunityPosts().first()
                android.util.Log.d("CommunityFeedActivity", "Database test: Found ${testPosts.size} posts in database")
                if (testPosts.isNotEmpty()) {
                    android.util.Log.d("CommunityFeedActivity", "Sample post: ${testPosts.first().title}")
                }
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "Database test failed", e)
            }
        }
    }


    private fun loadPosts(postType: PostType?, tagFilter: String? = null) {
        lifecycleScope.launch {
            android.util.Log.d("CommunityFeedActivity", "Starting to load posts - postType: $postType, tagFilter: $tagFilter")
            binding.progressBar.visibility = View.VISIBLE
            try {
                val postsFlow = when {
                    tagFilter != null -> {
                        android.util.Log.d("CommunityFeedActivity", "Loading posts by tag: $tagFilter")
                        communityRepository.searchPostsByTag(tagFilter)
                    }
                    postType == null -> {
                        android.util.Log.d("CommunityFeedActivity", "Loading all posts")
                        communityRepository.getAllCommunityPosts()
                    }
                    else -> {
                        android.util.Log.d("CommunityFeedActivity", "Loading posts by type: $postType")
                        communityRepository.getCommunityPostsByType(postType)
                    }
                }

                // Use collectLatest to handle cancellation properly and get real-time updates
                postsFlow.collectLatest { posts ->
                    android.util.Log.d("CommunityFeedActivity", "Loaded ${posts.size} posts for type: ${postType ?: "All"}")
                    
                    if (posts.isNotEmpty()) {
                        android.util.Log.d("CommunityFeedActivity", "First post details: ${posts.first().title} by ${posts.first().authorName}")
                    }
                    
                    // Update posts with like status
                    val postsWithLikeStatus = posts.map { post ->
                        val isLiked = try {
                            communityRepository.hasUserLikedPost(post.id)
                        } catch (e: Exception) {
                            android.util.Log.w("CommunityFeedActivity", "Error checking like status for post ${post.id}", e)
                            false
                        }
                        post.copy(isLikedByUser = isLiked)
                    }
                    
                    android.util.Log.d("CommunityFeedActivity", "Submitting ${postsWithLikeStatus.size} posts to adapter")
                    adapter.submitList(postsWithLikeStatus)
                    binding.textViewEmpty.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                    binding.progressBar.visibility = View.GONE
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("CommunityFeedActivity", "Post loading cancelled")
                // Don't show error for cancellation
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "Error loading posts", e)
                Toast.makeText(this@CommunityFeedActivity, "Error loading posts: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.progressBar.visibility = View.GONE
                binding.textViewEmpty.text = "Error loading posts: ${e.message}"
                binding.textViewEmpty.visibility = View.VISIBLE
            }
        }
    }


    private fun toggleLike(post: CommunityPost) {
        lifecycleScope.launch {
            try {
                android.util.Log.d("CommunityFeedActivity", "Toggling like for post: ${post.id}")
                val result = communityRepository.togglePostLike(post.id)
                when (result) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        android.util.Log.d("CommunityFeedActivity", "Like toggled successfully: ${result.data}")
                        // Refresh the posts to show updated like status
                        loadPosts(null)
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        android.util.Log.e("CommunityFeedActivity", "Error toggling like", result.exception)
                        Toast.makeText(this@CommunityFeedActivity, "Error updating like: ${result.exception.message}", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        android.util.Log.d("CommunityFeedActivity", "Like operation in progress...")
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("CommunityFeedActivity", "Like toggle cancelled")
                // Don't show error for cancellation
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "Error in toggleLike", e)
                Toast.makeText(this@CommunityFeedActivity, "Error updating like: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openComments(post: CommunityPost) {
        val intent = Intent(this, CommentActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }
    
    private fun openPostDetail(post: CommunityPost) {
        val intent = Intent(this, PostDetailActivity::class.java)
        intent.putExtra("post", post)
        startActivity(intent)
    }

    private fun sharePost(post: CommunityPost) {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Check out this post on EcoSort: ${post.title}\n${post.content}")
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share post via"))
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_CREATE_POST && resultCode == RESULT_OK) {
            // Post was created successfully, refresh the feed
            android.util.Log.d("CommunityFeedActivity", "Post created, refreshing feed...")
            loadPosts(null)
        }
    }

    private fun filterByTag(tag: String) {
        currentTagFilter = tag
        supportActionBar?.title = "Posts tagged: #$tag"
        loadPosts(null, tag)
    }

    private fun clearTagFilter() {
        currentTagFilter = null
        supportActionBar?.title = "Community Feed"
        loadPosts(null)
    }

    private fun openVideoPlayer(videoUrl: String) {
        val intent = Intent(this, VideoPlayerActivity::class.java)
        intent.putExtra("video_url", videoUrl)
        startActivity(intent)
    }
    
    private fun addSamplePosts() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("CommunityFeedActivity", "Adding sample posts...")
                
                // Sample posts with unique IDs
                val currentTime = System.currentTimeMillis()
                val samplePosts = listOf(
                    CommunityPost(
                        id = 0L, // Will be auto-generated
                        title = "Welcome to EcoSort Community!",
                        content = "This is our first community post. Share your eco-friendly tips, achievements, and questions here!",
                        authorId = 1L,
                        authorName = "EcoSort Team",
                        postType = PostType.TIP,
                        inputType = com.example.ecosort.data.model.InputType.TEXT,
                        postedAt = currentTime,
                        tags = listOf("welcome", "community", "tips")
                    ),
                    CommunityPost(
                        id = 0L, // Will be auto-generated
                        title = "My First Recycling Achievement!",
                        content = "Just recycled 50 plastic bottles this week. Every small action counts towards a greener planet! 🌱",
                        authorId = 1L,
                        authorName = "Ben",
                        postType = PostType.ACHIEVEMENT,
                        inputType = com.example.ecosort.data.model.InputType.TEXT,
                        postedAt = currentTime - 86400000, // 1 day ago
                        tags = listOf("achievement", "recycling", "plastic")
                    ),
                    CommunityPost(
                        id = 0L, // Will be auto-generated
                        title = "How to properly sort electronic waste?",
                        content = "I have some old phones and laptops. What's the best way to dispose of them responsibly?",
                        authorId = 1L,
                        authorName = "Ben",
                        postType = PostType.QUESTION,
                        inputType = com.example.ecosort.data.model.InputType.TEXT,
                        postedAt = currentTime - 172800000, // 2 days ago
                        tags = listOf("question", "e-waste", "electronics")
                    ),
                    CommunityPost(
                        id = 0L, // Will be auto-generated
                        title = "Community Cleanup Event This Saturday",
                        content = "Join us for a beach cleanup event this Saturday at 9 AM. Bring your friends and family!",
                        authorId = 1L,
                        authorName = "EcoSort Team",
                        postType = PostType.EVENT,
                        inputType = com.example.ecosort.data.model.InputType.TEXT,
                        postedAt = currentTime - 259200000, // 3 days ago
                        tags = listOf("event", "cleanup", "beach", "volunteer")
                    ),
                    CommunityPost(
                        id = 0L, // Will be auto-generated
                        title = "How to Recycle Electronics - Video Guide",
                        content = "Watch this video to learn the proper way to recycle your old electronics and e-waste!",
                        authorId = 1L,
                        authorName = "Ben",
                        postType = PostType.TIP,
                        inputType = com.example.ecosort.data.model.InputType.VIDEO,
                        videoUrl = "https://firebasestorage.googleapis.com/v0/b/ecosort-cb237.firebasestorage.app/o/community_videos%2Fpost_video_1761046671693.mp4?alt=media&token=594a191d-2cd4-4efa-938c-5e7228fe61fb",
                        postedAt = currentTime - 345600000, // 4 days ago
                        tags = listOf("video", "electronics", "recycling", "guide")
                    )
                )
                
                // Add each post to the database
                for (post in samplePosts) {
                    communityRepository.addCommunityPost(
                        title = post.title,
                        content = post.content,
                        postType = post.postType,
                        inputType = post.inputType,
                        imageUrls = post.imageUrls,
                        videoUrl = post.videoUrl,
                        location = post.location,
                        tags = post.tags
                    )
                    android.util.Log.d("CommunityFeedActivity", "Added sample post: ${post.title}")
                }
                
                Toast.makeText(this@CommunityFeedActivity, "Sample posts added! Refresh to see them.", Toast.LENGTH_LONG).show()
                android.util.Log.d("CommunityFeedActivity", "Sample posts added successfully")
                
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "Error adding sample posts", e)
                Toast.makeText(this@CommunityFeedActivity, "Error adding sample posts: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh posts when activity becomes visible
        android.util.Log.d("CommunityFeedActivity", "Activity resumed, refreshing posts...")
        
        // Force refresh with a small delay to ensure UI is ready
        binding.root.post {
            if (currentTagFilter != null) {
                loadPosts(null, currentTagFilter)
            } else {
                loadPosts(null) // Load all posts
            }
        }
    }

    override fun onBackPressed() {
        if (currentTagFilter != null) {
            // Clear tag filter instead of going back
            clearTagFilter()
        } else {
            super.onBackPressed()
        }
    }
}
