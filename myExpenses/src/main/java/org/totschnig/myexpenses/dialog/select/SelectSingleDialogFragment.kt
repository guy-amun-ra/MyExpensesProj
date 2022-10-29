package org.totschnig.myexpenses.dialog.select

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.widget.AbsListView
import android.widget.AdapterView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.setFragmentResult
import org.totschnig.myexpenses.provider.DatabaseConstants

abstract class SelectSingleDialogFragment : SelectFromTableDialogFragment(false) {
    override fun onClick(dialog: DialogInterface, which: Int) {
        if (activity == null || which != AlertDialog.BUTTON_POSITIVE) {
            return
        }
        buildExtras()?.let {
            setFragmentResult(requireArguments().getString(KEY_REQUEST_KEY)!!, it)
        }
        dismiss()
    }

    override val dialogTitle: Int
        get() = requireArguments().getInt(KEY_DIALOG_TITLE)

    private fun buildExtras(): Bundle? {
        val listView = (dialog as AlertDialog).listView
        return listView.checkedItemPosition.takeIf { it != AdapterView.INVALID_POSITION }?.let {
            val item = adapter.getItem(it) as DataHolder
            Bundle().apply {
                putString(DatabaseConstants.KEY_LABEL, item.label)
                putLong(DatabaseConstants.KEY_ROWID, listView.checkedItemIds[0])
            }
        }
    }

    override val choiceMode: Int = AbsListView.CHOICE_MODE_SINGLE

    companion object {
        private const val KEY_REQUEST_KEY = "requestKey"
        internal fun buildArguments(
            dialogTitle: Int,
            emptyMessage: Int? = null,
            requestKey: String
        ) = Bundle().apply {
            putInt(KEY_DIALOG_TITLE, dialogTitle)
            if (emptyMessage != null) {
                putInt(KEY_EMPTY_MESSAGE, emptyMessage)
            }
            putString(KEY_REQUEST_KEY, requestKey)
        }
    }
}