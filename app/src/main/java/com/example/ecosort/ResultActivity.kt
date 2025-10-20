package com.example.ecosort

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class ResultActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val btnRecycle = findViewById<Button>(R.id.btnRecycle)
        val btnSell = findViewById<Button>(R.id.btnSell)
        val btnBackResult = findViewById<Button>(R.id.btnBackResult)

        // Back button
        btnBackResult.setOnClickListener {
            finish() // Return to previous activity
        }

        // Action buttons
        btnRecycle.setOnClickListener {
            Toast.makeText(this, "Item sent to Recycle Center", Toast.LENGTH_SHORT).show()
            // Navigate back to main activity after action
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
        
        btnSell.setOnClickListener {
            Toast.makeText(this, "Item listed for Selling", Toast.LENGTH_SHORT).show()
            // Navigate to sell activity to complete the listing
            startActivity(Intent(this, SellActivity::class.java))
            finish()
        }
    }

    // Handle back button press
    override fun onBackPressed() {
        super.onBackPressed()
        finish() // Return to previous activity
    }
}
