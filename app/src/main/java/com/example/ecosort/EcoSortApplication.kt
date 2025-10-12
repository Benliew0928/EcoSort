package com.example.ecosort

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class EcoSortApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // App-wide initialization goes here
    }
}