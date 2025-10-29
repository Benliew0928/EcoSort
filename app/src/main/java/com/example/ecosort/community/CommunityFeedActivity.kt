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
import com.example.ecosort.utils.BottomNavigationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
        
        // Add bottom navigation BEFORE setting up click listeners
        BottomNavigationHelper.addBottomNavigationToActivity(this)
        
        // Raise FAB programmatically to ensure visibility above overlays
        binding.fabAddPost.post {
            val raiseByDp = 160
            val raiseByPx = (raiseByDp * resources.displayMetrics.density).toInt()
            binding.fabAddPost.translationY = -raiseByPx.toFloat()
            binding.fabAddPost.bringToFront()
            binding.fabAddPost.visibility = View.VISIBLE
            binding.fabAddPost.alpha = 1.0f
        }
        
        setupClickListeners()
        loadCurrentUser()
        
        // Load local posts IMMEDIATELY for fast UI response
        loadPosts(null)
        
        // 🔥 NEW: Start continuous real-time Firebase sync in background
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("CommunityFeedActivity", "🔄 === Starting Real-Time Firebase Sync ===")
                communityRepository.syncCommunityPostsFromFirebaseRealTime().collect { syncedCount ->
                    if (syncedCount > 0) {
                        android.util.Log.d("CommunityFeedActivity", "✅ Real-time sync: $syncedCount new/updated posts")
                        // Posts will automatically update via Room DB Flow - no manual refresh needed!
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "💥 Real-time sync error: ${e.message}")
                // Fallback to one-time sync if real-time fails
                try {
                    val syncResult = communityRepository.syncCommunityPostsFromFirebase()
                    if (syncResult is com.example.ecosort.data.model.Result.Success) {
                        android.util.Log.d("CommunityFeedActivity", "✅ Fallback sync: ${syncResult.data} posts")
                    }
                } catch (fallbackError: Exception) {
                    android.util.Log.e("CommunityFeedActivity", "❌ Fallback sync also failed: ${fallbackError.message}")
                }
            }
        }
        
        android.util.Log.d("CommunityFeedActivity", "=== ACTIVITY SETUP COMPLETE ===")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Community Feed"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        
        // Add refresh button to toolbar
        binding.toolbar.inflateMenu(R.menu.community_feed_menu)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_refresh -> {
                    android.util.Log.d("CommunityFeedActivity", "Manual refresh triggered")
                    loadPosts(null)
                    true
                }
                R.id.action_add_sample -> {
                    android.util.Log.d("CommunityFeedActivity", "Adding sample posts manually")
                    addSamplePosts()
                    true
                }
                R.id.action_debug -> {
                    android.util.Log.d("CommunityFeedActivity", "Debug database state")
                    debugDatabaseState()
                    true
                }
                else -> false
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = CommunityPostAdapter(
            onLikeClick = { post -> toggleLike(post) },
            onCommentClick = { post -> openComments(post) },
            onShareClick = { post -> sharePost(post) },
            onPostClick = { post -> openPostDetail(post) },
            onTagClick = { tag -> filterByTag(tag) },
            onVideoClick = { videoUrl -> openVideoPlayer(videoUrl) },
            onAuthorClick = { authorFirebaseUid -> openUserProfile(authorFirebaseUid) }
        )
        
        // Use LinearLayoutManager for better compatibility
        binding.rvPosts.layoutManager = LinearLayoutManager(this)
        binding.rvPosts.adapter = adapter
        
        // Ensure RecyclerView is visible and properly configured
        binding.rvPosts.visibility = View.VISIBLE
        binding.rvPosts.setHasFixedSize(false)
        
        android.util.Log.d("CommunityFeedActivity", "RecyclerView setup complete")
    }

    private fun setupClickListeners() {
        android.util.Log.d("CommunityFeedActivity", "Setting up FAB click listener")
        binding.fabAddPost.setOnClickListener {
            android.util.Log.d("CommunityFeedActivity", "FAB clicked!")
            startActivityForResult(Intent(this, CreatePostActivity::class.java), REQUEST_CODE_CREATE_POST)
        }
        
        // Debug FAB visibility
        android.util.Log.d("CommunityFeedActivity", "FAB visibility: ${binding.fabAddPost.visibility}")
        android.util.Log.d("CommunityFeedActivity", "FAB isShown: ${binding.fabAddPost.isShown}")
        android.util.Log.d("CommunityFeedActivity", "FAB alpha: ${binding.fabAddPost.alpha}")
        
        // Ensure FAB is visible
        binding.fabAddPost.visibility = View.VISIBLE
        binding.fabAddPost.alpha = 1.0f

        binding.btnFilterAll.setOnClickListener { 
            android.util.Log.d("CommunityFeedActivity", "All filter clicked")
            loadPosts(null) 
        }
        binding.btnFilterTips.setOnClickListener { 
            android.util.Log.d("CommunityFeedActivity", "Tips filter clicked")
            loadPosts(PostType.TIP) 
        }
        binding.btnFilterQuestions.setOnClickListener { 
            android.util.Log.d("CommunityFeedActivity", "Questions filter clicked")
            loadPosts(PostType.QUESTION) 
        }
        binding.btnFilterEvents.setOnClickListener { 
            android.util.Log.d("CommunityFeedActivity", "Events filter clicked")
            loadPosts(PostType.EVENT) 
        }
        
        // Manual sync removed; automatic sync happens on start and on resume
        
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
                    android.util.Log.d("CommunityFeedActivity", "📊 Loaded ${posts.size} posts for type: ${postType ?: "All"}")
                    
                    if (posts.isEmpty()) {
                        android.util.Log.w("CommunityFeedActivity", "⚠️ WARNING: No posts found! This could mean:")
                        android.util.Log.w("CommunityFeedActivity", "  1. No posts in local database")
                        android.util.Log.w("CommunityFeedActivity", "  2. Firebase sync hasn't happened yet")
                        android.util.Log.w("CommunityFeedActivity", "  3. All posts are filtered out (status != PUBLISHED)")
                    } else {
                        android.util.Log.d("CommunityFeedActivity", "📝 First post details:")
                        android.util.Log.d("CommunityFeedActivity", "  - Title: ${posts.first().title}")
                        android.util.Log.d("CommunityFeedActivity", "  - Author: ${posts.first().authorName}")
                        android.util.Log.d("CommunityFeedActivity", "  - Author ID: ${posts.first().authorId}")
                        android.util.Log.d("CommunityFeedActivity", "  - Status: ${posts.first().status}")
                        android.util.Log.d("CommunityFeedActivity", "  - Firebase ID: ${posts.first().firebaseId}")
                    }
                    
                    // Submit posts directly - like status will be updated when user interacts
                    android.util.Log.d("CommunityFeedActivity", "📤 Submitting ${posts.size} posts to adapter")
                    adapter.submitList(posts) {
                        android.util.Log.d("CommunityFeedActivity", "Adapter list updated with ${posts.size} posts")
                        // Ensure RecyclerView is visible after data is loaded
                        binding.rvPosts.visibility = View.VISIBLE
                    }
                    binding.llEmptyState.visibility = if (posts.isEmpty()) View.VISIBLE else View.GONE
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("CommunityFeedActivity", "Post loading cancelled")
                // Don't show error for cancellation
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "Error loading posts", e)
                Toast.makeText(this@CommunityFeedActivity, "Error loading posts: ${e.message}", Toast.LENGTH_SHORT).show()
                binding.llEmptyState.visibility = View.VISIBLE
            }
        }
    }


    private fun updatePostLikeStatus(postId: Long, isLiked: Boolean) {
        // Update the specific post in the adapter without refreshing all posts
        val currentList = adapter.currentList.toMutableList()
        val postIndex = currentList.indexOfFirst { it.id == postId }
        if (postIndex != -1) {
            val updatedPost = currentList[postIndex].copy(
                isLikedByUser = isLiked,
                likesCount = if (isLiked) currentList[postIndex].likesCount + 1 else currentList[postIndex].likesCount - 1
            )
            currentList[postIndex] = updatedPost
            adapter.submitList(currentList)
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
                        // Update the specific post in the adapter instead of refreshing all posts
                        updatePostLikeStatus(post.id, result.data)
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
                        authorFirebaseUid = "sample_uid_ecosort_team", // 🔑 Sample Firebase UID
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
                        authorFirebaseUid = "sample_uid_ben", // 🔑 Sample Firebase UID
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
                        authorFirebaseUid = "sample_uid_ben", // 🔑 Sample Firebase UID
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
                        authorFirebaseUid = "sample_uid_ecosort_team", // 🔑 Sample Firebase UID
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
                        authorFirebaseUid = "sample_uid_ben", // 🔑 Sample Firebase UID
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
                    val result = communityRepository.addCommunityPost(
                        title = post.title,
                        content = post.content,
                        postType = post.postType,
                        inputType = post.inputType,
                        imageUrls = post.imageUrls,
                        videoUrl = post.videoUrl,
                        location = post.location,
                        tags = post.tags
                    )
                    android.util.Log.d("CommunityFeedActivity", "Added sample post: ${post.title}, result: $result")
                }
                
                // Refresh the feed after adding sample posts
                loadPosts(null)
                
                Toast.makeText(this@CommunityFeedActivity, "Sample posts added successfully!", Toast.LENGTH_SHORT).show()
                android.util.Log.d("CommunityFeedActivity", "Sample posts added successfully")
                
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "Error adding sample posts", e)
                Toast.makeText(this@CommunityFeedActivity, "Error adding sample posts: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun debugDatabaseState() {
        lifecycleScope.launch {
            try {
                android.util.Log.d("CommunityFeedActivity", "=== DATABASE DEBUG ===")
                
                // Check total post count
                val allPosts = communityRepository.getAllCommunityPosts().first()
                android.util.Log.d("CommunityFeedActivity", "Total posts in database: ${allPosts.size}")
                
                // Log each post
                allPosts.forEachIndexed { index, post ->
                    android.util.Log.d("CommunityFeedActivity", "Post $index: ID=${post.id}, Title='${post.title}', Author='${post.authorName}', Type=${post.postType}, PostedAt=${post.postedAt}")
                }
                
                // Check if adapter has data
                android.util.Log.d("CommunityFeedActivity", "Adapter item count: ${adapter?.itemCount ?: 0}")
                
                // Check RecyclerView state
                android.util.Log.d("CommunityFeedActivity", "RecyclerView visibility: ${binding.rvPosts.visibility}")
                android.util.Log.d("CommunityFeedActivity", "RecyclerView adapter: ${binding.rvPosts.adapter}")
                
                Toast.makeText(this@CommunityFeedActivity, "Database has ${allPosts.size} posts. Check logs for details.", Toast.LENGTH_LONG).show()
                
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "Error debugging database", e)
                Toast.makeText(this@CommunityFeedActivity, "Debug error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openUserProfile(authorFirebaseUid: String) {
        val intent = Intent(this, com.example.ecosort.profile.UserProfileViewActivity::class.java)
        intent.putExtra("firebase_uid", authorFirebaseUid)
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        android.util.Log.d("CommunityFeedActivity", "Activity resumed, quick refresh...")
        
        // Quick background sync - don't block UI
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("CommunityFeedActivity", "Background sync on resume...")
                communityRepository.syncCommunityPostsFromFirebase()
            } catch (e: Exception) {
                android.util.Log.w("CommunityFeedActivity", "Resume sync error: ${e.message}")
            }
        }
        
        // Posts will automatically update via Flow - no manual reload needed
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
