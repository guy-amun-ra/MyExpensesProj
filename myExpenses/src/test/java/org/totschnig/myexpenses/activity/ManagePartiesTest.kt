package org.totschnig.myexpenses.activity

import android.content.ContentResolver
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.adevinta.android.barista.interaction.BaristaMenuClickInteractions.clickMenu
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.db2.requireParty

@RunWith(AndroidJUnit4::class)
class ManagePartiesTest {
    private lateinit var activityScenario: ActivityScenario<ManageParties>
    private var payee1 = "John Doe"
    private var payee2 = "Hinz Finz"

    private val contentResolver: ContentResolver
        get() = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver

    @Before
    fun fixture() {
        contentResolver.requireParty(payee1)
        contentResolver.requireParty(payee2)
        activityScenario = ActivityScenario.launch(ManageParties::class.java)
    }

    @Test
    fun testSearch() {
        onView(withId(R.id.list))
            .check(ViewAssertions.matches(ViewMatchers.hasChildCount(2)))
        clickMenu(R.id.SEARCH_COMMAND)
        onView(withId(androidx.appcompat.R.id.search_src_text)).perform(typeText("John"))
        onView(withId(R.id.list))
            .check(ViewAssertions.matches(ViewMatchers.hasChildCount(1)))
    }
}