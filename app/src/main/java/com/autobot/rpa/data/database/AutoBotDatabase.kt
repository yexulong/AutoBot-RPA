package com.autobot.rpa.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.autobot.rpa.data.model.Script
import com.autobot.rpa.data.model.ScriptConverters
import com.autobot.rpa.data.model.ScriptGroup

@Database(entities = [Script::class, ScriptGroup::class], version = 2, exportSchema = false)
@TypeConverters(ScriptConverters::class)
abstract class AutoBotDatabase : RoomDatabase() {
    abstract fun scriptDao(): ScriptDao
    abstract fun groupDao(): GroupDao

    companion object {
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("CREATE TABLE IF NOT EXISTS `script_groups` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL)")
                database.execSQL("ALTER TABLE `scripts` ADD COLUMN `groupId` INTEGER")
            }
        }
    }
}
