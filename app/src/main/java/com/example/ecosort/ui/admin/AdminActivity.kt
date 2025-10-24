package com.example.ecosort.ui.admin

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.ecosort.R
import com.example.ecosort.utils.SecurityManager
import com.example.ecosort.data.repository.AdminRepository
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AdminActivity : AppCompatActivity() {

    @Inject
    lateinit var adminRepository: AdminRepository

    private lateinit var btnPasskeySettings: Button
    private lateinit var btnUserManagement: Button
    private lateinit var btnAdminManagement: Button
    private lateinit var btnActionLogs: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_admin)

        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        btnPasskeySettings = findViewById(R.id.btnPasskeySettings)
        btnUserManagement = findViewById(R.id.btnUserManagement)
        btnAdminManagement = findViewById(R.id.btnAdminManagement)
        btnActionLogs = findViewById(R.id.btnActionLogs)
    }

    private fun setupListeners() {
        btnPasskeySettings.setOnClickListener {
            showPasskeyManagementDialog()
        }

        btnUserManagement.setOnClickListener {
            startActivity(Intent(this, UserManagementActivity::class.java))
        }

        btnAdminManagement.setOnClickListener {
            startActivity(Intent(this, AdminManagementActivity::class.java))
        }

        btnActionLogs.setOnClickListener {
            startActivity(Intent(this, AdminActionLogsActivity::class.java))
        }
    }

    // ==================== PASSKEY MANAGEMENT ====================

    private fun showPasskeyManagementDialog() {
        val builder = AlertDialog.Builder(this)
        val inflater = layoutInflater
        val view = inflater.inflate(R.layout.dialog_passkey_management, null)

        val tvPasskeyStatus = view.findViewById<TextView>(R.id.tvPasskeyStatus)
        val etCurrentPasskey = view.findViewById<EditText>(R.id.etCurrentPasskey)
        val etNewPasskey = view.findViewById<EditText>(R.id.etNewPasskey)
        val etConfirmPasskey = view.findViewById<EditText>(R.id.etConfirmPasskey)

        // Set passkey status
        val isCustomized = SecurityManager.isAdminPasskeyCustomized()
        tvPasskeyStatus.text = if (isCustomized) {
            "Current passkey is customized"
        } else {
            "Using default passkey (8888)"
        }

        builder.setView(view)
            .setTitle("Admin Passkey Management")
            .setPositiveButton("Update", null)
            .setNegativeButton("Reset to Default", null)
            .setNeutralButton("Cancel") { dialog, _ -> dialog.dismiss() }

        val dialog = builder.create()

        dialog.setOnShowListener {
            val updateButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            val resetButton = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)

            updateButton.setOnClickListener {
                val currentPasskey = etCurrentPasskey.text.toString()
                val newPasskey = etNewPasskey.text.toString()
                val confirmPasskey = etConfirmPasskey.text.toString()

                updatePasskey(currentPasskey, newPasskey, confirmPasskey)
                dialog.dismiss()
            }

            resetButton.setOnClickListener {
                resetPasskeyToDefault()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun updatePasskey(currentPasskey: String, newPasskey: String, confirmPasskey: String) {
        // Validate current passkey
        if (!SecurityManager.verifyAdminPasskey(currentPasskey)) {
            Toast.makeText(this, "Current passkey is incorrect", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate new passkey
        if (newPasskey.length != 4 || !newPasskey.all { it.isDigit() }) {
            Toast.makeText(this, "New passkey must be 4 digits", Toast.LENGTH_SHORT).show()
            return
        }

        // Validate confirmation
        if (newPasskey != confirmPasskey) {
            Toast.makeText(this, "New passkey and confirmation do not match", Toast.LENGTH_SHORT).show()
            return
        }

        // Update passkey
        if (SecurityManager.setAdminPasskey(newPasskey)) {
            // Log the passkey change action
            lifecycleScope.launch {
                adminRepository.logAdminAction(1L, "CHANGE_PASSKEY", null, "Admin passkey updated")
            }
            Toast.makeText(this, "Admin passkey updated successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to update admin passkey", Toast.LENGTH_SHORT).show()
        }
    }

    private fun resetPasskeyToDefault() {
        AlertDialog.Builder(this)
            .setTitle("Reset Passkey")
            .setMessage("Are you sure you want to reset the admin passkey to the default (8888)?")
            .setPositiveButton("Reset") { _, _ ->
                if (SecurityManager.resetAdminPasskeyToDefault()) {
                    // Log the passkey reset action
                    lifecycleScope.launch {
                        adminRepository.logAdminAction(1L, "CHANGE_PASSKEY", null, "Admin passkey reset to default")
                    }
                    Toast.makeText(this, "Admin passkey reset to default", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Failed to reset admin passkey", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
