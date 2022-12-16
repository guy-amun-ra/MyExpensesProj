package org.totschnig.myexpenses.testutils

import android.annotation.SuppressLint
import android.app.Instrumentation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.test.R as RT
import org.totschnig.myexpenses.contract.TransactionsContract.Transactions
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.model.Account
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.CrStatus
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.model.Money
import org.totschnig.myexpenses.model.Plan
import org.totschnig.myexpenses.model.SplitTransaction
import org.totschnig.myexpenses.model.Template
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.model.Transfer
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.viewmodel.data.Budget
import org.totschnig.myexpenses.viewmodel.data.Tag
import timber.log.Timber
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.ThreadLocalRandom

@SuppressLint("InlinedApi")
class Fixture(inst: Instrumentation) {
    private val testContext: Context
    private val appContext: MyApplication
    private var repository: Repository? = null
    lateinit var account1: Account
        private set
    lateinit var account2: Account
        private set
    lateinit var account3: Account
        private set
    private lateinit var account4: Account
    val syncAccount1: String
        get() = "Drive - " + appContext.getString(R.string.content_description_encrypted)
    val syncAccount2: String
        get() = "Dropbox - " + testContext.getString(RT.string.testData_sync_backend_2_name)
    val syncAccount3: String
        get() = "WebDAV - https://my.private.cloud/webdav/MyExpenses"

    init {
        testContext = inst.context
        appContext = inst.targetContext.applicationContext as MyApplication
    }

    fun setup(withPicture: Boolean, repository: Repository?) {
        this.repository = repository
        val defaultCurrency = Utils.getHomeCurrency()
        val foreignCurrency =
            appContext.appComponent.currencyContext()[if (defaultCurrency.code == "EUR") "GBP" else "EUR"]
        account1 = Account(
            appContext.getString(R.string.testData_account1Label),
            90000,
            testContext.getString(R.string.testData_account1Description)
        )
        account1.grouping = Grouping.WEEK
        account1.syncAccountName = syncAccount1
        account1.save()
        val formatter = DateTimeFormatter.ofPattern("MMMM yyyy")
        account2 = Account(
            testContext.getString(R.string.testData_account2Label),
            foreignCurrency,
            50000,
            formatter.format(LocalDate.now()), AccountType.CASH,
            testContext.resources.getColor(RT.color.material_red)
        )
        account2.syncAccountName = syncAccount2
        account2.save()
        account3 = Account(
            testContext.getString(R.string.testData_account3Label),
            Utils.getHomeCurrency(),
            200000,
            testContext.getString(R.string.testData_account3Description), AccountType.BANK,
            testContext.resources.getColor(RT.color.material_blue)
        )
        account3.grouping = Grouping.DAY
        account3.syncAccountName = syncAccount3
        account3.save()
        account4 = Account(
            testContext.getString(R.string.testData_account3Description),
            foreignCurrency,
            0,
            "",
            AccountType.CCARD,
            testContext.resources.getColor(RT.color.material_cyan)
        )
        account4.save()

        //set up categories
        setUpCategories(appContext)
        //set up transactions
        var offset = System.currentTimeMillis() - 1000
        //are used twice
        val mainCat1 = findCat(
            testContext.getString(RT.string.testData_transaction1MainCat),
            null
        )
        val mainCat2 = findCat(
            testContext.getString(RT.string.testData_transaction2MainCat),
            null
        )
        val mainCat3 = findCat(
            testContext.getString(RT.string.testData_transaction3MainCat),
            null
        )
        val mainCat6 = findCat(
            testContext.getString(RT.string.testData_transaction6MainCat),
            null
        )
        for (i in 0..14) {
            //Transaction 1
            val builder = TransactionBuilder(testContext)
                .accountId(account1.id)
                .amount(defaultCurrency, -random(12000))
                .catId(
                    findCat(
                        RT.string.testData_transaction1SubCat,
                        mainCat1
                    )
                )
                .date(offset - 300000)
            if (withPicture) {
                builder.pictureUri(
                    Uri.fromFile(
                        File(
                            appContext.getExternalFilesDir(null),
                            "screenshot.jpg"
                        )
                    )
                )
            }
            builder.persist()

            //Transaction 2
            TransactionBuilder(testContext)
                .accountId(account1.id)
                .amount(defaultCurrency, -random(2200L))
                .catId(
                    findCat(
                        RT.string.testData_transaction2SubCat,
                        mainCat2
                    )
                )
                .date(offset - 7200000)
                .comment(testContext.getString(R.string.testData_transaction2Comment))
                .persist()

            //Transaction 3 Cleared
            TransactionBuilder(testContext)
                .accountId(account1.id)
                .amount(defaultCurrency, -random(2500L))
                .catId(
                    findCat(
                        RT.string.testData_transaction3SubCat,
                        mainCat3
                    )
                )
                .date(offset - 72230000)
                .crStatus(CrStatus.CLEARED)
                .persist()

            //Transaction 4 Cleared
            TransactionBuilder(testContext)
                .accountId(account1.id)
                .amount(defaultCurrency, -random(5000L))
                .catId(
                    findCat(
                        RT.string.testData_transaction4SubCat,
                        mainCat2
                    )
                )
                .payee(R.string.testData_transaction4Payee)
                .date(offset - 98030000)
                .crStatus(CrStatus.CLEARED)
                .persist()

            //Transaction 5 Reconciled
            TransactionBuilder(testContext)
                .accountId(account1.id)
                .amount(defaultCurrency, -random(10000L))
                .date(offset - 100390000)
                .crStatus(CrStatus.RECONCILED)
                .persist()

            //Transaction 6 Gift Reconciled
            TransactionBuilder(testContext)
                .accountId(account1.id)
                .amount(defaultCurrency, -10000L)
                .catId(mainCat6)
                .date(offset - 210390000)
                .crStatus(CrStatus.RECONCILED)
                .persist()

            //Salary
            TransactionBuilder(testContext)
                .accountId(account3.id)
                .amount(defaultCurrency, 200000)
                .date(offset - 100000)
                .persist()

            //Transfer
            val transfer = Transfer.getNewInstance(account1.id, account3.id)
            transfer.setAmount(Money(defaultCurrency, 25000L))
            transfer.setDate(Date(offset))
            transfer.save()
            offset -= 400000000
        }

        //Second account foreign Currency
        TransactionBuilder(testContext)
            .accountId(account2.id)
            .amount(foreignCurrency, -random(34567))
            .date(offset - 303900000)
            .persist()

        //Transaction 8: Split
        val split: Transaction = SplitTransaction.getNewInstance(account1.id)
        split.amount = Money(defaultCurrency, -8967L)
        split.status  = DatabaseConstants.STATUS_NONE
        split.save(true)
        val label = testContext.getString(R.string.testData_tag_project)
        val tagList = listOf(Tag(saveTag(label), label, 0))
        split.saveTags(tagList)
        TransactionBuilder(testContext)
            .accountId(account1.id).parentId(split.id)
            .amount(defaultCurrency, -4523L)
            .catId(mainCat2)
            .persist()
        TransactionBuilder(testContext)
            .accountId(account1.id).parentId(split.id)
            .amount(defaultCurrency, -4444L)
            .catId(mainCat6)
            .persist()

        // Template
        Truth.assertWithMessage("Unable to create planner").that(
            MyApplication.getInstance().createPlanner(true)
        ).isNotEqualTo(MyApplication.INVALID_CALENDAR_ID)

        //createPlanner sets up a new plan, mPlannerCalendarId is only set in onSharedPreferenceChanged
        //if it is has not been called yet, when we save our plan, saving fails.
        try {
            Thread.sleep(1000)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        }
        val template = Template.getTypedNewInstance(
            Transactions.TYPE_TRANSACTION,
            account3.id,
            false,
            null
        )!!
        template.amount = Money(defaultCurrency, -90000L)
        val templateSubCat =
            testContext.getString(RT.string.testData_templateSubCat)
        template.catId = findCat(
                templateSubCat,
                findCat(
                    testContext.getString(RT.string.testData_templateMainCat),
                    null
                )
            )
        template.title = templateSubCat
        template.payee = testContext.getString(R.string.testData_templatePayee)
        val planUri = Plan(
            LocalDate.now(),
            "FREQ=WEEKLY;COUNT=10;WKST=SU",
            template.title,
            template.compileDescription(appContext)
        )
            .save()
        template.planId = ContentUris.parseId(planUri!!)
        template.save()
            ?: throw RuntimeException("Could not save template")
        val budget = Budget(
            0L,
            account1.id,
            testContext.getString(R.string.testData_account1Description),
            "DESCRIPTION",
            defaultCurrency,
            Grouping.MONTH,
            -1,
            null as LocalDate?,
            null as LocalDate?,
            account1.label,
            true
        )
        val budgetId = ContentUris.parseId(
            appContext.contentResolver.insert(
                TransactionProvider.BUDGETS_URI,
                budget.toContentValues(200000L)
            )!!
        )
        setCategoryBudget(budgetId, mainCat1, 50000)
        setCategoryBudget(budgetId, mainCat2, 40000)
        setCategoryBudget(budgetId, mainCat3, 30000)
        setCategoryBudget(budgetId, mainCat6, 20000)
    }

    private fun setCategoryBudget(budgetId: Long, categoryId: Long, amount: Long) {
        val contentValues = ContentValues(1)
        contentValues.put(DatabaseConstants.KEY_BUDGET, amount)
        val now = LocalDate.now()
        contentValues.put(DatabaseConstants.KEY_YEAR, now.year)
        contentValues.put(DatabaseConstants.KEY_SECOND_GROUP, now.monthValue - 1)
        val budgetUri = ContentUris.withAppendedId(TransactionProvider.BUDGETS_URI, budgetId)
        val result = appContext.contentResolver.update(
            ContentUris.withAppendedId(budgetUri, categoryId),
            contentValues, null, null
        )
        Timber.d("Insert category budget: %d", result)
    }

    private fun findCat(resId: Int, parent: Long): Long {
        return findCat(testContext.getString(resId), parent)
    }

    private fun findCat(label: String, parent: Long?): Long {
        val result = repository!!.findCategory(label, parent)
        if (result == -1L) {
            throw RuntimeException("Could not find category")
        }
        return result
    }

    private fun random(n: Long): Long {
        return ThreadLocalRandom.current().nextLong(n)
    }

    private class TransactionBuilder(private val context: Context) {
        private var accountId: Long = 0
        private var parentId: Long? = null
        private var amount: Money? = null
        private var catId: Long? = null
        private var date: Date? = null
        private var crStatus: CrStatus? = null
        private var pictureUri: Uri? = null
        private var comment: String? = null
        private var payee: String? = null
        fun accountId(accountId: Long): TransactionBuilder {
            this.accountId = accountId
            return this
        }

        fun parentId(parentId: Long): TransactionBuilder {
            this.parentId = parentId
            return this
        }

        fun amount(currency: CurrencyUnit, amountMinor: Long): TransactionBuilder {
            amount = Money(currency, amountMinor)
            return this
        }

        fun catId(catId: Long): TransactionBuilder {
            this.catId = catId
            return this
        }

        fun date(date: Long): TransactionBuilder {
            this.date = Date(date)
            return this
        }

        fun crStatus(crStatus: CrStatus): TransactionBuilder {
            this.crStatus = crStatus
            return this
        }

        fun pictureUri(uri: Uri): TransactionBuilder {
            pictureUri = uri
            return this
        }

        fun payee(resId: Int): TransactionBuilder {
            payee = context.getString(resId)
            return this
        }

        fun comment(comment: String): TransactionBuilder {
            this.comment = comment
            return this
        }

        fun persist(): Transaction {
            val transaction = Transaction.getNewInstance(accountId)
            transaction.amount = amount
            transaction.catId = catId
            if (date != null) {
                transaction.setDate(date)
            }
            if (crStatus != null) {
                transaction.crStatus = crStatus
            }
            transaction.pictureUri = pictureUri
            transaction.payee = payee
            transaction.comment = comment
            transaction.parentId = parentId
            transaction.save()
            return transaction
        }
    }

    private fun saveTag(label: String?): Long {
        val values = ContentValues()
        values.put(DatabaseConstants.KEY_LABEL, label)
        val uri = appContext.contentResolver.insert(TransactionProvider.TAGS_URI, values)
        return ContentUris.parseId(uri!!)
    }

    companion object {
        private fun setUpCategories(appContext: Context) {
            val integerIntegerPair = appContext.contentResolver
                .call(
                    TransactionProvider.DUAL_URI,
                    TransactionProvider.METHOD_SETUP_CATEGORIES,
                    null,
                    null
                )!!.getSerializable(TransactionProvider.KEY_RESULT) as Pair<Int, Int>?
            Timber.d("Set up %d categories", integerIntegerPair!!.first)
        }
    }
}