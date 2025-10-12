package com.example.ecosort

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.Toast

class MapActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        val btnBackMap = findViewById<Button>(R.id.btnBackMap)
        val btnNav1 = findViewById<Button>(R.id.btnNav1)
        val btnNav2 = findViewById<Button>(R.id.btnNav2)

        // Back button - return to main activity
        btnBackMap.setOnClickListener {
            finish() // This will go back to the previous activity (MainActivity)
        }

        // Navigation buttons for recycling stations
        btnNav1.setOnClickListener {
            // Open Google Maps with directions to Kuala Green Center
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Kuala+Green+Center"))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Kuala Green Center - 1.2 km away", Toast.LENGTH_SHORT).show()
            }
        }

        btnNav2.setOnClickListener {
            // Open Google Maps with directions to Petaling Recycle Hub
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=Petaling+Recycle+Hub"))
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                Toast.makeText(this, "Petaling Recycle Hub - 2.4 km away", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Handle back button press
    override fun onBackPressed() {
        super.onBackPressed()
        finish() // Return to previous activity
    }
}
