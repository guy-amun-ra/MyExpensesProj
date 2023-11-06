package org.totschnig.myexpenses.dialog

import android.os.Bundle
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModelProvider
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.ButtonRow
import org.totschnig.myexpenses.compose.Category
import org.totschnig.myexpenses.compose.ChoiceMode
import org.totschnig.myexpenses.compose.ExpansionMode
import org.totschnig.myexpenses.compose.rememberMutableStateListOf
import org.totschnig.myexpenses.viewmodel.CategoryViewModel
import org.totschnig.myexpenses.viewmodel.LoadingState
import org.totschnig.myexpenses.viewmodel.data.Category

class SelectCategoryMoveTargetDialogFragment : ComposeBaseDialogFragment() {
    lateinit var viewModel: CategoryViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(requireActivity())[CategoryViewModel::class.java]
    }

    @Composable
    override fun BuildContent() {
        val source = requireArguments().getParcelable<Category>(KEY_SOURCE)!!
        Column(modifier = Modifier.fillMaxSize()) {
            val selectionState: MutableState<Category?> = rememberSaveable {
                mutableStateOf(null)
            }

            Text(
                modifier = Modifier.padding(top = dialogPadding, start = dialogPadding),
                style = MaterialTheme.typography.titleLarge,
                text = stringResource(id = R.string.dialog_title_select_target)
            )

            val state = viewModel.categoryTreeForSelect.collectAsState(initial = LoadingState.Loading)

            (state.value as? LoadingState.Data)?.let {
                Category(
                    modifier = Modifier.weight(1f),
                    category = it.data.copy(
                        label = stringResource(id = R.string.transform_subcategory_to_main)
                    ),
                    expansionMode = ExpansionMode.DefaultExpanded(rememberMutableStateListOf()),
                    choiceMode = ChoiceMode.SingleChoiceMode(selectionState) {
                        it != source.parentId
                    },
                    excludedSubTree = source.id,
                    withRoot = source.parentId != null
                )
            }

            ButtonRow(modifier = Modifier.padding(bottom = dialogPadding, end = dialogPadding)) {
                Button(onClick = { dismiss() }) {
                    Text(stringResource(id = android.R.string.cancel))
                }
                Button(
                    onClick = {
                        viewModel.moveCategory(
                            source.id,
                            selectionState.value!!.id.takeIf { it != 0L })
                        dismiss()
                    },
                    enabled = selectionState.value != null
                ) {
                    val selection = selectionState.value
                    Text(
                        if (selection?.id == 0L)
                            selection.label
                        else
                            "Move ${source.label} to ${selection?.label ?: "?"}"
                    )
                }
            }
        }
    }

    companion object {
        const val KEY_SOURCE = "source"
        fun newInstance(category: Category) = SelectCategoryMoveTargetDialogFragment().apply {
            arguments = Bundle().apply {
                putParcelable(KEY_SOURCE, category)
            }
        }
    }
}