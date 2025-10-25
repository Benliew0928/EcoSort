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
import com.example.ecosort.ui.login.LoginActivity
import com.example.ecosort.utils.DatabaseDebugHelper
import com.example.ecosort.social.FollowingListActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import android.view.View
import com.example.ecosort.data.local.EcoSortDatabase
import com.bumptech.glide.Glide
import javax.inject.Inject
import com.example.ecosort.hms.MapActivity
import com.example.ecosort.utils.ResponsiveUtils
import com.example.ecosort.utils.VideoThumbnailGenerator
import kotlinx.coroutines.CoroutineScope
import androidx.cardview.widget.CardView
import android.widget.LinearLayout
import com.example.ecosort.ui.admin.AdminPasskeyDialog



@AndroidEntryPoint
class MainActivity : AppCompatActivity(), AdminPasskeyDialog.AdminPasskeyListener {

    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager

    @Inject
    lateinit var firestoreService: FirestoreService


    @Inject
    lateinit var communityRepository: com.example.ecosort.data.repository.CommunityRepository

    @Inject
    lateinit var userRepository: com.example.ecosort.data.repository.UserRepository

    @Inject
    lateinit var adminRepository: com.example.ecosort.data.repository.AdminRepository
    
    @Inject
    lateinit var profileImageManager: com.example.ecosort.utils.ProfileImageManager

    private var currentUserType: UserType = UserType.USER

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Apply saved language synchronously before setting content view
        applySavedLanguageSync()

        setContentView(R.layout.activity_main_responsive)

        // userPreferencesManager is now injected via Hilt

        // Debug database and user session info
        val debugHelper = DatabaseDebugHelper(this)
        debugHelper.logDatabaseInfo()
        debugHelper.logUserSessionInfo()

        // --- Initialize UI elements ---

        // Header
        val btnProfile = findViewById<android.widget.ImageView>(R.id.btnProfile)
        val tvWelcomeMessage = findViewById<TextView>(R.id.tvWelcomeMessage)

        // Quick Actions (Buttons inside CardViews)
        val btnScanContainer = findViewById<LinearLayout>(R.id.btnScanContainer)
        val btnFindStationsContainer = findViewById<LinearLayout>(R.id.btnFindStationsContainer)

        // NEW: Placeholder container for the Friends button/card
        val btnFriendsContainer = findViewById<LinearLayout>(R.id.btnFriendsContainer)

        // Stats
        val tvItemsRecycledCount = findViewById<TextView>(R.id.tvItemsRecycledCount)
        val tvPointsCount = findViewById<TextView>(R.id.tvPointsCount)

        // Community Feed
        val featuredViewAll = findViewById<TextView>(R.id.featuredViewAll)
        val btnFollowingList = findViewById<TextView>(R.id.btnFollowingList)
        val featuredContainer = findViewById<android.widget.LinearLayout>(R.id.featuredContainer)

        // Bottom navigation buttons
        val bottomHome = findViewById<Button>(R.id.bottomHome)
        val bottomCommunity = findViewById<Button>(R.id.bottomCommunity)
        val bottomChat = findViewById<Button>(R.id.bottomChat)
        val bottomProfileNav = findViewById<Button>(R.id.bottomProfile)

        // Check login session via DataStore
        lifecycleScope.launch {
            try {
                val session = withContext(Dispatchers.IO) { userPreferencesManager.getCurrentUser() }
                if (session == null || !session.isLoggedIn) {
                    // Quick database check - log how many users exist
                    val userCount = withContext(Dispatchers.IO) {
                        try {
                            val database = com.example.ecosort.data.local.EcoSortDatabase.getDatabase(this@MainActivity)
                            val userDao = database.userDao()
                            userDao.getAllUsers().size
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Database error: ${e.message}")
                            0
                        }
                    }
                    android.util.Log.d("MainActivity", "No active session. Users in database: $userCount")

                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                    return@launch
                }

                currentUserType = session.userType
                // Get the welcome message and replace the placeholder with actual username
                val welcomeText = getString(R.string.welcome_message)
                val personalizedWelcome = when {
                    welcomeText.contains("User!") -> welcomeText.replace("User!", session.username)
                    welcomeText.contains("用户！") -> welcomeText.replace("用户！", session.username)
                    welcomeText.contains("Pengguna!") -> welcomeText.replace("Pengguna!", session.username)
                    else -> welcomeText.replace("User", session.username)
                }
                tvWelcomeMessage.text = personalizedWelcome

                // Load user profile picture
                loadUserProfilePicture(btnProfile)

                // Handle user type specific functionality
                if (currentUserType == UserType.ADMIN) {
                    btnProfile.setOnClickListener {
                        // Show admin passkey dialog before accessing admin panel
                        showAdminPasskeyDialog()
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

        // --- Main Navigation Clicks (Quick Actions) ---

        // 1. Scan Waste
        btnScanContainer.setOnClickListener {
            startActivity(Intent(this, ObjectDetectionActivity::class.java))
        }

        // 2. Find Stations
        btnFindStationsContainer.setOnClickListener {
            startActivity(Intent(this, MapActivity::class.java))
        }

        // 3. Friends (The Empty Card in the design image)
        btnFriendsContainer.setOnClickListener {
            // Your friend's logic: Starts the Friends List Activity
            startActivity(Intent(this, com.example.ecosort.friends.FriendsListActivity::class.java))
        }

        // --- Bottom Navigation Clicks ---

        bottomHome.setOnClickListener {
            Toast.makeText(this, "You're already on the home screen", Toast.LENGTH_SHORT).show()
        }
        bottomCommunity.setOnClickListener {
            startActivity(Intent(this, com.example.ecosort.community.CommunityFeedActivity::class.java))
        }
        bottomChat.setOnClickListener {
            startActivity(Intent(this, com.example.ecosort.chat.ChatListActivity::class.java))
        }
        bottomProfileNav.setOnClickListener {
            startActivity(Intent(this, UserProfileActivity::class.java))
        }

        // Community Feeds: View All -> Community
        featuredViewAll.setOnClickListener {
            startActivity(Intent(this, com.example.ecosort.community.CommunityFeedActivity::class.java))
        }

        // Following List -> Following List Activity
        btnFollowingList.setOnClickListener {
            startActivity(Intent(this, FollowingListActivity::class.java))
        }

        // Load community posts from local database (with updated profile pictures)
        lifecycleScope.launch {
            try {
                // Wait a bit for database to initialize
                kotlinx.coroutines.delay(1000)

                // Sync user profiles from Firebase to local database
                try {
                    val syncResult = userRepository.syncAllUsersFromFirebase()
                    if (syncResult is com.example.ecosort.data.model.Result.Success) {
                        android.util.Log.d("MainActivity", "Successfully synced ${syncResult.data} users from Firebase")
                    } else {
                        android.util.Log.w("MainActivity", "Failed to sync users from Firebase: ${(syncResult as com.example.ecosort.data.model.Result.Error).exception.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Error syncing users from Firebase: ${e.message}")
                }

                // Sync admin profiles from Firebase to local database
                try {
                    val adminSyncResult = adminRepository.syncAllAdminsFromFirebase()
                    if (adminSyncResult is com.example.ecosort.data.model.Result.Success) {
                        android.util.Log.d("MainActivity", "Successfully synced ${adminSyncResult.data} admins from Firebase")
                    } else {
                        android.util.Log.w("MainActivity", "Failed to sync admins from Firebase: ${(adminSyncResult as com.example.ecosort.data.model.Result.Error).exception.message}")
                    }
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Error syncing admins from Firebase: ${e.message}")
                }

                // Update existing posts with profile pictures (one-time fix for old posts)
                try {
                    communityRepository.updateExistingPostsWithProfilePictures()
                    android.util.Log.d("MainActivity", "Updated existing posts with profile pictures")
                } catch (e: Exception) {
                    android.util.Log.w("MainActivity", "Failed to update existing posts with profile pictures: ${e.message}")
                }

                // Load following posts from local database (which has updated profile pictures)
                val followingPostsFlow = communityRepository.getFollowingPosts()
                followingPostsFlow.collect { followingPosts ->
                    withContext(Dispatchers.Main) {
                        // Check if activity is still valid before rendering
                        if (!isFinishing && !isDestroyed) {
                            renderCommunityPostsFromLocal(followingPosts, featuredContainer)
                        }
                    }
                }
            } catch (e: Exception) {
                // If database fails, show empty state
                withContext(Dispatchers.Main) {
                    // Check if activity is still valid before rendering
                    if (!isFinishing && !isDestroyed) {
                        renderCommunityPostsFromLocal(emptyList(), featuredContainer)
                    }
                }
                android.util.Log.e("MainActivity", "Error loading featured posts", e)
            }
        }

        // Load user stats data (Placeholder logic)
        lifecycleScope.launch {
            // Placeholder values set here. Replace with actual data fetching logic later.
            tvItemsRecycledCount.text = "206"
            tvPointsCount.text = "206"
        }
    }

    override fun onResume() {
        super.onResume()
        // Check if language has changed and apply it
        applySavedLanguage()
        
        // Refresh profile picture when returning to MainActivity
        // This ensures the image is updated if it was changed in other activities
        val btnProfile = findViewById<android.widget.ImageView>(R.id.btnProfile)
        loadUserProfilePicture(btnProfile)
    }

    override fun onBackPressed() {
        // Prevent going back to login screen
        moveTaskToBack(true)
    }

    private fun applySavedLanguageSync() {
        try {
            // Use runBlocking to make this synchronous
            val preferences = runBlocking(Dispatchers.IO) {
                userPreferencesManager.getUserPreferences()
            }

            // Check current language
            val currentLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                resources.configuration.locale
            }

            val currentLanguage = when (currentLocale.language) {
                "zh" -> "zh"
                "ms" -> "ms"
                else -> "en"
            }

            // Only apply if different
            if (preferences.language != currentLanguage) {
                val locale = when (preferences.language) {
                    "zh" -> java.util.Locale("zh", "CN")
                    "ms" -> java.util.Locale("ms", "MY")
                    else -> java.util.Locale("en", "US")
                }

                // Set locale globally
                java.util.Locale.setDefault(locale)

                val configuration = android.content.res.Configuration(resources.configuration)
                configuration.setLocale(locale)

                // Apply to current context
                resources.updateConfiguration(configuration, resources.displayMetrics)

                // Apply to application context for global effect
                applicationContext.resources.updateConfiguration(configuration, applicationContext.resources.displayMetrics)

                android.util.Log.d("MainActivity", "Applied saved language: ${preferences.language}")

            } else {
                android.util.Log.d("MainActivity", "Language already correct: ${preferences.language}")
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Error applying saved language", e)
        }
    }

    private fun applySavedLanguage() {
        lifecycleScope.launch {
            try {
                val preferences = withContext(Dispatchers.IO) {
                    userPreferencesManager.getUserPreferences()
                }

                // Check current language
                val currentLocale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    resources.configuration.locales[0]
                } else {
                    @Suppress("DEPRECATION")
                    resources.configuration.locale
                }

                val currentLanguage = when (currentLocale.language) {
                    "zh" -> "zh"
                    "ms" -> "ms"
                    else -> "en"
                }

                // Only apply if different
                if (preferences.language != currentLanguage) {
                    val locale = when (preferences.language) {
                        "zh" -> java.util.Locale("zh", "CN")
                        "ms" -> java.util.Locale("ms", "MY")
                        else -> java.util.Locale("en", "US")
                    }

                    // Set locale globally
                    java.util.Locale.setDefault(locale)

                    val configuration = android.content.res.Configuration(resources.configuration)
                    configuration.setLocale(locale)

                    // Apply to current context
                    resources.updateConfiguration(configuration, resources.displayMetrics)

                    // Apply to application context for global effect
                    applicationContext.resources.updateConfiguration(configuration, applicationContext.resources.displayMetrics)

                    android.util.Log.d("MainActivity", "Applied saved language: ${preferences.language}")

                    // Recreate the activity to apply language changes to the UI
                    if (!isFinishing && !isDestroyed) {
                        recreate()
                    }

                } else {
                    android.util.Log.d("MainActivity", "Language already correct: ${preferences.language}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error applying saved language", e)
            }
        }
    }


    private fun renderCommunityPosts(posts: List<com.example.ecosort.data.firebase.FirebaseCommunityPost>, container: android.widget.LinearLayout) {
        container.removeAllViews()

        if (posts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.no_community_posts)
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
            val card = layoutInflater.inflate(R.layout.item_community_post, container, false)

            // Set post details
            card.findViewById<TextView>(R.id.textPostTitle).text = post.title
            card.findViewById<TextView>(R.id.textPostType).text = post.postType // Show post type instead of price

            // Set author details
            card.findViewById<TextView>(R.id.textAuthorName).text = post.authorName

            // Load author profile picture
            val authorProfileImage = card.findViewById<android.widget.ImageView>(R.id.imageAuthorProfile)
            if (!post.authorAvatar.isNullOrBlank()) {
                Glide.with(this)
                    .load(post.authorAvatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person_24)
                    .error(R.drawable.ic_person_24)
                    .into(authorProfileImage)
            } else {
                authorProfileImage.setImageResource(R.drawable.ic_person_24)
            }

            // Load image using enhanced Glide with proper error handling
            val thumb = card.findViewById<android.widget.ImageView>(R.id.imagePost)
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

            // Set layout parameters for vertical layout
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = ResponsiveUtils.getResponsivePadding(this, 12)
            card.layoutParams = params

            container.addView(card)
        }
    }

    private fun renderCommunityPostsFromLocal(posts: List<com.example.ecosort.data.model.CommunityPost>, container: android.widget.LinearLayout) {
        container.removeAllViews()

        if (posts.isEmpty()) {
            val emptyView = TextView(this).apply {
                text = getString(R.string.no_community_posts)
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
            val card = layoutInflater.inflate(R.layout.item_community_post, container, false)

            // Set post details
            card.findViewById<TextView>(R.id.textPostTitle).text = post.title
            card.findViewById<TextView>(R.id.textPostContent).text = post.content
            card.findViewById<TextView>(R.id.textPostType).text = post.postType.name

            // Set author details
            card.findViewById<TextView>(R.id.textAuthorName).text = post.authorName
            card.findViewById<TextView>(R.id.textPostTime).text = formatTime(post.postedAt)

            // Load author profile picture
            val authorProfileImage = card.findViewById<android.widget.ImageView>(R.id.imageAuthorProfile)
            if (!post.authorAvatar.isNullOrBlank()) {
                Glide.with(this)
                    .load(post.authorAvatar)
                    .circleCrop()
                    .placeholder(R.drawable.ic_person_24)
                    .error(R.drawable.ic_person_24)
                    .into(authorProfileImage)
            } else {
                authorProfileImage.setImageResource(R.drawable.ic_person_24)
            }

            // Load image using enhanced Glide with proper error handling
            val thumb = card.findViewById<android.widget.ImageView>(R.id.imagePost)
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
                                // Called when the image load is cleared
                            }
                        })
                    thumb.visibility = android.view.View.VISIBLE
                }
                !videoUrl.isNullOrEmpty() -> {
                    // For video posts, generate and show video thumbnail
                    android.util.Log.d("MainActivity", "Loading video thumbnail for home screen: $videoUrl")
                    thumb.visibility = android.view.View.VISIBLE

                    // Generate and load video thumbnail
                    CoroutineScope(Dispatchers.Main).launch {
                        try {
                            android.util.Log.d("MainActivity", "Starting thumbnail generation for: $videoUrl")

                            // First, try to load the video URL directly with Glide
                            // This will work for Firebase URLs and show the first frame
                            android.util.Log.d("MainActivity", "Attempting to load video URL directly with Glide")
                            Glide.with(this@MainActivity)
                                .asBitmap()
                                .load(videoUrl)
                                .placeholder(R.drawable.ic_video)
                                .error(R.drawable.ic_video)
                                .into(object : com.bumptech.glide.request.target.CustomTarget<android.graphics.Bitmap>() {
                                    override fun onResourceReady(
                                        resource: android.graphics.Bitmap,
                                        transition: com.bumptech.glide.request.transition.Transition<in android.graphics.Bitmap>?
                                    ) {
                                        android.util.Log.d("MainActivity", "Successfully loaded video thumbnail from URL")
                                        thumb.setImageBitmap(resource)
                                    }

                                    override fun onLoadFailed(errorDrawable: android.graphics.drawable.Drawable?) {
                                        android.util.Log.w("MainActivity", "Failed to load video thumbnail from URL, trying thumbnail generation")

                                        // If direct loading fails, try thumbnail generation
                                        CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                val thumbnailUri = VideoThumbnailGenerator.generateThumbnailFromUrl(
                                                    this@MainActivity,
                                                    videoUrl
                                                )

                                                CoroutineScope(Dispatchers.Main).launch {
                                                    if (thumbnailUri != null) {
                                                        android.util.Log.d("MainActivity", "Loading generated thumbnail: $thumbnailUri")
                                                        Glide.with(this@MainActivity)
                                                            .load(thumbnailUri)
                                                            .placeholder(R.drawable.ic_video)
                                                            .error(R.drawable.ic_video)
                                                            .into(thumb)
                                                    } else {
                                                        android.util.Log.w("MainActivity", "Thumbnail generation failed, using video icon")
                                                        Glide.with(this@MainActivity)
                                                            .load(R.drawable.ic_video)
                                                            .placeholder(R.drawable.ic_video)
                                                            .error(R.drawable.ic_video)
                                                            .into(thumb)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                android.util.Log.e("MainActivity", "Error in thumbnail generation fallback", e)
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    Glide.with(this@MainActivity)
                                                        .load(R.drawable.ic_video)
                                                        .placeholder(R.drawable.ic_video)
                                                        .error(R.drawable.ic_video)
                                                        .into(thumb)
                                                }
                                            }
                                        }
                                    }

                                    override fun onLoadCleared(placeholder: android.graphics.drawable.Drawable?) {
                                        // Called when the image is cleared
                                    }
                                })

                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error in video thumbnail loading", e)
                            // Final fallback to video icon
                            Glide.with(this@MainActivity)
                                .load(R.drawable.ic_video)
                                .placeholder(R.drawable.ic_video)
                                .error(R.drawable.ic_video)
                                .into(thumb)
                        }
                    }
                }
                else -> {
                    // Hide image when there's no content
                    thumb.visibility = android.view.View.GONE
                }
            }

            // Set click listener to open community feed
            card.setOnClickListener {
                startActivity(Intent(this, com.example.ecosort.community.CommunityFeedActivity::class.java))
            }

            // Set layout parameters for vertical layout
            val params = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.bottomMargin = ResponsiveUtils.getResponsivePadding(this, 12)
            card.layoutParams = params

            container.addView(card)
        }
    }

    private fun formatTime(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diff = now - timestamp

        return when {
            diff < 60_000 -> "Just now"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            diff < 86400_000 -> "${diff / 3600_000}h ago"
            else -> {
                val date = java.util.Date(timestamp)
                java.text.SimpleDateFormat("MMM dd", java.util.Locale.getDefault()).format(date)
            }
        }
    }

    private fun loadUserProfilePicture(profileImageView: android.widget.ImageView) {
        lifecycleScope.launch {
            try {
                // Get current user's profile image
                val currentUser = userRepository.getCurrentUser()
                val profileImageUrl = if (currentUser is com.example.ecosort.data.model.Result.Success<*>) {
                    (currentUser.data as? com.example.ecosort.data.model.User)?.profileImageUrl
                } else {
                    null
                }

                // Use withContext to ensure we're on the main thread for UI operations
                withContext(Dispatchers.Main) {
                    // Check if activity is still valid before UI operations
                    if (isFinishing || isDestroyed) {
                        android.util.Log.d("MainActivity", "Activity is finishing or destroyed, skipping profile picture load")
                        return@withContext
                    }

                    try {
                        if (!profileImageUrl.isNullOrBlank()) {
                            android.util.Log.d("MainActivity", "Loading profile image: $profileImageUrl")
                            // Add cache-busting to prevent image sharing between users
                            val cacheBustedUrl = profileImageManager.addCacheBustingToUrl(profileImageUrl)
                            android.util.Log.d("MainActivity", "Cache-busted URL: $cacheBustedUrl")
                            Glide.with(this@MainActivity)
                                .load(cacheBustedUrl as String)
                                .placeholder(R.drawable.ic_person_24)
                                .error(R.drawable.ic_person_24)
                                .skipMemoryCache(false)
                                .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                                .into(profileImageView)
                        } else {
                            android.util.Log.d("MainActivity", "No profile image URL, setting default")
                            Glide.with(this@MainActivity)
                                .load(R.drawable.ic_person_24)
                                .into(profileImageView)
                        }
                    } catch (glideException: Exception) {
                        android.util.Log.e("MainActivity", "Error loading profile image with Glide", glideException)
                        // Fallback to default image
                        try {
                            profileImageView.setImageResource(R.drawable.ic_person_24)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error setting fallback image", e)
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Error loading user profile picture", e)
                // Fallback to default image on main thread
                withContext(Dispatchers.Main) {
                    if (!isFinishing && !isDestroyed) {
                        try {
                            profileImageView.setImageResource(R.drawable.ic_person_24)
                        } catch (e: Exception) {
                            android.util.Log.e("MainActivity", "Error setting fallback image", e)
                        }
                    }
                }
            }
        }
    }

    // ==================== ADMIN PASSKEY METHODS ====================

    private fun showAdminPasskeyDialog() {
        val dialog = AdminPasskeyDialog.newInstance(this)
        dialog.show(supportFragmentManager, "AdminPasskeyDialog")
    }

    override fun onPasskeyVerified() {
        // Passkey verified, proceed to admin activity
        startActivity(Intent(this, com.example.ecosort.ui.admin.AdminActivity::class.java))
    }

    override fun onPasskeyCancelled() {
        // User cancelled passkey entry, do nothing
        Toast.makeText(this, "Admin access cancelled", Toast.LENGTH_SHORT).show()
    }
}
