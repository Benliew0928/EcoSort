package com.example.ecosort

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class CameraActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_camera)

        val btnSnap = findViewById<Button>(R.id.btnSnap)
        val btnBackCamera = findViewById<Button>(R.id.btnBackCamera)
        val btnRecycleQuick = findViewById<Button>(R.id.btnRecycleQuick)
        val btnSellQuick = findViewById<Button>(R.id.btnSellQuick)

        // Back button - return to main activity
        btnBackCamera.setOnClickListener {
            finish() // This will go back to the previous activity (MainActivity)
        }

        // Snap button - capture and analyze
        btnSnap.setOnClickListener {
            Toast.makeText(this, "Item scanned! Analyzing...", Toast.LENGTH_SHORT).show()
            // For now just open result preview
            startActivity(Intent(this, ResultActivity::class.java))
        }

        // Quick action buttons for last scanned item
        btnRecycleQuick.setOnClickListener {
            Toast.makeText(this, "Plastic Bottle sent to recycle center", Toast.LENGTH_SHORT).show()
        }

        btnSellQuick.setOnClickListener {
            Toast.makeText(this, "Plastic Bottle listed for selling", Toast.LENGTH_SHORT).show()
        }
    }
}
