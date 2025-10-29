package com.example.ecosort.auth

import android.app.Activity
import android.content.Intent

/**
 * Abstraction interface for social authentication
 * Supports both Google Sign-In and Huawei Account Kit
 */
interface SocialAuthService {
    
    /**
     * Configure the social authentication service
     * 
     * @param activity The activity context
     */
    fun configure(activity: Activity)
    
    /**
     * Get the sign-in intent to launch
     * 
     * @return Intent for the sign-in flow
     */
    fun getSignInIntent(): Intent
    
    /**
     * Sign out the current user
     * 
     * @param onComplete Callback when sign-out is complete
     */
    fun signOut(onComplete: () -> Unit)
    
    /**
     * Handle the sign-in result from the intent
     * 
     * @param data The intent data from activity result
     * @return SocialAuthResult containing user information or error
     */
    suspend fun handleSignInResult(data: Intent?): SocialAuthResult
    
    /**
     * Get the provider name for display
     * 
     * @return Name of the provider (e.g., "Google", "Huawei")
     */
    fun getProviderName(): String
}

