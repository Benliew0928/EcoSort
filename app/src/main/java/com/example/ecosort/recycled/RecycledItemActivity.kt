package com.example.ecosort.recycled

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.MainActivity
import com.example.ecosort.R
import com.example.ecosort.data.model.RecycledItem
import com.example.ecosort.data.model.RecycledItemStats
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.repository.RecycledItemRepository
import com.example.ecosort.utils.BottomNavigationHelper
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class RecycledItemActivity : AppCompatActivity() {

    @Inject
    lateinit var recycledItemRepository: RecycledItemRepository
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager

    private lateinit var toolbar: MaterialToolbar
    private lateinit var rvRecycledItems: RecyclerView
    private lateinit var llEmptyState: View
    private lateinit var progressBar: ProgressBar
    private lateinit var fabAddItem: FloatingActionButton
    
    // Stats views
    private lateinit var tvTotalItems: TextView
    private lateinit var tvThisMonth: TextView
    private lateinit var tvThisWeek: TextView
    private lateinit var tvToday: TextView
    private lateinit var tvMostRecycledType: TextView
    
    private var currentUserId: Long = 0L
    private var currentUserType: UserType = UserType.USER
    private lateinit var recycledItemAdapter: RecycledItemAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycled_items)
        
        initializeViews()
        setupToolbar()
        setupRecyclerView()
        loadUserData()
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)
    }

    private fun initializeViews() {
        toolbar = findViewById(R.id.toolbar)
        rvRecycledItems = findViewById(R.id.rvRecycledItems)
        llEmptyState = findViewById(R.id.llEmptyState)
        progressBar = findViewById(R.id.progressBar)
        fabAddItem = findViewById(R.id.fabAddItem)
        
        // Stats views
        tvTotalItems = findViewById(R.id.tvTotalItems)
        tvThisMonth = findViewById(R.id.tvThisMonth)
        tvThisWeek = findViewById(R.id.tvThisWeek)
        tvToday = findViewById(R.id.tvToday)
        tvMostRecycledType = findViewById(R.id.tvMostRecycledType)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        
        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupRecyclerView() {
        recycledItemAdapter = RecycledItemAdapter { item ->
            // Handle item click - for future implementation
            android.util.Log.d("RecycledItemActivity", "Item clicked: ${item.itemName}")
        }
        
        rvRecycledItems.apply {
            layoutManager = LinearLayoutManager(this@RecycledItemActivity)
            adapter = recycledItemAdapter
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // Get current user session
                val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
                if (session == null || !session.isLoggedIn) {
                    android.util.Log.w("RecycledItemActivity", "No active session found")
                    finish()
                    return@launch
                }
                
                currentUserId = session.userId
                currentUserType = session.userType
                
                // Load recycled items and stats
                loadRecycledItems()
                loadRecycledItemStats()
                
            } catch (e: Exception) {
                android.util.Log.e("RecycledItemActivity", "Error loading user data", e)
            } finally {
                showLoading(false)
            }
        }
    }

    private suspend fun loadRecycledItems() {
        try {
            val result = recycledItemRepository.getUserRecycledItems(currentUserId)
            if (result is com.example.ecosort.data.model.Result.Success) {
                val items = result.data
                recycledItemAdapter.submitList(items)
                
                // Show/hide empty state
                if (items.isEmpty()) {
                    showEmptyState(true)
                } else {
                    showEmptyState(false)
                }
                
                android.util.Log.d("RecycledItemActivity", "Loaded ${items.size} recycled items")
            } else {
                android.util.Log.e("RecycledItemActivity", "Failed to load recycled items: ${(result as com.example.ecosort.data.model.Result.Error).exception.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemActivity", "Error loading recycled items", e)
        }
    }

    private suspend fun loadRecycledItemStats() {
        try {
            val result = recycledItemRepository.getRecycledItemStats(currentUserId)
            if (result is com.example.ecosort.data.model.Result.Success) {
                val stats = result.data
                updateStatsUI(stats)
                android.util.Log.d("RecycledItemActivity", "Loaded recycled item stats")
            } else {
                android.util.Log.e("RecycledItemActivity", "Failed to load stats: ${(result as com.example.ecosort.data.model.Result.Error).exception.message}")
            }
        } catch (e: Exception) {
            android.util.Log.e("RecycledItemActivity", "Error loading stats", e)
        }
    }

    private fun updateStatsUI(stats: RecycledItemStats) {
        tvTotalItems.text = stats.totalItems.toString()
        tvThisMonth.text = stats.itemsThisMonth.toString()
        tvThisWeek.text = stats.itemsThisWeek.toString()
        tvToday.text = stats.itemsToday.toString()
        
        tvMostRecycledType.text = stats.mostRecycledType ?: "None yet"
    }

    private fun showEmptyState(show: Boolean) {
        if (show) {
            llEmptyState.visibility = View.VISIBLE
            rvRecycledItems.visibility = View.GONE
        } else {
            llEmptyState.visibility = View.GONE
            rvRecycledItems.visibility = View.VISIBLE
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    override fun onBackPressed() {
        super.onBackPressed()
        // Navigate back to MainActivity
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }
}
