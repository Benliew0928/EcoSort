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
import com.example.ecosort.data.model.CommunityPost
import com.example.ecosort.data.repository.AdminRepository
import com.example.ecosort.data.repository.CommunityRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CommunityManagementActivity : AppCompatActivity(), CommunityManagementAdapter.OnCommunityActionListener {

    @Inject
    lateinit var adminRepository: AdminRepository
    
    @Inject
    lateinit var communityRepository: CommunityRepository

    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CommunityManagementAdapter
    private var communityPosts: List<CommunityPost> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_community_management)

        setupUI()
        setupRecyclerView()
        setupListeners()
        loadCommunityPosts()
    }

    private fun setupUI() {
        btnBack = findViewById(R.id.btnBackCommunityManagement)
        tvTitle = findViewById(R.id.tvCommunityManagementTitle)
        recyclerView = findViewById(R.id.recyclerViewCommunityPosts)
    }

    private fun setupRecyclerView() {
        adapter = CommunityManagementAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
    }

    private fun loadCommunityPosts() {
        lifecycleScope.launch {
            try {
                communityRepository.getAllCommunityPosts().collect { postsList ->
                    communityPosts = postsList
                    adapter.submitList(communityPosts)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation gracefully - this is normal when activity is destroyed
                android.util.Log.d("CommunityManagementActivity", "Loading community posts cancelled")
            } catch (e: Exception) {
                Toast.makeText(this@CommunityManagementActivity, "Error loading community posts: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDeletePost(post: CommunityPost) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Community Post")
            .setMessage("Are you sure you want to delete this post by '${post.authorName}'? This action cannot be undone and will also delete all comments.")
            .setPositiveButton("Delete") { _, _ ->
                deletePost(post)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onViewPostDetails(post: CommunityPost) {
        // Show post details dialog
        CommunityPostDetailDialog.newInstance(post).show(supportFragmentManager, "CommunityPostDetailDialog")
    }

    override fun onManageComments(post: CommunityPost) {
        // Navigate to comments management
        val intent = Intent(this, CommunityCommentsManagementActivity::class.java)
        intent.putExtra("post_id", post.id)
        intent.putExtra("post_title", post.title)
        startActivity(intent)
    }

    private fun deletePost(post: CommunityPost) {
        lifecycleScope.launch {
            try {
                // Use fallback admin ID for now (in a real app, this would come from session management)
                val adminId = 1L
                
                when (val result = adminRepository.deleteCommunityPost(adminId, post.id.toString())) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        Toast.makeText(this@CommunityManagementActivity, "Post deleted successfully", Toast.LENGTH_SHORT).show()
                        loadCommunityPosts() // Refresh the list
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        Toast.makeText(this@CommunityManagementActivity, "Error deleting post: ${result.exception.message}", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Toast.makeText(this@CommunityManagementActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Handle cancellation gracefully
                android.util.Log.d("CommunityManagementActivity", "Delete post operation cancelled")
            } catch (e: Exception) {
                Toast.makeText(this@CommunityManagementActivity, "Error deleting post: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
}
