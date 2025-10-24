// AnalysisResultActivity.kt
package com.example.ecosort

import android.graphics.Bitmap
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
import android.view.MenuItem

class AnalysisResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_BITMAP = "extra_bitmap"
        const val EXTRA_LABEL = "extra_label"
    }

    private lateinit var ivCapturedObject: ImageView
    private lateinit var tvGeminiOutput: TextView
    private lateinit var geminiModel: GenerativeModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_analysis_result)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        ivCapturedObject = findViewById(R.id.ivCapturedObject)
        tvGeminiOutput = findViewById(R.id.tvGeminiOutput)

        // 1. Get data passed from the previous Activity
        val capturedBitmap = intent.getParcelableExtra<Bitmap>(EXTRA_BITMAP)
        val modelLabel = intent.getStringExtra(EXTRA_LABEL) ?: "Unknown Object"

        if (capturedBitmap != null) {
            ivCapturedObject.setImageBitmap(capturedBitmap)
            tvGeminiOutput.text = "Detecting $modelLabel..."

            // 2. Initialize Gemini Model (Must be repeated here if not using Hilt)
            geminiModel = GenerativeModel(
                modelName = "gemini-2.5-flash",
                apiKey = BuildConfig.GEMINI_API_KEY
            )

            // 3. Start the Gemini Classification
            callGeminiApi(capturedBitmap, modelLabel)
        } else {
            tvGeminiOutput.text = "Error: Failed to load captured image."
        }
    }

    private fun callGeminiApi(image: Bitmap, modelLabel: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val prompt = "You are a waste classification expert for Malaysia. The object has been preliminarily detected as '$modelLabel'. Determine the correct bin: Blue (Paper), Brown (Glass), or Orange (Plastics/Metals). If non-recyclable, state 'General Waste'. Provide ONLY a clean, short instruction for the user in this format: 'Object Type - Bin Color (Material)'."

            try {
                val inputContent = content {
                    image(image)
                    text(prompt)
                }

                val response = geminiModel.generateContent(inputContent)
                val finalClassification = response.text ?: "Gemini Classification Failed"

                withContext(Dispatchers.Main) {
                    tvGeminiOutput.text = finalClassification // Update UI with the final result
                }
            } catch (e: Exception) {
                Log.e("GeminiError", "API call failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    tvGeminiOutput.text = "Classification Failed: ${e.message}"
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Check if the item clicked is the Home (Up) button
        if (item.itemId == android.R.id.home) {
            // This command closes the current Activity and returns to the previous one (ObjectDetectionActivity)
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}