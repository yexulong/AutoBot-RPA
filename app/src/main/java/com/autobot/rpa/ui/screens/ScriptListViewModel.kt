package com.autobot.rpa.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.repository.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScriptListViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository
) : ViewModel() {

    private val _scripts = MutableStateFlow<List<Script>>(emptyList())
    val scripts: StateFlow<List<Script>> = _scripts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadScripts()
    }

    private fun loadScripts() {
        viewModelScope.launch {
            _isLoading.value = true
            scriptRepository.getAllScripts().collect { scriptList ->
                _scripts.value = scriptList
                _isLoading.value = false
            }
        }
    }

    fun deleteScript(script: Script) {
        viewModelScope.launch {
            scriptRepository.deleteScript(script)
        }
    }

    fun createNewScript(name: String): Long {
        var scriptId = -1L
        viewModelScope.launch {
            val script = Script(name = name)
            scriptId = scriptRepository.insertScript(script)
        }
        return scriptId
    }
}
