package com.autobot.rpa.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobot.rpa.data.model.Script
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import com.autobot.rpa.data.model.ScriptGroup
import com.autobot.rpa.data.repository.GroupRepository
import com.autobot.rpa.data.repository.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
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

    private val _isShowingScripts = MutableStateFlow(false)
    val isShowingScripts: StateFlow<Boolean> = _isShowingScripts

    init {
        loadData()
    }

    fun setIsShowingScripts(showing: Boolean) {
        _isShowingScripts.value = showing
    }

    private fun loadData() {
        viewModelScope.launch {
            _isLoading.value = true
            
            combine(
                _selectedGroupId.flatMapConcat { selectedId ->
                    scriptRepository.getScriptsByGroupId(selectedId)
                },
                groupRepository.getAllGroups(),
                _selectedGroupId
            ) { filteredScripts, groups, selectedId ->
                Triple(filteredScripts, groups, selectedId)
            }.collect { (filteredScripts, groups, selectedId) ->
                _groups.value = groups
                _filteredScripts.value = filteredScripts
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
