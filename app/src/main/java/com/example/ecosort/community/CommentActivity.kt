package com.example.ecosort.community

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.CommunityComment
import com.example.ecosort.data.model.CommunityPost
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.repository.CommunityRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CommentActivity : AppCompatActivity() {
    
    @Inject
    lateinit var communityRepository: CommunityRepository
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    private lateinit var toolbar: Toolbar
    private lateinit var commentTitle: TextView
    private lateinit var recyclerViewComments: RecyclerView
    private lateinit var editTextComment: EditText
    private lateinit var buttonSendComment: ImageButton
    private lateinit var textViewEmpty: TextView
    
    private lateinit var commentAdapter: CommentAdapter
    private var currentPost: CommunityPost? = null
    private var currentUserId: Long = 1L
    private var currentUsername: String = "User"
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_comment)
        
        setupToolbar()
        setupViews()
        setupRecyclerView()
        setupClickListeners()
        loadCurrentUser()
        
        // Get post data from intent
        currentPost = intent.getSerializableExtra("post") as? CommunityPost
        if (currentPost != null) {
            displayCommentTitle(currentPost!!)
            loadComments()
        } else {
            finish()
        }
    }
    
    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Comments"
    }
    
    private fun setupViews() {
        commentTitle = findViewById(R.id.textCommentTitle)
        recyclerViewComments = findViewById(R.id.recyclerViewComments)
        editTextComment = findViewById(R.id.editTextComment)
        buttonSendComment = findViewById(R.id.buttonSendComment)
        textViewEmpty = findViewById(R.id.textViewEmpty)
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
    }
    
    private fun loadCurrentUser() {
        lifecycleScope.launch {
            try {
                val userSession = userPreferencesManager.getCurrentUser()
                currentUserId = userSession?.userId ?: 1L
                currentUsername = userSession?.username ?: "Anonymous User"
                android.util.Log.d("CommentActivity", "Current user loaded: $currentUsername (ID: $currentUserId)")
            } catch (e: Exception) {
                android.util.Log.e("CommentActivity", "Error loading current user", e)
                // Use fallback values
                currentUserId = 1L
                currentUsername = "Anonymous User"
            }
        }
    }
    
    private fun displayCommentTitle(post: CommunityPost) {
        commentTitle.text = "Comments for post \"${post.title}\""
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
                    android.util.Log.e("CommentActivity", "Error loading comments", e)
                }
            }
        }
    }
    
    private fun sendComment() {
        val commentText = editTextComment.text.toString().trim()
        
        if (commentText.isEmpty()) {
            android.util.Log.w("CommentActivity", "Attempted to send empty comment")
            return
        }
        
        currentPost?.let { post ->
            lifecycleScope.launch {
                try {
                    android.util.Log.d("CommentActivity", "Sending comment for post: ${post.id}")
                    val result = communityRepository.addComment(
                        postId = post.id,
                        authorId = currentUserId,
                        authorName = currentUsername,
                        content = commentText
                    )
                    
                    when (result) {
                        is com.example.ecosort.data.model.Result.Success -> {
                            android.util.Log.d("CommentActivity", "Comment added successfully")
                            editTextComment.text.clear()
                            // Comments will be updated automatically via Flow
                        }
                        is com.example.ecosort.data.model.Result.Error -> {
                            android.util.Log.e("CommentActivity", "Error adding comment", result.exception)
                            android.widget.Toast.makeText(this@CommentActivity, "Error adding comment: ${result.exception.message}", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        is com.example.ecosort.data.model.Result.Loading -> {
                            android.util.Log.d("CommentActivity", "Comment operation in progress...")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("CommentActivity", "Error sending comment", e)
                    android.widget.Toast.makeText(this@CommentActivity, "Error sending comment: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        } ?: run {
            android.util.Log.e("CommentActivity", "Cannot send comment: currentPost is null")
            android.widget.Toast.makeText(this, "Error: Post not found", android.widget.Toast.LENGTH_SHORT).show()
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
                        android.util.Log.e("CommentActivity", "Error deleting comment", result.exception)
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Should not happen for suspend fun
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("CommentActivity", "Error deleting comment", e)
            }
        }
    }
    
    private fun updateEmptyState(isEmpty: Boolean) {
        textViewEmpty.visibility = if (isEmpty) View.VISIBLE else View.GONE
        recyclerViewComments.visibility = if (isEmpty) View.GONE else View.VISIBLE
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
}
