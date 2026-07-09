package org.skynetsoftware.skeletonnotes

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isChecked
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import org.hamcrest.CoreMatchers.allOf
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.skynetsoftware.skeletonnotes.di.AppDi
import org.skynetsoftware.skeletonnotes.di.ProductionAppGraph
import org.skynetsoftware.skeletonnotes.domain.model.Result
import org.skynetsoftware.skeletonnotes.domain.model.nextcloud.NextcloudConnectionInfo
import org.skynetsoftware.skeletonnotes.settings.SettingsActivity

@RunWith(AndroidJUnit4::class)
class SettingsActivityTest {

    @After
    fun tearDown() {
        AppDi.install(ProductionAppGraph(testApplication, inMemoryDatabase = true))
    }

    @Test
    fun toolbarTitleIsSettings() {
        ActivityScenario.launch(SettingsActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_title))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.settings_title)))
        }
    }

    @Test
    fun backIconIsDisplayed() {
        ActivityScenario.launch(SettingsActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_back))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun nextcloudSyncSectionHeaderIsDisplayed() {
        ActivityScenario.launch(SettingsActivity::class.java).use { _ ->
            onView(withText(R.string.settings_section_nextcloud_sync))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun dataSectionHeaderIsDisplayed() {
        ActivityScenario.launch(SettingsActivity::class.java).use { _ ->
            onView(withText(R.string.settings_section_data))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun periodicSyncItemIsDisplayed() {
        ActivityScenario.launch(SettingsActivity::class.java).use { _ ->
            onView(withText(R.string.settings_item_periodic_sync_title))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun syncNowItemIsDisplayed() {
        ActivityScenario.launch(SettingsActivity::class.java).use { _ ->
            onView(withText(R.string.settings_item_sync_now_title))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun tapConnectItemShowsServerUrlDialog() {
        ActivityScenario.launch(SettingsActivity::class.java).use { _ ->
            onView(withId(R.id.itemNextcloudConnect)).perform(click())
            onView(withText(R.string.nextcloud_server_url_title))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun connectItemShowsConnectedStateWhenConnectionExists() {
        val connectionInfo = NextcloudConnectionInfo("https://example.com", "user")
        val ncRepo = FakeSettingsNextcloudRepository(connection = connectionInfo)
        val settingsRepo = FakeSettingsRepository()
        val prodGraph = ProductionAppGraph(testApplication, inMemoryDatabase = true)
        val fakeGraph = FakeSettingsAppGraph(prodGraph, settingsRepo, ncRepo)
        AppDi.install(fakeGraph)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(allOf(
                withId(R.id.item_title),
                isDescendantOfA(withId(R.id.itemNextcloudConnect)),
            )).check(matches(withText(R.string.settings_item_nextcloud_connected_title)))
        }
    }

    @Test
    fun connectItemShowsErrorAfterFailedLogin() {
        val ncRepo = FakeSettingsNextcloudRepository(
            initiateResult = Result.Failure(Exception("connection failed")),
        )
        val settingsRepo = FakeSettingsRepository()
        val prodGraph = ProductionAppGraph(testApplication, inMemoryDatabase = true)
        val fakeGraph = FakeSettingsAppGraph(prodGraph, settingsRepo, ncRepo)
        AppDi.install(fakeGraph)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.itemNextcloudConnect)).perform(click())
            onView(withId(R.id.nextcloudServerUrl)).perform(typeText("https://example.com"))
            onView(withText(android.R.string.ok)).perform(click())

            onView(withText(R.string.settings_item_nextcloud_connect_error_subtitle))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun connectItemShowsProgressDuringInitiation() {
        val blocker = CompletableDeferred<Unit>()
        val ncRepo = FakeSettingsNextcloudRepository(suspendBlocker = blocker)
        val settingsRepo = FakeSettingsRepository()
        val prodGraph = ProductionAppGraph(testApplication, inMemoryDatabase = true)
        val fakeGraph = FakeSettingsAppGraph(prodGraph, settingsRepo, ncRepo)
        AppDi.install(fakeGraph)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.itemNextcloudConnect)).perform(click())
            onView(withId(R.id.nextcloudServerUrl)).perform(typeText("https://example.com"))
            onView(withText(android.R.string.ok)).perform(click())

            onView(allOf(
                withId(R.id.progressBar),
                isDescendantOfA(withId(R.id.itemNextcloudConnect)),
            )).check(matches(isDisplayed()))

            blocker.complete(Unit)
        }
    }

    @Test
    fun syncNowItemShowsFormattedDateWhenSyncDone() {
        val settingsRepo = FakeSettingsRepository(lastSyncTimestamp = System.currentTimeMillis())
        val ncRepo = FakeSettingsNextcloudRepository()
        val prodGraph = ProductionAppGraph(testApplication, inMemoryDatabase = true)
        val fakeGraph = FakeSettingsAppGraph(prodGraph, settingsRepo, ncRepo)
        AppDi.install(fakeGraph)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            val neverText = InstrumentationRegistry.getInstrumentation()
                .targetContext.getString(R.string.settings_item_nextcloud_last_sync_never)
            onView(allOf(
                withId(R.id.item_subtitle),
                isDescendantOfA(withId(R.id.itemNextcloudSyncNow)),
            )).check(matches(not(withText(containsString(neverText)))))
        }
    }

    @Test
    fun periodicSyncSwitchIsCheckedWhenEnabled() {
        val settingsRepo = FakeSettingsRepository(periodicSync = true)
        val ncRepo = FakeSettingsNextcloudRepository()
        val prodGraph = ProductionAppGraph(testApplication, inMemoryDatabase = true)
        val fakeGraph = FakeSettingsAppGraph(prodGraph, settingsRepo, ncRepo)
        AppDi.install(fakeGraph)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(allOf(
                withId(R.id.item_switch),
                isDescendantOfA(withId(R.id.itemNextcloudPeriodicSync)),
            )).check(matches(isChecked()))
        }
    }

    @Test
    fun periodicSyncSwitchIsUncheckedWhenDisabled() {
        val settingsRepo = FakeSettingsRepository(periodicSync = false)
        val ncRepo = FakeSettingsNextcloudRepository()
        val prodGraph = ProductionAppGraph(testApplication, inMemoryDatabase = true)
        val fakeGraph = FakeSettingsAppGraph(prodGraph, settingsRepo, ncRepo)
        AppDi.install(fakeGraph)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(allOf(
                withId(R.id.item_switch),
                isDescendantOfA(withId(R.id.itemNextcloudPeriodicSync)),
            )).check(matches(not(isChecked())))
        }
    }

    @Test
    fun serverUrlDialogCancelClosesDialog() {
        val ncRepo = FakeSettingsNextcloudRepository()
        val settingsRepo = FakeSettingsRepository()
        val prodGraph = ProductionAppGraph(testApplication, inMemoryDatabase = true)
        val fakeGraph = FakeSettingsAppGraph(prodGraph, settingsRepo, ncRepo)
        AppDi.install(fakeGraph)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.itemNextcloudConnect)).perform(click())
            onView(withText(android.R.string.cancel)).perform(click())

            onView(withId(R.id.itemNextcloudConnect))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun serverUrlDialogPreFillsExistingUrl() {
        val existingUrl = "https://my.nextcloud.org"
        val ncRepo = FakeSettingsNextcloudRepository(
            initiateResult = Result.Failure(Exception("connection failed")),
        )
        val settingsRepo = FakeSettingsRepository()
        val prodGraph = ProductionAppGraph(testApplication, inMemoryDatabase = true)
        val fakeGraph = FakeSettingsAppGraph(prodGraph, settingsRepo, ncRepo)
        AppDi.install(fakeGraph)

        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.itemNextcloudConnect)).perform(click())
            onView(withId(R.id.nextcloudServerUrl)).perform(typeText(existingUrl))
            onView(withText(android.R.string.ok)).perform(click())

            // Open dialog again - the URL should be pre-filled from the ViewModel state
            onView(withId(R.id.itemNextcloudConnect)).perform(click())
            onView(withId(R.id.nextcloudServerUrl))
                .check(matches(withText(existingUrl)))
        }
    }

    @Test
    fun toolbarActionButtonsAreHidden() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            onView(withId(R.id.toolbar_settings))
                .check(matches(not(isDisplayed())))
            onView(withId(R.id.toolbar_add_note))
                .check(matches(not(isDisplayed())))
        }
    }
}
