package com.autobot.rpa.data.database

import androidx.room.*
import com.autobot.rpa.data.model.Script
import kotlinx.coroutines.flow.Flow

@Dao
interface ScriptDao {
    @Query("SELECT * FROM scripts ORDER BY updatedAt DESC")
    fun getAllScripts(): Flow<List<Script>>

    @Query("SELECT * FROM scripts WHERE id = :id")
    suspend fun getScriptById(id: Long): Script?

    @Query("SELECT * FROM scripts WHERE groupId IS NULL ORDER BY updatedAt DESC")
    fun getScriptsWithoutGroup(): Flow<List<Script>>

    @Query("SELECT * FROM scripts WHERE groupId = :groupId ORDER BY updatedAt DESC")
    fun getScriptsByGroupId(groupId: Long): Flow<List<Script>>

    fun getScriptsByGroupId(groupId: Long?): Flow<List<Script>> {
        return if (groupId == null) {
            getScriptsWithoutGroup()
        } else {
            getScriptsByGroupId(groupId)
        }
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScript(script: Script): Long

    @Update
    suspend fun updateScript(script: Script)

    @Delete
    suspend fun deleteScript(script: Script)

    @Query("DELETE FROM scripts WHERE id = :id")
    suspend fun deleteScriptById(id: Long)

    @Query("UPDATE scripts SET runCount = runCount + 1, lastRunAt = :timestamp WHERE id = :id")
    suspend fun incrementRunCount(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE scripts SET groupId = :groupId WHERE id = :scriptId")
    suspend fun updateScriptGroup(scriptId: Long, groupId: Long?)

    @Query("UPDATE scripts SET groupId = NULL WHERE groupId = :groupId")
    suspend fun setScriptsGroupToNull(groupId: Long)
}
