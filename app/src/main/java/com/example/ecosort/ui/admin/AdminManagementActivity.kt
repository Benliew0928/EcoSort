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
import com.example.ecosort.data.model.Admin
import com.example.ecosort.data.repository.AdminRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdminManagementActivity : AppCompatActivity(), AdminManagementAdapter.OnAdminActionListener {

    @Inject
    lateinit var adminRepository: AdminRepository

    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdminManagementAdapter
    private var admins: List<Admin> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_management)

        setupUI()
        setupRecyclerView()
        setupListeners()
        loadAdmins()
    }

    override fun onResume() {
        super.onResume()
        // Refresh admins when returning to this activity
        loadAdmins()
    }

    private fun setupUI() {
        btnBack = findViewById(R.id.btnBackAdminManagement)
        tvTitle = findViewById(R.id.tvAdminManagementTitle)
        recyclerView = findViewById(R.id.recyclerViewAdmins)
    }

    private fun setupRecyclerView() {
        adapter = AdminManagementAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
    }

    private fun loadAdmins() {
        lifecycleScope.launch {
            when (val result = adminRepository.getAllAdmins()) {
                is com.example.ecosort.data.model.Result.Success -> {
                    admins = result.data
                    adapter.submitList(admins)
                }
                is com.example.ecosort.data.model.Result.Error -> {
                    Toast.makeText(this@AdminManagementActivity, "Error loading admins: ${result.exception.message}", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this@AdminManagementActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDeleteAdmin(admin: Admin) {
        // Show confirmation dialog
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Delete Admin")
            .setMessage("Are you sure you want to delete admin '${admin.username}'? This action cannot be undone.")
            .setPositiveButton("Delete") { _, _ ->
                deleteAdmin(admin)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onViewAdminDetails(admin: Admin) {
        try {
            android.util.Log.d("AdminManagementActivity", "Showing details for admin: ${admin.username}")
            // Show admin details dialog
            AdminDetailDialog.newInstance(admin).show(supportFragmentManager, "AdminDetailDialog")
        } catch (e: Exception) {
            android.util.Log.e("AdminManagementActivity", "Error showing admin details", e)
            Toast.makeText(this, "Error showing admin details: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteAdmin(admin: Admin) {
        lifecycleScope.launch {
            // For now, we'll just log the action since we can't modify the Admin table directly
            // In a real implementation, you might want to add a "deleted" flag or move to archive table
            when (val result = adminRepository.deleteAdmin(1L, admin.id)) { // Using adminId = 1L as placeholder
                is com.example.ecosort.data.model.Result.Success -> {
                    Toast.makeText(this@AdminManagementActivity, "Admin '${admin.username}' deleted successfully", Toast.LENGTH_SHORT).show()
                    loadAdmins() // Refresh the list
                }
                is com.example.ecosort.data.model.Result.Error -> {
                    Toast.makeText(this@AdminManagementActivity, "Error deleting admin: ${result.exception.message}", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this@AdminManagementActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
