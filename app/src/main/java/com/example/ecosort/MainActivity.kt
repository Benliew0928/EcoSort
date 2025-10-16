package com.example.ecosort

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.firebase.FirebaseMarketplaceItem
import com.example.ecosort.ui.login.LoginActivity
import com.example.ecosort.utils.DatabaseDebugHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import android.view.View
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.MarketplaceItem
import com.bumptech.glide.Glide
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    @Inject
    lateinit var firestoreService: FirestoreService
    
    private var currentUserType: UserType = UserType.USER
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // userPreferencesManager is now injected via Hilt
        
        // Debug database and user session info
        val debugHelper = DatabaseDebugHelper(this)
        debugHelper.logDatabaseInfo()
        debugHelper.logUserSessionInfo()

        // Initialize UI elements
        val btnScan = findViewById<Button>(R.id.btnScan)
        val btnFindStations = findViewById<Button>(R.id.btnFindStations)
        val btnSell = findViewById<Button>(R.id.btnSell)
        val btnCommunity = findViewById<Button>(R.id.btnCommunity)
        val btnProfile = findViewById<Button>(R.id.btnProfile)
        val tvWelcomeMessage = findViewById<TextView>(R.id.tvWelcomeMessage)
        val featuredViewAll = findViewById<TextView>(R.id.featuredViewAll)
        val featuredContainer = findViewById<android.widget.LinearLayout>(R.id.featuredContainer)
        
        // Bottom navigation buttons
        val bottomHome = findViewById<Button>(R.id.bottomHome)
        val bottomScan = findViewById<Button>(R.id.bottomScan)
        val bottomMap = findViewById<Button>(R.id.bottomMap)
        val bottomSell = findViewById<Button>(R.id.bottomSell)
        
        // Check login session via DataStore
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { userPreferencesManager.getCurrentUser() }
                if (session == null || !session.isLoggedIn) {
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }
                
                currentUserType = session.userType
                tvWelcomeMessage.text = "Welcome back, ${session.username}!"
                
                // Handle user type specific functionality
                if (currentUserType == UserType.ADMIN) {
                    btnProfile.setOnClickListener {
                        startActivity(Intent(this@MainActivity, AdminActivity::class.java))
                    }
                } else {
                    btnProfile.setOnClickListener {
                        startActivity(Intent(this@MainActivity, UserProfileActivity::class.java))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error checking user session", e)
                // If there's an error, redirect to login
                startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                finish()
            }
        }
        
        // Main navigation
        btnScan.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        
        btnFindStations.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        
        btnSell.setOnClickListener {
            startActivity(Intent(this, SellActivity::class.java))
        }
        
        btnCommunity.setOnClickListener {
            try {
                startActivity(Intent(this, com.example.ecosort.chat.ChatListActivity::class.java))
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error starting ChatListActivity", e)
                Toast.makeText(this, "Chat feature coming soon!", Toast.LENGTH_SHORT).show()
            }
        }
        
        // Bottom navigation
        bottomHome.setOnClickListener {
            Toast.makeText(this, "You're already on the home screen", Toast.LENGTH_SHORT).show()
        }
        bottomScan.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }
        bottomMap.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }
        bottomSell.setOnClickListener {
            startActivity(Intent(this, SellActivity::class.java))
        }

        // Featured: View All -> Marketplace
        featuredViewAll.setOnClickListener {
            startActivity(Intent(this, com.example.ecosort.marketplace.MarketplaceActivity::class.java))
        }

        // Listen to real-time Firestore data for featured items (with delay to ensure Firebase is ready)
        lifecycleScope.launch {
            try {
                // Wait a bit for Firebase to initialize
                kotlinx.coroutines.delay(1000)
                firestoreService.getFeaturedMarketplaceItems().collect { firebaseItems ->
                    withContext(Dispatchers.Main) {
                        renderFeaturedItems(firebaseItems, featuredContainer)
                    }
                }
            } catch (e: Exception) {
                // If Firebase fails, show empty state
                withContext(Dispatchers.Main) {
                    renderFeaturedItems(emptyList(), featuredContainer)
                }
                android.util.Log.e("MainActivity", "Error loading featured items", e)
            }
        }
    }
    
    override fun onBackPressed() {
        // Prevent going back to login screen
        moveTaskToBack(true)
    }

    private fun renderFeaturedItems(items: List<FirebaseMarketplaceItem>, container: android.widget.LinearLayout) {
        container.removeAllViews()
        
        if (items.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No featured items available"
                textSize = 14f
                setPadding(16, 16, 16, 16)
            }
            container.addView(emptyView)
            return
        }
        
        items.take(3).forEach { item ->
            val card = layoutInflater.inflate(R.layout.row_marketplace_item, container, false)
            
            // Set item details
            card.findViewById<TextView>(R.id.rowTitle).text = item.title
            card.findViewById<TextView>(R.id.rowPrice).text = "RM %.2f".format(item.price)
            
            // Load image using Glide
            val thumb = card.findViewById<android.widget.ImageView>(R.id.rowThumb)
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
            card.setOnClickListener {
                startActivity(Intent(this, com.example.ecosort.marketplace.MarketplaceDetailActivity::class.java).apply {
                    putExtra("item_id", item.id)
                    putExtra("item_title", item.title)
                    putExtra("item_price", item.price)
                    putExtra("item_description", item.description)
                    putExtra("item_image_url", item.imageUrl)
                    putExtra("item_owner", item.ownerName)
                })
            }
            
            // Set layout parameters
            val params = android.widget.LinearLayout.LayoutParams(card.layoutParams)
            params.width = resources.getDimensionPixelSize(R.dimen.featured_item_card_width)
            params.rightMargin = resources.getDimensionPixelSize(R.dimen.featured_item_card_height)
            card.layoutParams = params
            
            container.addView(card)
        }
    }
}
