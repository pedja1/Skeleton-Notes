package org.skynetsoftware.skeletonnotes

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.matcher.IntentMatchers.hasComponent
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @Before
    fun setUp() {
        Intents.init()
    }

    @After
    fun tearDown() {
        Intents.release()
    }

    @Test
    fun toolbarTitleIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_title))
                .check(matches(isDisplayed()))
            onView(withId(R.id.toolbar_title))
                .check(matches(withText(R.string.app_name)))
        }
    }

    @Test
    fun toolbarHasSettingsIcon() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_settings))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun notesRecyclerViewIsDisplayed() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.recycler_notes))
                .check(matches(isDisplayed()))
        }
    }

    @Test
    fun settingsIconOpensSettingsActivity() {
        ActivityScenario.launch(MainActivity::class.java).use { _ ->
            onView(withId(R.id.toolbar_settings)).perform(click())
            intended(hasComponent(SettingsActivity::class.java.name))
        }
    }
}
