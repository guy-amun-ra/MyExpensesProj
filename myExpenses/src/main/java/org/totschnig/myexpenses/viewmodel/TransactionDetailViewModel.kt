package org.totschnig.myexpenses.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import app.cash.copper.flow.mapToList
import app.cash.copper.flow.observeQuery
import org.totschnig.myexpenses.model.Transaction
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID
import org.totschnig.myexpenses.viewmodel.data.Tag
import org.totschnig.myexpenses.viewmodel.data.Transaction.Companion.fromCursor
import org.totschnig.myexpenses.viewmodel.data.Transaction.Companion.projection
import org.totschnig.myexpenses.viewmodel.data.Transaction as TData

class TransactionDetailViewModel(application: Application) :
    ContentResolvingAndroidViewModel(application) {

    private val transactionLiveData: Map<Long, LiveData<List<TData>>> = lazyMap { transactionId ->
        contentResolver.observeQuery(
            Transaction.EXTENDED_URI,
            projection(application),
            "$KEY_ROWID = ? OR $KEY_PARENTID = ?",
            Array(2) { transactionId.toString() },
            "$KEY_PARENTID IS NULL DESC",
            false
        ).mapToList { fromCursor(getApplication(), it, currencyContext) }.asLiveData(coroutineContext())
    }

    fun transaction(transactionId: Long): LiveData<List<TData>> =
        transactionLiveData.getValue(transactionId)

    fun loadOriginalTags(id: Long, uri: Uri, column: String) =
        contentResolver.observeQuery(uri, null, "$column = ?", arrayOf(id.toString()), null, false)
            .mapToList { cursor ->
                Tag(cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ROWID)), cursor.getString(cursor.getColumnIndexOrThrow(
                    DatabaseConstants.KEY_LABEL)))
            }
}