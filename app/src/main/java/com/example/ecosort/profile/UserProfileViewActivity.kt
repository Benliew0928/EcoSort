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
    lateinit var userPreferencesManager: UserPreferencesManager

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
    private lateinit var tvStatsEarnings: TextView
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
        tvStatsEarnings = findViewById(R.id.tvStatsEarnings)
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
            // Navigate to chat with this user
            val intent = Intent(this, com.example.ecosort.chat.SimpleChatActivity::class.java)
            intent.putExtra("target_user_id", targetUserId)
            intent.putExtra("target_username", targetUser?.username ?: "User")
            startActivity(intent)
        }

        btnFollow.setOnClickListener {
            // TODO: Implement follow functionality
            Toast.makeText(this, "Follow functionality coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadUserData() {
        lifecycleScope.launch {
            try {
                showLoading(true)
                
                // Get current user ID
                val session = withContext(Dispatchers.IO) { userPreferencesManager.userSession.first() }
                if (session == null || !session.isLoggedIn) {
                    finish()
                    return@launch
                }
                currentUserId = session.userId
                
                // Load target user data
                val userResult = withContext(Dispatchers.IO) { userRepository.getUserById(targetUserId) }
                if (userResult is com.example.ecosort.data.model.Result.Success) {
                    targetUser = userResult.data
                    populateUserProfile()
                } else {
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
            tvStatsEarnings.text = "RM ${String.format("%.2f", user.totalEarnings)}"
            
            // Profile completion
            val completion = user.profileCompletion
            tvProfileCompletion.text = "Profile Completion: $completion%"
            progressBarCompletion.progress = completion
            
            // Load social links
            loadSocialLinks(user.id)
            
            // Load achievements
            loadAchievements(user.id)
            
            // Hide message button if viewing own profile
            if (currentUserId == targetUserId) {
                btnMessage.visibility = View.GONE
                btnFollow.visibility = View.GONE
            }
        }
    }

    private fun loadProfileImage(imageUrl: String?) {
        if (!imageUrl.isNullOrBlank()) {
            Glide.with(this)
                .load(imageUrl)
                .circleCrop()
                .placeholder(R.drawable.ic_person_24)
                .error(R.drawable.ic_person_24)
                .into(ivProfileImage)
            
            tvProfilePlaceholder.visibility = View.GONE
            ivProfileImage.visibility = View.VISIBLE
        } else {
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
}
