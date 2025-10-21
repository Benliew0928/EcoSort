package com.example.ecosort.community

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import com.example.ecosort.databinding.ActivityVideoPlayerBinding

class VideoPlayerActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityVideoPlayerBinding
    private lateinit var videoView: VideoView
    private lateinit var mediaController: MediaController
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVideoPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupVideoPlayer()
        
        // Get video URL from intent
        val videoUrl = intent.getStringExtra("video_url")
        if (videoUrl != null && videoUrl.isNotEmpty()) {
            android.util.Log.d("VideoPlayerActivity", "Received video URL: $videoUrl")
            playVideo(videoUrl)
        } else {
            android.util.Log.e("VideoPlayerActivity", "No video URL provided")
            android.widget.Toast.makeText(this, "No video URL provided", android.widget.Toast.LENGTH_SHORT).show()
            finish()
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Video Player"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
    }
    
    private fun setupVideoPlayer() {
        videoView = binding.videoView
        mediaController = MediaController(this)
        mediaController.setAnchorView(videoView)
        videoView.setMediaController(mediaController)
        
        // Set video view properties for better display
        videoView.setZOrderOnTop(false)
        videoView.setZOrderMediaOverlay(false)
        
        // Handle video completion
        videoView.setOnCompletionListener {
            binding.progressBar.visibility = View.GONE
        }
        
        // Handle video preparation
        videoView.setOnPreparedListener { mediaPlayer ->
            binding.progressBar.visibility = View.GONE
            
            // Get video dimensions
            val videoWidth = mediaPlayer.videoWidth
            val videoHeight = mediaPlayer.videoHeight
            android.util.Log.d("VideoPlayerActivity", "Video dimensions: ${videoWidth}x${videoHeight}")
            
            // Set proper video scaling - fit to screen without cropping
            mediaPlayer.setVideoScalingMode(android.media.MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT)
            
            // Adjust video view size to maintain aspect ratio (with delay to ensure container is ready)
            binding.videoView.post {
                adjustVideoViewSize(videoWidth, videoHeight)
            }
            
            mediaPlayer.start()
        }
        
        // Handle video errors
        videoView.setOnErrorListener { _, what, extra ->
            binding.progressBar.visibility = View.GONE
            android.util.Log.e("VideoPlayerActivity", "Video error: what=$what, extra=$extra")
            android.widget.Toast.makeText(this, "Error playing video: $what", android.widget.Toast.LENGTH_LONG).show()
            true
        }
        
        // Handle video info
        videoView.setOnInfoListener { _, what, extra ->
            android.util.Log.d("VideoPlayerActivity", "Video info: what=$what, extra=$extra")
            when (what) {
                android.media.MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> {
                    binding.progressBar.visibility = View.GONE
                }
            }
            true
        }
    }
    
    private fun adjustVideoViewSize(videoWidth: Int, videoHeight: Int) {
        if (videoWidth > 0 && videoHeight > 0) {
            val containerWidth = binding.videoView.width
            val containerHeight = binding.videoView.height
            
            if (containerWidth > 0 && containerHeight > 0) {
                val videoAspectRatio = videoWidth.toFloat() / videoHeight.toFloat()
                val containerAspectRatio = containerWidth.toFloat() / containerHeight.toFloat()
                
                val layoutParams = binding.videoView.layoutParams
                
                if (videoAspectRatio > containerAspectRatio) {
                    // Video is wider - fit to width
                    layoutParams.width = containerWidth
                    layoutParams.height = (containerWidth / videoAspectRatio).toInt()
                } else {
                    // Video is taller - fit to height
                    layoutParams.height = containerHeight
                    layoutParams.width = (containerHeight * videoAspectRatio).toInt()
                }
                
                binding.videoView.layoutParams = layoutParams
                android.util.Log.d("VideoPlayerActivity", "Adjusted video size: ${layoutParams.width}x${layoutParams.height}")
            }
        }
    }
    
    private fun playVideo(videoUrl: String) {
        binding.progressBar.visibility = View.VISIBLE
        
        try {
            android.util.Log.d("VideoPlayerActivity", "Playing video: $videoUrl")
            val uri = Uri.parse(videoUrl)
            videoView.setVideoURI(uri)
            
            // Request focus for better video rendering
            videoView.requestFocus()
        } catch (e: Exception) {
            binding.progressBar.visibility = View.GONE
            android.util.Log.e("VideoPlayerActivity", "Error setting video URI", e)
            android.widget.Toast.makeText(this, "Error loading video: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onPause() {
        super.onPause()
        if (::videoView.isInitialized) {
            videoView.pause()
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (::videoView.isInitialized && !videoView.isPlaying) {
            videoView.start()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (::videoView.isInitialized) {
            videoView.stopPlayback()
        }
    }
}
