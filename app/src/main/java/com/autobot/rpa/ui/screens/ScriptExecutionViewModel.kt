package com.autobot.rpa.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.repository.ScriptRepository
import com.autobot.rpa.service.AutomationEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScriptExecutionViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository,
    private val automationEngine: AutomationEngine
) : ViewModel() {

    val executionState = automationEngine.executionState
    val logs = automationEngine.logs
    val currentActionIndex = automationEngine.currentActionIndex

    private val _scripts = MutableStateFlow<List<Script>>(emptyList())
    val scripts: StateFlow<List<Script>> = _scripts

    private val _selectedScript = MutableStateFlow<Script?>(null)
    val selectedScript: StateFlow<Script?> = _selectedScript

    init {
        loadScripts()
    }

    private fun loadScripts() {
        viewModelScope.launch {
            scriptRepository.getAllScripts().collect { scriptList ->
                _scripts.value = scriptList
            }
        }
    }

    fun selectScript(script: Script) {
        _selectedScript.value = script
    }

    fun startExecution() {
        _selectedScript.value?.let { script ->
            automationEngine.startExecution(script)
        }
    }

    fun pauseExecution() {
        automationEngine.pauseExecution()
    }

    fun resumeExecution() {
        automationEngine.resumeExecution()
    }

    fun stopExecution() {
        automationEngine.stopExecution()
    }

    fun clearLogs() {
        automationEngine.clearLogs()
    }
}
