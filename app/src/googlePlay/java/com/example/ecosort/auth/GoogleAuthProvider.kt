package com.example.ecosort.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Google Sign-In implementation of SocialAuthService
 * Used for Google Play Store build variant
 */
class GoogleAuthProvider : SocialAuthService {
    
    private var googleSignInClient: GoogleSignInClient? = null
    
    override fun configure(activity: Activity) {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(activity, gso)
        Log.d("GoogleAuthProvider", "Google Sign-In configured")
    }
    
    override fun getSignInIntent(): Intent {
        val client = googleSignInClient
            ?: throw IllegalStateException("GoogleAuthProvider not configured. Call configure() first.")
        
        return client.signInIntent
    }
    
    override fun signOut(onComplete: () -> Unit) {
        googleSignInClient?.signOut()?.addOnCompleteListener {
            Log.d("GoogleAuthProvider", "Google Sign-Out complete")
            onComplete()
        } ?: run {
            Log.w("GoogleAuthProvider", "GoogleSignInClient is null during sign out")
            onComplete()
        }
    }
    
    override suspend fun handleSignInResult(data: Intent?): SocialAuthResult {
        return suspendCancellableCoroutine { continuation ->
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.getResult(ApiException::class.java)
                
                if (account != null) {
                    // Validate required fields
                    val email = account.email
                    val accountId = account.id
                    
                    if (email.isNullOrEmpty() || accountId.isNullOrEmpty()) {
                        val errorResult = SocialAuthResult(
                            email = "",
                            displayName = "",
                            photoUrl = "",
                            accountId = "",
                            isSuccess = false,
                            errorMessage = "Google account is missing required information (email or ID)"
                        )
                        Log.e("GoogleAuthProvider", "Google account missing required fields. Email: $email, ID: $accountId")
                        continuation.resume(errorResult)
                        return@suspendCancellableCoroutine
                    }
                    
                    val result = SocialAuthResult(
                        email = email,
                        displayName = account.displayName ?: "",
                        photoUrl = account.photoUrl?.toString() ?: "",
                        accountId = accountId,
                        isSuccess = true,
                        errorMessage = null
                    )
                    Log.d("GoogleAuthProvider", "Google Sign-In successful: ${result.email}")
                    continuation.resume(result)
                } else {
                    val errorResult = SocialAuthResult(
                        email = "",
                        displayName = "",
                        photoUrl = "",
                        accountId = "",
                        isSuccess = false,
                        errorMessage = "Google account is null"
                    )
                    Log.e("GoogleAuthProvider", "Google account is null")
                    continuation.resume(errorResult)
                }
            } catch (e: ApiException) {
                val errorMessage = when (e.statusCode) {
                    7 -> "Network error. Please check your connection."
                    8 -> "Internal error. Please try again."
                    5 -> "Invalid account. Please try again."
                    12501 -> "Sign-in cancelled"
                    else -> "Sign-in failed: ${e.message}"
                }
                
                val errorResult = SocialAuthResult(
                    email = "",
                    displayName = "",
                    photoUrl = "",
                    accountId = "",
                    isSuccess = false,
                    errorMessage = errorMessage
                )
                Log.e("GoogleAuthProvider", "Google Sign-In error: ${e.statusCode} - $errorMessage")
                continuation.resume(errorResult)
            } catch (e: Exception) {
                val errorResult = SocialAuthResult(
                    email = "",
                    displayName = "",
                    photoUrl = "",
                    accountId = "",
                    isSuccess = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
                Log.e("GoogleAuthProvider", "Unexpected error during Google Sign-In", e)
                continuation.resume(errorResult)
            }
        }
    }
    
    override fun getProviderName(): String = "Google"
}

