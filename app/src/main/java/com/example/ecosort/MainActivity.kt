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
import com.example.ecosort.hms.MapActivity // <--- FIX: Added the necessary import for MapActivity
import com.example.ecosort.utils.ResponsiveUtils
import com.example.ecosort.utils.ResponsiveLayoutManager



@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager

    @Inject
    lateinit var firestoreService: FirestoreService

    private var currentUserType: UserType = UserType.USER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main_responsive)

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
            // This now correctly references MapActivity due to the import
            startActivity(Intent(this, MapActivity::class.java))
        }

        btnSell.setOnClickListener {
            startActivity(Intent(this, com.example.ecosort.community.CommunityFeedActivity::class.java))
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
            // This now correctly references MapActivity due to the import
            startActivity(Intent(this, MapActivity::class.java))
        }
        bottomSell.setOnClickListener {
            startActivity(Intent(this, SellActivity::class.java))
        }

        // Community Feeds: View All -> Community
        featuredViewAll.setOnClickListener {
            startActivity(Intent(this, com.example.ecosort.community.CommunityFeedActivity::class.java))
        }

        // Listen to real-time Firestore data for featured community posts (with delay to ensure Firebase is ready)
        lifecycleScope.launch {
            try {
                // Wait a bit for Firebase to initialize
                kotlinx.coroutines.delay(1000)
                firestoreService.getAllCommunityPosts().collect { firebasePosts ->
                    withContext(Dispatchers.Main) {
                        renderCommunityPosts(firebasePosts, featuredContainer)
                    }
                }
            } catch (e: Exception) {
                // If Firebase fails, show empty state
                withContext(Dispatchers.Main) {
                    renderCommunityPosts(emptyList(), featuredContainer)
                }
                android.util.Log.e("MainActivity", "Error loading featured posts", e)
            }
        }
    }

    override fun onBackPressed() {
        // Prevent going back to login screen
        moveTaskToBack(true)
    }

    private fun renderCommunityPosts(posts: List<com.example.ecosort.data.firebase.FirebaseCommunityPost>, container: android.widget.LinearLayout) {
        container.removeAllViews()

        if (posts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = "No community posts yet. Be the first to share!"
                textSize = ResponsiveUtils.getResponsiveTextSize(this@MainActivity, 14f)
                setPadding(
                    ResponsiveUtils.getResponsivePadding(this@MainActivity, 16),
                    ResponsiveUtils.getResponsivePadding(this@MainActivity, 16),
                    ResponsiveUtils.getResponsivePadding(this@MainActivity, 16),
                    ResponsiveUtils.getResponsivePadding(this@MainActivity, 16)
                )
                setTextColor(getColor(R.color.text_secondary))
            }
            container.addView(emptyView)
            return
        }

        posts.take(3).forEach { post ->
            val card = layoutInflater.inflate(R.layout.row_marketplace_item, container, false)

            // Set post details
            card.findViewById<TextView>(R.id.rowTitle).text = post.title
            card.findViewById<TextView>(R.id.rowPrice).text = post.postType // Show post type instead of price

            // Load image using enhanced Glide with proper error handling
            val thumb = card.findViewById<android.widget.ImageView>(R.id.rowThumb)
            val imageUrl = post.imageUrls.firstOrNull()
            val videoUrl = post.videoUrl
            
            when {
                !imageUrl.isNullOrEmpty() && imageUrl != "demo_black_image" -> {
                    // Load image with enhanced error handling
                    android.util.Log.d("MainActivity", "Loading image for home screen: $imageUrl")
                    Glide.with(this)
                        .load(imageUrl)
                        .placeholder(R.drawable.ic_image_placeholder)
                        .error(R.drawable.ic_image_placeholder)
                        .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.drawable.Drawable>() {
                            override fun onResourceReady(
                                resource: android.graphics.drawable.Drawable,
                                transition: com.bumptech.glide.request.transition.Transition<in android.graphics.drawable.Drawable>?
                            ) {
                                android.util.Log.d("MainActivity", "Successfully loaded image for home screen")
                                thumb.setImageDrawable(resource)
                            }
                            
                            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                android.util.Log.e("MainActivity", "Failed to load image for home screen: $imageUrl")
                                thumb.setImageResource(R.drawable.ic_image_placeholder)
                            }
                            
                            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                // Called when the image is cleared
                            }
                        })
                }
                !videoUrl.isNullOrEmpty() -> {
                    // Load video thumbnail with enhanced error handling
                    android.util.Log.d("MainActivity", "Loading video thumbnail for home screen: $videoUrl")
                    Glide.with(this)
                        .asBitmap()
                        .load(videoUrl)
                        .placeholder(R.drawable.ic_video)
                        .error(R.drawable.ic_video)
                        .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                            override fun onResourceReady(
                                resource: android.graphics.Bitmap,
                                transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?
                            ) {
                                android.util.Log.d("MainActivity", "Successfully loaded video thumbnail for home screen")
                                thumb.setImageBitmap(resource)
                            }
                            
                            override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                android.util.Log.w("MainActivity", "Failed to load video thumbnail for home screen, trying thumbnail generation")
                                
                                // Try thumbnail generation as fallback
                                lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val thumbnailUri = com.example.ecosort.utils.VideoThumbnailGenerator.generateThumbnailFromUrl(
                                            this@MainActivity, 
                                            videoUrl
                                        )
                                        
                                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                            if (thumbnailUri != null) {
                                                android.util.Log.d("MainActivity", "Loading generated thumbnail for home screen: $thumbnailUri")
                                                Glide.with(this@MainActivity)
                                                    .load(thumbnailUri)
                                                    .placeholder(R.drawable.ic_video)
                                                    .error(R.drawable.ic_video)
                                                    .into(thumb)
                                            } else {
                                                android.util.Log.w("MainActivity", "Thumbnail generation failed for home screen, using video icon")
                                                thumb.setImageResource(R.drawable.ic_video)
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("MainActivity", "Error in thumbnail generation fallback for home screen", e)
                                        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                            thumb.setImageResource(R.drawable.ic_video)
                                        }
                                    }
                                }
                            }
                            
                            override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                // Called when the image is cleared
                            }
                        })
                }
                else -> {
                    // Show post type icon instead of placeholder
                    val iconRes = when (post.postType) {
                        "TIP" -> R.drawable.ic_lightbulb
                        "ACHIEVEMENT" -> R.drawable.ic_trophy
                        "QUESTION" -> R.drawable.ic_help
                        "EVENT" -> R.drawable.ic_event
                        else -> R.drawable.ic_image_placeholder
                    }
                    android.util.Log.d("MainActivity", "Using post type icon for home screen: ${post.postType}")
                    thumb.setImageResource(iconRes)
                }
            }

            // Set click listener to open community feed
            card.setOnClickListener {
                startActivity(Intent(this, com.example.ecosort.community.CommunityFeedActivity::class.java))
            }

            // Set responsive layout parameters
            val params = android.widget.LinearLayout.LayoutParams(card.layoutParams)
            params.width = ResponsiveUtils.getResponsiveCardWidth(this)
            params.rightMargin = ResponsiveUtils.getResponsivePadding(this, 12)
            card.layoutParams = params
            
            // Apply responsive layout to the card
            ResponsiveUtils.applyResponsiveLayout(card, this)

            container.addView(card)
        }
    }
}
