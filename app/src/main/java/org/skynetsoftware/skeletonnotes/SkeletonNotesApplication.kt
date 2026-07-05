package org.skynetsoftware.skeletonnotes

import android.app.Application
import org.skynetsoftware.skeletonnotes.di.AppDi

class SkeletonNotesApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppDi.init(this)
    }
}