package com.example.ecosort.hms

import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide // Import Glide
import com.example.ecosort.R

class BinDetailActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bin_detail) // You need to create this layout

        val imageUrl = intent.getStringExtra("EXTRA_PHOTO_URL")

        if (imageUrl.isNullOrEmpty()) {
            Toast.makeText(this, "Bin photo URL is missing.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val imageView = findViewById<ImageView>(R.id.imgBinPhoto)

        // Load image using Glide
        Glide.with(this)
            .load(imageUrl)
            .placeholder(R.drawable.ic_placeholder) // Use a placeholder drawable if you have one
            .error(R.drawable.app_logo)
            .into(imageView)
    }
}