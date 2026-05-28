package com.autobot.rpa.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.model.ScriptGroup
import com.autobot.rpa.data.repository.GroupRepository
import com.autobot.rpa.data.repository.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ScriptListViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _scripts = MutableStateFlow<List<Script>>(emptyList())
    val scripts: StateFlow<List<Script>> = _scripts

    private val _groups = MutableStateFlow<List<ScriptGroup>>(emptyList())
    val groups: StateFlow<List<ScriptGroup>> = _groups

    private val _selectedGroupId = MutableStateFlow<Long?>(null)
    val selectedGroupId: StateFlow<Long?> = _selectedGroupId

    private val _filteredScripts = MutableStateFlow<List<Script>>(emptyList())
    val filteredScripts: StateFlow<List<Script>> = _filteredScripts

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            combine(
                scriptRepository.getAllScripts(),
                groupRepository.getAllGroups(),
                _selectedGroupId
            ) { scripts, groups, selectedId ->
                Triple(scripts, groups, selectedId)
            }.collect { (scripts, groups, selectedId) ->
                _scripts.value = scripts
                _groups.value = groups
                _filteredScripts.value = if (selectedId == null) {
                    scripts.filter { it.groupId == null }
                } else {
                    scripts.filter { it.groupId == selectedId }
                }
                _isLoading.value = false
            }
        }
    }

    fun selectGroup(groupId: Long?) {
        _selectedGroupId.value = groupId
    }

    fun updateScriptGroup(script: Script, groupId: Long?) {
        viewModelScope.launch {
            scriptRepository.updateScript(script.copy(groupId = groupId))
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
            val script = Script(name = name, groupId = _selectedGroupId.value)
            scriptId = scriptRepository.insertScript(script)
        }
        return scriptId
    }
}
