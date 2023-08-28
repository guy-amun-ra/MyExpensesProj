package org.totschnig.myexpenses.viewmodel.data

import android.database.Cursor
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_BIC
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_IBAN
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_DEBTS
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_TEMPLATES
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_MAPPED_TRANSACTIONS
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_SHORT_NAME
import org.totschnig.myexpenses.provider.getInt
import org.totschnig.myexpenses.provider.getLong
import org.totschnig.myexpenses.provider.getString
import org.totschnig.myexpenses.provider.getStringOrNull

data class Party(
    val id: Long,
    val name: String,
    val shortName: String? = null,
    val bic: String? = null,
    val iban: String? = null,
    val mappedTransactions: Boolean = false,
    val mappedTemplates: Boolean = false,
    val mappedDebts: Boolean = false
) {
    override fun toString() = name

    companion object {
        fun fromCursor(cursor: Cursor) = Party(
            cursor.getLong(KEY_ROWID),
            cursor.getString(KEY_PAYEE_NAME),
            cursor.getStringOrNull(KEY_SHORT_NAME),
            cursor.getStringOrNull(KEY_BIC),
            cursor.getStringOrNull(KEY_IBAN),
            cursor.getInt(KEY_MAPPED_TRANSACTIONS) > 0,
            cursor.getInt(KEY_MAPPED_TEMPLATES) > 0,
            cursor.getInt(KEY_MAPPED_DEBTS) > 0
        )
    }
}