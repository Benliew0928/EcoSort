package com.example.ecosort.community

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.ecosort.R
import com.example.ecosort.data.model.InputType
import com.example.ecosort.data.model.PostType
import com.example.ecosort.data.model.Result
import com.example.ecosort.data.repository.CommunityRepository
import com.example.ecosort.databinding.ActivityCreatePostBinding
import com.example.ecosort.utils.BottomNavigationHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CreatePostActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreatePostBinding

    @Inject
    lateinit var communityRepository: CommunityRepository

    private var selectedMediaUri: Uri? = null
    private var selectedInputType: InputType = InputType.TEXT

    // Activity result launchers - using modern PickVisualMedia approach
    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            android.util.Log.d("CreatePostActivity", "Original URI: $uri")
            // Copy the image to app storage to avoid permission issues
            val copiedUri = copyImageToAppStorage(it)
            android.util.Log.d("CreatePostActivity", "Copied URI: $copiedUri")
            selectedMediaUri = copiedUri
            selectedInputType = InputType.IMAGE
            showMediaPreview(copiedUri)
        }
    }

    private val cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && selectedMediaUri != null) {
            selectedInputType = InputType.IMAGE
            showMediaPreview(selectedMediaUri!!)
        }
    }

    private val videoLauncher = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            android.util.Log.d("CreatePostActivity", "Video URI: $uri")
            selectedMediaUri = it
            selectedInputType = InputType.VIDEO
            showMediaPreview(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreatePostBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupPostTypeSpinner()
        setupMediaButtons()
        setupClickListener()
        
        // Add bottom navigation
        BottomNavigationHelper.addBottomNavigationToActivity(this)
        
        // Ensure content can scroll above bottom nav by adding extra bottom padding
        binding.root.post {
            val extraBottomDp = 120
            val extraBottomPx = (extraBottomDp * resources.displayMetrics.density).toInt()
            val content = findViewById<android.view.View>(R.id.contentContainer)
            content?.setPadding(
                content.paddingLeft,
                content.paddingTop,
                content.paddingRight,
                content.paddingBottom + extraBottomPx
            )
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Create New Post"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }

    private fun setupPostTypeSpinner() {
        val postTypes = PostType.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, postTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerPostType.adapter = adapter
    }
    
    private fun setupMediaButtons() {
        binding.btnGallery.setOnClickListener {
            // No permission check needed for PickVisualMedia on Android 13+
            galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        binding.btnCamera.setOnClickListener {
            if (checkCameraPermission()) {
                openCamera()
            }
        }

        binding.btnVideo.setOnClickListener {
            // No permission check needed for PickVisualMedia on Android 13+
            videoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly))
        }
    }

    private fun setupClickListener() {
        binding.btnSubmitPost.setOnClickListener {
            createPost()
        }
    }

    private fun createPost() {
        val title = binding.editTextTitle.text.toString().trim()
        val content = binding.editTextContent.text.toString().trim()
        val postType = PostType.valueOf(binding.spinnerPostType.selectedItem.toString())
        val tagsText = binding.editTextTags.text.toString().trim()
        
        // Parse tags from comma-separated string
        val tags = if (tagsText.isNotEmpty()) {
            tagsText.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        } else {
            emptyList()
        }

        if (title.isEmpty() || content.isEmpty()) {
            Toast.makeText(this, "Title and content cannot be empty", Toast.LENGTH_SHORT).show()
            return
        }

        binding.progressBar.visibility = View.VISIBLE
        binding.btnSubmitPost.isEnabled = false

        lifecycleScope.launch {
            try {
                val (imageUrls, videoUrl) = if (selectedMediaUri != null) {
                    when (selectedInputType) {
                        InputType.IMAGE -> {
                            // Upload image to Firebase Storage
                            val fileName = "post_image_${System.currentTimeMillis()}.jpg"
                            android.util.Log.d("CreatePostActivity", "Uploading image URI: $selectedMediaUri")
                            
                            val uploadResult = communityRepository.uploadImageToFirebase(selectedMediaUri!!, fileName)
                            when (uploadResult) {
                                is com.example.ecosort.data.model.Result.Success -> {
                                    android.util.Log.d("CreatePostActivity", "Image upload successful: ${uploadResult.data}")
                                    Pair(listOf(uploadResult.data), null)
                                }
                                is com.example.ecosort.data.model.Result.Error -> {
                                    android.util.Log.e("CreatePostActivity", "Image upload failed: ${uploadResult.exception.message}")
                                    Pair(emptyList(), null)
                                }
                                is com.example.ecosort.data.model.Result.Loading -> {
                                    Pair(emptyList(), null)
                                }
                            }
                        }
                        InputType.VIDEO -> {
                            // Upload video to Firebase Storage
                            val fileName = "post_video_${System.currentTimeMillis()}.mp4"
                            android.util.Log.d("CreatePostActivity", "Uploading video URI: $selectedMediaUri")
                            
                            val uploadResult = communityRepository.uploadVideoToFirebase(selectedMediaUri!!, fileName)
                            when (uploadResult) {
                                is com.example.ecosort.data.model.Result.Success -> {
                                    android.util.Log.d("CreatePostActivity", "Video upload successful: ${uploadResult.data}")
                                    Pair(emptyList(), uploadResult.data)
                                }
                                is com.example.ecosort.data.model.Result.Error -> {
                                    android.util.Log.e("CreatePostActivity", "Video upload failed: ${uploadResult.exception.message}")
                                    Pair(emptyList(), null)
                                }
                                is com.example.ecosort.data.model.Result.Loading -> {
                                    Pair(emptyList(), null)
                                }
                            }
                        }
                        else -> Pair(emptyList(), null)
                    }
                } else {
                    // No media for text-only posts
                    Pair(emptyList(), null)
                }
                
                val result = communityRepository.addCommunityPost(
                    title = title,
                    content = content,
                    postType = postType,
                    inputType = selectedInputType,
                    imageUrls = imageUrls,
                    videoUrl = videoUrl,
                    tags = tags
                )
                when (result) {
                    is Result.Success -> {
                        Toast.makeText(this@CreatePostActivity, "Post created successfully!", Toast.LENGTH_SHORT).show()
                        // Set result to indicate post was created
                        setResult(RESULT_OK)
                        finish()
                    }
                    is Result.Error -> {
                        Toast.makeText(this@CreatePostActivity, "Error creating post: ${result.exception.message}", Toast.LENGTH_LONG).show()
                        binding.progressBar.visibility = View.GONE
                        binding.btnSubmitPost.isEnabled = true
                    }
                    is Result.Loading -> {
                        // Should not happen for suspend fun
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                android.util.Log.d("CreatePostActivity", "Post creation cancelled")
                binding.progressBar.visibility = View.GONE
                binding.btnSubmitPost.isEnabled = true
            } catch (e: Exception) {
                android.util.Log.e("CreatePostActivity", "Error creating post", e)
                Toast.makeText(this@CreatePostActivity, "Error creating post: ${e.message}", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
                binding.btnSubmitPost.isEnabled = true
            }
        }
    }

    // Storage permission check removed - not needed for PickVisualMedia on Android 13+

    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)
            false
        }
    }

    private fun openCamera() {
        val photoUri = createImageUri()
        selectedMediaUri = photoUri
        cameraLauncher.launch(photoUri)
    }

    private fun createImageUri(): Uri {
        val contentValues = android.content.ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "post_image_${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)!!
    }

    private fun copyImageToAppStorage(sourceUri: Uri): Uri {
        return try {
            val inputStream = contentResolver.openInputStream(sourceUri)
            val fileName = "post_image_${System.currentTimeMillis()}.jpg"
            
            // Use cache directory which is more accessible
            val cacheDir = File(cacheDir, "community_images")
            if (!cacheDir.exists()) {
                cacheDir.mkdirs()
            }
            val destFile = File(cacheDir, fileName)
            
            android.util.Log.d("CreatePostActivity", "Copying from: $sourceUri")
            android.util.Log.d("CreatePostActivity", "Copying to: ${destFile.absolutePath}")
            
            inputStream?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            
            android.util.Log.d("CreatePostActivity", "Image copied successfully to: ${destFile.absolutePath}")
            android.util.Log.d("CreatePostActivity", "File exists: ${destFile.exists()}")
            android.util.Log.d("CreatePostActivity", "File size: ${destFile.length()} bytes")
            
            Uri.fromFile(destFile)
        } catch (e: Exception) {
            android.util.Log.e("CreatePostActivity", "Error copying image", e)
            sourceUri // Return original URI if copying fails
        }
    }

    private fun showMediaPreview(uri: Uri) {
        binding.imageMediaPreview.visibility = View.VISIBLE
        binding.textMediaInfo.visibility = View.VISIBLE
        
        when (selectedInputType) {
            InputType.IMAGE -> {
                binding.textMediaInfo.text = "Image selected"
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_image_placeholder)
                    .error(R.drawable.ic_image_placeholder)
                    .into(binding.imageMediaPreview)
            }
            InputType.VIDEO -> {
                binding.textMediaInfo.text = "Video selected"
                // For video, we'll show a video thumbnail or placeholder
                Glide.with(this)
                    .load(uri)
                    .placeholder(R.drawable.ic_video)
                    .error(R.drawable.ic_video)
                    .into(binding.imageMediaPreview)
            }
            else -> {
                binding.textMediaInfo.text = "Media selected"
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            101 -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    openCamera()
                } else {
                    Toast.makeText(this, "Camera permission required to take photos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onBackPressed() {
        super.onBackPressed()
    }
}
