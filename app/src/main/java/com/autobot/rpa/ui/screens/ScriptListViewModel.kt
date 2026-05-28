package com.autobot.rpa.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.model.ScriptGroup
import com.autobot.rpa.data.repository.GroupRepository
import com.autobot.rpa.data.repository.ScriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class ScriptListViewModel @Inject constructor(
    private val scriptRepository: ScriptRepository,
    private val groupRepository: GroupRepository
) : ViewModel() {

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
            _selectedGroupId.flatMapLatest { selectedId ->
                // 每当分组改变时，先设置加载状态
                _isLoading.value = true
                _filteredScripts.value = emptyList()
                
                combine(
                    scriptRepository.getScriptsByGroupId(selectedId),
                    groupRepository.getAllGroups()
                ) { scripts, groups ->
                    Triple(scripts, groups, selectedId)
                }
            }.collect { (scripts, groups, _) ->
                _groups.value = groups
                _filteredScripts.value = scripts
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

    suspend fun createNewScript(name: String): Long {
        val script = Script(name = name, groupId = _selectedGroupId.value)
        return scriptRepository.insertScript(script)
    }
}
