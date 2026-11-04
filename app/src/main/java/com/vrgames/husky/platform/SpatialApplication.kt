package com.vrgames.husky.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.vrgames.husky.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
