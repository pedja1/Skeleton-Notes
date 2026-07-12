package org.skynetsoftware.skeletonnotes.nextcloud.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Unit tests for [SerialSyncRunner]'s single-in-flight and preempt-and-serialize behavior.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SerialSyncRunnerTest {
    @Test
    fun startRunsSyncAndInvokesOnComplete() =
        runTest {
            var synced = false
            var completed = false
            val done = CompletableDeferred<Unit>()
            val runner = SerialSyncRunner(backgroundScope) { synced = true }

            runner.start {
                completed = true
                done.complete(Unit)
            }
            done.await()

            assertTrue(synced)
            assertTrue(completed)
        }

    @Test
    fun stopCancelsCurrentRunSoOnCompleteIsNotInvoked() =
        runTest {
            val entered = CompletableDeferred<Unit>()
            val finished = CompletableDeferred<Unit>()
            var completed = false
            val runner =
                SerialSyncRunner(backgroundScope) {
                    try {
                        entered.complete(Unit)
                        awaitCancellation()
                    } finally {
                        finished.complete(Unit)
                    }
                }

            runner.start { completed = true }
            entered.await()
            runner.stop()
            finished.await()

            assertFalse(completed)
        }

    @Test
    fun secondStartPreemptsFirstSoRunsNeverOverlap() =
        runTest {
            val active = AtomicInteger(0)
            val overlaps = AtomicInteger(0)
            val callCount = AtomicInteger(0)
            val firstEntered = CompletableDeferred<Unit>()
            val secondEntered = CompletableDeferred<Unit>()

            val runner =
                SerialSyncRunner(backgroundScope) {
                    if (active.incrementAndGet() > 1) overlaps.incrementAndGet()
                    try {
                        if (callCount.incrementAndGet() == 1) {
                            firstEntered.complete(Unit)
                        } else {
                            secondEntered.complete(Unit)
                        }
                        awaitCancellation()
                    } finally {
                        active.decrementAndGet()
                    }
                }

            runner.start { }
            firstEntered.await()
            runner.start { }
            // The second run only enters after the first has been cancelled and joined.
            secondEntered.await()

            assertEquals(0, overlaps.get())
            assertEquals(1, active.get())

            runner.stop()
        }
}
