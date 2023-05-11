package org.totschnig.myexpenses.compose

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.semantics

const val TEST_TAG_PAGER = "PAGER"
const val TEST_TAG_LIST = "LIST"
const val TEST_TAG_ROW = "ROW"
const val TEST_TAG_HEADER = "HEADER"
const val TEST_TAG_ACCOUNTS = "ACCOUNTS"
const val TEST_TAG_EDIT_TEXT = "EDIT_TEXT"
const val TEST_TAG_POSITIVE_BUTTON = "POSITIVE_BUTTON"
const val TEST_TAG_SELECT_DIALOG = "SELECT_DIALOG"
const val TEST_TAG_CONTEXT_MENU =  "CONTEXT_MENU"
const val TEST_TAG_BUDGET_BUDGET = "BUDGET_BUDGET"
const val TEST_TAG_BUDGET_ALLOCATION = "BUDGET_ALLOCATION"
const val TEST_TAG_BUDGET_SPENT = "BUDGET_SPENT"

val amountProperty = SemanticsPropertyKey<Long>("amount")

fun Modifier.amountSemantics(amount: Long) = semantics { set(amountProperty, amount) }