package org.skynetsoftware.skeletonnotes

import org.skynetsoftware.skeletonnotes.di.AppGraph
import org.skynetsoftware.skeletonnotes.di.ProductionAppGraph

/**
 * [SkeletonNotesApplication] used by instrumented tests. Installs a production-shaped graph
 * backed by an in-memory database so tests never touch the on-disk database.
 */
class TestSkeletonNotesApplication : SkeletonNotesApplication() {

    override fun createGraph(): AppGraph = ProductionAppGraph(this, inMemoryDatabase = true)
}
