package org.skynetsoftware.skeletonnotes

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation test runner that swaps in [TestSkeletonNotesApplication] so instrumented
 * tests run against an in-memory database instead of the production on-disk one.
 */
class SkeletonNotesTestRunner : AndroidJUnitRunner() {

    override fun newApplication(
        classLoader: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(
        classLoader,
        TestSkeletonNotesApplication::class.java.name,
        context,
    )
}
