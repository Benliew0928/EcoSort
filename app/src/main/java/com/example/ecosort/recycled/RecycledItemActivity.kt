package com.example.ecosort.recycled

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.ecosort.R
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.model.UserType
import com.example.ecosort.utils.FirebaseUidHelper
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class RecycledItemActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: RecycledItemAdapter
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var firestoreService: FirestoreService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recycled_item)
        
        // Dependencies are injected by Hilt
        
        initViews()
        loadRecycledItems()
    }
    
    private fun initViews() {
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = RecycledItemAdapter()
        recyclerView.adapter = adapter
    }
    
    private fun loadRecycledItems() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Try to get Firebase UID from current Firebase session first
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    val uid = firebaseUser.uid
                    android.util.Log.d("RecycledItemActivity", "Using Firebase UID: $uid")
                    
                    // Load recycled items from Firebase
                    val result = firestoreService.getUserRecycledItems(uid)
                    when (result) {
                        is com.example.ecosort.data.model.Result.Success -> {
                            withContext(Dispatchers.Main) {
                                adapter.updateItems(result.data)
                            }
                        }
                        is com.example.ecosort.data.model.Result.Error -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@RecycledItemActivity, "Failed to load recycled items", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is com.example.ecosort.data.model.Result.Loading -> {
                            // Handle loading state if needed
                        }
                    }
                } else {
                    // Fallback to regular user repository for non-Firebase users
                    val userResult = userRepository.getCurrentUser()
                    if (userResult is Result.Error) {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(this@RecycledItemActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        }
                    return@launch
                }
                
                    val user = (userResult as Result.Success).data
                    val firebaseUid = FirebaseUidHelper.getFirebaseUid(user)
                    
                    // Log user identification for debugging
                    FirebaseUidHelper.logUserIdentification(user)
                    
                    // Load recycled items from Firebase
                    val result = firestoreService.getUserRecycledItems(firebaseUid)
                    when (result) {
                        is com.example.ecosort.data.model.Result.Success -> {
                            withContext(Dispatchers.Main) {
                                adapter.updateItems(result.data)
                            }
                        }
                        is com.example.ecosort.data.model.Result.Error -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@RecycledItemActivity, "Failed to load recycled items", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is com.example.ecosort.data.model.Result.Loading -> {
                            // Handle loading state if needed
                        }
                    }
            }
        } catch (e: Exception) {
                Log.e("RecycledItemActivity", "Error loading recycled items", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RecycledItemActivity, "Error loading recycled items: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}