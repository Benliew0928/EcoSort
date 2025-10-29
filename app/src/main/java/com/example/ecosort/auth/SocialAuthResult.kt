package com.example.ecosort.auth

/**
 * Common data class for social authentication results
 * Works with both Google Sign-In and Huawei Account Kit
 */
data class SocialAuthResult(
    val email: String,
    val displayName: String,
    val photoUrl: String,
    val accountId: String, // Google ID or Huawei Open ID
    val isSuccess: Boolean = true,
    val errorMessage: String? = null
)

