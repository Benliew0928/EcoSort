package com.example.ecosort.maps

import android.content.Context
import android.content.Intent
import android.widget.Toast

/**
 * Smart map launcher that automatically detects which map service is available
 * and launches the appropriate map activity.
 * 
 * This class:
 * 1. Detects available map services (Google Maps or Huawei Maps)
 * 2. Launches the correct map activity
 * 3. Shows error messages when no service is available
 */
object MapLauncher {
    
    /**
     * Launches the appropriate map activity based on available services
     * 
     * @param context Context from which to launch the map
     * @return true if a map was launched, false if no service is available
     */
    fun launchMap(context: Context): Boolean {
        val mapServiceType = MapServiceFactory.detectMapService(context)
        
        return when (mapServiceType) {
            MapServiceFactory.MapServiceType.GOOGLE_MAPS -> {
                launchGoogleMap(context)
                true
            }
            MapServiceFactory.MapServiceType.HUAWEI_MAPS -> {
                launchHuaweiMap(context)
                true
            }
            MapServiceFactory.MapServiceType.NONE -> {
                showNoServiceError(context)
                false
            }
        }
    }
    
    /**
     * Launches Google Maps activity
     */
    private fun launchGoogleMap(context: Context) {
        try {
            // Use reflection to avoid compile-time dependency on GoogleMapActivity
            // This allows the code to work even when GoogleMapActivity isn't available (HMS-only build)
            val intent = Intent(context, Class.forName("com.example.ecosort.hms.GoogleMapActivity"))
            context.startActivity(intent)
        } catch (e: ClassNotFoundException) {
            // Fallback: If GoogleMapActivity isn't available, try Huawei Maps
            android.util.Log.w("MapLauncher", "GoogleMapActivity not found, falling back to Huawei Maps")
            launchHuaweiMap(context)
        } catch (e: Exception) {
            android.util.Log.e("MapLauncher", "Failed to launch Google Maps: ${e.message}")
            showNoServiceError(context)
        }
    }
    
    /**
     * Launches Huawei Maps activity
     * Uses reflection to avoid compile-time dependency
     */
    private fun launchHuaweiMap(context: Context) {
        try {
            // Use reflection to avoid compile-time dependency on HuaweiMapActivity
            val intent = Intent(context, Class.forName("com.example.ecosort.hms.HuaweiMapActivity"))
            context.startActivity(intent)
        } catch (e: ClassNotFoundException) {
            android.util.Log.e("MapLauncher", "HuaweiMapActivity not found (may be Google Play build)")
            showNoServiceError(context)
        } catch (e: Exception) {
            android.util.Log.e("MapLauncher", "Failed to launch Huawei Maps: ${e.message}")
            showNoServiceError(context)
        }
    }
    
    /**
     * Shows error message when no map service is available
     */
    private fun showNoServiceError(context: Context) {
        Toast.makeText(
            context,
            MapServiceFactory.getNoServiceMessage(),
            Toast.LENGTH_LONG
        ).show()
    }
}

