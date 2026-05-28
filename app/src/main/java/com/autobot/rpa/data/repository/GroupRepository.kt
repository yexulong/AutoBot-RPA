package com.autobot.rpa.data.repository

import com.autobot.rpa.data.database.GroupDao
import com.autobot.rpa.data.database.ScriptDao
import com.autobot.rpa.data.model.ScriptGroup
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(
    private val groupDao: GroupDao,
    private val scriptDao: ScriptDao
) {
    fun getAllGroups(): Flow<List<ScriptGroup>> = groupDao.getAllGroups()

    suspend fun getGroupById(id: Long): ScriptGroup? = groupDao.getGroupById(id)

    suspend fun insertGroup(group: ScriptGroup): Long = groupDao.insertGroup(group)

    suspend fun updateGroup(group: ScriptGroup) = groupDao.updateGroup(group)

    suspend fun deleteGroup(group: ScriptGroup) = groupDao.deleteGroup(group)

    suspend fun deleteGroupById(id: Long) {
        scriptDao.setScriptsGroupToNull(id)
        groupDao.deleteGroupById(id)
    }
}
