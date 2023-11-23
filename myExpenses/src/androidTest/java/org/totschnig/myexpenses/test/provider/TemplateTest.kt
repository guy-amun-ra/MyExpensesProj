package org.totschnig.myexpenses.test.provider

import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PARENTID
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.insert
import org.totschnig.shared_test.CursorSubject

class TemplateTest : BaseTemplateTest() {

    private fun insertSplitTemplate() = mDb.insert(
        DatabaseConstants.TABLE_TEMPLATES,
        TemplateInfo(testAccountId, "Template daily", 100).contentValues
    ).also {
        mDb.insert(
            DatabaseConstants.TABLE_TEMPLATES,
            TemplateInfo(testAccountId, "", 100, parentId = it).contentValues
        )
    }


    fun testTemplateQueryShouldReturnMainTemplates() {
        insertData()
        insertSplitTemplate()
        mockContentResolver.query(
            TransactionProvider.TEMPLATES_URI,
            null,
            KEY_PARENTID + " is null",
            null,
            null
        )!!.use {
            CursorSubject.assertThat(it).hasCount(4)
        }
    }

    fun testTemplateQueryShouldReturnSplitParts() {
        val parent = insertSplitTemplate()
        mockContentResolver.query(
            TransactionProvider.TEMPLATES_URI.buildUpon()
                .appendQueryParameter(KEY_PARENTID, parent.toString()).build(),
            null,
            null,
            arrayOf(),
            null
        )!!.use {
            CursorSubject.assertThat(it).hasCount(1)
        }
    }
}