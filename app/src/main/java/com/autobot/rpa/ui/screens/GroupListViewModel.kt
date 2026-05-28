package com.autobot.rpa.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.autobot.rpa.data.model.ScriptGroup
import com.autobot.rpa.data.repository.GroupRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GroupListViewModel @Inject constructor(
    private val groupRepository: GroupRepository
) : ViewModel() {

    private val _groups = MutableStateFlow<List<ScriptGroup>>(emptyList())
    val groups: StateFlow<List<ScriptGroup>> = _groups

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    init {
        loadGroups()
    }

    private fun loadGroups() {
        viewModelScope.launch {
            _isLoading.value = true
            groupRepository.getAllGroups().collect { groupList ->
                _groups.value = groupList
                _isLoading.value = false
            }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            val group = ScriptGroup(name = name)
            groupRepository.insertGroup(group)
        }
    }

    fun updateGroup(group: ScriptGroup) {
        viewModelScope.launch {
            groupRepository.updateGroup(group.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteGroup(group: ScriptGroup) {
        viewModelScope.launch {
            groupRepository.deleteGroupById(group.id)
        }
    }
}
