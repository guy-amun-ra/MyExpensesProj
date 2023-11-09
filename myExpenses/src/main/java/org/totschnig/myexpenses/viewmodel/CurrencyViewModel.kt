package org.totschnig.myexpenses.viewmodel

import android.app.Application
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.model.CurrencyEnum
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_CODE
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.viewmodel.data.Currency
import java.text.Collator

open class CurrencyViewModel(application: Application) :
    ContentResolvingAndroidViewModel(application) {

    val currencies: Flow<List<Currency>>
        get() = contentResolver.observeQuery(
            TransactionProvider.CURRENCIES_URI, null, null, null,
            KEY_CODE, true
        )
            .mapToList(dispatcher = coroutineDispatcher) { Currency.create(it, userPreferredLocale) }
            .map { it.sorted() }

    val currenciesFromEnum: List<Currency>
        get() = CurrencyEnum.values()
            .map { Currency.create(it.name, userPreferredLocale) }
            .sorted()

    private fun List<Currency>.sorted(): List<Currency> = try {
        Collator.getInstance()
    } catch (e: Exception) {
        CrashHandler.report(e)
        null
    }?.let { collator ->
        this.sortedWith { lhs, rhs ->
            rhs.usages.compareTo(lhs.usages).takeIf { it != 0 }
                ?: lhs.sortClass.compareTo(rhs.sortClass).takeIf { it != 0 }
                ?: collator.compare(lhs.toString(), rhs.toString())
        }
    } ?: this

    val default: Currency
        get() = Currency.create(
            homeCurrencyProvider.homeCurrencyUnit.code,
            userPreferredLocale
        )
}
