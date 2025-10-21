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
        binding = ActivityCommunityFeedBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupClickListeners()
        loadCurrentUser()
        loadPosts(null) // Load all posts initially
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
        binding.recyclerViewPosts.layoutManager = LinearLayoutManager(this)
        binding.recyclerViewPosts.adapter = adapter
    }

    private fun setupClickListeners() {
            binding.fabAddPost.setOnClickListener {
                startActivityForResult(Intent(this, CreatePostActivity::class.java), REQUEST_CODE_CREATE_POST)
            }

        binding.btnFilterAll.setOnClickListener { loadPosts(null) }
        binding.btnFilterTips.setOnClickListener { loadPosts(PostType.TIP) }
        binding.btnFilterAchievements.setOnClickListener { loadPosts(PostType.ACHIEVEMENT) }
        binding.btnFilterQuestions.setOnClickListener { loadPosts(PostType.QUESTION) }
    }

    private fun loadCurrentUser() {
        // For now, use a simple approach
        currentUserId = 1L
        android.util.Log.d("CommunityFeedActivity", "Current user ID: $currentUserId")
    }


    private fun loadPosts(postType: PostType?, tagFilter: String? = null) {
        lifecycleScope.launch {
            binding.progressBar.visibility = View.VISIBLE
            try {
                val postsFlow = when {
                    tagFilter != null -> {
                        communityRepository.searchPostsByTag(tagFilter)
                    }
                    postType == null -> {
                        communityRepository.getAllCommunityPosts()
                    }
                    else -> {
                        communityRepository.getCommunityPostsByType(postType)
                    }
                }

                // Use collectLatest to handle cancellation properly and get real-time updates
                postsFlow.collectLatest { posts ->
                    android.util.Log.d("CommunityFeedActivity", "Loaded ${posts.size} posts for type: ${postType ?: "All"}")
                    
                    // Update posts with like status
                    val postsWithLikeStatus = posts.map { post ->
                        val isLiked = try {
                            communityRepository.hasUserLikedPost(post.id)
                        } catch (e: Exception) {
                            false
                        }
                        post.copy(isLikedByUser = isLiked)
                    }
                    
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
                val result = communityRepository.togglePostLike(post.id)
                when (result) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        android.util.Log.d("CommunityFeedActivity", "Like toggled successfully: ${result.data}")
                        // Refresh the posts to show updated like status
                        loadPosts(null)
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        android.util.Log.e("CommunityFeedActivity", "Error toggling like", result.exception)
                        Toast.makeText(this@CommunityFeedActivity, "Error updating like", Toast.LENGTH_SHORT).show()
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Show loading state if needed
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("CommunityFeedActivity", "Like toggle cancelled")
                // Don't show error for cancellation
            } catch (e: Exception) {
                android.util.Log.e("CommunityFeedActivity", "Error in toggleLike", e)
                Toast.makeText(this@CommunityFeedActivity, "Error updating like", Toast.LENGTH_SHORT).show()
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

    override fun onResume() {
        super.onResume()
        // Refresh posts when activity becomes visible
        android.util.Log.d("CommunityFeedActivity", "Activity resumed, refreshing posts...")
        if (currentTagFilter != null) {
            loadPosts(null, currentTagFilter)
        } else {
            loadPosts(null) // Load all posts
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
