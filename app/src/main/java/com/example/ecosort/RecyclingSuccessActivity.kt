package com.example.ecosort

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RecyclingSuccessActivity : AppCompatActivity() {
    
    private lateinit var lottieTrophy: LottieAnimationView
    private lateinit var lottieSuccess: LottieAnimationView
    private lateinit var tvSuccessMessage: TextView
    private lateinit var tvItemName: TextView
    private lateinit var tvPointsEarned: TextView
    private lateinit var btnContinue: Button
    
    private var itemName: String = ""
    private var pointsEarned: Int = 100
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycling_success)
        
        // Get data from intent
        itemName = intent.getStringExtra("item_name") ?: "Item"
        pointsEarned = intent.getIntExtra("points_earned", 100)
        
        initViews()
        setupAnimations()
        updateUI()
        setupClickListeners()
    }
    
    private fun initViews() {
        lottieTrophy = findViewById(R.id.lottieTrophy)
        lottieSuccess = findViewById(R.id.lottieSuccess)
        tvSuccessMessage = findViewById(R.id.tvSuccessMessage)
        tvItemName = findViewById(R.id.tvItemName)
        tvPointsEarned = findViewById(R.id.tvPointsEarned)
        btnContinue = findViewById(R.id.btnContinue)
    }
    
    private fun setupAnimations() {
        // Setup trophy animation - play once
        lottieTrophy.setAnimation("trophy_animation.json")
        lottieTrophy.setRepeatCount(0)
        lottieTrophy.playAnimation()
        
        // Setup success check animation - play once
        lottieSuccess.setAnimation("success_animation.json")
        lottieSuccess.setRepeatCount(0)
        lottieSuccess.playAnimation()
    }
    
    private fun updateUI() {
        tvSuccessMessage.text = "Congratulations! You've successfully recycled an item!"
        tvItemName.text = "Item: $itemName"
        tvPointsEarned.text = "+$pointsEarned points earned!"
    }
    
    private fun setupClickListeners() {
        btnContinue.setOnClickListener {
            // Navigate back to main activity
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }
    }
    
    override fun onBackPressed() {
        // Prevent back button from going back to analysis result
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        startActivity(intent)
        finish()
    }
}

