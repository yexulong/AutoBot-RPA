package com.autobot.rpa.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Build
import android.view.KeyEvent
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class AutoBotAccessibilityService : AccessibilityService() {

    private var isConnected = false

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

        private var serviceInstance: AutoBotAccessibilityService? = null

        fun getInstance(): AutoBotAccessibilityService? = serviceInstance

        fun isAccessibilityServiceEnabled(): Boolean = serviceInstance?.isConnected == true
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        _isServiceRunning.value = true
    }

    override fun onDestroy() {
        super.onDestroy()
        isConnected = false
        serviceInstance = null
        _isServiceRunning.value = false
        scope.cancel()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        serviceInstance = this
        _isServiceRunning.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        stopSelf()
    }

    fun performTap(x: Int, y: Int, duration: Int = 100): Boolean {
        if (!isConnected) return false

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    fun performSwipe(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Int = 500
    ): Boolean {
        if (!isConnected) return false

        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    fun performLongPress(x: Int, y: Int, duration: Int = 1000): Boolean {
        if (!isConnected) return false

        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }

        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration.toLong()))
            .build()

        return dispatchGesture(gesture, null, null)
    }

    suspend fun performTapWithDelay(x: Int, y: Int, duration: Int = 100, delayMs: Long = 100): Boolean {
        return withContext(Dispatchers.Main) {
            delay(delayMs)
            performTap(x, y, duration)
        }
    }

    suspend fun performSwipeWithDelay(
        startX: Int,
        startY: Int,
        endX: Int,
        endY: Int,
        duration: Int = 500,
        delayMs: Long = 100
    ): Boolean {
        return withContext(Dispatchers.Main) {
            delay(delayMs)
            performSwipe(startX, startY, endX, endY, duration)
        }
    }

    suspend fun performLongPressWithDelay(
        x: Int,
        y: Int,
        duration: Int = 1000,
        delayMs: Long = 100
    ): Boolean {
        return withContext(Dispatchers.Main) {
            delay(delayMs)
            performLongPress(x, y, duration)
        }
    }

    fun performKeyEvent(keyCode: Int): Boolean {
        if (!isConnected) return false
        
        // 首先尝试使用 AccessibilityService 的全局操作（最可靠的方式）
        return try {
            when (keyCode) {
                KeyEvent.KEYCODE_HOME -> {
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return true
                }
                KeyEvent.KEYCODE_BACK -> {
                    performGlobalAction(GLOBAL_ACTION_BACK)
                    return true
                }
                KeyEvent.KEYCODE_RECENT_APPS -> {
                    performGlobalAction(GLOBAL_ACTION_RECENTS)
                    return true
                }
                KeyEvent.KEYCODE_POWER -> {
                    performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_RAISE,
                        android.media.AudioManager.FLAG_SHOW_UI
                    )
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_LOWER,
                        android.media.AudioManager.FLAG_SHOW_UI
                    )
                    return true
                }
            }
            
            // 对于其他按键，暂时不支持（需要更高权限）
            false
        } catch (e: Exception) {
            // 如果所有方法都失败，返回 false
            false
        }
    }

    suspend fun performKeyEventWithDelay(keyCode: Int, delayMs: Long = 100): Boolean {
        return withContext(Dispatchers.Main) {
            delay(delayMs)
            performKeyEvent(keyCode)
        }
    }
}
