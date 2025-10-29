package com.example.ecosort

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.WasteCategory
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.getstream.chat.android.client.ChatClient
import io.getstream.chat.android.client.logger.ChatLogLevel
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

import kotlinx.coroutines.GlobalScope


@HiltAndroidApp
class EcoSortApplication : Application() {

    companion object {
        // chatClient declaration remains here
        lateinit var chatClient: ChatClient
            private set

        // Application context for global access
        private var instance: EcoSortApplication? = null

        fun getContext(): android.content.Context? {
            return instance?.applicationContext
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Set instance for global context access
        instance = this

        // Initialize Huawei Maps (HMS) if available - uses reflection to avoid compile dependency
        // This is only needed for AppGallery builds, but we check at runtime
        try {
            val mapsInitializerClass = Class.forName("com.huawei.hms.maps.MapsInitializer")
            val initializeMethod = mapsInitializerClass.getMethod("initialize", android.content.Context::class.java)
            initializeMethod.invoke(null, applicationContext)
            android.util.Log.d("EcoSortApplication", "✅ HMS Maps initialized successfully")
        } catch (e: ClassNotFoundException) {
            // HMS SDK not available in this build (expected for Google Play builds)
            android.util.Log.d("EcoSortApplication", "HMS Maps not available in this build (Google Play build)")
        } catch (e: Exception) {
            android.util.Log.w("EcoSortApplication", "HMS Maps initialization failed: ${e.message}")
        }

        // Log app startup
        android.util.Log.d("EcoSortApplication", "App started successfully")
        
        // 🔑 CRITICAL: Migrate old social users to Firebase on app startup
        GlobalScope.launch(Dispatchers.IO) {
            try {
                android.util.Log.d("EcoSortApplication", "🔄 Starting migration of old social users...")
                // Migration will happen automatically when users log in
                // This is just a placeholder for future batch migration if needed
                android.util.Log.d("EcoSortApplication", "✅ Migration system ready")
            } catch (e: Exception) {
                android.util.Log.e("EcoSortApplication", "❌ Migration error: ${e.message}")
            }
        }

        // Global crash logger (rest of the file remains the same)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val stack = sw.toString()
                Log.e("GlobalCrash", "Uncaught exception in thread ${thread.name}: ${throwable.message}\n$stack")
            } catch (e: Exception) {
                Log.e("GlobalCrash", "Failed to log uncaught exception", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }

        // Delete default items from Firebase
        GlobalScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000)
        }
    }

    
    private fun initializeStreamChat() {
        try {
            chatClient = ChatClient.Builder("7v6rshncwvwm", this)
                .logLevel(ChatLogLevel.ALL)
                .build()
            
            android.util.Log.d("EcoSortApplication", "Stream Chat initialized successfully")
        } catch (e: Exception) {
            android.util.Log.e("EcoSortApplication", "Failed to initialize Stream Chat", e)
            // Don't crash the app if Stream Chat fails to initialize
        }
    }

}