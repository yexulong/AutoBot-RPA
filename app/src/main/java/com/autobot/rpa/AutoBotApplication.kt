package com.autobot.rpa

import android.app.Application
import com.autobot.rpa.service.ImageMatchingService
import com.autobot.rpa.service.TextRecognitionService
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class AutoBotApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Initialize OpenCV for image matching
        ImageMatchingService.init(this)
        // Initialize ML Kit for text recognition
        TextRecognitionService.init(this)
    }
}
