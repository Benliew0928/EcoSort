package com.example.ecosort.utils

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.widget.NestedScrollView
import com.example.ecosort.MainActivity
import com.example.ecosort.R
import com.example.ecosort.community.CommunityFeedActivity
import com.example.ecosort.chat.ChatListActivity
import com.example.ecosort.UserProfileActivity

object BottomNavigationHelper {

    fun addBottomNavigationToActivity(activity: AppCompatActivity) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        
        // Find the main content view
        val mainContent = when {
            rootView is ConstraintLayout -> rootView
            rootView is NestedScrollView -> rootView
            else -> rootView
        }

        // Inflate the bottom navigation
        val bottomNavView = LayoutInflater.from(activity).inflate(R.layout.bottom_navigation, null)
        
        // Add bottom navigation to the root view
        if (mainContent is ConstraintLayout) {
            val layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            layoutParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomNavView.layoutParams = layoutParams
            mainContent.addView(bottomNavView)
        } else {
            // For other layouts, we'll need to wrap them
            wrapLayoutWithBottomNav(activity, mainContent, bottomNavView)
        }

        // Set up click listeners
        setupBottomNavClickListeners(activity, bottomNavView)
        
        // Update active state
        updateBottomNavState(activity, bottomNavView)
    }

    private fun wrapLayoutWithBottomNav(activity: AppCompatActivity, originalContent: View, bottomNavView: View) {
        val parent = originalContent.parent as? android.view.ViewGroup
        if (parent != null) {
            // Create a new ConstraintLayout to wrap everything
            val wrapper = ConstraintLayout(activity)
            val wrapperParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            
            // Remove original content from parent
            parent.removeView(originalContent)
            
            // Add original content to wrapper
            val contentParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
            contentParams.bottomToTop = bottomNavView.id
            originalContent.layoutParams = contentParams
            wrapper.addView(originalContent)
            
            // Add bottom nav to wrapper
            val bottomNavParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            )
            bottomNavParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            bottomNavParams.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            bottomNavParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            bottomNavView.layoutParams = bottomNavParams
            wrapper.addView(bottomNavView)
            
            // Add wrapper to parent
            parent.addView(wrapper, wrapperParams)
        }
    }

    private fun setupBottomNavClickListeners(activity: AppCompatActivity, bottomNavView: View) {
        val bottomHome = bottomNavView.findViewById<Button>(R.id.bottomHome)
        val bottomCommunity = bottomNavView.findViewById<Button>(R.id.bottomCommunity)
        val bottomChat = bottomNavView.findViewById<Button>(R.id.bottomChat)
        val bottomProfile = bottomNavView.findViewById<Button>(R.id.bottomProfile)

        bottomHome.setOnClickListener {
            if (activity.javaClass != MainActivity::class.java) {
                activity.startActivity(Intent(activity, MainActivity::class.java))
                activity.finish()
            } else {
                Toast.makeText(activity, "You're already on the home screen", Toast.LENGTH_SHORT).show()
            }
        }

        bottomCommunity.setOnClickListener {
            if (activity.javaClass != CommunityFeedActivity::class.java) {
                activity.startActivity(Intent(activity, CommunityFeedActivity::class.java))
                activity.finish()
            } else {
                Toast.makeText(activity, "You're already on the community screen", Toast.LENGTH_SHORT).show()
            }
        }

        bottomChat.setOnClickListener {
            if (activity.javaClass != ChatListActivity::class.java) {
                activity.startActivity(Intent(activity, ChatListActivity::class.java))
                activity.finish()
            } else {
                Toast.makeText(activity, "You're already on the chat screen", Toast.LENGTH_SHORT).show()
            }
        }

        bottomProfile.setOnClickListener {
            if (activity.javaClass != UserProfileActivity::class.java) {
                activity.startActivity(Intent(activity, UserProfileActivity::class.java))
                activity.finish()
            } else {
                Toast.makeText(activity, "You're already on the profile screen", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateBottomNavState(activity: AppCompatActivity, bottomNavView: View) {
        val bottomHome = bottomNavView.findViewById<Button>(R.id.bottomHome)
        val bottomCommunity = bottomNavView.findViewById<Button>(R.id.bottomCommunity)
        val bottomChat = bottomNavView.findViewById<Button>(R.id.bottomChat)
        val bottomProfile = bottomNavView.findViewById<Button>(R.id.bottomProfile)

        // Reset all buttons to inactive state
        resetButtonState(bottomHome, R.drawable.ic_home_24, R.color.text_secondary)
        resetButtonState(bottomCommunity, R.drawable.ic_com_gray_24, R.color.text_secondary)
        resetButtonState(bottomChat, R.drawable.ic_chat_24, R.color.text_secondary)
        resetButtonState(bottomProfile, R.drawable.ic_person_24, R.color.text_secondary)

        // Set active state based on current activity
        when (activity.javaClass) {
            MainActivity::class.java -> setActiveButtonState(bottomHome, R.drawable.ic_home_24, R.color.primary_green)
            CommunityFeedActivity::class.java -> setActiveButtonState(bottomCommunity, R.drawable.ic_com_gray_24, R.color.primary_green)
            ChatListActivity::class.java -> setActiveButtonState(bottomChat, R.drawable.ic_chat_24, R.color.primary_green)
            UserProfileActivity::class.java -> setActiveButtonState(bottomProfile, R.drawable.ic_person_24, R.color.primary_green)
        }
    }

    private fun resetButtonState(button: Button, drawableRes: Int, colorRes: Int) {
        button.setCompoundDrawablesWithIntrinsicBounds(0, drawableRes, 0, 0)
        button.setTextColor(button.context.getColor(colorRes))
        button.compoundDrawableTintList = button.context.getColorStateList(colorRes)
    }

    private fun setActiveButtonState(button: Button, drawableRes: Int, colorRes: Int) {
        button.setCompoundDrawablesWithIntrinsicBounds(0, drawableRes, 0, 0)
        button.setTextColor(button.context.getColor(colorRes))
        button.compoundDrawableTintList = button.context.getColorStateList(colorRes)
    }
}
