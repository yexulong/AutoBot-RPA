package com.autobot.rpa.di

import android.content.Context
import androidx.room.Room
import com.autobot.rpa.data.database.AutoBotDatabase
import com.autobot.rpa.data.database.ScriptDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AutoBotDatabase {
        return Room.databaseBuilder(
            context,
            AutoBotDatabase::class.java,
            "autobot_database"
        ).build()
    }

    @Provides
    @Singleton
    fun provideScriptDao(database: AutoBotDatabase): ScriptDao {
        return database.scriptDao()
    }
}
