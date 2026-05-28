package com.autobot.rpa.service

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputMethodManager
import com.autobot.rpa.data.model.ConditionType
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.model.ScriptAction
import com.autobot.rpa.data.repository.ScriptRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutomationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val scriptRepository: ScriptRepository
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var executionJob: Job? = null
    private var currentScript: Script? = null
    
    fun getCurrentScript(): Script? = currentScript

    private val _executionState = MutableStateFlow<ExecutionState>(ExecutionState.Idle)
    val executionState: StateFlow<ExecutionState> = _executionState

    private val _logs = MutableStateFlow<List<ExecutionLog>>(emptyList())
    val logs: StateFlow<List<ExecutionLog>> = _logs

    private val _currentActionIndex = MutableStateFlow(-1)
    val currentActionIndex: StateFlow<Int> = _currentActionIndex

    private val variableStore = mutableMapOf<String, Any>()

    sealed class ExecutionState {
        object Idle : ExecutionState()
        object Running : ExecutionState()
        object Paused : ExecutionState()
        data class Completed(val scriptId: Long) : ExecutionState()
        data class Error(val message: String) : ExecutionState()
    }

    data class ExecutionLog(
        val timestamp: Long = System.currentTimeMillis(),
        val message: String,
        val type: LogType = LogType.INFO
    )

    enum class LogType {
        INFO, SUCCESS, WARNING, ERROR
    }

    enum class RunMode {
        DEBUG, EXECUTE
    }

    private val _currentRunMode = MutableStateFlow<RunMode>(RunMode.EXECUTE)
    val currentRunMode: StateFlow<RunMode> = _currentRunMode

    fun setRunMode(mode: RunMode) {
        _currentRunMode.value = mode
        log("Run mode set to: ${mode.name}", LogType.INFO)
    }

    fun openFloatingWindow(script: Script) {
        currentScript = script
        FloatingWindowService.setCurrentScript(script)
        FloatingWindowService.setCurrentActionIndex(-1)
        _executionState.value = ExecutionState.Idle
        _logs.value = emptyList()
        _currentActionIndex.value = -1

        // 保持当前的服务类型，不要随意改变
        // 如果服务没有运行，才启动默认类型的服务
        if (!AutoBotForegroundService.isRunning.value) {
            AutoBotForegroundService.startService(context)
        }
        AutoBotForegroundService.updateNotification(script.name)
        
        val floatingModeName = when (_currentRunMode.value) {
            RunMode.DEBUG -> "DEBUG"
            RunMode.EXECUTE -> "EXECUTION"
        }
        FloatingWindowService.startServiceWithName(context, floatingModeName, script.name)
    }

    fun startExecution() {
        currentScript?.let { script ->
            if (_executionState.value == ExecutionState.Running) {
                log("Script is already running", LogType.WARNING)
                return
            }

            executionJob = scope.launch {
                _executionState.value = ExecutionState.Running
                _logs.value = emptyList()
                _currentActionIndex.value = -1

                log("Starting script: ${script.name}")
                
                try {
                    scriptRepository.incrementRunCount(script.id)
                    executeActions(script.actions)
                    _executionState.value = ExecutionState.Idle
                    log("Script completed successfully", LogType.SUCCESS)
                    FloatingWindowService.updateStep("✅ 执行完成")
                } catch (e: CancellationException) {
                    log("Script execution cancelled", LogType.WARNING)
                    _executionState.value = ExecutionState.Idle
                    FloatingWindowService.updateStep("⏹️ 已取消")
                } catch (e: Exception) {
                    val errorMessage = e.message ?: "Unknown error"
                    log("Script execution failed: $errorMessage", LogType.ERROR)
                    _executionState.value = ExecutionState.Idle
                    FloatingWindowService.updateStep("❌ 执行失败: $errorMessage")
                } finally {
                    delay(1000)
                    // 不要停止前台服务 - 这会导致 MediaProjection 失效
                    // AutoBotForegroundService.stopService(context)
                    // 不自动关闭悬浮窗，让用户自己关闭
                }
            }
        }
    }

    fun startExecution(script: Script) {
        currentScript = script
        startExecution()
    }

    fun pauseExecution() {
        if (_executionState.value == ExecutionState.Running) {
            _executionState.value = ExecutionState.Paused
            log("Script paused", LogType.INFO)
        }
    }

    fun resumeExecution() {
        if (_executionState.value == ExecutionState.Paused) {
            _executionState.value = ExecutionState.Running
            log("Script resumed", LogType.INFO)
        }
    }

    fun stopExecution() {
        executionJob?.cancel()
        _executionState.value = ExecutionState.Idle
        _currentActionIndex.value = -1
        FloatingWindowService.setCurrentActionIndex(-1)
        log("Script stopped", LogType.WARNING)
        FloatingWindowService.updateStep("⏹️ 已停止")
        // 不自动关闭悬浮窗，让用户自己关闭
    }

    fun rerunCurrentScript() {
        currentScript?.let { script ->
            startExecution(script)
        }
    }

    private suspend fun executeActions(actions: List<ScriptAction>) {
        var index = 0
        val loopStack = mutableListOf<LoopContext>()
        
        while (index < actions.size) {
            if (executionJob?.isActive != true) break

            while (_executionState.value == ExecutionState.Paused) {
                delay(100)
                if (executionJob?.isActive != true) break
            }

            if (executionJob?.isActive != true) break

            val action = actions[index]
            _currentActionIndex.value = index
            FloatingWindowService.setCurrentActionIndex(index)

            val actionName = when (action) {
            is ScriptAction.Tap -> "Tap (${action.x}, ${action.y})"
            is ScriptAction.Swipe -> "Swipe"
            is ScriptAction.LongPress -> "LongPress (${action.x}, ${action.y})"
            is ScriptAction.TextInput -> "TextInput: ${action.text}"
            is ScriptAction.KeyPress -> "KeyPress: ${action.keyCode}"
            is ScriptAction.Delay -> "Delay: ${action.milliseconds}ms"
            is ScriptAction.Screenshot -> "Screenshot"
            is ScriptAction.FindImage -> "FindImage"
            is ScriptAction.FindText -> "FindText"
            is ScriptAction.LoopStart -> "LoopStart"
            is ScriptAction.LoopEnd -> "LoopEnd"
            is ScriptAction.Condition -> "Condition"
            is ScriptAction.Comment -> "Comment: ${action.text}"
            is ScriptAction.SetVariable -> "SetVariable: ${action.varName}"
        }
            
            val stepInfo = "Step ${index + 1}/${actions.size}: $actionName"
            FloatingWindowService.updateStep(stepInfo)
            
            log("Executing: ${action::class.simpleName}", LogType.INFO)
            
            when (action) {
                is ScriptAction.LoopStart -> {
                    val loopEndIndex = findMatchingLoopEnd(actions, index)
                    if (loopEndIndex != null) {
                        val context = LoopContext(
                            startIndex = index,
                            endIndex = loopEndIndex,
                            times = if (action.infinite) -1 else action.times,
                            currentIteration = 0
                        )
                        loopStack.add(context)
                        log("Loop started: ${if (action.infinite) "infinite" else "${action.times} times"}", LogType.INFO)
                        index++
                    } else {
                        log("❌ Skipping invalid LoopStart at index $index", LogType.ERROR)
                        index++
                    }
                }
                is ScriptAction.LoopEnd -> {
                    if (loopStack.isNotEmpty()) {
                        val currentLoop = loopStack.last()
                        currentLoop.currentIteration++
                        
                        val shouldContinue = if (currentLoop.times == -1) {
                            true
                        } else {
                            currentLoop.currentIteration < currentLoop.times
                        }
                        
                        if (shouldContinue) {
                            log("Loop iteration ${currentLoop.currentIteration}/${currentLoop.times.takeIf { it != -1 } ?: "∞"}", LogType.INFO)
                            index = currentLoop.startIndex + 1
                        } else {
                            log("Loop completed after ${currentLoop.currentIteration} iterations", LogType.SUCCESS)
                            loopStack.removeLast()
                            index++
                        }
                    } else {
                        log("❌ Unmatched LoopEnd at index $index", LogType.ERROR)
                        index++
                    }
                }
                else -> {
                    executeAction(action)
                    index++
                }
            }
        }
    }

    private data class LoopContext(
        val startIndex: Int,
        val endIndex: Int,
        val times: Int,
        var currentIteration: Int
    )

    private fun resolveString(valueStr: String?, defaultValue: String): String {
        if (valueStr.isNullOrEmpty()) {
            return defaultValue
        }
        
        // 简单的变量替换，只处理 ${var} 格式
        var result = valueStr
        val pattern = Regex("\\$\\{([^}]+)\\}")
        
        // 替换所有匹配项
        result = pattern.replace(result) { matchResult ->
            val varName = matchResult.groupValues[1]
            resolveVariable(varName)?.toString() ?: defaultValue
        }
        
        return result
    }

    private fun resolveInt(valueStr: String?, defaultValue: Int): Int {
        if (valueStr.isNullOrEmpty()) {
            return defaultValue
        }
        
        // 尝试直接解析为数字
        valueStr.toIntOrNull()?.let { return it }
        
        // 尝试解析变量
        val resolved = resolveVariable(valueStr)
        return when (resolved) {
            is Int -> resolved
            is Number -> resolved.toInt()
            is String -> resolved.toIntOrNull() ?: defaultValue
            else -> defaultValue
        }
    }
    
    private fun resolveVariable(expr: String): Any? {
        var varName = expr
        
        // 如果是 ${variable} 格式，提取变量名
        if (expr.startsWith("\${") && expr.endsWith("}")) {
            varName = expr.substring(2, expr.length - 1)
        }
        
        // 支持 variable.property 格式
        val parts = varName.split(".", limit = 2)
        val mainVar = parts[0]
        
        if (variableStore.containsKey(mainVar)) {
            val value = variableStore[mainVar]
            if (parts.size == 2 && value is Map<*, *>) {
                return value[parts[1]]
            }
            return value
        }
        
        return null
    }

    private fun resolveCoordinate(valueStr: String?, defaultValue: Int): Int {
        return resolveInt(valueStr, defaultValue)
    }

    private suspend fun executeAction(action: ScriptAction) {
        val accessibilityService = AutoBotAccessibilityService.getInstance()

        when (action) {
            is ScriptAction.Tap -> {
                val x = resolveCoordinate(action.xStr, action.x)
                val y = resolveCoordinate(action.yStr, action.y)
                if (accessibilityService != null) {
                    accessibilityService.performTapWithDelay(x, y, action.duration)
                    log("Tapped at ($x, $y)", LogType.SUCCESS)
                } else {
                    log("Accessibility service not available", LogType.ERROR)
                }
            }

            is ScriptAction.Swipe -> {
                val startX = resolveCoordinate(action.startXStr, action.startX)
                val startY = resolveCoordinate(action.startYStr, action.startY)
                val endX = resolveCoordinate(action.endXStr, action.endX)
                val endY = resolveCoordinate(action.endYStr, action.endY)
                if (accessibilityService != null) {
                    accessibilityService.performSwipeWithDelay(startX, startY, endX, endY, action.duration)
                    log("Swiped from ($startX, $startY) to ($endX, $endY)", LogType.SUCCESS)
                } else {
                    log("Accessibility service not available", LogType.ERROR)
                }
            }

            is ScriptAction.LongPress -> {
                val x = resolveCoordinate(action.xStr, action.x)
                val y = resolveCoordinate(action.yStr, action.y)
                if (accessibilityService != null) {
                    accessibilityService.performLongPressWithDelay(x, y, action.duration)
                    log("Long pressed at ($x, $y) for ${action.duration}ms", LogType.SUCCESS)
                } else {
                    log("Accessibility service not available", LogType.ERROR)
                }
            }

            is ScriptAction.TextInput -> {
                val text = resolveString(action.textStr, action.text)
                inputText(text)
                log("Input text: $text", LogType.SUCCESS)
            }

            is ScriptAction.KeyPress -> {
                val keyCode = resolveInt(action.keyCodeStr, action.keyCode)
                var success = false
                if (accessibilityService != null) {
                    success = accessibilityService.performKeyEventWithDelay(keyCode)
                }
                
                if (!success) {
                    sendKeyEvent(keyCode)
                }
                
                val keyName = getKeyName(keyCode)
                log("Pressed key: $keyName ($keyCode)", LogType.SUCCESS)
            }

            is ScriptAction.Delay -> {
                val milliseconds = resolveInt(action.millisecondsStr, action.milliseconds)
                delay(milliseconds.toLong())
                log("Waited ${milliseconds}ms", LogType.INFO)
            }

            is ScriptAction.Screenshot -> {
                val fileName = resolveString(action.fileNameStr, action.fileName)
                takeScreenshot(fileName)
            }

            is ScriptAction.SetVariable -> {
                val varValue = resolveString(action.varValue, action.varValue)
                variableStore[action.varName] = varValue
                log("Set variable: ${action.varName} = $varValue", LogType.SUCCESS)
            }

            is ScriptAction.FindImage -> {
                findImage(action)
            }
            is ScriptAction.FindText -> {
                findText(action)
            }

            is ScriptAction.LoopStart, is ScriptAction.LoopEnd -> {
                // Handled in executeActions
            }

            is ScriptAction.Condition -> {
                log("Condition check: ${action.type}", LogType.INFO)
                val conditionMet = checkCondition(action)
                val branchToExecute = if (conditionMet) {
                    log("Condition met, executing true branch", LogType.INFO)
                    action.trueBranch
                } else {
                    log("Condition not met, executing false branch", LogType.INFO)
                    action.falseBranch
                }
                executeActions(branchToExecute)
            }

            is ScriptAction.Comment -> {
                val text = resolveString(action.textStr, action.text)
                log("Comment: $text", LogType.INFO)
            }
        }
    }

    private suspend fun inputText(text: String) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val currentFocus = (context as? android.app.Activity)?.currentFocus
        currentFocus?.let {
            imm.showSoftInput(it, InputMethodManager.SHOW_IMPLICIT)
        }

        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("input", text)
        clipboard.setPrimaryClip(clip)

        delay(200)
    }

    private fun sendKeyEvent(keyCode: Int) {
        try {
            (context as? android.app.Activity)?.let { activity ->
                val eventTime = System.currentTimeMillis()
                val downEvent = KeyEvent(
                    eventTime - 100,
                    eventTime - 100,
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    0
                )
                val upEvent = KeyEvent(
                    eventTime,
                    eventTime,
                    KeyEvent.ACTION_UP,
                    keyCode,
                    0
                )
                activity.dispatchKeyEvent(downEvent)
                activity.dispatchKeyEvent(upEvent)
            }
        } catch (e: Exception) {
        }
    }

    private fun getKeyName(keyCode: Int): String {
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME -> "HOME"
            KeyEvent.KEYCODE_BACK -> "BACK"
            KeyEvent.KEYCODE_MENU -> "MENU"
            KeyEvent.KEYCODE_CALL -> "CALL"
            KeyEvent.KEYCODE_ENDCALL -> "ENDCALL"
            KeyEvent.KEYCODE_VOLUME_UP -> "VOLUME_UP"
            KeyEvent.KEYCODE_VOLUME_DOWN -> "VOLUME_DOWN"
            KeyEvent.KEYCODE_POWER -> "POWER"
            KeyEvent.KEYCODE_CAMERA -> "CAMERA"
            KeyEvent.KEYCODE_ENTER -> "ENTER"
            KeyEvent.KEYCODE_DEL -> "DEL"
            KeyEvent.KEYCODE_FORWARD_DEL -> "FORWARD_DEL"
            KeyEvent.KEYCODE_DPAD_UP -> "DPAD_UP"
            KeyEvent.KEYCODE_DPAD_DOWN -> "DPAD_DOWN"
            KeyEvent.KEYCODE_DPAD_LEFT -> "DPAD_LEFT"
            KeyEvent.KEYCODE_DPAD_RIGHT -> "DPAD_RIGHT"
            KeyEvent.KEYCODE_DPAD_CENTER -> "DPAD_CENTER"
            KeyEvent.KEYCODE_RECENT_APPS -> "RECENT_APPS"
            else -> "KEYCODE_$keyCode"
        }
    }

    private fun findMatchingLoopEnd(actions: List<ScriptAction>, startIndex: Int): Int? {
        var stack = 1
        var index = startIndex + 1
        
        while (index < actions.size && stack > 0) {
            when (actions[index]) {
                is ScriptAction.LoopStart -> stack++
                is ScriptAction.LoopEnd -> stack--
                else -> {}
            }
            if (stack > 0) {
                index++
            }
        }
        
        return if (stack == 0) {
            index
        } else {
            log("❌ No matching LoopEnd found for LoopStart at index $startIndex", LogType.ERROR)
            null
        }
    }

    companion object {
        private const val TAG = "AutomationEngine"
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun takeScreenshot(fileName: String? = null) {
        val screenshotManager = ScreenshotManager.getInstance(context)
        
        val currentState = screenshotManager.permissionState.value
        if (currentState != ScreenshotManager.PermissionState.Granted) {
            log("Screen capture permission not granted. Please grant permission first.", LogType.ERROR)
            return
        }

        // 使用 Kotlin 协程和 suspendCancellableCoroutine 来处理回调
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val actualFileName = if (fileName.isNullOrBlank()) {
                val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
                "Screenshot_${sdf.format(java.util.Date())}"
            } else {
                fileName
            }
            
            screenshotManager.takeScreenshot(actualFileName) { result ->
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    log("Screenshot saved to: ${file.absolutePath}", LogType.SUCCESS)
                    continuation.resume(Unit, onCancellation = null)
                } else {
                    val error = result.exceptionOrNull() ?: Exception("Unknown error")
                    log("Failed to take screenshot: ${error.message}", LogType.ERROR)
                    continuation.resume(Unit, onCancellation = null)
                }
            }
        }
    }

    private suspend fun findImage(action: ScriptAction.FindImage) {
        log("===== Starting Find Image ======", LogType.INFO)
        log("Template path: ${action.templatePath}", LogType.INFO)
        log("Threshold: ${action.threshold}", LogType.INFO)
        log("Timeout: ${action.timeout}ms", LogType.INFO)
        log("Debug mode: ${action.debugMode}", LogType.INFO)

        val templateFile = File(action.templatePath)
        if (!templateFile.exists()) {
            log("❌ Template image not found: ${action.templatePath}", LogType.ERROR)
            return
        }
        log("✅ Template file exists, size: ${templateFile.length()} bytes", LogType.INFO)

        val templateBitmap = BitmapFactory.decodeFile(action.templatePath)
        if (templateBitmap == null) {
            log("❌ Failed to load template image - bitmap is null", LogType.ERROR)
            return
        }
        log("✅ Template image loaded: ${templateBitmap.width}x${templateBitmap.height}", LogType.INFO)

        val screenshotManager = ScreenshotManager.getInstance(context)
        val imageMatchingService = ImageMatchingService.getInstance()
        val currentState = screenshotManager.permissionState.value

        if (currentState != ScreenshotManager.PermissionState.Granted) {
            log("❌ Screen capture permission not granted. Please grant permission first.", LogType.ERROR)
            return
        }
        log("✅ Screen capture permission granted", LogType.INFO)

        val startTime = System.currentTimeMillis()
        val retryInterval = 500L
        var attemptCount = 0
        var lastScreenBitmap: Bitmap? = null
        var lastMatchResult: MatchResult? = null

        while (System.currentTimeMillis() - startTime < action.timeout) {
            if (executionJob?.isActive != true) {
                log("Execution stopped", LogType.WARNING)
                break
            }

            attemptCount++
            val elapsed = System.currentTimeMillis() - startTime
            log("🔍 Attempt $attemptCount (elapsed: ${elapsed}ms/$action.timeout)...", LogType.INFO)

            try {
                val screenBitmap = takeScreenshotToBitmap()
                if (screenBitmap != null) {
                    lastScreenBitmap = screenBitmap
                    log("📸 Screenshot taken: ${screenBitmap.width}x${screenBitmap.height}", LogType.INFO)
                    
                    val matchResult = imageMatchingService.findMatch(
                        screenBitmap,
                        templateBitmap,
                        action.threshold
                    )
                    lastMatchResult = matchResult

                    // 判断是否达到阈值
                    val isMatchFound = matchResult != null && matchResult.similarity >= action.threshold

                    if (isMatchFound) {
                        log("✅ Image found at (${matchResult.x}, ${matchResult.y}) with similarity ${String.format("%.2f", matchResult.similarity)}", LogType.SUCCESS)
                        
                        if (action.debugMode) {
                            saveDebugScreenshot(screenBitmap, matchResult, templateBitmap.width, templateBitmap.height, true, attemptCount)
                        }
                        
                        if (action.saveResult) {
                            // Save result to variable store
                            val resultMap = mapOf(
                                "found" to true,
                                "x" to matchResult.x,
                                "y" to matchResult.y,
                                "similarity" to matchResult.similarity
                            )
                            action.resultVarName?.let { varName ->
                                variableStore[varName] = resultMap
                            }
                        }
                        return
                    } else {
                        // 没找到匹配
                        if (matchResult != null) {
                            log("❌ No match above threshold (best: ${String.format("%.2f", matchResult.similarity)})", LogType.INFO)
                        } else {
                            log("❌ No match at all in this attempt", LogType.INFO)
                        }
                        
                        // 如果是调试模式，每次尝试都保存截图
                        if (action.debugMode) {
                            saveDebugScreenshot(screenBitmap, matchResult, templateBitmap.width, templateBitmap.height, false, attemptCount)
                        }
                    }
                } else {
                    log("⚠️ Failed to take screenshot", LogType.WARNING)
                }
            } catch (e: Exception) {
                log("❌ Error during image matching: ${e.message}", LogType.WARNING)
                e.printStackTrace()
            }

            if (System.currentTimeMillis() - startTime < action.timeout) {
                log("⏳ Waiting $retryInterval ms before next attempt...", LogType.INFO)
                delay(retryInterval)
            }
        }

        log("⏰ Timeout reached! Image not found after $attemptCount attempts and ${System.currentTimeMillis() - startTime}ms", LogType.WARNING)
    }

    private suspend fun findText(action: ScriptAction.FindText) {
        log("===== Starting Find Text ======", LogType.INFO)
        log("Target text: ${action.targetText}", LogType.INFO)
        log("Threshold: ${action.threshold}", LogType.INFO)
        log("Timeout: ${action.timeout}ms", LogType.INFO)
        log("Debug mode: ${action.debugMode}", LogType.INFO)

        val screenshotManager = ScreenshotManager.getInstance(context)
        val textRecognitionService = TextRecognitionService.getInstance()
        val currentState = screenshotManager.permissionState.value

        if (currentState != ScreenshotManager.PermissionState.Granted) {
            log("❌ Screen capture permission not granted. Please grant permission first.", LogType.ERROR)
            return
        }
        log("✅ Screen capture permission granted", LogType.INFO)

        val startTime = System.currentTimeMillis()
        val retryInterval = 500L
        var attemptCount = 0
        var lastScreenBitmap: Bitmap? = null
        var lastRecognitionResult: TextRecognitionService.TextRecognitionResult? = null

        while (System.currentTimeMillis() - startTime < action.timeout) {
            if (executionJob?.isActive != true) {
                log("Execution stopped", LogType.WARNING)
                break
            }

            attemptCount++
            val elapsed = System.currentTimeMillis() - startTime
            log("🔍 Attempt $attemptCount (elapsed: ${elapsed}ms/$action.timeout)...", LogType.INFO)

            try {
                val screenBitmap = takeScreenshotToBitmap()
                if (screenBitmap != null) {
                    lastScreenBitmap = screenBitmap
                    log("📸 Screenshot taken: ${screenBitmap.width}x${screenBitmap.height}", LogType.INFO)
                    
                    val recognitionResult = textRecognitionService.findTextWithAllResults(
                        screenBitmap,
                        action.targetText,
                        action.threshold
                    )
                    lastRecognitionResult = recognitionResult

                    // 判断是否达到阈值
                    val isTextFound = recognitionResult.bestMatch != null && recognitionResult.bestMatch.similarity >= action.threshold

                    if (isTextFound) {
                        val match = recognitionResult.bestMatch
                        log("✅ Text found at (${match.x}, ${match.y}) with similarity ${String.format("%.2f", match.similarity)}, text: '${match.text}'", LogType.SUCCESS)
                        
                        if (action.debugMode) {
                            saveTextDebugScreenshot(screenBitmap, recognitionResult.allMatches, true, attemptCount, action.targetText)
                        }
                        
                        if (action.saveResult) {
                            // Save result to variable store
                            val resultMap = mapOf(
                                "found" to true,
                                "x" to match.x,
                                "y" to match.y,
                                "similarity" to match.similarity,
                                "text" to match.text
                            )
                            action.resultVarName?.let { varName ->
                                variableStore[varName] = resultMap
                            }
                        }
                        return
                    } else {
                        // 没找到匹配
                        val bestMatch = recognitionResult.bestMatch
                        if (bestMatch != null) {
                            log("❌ No match above threshold (best: ${String.format("%.2f", bestMatch.similarity)}, text: '${bestMatch.text}')", LogType.INFO)
                        } else {
                            log("❌ No match at all in this attempt", LogType.INFO)
                        }
                        
                        // 如果是调试模式，每次尝试都保存截图
                        if (action.debugMode) {
                            saveTextDebugScreenshot(screenBitmap, recognitionResult.allMatches, false, attemptCount, action.targetText)
                        }
                    }
                } else {
                    log("⚠️ Failed to take screenshot", LogType.WARNING)
                }
            } catch (e: Exception) {
                log("❌ Error during text recognition: ${e.message}", LogType.WARNING)
                e.printStackTrace()
            }

            if (System.currentTimeMillis() - startTime < action.timeout) {
                log("⏳ Waiting $retryInterval ms before next attempt...", LogType.INFO)
                delay(retryInterval)
            }
        }

        log("⏰ Timeout reached! Text not found after $attemptCount attempts and ${System.currentTimeMillis() - startTime}ms", LogType.WARNING)
    }

    private fun saveDebugScreenshot(
        screenBitmap: Bitmap,
        matchResult: MatchResult?,
        templateWidth: Int,
        templateHeight: Int,
        found: Boolean,
        attemptCount: Int
    ) {
        try {
            // Create a mutable copy of the bitmap
            val mutableBitmap = screenBitmap.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = android.graphics.Canvas(mutableBitmap)
            val paint = android.graphics.Paint()
            
            if (matchResult != null) {
                if (found) {
                    // 找到匹配 - 红色框
                    paint.color = android.graphics.Color.RED
                } else {
                    // 没找到但有最佳匹配 - 黄色框
                    paint.color = android.graphics.Color.YELLOW
                }
                paint.style = android.graphics.Paint.Style.STROKE
                paint.strokeWidth = 8f
                
                val left = matchResult.x - templateWidth / 2
                val top = matchResult.y - templateHeight / 2
                val right = matchResult.x + templateWidth / 2
                val bottom = matchResult.y + templateHeight / 2
                
                canvas.drawRect(
                    left.toFloat(),
                    top.toFloat(),
                    right.toFloat(),
                    bottom.toFloat(),
                    paint
                )
                
                // Draw text with similarity
                paint.style = android.graphics.Paint.Style.FILL
                paint.textSize = 48f
                val text = if (found) {
                    "Found: ${String.format("%.2f", matchResult.similarity)}"
                } else {
                    "Best Match: ${String.format("%.2f", matchResult.similarity)}"
                }
                canvas.drawText(text, left.toFloat(), (top - 10).toFloat(), paint)
            } else {
                // No match found at all
                paint.color = android.graphics.Color.YELLOW
                paint.style = android.graphics.Paint.Style.FILL
                paint.textSize = 64f
                canvas.drawText("No Match Found", 50f, 100f, paint)
            }
            
            // Save the bitmap
            val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            val fileName = "Debug_FindImage_Attempt${attemptCount}_${sdf.format(java.util.Date())}"
            
            val screenshotManager = ScreenshotManager.getInstance(context)
            screenshotManager.saveBitmapToFile(mutableBitmap, fileName) { result ->
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    log("📸 Debug screenshot saved: ${file.absolutePath}", LogType.SUCCESS)
                } else {
                    log("❌ Failed to save debug screenshot", LogType.ERROR)
                }
            }
            
        } catch (e: Exception) {
            log("❌ Error saving debug screenshot: ${e.message}", LogType.ERROR)
            e.printStackTrace()
        }
    }

    private fun saveTextDebugScreenshot(
        screenBitmap: Bitmap,
        allMatches: List<TextMatchResult>,
        found: Boolean,
        attemptCount: Int,
        targetText: String
    ) {
        try {
            val title = if (found) "✅ Found: '$targetText'" else "❌ Looking for: '$targetText'"
            val mutableBitmap = TextRecognitionService.getInstance().drawMultipleTextDetectionsOnBitmap(
                screenBitmap,
                allMatches,
                title
            )
            
            // Save the bitmap
            val sdf = java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.getDefault())
            val fileName = "Debug_FindText_Attempt${attemptCount}_${sdf.format(java.util.Date())}"
            
            val screenshotManager = ScreenshotManager.getInstance(context)
            screenshotManager.saveBitmapToFile(mutableBitmap, fileName) { result ->
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    log("📸 Text debug screenshot saved: ${file.absolutePath}", LogType.SUCCESS)
                } else {
                    log("❌ Failed to save text debug screenshot", LogType.ERROR)
                }
            }
            
        } catch (e: Exception) {
            log("❌ Error saving text debug screenshot: ${e.message}", LogType.ERROR)
            e.printStackTrace()
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private suspend fun takeScreenshotToBitmap(): Bitmap? {
        val screenshotManager = ScreenshotManager.getInstance(context)
        
        val currentState = screenshotManager.permissionState.value
        if (currentState != ScreenshotManager.PermissionState.Granted) {
            return null
        }

        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val fileName = "Temp_${System.currentTimeMillis()}"
            
            screenshotManager.takeScreenshot(fileName) { result ->
                if (result.isSuccess) {
                    val file = result.getOrThrow()
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                    file.delete()
                    continuation.resume(bitmap, onCancellation = null)
                } else {
                    continuation.resume(null, onCancellation = null)
                }
            }
        }
    }

    private suspend fun checkCondition(action: ScriptAction.Condition): Boolean {
        return when (action.type) {
            ConditionType.IMAGE_FOUND -> {
                checkImageFound(action.param1, action.param2.toDoubleOrNull() ?: 0.8)
            }
            ConditionType.IMAGE_NOT_FOUND -> {
                !checkImageFound(action.param1, action.param2.toDoubleOrNull() ?: 0.8)
            }
            ConditionType.TEXT_FOUND -> {
                checkTextFound(action.param1, action.param2.toDoubleOrNull() ?: 0.8)
            }
            ConditionType.TEXT_NOT_FOUND -> {
                !checkTextFound(action.param1, action.param2.toDoubleOrNull() ?: 0.8)
            }
            ConditionType.COLOR_MATCH -> {
                false
            }
            ConditionType.COLOR_NOT_MATCH -> {
                false
            }
            ConditionType.ALWAYS_TRUE -> {
                true
            }
            ConditionType.ALWAYS_FALSE -> {
                false
            }
        }
    }

    private suspend fun checkImageFound(templatePath: String, threshold: Double): Boolean {
        log("===== Checking if image exists ======", LogType.INFO)
        
        val templateFile = File(templatePath)
        if (!templateFile.exists()) {
            log("❌ Template image not found: $templatePath", LogType.ERROR)
            return false
        }

        val templateBitmap = BitmapFactory.decodeFile(templatePath)
        if (templateBitmap == null) {
            log("❌ Failed to load template image", LogType.ERROR)
            return false
        }

        val screenshotManager = ScreenshotManager.getInstance(context)
        val imageMatchingService = ImageMatchingService.getInstance()
        val currentState = screenshotManager.permissionState.value

        if (currentState != ScreenshotManager.PermissionState.Granted) {
            log("❌ Screen capture permission not granted", LogType.ERROR)
            return false
        }

        log("📸 Taking screenshot for image check...", LogType.INFO)
        val screenBitmap = takeScreenshotToBitmap()
        if (screenBitmap == null) {
            log("❌ Failed to take screenshot for condition check", LogType.ERROR)
            return false
        }
        log("✅ Screenshot taken, starting match...", LogType.INFO)
        
        val matchResult = imageMatchingService.findMatch(screenBitmap, templateBitmap, threshold)
        val found = matchResult != null
        
        if (found) {
            log("✅ Image FOUND at (${matchResult?.x}, ${matchResult?.y}), similarity: ${String.format("%.2f", matchResult?.similarity ?: 0.0)}", LogType.SUCCESS)
        } else {
            log("❌ Image NOT found", LogType.INFO)
        }
        
        return found
    }

    private suspend fun checkTextFound(targetText: String, threshold: Double): Boolean {
        log("===== Checking if text exists ======", LogType.INFO)
        
        val screenshotManager = ScreenshotManager.getInstance(context)
        val textRecognitionService = TextRecognitionService.getInstance()
        val currentState = screenshotManager.permissionState.value

        if (currentState != ScreenshotManager.PermissionState.Granted) {
            log("❌ Screen capture permission not granted", LogType.ERROR)
            return false
        }

        log("📸 Taking screenshot for text check...", LogType.INFO)
        val screenBitmap = takeScreenshotToBitmap()
        if (screenBitmap == null) {
            log("❌ Failed to take screenshot for condition check", LogType.ERROR)
            return false
        }
        log("✅ Screenshot taken, starting text recognition...", LogType.INFO)
        
        val recognitionResult = textRecognitionService.findTextWithAllResults(screenBitmap, targetText, threshold)
        val found = recognitionResult.bestMatch != null && recognitionResult.bestMatch.similarity >= threshold
        
        if (found) {
            val match = recognitionResult.bestMatch
            log("✅ Text FOUND at (${match.x}, ${match.y}), similarity: ${String.format("%.2f", match.similarity)}, text: '${match.text}'", LogType.SUCCESS)
        } else {
            log("❌ Text NOT found", LogType.INFO)
        }
        
        return found
    }

    private fun log(message: String, type: LogType = LogType.INFO) {
        // 输出到 logcat
        when (type) {
            LogType.INFO -> Log.i(TAG, message)
            LogType.SUCCESS -> Log.d(TAG, "[SUCCESS] $message")
            LogType.WARNING -> Log.w(TAG, message)
            LogType.ERROR -> Log.e(TAG, message)
        }
        
        // 输出到内部状态
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(ExecutionLog(message = message, type = type))
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }
        _logs.value = currentLogs
        
        val typePrefix = when (type) {
            LogType.INFO -> "[INFO]"
            LogType.SUCCESS -> "[OK]"
            LogType.WARNING -> "[WARN]"
            LogType.ERROR -> "[ERR]"
        }
        FloatingWindowService.addLog("$typePrefix $message")
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
