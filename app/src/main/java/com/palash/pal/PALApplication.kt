package com.palash.pal

import android.app.Application
import com.palash.pal.di.AppContainer

class PALApplication : Application() {
    val appContainer: AppContainer by lazy {
        AppContainer(this)
    }
}
