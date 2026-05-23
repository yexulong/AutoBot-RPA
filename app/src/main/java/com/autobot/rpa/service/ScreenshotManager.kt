package com.autobot.rpa.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

class ScreenshotManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "ScreenshotManager"
        @Volatile
        private var instance: ScreenshotManager? = null

        fun getInstance(context: Context): ScreenshotManager {
            return instance ?: synchronized(this) {
                instance ?: ScreenshotManager(context).also { instance = it }
            }
        }

        const val SCREEN_CAPTURE_REQUEST_CODE = 1001
    }

    private val mediaProjectionManager =
        context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val _permissionState = MutableStateFlow<PermissionState>(PermissionState.NotRequested)
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())

    private var screenDensity = 0
    private var screenWidth = 0
    private var screenHeight = 0
    
    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            Log.w(TAG, "MediaProjection onStop called - cleaning up everything")
            cleanupEverything()
        }
    }

    sealed class PermissionState {
        data object NotRequested : PermissionState()
        data object Requesting : PermissionState()
        data object Granted : PermissionState()
        data object Denied : PermissionState()
    }

    init {
        updateScreenMetrics()
    }

    @Suppress("DEPRECATION")
    private fun updateScreenMetrics() {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                screenWidth = bounds.width()
                screenHeight = bounds.height()
                
                val displayMetrics = context.resources.displayMetrics
                screenDensity = displayMetrics.densityDpi
            } catch (e: Exception) {
                val displayMetrics = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(displayMetrics)
                screenDensity = displayMetrics.densityDpi
                screenWidth = displayMetrics.widthPixels
                screenHeight = displayMetrics.heightPixels
            }
        } else {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            screenDensity = displayMetrics.densityDpi
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
        }
    }

    fun createScreenCaptureIntent(): Intent {
        Log.d(TAG, "createScreenCaptureIntent: Requesting screen capture permission")
        _permissionState.value = PermissionState.Requesting
        return mediaProjectionManager.createScreenCaptureIntent()
    }

    fun requestPermission(activity: Activity) {
        Log.d(TAG, "requestPermission: Current state=${_permissionState.value}")
        if (_permissionState.value == PermissionState.Granted) return

        _permissionState.value = PermissionState.Requesting
        val intent = mediaProjectionManager.createScreenCaptureIntent()
        activity.startActivityForResult(intent, SCREEN_CAPTURE_REQUEST_CODE)
    }

    fun onPermissionResult(resultCode: Int, data: Intent?) {
        Log.d(TAG, "onPermissionResult: resultCode=$resultCode, data=$data, current state=${_permissionState.value}")
        if (resultCode == Activity.RESULT_OK && data != null) {
            try {
                // Ensure foreground service is running with media projection type
                if (!AutoBotForegroundService.isRunning.value) {
                    Log.d(TAG, "onPermissionResult: Starting foreground service for media projection")
                    AutoBotForegroundService.startService(context, AutoBotForegroundService.SERVICE_TYPE_MEDIA_PROJECTION)
                    
                    // Simple delay to give service time to start
                    Thread.sleep(300)
                }
                
                // Get media projection
                val projection = mediaProjectionManager.getMediaProjection(resultCode, data)
                
                // Register callback BEFORE doing anything else
                projection.registerCallback(mediaProjectionCallback, mainHandler)
                
                mediaProjection = projection
                
                // Also create VirtualDisplay and ImageReader immediately to keep projection active
                updateScreenMetrics()
                imageReader = ImageReader.newInstance(
                    screenWidth,
                    screenHeight,
                    PixelFormat.RGBA_8888,
                    2
                )
                virtualDisplay = mediaProjection?.createVirtualDisplay(
                    "Screenshot",
                    screenWidth,
                    screenHeight,
                    screenDensity,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader?.surface,
                    null,
                    mainHandler
                )
                
                _permissionState.value = PermissionState.Granted
                Log.d(TAG, "onPermissionResult: Permission GRANTED! MediaProjection and VirtualDisplay created successfully")
            } catch (e: Exception) {
                Log.e(TAG, "onPermissionResult: Error getting media projection", e)
                cleanupEverything()
                _permissionState.value = PermissionState.Denied
            }
        } else {
            Log.d(TAG, "onPermissionResult: Permission DENIED")
            cleanupEverything()
            _permissionState.value = PermissionState.Denied
        }
    }
    
    fun hasPermission(): Boolean {
        return _permissionState.value == PermissionState.Granted && mediaProjection != null
    }

    private fun generateDefaultFileName(): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
        return "Screenshot_${sdf.format(java.util.Date())}"
    }

    @JvmOverloads
    fun takeScreenshot(fileName: String = generateDefaultFileName(), callback: (Result<File>) -> Unit) {
        Log.d(TAG, "takeScreenshot: Starting screenshot with fileName: $fileName")
        
        if (!hasPermission()) {
            val errorMsg = "Screen capture permission not granted. Please grant permission first."
            Log.e(TAG, "takeScreenshot: $errorMsg")
            callback(Result.failure(IllegalStateException(errorMsg)))
            return
        }

        if (virtualDisplay == null || imageReader == null) {
            val errorMsg = "Virtual display or ImageReader not initialized. Please re-grant permission."
            Log.e(TAG, "takeScreenshot: $errorMsg")
            callback(Result.failure(IllegalStateException(errorMsg)))
            return
        }

        var hasCalledBack = false
        val timeoutRunnable = Runnable {
            if (!hasCalledBack) {
                hasCalledBack = true
                Log.e(TAG, "takeScreenshot: Timeout waiting for image")
                callback(Result.failure(Exception("Screenshot timeout")))
            }
        }
        mainHandler.postDelayed(timeoutRunnable, 5000) // 5秒超时

        try {
            // Try to acquire latest image immediately
            Log.d(TAG, "takeScreenshot: Trying to acquire latest image...")
            
            var image: Image? = null
            try {
                image = imageReader?.acquireLatestImage()
                if (image != null) {
                    Log.d(TAG, "takeScreenshot: Image acquired, processing...")
                    val bitmap = imageToBitmap(image)
                    val file = saveBitmapToFile(bitmap, fileName)
                    Log.d(TAG, "takeScreenshot: Screenshot saved successfully to ${file.absolutePath}")
                    
                    hasCalledBack = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    callback(Result.success(file))
                } else {
                    // No image available yet, wait for next frame
                    Log.d(TAG, "takeScreenshot: No image available, waiting for next frame...")
                    val tempListener = object : ImageReader.OnImageAvailableListener {
                        override fun onImageAvailable(reader: ImageReader) {
                            if (hasCalledBack) return
                            
                            var img: Image? = null
                            try {
                                img = reader.acquireLatestImage()
                                if (img != null) {
                                    Log.d(TAG, "takeScreenshot: Image received from listener, processing...")
                                    val bitmap = imageToBitmap(img)
                                    val file = saveBitmapToFile(bitmap, fileName)
                                    Log.d(TAG, "takeScreenshot: Screenshot saved successfully to ${file.absolutePath}")
                                    
                                    hasCalledBack = true
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    callback(Result.success(file))
                                }
                            } catch (e: Exception) {
                                if (!hasCalledBack) {
                                    hasCalledBack = true
                                    mainHandler.removeCallbacks(timeoutRunnable)
                                    Log.e(TAG, "takeScreenshot: Error processing image from listener", e)
                                    callback(Result.failure(e))
                                }
                            } finally {
                                img?.close()
                            }
                            reader.setOnImageAvailableListener(null, null)
                        }
                    }
                    imageReader?.setOnImageAvailableListener(tempListener, mainHandler)
                }
            } catch (e: Exception) {
                if (!hasCalledBack) {
                    hasCalledBack = true
                    mainHandler.removeCallbacks(timeoutRunnable)
                    Log.e(TAG, "takeScreenshot: Error taking screenshot", e)
                    callback(Result.failure(e))
                }
            } finally {
                image?.close()
            }

        } catch (e: Exception) {
            if (!hasCalledBack) {
                hasCalledBack = true
                mainHandler.removeCallbacks(timeoutRunnable)
                Log.e(TAG, "takeScreenshot: Error taking screenshot", e)
                callback(Result.failure(e))
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)

        return Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
    }

    private fun saveBitmapToFile(bitmap: Bitmap, fileName: String): File {
        val screenshotsDir = File(context.filesDir, "screenshots")
        if (!screenshotsDir.exists()) {
            screenshotsDir.mkdirs()
        }

        val file = File(screenshotsDir, "$fileName.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        return file
    }

    private fun cleanupVirtualDisplay() {
        Log.d(TAG, "cleanupVirtualDisplay called")
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    private fun cleanupEverything() {
        Log.d(TAG, "cleanupEverything called")
        cleanupVirtualDisplay()
        mediaProjection?.stop()
        mediaProjection = null
        _permissionState.value = PermissionState.NotRequested
        
        // Stop the foreground service if it's running for media projection
        AutoBotForegroundService.stopService(context)
    }

    fun release() {
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        cleanupEverything()
    }
}
