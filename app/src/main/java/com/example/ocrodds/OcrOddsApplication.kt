package com.example.ocrodds

import android.app.Application
import androidx.compose.ui.platform.LocalContext
import com.example.ocrodds.data.AppContainer
import com.example.ocrodds.data.DefaultAppContainer

class OcrOddsApplication : Application() {
    lateinit var container: AppContainer
    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer()
    }
}