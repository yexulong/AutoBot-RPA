package com.autobot.rpa.data.database

import androidx.room.*
import com.autobot.rpa.data.model.ScriptGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    @Query("SELECT * FROM script_groups ORDER BY updatedAt DESC")
    fun getAllGroups(): Flow<List<ScriptGroup>>

    @Query("SELECT * FROM script_groups WHERE id = :id")
    suspend fun getGroupById(id: Long): ScriptGroup?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: ScriptGroup): Long

    @Update
    suspend fun updateGroup(group: ScriptGroup)

    @Delete
    suspend fun deleteGroup(group: ScriptGroup)

    @Query("DELETE FROM script_groups WHERE id = :id")
    suspend fun deleteGroupById(id: Long)
}
