package com.autobot.rpa

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.autobot.rpa.service.AutoBotForegroundService
import com.autobot.rpa.service.ScreenshotManager
import com.autobot.rpa.ui.theme.AutoBotRPATheme
import com.autobot.rpa.ui.AutoBotApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
        private var currentActivity: MainActivity? = null
        
        fun getCurrentActivity(): MainActivity? = currentActivity
        
        fun requestScreenshotPermission(context: Context) {
            Log.d(TAG, "requestScreenshotPermission: currentActivity=$currentActivity")
            currentActivity?.requestScreenCapturePermission()
        }
    }
    
    private lateinit var screenshotManager: ScreenshotManager
    
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "screenCaptureLauncher: resultCode=${result.resultCode}, data=${result.data}")
        if (result.resultCode == RESULT_OK && result.data != null) {
            // Use lifecycleScope to handle the permission result with proper service startup
            lifecycleScope.launch {
                handleScreenCapturePermissionResult(result.resultCode, result.data!!)
            }
        } else {
            screenshotManager.onPermissionResult(result.resultCode, result.data)
        }
    }
    
    private suspend fun handleScreenCapturePermissionResult(resultCode: Int, data: Intent) {
        try {
            Log.d(TAG, "Starting foreground service for media projection...")
            
            // Start the foreground service
            AutoBotForegroundService.startService(this, AutoBotForegroundService.SERVICE_TYPE_MEDIA_PROJECTION)
            
            // Wait for service to start and be in foreground - wait up to 2 seconds
            var retryCount = 0
            val maxRetries = 20
            var serviceStarted = false
            
            while (retryCount < maxRetries && !serviceStarted) {
                if (AutoBotForegroundService.isRunning.value) {
                    serviceStarted = true
                    Log.d(TAG, "Foreground service confirmed running after ${retryCount * 100}ms")
                } else {
                    delay(100)
                    retryCount++
                }
            }
            
            if (!serviceStarted) {
                Log.w(TAG, "Service didn't confirm start, proceeding anyway...")
            }
            
            // Now get the media projection
            screenshotManager.onPermissionResult(resultCode, data)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling screen capture permission", e)
            try {
                AutoBotForegroundService.stopService(this)
            } catch (ex: Exception) {
                Log.e(TAG, "Error stopping service", ex)
            }
            screenshotManager.onPermissionResult(RESULT_CANCELED, null)
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        screenshotManager = ScreenshotManager.getInstance(this)
        
        setContent {
            AutoBotRPATheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AutoBotApp()
                }
            }
        }
    }
    
    fun requestScreenCapturePermission() {
        Log.d(TAG, "requestScreenCapturePermission: Launching permission request")
        val intent = screenshotManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        // Don't release screenshot manager here - we want to keep the media projection active
        // even when the activity is destroyed (e.g., when showing the floating window)
    }
    
    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume")
        currentActivity = this
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        if (currentActivity == this) {
            currentActivity = null
        }
    }
}
