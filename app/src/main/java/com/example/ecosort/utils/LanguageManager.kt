package com.example.ecosort.utils

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import java.util.*

object LanguageManager {
    
    fun setAppLanguage(context: Context, languageCode: String) {
        val locale = when (languageCode) {
            "zh" -> Locale("zh", "CN")
            "ms" -> Locale("ms", "MY")
            else -> Locale("en", "US")
        }
        
        Locale.setDefault(locale)
        
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocale(locale)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            context.resources.updateConfiguration(configuration, context.resources.displayMetrics)
        }
        
        // Also apply to application context
        val appContext = context.applicationContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appContext.createConfigurationContext(configuration)
        } else {
            @Suppress("DEPRECATION")
            appContext.resources.updateConfiguration(configuration, appContext.resources.displayMetrics)
        }
    }
    
    fun getCurrentLanguage(context: Context): String {
        val locale = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        
        return when (locale.language) {
            "zh" -> "zh"
            "ms" -> "ms"
            else -> "en"
        }
    }
}
