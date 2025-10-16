package com.example.ecosort.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioTrack
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

class VoicePlayer(private val context: Context) {
    
    private var mediaPlayer: MediaPlayer? = null
    private var isPlaying = false
    private var currentFile: File? = null
    private var audioManager: AudioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    
    fun playVoiceMessage(file: File, onCompletion: () -> Unit = {}) {
        try {
            Log.d("VoicePlayer", "Attempting to play voice message: ${file.absolutePath}")
            
            // Check if file exists
            if (!file.exists()) {
                Log.e("VoicePlayer", "Voice file does not exist: ${file.absolutePath}")
                onCompletion()
                return
            }
            
            // Check file size
            if (file.length() == 0L) {
                Log.e("VoicePlayer", "Voice file is empty: ${file.absolutePath}")
                onCompletion()
                return
            }
            
            stopPlaying() // Stop any current playback
            
                    // Try to request audio focus, but don't fail if we can't get it
                    val hasAudioFocus = requestAudioFocus()
                    if (!hasAudioFocus) {
                        Log.w("VoicePlayer", "Could not get audio focus, trying to play anyway")
                    }
            
            // Ensure audio routing is correct - try multiple approaches
            try {
                // Method 1: Force through speaker
                audioManager.isSpeakerphoneOn = true
                audioManager.mode = AudioManager.MODE_NORMAL
                
                // Method 2: Set audio stream type explicitly for music
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
                
                // Method 3: Ensure music volume is up
                val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                
                Log.d("VoicePlayer", "Audio routing: speakerphone=${audioManager.isSpeakerphoneOn}, mode=${audioManager.mode}")
                Log.d("VoicePlayer", "Volume: current=$currentVolume, max=$maxVolume")
                Log.d("VoicePlayer", "Audio focus: ${audioManager.isMusicActive}")
                
            } catch (e: Exception) {
                Log.e("VoicePlayer", "Error setting audio routing", e)
            }
            
            mediaPlayer = MediaPlayer().apply {
                // Try multiple audio stream types for better compatibility
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        setAudioAttributes(
                            AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                                .build()
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        setAudioStreamType(AudioManager.STREAM_MUSIC)
                    }
                    
                    // Use STREAM_MUSIC for better compatibility
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_MUSIC)
                    
                    Log.d("VoicePlayer", "MediaPlayer audio stream set to STREAM_MUSIC")
                } catch (e: Exception) {
                    Log.e("VoicePlayer", "Error setting MediaPlayer audio attributes", e)
                }
                
                setDataSource(file.absolutePath)
                
                setOnPreparedListener { mp ->
                    try {
                        this@VoicePlayer.isPlaying = true
                        
                        // Set volume to maximum to ensure audio is heard
                        mp.setVolume(1.0f, 1.0f)
                        
                        // Ensure audio focus is maintained
                        if (!audioManager.isMusicActive) {
                            Log.w("VoicePlayer", "Audio not active, requesting focus again")
                            requestAudioFocus()
                        }
                        
                        // Start playback
                        mp.start()
                        Log.d("VoicePlayer", "Started playing: ${file.name}, duration: ${mp.duration}ms")
                        Log.d("VoicePlayer", "Audio active after start: ${audioManager.isMusicActive}")
                    } catch (e: Exception) {
                        Log.e("VoicePlayer", "Error starting playback", e)
                        this@VoicePlayer.isPlaying = false
                        onCompletion()
                    }
                }
                
                setOnCompletionListener { mp ->
                    Log.d("VoicePlayer", "Playback completed: ${file.name}")
                    this@VoicePlayer.isPlaying = false
                    releaseMediaPlayer()
                    abandonAudioFocus()
                    onCompletion()
                }
                
                setOnErrorListener { mp, what, extra ->
                    Log.e("VoicePlayer", "Error playing audio: what=$what, extra=$extra")
                    this@VoicePlayer.isPlaying = false
                    releaseMediaPlayer()
                    abandonAudioFocus()
                    onCompletion()
                    true
                }
                
                setOnSeekCompleteListener { mp ->
                    Log.d("VoicePlayer", "Seek completed")
                }
                
                prepareAsync()
            }
            
            currentFile = file
            
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error playing voice message", e)
            isPlaying = false
            releaseMediaPlayer()
            abandonAudioFocus()
            onCompletion()
        }
    }
    
    fun stopPlaying() {
        try {
            Log.d("VoicePlayer", "Stopping playback")
            isPlaying = false
            releaseMediaPlayer()
            abandonAudioFocus()
            currentFile = null
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error stopping playback", e)
        }
    }
    
    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.apply {
                if (isPlaying) {
                    stop()
                }
                release()
            }
            mediaPlayer = null
            Log.d("VoicePlayer", "MediaPlayer released")
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error releasing MediaPlayer", e)
        }
    }
    
    private fun requestAudioFocus(): Boolean {
        return try {
            // Try a more permissive audio focus request
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED)
                            .build()
                    )
                    .setOnAudioFocusChangeListener { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d("VoicePlayer", "Audio focus gained")
                                resume()
                            }
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                Log.d("VoicePlayer", "Audio focus lost - stopping")
                                stopPlaying()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                Log.d("VoicePlayer", "Audio focus lost transient - continuing anyway")
                                // Don't pause on transient loss - keep playing
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                Log.d("VoicePlayer", "Audio focus lost transient can duck - continuing")
                            }
                        }
                    }
                    .build()

                val result = audioManager.requestAudioFocus(audioFocusRequest!!)
                Log.d("VoicePlayer", "Audio focus request result: $result")
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                val result = audioManager.requestAudioFocus(
                    { focusChange ->
                        when (focusChange) {
                            AudioManager.AUDIOFOCUS_GAIN -> {
                                Log.d("VoicePlayer", "Audio focus gained")
                                resume()
                            }
                            AudioManager.AUDIOFOCUS_LOSS -> {
                                Log.d("VoicePlayer", "Audio focus lost - stopping")
                                stopPlaying()
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                                Log.d("VoicePlayer", "Audio focus lost transient - continuing anyway")
                                // Don't pause on transient loss - keep playing
                            }
                            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                                Log.d("VoicePlayer", "Audio focus lost transient can duck - continuing")
                            }
                        }
                    },
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                )
                Log.d("VoicePlayer", "Audio focus request result: $result")
                result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error requesting audio focus", e)
            false
        }
    }
    
    private fun abandonAudioFocus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { request ->
                    audioManager.abandonAudioFocusRequest(request)
                    audioFocusRequest = null
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
            Log.d("VoicePlayer", "Audio focus abandoned")
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error abandoning audio focus", e)
        }
    }
    
    fun isPlaying(): Boolean = isPlaying
    
    fun isPlayingFile(file: File): Boolean {
        return isPlaying && currentFile?.absolutePath == file.absolutePath
    }
    
    fun getCurrentFile(): File? = currentFile
    
    fun getDuration(): Int {
        return try {
            mediaPlayer?.duration ?: 0
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error getting duration", e)
            0
        }
    }
    
    fun getCurrentPosition(): Int {
        return try {
            mediaPlayer?.currentPosition ?: 0
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error getting current position", e)
            0
        }
    }
    
    fun pause() {
        try {
            mediaPlayer?.let { mp ->
                if (isPlaying && mp.isPlaying) {
                    mp.pause()
                    isPlaying = false
                    Log.d("VoicePlayer", "Playback paused")
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error pausing playback", e)
        }
    }
    
    fun resume() {
        try {
            mediaPlayer?.let { mp ->
                if (!isPlaying && !mp.isPlaying) {
                    mp.start()
                    isPlaying = true
                    Log.d("VoicePlayer", "Playback resumed")
                }
            }
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error resuming playback", e)
        }
    }
    
    /**
     * Test audio output with a simple beep to verify audio system is working
     */
    fun testAudioOutput() {
        try {
            Log.d("VoicePlayer", "Testing audio output...")
            
            // Test audio manager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val isMusicActive = audioManager.isMusicActive
            
            Log.d("VoicePlayer", "Audio Test - Volume: $currentVolume/$maxVolume, MusicActive: $isMusicActive")
            
            // Test speakerphone
            audioManager.isSpeakerphoneOn = true
            Log.d("VoicePlayer", "Audio Test - Speakerphone: ${audioManager.isSpeakerphoneOn}")
            
            // Test audio focus
            val focusResult = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioManager.requestAudioFocus(audioFocusRequest!!)
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
            }
            
            Log.d("VoicePlayer", "Audio Test - Focus Result: $focusResult")
            
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error testing audio output", e)
        }
    }
    
    /**
     * Play a simple test beep to verify audio output is working
     */
    fun playTestBeep() {
        try {
            Log.d("VoicePlayer", "Playing test beep...")
            
            // Create a simple 440Hz tone for 1 second
            val sampleRate = 44100
            val duration = 1.0 // seconds
            val frequency = 440.0 // Hz
            val numSamples = (duration * sampleRate).toInt()
            val samples = ShortArray(numSamples)
            
            for (i in 0 until numSamples) {
                samples[i] = (Math.sin(2 * Math.PI * i / (sampleRate / frequency)) * 0.3 * Short.MAX_VALUE).toInt().toShort()
            }
            
            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                .setAudioFormat(
                    android.media.AudioFormat.Builder()
                        .setEncoding(android.media.AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(android.media.AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(numSamples * 2)
                .build()
            
            audioTrack.play()
            audioTrack.write(samples, 0, numSamples)
            
            // Stop after 1 second
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                audioTrack.stop()
                audioTrack.release()
                Log.d("VoicePlayer", "Test beep completed")
            }, 1000)
            
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error playing test beep", e)
        }
    }
    
    /**
     * Test if a recorded voice file has actual audio content
     */
    fun testVoiceFileContent(file: File) {
        try {
            Log.d("VoicePlayer", "Testing voice file content: ${file.absolutePath}")
            Log.d("VoicePlayer", "File size: ${file.length()} bytes")
            Log.d("VoicePlayer", "File exists: ${file.exists()}")
            
            if (!file.exists() || file.length() == 0L) {
                Log.e("VoicePlayer", "File is empty or doesn't exist")
                return
            }
            
            // Try to read the file as raw audio data
            val inputStream = file.inputStream()
            val buffer = ByteArray(1024)
            var totalBytes = 0
            var nonZeroBytes = 0
            
            while (inputStream.read(buffer) != -1) {
                totalBytes += buffer.size
                for (byte in buffer) {
                    if (byte != 0.toByte()) {
                        nonZeroBytes++
                    }
                }
            }
            inputStream.close()
            
            val nonZeroPercentage = (nonZeroBytes.toFloat() / totalBytes * 100).toInt()
            Log.d("VoicePlayer", "File analysis: $nonZeroBytes/$totalBytes bytes are non-zero ($nonZeroPercentage%)")
            
            if (nonZeroPercentage < 10) {
                Log.w("VoicePlayer", "File appears to be mostly silence - possible recording issue")
            } else {
                Log.d("VoicePlayer", "File contains audio data - possible playback issue")
            }
            
            // Try to get audio metadata
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                
                val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                val bitrate = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_BITRATE)
                val mimeType = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                
                Log.d("VoicePlayer", "Audio metadata - Duration: ${duration}ms, Bitrate: $bitrate, MIME: $mimeType")
                
                retriever.release()
            } catch (e: Exception) {
                Log.e("VoicePlayer", "Error reading audio metadata", e)
            }
            
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error testing voice file content", e)
        }
    }
    
    /**
     * Test audio output with different methods
     */
    fun testAudioOutputMethods() {
        try {
            Log.d("VoicePlayer", "Testing multiple audio output methods...")
            
            // Test 1: AudioTrack with generated tone
            Log.d("VoicePlayer", "Test 1: AudioTrack tone")
            playTestBeep()
            
            // Test 2: MediaPlayer with system notification sound
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    Log.d("VoicePlayer", "Test 2: MediaPlayer system sound")
                    val mediaPlayer = MediaPlayer().apply {
                        setAudioStreamType(AudioManager.STREAM_MUSIC)
                        setDataSource(android.provider.Settings.System.DEFAULT_NOTIFICATION_URI.toString())
                        setOnPreparedListener { mp ->
                            mp.start()
                            Log.d("VoicePlayer", "System notification sound played")
                        }
                        setOnErrorListener { mp, what, extra ->
                            Log.e("VoicePlayer", "Error playing system sound: $what, $extra")
                            true
                        }
                        prepareAsync()
                    }
                } catch (e: Exception) {
                    Log.e("VoicePlayer", "Error testing system sound", e)
                }
            }, 2000)
            
            // Test 3: AudioManager tone generation
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                try {
                    Log.d("VoicePlayer", "Test 3: AudioManager tone")
                    val toneGenerator = android.media.ToneGenerator(AudioManager.STREAM_MUSIC, 100)
                    toneGenerator.startTone(android.media.ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 1000)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        toneGenerator.release()
                        Log.d("VoicePlayer", "ToneGenerator test completed")
                    }, 1000)
                } catch (e: Exception) {
                    Log.e("VoicePlayer", "Error testing ToneGenerator", e)
                }
            }, 4000)
            
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error testing audio output methods", e)
        }
    }
    
    /**
     * Play voice message using AudioTrack (placeholder for future implementation)
     */
    fun playVoiceMessageWithAudioTrack(file: File, onCompletion: () -> Unit = {}) {
        try {
            Log.d("VoicePlayer", "AudioTrack playback not implemented yet for: ${file.absolutePath}")
            // AudioTrack requires decoding M4A to PCM first - not implemented yet
            // For now, just call completion immediately
            onCompletion()
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error with AudioTrack voice playback", e)
            onCompletion()
        }
    }
    
    /**
     * Alternative playback method using system intent with FileProvider
     */
    fun playWithSystemPlayer(file: File, onCompletion: () -> Unit = {}) {
        try {
            Log.d("VoicePlayer", "Trying system player for: ${file.absolutePath}")
            
            // Use FileProvider to create a secure URI
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "audio/mp4")
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(intent)
            Log.d("VoicePlayer", "System player launched with FileProvider URI")
            
            // Call completion after a delay
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                onCompletion()
            }, 1000)
            
        } catch (e: Exception) {
            Log.e("VoicePlayer", "Error launching system player", e)
            onCompletion()
        }
    }
}
