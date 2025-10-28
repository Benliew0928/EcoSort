package com.example.ecosort.ui.admin

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.data.repository.AdminRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UserManagementActivity : AppCompatActivity(), UserManagementAdapter.OnUserActionListener {

    @Inject
    lateinit var adminRepository: AdminRepository

    private lateinit var btnBack: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UserManagementAdapter
    private var users: List<User> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        setupUI()
        setupRecyclerView()
        setupListeners()
        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        // Refresh users when returning to this activity
        loadUsers()
    }

    private fun setupUI() {
        btnBack = findViewById(R.id.btnBackUserManagement)
        btnRefresh = findViewById(R.id.btnRefreshUsers)
        tvTitle = findViewById(R.id.tvUserManagementTitle)
        recyclerView = findViewById(R.id.recyclerViewUsers)
    }

    private fun setupRecyclerView() {
        adapter = UserManagementAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnRefresh.setOnClickListener { 
            loadUsers()
            Toast.makeText(this, "Refreshing users...", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            try {
                // First sync users from Firebase to ensure we have latest data
                android.util.Log.d("UserManagementActivity", "Syncing users from Firebase...")
                val syncResult = adminRepository.syncAllUsersFromFirebase()
                if (syncResult is com.example.ecosort.data.model.Result.Success) {
                    android.util.Log.d("UserManagementActivity", "Synced ${syncResult.data} users from Firebase")
                } else {
                    android.util.Log.w("UserManagementActivity", "Failed to sync users from Firebase: ${syncResult}")
                }
                
                // Then load users from local database
                when (val result = adminRepository.getAllUsers()) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        users = result.data
                        adapter.submitList(users)
                        android.util.Log.d("UserManagementActivity", "Loaded ${users.size} users")
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        Toast.makeText(this@UserManagementActivity, "Error loading users: ${result.exception.message}", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        Toast.makeText(this@UserManagementActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserManagementActivity", "Error loading users", e)
                Toast.makeText(this@UserManagementActivity, "Error loading users: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDeleteUser(user: User) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to delete user '${user.username}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteUser(user)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onViewUserDetails(user: User) {
        try {
            android.util.Log.d("UserManagementActivity", "Showing details for user: ${user.username}")
            // Show user details dialog
            UserDetailDialog.newInstance(user).show(supportFragmentManager, "UserDetailDialog")
        } catch (e: Exception) {
            android.util.Log.e("UserManagementActivity", "Error showing user details", e)
            Toast.makeText(this, "Error showing user details: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteUser(user: User) {
        lifecycleScope.launch {
            // For now, we'll just log the action since we can't modify the User table
            // In a real implementation, you might want to add a "deleted" flag or move to archive table
            when (val result = adminRepository.deleteUser(1L, user.id)) { // Using adminId = 1L as placeholder
                is com.example.ecosort.data.model.Result.Success -> {
                    Toast.makeText(this@UserManagementActivity, "User '${user.username}' deleted successfully", Toast.LENGTH_SHORT).show()
                    loadUsers() // Refresh the list
                }
                is com.example.ecosort.data.model.Result.Error -> {
                    Toast.makeText(this@UserManagementActivity, "Error deleting user: ${result.exception.message}", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this@UserManagementActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
