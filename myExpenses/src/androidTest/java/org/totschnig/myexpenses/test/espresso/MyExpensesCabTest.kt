package org.totschnig.myexpenses.test.espresso

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.*
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.TEST_TAG_CONTEXT_MENU
import org.totschnig.myexpenses.compose.TEST_TAG_LIST
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.testutils.BaseMyExpensesTest
import org.totschnig.myexpenses.util.Utils

class MyExpensesCabTest : BaseMyExpensesTest() {
    private val origListSize = 6
    private lateinit var account: Account
    @Before
    fun fixture() {
        val home = Utils.getHomeCurrency()
        account = Account(
            "Test account 1", home, 0, "",
            AccountType.CASH, Account.DEFAULT_COLOR
        )
        account.save()
        val op0 = Transaction.getNewInstance(account.id)
        op0.amount = Money(home, -100L)
        op0.save()
        for (i in 2 until 7) {
            op0.amount = Money(home, -100L * i)
            op0.date = op0.date - 10000
            op0.saveAsNew()
        }
        launch(account.id)
    }

    @Test
    fun cloneCommandIncreasesListSize() {
        assertListSize(origListSize)
        clickContextItem(R.string.menu_clone_transaction)
        closeKeyboardAndSave()
        assertListSize(origListSize + 1)
    }

    @Test
    fun editCommandKeepsListSize() {
        assertListSize(origListSize)
        clickContextItem(R.string.menu_edit)
        closeKeyboardAndSave()
        assertListSize(origListSize)
    }

    @Test
    fun createTemplateCommandCreatesTemplate() {
        val templateTitle = "Espresso Template Test"
        assertListSize(origListSize)
        clickContextItem(R.string.menu_create_template_from_transaction)
        onView(withId(R.id.Title)).perform(
            closeSoftKeyboard(),
            typeText(templateTitle),
            closeSoftKeyboard()
        )
        closeKeyboardAndSave()
        clickMenuItem(R.id.MANAGE_TEMPLATES_COMMAND)
        onView(withText(Matchers.`is`(templateTitle)))
            .check(matches(isDisplayed()))
    }

    @Test
    fun deleteCommandDecreasesListSize() {
        doDelete(useCab = false, cancel = false)
    }

    @Test
    fun deleteCommandDecreasesListSizeCab() {
        doDelete(useCab = true, cancel = false)
    }

    @Test
    fun deleteCommandCancelKeepsListSize() {
        doDelete(useCab = false, cancel = true)
    }

    @Test
    fun deleteCommandCancelKeepsListSizeCab() {
        doDelete(useCab = true, cancel = true)
    }

    private fun doDelete(useCab: Boolean, cancel: Boolean) {
        assertListSize(origListSize)
        triggerDelete(useCab)
        onView(withText(if (cancel) android.R.string.cancel else  R.string.menu_delete))
            .inRoot(isDialog())
            .perform(click())
        assertListSize(if (cancel) origListSize else origListSize - 1)
    }

    @Test
    fun deleteCommandWithVoidOptionCab() {
        doDeleteCommandWithVoidOption(true)
    }

    @Test
    fun deleteCommandWithVoidOption() {
        doDeleteCommandWithVoidOption(false)
    }

    private fun triggerDelete(useCab: Boolean) {
        if (useCab) {
            openCab(R.id.DELETE_COMMAND)
        } else {
            clickContextItem(R.string.menu_delete)
        }
    }

    private fun doDeleteCommandWithVoidOption(useCab: Boolean) {
        assertListSize(origListSize)
        triggerDelete(useCab)
        onView(withId(R.id.checkBox)).perform(click())
        onView(withText(R.string.menu_delete)).perform(click())
        val voidStatus = getString(R.string.status_void)
        composeTestRule.onNodeWithTag(TEST_TAG_LIST).onChildren().onFirst()
            .assertContentDescriptionEquals(voidStatus)
        assertListSize(origListSize)
        clickContextItem(R.string.menu_undelete_transaction)
        composeTestRule.onNodeWithTag(TEST_TAG_LIST).onChildren().onFirst()
            .assert(hasContentDescription(voidStatus).not())
        assertListSize(origListSize)
    }

    @Test
    fun splitCommandCreatesSplitTransaction() {
        openCab(R.id.SPLIT_TRANSACTION_COMMAND)
        handleContribDialog(ContribFeature.SPLIT_TRANSACTION)
        onView(withText(R.string.menu_split_transaction))
            .perform(click())
        composeTestRule.onNodeWithTag(TEST_TAG_LIST).onChildren().onFirst().assertTextContains(getString(R.string.split_transaction))
    }

    @Test
    fun cabIsRestoredAfterOrientationChange() {
        openCab(null)
        rotate()
        onView(withId(R.id.action_mode_bar)).check(matches(isDisplayed()))
    }

    @Test
    fun contextForSealedAccount() {
        testScenario.onActivity {
            it.viewModel.setSealed(account.id, true)
        }
        openCab(null)
        onView(withId(R.id.action_mode_bar)).check(doesNotExist())
        //context menu should only have the details entry
        composeTestRule.onNodeWithTag(TEST_TAG_CONTEXT_MENU).assert(hasChildCount(1))
    }
}