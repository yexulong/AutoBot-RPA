package com.autobot.rpa

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import com.autobot.rpa.service.AutoBotAccessibilityService
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
        
        fun requestOverlayPermission(context: Context, callback: ((Boolean) -> Unit)? = null) {
            Log.d(TAG, "requestOverlayPermission: currentActivity=$currentActivity")
            currentActivity?.requestOverlayPermission(callback)
        }
        
        fun startFullPermissionFlow(context: Context, onAllPermissionsGranted: (() -> Unit)? = null) {
            Log.d(TAG, "startFullPermissionFlow: currentActivity=$currentActivity")
            currentActivity?.startFullPermissionFlow(onAllPermissionsGranted)
        }
    }
    
    private lateinit var screenshotManager: ScreenshotManager
    
    // 完整权限申请流程回调
    private var fullPermissionFlowCallback: (() -> Unit)? = null
    
    // 权限申请状态
    private sealed class PermissionRequestState {
        object Idle : PermissionRequestState()
        object WaitingForAccessibility : PermissionRequestState()
        object RequestingOverlay : PermissionRequestState()
        object RequestingScreenCapture : PermissionRequestState()
    }
    
    private var currentPermissionState: PermissionRequestState = PermissionRequestState.Idle
    
    // 悬浮窗权限回调
    private var overlayPermissionCallback: ((Boolean) -> Unit)? = null
    
    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "screenCaptureLauncher: resultCode=${result.resultCode}, data=${result.data}")
        val wasInFullFlow = currentPermissionState == PermissionRequestState.RequestingScreenCapture
        currentPermissionState = PermissionRequestState.Idle
        if (result.resultCode == RESULT_OK && result.data != null) {
            // Use lifecycleScope to handle the permission result with proper service startup
            lifecycleScope.launch {
                handleScreenCapturePermissionResult(result.resultCode, result.data!!, wasInFullFlow)
            }
        } else {
            screenshotManager.onPermissionResult(result.resultCode, result.data)
            if (wasInFullFlow) {
                fullPermissionFlowCallback = null
            }
        }
    }
    
    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        Log.d(TAG, "overlayPermissionLauncher: Permission request completed")
        val wasInFullFlow = currentPermissionState == PermissionRequestState.RequestingOverlay
        currentPermissionState = PermissionRequestState.Idle
        val granted = checkOverlayPermission(this)
        overlayPermissionCallback?.invoke(granted)
        overlayPermissionCallback = null
        
        if (wasInFullFlow) {
            if (granted) {
                lifecycleScope.launch {
                    continueFullPermissionFlow()
                }
            } else {
                fullPermissionFlowCallback = null
            }
        }
    }
    
    private suspend fun handleScreenCapturePermissionResult(resultCode: Int, data: Intent, isFullFlow: Boolean) {
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
            
            if (isFullFlow) {
                // 截图权限获取成功，权限流程完成
                Log.d(TAG, "Full permission flow completed successfully")
                fullPermissionFlowCallback?.invoke()
                fullPermissionFlowCallback = null
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling screen capture permission", e)
            try {
                AutoBotForegroundService.stopService(this)
            } catch (ex: Exception) {
                Log.e(TAG, "Error stopping service", ex)
            }
            screenshotManager.onPermissionResult(RESULT_CANCELED, null)
            if (isFullFlow) {
                fullPermissionFlowCallback = null
            }
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
    
    fun startFullPermissionFlow(onAllPermissionsGranted: (() -> Unit)? = null) {
        Log.d(TAG, "startFullPermissionFlow: Starting complete permission flow")
        fullPermissionFlowCallback = onAllPermissionsGranted
        lifecycleScope.launch {
            continueFullPermissionFlow()
        }
    }
    
    private suspend fun continueFullPermissionFlow() {
        Log.d(TAG, "continueFullPermissionFlow: currentState=$currentPermissionState")
        
        // 检查无障碍服务权限
        if (!AutoBotAccessibilityService.isAccessibilityServiceEnabled()) {
            Log.d(TAG, "continueFullPermissionFlow: Requesting accessibility service")
            currentPermissionState = PermissionRequestState.WaitingForAccessibility
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
            return
        }
        
        // 检查悬浮窗权限
        if (!checkOverlayPermission(this)) {
            Log.d(TAG, "continueFullPermissionFlow: Requesting overlay permission")
            currentPermissionState = PermissionRequestState.RequestingOverlay
            
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${packageName}")
                )
            } else {
                // Android 6.0 以下不需要特殊权限
                continueFullPermissionFlow()
                return
            }
            overlayPermissionLauncher.launch(intent)
            return
        }
        
        // 检查截图权限
        if (!screenshotManager.hasPermission()) {
            Log.d(TAG, "continueFullPermissionFlow: Requesting screen capture permission")
            currentPermissionState = PermissionRequestState.RequestingScreenCapture
            val intent = screenshotManager.createScreenCaptureIntent()
            screenCaptureLauncher.launch(intent)
            return
        }
        
        // 所有权限都已获取
        Log.d(TAG, "continueFullPermissionFlow: All permissions granted")
        fullPermissionFlowCallback?.invoke()
        fullPermissionFlowCallback = null
    }
    
    fun requestScreenCapturePermission() {
        Log.d(TAG, "requestScreenCapturePermission: Launching permission request")
        currentPermissionState = PermissionRequestState.RequestingScreenCapture
        val intent = screenshotManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(intent)
    }
    
    fun requestOverlayPermission(callback: ((Boolean) -> Unit)? = null) {
        Log.d(TAG, "requestOverlayPermission: Launching permission request")
        currentPermissionState = PermissionRequestState.RequestingOverlay
        overlayPermissionCallback = callback
        
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${packageName}")
            )
        } else {
            // Android 6.0 以下不需要特殊权限
            currentPermissionState = PermissionRequestState.Idle
            overlayPermissionCallback?.invoke(true)
            overlayPermissionCallback = null
            return
        }
        overlayPermissionLauncher.launch(intent)
    }
    
    fun checkOverlayPermission(): Boolean {
        return checkOverlayPermission(this)
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
        
        // 如果正在等待无障碍服务权限，继续权限流程
        if (currentPermissionState == PermissionRequestState.WaitingForAccessibility) {
            lifecycleScope.launch {
                continueFullPermissionFlow()
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause")
        if (currentActivity == this) {
            currentActivity = null
        }
    }
}

private fun checkOverlayPermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        Settings.canDrawOverlays(context)
    } else {
        true
    }
}
