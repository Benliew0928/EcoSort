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

import com.huawei.hms.maps.MapsInitializer // <--- CRITICAL IMPORT ADDED
import kotlinx.coroutines.GlobalScope


@HiltAndroidApp
class EcoSortApplication : Application() {

    companion object {
        // chatClient declaration remains here
        lateinit var chatClient: ChatClient
            private set
    }

    override fun onCreate() {
        super.onCreate()

        // CRITICAL FIX: Initialize Huawei Maps EARLY and SAFELY.
        // This solves the "MapsInitializer is not initialized" error by running before the Map Fragment starts.
        try {
            MapsInitializer.initialize(applicationContext)
            android.util.Log.d("EcoSortApplication", "HMS Maps Initializer completed successfully.")
        } catch (e: Exception) {
            android.util.Log.e("EcoSortApplication", "FATAL: HMS Maps Initializer failed early: ${e.message}")
        }

        // Log app startup
        android.util.Log.d("EcoSortApplication", "App started successfully")

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