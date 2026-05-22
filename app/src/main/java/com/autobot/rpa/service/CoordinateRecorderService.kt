package com.autobot.rpa.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.TextView
import com.autobot.rpa.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class CoordinateRecorderService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private val coordinateIndicators = mutableListOf<View>()
    private val rippleViews = mutableListOf<View>()
    private var coordinateText: TextView? = null
    private var btnConfirm: Button? = null
    private var btnCancel: Button? = null
    private var btnReset: Button? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var recordedCoordinates = mutableListOf<Point>()
    private var maxCoordinateCount = 1

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

        private val _recordedPoints = MutableStateFlow<List<Point>>(emptyList())
        val recordedPoints: StateFlow<List<Point>> = _recordedPoints

        private var instance: CoordinateRecorderService? = null
        private var onCoordinateRecordedListener: OnCoordinateRecordedListener? = null

        interface OnCoordinateRecordedListener {
            fun onCoordinatesConfirmed(points: List<Point>)
            fun onCoordinateCancelled()
        }

        fun startService(context: Context, maxPoints: Int = 1) {
            val intent = Intent(context, CoordinateRecorderService::class.java)
            intent.putExtra("max_points", maxPoints)
            context.startService(intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, CoordinateRecorderService::class.java)
            context.stopService(intent)
        }

        fun setOnCoordinateRecordedListener(listener: OnCoordinateRecordedListener?) {
            onCoordinateRecordedListener = listener
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isServiceRunning.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        maxCoordinateCount = intent?.getIntExtra("max_points", 1) ?: 1
        resetRecording()
        showOverlay()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        hideOverlay()
        instance = null
        _isServiceRunning.value = false
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun resetRecording() {
        recordedCoordinates.clear()
        _recordedPoints.value = emptyList()
        clearAllIndicators()
    }

    private fun clearAllIndicators() {
        coordinateIndicators.forEach { it.visibility = View.GONE }
        rippleViews.forEach { it.visibility = View.GONE }
        updateUIState()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = View.inflate(this, R.layout.overlay_coordinate_recorder, null)

        coordinateText = overlayView?.findViewById(R.id.coordinate_text)
        btnConfirm = overlayView?.findViewById(R.id.btn_confirm)
        btnCancel = overlayView?.findViewById(R.id.btn_cancel)
        btnReset = overlayView?.findViewById(R.id.btn_reset)

        val touchLayer = overlayView?.findViewById<View>(R.id.touch_layer)
        touchLayer?.setOnTouchListener { _, event ->
            handleTouchEvent(event)
            true
        }

        btnConfirm?.setOnClickListener {
            if (recordedCoordinates.isNotEmpty()) {
                onCoordinateRecordedListener?.onCoordinatesConfirmed(recordedCoordinates.toList())
                stopSelf()
            }
        }

        btnCancel?.setOnClickListener {
            onCoordinateRecordedListener?.onCoordinateCancelled()
            stopSelf()
        }

        btnReset?.setOnClickListener {
            resetRecording()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.LEFT

        windowManager?.addView(overlayView, params)
        updateUIState()
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
        coordinateIndicators.clear()
        rippleViews.clear()
    }

    private fun handleTouchEvent(event: MotionEvent) {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x.toInt()
                val y = event.y.toInt()

                if (recordedCoordinates.size < maxCoordinateCount) {
                    recordedCoordinates.add(Point(x, y))
                    _recordedPoints.value = recordedCoordinates.toList()
                    addCoordinateIndicator(x, y, recordedCoordinates.size - 1)
                }

                updateUIState()
            }
        }
    }

    private fun addCoordinateIndicator(x: Int, y: Int, index: Int) {
        val indicatorsContainer = overlayView?.findViewById<ViewGroup>(R.id.indicators_container) ?: return

        val indicatorView = if (index < coordinateIndicators.size) {
            coordinateIndicators[index]
        } else {
            val view = LayoutInflater.from(this).inflate(R.layout.item_coordinate_indicator, indicatorsContainer, false)
            coordinateIndicators.add(view)
            indicatorsContainer.addView(view)
            view
        }

        val rippleView = if (index < rippleViews.size) {
            rippleViews[index]
        } else {
            val view = View(this)
            view.setBackgroundResource(R.drawable.indicator_ripple_effect)
            val size = 80
            val rippleParams = ViewGroup.LayoutParams(size, size)
            view.layoutParams = rippleParams
            rippleViews.add(view)
            indicatorsContainer.addView(view)
            view
        }

        val outerCircle = indicatorView.findViewById<View>(R.id.indicator_outer_circle)
        val numberText = indicatorView.findViewById<TextView>(R.id.indicator_number)

        val circleRes = when (index) {
            0 -> R.drawable.indicator_circle_primary
            else -> R.drawable.indicator_circle_secondary
        }
        outerCircle.setBackgroundResource(circleRes)
        numberText.text = (index + 1).toString()

        indicatorView.visibility = View.VISIBLE
        rippleView.visibility = View.VISIBLE

        indicatorView.post {
            val indicatorParams = indicatorView.layoutParams as ViewGroup.MarginLayoutParams
            indicatorParams.leftMargin = x - (indicatorView.width / 2)
            indicatorParams.topMargin = y - (indicatorView.height / 2)
            indicatorView.layoutParams = indicatorParams

            val rippleParams = rippleView.layoutParams as ViewGroup.MarginLayoutParams
            rippleParams.leftMargin = x - (rippleView.width / 2)
            rippleParams.topMargin = y - (rippleView.height / 2)
            rippleView.layoutParams = rippleParams

            val pulseAnimation = AnimationUtils.loadAnimation(this, R.anim.indicator_pulse_anim)
            val rippleAnimation = AnimationUtils.loadAnimation(this, R.anim.indicator_ripple_anim)

            indicatorView.startAnimation(pulseAnimation)
            rippleView.startAnimation(rippleAnimation)
        }
    }

    private fun updateUIState() {
        btnConfirm?.isEnabled = recordedCoordinates.size == maxCoordinateCount
        btnReset?.visibility = if (recordedCoordinates.isNotEmpty()) View.VISIBLE else View.GONE

        when {
            recordedCoordinates.isEmpty() -> {
                coordinateText?.text = "📍 点击屏幕任意位置开始记录坐标"
            }
            recordedCoordinates.size < maxCoordinateCount -> {
                val sb = StringBuilder()
                recordedCoordinates.forEachIndexed { index, point ->
                    sb.append("✓ 点${index + 1}: (${point.x}, ${point.y})\n")
                }
                sb.append("📍 还需记录 ${maxCoordinateCount - recordedCoordinates.size} 个点，请继续点击...")
                coordinateText?.text = sb.toString()
            }
            else -> {
                val sb = StringBuilder()
                recordedCoordinates.forEachIndexed { index, point ->
                    sb.append("✓ 点${index + 1}: (${point.x}, ${point.y})\n")
                }
                sb.append("✅ 所有坐标记录完成，点击确认")
                coordinateText?.text = sb.toString()
            }
        }
    }
}
