package com.example.ecosort.marketplace

import android.os.Bundle
import android.widget.TextView
import android.widget.ImageView
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.R
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.MarketplaceItem
import com.example.ecosort.data.firebase.FirestoreService
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MarketplaceDetailActivity : AppCompatActivity() {
    
    @Inject
    lateinit var firestoreService: FirestoreService
    
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_marketplace_detail)

        val title = findViewById<TextView>(R.id.detailTitle)
		val price = findViewById<TextView>(R.id.detailPrice)
		val desc = findViewById<TextView>(R.id.detailDesc)
        val image = findViewById<ImageView>(R.id.detailImage)

        // Get data from intent (passed from MarketplaceActivity)
        val itemId = intent.getStringExtra("item_id")
        val itemTitle = intent.getStringExtra("item_title")
        val itemPrice = intent.getDoubleExtra("item_price", 0.0)
        val itemDescription = intent.getStringExtra("item_description")
        val itemImageUrl = intent.getStringExtra("item_image_url")
        val itemOwner = intent.getStringExtra("item_owner")

        if (itemId.isNullOrEmpty()) {
            finish()
            return
        }

        // Display the item details
        title.text = itemTitle ?: "Unknown Item"
        price.text = "RM %.2f".format(itemPrice)
        desc.text = itemDescription ?: "No description available"
        
        // Load image using Glide
        if (!itemImageUrl.isNullOrEmpty()) {
            Glide.with(this)
                .load(itemImageUrl)
                .placeholder(R.drawable.ic_placeholder)
                .error(R.drawable.ic_placeholder)
                .into(image)
        } else {
            image.setImageResource(R.drawable.ic_placeholder)
        }
	}
}
