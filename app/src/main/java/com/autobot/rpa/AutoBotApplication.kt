package com.autobot.rpa

import android.app.Application
import com.autobot.rpa.service.ImageMatchingService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AutoBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize OpenCV for image matching
        ImageMatchingService.init(this)
    }
}
