package com.example.ecosort

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import android.widget.ImageView
import android.widget.TextView
import android.view.View
import com.airbnb.lottie.LottieAnimationView
import com.example.ecosort.ui.login.LoginActivity
import com.example.ecosort.data.preferences.UserPreferencesManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class SplashActivity : AppCompatActivity() {

    @Inject
    lateinit var userPreferencesManager: UserPreferencesManager

    private val splashTimeOut: Long = 8000 // 8 seconds (4s PNG + 4s Lottie)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        // Initialize views
        val appLogo = findViewById<ImageView>(R.id.ivAppLogo)
        val lottieAnimationView = findViewById<LottieAnimationView>(R.id.lottieAnimationView)
        val loadingText = findViewById<TextView>(R.id.tvLoadingText)
        
        // Show PNG logo for 2 loops (4 seconds), then switch to Lottie animation
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                appLogo.visibility = View.GONE
                lottieAnimationView.visibility = View.VISIBLE
                loadingText.visibility = View.VISIBLE // Show loading text with animation
                
                // Configure Lottie animation
                lottieAnimationView.repeatCount = 1 // This will play 2 times (0-based: 0=1 loop, 1=2 loops)
                lottieAnimationView.setRenderMode(com.airbnb.lottie.RenderMode.HARDWARE)
                
                // Add error handling for Lottie animation
                lottieAnimationView.addAnimatorListener(object : android.animation.Animator.AnimatorListener {
                    override fun onAnimationStart(animation: android.animation.Animator) {
                        android.util.Log.d("SplashActivity", "Lottie animation started")
                    }
                    
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        android.util.Log.d("SplashActivity", "Lottie animation ended")
                    }
                    
                    override fun onAnimationCancel(animation: android.animation.Animator) {
                        android.util.Log.d("SplashActivity", "Lottie animation cancelled")
                    }
                    
                    override fun onAnimationRepeat(animation: android.animation.Animator) {
                        android.util.Log.d("SplashActivity", "Lottie animation repeated")
                    }
                })
                
                lottieAnimationView.playAnimation()
            } catch (e: Exception) {
                android.util.Log.e("SplashActivity", "Error starting Lottie animation", e)
                // Fallback: just show loading text without animation
                loadingText.visibility = View.VISIBLE
            }
        }, 4000)

        // Check user session and navigate accordingly
        checkUserSessionAndNavigate()
    }

    private fun checkUserSessionAndNavigate() {
        lifecycleScope.launch {
            try {
                // Check if user is logged in
                val session = withContext(Dispatchers.IO) { 
                    userPreferencesManager.getCurrentUser() 
                }
                
                // Wait for minimum splash time to show animation
                val startTime = System.currentTimeMillis()
                
                // Navigate based on session status
                val targetActivity = if (session != null && session.isLoggedIn) {
                    MainActivity::class.java
                } else {
                    LoginActivity::class.java
                }
                
                // Ensure minimum splash time
                val elapsedTime = System.currentTimeMillis() - startTime
                val remainingTime = if (elapsedTime < splashTimeOut) {
                    splashTimeOut - elapsedTime
                } else {
                    0L
                }
                
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this@SplashActivity, targetActivity)
                    startActivity(intent)
                    finish()
                }, remainingTime)
                
            } catch (e: Exception) {
                // On error, navigate to login after minimum time
                Handler(Looper.getMainLooper()).postDelayed({
                    val intent = Intent(this@SplashActivity, LoginActivity::class.java)
                    startActivity(intent)
                    finish()
                }, splashTimeOut)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop Lottie animation to prevent memory leaks
        findViewById<LottieAnimationView>(R.id.lottieAnimationView)?.cancelAnimation()
    }
}
