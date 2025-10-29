package com.example.ecosort

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Button
import com.example.ecosort.data.repository.UserRepository
import com.example.ecosort.data.firebase.FirestoreService
import com.example.ecosort.utils.FirebaseUidHelper
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import java.util.*
import android.content.Intent
import com.google.firebase.Timestamp
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.airbnb.lottie.LottieAnimationView
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import java.io.File // NEW Import for file handling
import com.example.ecosort.utils.BottomNavigationHelper

@AndroidEntryPoint
class AnalysisResultActivity : AppCompatActivity() {

    companion object {
        // ⭐ CRITICAL CHANGE: Use path instead of Parcelable Bitmap ⭐
        const val EXTRA_BITMAP_PATH = "com.example.ecosort.EXTRA_BITMAP_PATH"
        const val EXTRA_LABEL = "extra_label"
    }

    // Loading state views
    private lateinit var llLoadingState: LinearLayout
    private lateinit var lottieLoading: LottieAnimationView
    private lateinit var tvAnalyzingText: TextView
    private lateinit var ivCapturedObject: ImageView
    private lateinit var btnRecycleIt: Button
    
    // Dependencies
    @Inject
    lateinit var userRepository: UserRepository
    @Inject
    lateinit var firestoreService: FirestoreService
    
    // Results state views
    private lateinit var llResultsState: LinearLayout
    private lateinit var ivCapturedObjectResult: ImageView
    private lateinit var tvItemName: TextView
    private lateinit var tvCategory: TextView
    private lateinit var tvBinColor: TextView
    private lateinit var tvRecyclableStatus: TextView
    private lateinit var tvConfidence: TextView
    private lateinit var llPreparation: LinearLayout
    private lateinit var tvPreparation: TextView
    private lateinit var llImpact: LinearLayout
    private lateinit var tvImpact: TextView
    
    private lateinit var geminiModel: GenerativeModel
    private var tempImagePath: String? = null // To hold the path for cleanup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_result)

        // Initialize views
        initializeViews()
        
        // Dependencies are injected by Hilt
        
        // Setup recycle button
        setupRecycleButton()

        // 1. Get file path and model label
        val imagePath = intent.getStringExtra(EXTRA_BITMAP_PATH)
        val modelLabel = intent.getStringExtra(EXTRA_LABEL) ?: "Unknown Object"

        var loadedBitmap: Bitmap? = null

        if (imagePath != null) {
            tempImagePath = imagePath // Store for later cleanup

            // ⭐ 2. Load Bitmap Safely from the File Path ⭐
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                loadedBitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
            } else {
                Log.e("AnalysisResult", "Image file not found at path: $imagePath")
            }
        }

        if (loadedBitmap != null) {
            // Show loading state
            showLoadingState()
            
            // Set up the image in both loading and results states
            ivCapturedObject.setImageBitmap(loadedBitmap)
            ivCapturedObjectResult.setImageBitmap(loadedBitmap)

            Log.d("ImageDisplay", "Displaying image: ${loadedBitmap.width}x${loadedBitmap.height}")

            // 3. Initialize Gemini Model
            try {
                geminiModel = GenerativeModel(
                    modelName = "gemini-2.5-flash",
                    apiKey = BuildConfig.GEMINI_API_KEY
                )
                // 4. Start the Gemini Classification
                callGeminiApi(loadedBitmap, modelLabel)
            } catch (e: Exception) {
                Log.e("Gemini", "Failed to initialize Gemini model: ${e.message}", e)
                showErrorState("AI analysis unavailable. Check API Key.")
            }

        } else {
            showErrorState("Error: Failed to load captured image.")
        }
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)
        
        // Adjust padding for bottom navigation after it's rendered
        ivCapturedObject.post {
            adjustPaddingForBottomNavigation()
        }
    }

    // --- UI State Management ---
    private fun initializeViews() {
        // Loading state views
        llLoadingState = findViewById(R.id.llLoadingState)
        lottieLoading = findViewById(R.id.lottieLoading)
        tvAnalyzingText = findViewById(R.id.tvAnalyzingText)
        ivCapturedObject = findViewById(R.id.ivCapturedObject)
        
        // Results state views
        llResultsState = findViewById(R.id.llResultsState)
        ivCapturedObjectResult = findViewById(R.id.ivCapturedObjectResult)
        tvItemName = findViewById(R.id.tvItemName)
        tvCategory = findViewById(R.id.tvCategory)
        tvBinColor = findViewById(R.id.tvBinColor)
        tvRecyclableStatus = findViewById(R.id.tvRecyclableStatus)
        tvConfidence = findViewById(R.id.tvConfidence)
        llPreparation = findViewById(R.id.llPreparation)
        tvPreparation = findViewById(R.id.tvPreparation)
        llImpact = findViewById(R.id.llImpact)
        tvImpact = findViewById(R.id.tvImpact)
        btnRecycleIt = findViewById(R.id.btnRecycleIt)
        
        // Set up Lottie animation
        lottieLoading.setAnimation("loading_animation.json")
        lottieLoading.loop(true)
        lottieLoading.playAnimation()
    }
    
    private fun showLoadingState() {
        llLoadingState.visibility = View.VISIBLE
        llResultsState.visibility = View.GONE
        lottieLoading.playAnimation()
    }
    
    private fun showResultsState() {
        llLoadingState.visibility = View.GONE
        llResultsState.visibility = View.VISIBLE
        lottieLoading.pauseAnimation()
    }
    
    private fun showErrorState(message: String) {
        llLoadingState.visibility = View.GONE
        llResultsState.visibility = View.VISIBLE
        lottieLoading.pauseAnimation()
        
        // Show error in results state
        tvItemName.text = "Analysis Error"
        tvCategory.text = "Error"
        tvBinColor.text = "Unknown"
        tvRecyclableStatus.text = "Unable to determine"
        tvConfidence.text = "0% Confidence"
        llPreparation.visibility = View.GONE
        llImpact.visibility = View.GONE
    }
    
    private fun applyDynamicColors(category: String, binColor: String, isRecyclable: Boolean, confidence: Double) {
        val context = this
        
        // Category color based on type
        val categoryColor = when (category.uppercase()) {
            "PLASTIC" -> context.getColor(R.color.accent_teal)
            "PAPER" -> context.getColor(R.color.primary_green)
            "GLASS" -> context.getColor(R.color.accent_teal_light)
            "METAL" -> context.getColor(R.color.warning_orange)
            "ELECTRONIC" -> context.getColor(R.color.question_color)
            "ORGANIC" -> context.getColor(R.color.success_green)
            "HAZARDOUS" -> context.getColor(R.color.error_red)
            else -> context.getColor(R.color.text_secondary)
        }
        tvCategory.setTextColor(categoryColor)
        
        // Bin color based on bin type
        val binColorRes = when (binColor.uppercase()) {
            "BLUE" -> context.getColor(R.color.question_color)
            "BROWN" -> context.getColor(R.color.warning_orange)
            "ORANGE" -> context.getColor(R.color.accent_teal)
            "BLACK" -> context.getColor(R.color.text_primary)
            "RED" -> context.getColor(R.color.error_red)
            else -> context.getColor(R.color.text_secondary)
        }
        tvBinColor.setTextColor(binColorRes)
        
        // Recyclable status color
        val recyclableColor = if (isRecyclable) {
            context.getColor(R.color.success_green)
        } else {
            context.getColor(R.color.error_red)
        }
        tvRecyclableStatus.setTextColor(recyclableColor)
        
        // Confidence color based on level
        val confidenceColor = when {
            confidence >= 0.8 -> context.getColor(R.color.success_green)
            confidence >= 0.6 -> context.getColor(R.color.warning_orange)
            else -> context.getColor(R.color.error_red)
        }
        tvConfidence.setTextColor(confidenceColor)
    }
    
    private fun adjustPaddingForBottomNavigation() {
        // Add extra bottom padding to ensure content is not covered by bottom navigation
        val scrollView = findViewById<ScrollView>(R.id.scrollView)
        val contentLayout = scrollView?.getChildAt(0) as? LinearLayout
        contentLayout?.let { layout ->
            val currentPadding = layout.paddingBottom
            val extraPadding = resources.getDimensionPixelSize(R.dimen.extra_bottom_padding)
            val totalBottomPadding = currentPadding + extraPadding
            
            layout.setPadding(
                layout.paddingLeft,
                layout.paddingTop,
                layout.paddingRight,
                totalBottomPadding
            )
        }
    }

    // --- Lifecycle Cleanup ---
    override fun onDestroy() {
        super.onDestroy()
        // Delete the temporary file when the activity is finished
        if (tempImagePath != null) {
            val fileToDelete = File(tempImagePath!!)
            if (fileToDelete.exists()) {
                fileToDelete.delete()
                Log.d("FileCleanup", "Deleted temporary file: $tempImagePath")
            }
        }
    }

    // --- Gemini API Call and Parsing Functions (Unchanged) ---

    private fun callGeminiApi(image: Bitmap, modelLabel: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            // Optimize image size for faster processing
            val optimizedImage = optimizeImageForAnalysis(image)
            
            val prompt = """
                Analyze this waste item for Malaysia recycling. Return ONLY this JSON:
                {
                    "itemName": "Specific item name",
                    "category": "PLASTIC|PAPER|GLASS|METAL|ELECTRONIC|ORGANIC|HAZARDOUS|OTHER",
                    "binColor": "Blue|Brown|Orange|Black|Red",
                    "isRecyclable": true|false,
                    "confidence": 0.85,
                    "preparation": "Brief prep steps",
                    "impact": "CO2 saved if recycled"
                }
                
                Rules: Be specific (e.g., "Plastic Bottle" not "Plastic"). Use Malaysia bins: Blue=Paper, Brown=Glass, Orange=Plastics/Metals, Black=General, Red=Hazardous. Confidence 0.0-1.0. Keep responses concise.
            """.trimIndent()

            try {
                val inputContent = content {
                    image(optimizedImage)
                    text(prompt)
                }

                val response = geminiModel.generateContent(inputContent)
                val responseText = response.text ?: "Gemini Classification Failed"

                withContext(Dispatchers.Main) {
                    parseAndDisplayResult(responseText, modelLabel)
                }
            } catch (e: Exception) {
                Log.e("GeminiError", "API call failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    showErrorState("Classification Failed: ${e.message}")
                }
            }
        }
    }
    
    private fun optimizeImageForAnalysis(originalBitmap: Bitmap): Bitmap {
        // Resize image to optimal size for AI analysis (faster processing)
        val maxSize = 1024
        val width = originalBitmap.width
        val height = originalBitmap.height
        
        return if (width > maxSize || height > maxSize) {
            val scale = minOf(maxSize.toFloat() / width, maxSize.toFloat() / height)
            val newWidth = (width * scale).toInt()
            val newHeight = (height * scale).toInt()
            Bitmap.createScaledBitmap(originalBitmap, newWidth, newHeight, true)
        } else {
            originalBitmap
        }
    }

    private fun parseAndDisplayResult(responseText: String, fallbackLabel: String) {
        try {
            val cleanResponse = responseText.trim()
                .removePrefix("```json")
                .removeSuffix("```")
                .trim()

            val jsonObject = org.json.JSONObject(cleanResponse)

            val itemName = jsonObject.optString("itemName", fallbackLabel)
            val category = jsonObject.optString("category", "OTHER")
            val binColor = jsonObject.optString("binColor", "Black")
            val isRecyclable = jsonObject.optBoolean("isRecyclable", false)
            val confidence = jsonObject.optDouble("confidence", 0.0)
            val preparation = jsonObject.optString("preparation", "No special preparation required")
            val impact = jsonObject.optString("impact", "0 kg CO2")

            // Update UI with parsed data
            tvItemName.text = itemName
            tvCategory.text = category
            tvBinColor.text = binColor
            tvRecyclableStatus.text = if (isRecyclable) "Recyclable" else "Not Recyclable"
            tvConfidence.text = "${(confidence * 100).toInt()}% Confidence"
            
            // Apply dynamic colors based on content
            applyDynamicColors(category, binColor, isRecyclable, confidence)
            
            // Show preparation if available
            if (preparation.isNotEmpty() && preparation != "No special preparation required") {
                llPreparation.visibility = View.VISIBLE
                tvPreparation.text = preparation
            } else {
                llPreparation.visibility = View.GONE
            }
            
            // Show impact if available
            if (impact.isNotEmpty() && impact != "0 kg CO2") {
                llImpact.visibility = View.VISIBLE
                tvImpact.text = impact
            } else {
                llImpact.visibility = View.GONE
            }

            // Switch to results state
            showResultsState()

        } catch (e: Exception) {
            Log.e("JSONParseError", "Failed to parse Gemini response: ${e.message}")
            Log.e("JSONParseError", "Raw response: $responseText")

            // Show error state
            showErrorState("Failed to parse analysis results")
        }
    }
    
    private fun setupRecycleButton() {
        btnRecycleIt.setOnClickListener {
            handleRecycleItem()
        }
    }
    
    private fun handleRecycleItem() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Get current user
                val userResult = userRepository.getCurrentUser()
                if (userResult is com.example.ecosort.data.model.Result.Error) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AnalysisResultActivity, "Please login first", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }
                
                // Always use the active FirebaseAuth user to avoid cross-account writes
                val authUser = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                val firebaseUid = authUser?.uid ?: run {
                    val user = (userResult as com.example.ecosort.data.model.Result.Success).data
                    FirebaseUidHelper.logUserIdentification(user)
                    FirebaseUidHelper.getFirebaseUid(user)
                }
                
                // Get current analysis data
                val itemName = tvItemName.text.toString().replace("Item: ", "")
                val category = tvCategory.text.toString().replace("Category: ", "")
                val binColor = tvBinColor.text.toString().replace("Bin Color: ", "")
                val isRecyclable = tvRecyclableStatus.text.toString().contains("Yes")
                val confidence = tvConfidence.text.toString().replace("Confidence: ", "").replace("%", "").toFloatOrNull() ?: 0f
                
                // Create recycled item data
                val recycledItemData = hashMapOf<String, Any>(
                    "id" to System.currentTimeMillis(),
                    "userId" to firebaseUid, // Use Firebase UID instead of local ID
                    "itemName" to itemName,
                    "category" to category,
                    "binColor" to binColor,
                    "isRecyclable" to isRecyclable,
                    "confidence" to confidence,
                    "recycledDate" to com.google.firebase.Timestamp.now(),
                    "pointsEarned" to 100
                )
                
                // Save recycled item to Firebase
                Log.d("RecycleItem", "Attempting to save recycled item for user: $firebaseUid")
                val saveItemResult = firestoreService.saveRecycledItem(recycledItemData)
                if (saveItemResult is com.example.ecosort.data.model.Result.Error) {
                    Log.e("RecycleItem", "Failed to save recycled item", saveItemResult.exception)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AnalysisResultActivity, "Failed to save recycled item: ${saveItemResult.exception.message}", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                Log.d("RecycleItem", "Recycled item saved successfully")
                
                // Update user points
                val pointsResult = firestoreService.getUserPoints(firebaseUid)
                val currentPoints = if (pointsResult is com.example.ecosort.data.model.Result.Success && pointsResult.data != null) {
                    (pointsResult.data["totalPoints"] as? Long)?.toInt() ?: 0
                } else {
                    0
                }
                
                val newTotalPoints = currentPoints + 100
                val pointsData = hashMapOf<String, Any>(
                    "userId" to firebaseUid, // Use Firebase UID instead of local ID
                    "totalPoints" to newTotalPoints.toLong(), // Convert to Long for Firebase consistency
                    "lastUpdated" to com.google.firebase.Timestamp.now()
                )
                
                Log.d("RecycleItem", "Attempting to save points: $currentPoints + 100 = $newTotalPoints for user: $firebaseUid")
                val savePointsResult = firestoreService.saveUserPoints(pointsData)
                if (savePointsResult is com.example.ecosort.data.model.Result.Error) {
                    Log.e("RecycleItem", "Failed to save points", savePointsResult.exception)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@AnalysisResultActivity, "Failed to update points: ${savePointsResult.exception.message}", Toast.LENGTH_LONG).show()
                    }
                    return@launch
                }
                Log.d("RecycleItem", "Points saved successfully: $newTotalPoints")
                
                // Create points transaction
                val transactionData = hashMapOf<String, Any>(
                    "id" to System.currentTimeMillis(),
                    "userId" to firebaseUid, // Use Firebase UID instead of local ID
                    "points" to 100,
                    "type" to "earned",
                    "description" to "Recycled: $itemName",
                    "timestamp" to com.google.firebase.Timestamp.now()
                )
                
                val saveTransactionResult = firestoreService.savePointsTransaction(transactionData)
                if (saveTransactionResult is com.example.ecosort.data.model.Result.Error) {
                    Log.w("RecycleItem", "Failed to save points transaction", saveTransactionResult.exception)
                } else {
                    Log.d("RecycleItem", "Points transaction saved successfully")
                }
                
                // All operations successful! Navigate to success screen
                withContext(Dispatchers.Main) {
                    Log.d("RecycleItem", "All save operations completed. Navigating to success screen.")
                    Toast.makeText(this@AnalysisResultActivity, "Item recycled successfully! +100 points", Toast.LENGTH_SHORT).show()
                    
                    val intent = Intent(this@AnalysisResultActivity, RecyclingSuccessActivity::class.java)
                    intent.putExtra("item_name", itemName)
                    intent.putExtra("points_earned", 100)
                    startActivity(intent)
                    finish()
                }
                
            } catch (e: Exception) {
                Log.e("RecycleItem", "Error recycling item", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@AnalysisResultActivity, "Error recycling item: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}