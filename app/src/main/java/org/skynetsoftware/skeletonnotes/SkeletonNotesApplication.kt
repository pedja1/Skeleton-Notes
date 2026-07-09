package org.skynetsoftware.skeletonnotes

import android.app.Application
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.di.AppGraph
import org.skynetsoftware.skeletonnotes.di.ProductionAppGraph

/**
 * Application entry point. Builds the object graph and installs it into [AppDi].
 *
 * Open so instrumented tests can subclass it and override [createGraph] to install an
 * alternative graph (for example one backed by an in-memory database).
 */
open class SkeletonNotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppDi.install(createGraph())
    }

    /**
     * Creates the [AppGraph] to install. Override in tests to substitute the object graph.
     */
    protected open fun createGraph(): AppGraph = ProductionAppGraph(this)
}
