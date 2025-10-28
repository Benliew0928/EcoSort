package com.example.ecosort.points

import android.os.Bundle
import android.util.Log
import android.widget.TextView
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
class PointsActivity : AppCompatActivity() {

    private lateinit var tvTotalPoints: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PointsTransactionAdapter
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var firestoreService: FirestoreService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_points)
        
        // Dependencies are injected by Hilt

        initViews()
        loadPointsData()
    }

    private fun initViews() {
        tvTotalPoints = findViewById(R.id.tvTotalPoints)
        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = PointsTransactionAdapter()
        recyclerView.adapter = adapter
    }
    
    private fun loadPointsData() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Try to get Firebase UID from current Firebase session first
                val firebaseUser = FirebaseAuth.getInstance().currentUser
                if (firebaseUser != null) {
                    val uid = firebaseUser.uid
                    android.util.Log.d("PointsActivity", "Using Firebase UID: $uid")
                    
                    // Load user points
                    val pointsResult = firestoreService.getUserPoints(uid)
                when (pointsResult) {
                    is com.example.ecosort.data.model.Result.Success -> {
                        val totalPoints = if (pointsResult.data != null) {
                            (pointsResult.data["totalPoints"] as? Long)?.toInt() ?: 0
                } else {
                            0
                        }
                        
                        withContext(Dispatchers.Main) {
                            tvTotalPoints.text = "$totalPoints points"
                        }
                    }
                    is com.example.ecosort.data.model.Result.Error -> {
                        withContext(Dispatchers.Main) {
                            tvTotalPoints.text = "0 points"
                        }
                    }
                    is com.example.ecosort.data.model.Result.Loading -> {
                        // Handle loading state if needed
                    }
                }
                
                    // Load points transactions
                    val transactionsResult = firestoreService.getUserPointsTransactions(uid)
                    when (transactionsResult) {
                        is com.example.ecosort.data.model.Result.Success -> {
                            withContext(Dispatchers.Main) {
                                adapter.updateTransactions(transactionsResult.data)
                            }
                        }
                        is com.example.ecosort.data.model.Result.Error -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@PointsActivity, "Failed to load points transactions", Toast.LENGTH_SHORT).show()
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
                            Toast.makeText(this@PointsActivity, "Please login first", Toast.LENGTH_SHORT).show()
                        }
                    return@launch
                }

                    val user = (userResult as Result.Success).data
                    val firebaseUid = FirebaseUidHelper.getFirebaseUid(user)

                    // Log user identification for debugging
                    FirebaseUidHelper.logUserIdentification(user)

                // Load user points
                    val pointsResult = firestoreService.getUserPoints(firebaseUid)
                    when (pointsResult) {
                        is com.example.ecosort.data.model.Result.Success -> {
                            val totalPoints = if (pointsResult.data != null) {
                                (pointsResult.data["totalPoints"] as? Long)?.toInt() ?: 0
                } else {
                                0
                            }
                            
                            withContext(Dispatchers.Main) {
                                tvTotalPoints.text = "$totalPoints points"
                            }
                        }
                        is com.example.ecosort.data.model.Result.Error -> {
                            withContext(Dispatchers.Main) {
                                tvTotalPoints.text = "0 points"
                            }
                        }
                        is com.example.ecosort.data.model.Result.Loading -> {
                            // Handle loading state if needed
                        }
                    }
                    
                    // Load points transactions
                    val transactionsResult = firestoreService.getUserPointsTransactions(firebaseUid)
                    when (transactionsResult) {
                        is com.example.ecosort.data.model.Result.Success -> {
                            withContext(Dispatchers.Main) {
                                adapter.updateTransactions(transactionsResult.data)
                            }
                        }
                        is com.example.ecosort.data.model.Result.Error -> {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@PointsActivity, "Failed to load points transactions", Toast.LENGTH_SHORT).show()
                            }
                        }
                        is com.example.ecosort.data.model.Result.Loading -> {
                            // Handle loading state if needed
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e("PointsActivity", "Error loading points data", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PointsActivity, "Error loading points data: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}