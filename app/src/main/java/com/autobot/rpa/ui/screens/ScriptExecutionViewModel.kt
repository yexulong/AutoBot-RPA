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
                
                // 如果有选中的脚本，尝试从新列表中找到更新后的版本
                val currentSelected = _selectedScript.value
                if (currentSelected != null) {
                    val updatedScript = scriptList.find { it.id == currentSelected.id }
                    if (updatedScript != null) {
                        _selectedScript.value = updatedScript
                    } else if (scriptList.isNotEmpty()) {
                        // 如果原来的脚本被删除了，选中第一个脚本
                        _selectedScript.value = scriptList.first()
                    } else {
                        // 如果没有脚本了，清空选中状态
                        _selectedScript.value = null
                    }
                }
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
