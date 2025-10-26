package com.example.ecosort

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri // NEW Import for file handling
import android.os.Bundle
import android.util.Log
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File // NEW Import for file handling

class AnalysisResultActivity : AppCompatActivity() {

    companion object {
        // ⭐ CRITICAL CHANGE: Use path instead of Parcelable Bitmap ⭐
        const val EXTRA_BITMAP_PATH = "com.example.ecosort.EXTRA_BITMAP_PATH"
        const val EXTRA_LABEL = "extra_label"
    }

    private lateinit var ivCapturedObject: ImageView
    private lateinit var tvGeminiOutput: TextView
    private lateinit var geminiModel: GenerativeModel

    private var tempImagePath: String? = null // To hold the path for cleanup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_result)

        ivCapturedObject = findViewById(R.id.ivCapturedObject)
        tvGeminiOutput = findViewById(R.id.tvGeminiOutput)

        // 1. Get file path and model label
        val imagePath = intent.getStringExtra(EXTRA_BITMAP_PATH) // ⭐ GET STRING PATH ⭐
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
            // Ensure proper image display
            ivCapturedObject.setImageBitmap(loadedBitmap)
            ivCapturedObject.scaleType = ImageView.ScaleType.FIT_CENTER // Changed scale type for clarity
            ivCapturedObject.adjustViewBounds = true

            Log.d("ImageDisplay", "Displaying image: ${loadedBitmap.width}x${loadedBitmap.height}")
            tvGeminiOutput.text = "Detecting $modelLabel..."

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
                tvGeminiOutput.text = "AI analysis unavailable. Check API Key."
            }

        } else {
            tvGeminiOutput.text = "Error: Failed to load captured image."
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
            val prompt = """
                You are an expert waste classification specialist for Malaysia's recycling system. Analyze this image of an object preliminarily detected as '$modelLabel'.

                Provide a comprehensive analysis in this EXACT JSON format:
                {
                    "itemName": "Specific item name",
                    "category": "PLASTIC|PAPER|GLASS|METAL|ELECTRONIC|ORGANIC|HAZARDOUS|OTHER",
                    "binColor": "Blue|Brown|Orange|Black|Red",
                    "binType": "Paper|Glass|Plastics/Metals|General Waste|Hazardous",
                    "isRecyclable": true|false,
                    "confidence": 0.85,
                    "preparation": "Specific preparation steps required",
                    "environmentalImpact": "CO2 saved in kg if recycled",
                    "alternativeDisposal": "Alternative disposal method if not recyclable",
                    "recyclingTips": "Additional helpful tips",
                    "malaysiaSpecific": "Malaysia-specific recycling information"
                }

                Rules:
                1. Be specific about the item (e.g., "Plastic Water Bottle" not just "Plastic")
                2. Use Malaysia's bin system: Blue (Paper), Brown (Glass), Orange (Plastics/Metals), Black (General Waste), Red (Hazardous)
                3. Provide realistic confidence scores (0.0-1.0)
                4. Include practical preparation steps
                5. Give environmental impact in kg CO2 saved
                6. Provide Malaysia-specific recycling information
                7. Return ONLY the JSON, no additional text
            """.trimIndent()

            try {
                val inputContent = content {
                    image(image)
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
                    tvGeminiOutput.text = "Classification Failed: ${e.message}"
                }
            }
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
            val binType = jsonObject.optString("binType", "General Waste")
            val isRecyclable = jsonObject.optBoolean("isRecyclable", false)
            val confidence = jsonObject.optDouble("confidence", 0.0)
            val preparation = jsonObject.optString("preparation", "No special preparation required")
            val environmentalImpact = jsonObject.optString("environmentalImpact", "0 kg CO2")
            val alternativeDisposal = jsonObject.optString("alternativeDisposal", "Dispose in general waste")
            val recyclingTips = jsonObject.optString("recyclingTips", "Check local recycling guidelines")
            val malaysiaSpecific = jsonObject.optString("malaysiaSpecific", "Follow local municipality guidelines")

            // Create enhanced display text
            val displayText = buildString {
                appendLine("🗑️ **$itemName**")
                appendLine("📦 Category: $category")
                appendLine("🎨 Bin: $binColor ($binType)")
                appendLine("♻️ Recyclable: ${if (isRecyclable) "Yes" else "No"}")
                appendLine("📊 Confidence: ${(confidence * 100).toInt()}%")
                appendLine()
                appendLine("🔧 **Preparation:**")
                appendLine(preparation)
                appendLine()
                appendLine("🌱 **Environmental Impact:**")
                appendLine("$environmentalImpact saved if recycled")
                appendLine()
                if (!isRecyclable) {
                    appendLine("⚠️ **Alternative Disposal:**")
                    appendLine(alternativeDisposal)
                    appendLine()
                }
                appendLine("💡 **Tips:**")
                appendLine(recyclingTips)
                appendLine()
                appendLine("🇲🇾 **Malaysia Specific:**")
                appendLine(malaysiaSpecific)
            }

            tvGeminiOutput.text = displayText

        } catch (e: Exception) {
            Log.e("JSONParseError", "Failed to parse Gemini response: ${e.message}")
            Log.e("JSONParseError", "Raw response: $responseText")

            // Fallback to simple display if JSON parsing fails
            tvGeminiOutput.text = buildString {
                appendLine("🗑️ **$fallbackLabel**")
                appendLine("📦 Analysis completed")
                appendLine("🎨 Bin: Check response below")
                appendLine()
                appendLine("📝 **Raw Analysis:**")
                appendLine(responseText)
            }
        }
    }
}