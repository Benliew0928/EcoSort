package com.example.ecosort.chat

import android.util.Log
import java.security.MessageDigest
import android.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Simple token generator for Stream Chat
 * WARNING: This is for development only. In production, tokens should be generated server-side.
 */
object StreamTokenGenerator {
    
    private const val TAG = "StreamTokenGenerator"
    
    // Your Stream secret (keep this secure in production)
    private const val STREAM_SECRET = "2p49je4gavycf36ma978nbbzdmhbmkwjqwcukzx6zwyuufk9b7meqtdhthmr6d4f"
    
    /**
     * Generate a user token for Stream Chat
     * In production, this should be done server-side for security
     */
    fun generateUserToken(userId: String): String {
        return try {
            // For development, we'll use a simple approach
            // In production, use proper JWT token generation with your Stream secret
            val payload = "user_id:$userId"
            val signature = generateSignature(payload, STREAM_SECRET)
            
            val token = "$payload.$signature"
            Log.d(TAG, "Generated token for user: $userId")
            token
        } catch (e: Exception) {
            Log.e(TAG, "Error generating token", e)
            "development_token_$userId"
        }
    }
    
    private fun generateSignature(payload: String, secret: String): String {
        return try {
            val mac = Mac.getInstance("HmacSHA256")
            val secretKeySpec = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
            mac.init(secretKeySpec)
            val signature = mac.doFinal(payload.toByteArray())
            Base64.encodeToString(signature, Base64.DEFAULT)
        } catch (e: Exception) {
            Log.e(TAG, "Error generating signature", e)
            "fallback_signature"
        }
    }
}
