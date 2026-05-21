package com.autobot.rpa.data.repository

import com.autobot.rpa.data.database.ScriptDao
import com.autobot.rpa.data.model.Script
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScriptRepository @Inject constructor(
    private val scriptDao: ScriptDao
) {
    fun getAllScripts(): Flow<List<Script>> = scriptDao.getAllScripts()

    suspend fun getScriptById(id: Long): Script? = scriptDao.getScriptById(id)

    suspend fun insertScript(script: Script): Long = scriptDao.insertScript(script)

    suspend fun updateScript(script: Script) = scriptDao.updateScript(script)

    suspend fun deleteScript(script: Script) = scriptDao.deleteScript(script)

    suspend fun deleteScriptById(id: Long) = scriptDao.deleteScriptById(id)

    suspend fun incrementRunCount(id: Long) = scriptDao.incrementRunCount(id)
}
