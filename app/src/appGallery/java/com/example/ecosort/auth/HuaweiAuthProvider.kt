package com.example.ecosort.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.huawei.hms.support.account.AccountAuthManager
import com.huawei.hms.support.account.request.AccountAuthParams
import com.huawei.hms.support.account.request.AccountAuthParamsHelper
import com.huawei.hms.support.account.service.AccountAuthService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Huawei Account Kit implementation of SocialAuthService
 * Used for AppGallery build variant
 */
class HuaweiAuthProvider : SocialAuthService {
    
    private var huaweiAccountService: AccountAuthService? = null
    
    override fun configure(activity: Activity) {
        val authParams = AccountAuthParamsHelper(AccountAuthParams.DEFAULT_AUTH_REQUEST_PARAM)
            .setEmail()
            .setProfile()
            .createParams()
        
        huaweiAccountService = AccountAuthManager.getService(activity, authParams)
        Log.d("HuaweiAuthProvider", "Huawei Account Kit configured")
    }
    
    override fun getSignInIntent(): Intent {
        val service = huaweiAccountService
            ?: throw IllegalStateException("HuaweiAuthProvider not configured. Call configure() first.")
        
        return service.signInIntent
    }
    
    override fun signOut(onComplete: () -> Unit) {
        huaweiAccountService?.signOut()?.addOnCompleteListener {
            Log.d("HuaweiAuthProvider", "Huawei Sign-Out complete")
            onComplete()
        } ?: run {
            Log.w("HuaweiAuthProvider", "HuaweiAccountService is null during sign out")
            onComplete()
        }
    }
    
    override suspend fun handleSignInResult(data: Intent?): SocialAuthResult {
        return suspendCancellableCoroutine { continuation ->
            try {
                val task = AccountAuthManager.parseAuthResultFromIntent(data)
                
                if (task.isSuccessful) {
                    val authAccount = task.result
                    
                    if (authAccount != null) {
                        // Validate required fields
                        val email = authAccount.email
                        val accountId = authAccount.openId
                        
                        if (email.isNullOrEmpty() || accountId.isNullOrEmpty()) {
                            val errorResult = SocialAuthResult(
                                email = "",
                                displayName = "",
                                photoUrl = "",
                                accountId = "",
                                isSuccess = false,
                                errorMessage = "Huawei account is missing required information (email or ID)"
                            )
                            Log.e("HuaweiAuthProvider", "Huawei account missing required fields. Email: $email, ID: $accountId")
                            continuation.resume(errorResult)
                            return@suspendCancellableCoroutine
                        }
                        
                        val result = SocialAuthResult(
                            email = email,
                            displayName = authAccount.displayName ?: "",
                            photoUrl = authAccount.avatarUri?.toString() ?: "",
                            accountId = accountId,
                            isSuccess = true,
                            errorMessage = null
                        )
                        Log.d("HuaweiAuthProvider", "Huawei Sign-In successful: ${result.email}")
                        continuation.resume(result)
                    } else {
                        val errorResult = SocialAuthResult(
                            email = "",
                            displayName = "",
                            photoUrl = "",
                            accountId = "",
                            isSuccess = false,
                            errorMessage = "Huawei account is null"
                        )
                        Log.e("HuaweiAuthProvider", "Huawei account is null")
                        continuation.resume(errorResult)
                    }
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception?.message) {
                        null -> "Sign-in cancelled or failed"
                        else -> "Sign-in failed: ${exception.message}"
                    }
                    
                    val errorResult = SocialAuthResult(
                        email = "",
                        displayName = "",
                        photoUrl = "",
                        accountId = "",
                        isSuccess = false,
                        errorMessage = errorMessage
                    )
                    Log.e("HuaweiAuthProvider", "Huawei Sign-In error: $errorMessage")
                    continuation.resume(errorResult)
                }
            } catch (e: Exception) {
                val errorResult = SocialAuthResult(
                    email = "",
                    displayName = "",
                    photoUrl = "",
                    accountId = "",
                    isSuccess = false,
                    errorMessage = "Unexpected error: ${e.message}"
                )
                Log.e("HuaweiAuthProvider", "Unexpected error during Huawei Sign-In", e)
                continuation.resume(errorResult)
            }
        }
    }
    
    override fun getProviderName(): String = "Huawei"
}

