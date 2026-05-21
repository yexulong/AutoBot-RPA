package com.autobot.rpa.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.model.ScriptAction
import com.autobot.rpa.data.repository.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScriptEditorViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val scriptId: Long = savedStateHandle["scriptId"] ?: -1L

    private val _script = MutableStateFlow<Script?>(null)
    val script: StateFlow<Script?> = _script

    private val _actions = MutableStateFlow<List<ScriptAction>>(emptyList())
    val actions: StateFlow<List<ScriptAction>> = _actions

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _hasChanges = MutableStateFlow(false)
    val hasChanges: StateFlow<Boolean> = _hasChanges

    init {
        if (scriptId > 0) {
            loadScript()
        }
    }

    private fun loadScript() {
        viewModelScope.launch {
            _isLoading.value = true
            val loadedScript = scriptRepository.getScriptById(scriptId)
            _script.value = loadedScript
            _actions.value = loadedScript?.actions ?: emptyList()
            _isLoading.value = false
        }
    }

    fun addAction(action: ScriptAction) {
        val currentActions = _actions.value.toMutableList()
        val newAction = action.copy(order = currentActions.size)
        currentActions.add(newAction)
        _actions.value = currentActions
        _hasChanges.value = true
    }

    fun updateAction(action: ScriptAction) {
        val currentActions = _actions.value.toMutableList()
        val index = currentActions.indexOfFirst { it.id == action.id }
        if (index != -1) {
            currentActions[index] = action
            _actions.value = currentActions
            _hasChanges.value = true
        }
    }

    fun removeAction(actionId: String) {
        val currentActions = _actions.value.toMutableList()
        currentActions.removeAll { it.id == actionId }
        _actions.value = currentActions.mapIndexed { index, scriptAction ->
            scriptAction.copy(order = index)
        }
        _hasChanges.value = true
    }

    fun moveAction(fromIndex: Int, toIndex: Int) {
        if (fromIndex < 0 || toIndex < 0 || fromIndex >= _actions.value.size || toIndex >= _actions.value.size) {
            return
        }
        val currentActions = _actions.value.toMutableList()
        val action = currentActions.removeAt(fromIndex)
        currentActions.add(toIndex, action)
        _actions.value = currentActions.mapIndexed { index, scriptAction ->
            scriptAction.copy(order = index)
        }
        _hasChanges.value = true
    }

    fun saveScript(name: String, description: String = "") {
        viewModelScope.launch {
            val existingScript = _script.value
            if (existingScript != null) {
                val updatedScript = existingScript.copy(
                    name = name,
                    description = description,
                    actions = _actions.value,
                    updatedAt = System.currentTimeMillis()
                )
                scriptRepository.updateScript(updatedScript)
            } else {
                val newScript = Script(
                    name = name,
                    description = description,
                    actions = _actions.value
                )
                scriptRepository.insertScript(newScript)
            }
            _hasChanges.value = false
        }
    }
}
