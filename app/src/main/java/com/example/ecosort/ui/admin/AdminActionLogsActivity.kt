package com.example.ecosort.ui.admin

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.model.AdminAction
import com.example.ecosort.data.repository.AdminRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdminActionLogsActivity : AppCompatActivity(), AdminActionLogsAdapter.OnActionLogListener {

    @Inject
    lateinit var adminRepository: AdminRepository

    private lateinit var btnBack: ImageButton
    private lateinit var tvTitle: TextView
    private lateinit var btnRefresh: Button
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AdminActionLogsAdapter
    private var actionLogs: List<AdminAction> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin_action_logs)

        setupUI()
        setupRecyclerView()
        setupListeners()
        loadActionLogs()
    }

    override fun onResume() {
        super.onResume()
        // Refresh logs when activity becomes visible
        loadActionLogs()
    }

    private fun setupUI() {
        btnBack = findViewById(R.id.btnBackAdminActionLogs)
        tvTitle = findViewById(R.id.tvAdminActionLogsTitle)
        btnRefresh = findViewById(R.id.btnRefreshActionLogs)
        recyclerView = findViewById(R.id.recyclerViewActionLogs)
    }

    private fun setupRecyclerView() {
        adapter = AdminActionLogsAdapter(this)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }

    private fun setupListeners() {
        btnBack.setOnClickListener { finish() }
        btnRefresh.setOnClickListener { loadActionLogs() }
    }

    private fun loadActionLogs() {
        lifecycleScope.launch {
            when (val result = adminRepository.getAllAdminActions()) {
                is com.example.ecosort.data.model.Result.Success -> {
                    actionLogs = result.data
                    adapter.submitList(actionLogs)
                }
                is com.example.ecosort.data.model.Result.Error -> {
                    Toast.makeText(this@AdminActionLogsActivity, "Error loading action logs: ${result.exception.message}", Toast.LENGTH_LONG).show()
                }
                else -> {
                    Toast.makeText(this@AdminActionLogsActivity, "Unexpected error occurred", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onViewActionDetails(action: AdminAction) {
        // Show action details dialog
        ActionLogDetailDialog.newInstance(action).show(supportFragmentManager, "ActionLogDetailDialog")
    }
}
