package com.example.ecosort

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import com.example.ecosort.data.local.EcoSortDatabase
import com.example.ecosort.data.model.ItemCondition
import com.example.ecosort.data.model.MarketplaceItem
import com.example.ecosort.data.model.WasteCategory
import com.example.ecosort.data.firebase.FirebaseMarketplaceItem
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

@HiltAndroidApp
class EcoSortApplication : Application() {
    
    companion object {
        lateinit var chatClient: ChatClient
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        // App-wide initialization goes here
        // Initialize Firebase (this will be done automatically by the Firebase SDK)
        // Note: Demo items are now managed by Firebase, not local deletion
        
        // Initialize Stream Chat - Temporarily disabled to prevent crashes
        // initializeStreamChat()
        
        // Log app startup
        android.util.Log.d("EcoSortApplication", "App started successfully")

        // Global crash logger to capture any fatal crash details
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
        
        // Delete default items from Firebase with delay to ensure Firebase is ready
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000) // Wait 2 seconds for Firebase to initialize
            deleteDefaultItems()
        }
    }

    private fun deleteDefaultItems() {
        kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
            try {
                val firestore = FirebaseFirestore.getInstance()
                val collection = firestore.collection("marketplace_items")
                
                // List of default item titles to delete
                val defaultItemTitles = listOf(
                    "Vintage Radio",
                    "Glass Vase", 
                    "Metal Frame"
                )
                
                // Delete each default item by title
                defaultItemTitles.forEach { title ->
                    collection.whereEqualTo("title", title)
                        .get()
                        .addOnSuccessListener { documents ->
                            documents.forEach { document ->
                                document.reference.delete()
                                    .addOnSuccessListener {
                                        android.util.Log.d("EcoSortApplication", "Deleted default item: $title")
                                    }
                                    .addOnFailureListener { e ->
                                        android.util.Log.e("EcoSortApplication", "Failed to delete default item: $title", e)
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            android.util.Log.e("EcoSortApplication", "Error querying for default item: $title", e)
                        }
                }
            } catch (e: Exception) {
                android.util.Log.e("EcoSortApplication", "Error deleting default items", e)
            }
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