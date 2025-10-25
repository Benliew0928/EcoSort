package com.example.ecosort.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.CommunityComment
import com.example.ecosort.data.repository.AdminRepository
import com.example.ecosort.data.repository.CommunityRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CommunityCommentsManagementActivity : AppCompatActivity(), CommunityCommentsManagementAdapter.OnCommentActionListener {

    @Inject
    lateinit var adminRepository: AdminRepository
    
    @Inject
    lateinit var communityRepository: CommunityRepository

    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CommunityCommentsManagementAdapter
    private var comments: List<CommunityComment> = emptyList()
    private var postId: String = ""
    private var postTitle: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_comments_management)

        // Get post information from intent
        postId = intent.getStringExtra("post_id") ?: ""
        postTitle = intent.getStringExtra("post_title") ?: "Unknown Post"

        setupUI()
        setupRecyclerView()
        setupListeners()
        loadComments()
    }

    private fun setupUI() {
        btnBack = findViewById(R.id.btnBackCommentsManagement)
        tvTitle = findViewById(R.id.tvCommentsManagementTitle)
        recyclerView = findViewById(R.id.recyclerViewComments)
        
        tvTitle.text = "Comments for: $postTitle"
    }

    private fun setupRecyclerView() {
        adapter = CommunityCommentsManagementAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
    }

    private fun loadComments() {
        lifecycleScope.launch {
            try {
                val postIdLong = postId.toLongOrNull() ?: 0L
                communityRepository.getCommentsForPost(postIdLong).collect { commentsList ->
                    comments = commentsList
                    adapter.submitList(comments)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation gracefully - this is normal when activity is destroyed
                android.util.Log.d("CommunityCommentsManagementActivity", "Loading comments cancelled")
            } catch (e: Exception) {
                Toast.makeText(this@CommunityCommentsManagementActivity, "Error loading comments: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDeleteComment(comment: CommunityComment) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Comment")
            .setMessage("Are you sure you want to delete this comment by '${comment.authorName}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteComment(comment)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onViewCommentDetails(comment: CommunityComment) {
        // Show comment details dialog
        CommunityCommentDetailDialog.newInstance(comment).show(supportFragmentManager, "CommunityCommentDetailDialog")
    }

    private fun deleteComment(comment: CommunityComment) {
        lifecycleScope.launch {
            try {
                // Use fallback admin ID for now (in a real app, this would come from session management)
                val adminId = 1L
                
                when (val result = adminRepository.deleteCommunityComment(adminId, comment.id.toString())) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        Toast.makeText(this@CommunityCommentsManagementActivity, "Comment deleted successfully", Toast.LENGTH_SHORT).show()
                        loadComments() // Refresh the list
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        Toast.makeText(this@CommunityCommentsManagementActivity, "Error deleting comment: ${result.exception.message}", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Toast.makeText(this@CommunityCommentsManagementActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation gracefully
                android.util.Log.d("CommunityCommentsManagementActivity", "Delete comment operation cancelled")
            } catch (e: Exception) {
                Toast.makeText(this@CommunityCommentsManagementActivity, "Error deleting comment: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
