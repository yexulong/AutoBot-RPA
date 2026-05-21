package com.autobot.rpa.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.model.ScriptConverters

@Database(entities = [Script::class], version = 1, exportSchema = false)
@TypeConverters(ScriptConverters::class)
abstract class AutoBotDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao
}
