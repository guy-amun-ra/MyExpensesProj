package org.totschnig.myexpenses.dialog.select

import android.content.DialogInterface
import android.net.Uri
import android.os.Bundle
import android.widget.AbsListView
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Checkbox
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.RadioButton
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.compose.toggle
import org.totschnig.myexpenses.dialog.ComposeBaseDialogFragment
import org.totschnig.myexpenses.viewmodel.LoadState
import org.totschnig.myexpenses.viewmodel.SelectFromTableViewModel

abstract class SelectFromTableDialogFragment(private val withNullItem: Boolean) :
    ComposeBaseDialogFragment(), DialogInterface.OnClickListener {

    val dataViewModel: SelectFromTableViewModel by viewModels()

    protected open val dialogTitle: Int = 0
    abstract val uri: Uri
    abstract val column: String
    protected open val selectionArgs: Array<String>? = null
    protected open val selection: String? = null
    protected open val neutralButton: Int
        get() = 0
    protected open val negativeButton: Int
        get() = android.R.string.cancel
    protected open val positiveButton: Int
        get() = android.R.string.ok
    protected open val choiceMode: Int
        get() = AbsListView.CHOICE_MODE_MULTIPLE

    private val emptyMessage: String
        get() = arguments?.getInt(KEY_EMPTY_MESSAGE).takeIf { it != 0 }?.let {
            getString(it)
        } ?: "No data"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (requireActivity().application as MyApplication).appComponent.inject(dataViewModel)
        if (savedInstanceState == null) {
            dataViewModel.loadData(uri, column, selection, selectionArgs, withNullItem)
        }
    }

    override fun initBuilder(): AlertDialog.Builder = super.initBuilder().apply {
        if (dialogTitle != 0) setTitle(dialogTitle)
        setPositiveButton(positiveButton, null)
        if (neutralButton != 0) setNeutralButton(neutralButton, null)
        if (negativeButton != 0) setNegativeButton(negativeButton, null)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog =
        super.onCreateDialog(savedInstanceState).apply {
            setOnShowListener {
                getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    onClick(this, AlertDialog.BUTTON_POSITIVE)
                }
                val neutral = getButton(AlertDialog.BUTTON_NEUTRAL)
                neutral?.setOnClickListener {
                    onClick(this, AlertDialog.BUTTON_NEUTRAL)
                }
            }
        }

    @Composable
    override fun BuildContent() {
        when (val data = dataViewModel.data.value) {
            LoadState.Loading -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(96.dp)
                            .align(Alignment.Center)
                    )
                }
            }
            is LoadState.Result -> {
                if (data.items.isNotEmpty()) {
                    LazyColumn(modifier = Modifier.dialogPadding()) {
                        items(data.items.size) {
                            val dataHolder = data.items[it]
                            fun toggle() {
                                dataViewModel.selectionState.toggle(dataHolder)
                            }
                            Row(
                                modifier = Modifier
                                    .height(48.dp)
                                    .clickable(onClick = ::toggle),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(modifier = Modifier.weight(1f),
                                    text = dataHolder.label)
                                val selected = dataViewModel.selectionState.value.contains(dataHolder)
                                if (choiceMode == AbsListView.CHOICE_MODE_MULTIPLE) {
                                    Checkbox(checked = selected, onCheckedChange = { toggle() })
                                } else {
                                    RadioButton(selected = selected, onClick = ::toggle)
                                }
                            }
                        }
                    }
                } else {
                    Text(emptyMessage, modifier = Modifier.dialogPadding())
                }
            }
        }
    }

    private fun Modifier.dialogPadding() = padding(start = 24.dp, end = 24.dp, top = 0.dp)

    abstract override fun onClick(dialog: DialogInterface, which: Int)

    companion object {
        const val KEY_DIALOG_TITLE = "dialog_tile"
        const val KEY_EMPTY_MESSAGE = "empty_message"
    }
}