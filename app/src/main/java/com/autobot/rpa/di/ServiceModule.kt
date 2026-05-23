package com.autobot.rpa.di

import android.content.Context
import com.autobot.rpa.service.ScreenshotManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ServiceModule {

    @Provides
    @Singleton
    fun provideScreenshotManager(@ApplicationContext context: Context): ScreenshotManager {
        return ScreenshotManager.getInstance(context)
    }
}
