package com.example.ecosort.profile

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.User
import com.example.ecosort.data.model.UserType
import com.example.ecosort.data.preferences.UserPreferencesManager
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.repository.ChatRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject

@AndroidEntryPoint
class UserProfileViewActivity : AppCompatActivity() {

    @Inject
    lateinit var userRepository: UserRepository
    
    @Inject
    lateinit var socialRepository: com.example.ecosort.data.repository.SocialRepository
    
    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager
    
    @Inject
    lateinit var database: com.example.ecosort.data.local.EcoSortDatabase
    
    @Inject
    lateinit var chatRepository: ChatRepository

    private var targetUserId: Long = 0L
    private var currentUserId: Long = 0L
    private var targetUser: User? = null

    // UI Components
    private lateinit var ivProfileImage: ImageView
    private lateinit var tvProfilePlaceholder: TextView
    private lateinit var tvUsername: TextView
    private lateinit var tvUserType: TextView
    private lateinit var tvBio: TextView
    private lateinit var tvLocation: TextView
    private lateinit var tvJoinDate: TextView
    private lateinit var tvStatsRecycled: TextView
    private lateinit var tvStatsPoints: TextView
    private lateinit var tvStatsFollowers: TextView
    private lateinit var followersContainer: LinearLayout
    private lateinit var tvProfileCompletion: TextView
    private lateinit var progressBarCompletion: ProgressBar
    private lateinit var btnBack: Button
    private lateinit var btnMessage: Button
    private lateinit var btnFollow: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var socialLinksContainer: LinearLayout
    private lateinit var achievementsContainer: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_profile_view)

        initializeViews()
        setupClickListeners()
        loadUserData()
    }

    private fun initializeViews() {
        ivProfileImage = findViewById(R.id.ivProfileImage)
        tvProfilePlaceholder = findViewById(R.id.tvProfilePlaceholder)
        tvUsername = findViewById(R.id.tvUsername)
        tvUserType = findViewById(R.id.tvUserType)
        tvBio = findViewById(R.id.tvBio)
        tvLocation = findViewById(R.id.tvLocation)
        tvJoinDate = findViewById(R.id.tvJoinDate)
        tvStatsRecycled = findViewById(R.id.tvStatsRecycled)
        tvStatsPoints = findViewById(R.id.tvStatsPoints)
        tvStatsFollowers = findViewById(R.id.tvStatsFollowers)
        followersContainer = findViewById(R.id.followersContainer)
        tvProfileCompletion = findViewById(R.id.tvProfileCompletion)
        progressBarCompletion = findViewById(R.id.progressBarCompletion)
        btnBack = findViewById(R.id.btnBack)
        btnMessage = findViewById(R.id.btnMessage)
        btnFollow = findViewById(R.id.btnFollow)
        progressBar = findViewById(R.id.progressBar)
        socialLinksContainer = findViewById(R.id.socialLinksContainer)
        achievementsContainer = findViewById(R.id.achievementsContainer)

        // Get target user ID from intent
        targetUserId = intent.getLongExtra("user_id", 0L)
        if (targetUserId == 0L) {
            Toast.makeText(this, "Invalid user profile", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
    }

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            finish()
        }

        btnMessage.setOnClickListener {
            startChatWithUser()
        }

        btnFollow.setOnClickListener {
            toggleFollow()
        }
        
        followersContainer.setOnClickListener {
            showFollowersList()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                android.util.Log.d("UserProfileViewActivity", "Loading user data for targetUserId: $targetUserId")
                
                // Get current user ID
                val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
                if (session == null || !session.isLoggedIn) {
                    android.util.Log.e("UserProfileViewActivity", "No active session")
                    finish()
                    return@launch
                }
                currentUserId = session.userId
                android.util.Log.d("UserProfileViewActivity", "Current user ID: $currentUserId")
                
                // Load target user data
                val userResult = withContext(Dispatchers.IO) { userRepository.getUserById(targetUserId) }
                android.util.Log.d("UserProfileViewActivity", "User result: $userResult")
                
                if (userResult is com.example.ecosort.data.model.Result.Success) {
                    targetUser = userResult.data
                    android.util.Log.d("UserProfileViewActivity", "Target user loaded: ${targetUser?.username}, profileImageUrl: ${targetUser?.profileImageUrl}")
                    populateUserProfile()
                } else {
                    android.util.Log.e("UserProfileViewActivity", "User not found: ${userResult}")
                    Toast.makeText(this@UserProfileViewActivity, "User not found", Toast.LENGTH_SHORT).show()
                    finish()
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error loading user data", e)
                Toast.makeText(this@UserProfileViewActivity, "Error loading user profile", Toast.LENGTH_SHORT).show()
                finish()
            } finally {
                showLoading(false)
            }
        }
    }

    private fun populateUserProfile() {
        targetUser?.let { user ->
            // Check if viewing own profile
            if (currentUserId == targetUserId) {
                // Show full profile for own profile
                loadFullProfile(user)
                btnMessage.visibility = View.GONE
                btnFollow.visibility = View.GONE
            } else {
                // Check privacy settings for other users
                checkPrivacyAndLoadProfile(user)
            }
        }
    }
    
    private fun loadFullProfile(user: com.example.ecosort.data.model.User) {
        // Basic info
        tvUsername.text = user.username.ifBlank { "User" }
        tvUserType.text = if (user.userType == UserType.ADMIN) "Administrator" else "Regular User"
        
        // Profile image
        loadProfileImage(user.profileImageUrl)
        
        // Bio and location
        if (!user.bio.isNullOrBlank()) {
            tvBio.text = user.bio
            tvBio.visibility = View.VISIBLE
        } else {
            tvBio.visibility = View.GONE
        }
        
        if (!user.location.isNullOrBlank()) {
            tvLocation.text = "📍 ${user.location}"
            tvLocation.visibility = View.VISIBLE
        } else {
            tvLocation.visibility = View.GONE
        }
        
        // Join date
        val joinDate = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.getDefault())
            .format(java.util.Date(user.joinDate))
        tvJoinDate.text = "Joined $joinDate"
        
        // Stats
        tvStatsRecycled.text = user.itemsRecycled.toString()
        tvStatsPoints.text = "${user.totalPoints} points"
        
        // Load followers count
        loadFollowersCount(user.id)
        
        // Profile completion
        val completion = user.profileCompletion
        tvProfileCompletion.text = "Profile Completion: $completion%"
        progressBarCompletion.progress = completion
        
        // Load social links
        loadSocialLinks(user.id)
        
        // Load achievements
        loadAchievements(user.id)
        
        // Show action buttons for other users
        btnMessage.visibility = View.VISIBLE
        btnFollow.visibility = View.VISIBLE
        updateFollowButtonState()
    }
    
    private fun checkPrivacyAndLoadProfile(user: com.example.ecosort.data.model.User) {
        lifecycleScope.launch {
            try {
                val privacySettingsResult = withContext(Dispatchers.IO) {
                    userRepository.getPrivacySettings(user.id)
                }
                
                val privacySettings = if (privacySettingsResult is com.example.ecosort.data.model.Result.Success) {
                    privacySettingsResult.data
                } else {
                    com.example.ecosort.data.model.PrivacySettings() // Default to public
                }
                
                withContext(Dispatchers.Main) {
                    when (privacySettings.profileVisibility) {
                        com.example.ecosort.data.model.ProfileVisibility.PUBLIC -> {
                            // Show full profile
                            loadFullProfile(user)
                        }
                        com.example.ecosort.data.model.ProfileVisibility.FRIENDS_ONLY -> {
                            // Check if users are friends
                            val areFriends = withContext(Dispatchers.IO) {
                                socialRepository.areFriends(currentUserId, targetUserId)
                            }
                            if (areFriends) {
                                loadFullProfile(user)
                            } else {
                                showPrivateProfile("This user's profile is only visible to friends")
                            }
                        }
                        com.example.ecosort.data.model.ProfileVisibility.PRIVATE -> {
                            showPrivateProfile("This user has set their profile to private")
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error checking privacy settings", e)
                withContext(Dispatchers.Main) {
                    // Default to showing full profile if privacy check fails
                    loadFullProfile(user)
                }
            }
        }
    }
    
    private fun showPrivateProfile(message: String) {
        targetUser?.let { user ->
            // Show basic info only
            tvUsername.text = user.username.ifBlank { "User" }
            tvUserType.text = if (user.userType == UserType.ADMIN) "Administrator" else "Regular User"
            
            // Load profile image (profile pictures are usually public)
            loadProfileImage(user.profileImageUrl)
            
            // Show privacy message instead of bio
            tvBio.text = message
            tvBio.visibility = View.VISIBLE
            
            // Hide private information
            tvLocation.visibility = View.GONE
            tvStatsRecycled.text = "Hidden"
            tvStatsPoints.text = "Hidden"
            tvProfileCompletion.text = "Profile hidden"
            progressBarCompletion.progress = 0
            
            // Hide social links and achievements
            findViewById<LinearLayout>(R.id.socialLinksContainer).visibility = View.GONE
            findViewById<LinearLayout>(R.id.achievementsContainer).visibility = View.GONE
            
            // Hide action buttons
            btnMessage.visibility = View.GONE
            btnFollow.visibility = View.GONE
        }
    }

    private fun loadProfileImage(imageUrl: String?) {
        android.util.Log.d("UserProfileViewActivity", "Loading profile image: $imageUrl")
        
        if (!imageUrl.isNullOrBlank()) {
            android.util.Log.d("UserProfileViewActivity", "Loading image with Glide: $imageUrl")
            Glide.with(this)
                .load(imageUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_person_24)
                .error(R.drawable.ic_person_24)
                .into(ivProfileImage)
            
            tvProfilePlaceholder.visibility = View.GONE
            ivProfileImage.visibility = View.VISIBLE
        } else {
            android.util.Log.d("UserProfileViewActivity", "No profile image URL, showing placeholder")
            tvProfilePlaceholder.visibility = View.VISIBLE
            ivProfileImage.visibility = View.GONE
        }
    }

    private fun loadSocialLinks(userId: Long) {
        lifecycleScope.launch {
            try {
                val socialLinksResult = withContext(Dispatchers.IO) { userRepository.getSocialLinks(userId) }
                if (socialLinksResult is com.example.ecosort.data.model.Result.Success) {
                    val socialLinks = socialLinksResult.data
                    setupSocialLinks(socialLinks)
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error loading social links", e)
            }
        }
    }

    private fun setupSocialLinks(socialLinks: com.example.ecosort.data.model.SocialLinks) {
        socialLinksContainer.removeAllViews()
        
        val links = listOf(
            "Website" to socialLinks.website,
            "Instagram" to socialLinks.instagram,
            "Twitter" to socialLinks.twitter,
            "LinkedIn" to socialLinks.linkedin
        )
        
        links.forEach { (platform, url) ->
            if (!url.isNullOrBlank()) {
                val linkView = layoutInflater.inflate(R.layout.item_social_link, socialLinksContainer, false)
                val tvPlatform = linkView.findViewById<TextView>(R.id.tvPlatform)
                val tvUrl = linkView.findViewById<TextView>(R.id.tvUrl)
                
                tvPlatform.text = platform
                tvUrl.text = url
                
                linkView.setOnClickListener {
                    // Open URL in browser
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Cannot open link", Toast.LENGTH_SHORT).show()
                    }
                }
                
                socialLinksContainer.addView(linkView)
            }
        }
        
        if (socialLinksContainer.childCount == 0) {
            socialLinksContainer.visibility = View.GONE
        }
    }

    private fun loadAchievements(userId: Long) {
        lifecycleScope.launch {
            try {
                val achievementsResult = withContext(Dispatchers.IO) { userRepository.getAchievements(userId) }
                if (achievementsResult is com.example.ecosort.data.model.Result.Success) {
                    val achievements = achievementsResult.data
                    setupAchievements(achievements)
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error loading achievements", e)
            }
        }
    }

    private fun setupAchievements(achievements: List<com.example.ecosort.data.model.Achievement>) {
        achievementsContainer.removeAllViews()
        
        if (achievements.isEmpty()) {
            achievementsContainer.visibility = View.GONE
            return
        }
        
        achievements.forEach { achievement ->
            val achievementView = layoutInflater.inflate(R.layout.item_achievement, achievementsContainer, false)
            val tvTitle = achievementView.findViewById<TextView>(R.id.tvTitle)
            val tvDescription = achievementView.findViewById<TextView>(R.id.tvDescription)
            val tvDate = achievementView.findViewById<TextView>(R.id.tvDate)
            
            tvTitle.text = achievement.title
            tvDescription.text = achievement.description
            tvDate.text = java.text.SimpleDateFormat("MMM dd, yyyy", java.util.Locale.getDefault())
                .format(java.util.Date(achievement.unlockedAt))
            
            achievementsContainer.addView(achievementView)
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        btnMessage.isEnabled = !show
        btnFollow.isEnabled = !show
    }
    
    private fun updateFollowButtonState() {
        lifecycleScope.launch {
            try {
                val isFollowing = socialRepository.isFollowing(currentUserId, targetUserId)
                withContext(Dispatchers.Main) {
                    if (isFollowing) {
                        btnFollow.text = "Unfollow"
                        btnFollow.backgroundTintList = getColorStateList(R.color.accent_teal)
                        btnFollow.setTextColor(getColor(R.color.white))
                    } else {
                        btnFollow.text = "Follow"
                        btnFollow.backgroundTintList = getColorStateList(R.color.primary_green)
                        btnFollow.setTextColor(getColor(R.color.white))
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error checking follow status", e)
            }
        }
    }
    
    private fun toggleFollow() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                val isCurrentlyFollowing = socialRepository.isFollowing(currentUserId, targetUserId)
                
                val result = if (isCurrentlyFollowing) {
                    socialRepository.unfollowUser(currentUserId, targetUserId)
                } else {
                    socialRepository.followUser(currentUserId, targetUserId)
                }
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    
                    if (result is com.example.ecosort.data.model.Result.Success) {
                        // Update button state
                        updateFollowButtonState()
                        
                        // Refresh followers count
                        loadFollowersCount(targetUserId)
                        
                        val action = if (isCurrentlyFollowing) "unfollowed" else "followed"
                        Toast.makeText(this@UserProfileViewActivity, "Successfully $action user", Toast.LENGTH_SHORT).show()
                    } else {
                        val errorMessage = (result as? com.example.ecosort.data.model.Result.Error)?.exception?.message ?: "Unknown error"
                        Toast.makeText(this@UserProfileViewActivity, "Error: $errorMessage", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error toggling follow", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@UserProfileViewActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun startChatWithUser() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                android.util.Log.d("UserProfileViewActivity", "Starting chat with user $targetUserId")
                
                // Use ChatRepository to get or create conversation
                val conversationResult = withContext(Dispatchers.IO) {
                    chatRepository.getOrCreateConversation(currentUserId, targetUserId)
                }
                
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    
                    when (conversationResult) {
                        is com.example.ecosort.data.model.Result.Success -> {
                            val conversation = conversationResult.data
                            android.util.Log.d("UserProfileViewActivity", "Got conversation: ${conversation.channelId}")
                            
                            // Start chat activity with the conversation
                            val intent = Intent(this@UserProfileViewActivity, com.example.ecosort.chat.ChatActivity::class.java)
                            intent.putExtra("channel_id", conversation.channelId)
                            intent.putExtra("target_user_id", targetUserId)
                            intent.putExtra("target_username", targetUser?.username ?: "User")
                            startActivity(intent)
                        }
                        is com.example.ecosort.data.model.Result.Error -> {
                            android.util.Log.e("UserProfileViewActivity", "Error getting conversation", conversationResult.exception)
                            Toast.makeText(this@UserProfileViewActivity, "Error starting chat: ${conversationResult.exception.message}", Toast.LENGTH_SHORT).show()
                        }
                        is com.example.ecosort.data.model.Result.Loading -> {
                            // This shouldn't happen since we're using suspend function
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error starting chat", e)
                withContext(Dispatchers.Main) {
                    showLoading(false)
                    Toast.makeText(this@UserProfileViewActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private suspend fun getCurrentUsername(): String {
        return try {
            val userResult = userRepository.getUserById(currentUserId)
            if (userResult is com.example.ecosort.data.model.Result.Success) {
                userResult.data.username
            } else {
                "User"
            }
        } catch (e: Exception) {
            "User"
        }
    }
    
    private fun loadFollowersCount(userId: Long) {
        lifecycleScope.launch {
            try {
                val followersCount = withContext(Dispatchers.IO) {
                    socialRepository.getFollowersCount(userId)
                }
                
                withContext(Dispatchers.Main) {
                    tvStatsFollowers.text = followersCount.toString()
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error loading followers count", e)
                withContext(Dispatchers.Main) {
                    tvStatsFollowers.text = "0"
                }
            }
        }
    }
    
    private fun showFollowersList() {
        lifecycleScope.launch {
            try {
                val followersFlow = socialRepository.getUserFollowers(targetUserId)
                followersFlow.collect { followers ->
                    withContext(Dispatchers.Main) {
                        if (followers.isNotEmpty()) {
                            // Create a simple dialog to show followers
                            val followersList = followers.map { follow ->
                                "User ID: ${follow.followerId}"
                            }.joinToString("\n")
                            
                            androidx.appcompat.app.AlertDialog.Builder(this@UserProfileViewActivity)
                                .setTitle("Followers (${followers.size})")
                                .setMessage(followersList)
                                .setPositiveButton("OK", null)
                                .show()
                        } else {
                            androidx.appcompat.app.AlertDialog.Builder(this@UserProfileViewActivity)
                                .setTitle("Followers")
                                .setMessage("No followers yet")
                                .setPositiveButton("OK", null)
                                .show()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("UserProfileViewActivity", "Error loading followers list", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@UserProfileViewActivity, "Error loading followers", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
