package com.example.ecosort

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast

class SellActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sell)

        val name = findViewById<EditText>(R.id.txtItemName)
        val price = findViewById<EditText>(R.id.txtItemPrice)
        val desc = findViewById<EditText>(R.id.txtItemDesc)
        val submit = findViewById<Button>(R.id.btnSubmitSell)
        val btnBackSell = findViewById<Button>(R.id.btnBackSell)

        // Back button
        btnBackSell.setOnClickListener {
            finish() // Return to previous activity
        }

        submit.setOnClickListener {
            // Validate form inputs
            if (name.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Please enter item name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            if (price.text.toString().trim().isEmpty()) {
                Toast.makeText(this, "Please enter item price", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Show success message
            Toast.makeText(this,
                "Item '${name.text}' submitted successfully!",
                Toast.LENGTH_SHORT).show()
            
            // Navigate back to main activity after successful submission
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    // Handle back button press
    override fun onBackPressed() {
        super.onBackPressed()
        finish() // Return to previous activity
    }
}
