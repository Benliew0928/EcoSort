package com.example.ecosort.points

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.PointsTransaction
import com.example.ecosort.data.model.UserPoints
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.repository.PointsRepository
import com.example.ecosort.utils.BottomNavigationHelper
import com.google.android.material.appbar.MaterialToolbar
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class PointsActivity : AppCompatActivity() {

    @Inject
    lateinit var pointsRepository: PointsRepository

    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager

    // Views
    private lateinit var toolbar: MaterialToolbar
    private lateinit var tvTotalPoints: TextView
    private lateinit var tvPointsEarned: TextView
    private lateinit var tvPointsSpent: TextView
    private lateinit var rvTransactions: RecyclerView
    private lateinit var llEmptyState: LinearLayout
    private lateinit var progressBar: ProgressBar

    // Adapter
    private lateinit var transactionsAdapter: PointsTransactionAdapter

    // Data
    private var currentUserId: Long = 0L
    private var currentUserType: UserType = UserType.USER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_points)

        initViews()
        setupToolbar()
        setupRecyclerView()
        loadUserData()
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)
    }

    private fun initViews() {
        toolbar = findViewById(R.id.toolbar)
        tvTotalPoints = findViewById(R.id.tvTotalPoints)
        tvPointsEarned = findViewById(R.id.tvPointsEarned)
        tvPointsSpent = findViewById(R.id.tvPointsSpent)
        rvTransactions = findViewById(R.id.rvTransactions)
        llEmptyState = findViewById(R.id.llEmptyState)
        progressBar = findViewById(R.id.progressBar)
    }

    private fun setupToolbar() {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { onBackPressed() }
    }

    private fun setupRecyclerView() {
        transactionsAdapter = PointsTransactionAdapter { transaction ->
            // Handle transaction click if needed
            Toast.makeText(this, "Transaction: ${transaction.description}", Toast.LENGTH_SHORT).show()
        }
        rvTransactions.layoutManager = LinearLayoutManager(this)
        rvTransactions.adapter = transactionsAdapter
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // Get current user session
                val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
                if (session == null || !session.isLoggedIn) {
                    android.util.Log.w("PointsActivity", "No active session found")
                    finish()
                    return@launch
                }

                currentUserId = session.userId
                currentUserType = session.userType

                // Sync from Firebase first to ensure latest data
                pointsRepository.syncPointsFromFirebase(currentUserId)

                // Load user points
                val pointsResult = pointsRepository.getUserPoints(currentUserId)
                if (pointsResult is com.example.ecosort.data.model.Result.Success) {
                    updatePointsUI(pointsResult.data)
                } else {
                    android.util.Log.e("PointsActivity", "Failed to load points: ${(pointsResult as? com.example.ecosort.data.model.Result.Error)?.exception?.message}")
                    Toast.makeText(this@PointsActivity, "Failed to load points data", Toast.LENGTH_SHORT).show()
                }

                // Load transactions
                val transactionsResult = pointsRepository.getUserTransactions(currentUserId)
                if (transactionsResult is com.example.ecosort.data.model.Result.Success) {
                    transactionsAdapter.submitList(transactionsResult.data)
                    updateEmptyState(transactionsResult.data.isEmpty())
                } else {
                    android.util.Log.e("PointsActivity", "Failed to load transactions: ${(transactionsResult as? com.example.ecosort.data.model.Result.Error)?.exception?.message}")
                    updateEmptyState(true)
                }

            } catch (e: Exception) {
                android.util.Log.e("PointsActivity", "Error loading user data", e)
                Toast.makeText(this@PointsActivity, "Error loading points data", Toast.LENGTH_SHORT).show()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun updatePointsUI(userPoints: UserPoints) {
        tvTotalPoints.text = userPoints.totalPoints.toString()
        tvPointsEarned.text = userPoints.pointsEarned.toString()
        tvPointsSpent.text = userPoints.pointsSpent.toString()
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        llEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        rvTransactions.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showLoading(isLoading: Boolean) {
        progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onBackPressed() {
        super.onBackPressed()
        finish()
    }
}
