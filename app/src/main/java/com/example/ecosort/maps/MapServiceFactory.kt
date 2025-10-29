package com.example.ecosort.maps

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability

/**
 * Factory class to detect available map services (GMS or HMS) and provide appropriate map implementation.
 * 
 * This class:
 * 1. Detects whether Google Play Services (GMS) or Huawei Mobile Services (HMS) are available
 * 2. Returns the correct map service type
 * 3. Provides user-friendly error messages when neither service is available
 */
object MapServiceFactory {
    
    private const val TAG = "MapServiceFactory"
    
    enum class MapServiceType {
        GOOGLE_MAPS,
        HUAWEI_MAPS,
        NONE
    }
    
    /**
     * Detects which map service is available on the device
     * Priority: Huawei Maps > Google Maps > None
     * 
     * @param context Application or Activity context
     * @return MapServiceType indicating which service is available
     */
    fun detectMapService(context: Context): MapServiceType {
        // Check Huawei Mobile Services first (prioritize HMS Map Kit)
        val hmsAvailable = isHuaweiMobileServicesAvailable(context)
        if (hmsAvailable) {
            Log.d(TAG, "✅ Huawei Mobile Services detected - Using Huawei Map Kit (Priority)")
            return MapServiceType.HUAWEI_MAPS
        }
        
        // Check Google Play Services as fallback
        val gmsAvailable = isGooglePlayServicesAvailable(context)
        if (gmsAvailable) {
            Log.d(TAG, "✅ Google Play Services detected - Using Google Maps (Fallback)")
            return MapServiceType.GOOGLE_MAPS
        }
        
        // Neither service is available
        Log.e(TAG, "❌ No map service available - Neither HMS nor GMS detected")
        return MapServiceType.NONE
    }
    
    /**
     * Checks if Google Play Services is available and up-to-date
     */
    private fun isGooglePlayServicesAvailable(context: Context): Boolean {
        return try {
            val googleApiAvailability = GoogleApiAvailability.getInstance()
            val resultCode = googleApiAvailability.isGooglePlayServicesAvailable(context)
            
            when (resultCode) {
                ConnectionResult.SUCCESS -> {
                    Log.d(TAG, "Google Play Services: Available")
                    true
                }
                ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> {
                    Log.w(TAG, "Google Play Services: Update required")
                    false
                }
                ConnectionResult.SERVICE_DISABLED -> {
                    Log.w(TAG, "Google Play Services: Disabled")
                    false
                }
                else -> {
                    Log.w(TAG, "Google Play Services: Unavailable (code: $resultCode)")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Google Play Services: ${e.message}")
            false
        }
    }
    
    /**
     * Checks if Huawei Mobile Services is available and up-to-date
     * Uses reflection to avoid compile-time dependency on HMS SDK
     */
    private fun isHuaweiMobileServicesAvailable(context: Context): Boolean {
        return try {
            // Use reflection to check HMS availability without importing HMS classes
            val huaweiApiAvailabilityClass = Class.forName("com.huawei.hms.api.HuaweiApiAvailability")
            val getInstanceMethod = huaweiApiAvailabilityClass.getMethod("getInstance")
            val huaweiApiAvailability = getInstanceMethod.invoke(null)
            
            val isAvailableMethod = huaweiApiAvailabilityClass.getMethod("isHuaweiMobileServicesAvailable", Context::class.java)
            val resultCode = isAvailableMethod.invoke(huaweiApiAvailability, context) as Int
            
            // ConnectionResult.SUCCESS = 0
            when (resultCode) {
                0 -> {
                    Log.d(TAG, "Huawei Mobile Services: Available")
                    true
                }
                1 -> {
                    Log.w(TAG, "Huawei Mobile Services: Update required")
                    false
                }
                2 -> {
                    Log.w(TAG, "Huawei Mobile Services: Disabled")
                    false
                }
                else -> {
                    Log.w(TAG, "Huawei Mobile Services: Unavailable (code: $resultCode)")
                    false
                }
            }
        } catch (e: ClassNotFoundException) {
            Log.d(TAG, "HMS SDK not available in this build")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Huawei Mobile Services: ${e.message}")
            false
        }
    }
    
    /**
     * Gets a user-friendly message for when no map service is available
     */
    fun getNoServiceMessage(): String {
        return "No map service available on this device. Please install Google Play Services or Huawei Mobile Services."
    }
}

