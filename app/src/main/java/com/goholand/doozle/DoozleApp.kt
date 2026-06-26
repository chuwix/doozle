package com.goholand.doozle

import android.app.Application
import com.goholand.doozle.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class DoozleApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@DoozleApp)
            modules(appModule)
        }
    }
}
