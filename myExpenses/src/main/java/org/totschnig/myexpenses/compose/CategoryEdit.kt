package org.totschnig.myexpenses.compose

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.viewmodel.CategoryViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CategoryEdit(
    dialogState: CategoryViewModel.Show,
    onDismissRequest: () -> Unit = {},
    onSave: (String, String?) -> Unit = { _, _ -> }
) {
    val titleBottomPadding = 12.dp
    val fieldPadding = 12.dp
    val buttonRowTopPadding = 12.dp
    val context = LocalContext.current
    var label by rememberSaveable { mutableStateOf(dialogState.label ?: "") }
    var icon by rememberSaveable { mutableStateOf(dialogState.icon) }
    var shouldValidate by remember { mutableStateOf(false) }
    var showIconSelection by rememberSaveable { mutableStateOf(false) }

    val isError = if (shouldValidate) {
        when {
            dialogState.error -> context.getString(R.string.already_defined, label)
            label.isEmpty() -> context.getString(R.string.required)
            else -> null
        }
    } else null

    Dialog(
        onDismissRequest = { }
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colors.background,
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
            ) {
                Text(
                    modifier = Modifier.padding(bottom = titleBottomPadding),
                    text = stringResource(
                        if (dialogState.id == null)
                            if (dialogState.parentId == null) R.string.menu_create_main_cat
                            else R.string.menu_create_sub_cat
                        else R.string.menu_edit_cat
                    ),
                    style = MaterialTheme.typography.subtitle1
                )
                OutlinedTextField(
                    modifier = Modifier.testTag(TEST_TAG_EDIT_TEXT),
                    label = { Text(stringResource(id = R.string.label)) },
                    value = label,
                    isError = isError != null,
                    onValueChange = { shouldValidate = false; label = it })
                Text(
                    text = isError ?: "",
                    color = MaterialTheme.colors.error,
                    style = MaterialTheme.typography.caption,
                    modifier = Modifier.padding(start = 16.dp, top = 0.dp)
                )

                Spacer(modifier = Modifier.height(fieldPadding))

                Text(stringResource(id = R.string.icon))
                Button(onClick = { showIconSelection = true }) {
                    icon?.let {
                        Icon(it)
                    } ?: Text(stringResource(id = R.string.select))
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = buttonRowTopPadding),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        enabled = !dialogState.saving,
                        onClick = {
                            onDismissRequest()
                        }) {
                        Text(stringResource(id = android.R.string.cancel))
                    }
                    TextButton(
                        modifier = Modifier.testTag(TEST_TAG_POSITIVE_BUTTON),
                        enabled = !dialogState.saving && isError == null,
                        onClick = {
                            shouldValidate = true
                            if (label.isNotEmpty()) {
                                onSave(label, icon)
                            }
                        }) {
                        Text(
                            stringResource(
                                id = if (dialogState.id == 0L) R.string.dialog_button_add
                                else R.string.menu_save
                            )
                        )
                    }
                }
            }
        }
    }
    if (showIconSelection) {
        Dialog(
            onDismissRequest = { },
            properties = DialogProperties(usePlatformDefaultWidth = true)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colors.background,
            ) {
                Column {
                    IconSelector(
                        modifier = Modifier.weight(1f),
                        onIconSelected = {
                            icon = it.key
                            showIconSelection = false
                        }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = buttonRowTopPadding),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(
                            onClick = {
                                showIconSelection = false
                            }) {
                            Text(stringResource(id = android.R.string.cancel))
                        }
                        if (icon != null) {
                            TextButton(
                                onClick = {
                                    icon = null
                                    showIconSelection = false
                                }) {
                                Text(stringResource(id = R.string.menu_remove))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Preview
@Composable
fun PreviewDialog() {
    CategoryEdit(dialogState = CategoryViewModel.Show(0L))
}