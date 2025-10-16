package com.example.ecosort.utils

import android.content.Context
import android.util.Log
import com.example.ecosort.data.local.EcoSortDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DatabaseDebugHelper(private val context: Context) {
    
    fun logDatabaseInfo() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = EcoSortDatabase.getDatabase(context)
                val userDao = database.userDao()
                val chatMessageDao = database.chatMessageDao()
                val conversationDao = database.conversationDao()
                
                // Get user count
                val userCount = userDao.getAllUsers().size
                Log.d("DatabaseDebug", "Total users in database: $userCount")
                
                // Get chat message count (using a sample channel to count messages)
                val sampleMessages = chatMessageDao.getMessagesForChannel("sample")
                Log.d("DatabaseDebug", "Sample chat messages retrieved")
                
                // Note: We can't easily count all messages without a specific channel
                // This is just for debugging database connectivity
                
                // Log database file info
                val dbFile = context.getDatabasePath("ecosort_database")
                Log.d("DatabaseDebug", "Database file exists: ${dbFile.exists()}")
                Log.d("DatabaseDebug", "Database file size: ${if (dbFile.exists()) dbFile.length() else 0} bytes")
                Log.d("DatabaseDebug", "Database file path: ${dbFile.absolutePath}")
                
            } catch (e: Exception) {
                Log.e("DatabaseDebug", "Error getting database info", e)
            }
        }
    }
    
    fun logUserSessionInfo() {
        try {
            val prefs = context.getSharedPreferences("user_preferences", Context.MODE_PRIVATE)
            val isLoggedIn = prefs.getBoolean("is_logged_in", false)
            val userId = prefs.getLong("user_id", 0L)
            val username = prefs.getString("username", "Unknown")
            
            Log.d("UserSessionDebug", "User logged in: $isLoggedIn")
            Log.d("UserSessionDebug", "User ID: $userId")
            Log.d("UserSessionDebug", "Username: $username")
            
        } catch (e: Exception) {
            Log.e("UserSessionDebug", "Error getting user session info", e)
        }
    }
}
