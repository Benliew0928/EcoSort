package com.example.ecosort.auth

import android.util.Log

/**
 * Factory for creating the appropriate SocialAuthService based on build flavor
 */
object SocialAuthFactory {
    
    /**
     * Create the appropriate social auth provider for the current build
     * 
     * @param storeType The store type from BuildConfig
     * @return SocialAuthService instance
     */
    fun createAuthProvider(storeType: String): SocialAuthService {
        return try {
            when (storeType) {
                "GOOGLE_PLAY" -> {
                    // Use reflection to avoid direct import
                    val clazz = Class.forName("com.example.ecosort.auth.GoogleAuthProvider")
                    clazz.newInstance() as SocialAuthService
                }
                "APP_GALLERY" -> {
                    // Use reflection to avoid direct import
                    val clazz = Class.forName("com.example.ecosort.auth.HuaweiAuthProvider")
                    clazz.newInstance() as SocialAuthService
                }
                else -> {
                    Log.w("SocialAuthFactory", "Unknown store type: $storeType, defaulting to Google Play")
                    val clazz = Class.forName("com.example.ecosort.auth.GoogleAuthProvider")
                    clazz.newInstance() as SocialAuthService
                }
            }
        } catch (e: Exception) {
            Log.e("SocialAuthFactory", "Failed to create auth provider: ${e.message}", e)
            throw IllegalStateException("Could not create auth provider for store type: $storeType", e)
        }
    }
    
    /**
     * Get the button text for the current store
     * 
     * @param storeType The store type from BuildConfig
     * @return Button text (e.g., "Sign in with G", "Sign in with Huawei")
     */
    fun getSignInButtonText(storeType: String): String {
        return when (storeType) {
            "GOOGLE_PLAY" -> "Sign in with G"
            "APP_GALLERY" -> "Sign in with Huawei"
            else -> "Sign in"
        }
    }
}

