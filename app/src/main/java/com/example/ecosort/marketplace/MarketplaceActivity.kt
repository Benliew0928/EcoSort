package com.example.ecosort.marketplace

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.R
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.MarketplaceItem
import com.example.ecosort.data.model.WasteCategory
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.FirebaseMarketplaceItem
import com.bumptech.glide.Glide
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MarketplaceActivity : AppCompatActivity() {
    
    @Inject
    lateinit var firestoreService: FirestoreService
    
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_marketplace)

		val listContainer = findViewById<LinearLayout>(R.id.marketplace_list_container)

        // Listen to real-time Firestore data
        lifecycleScope.launch {
            try {
                firestoreService.getAllMarketplaceItems().collect { firebaseItems ->
                    withContext(Dispatchers.Main) {
                        renderMarketplaceItems(firebaseItems, listContainer)
                    }
                }
            } catch (e: Exception) {
                // If Firebase fails, show empty state
                withContext(Dispatchers.Main) {
                    renderMarketplaceItems(emptyList(), listContainer)
                }
                android.util.Log.e("MarketplaceActivity", "Error loading marketplace items", e)
            }
        }
	}
    
    private fun renderMarketplaceItems(items: List<FirebaseMarketplaceItem>, container: LinearLayout) {
        container.removeAllViews()
        
        if (items.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No items available in marketplace"
                textSize = 16f
                setPadding(32, 32, 32, 32)
            }
            container.addView(emptyView)
            return
        }
        
        items.forEach { item ->
            val row = layoutInflater.inflate(R.layout.row_marketplace_item, container, false)
            
            // Set item details
            row.findViewById<TextView>(R.id.rowTitle).text = item.title
            row.findViewById<TextView>(R.id.rowPrice).text = "RM %.2f".format(item.price)
            
            // Load image using Glide
            val thumb = row.findViewById<ImageView>(R.id.rowThumb)
            if (item.imageUrl.isNotEmpty()) {
                Glide.with(this)
                    .load(item.imageUrl)
                    .placeholder(R.drawable.ic_placeholder)
                    .error(R.drawable.ic_placeholder)
                    .into(thumb)
            } else {
                thumb.setImageResource(R.drawable.ic_placeholder)
            }
            
            // Set click listener
            row.setOnClickListener {
                startActivity(Intent(this@MarketplaceActivity, MarketplaceDetailActivity::class.java).apply {
                    putExtra("item_id", item.id)
                    putExtra("item_title", item.title)
                    putExtra("item_price", item.price)
                    putExtra("item_description", item.description)
                    putExtra("item_image_url", item.imageUrl)
                    putExtra("item_owner", item.ownerName)
                })
            }
            
            container.addView(row)
        }
    }
}
