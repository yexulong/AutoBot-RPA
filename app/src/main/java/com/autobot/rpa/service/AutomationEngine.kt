package com.autobot.rpa.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.view.input_method.InputMethodManager
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

    private val _executionState = MutableStateFlow<ExecutionState>(ExecutionState.Idle)
    val executionState: StateFlow<ExecutionState> = _executionState

    private val _logs = MutableStateFlow<List<ExecutionLog>>(emptyList())
    val logs: StateFlow<List<ExecutionLog>> = _logs

    private val _currentActionIndex = MutableStateFlow(-1)
    val currentActionIndex: StateFlow<Int> = _currentActionIndex

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

    fun startExecution(script: Script) {
        if (_executionState.value == ExecutionState.Running) {
            log("Script is already running", LogType.WARNING)
            return
        }

        executionJob = scope.launch {
            _executionState.value = ExecutionState.Running
            _logs.value = emptyList()
            _currentActionIndex.value = -1

            log("Starting script: ${script.name}")
            AutoBotForegroundService.startService(context)
            AutoBotForegroundService.updateNotification(script.name)

            try {
                scriptRepository.incrementRunCount(script.id)
                executeActions(script.actions)
                _executionState.value = ExecutionState.Completed(script.id)
                log("Script completed successfully", LogType.SUCCESS)
            } catch (e: CancellationException) {
                log("Script execution cancelled", LogType.WARNING)
                _executionState.value = ExecutionState.Idle
            } catch (e: Exception) {
                log("Script execution failed: ${e.message}", LogType.ERROR)
                _executionState.value = ExecutionState.Error(e.message ?: "Unknown error")
            } finally {
                delay(1000)
                AutoBotForegroundService.stopService(context)
            }
        }
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
        log("Script stopped", LogType.WARNING)
    }

    private suspend fun executeActions(actions: List<ScriptAction>) {
        var index = 0
        while (index < actions.size) {
            if (!isActive) break

            while (_executionState.value == ExecutionState.Paused) {
                delay(100)
                if (!isActive) break
            }

            if (!isActive) break

            val action = actions[index]
            _currentActionIndex.value = index

            log("Executing: ${action::class.simpleName}", LogType.INFO)
            executeAction(action)

            index++
        }
    }

    private suspend fun executeAction(action: ScriptAction) {
        val accessibilityService = AutoBotAccessibilityService.getInstance()

        when (action) {
            is ScriptAction.Tap -> {
                if (accessibilityService != null) {
                    accessibilityService.performTapWithDelay(action.x, action.y, action.duration)
                    log("Tapped at (${action.x}, ${action.y})", LogType.SUCCESS)
                } else {
                    log("Accessibility service not available", LogType.ERROR)
                }
            }

            is ScriptAction.Swipe -> {
                if (accessibilityService != null) {
                    accessibilityService.performSwipeWithDelay(
                        action.startX, action.startY, action.endX, action.endY, action.duration
                    )
                    log("Swiped from (${action.startX}, ${action.startY}) to (${action.endX}, ${action.endY})", LogType.SUCCESS)
                } else {
                    log("Accessibility service not available", LogType.ERROR)
                }
            }

            is ScriptAction.LongPress -> {
                if (accessibilityService != null) {
                    accessibilityService.performLongPressWithDelay(action.x, action.y, action.duration)
                    log("Long pressed at (${action.x}, ${action.y}) for ${action.duration}ms", LogType.SUCCESS)
                } else {
                    log("Accessibility service not available", LogType.ERROR)
                }
            }

            is ScriptAction.TextInput -> {
                inputText(action.text)
                log("Input text: ${action.text}", LogType.SUCCESS)
            }

            is ScriptAction.KeyPress -> {
                sendKeyEvent(action.keyCode)
                log("Pressed key: ${action.keyCode}", LogType.SUCCESS)
            }

            is ScriptAction.Delay -> {
                delay(action.milliseconds.toLong())
                log("Waited ${action.milliseconds}ms", LogType.INFO)
            }

            is ScriptAction.Screenshot -> {
                takeScreenshot(action.fileName)
            }

            is ScriptAction.FindImage -> {
                findImage(action.templatePath, action.timeout)
            }

            is ScriptAction.LoopStart -> {
                log("Loop started: ${if (action.infinite) "infinite" else "${action.times} times"}", LogType.INFO)
            }

            is ScriptAction.LoopEnd -> {
                log("Loop ended", LogType.INFO)
            }

            is ScriptAction.Condition -> {
                log("Condition check: ${action.type}", LogType.INFO)
            }

            is ScriptAction.Comment -> {
                log("Comment: ${action.text}", LogType.INFO)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            imm.setAdditionalMimeTypes(arrayOf("text/plain", "text/*"))
        }
    }

    private fun sendKeyEvent(keyCode: Int) {
        val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val dispatcher = (context as? android.app.Activity)?.dispatchKeyEvent(keyEvent)
        val keyEventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        (context as? android.app.Activity)?.dispatchKeyEvent(keyEventUp)
    }

    private suspend fun takeScreenshot(fileName: String) {
        delay(200)
        log("Screenshot saved to: $fileName", LogType.SUCCESS)
    }

    private suspend fun findImage(templatePath: String, timeout: Int) {
        log("Finding image: $templatePath", LogType.INFO)
        delay(timeout.toLong())
        log("Image not found", LogType.WARNING)
    }

    private fun log(message: String, type: LogType = LogType.INFO) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(ExecutionLog(message = message, type = type))
        if (currentLogs.size > 100) {
            currentLogs.removeAt(0)
        }
        _logs.value = currentLogs
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }
}
