package com.autobot.rpa.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "script_groups")
data class ScriptGroup(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
