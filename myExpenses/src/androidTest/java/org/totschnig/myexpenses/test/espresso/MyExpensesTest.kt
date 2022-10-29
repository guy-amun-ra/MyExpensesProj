package org.totschnig.myexpenses.test.espresso

import android.content.OperationApplicationException
import android.os.RemoteException
import android.widget.Button
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.contrib.DrawerActions
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.matcher.IntentMatchers
import androidx.test.espresso.matcher.ViewMatchers
import org.hamcrest.CoreMatchers
import org.hamcrest.Matchers
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.AccountEdit
import org.totschnig.myexpenses.activity.ExpenseEdit
import org.totschnig.myexpenses.activity.ManageTemplates
import org.totschnig.myexpenses.activity.MyPreferenceActivity
import org.totschnig.myexpenses.compose.TEST_TAG_ACCOUNTS
import org.totschnig.myexpenses.compose.TEST_TAG_LIST
import org.totschnig.myexpenses.compose.TEST_TAG_PAGER
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.testutils.BaseMyExpensesTest
import org.totschnig.myexpenses.testutils.Espresso.openActionBarOverflowMenu
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.formatMoney

class MyExpensesTest : BaseMyExpensesTest() {
    lateinit var account: Account
    @Before
    fun fixture() {
        account = Account(
            "Test account 1", Utils.getHomeCurrency(), 0, "",
            AccountType.CASH, Account.DEFAULT_COLOR
        )
        account.save()
        launch(account.id)
        Intents.init()
    }

    @After
    override fun tearDown() {
        Intents.release()
    }

    private fun assertDataSize(size: Int) {
        composeTestRule.onNodeWithTag(TEST_TAG_PAGER).assert(hasColumnCount(size))
    }

    @Test
    fun viewPagerIsSetup() {
        composeTestRule.onNodeWithText(getString(R.string.no_expenses)).assertIsDisplayed()
        assertDataSize(1)
    }

    @Test
    fun floatingActionButtonOpensForm() {
        Espresso.onView(ViewMatchers.withId(R.id.CREATE_COMMAND)).perform(ViewActions.click())
        Intents.intended(
            IntentMatchers.hasComponent(
                ExpenseEdit::class.java.name
            )
        )
    }

    @Test
    fun helpDialogIsOpened() {
        openActionBarOverflowMenu()
        Espresso.onData(Matchers.hasToString(getString(R.string.menu_help)))
            .perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(Matchers.containsString(getString(R.string.help_MyExpenses_title))))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf(
                ViewMatchers.isAssignableFrom(Button::class.java),
                ViewMatchers.withText(Matchers.`is`(app.getString(android.R.string.ok)))
            )
        )
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }

    @Test
    fun settingsScreenIsOpened() {
        openActionBarOverflowMenu()
        Espresso.onData(Matchers.hasToString(getString(R.string.menu_settings)))
            .perform(ViewActions.click())
        Intents.intended(
            IntentMatchers.hasComponent(
                MyPreferenceActivity::class.java.name
            )
        )
    }

    @Test
    fun inActiveItemsOpenDialog() {
        testInActiveItemHelper(
            R.id.RESET_COMMAND,
            R.string.dialog_command_disabled_reset_account
        )
        testInActiveItemHelper(
            R.id.DISTRIBUTION_COMMAND,
            R.string.dialog_command_disabled_distribution
        )
        testInActiveItemHelper(
            R.id.PRINT_COMMAND,
            R.string.dialog_command_disabled_reset_account
        )
    }

    /**
     * Call a menu item and verify that a message is shown in dialog
     */
    private fun testInActiveItemHelper(menuItemId: Int, messageResId: Int) {
        clickMenuItem(menuItemId)
        Espresso.onView(ViewMatchers.withText(messageResId))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf(
                ViewMatchers.isAssignableFrom(Button::class.java),
                ViewMatchers.withText(Matchers.`is`(getString(android.R.string.ok)))
            )
        ).perform(ViewActions.click())
    }

    @Test
    fun newAccountFormIsOpened() {
        openDrawer()
        Espresso.onView(ViewMatchers.withId(R.id.expansionTrigger)).perform(ViewActions.click())
        Espresso.onView(ViewMatchers.withText(R.string.menu_create_account))
            .perform(ViewActions.click())
        Intents.intended(
            Matchers.allOf(
                IntentMatchers.hasComponent(
                    AccountEdit::class.java.name
                ),
                Matchers.not(IntentMatchers.hasExtraWithKey(DatabaseConstants.KEY_ROWID))
            )
        )
    }

    private fun openDrawer() {
        try {
            Espresso.onView(ViewMatchers.withId(R.id.drawer)).perform(DrawerActions.open())
        } catch (e: NoMatchingViewException) { /*drawerLess layout*/
        }
    }

    private fun clickContextItem(@StringRes resId: Int, position: Int = 1) {
        clickContextItem(resId, composeTestRule.onNodeWithTag(TEST_TAG_ACCOUNTS), position)
    }

    @Test
    fun editAccountFormIsOpened() {
        openDrawer()
        clickContextItem(R.string.menu_edit)
        Intents.intended(
            Matchers.allOf(
                IntentMatchers.hasComponent(
                    AccountEdit::class.java.name
                ), IntentMatchers.hasExtraWithKey(DatabaseConstants.KEY_ROWID)
            )
        )
    }

    @Test
    @Throws(InterruptedException::class)
    fun deleteConfirmationDialogDeleteButtonDeletes() {
        openDrawer()
        clickContextItem(R.string.menu_delete)
        Espresso.onView(ViewMatchers.withText(dialogTitleWarningDeleteAccount))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf(
                ViewMatchers.isAssignableFrom(Button::class.java),
                ViewMatchers.withText(Matchers.`is`(getString(R.string.menu_delete)))
            )
        ).perform(ViewActions.click())
        assertDataSize(0)
    }

    private val dialogTitleWarningDeleteAccount: String
        get() = getQuantityString(R.plurals.dialog_title_warning_delete_account, 1)

    @Test
    fun deleteConfirmationDialogCancelButtonCancels() {
        openDrawer()
        clickContextItem(R.string.menu_delete)
        Espresso.onView(ViewMatchers.withText(dialogTitleWarningDeleteAccount))
            .check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf(
                ViewMatchers.isAssignableFrom(Button::class.java),
                ViewMatchers.withText(Matchers.`is`(getString(android.R.string.cancel)))
            )
        ).perform(ViewActions.click())
        assertDataSize(1)
    }

    @Test
    @Throws(RemoteException::class, OperationApplicationException::class)
    fun deleteConfirmationDialogShowsLabelOfAccountToBeDeleted() {
        val label1 = "Konto A"
        val label2 = "Konto B"
        val account1 = Account(label1, 0, "")
        account1.save()
        val account2 = Account(label2, 0, "")
        account2.save()

        //we try to delete account 1
        openDrawer()
        clickContextItem(R.string.menu_delete)
        Thread.sleep(5000)
        Espresso.onView(
            ViewMatchers.withSubstring(
                getString(
                    R.string.warning_delete_account,
                    label1
                )
            )
        ).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf(
                ViewMatchers.isAssignableFrom(Button::class.java),
                ViewMatchers.withText(android.R.string.cancel)
            )
        ).perform(ViewActions.click())

        //we try to delete account 2
        openDrawer()
        clickContextItem(R.string.menu_delete, 2)
        Espresso.onView(
            ViewMatchers.withSubstring(
                getString(
                    R.string.warning_delete_account,
                    label2
                )
            )
        ).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf(
                ViewMatchers.isAssignableFrom(Button::class.java),
                ViewMatchers.withText(android.R.string.cancel)
            )
        ).perform(ViewActions.click())
    }

    @Test
    fun templateScreenIsOpened() {
        clickMenuItem(R.id.MANAGE_TEMPLATES_COMMAND)
        Intents.intended(
            IntentMatchers.hasComponent(
                ManageTemplates::class.java.name
            )
        )
    }

    @Test
    fun titleAndSubtitleAreSetAndSurviveOrientationChange() {
        checkTitle()
        rotate()
        checkTitle()
    }

    private fun checkTitle() {
        val currencyFormatter = app.appComponent.currencyFormatter()
        val balance = currencyFormatter.formatMoney(Money(account.currencyUnit, 0))
        Espresso.onView(
            Matchers.allOf(
                CoreMatchers.instanceOf(
                    TextView::class.java
                ),
                ViewMatchers.withParent(ViewMatchers.withId(R.id.toolbar)),
                ViewMatchers.withText("Test account 1")
            )
        ).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
        Espresso.onView(
            Matchers.allOf(
                CoreMatchers.instanceOf(
                    TextView::class.java
                ),
                ViewMatchers.withParent(ViewMatchers.withId(R.id.toolbar)),
                ViewMatchers.withText(balance)
            )
        ).check(ViewAssertions.matches(ViewMatchers.isDisplayed()))
    }
}