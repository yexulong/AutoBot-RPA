package com.autobot.rpa.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.autobot.rpa.MainActivity
import com.autobot.rpa.R
import com.autobot.rpa.data.model.Script
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FloatingWindowService : Service() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var debugContainer: View? = null
    private var executionContainer: View? = null
    private var btnStart: Button? = null
    private var btnStartExec: Button? = null
    private var btnRerun: Button? = null
    private var btnRerunExec: Button? = null
    private var btnStop: Button? = null
    private var btnStopDebug: Button? = null
    private var btnDebug: Button? = null
    private var btnClose: ImageView? = null
    private var statusText: TextView? = null
    private var stepText: TextView? = null
    private var titleText: TextView? = null
    private var dragHandle: View? = null
    private var actionListDebug: RecyclerView? = null
    private var actionList: RecyclerView? = null
    private var actionListAdapterDebug: ActionListAdapter? = null
    private var actionListAdapter: ActionListAdapter? = null

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var stateCollectionJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private var currentMode = FloatingWindowMode.EXECUTION
    private val logList = mutableListOf<String>()
    private var currentScriptId: Long = -1
    private var currentScript: Script? = null
    private var currentActionIndex: Int = -1
    private var lastStepText: String = "就绪"

    companion object {
        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning

        private val _currentModeFlow = MutableStateFlow(FloatingWindowMode.EXECUTION)
        val currentModeFlow: StateFlow<FloatingWindowMode> = _currentModeFlow

        private var instance: FloatingWindowService? = null
        private var onFloatingWindowActionListener: OnFloatingWindowActionListener? = null

        interface OnFloatingWindowActionListener {
            fun onStartExecution()
            fun onStopExecution()
            fun onToggleDebug()
            fun onClose()
            fun onRerun()
        }

        enum class FloatingWindowMode {
            DEBUG,
            EXECUTION
        }

        fun startService(context: Context, mode: FloatingWindowMode = FloatingWindowMode.EXECUTION) {
            val intent = Intent(context, FloatingWindowService::class.java)
            intent.putExtra("mode", mode.name)
            context.startService(intent)
        }

        fun startServiceWithName(context: Context, modeName: String, scriptName: String = "") {
            val intent = Intent(context, FloatingWindowService::class.java)
            intent.putExtra("mode", modeName)
            intent.putExtra("scriptName", scriptName)
            context.startService(intent)
        }

        fun updateTitle(scriptName: String) {
            instance?.updateTitleText(scriptName)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingWindowService::class.java)
            context.stopService(intent)
        }

        fun setOnFloatingWindowActionListener(listener: OnFloatingWindowActionListener?) {
            onFloatingWindowActionListener = listener
        }

        fun switchMode(mode: FloatingWindowMode) {
            instance?.switchWindowMode(mode)
        }

        fun updateStatus(status: String) {
            instance?.updateStatusText(status)
        }

        fun updateStep(step: String) {
            instance?.updateStepText(step)
        }

        fun addLog(log: String) {
            instance?.addLogToList(log)
        }

        fun setCurrentScriptId(scriptId: Long) {
            instance?.currentScriptId = scriptId
        }

        fun getCurrentScriptId(): Long {
            return instance?.currentScriptId ?: -1L
        }

        fun setCurrentScript(script: Script) {
            instance?.currentScript = script
            instance?.currentScriptId = script.id
            instance?.updateActionList(script.actions)
            instance?.updateCurrentActionIndex(-1)
        }

        fun getCurrentScript(): Script? {
            return instance?.currentScript
        }

        fun setCurrentActionIndex(index: Int) {
            instance?.currentActionIndex = index
            instance?.updateCurrentActionIndex(index)
        }

        fun getCurrentActionIndex(): Int {
            return instance?.currentActionIndex ?: -1
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        _isServiceRunning.value = true
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val modeName = intent?.getStringExtra("mode")
        val scriptName = intent?.getStringExtra("scriptName") ?: ""
        modeName?.let {
            currentMode = try {
                FloatingWindowMode.valueOf(it)
            } catch (e: IllegalArgumentException) {
                FloatingWindowMode.EXECUTION
            }
        }
        showOverlay(scriptName)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        stateCollectionJob?.cancel()
        hideOverlay()
        instance = null
        _isServiceRunning.value = false
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun showOverlay(scriptName: String = "") {
        if (overlayView != null) return

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        overlayView = View.inflate(this, R.layout.overlay_floating_window, null)

        debugContainer = overlayView?.findViewById(R.id.debug_container)
        executionContainer = overlayView?.findViewById(R.id.execution_container)
        btnRerun = overlayView?.findViewById(R.id.btn_rerun)
        btnRerunExec = overlayView?.findViewById(R.id.btn_rerun_exec)
        btnStart = overlayView?.findViewById(R.id.btn_start)
        btnStartExec = overlayView?.findViewById(R.id.btn_start_exec)
        btnStop = overlayView?.findViewById(R.id.btn_stop)
        btnStopDebug = overlayView?.findViewById(R.id.btn_stop_debug)
        btnClose = overlayView?.findViewById(R.id.btn_close)
        statusText = overlayView?.findViewById(R.id.status_text)
        stepText = overlayView?.findViewById(R.id.step_text)
        titleText = overlayView?.findViewById(R.id.title_text)
        dragHandle = overlayView?.findViewById(R.id.drag_handle)
        actionListDebug = overlayView?.findViewById(R.id.action_list_debug)
        actionList = overlayView?.findViewById(R.id.action_list)

        // 初始化 DEBUG 模式的 RecyclerView 和 Adapter
        actionListAdapterDebug = ActionListAdapter()
        actionListDebug?.apply {
            layoutManager = LinearLayoutManager(this@FloatingWindowService)
            adapter = actionListAdapterDebug
        }

        // 初始化 EXECUTE 模式的 RecyclerView 和 Adapter - 只显示当前动作
        actionListAdapter = ActionListAdapter(showOnlyCurrentAction = true)
        actionList?.apply {
            layoutManager = LinearLayoutManager(this@FloatingWindowService)
            adapter = actionListAdapter
        }

        setupDragListener()

        // 开始按钮监听器
        val startListener = android.view.View.OnClickListener {
            ServiceBridge.startExecution()
        }
        btnStart?.setOnClickListener(startListener)
        btnStartExec?.setOnClickListener(startListener)

        // 重新运行按钮监听器
        val rerunListener = android.view.View.OnClickListener {
            ServiceBridge.rerunCurrentScript()
        }
        btnRerun?.setOnClickListener(rerunListener)
        btnRerunExec?.setOnClickListener(rerunListener)

        // 停止按钮监听器
        val stopListener = android.view.View.OnClickListener {
            ServiceBridge.stopExecution()
        }
        btnStop?.setOnClickListener(stopListener)
        btnStopDebug?.setOnClickListener(stopListener)

        btnClose?.setOnClickListener {
            onFloatingWindowActionListener?.onClose()
            stopSelf()
        }

        dragHandle?.setOnClickListener {
            launchMainActivity()
        }

        // 将 220dp 转换为像素
        val widthInPixels = (220 * resources.displayMetrics.density).toInt()
        
        val params = WindowManager.LayoutParams(
            widthInPixels,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager?.addView(overlayView, params)
        if (scriptName.isNotEmpty()) {
            updateTitleText(scriptName)
        }
        switchWindowMode(currentMode)
        
        // 开始监听执行状态
        startStateCollection()
        
        // 初始化UI：尝试从AutomationEngine获取当前状态
        ServiceBridge.getAutomationEngine()?.let { engine ->
            // 获取当前脚本
            val script = engine.getCurrentScript()
            if (script != null) {
                currentScript = script
                updateActionList(script.actions)
                updateCurrentActionIndex(engine.currentActionIndex.value)
            }
        }
    }
    
    private fun startStateCollection() {
        stateCollectionJob?.cancel()
        stateCollectionJob = scope.launch {
            ServiceBridge.getAutomationEngine()?.let { engine ->
                // 同时监听两个 Flow
                launch {
                    engine.executionState.collectLatest { state ->
                        updateUiForState(state)
                    }
                }
                launch {
                    engine.currentActionIndex.collectLatest { index ->
                        setCurrentActionIndex(index)
                    }
                }
            }
        }
        
        // 初始化 UI 为当前状态
        ServiceBridge.getAutomationEngine()?.let { engine ->
            updateUiForState(engine.executionState.value)
            setCurrentActionIndex(engine.currentActionIndex.value)
        }
    }
    
    private fun updateUiForState(state: AutomationEngine.ExecutionState) {
        runOnMainThread {
            when (state) {
                is AutomationEngine.ExecutionState.Idle,
                is AutomationEngine.ExecutionState.Completed,
                is AutomationEngine.ExecutionState.Error -> {
                    // 显示开始和重新运行，隐藏停止（EXECUTE 模式）
                    btnStartExec?.visibility = View.VISIBLE
                    btnRerunExec?.visibility = View.VISIBLE
                    btnStop?.visibility = View.GONE
                    
                    // DEBUG 模式也更新按钮
                    btnStart?.visibility = View.VISIBLE
                    btnRerun?.visibility = View.VISIBLE
                    btnStopDebug?.visibility = View.GONE
                    
                    // 设置按钮文本为"开始"
                    btnStartExec?.text = "开始"
                    btnStart?.text = "开始"
                    btnStartExec?.setOnClickListener {
                        ServiceBridge.startExecution()
                    }
                    btnStart?.setOnClickListener {
                        ServiceBridge.startExecution()
                    }
                    
                    // 更新状态文本（EXECUTE 模式）
                    val stateText = when (state) {
                        is AutomationEngine.ExecutionState.Idle -> "就绪"
                        is AutomationEngine.ExecutionState.Completed -> {
                            // 完成时设置所有动作都已完成
                            currentScript?.actions?.size?.let { total ->
                                updateCurrentActionIndex(total)
                            }
                            "✅ 执行完成"
                        }
                        is AutomationEngine.ExecutionState.Error -> "❌ 错误: ${state.message}"
                        else -> "就绪"
                    }
                    lastStepText = stateText
                    stepText?.text = stateText
                    
                    // 更新 DEBUG 模式状态文本
                    statusText?.text = stateText
                }
                
                is AutomationEngine.ExecutionState.Running -> {
                    // 显示停止，隐藏开始和重新运行（EXECUTE 模式）
                    btnStartExec?.visibility = View.GONE
                    btnRerunExec?.visibility = View.GONE
                    btnStop?.visibility = View.VISIBLE
                    
                    // DEBUG 模式也显示停止
                    btnStart?.visibility = View.GONE
                    btnRerun?.visibility = View.GONE
                    btnStopDebug?.visibility = View.VISIBLE
                }
                
                is AutomationEngine.ExecutionState.Paused -> {
                    // 显示继续和停止，隐藏重新运行（EXECUTE 模式）
                    btnStartExec?.visibility = View.VISIBLE
                    btnRerunExec?.visibility = View.GONE
                    btnStop?.visibility = View.VISIBLE
                    
                    // DEBUG 模式也显示继续和停止
                    btnStart?.visibility = View.VISIBLE
                    btnRerun?.visibility = View.GONE
                    btnStopDebug?.visibility = View.VISIBLE
                    
                    // 设置按钮文本为"继续"
                    btnStartExec?.text = "继续"
                    btnStart?.text = "继续"
                    btnStartExec?.setOnClickListener {
                        ServiceBridge.getAutomationEngine()?.resumeExecution()
                    }
                    btnStart?.setOnClickListener {
                        ServiceBridge.getAutomationEngine()?.resumeExecution()
                    }
                    
                    // 更新状态文本
                    lastStepText = "⏸️ 已暂停"
                    stepText?.text = "⏸️ 已暂停"
                    statusText?.text = "⏸️ 已暂停"
                }
            }
        }
    }

    private fun updateTitleText(title: String) {
        runOnMainThread {
            titleText?.text = title
        }
    }

    private fun hideOverlay() {
        overlayView?.let {
            windowManager?.removeView(it)
            overlayView = null
        }
    }

    private fun setupDragListener() {
        dragHandle?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var isClick = false

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isClick = true
                        initialX = overlayView?.layoutParams?.let { (it as WindowManager.LayoutParams).x } ?: 0
                        initialY = overlayView?.layoutParams?.let { (it as WindowManager.LayoutParams).y } ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = event.rawX - initialTouchX
                        val deltaY = event.rawY - initialTouchY
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isClick = false
                            overlayView?.layoutParams?.let { params ->
                                val newX = initialX + deltaX.toInt()
                                val newY = initialY + deltaY.toInt()
                                (params as WindowManager.LayoutParams).x = newX
                                params.y = newY
                                windowManager?.updateViewLayout(overlayView, params)
                            }
                        }
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            v?.performClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun launchMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        startActivity(intent)
        // 恢复应用后关闭悬浮窗
        stopSelf()
    }

    private fun switchWindowMode(mode: FloatingWindowMode) {
        currentMode = mode
        _currentModeFlow.value = mode

        runOnMainThread {
            when (mode) {
                FloatingWindowMode.DEBUG -> {
                    debugContainer?.visibility = View.VISIBLE
                    executionContainer?.visibility = View.GONE
                }
                FloatingWindowMode.EXECUTION -> {
                    debugContainer?.visibility = View.GONE
                    executionContainer?.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun updateStatusText(status: String) {
        runOnMainThread {
            statusText?.text = status
        }
    }

    private fun updateStepText(step: String) {
        runOnMainThread {
            stepText?.text = step
        }
    }

    private fun addLogToList(log: String) {
        logList.add(0, log)
        if (logList.size > 10) {
            logList.removeAt(logList.size - 1)
        }
        updateStatusText(logList.joinToString("\n"))
    }

    private fun updateActionList(actions: List<com.autobot.rpa.data.model.ScriptAction>) {
        runOnMainThread {
            actionListAdapter?.updateActions(actions)
            actionListAdapterDebug?.updateActions(actions)
        }
    }
    
    private fun updateCurrentActionIndex(index: Int) {
        runOnMainThread {
            actionListAdapter?.setCurrentActionIndex(index)
            actionListAdapterDebug?.setCurrentActionIndex(index)
        }
    }

    private fun runOnMainThread(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            action()
        } else {
            mainHandler.post(action)
        }
    }
}
