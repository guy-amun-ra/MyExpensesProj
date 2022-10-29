/*   This file is part of My Expenses.
 *   My Expenses is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   My Expenses is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with My Expenses.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.totschnig.myexpenses.dialog.select

import android.net.Uri
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.provider.TransactionProvider
import org.totschnig.myexpenses.provider.filter.MethodCriterion

class SelectMethodDialogFragment : SelectFromMappedTableDialogFragment<MethodCriterion>(true) {
    override val dialogTitle: Int
        get() = R.string.search_method
    override val uri: Uri
        get() = TransactionProvider.MAPPED_METHODS_URI

    override fun makeCriteria(label: String, vararg ids: Long): MethodCriterion {
        return if (ids.size == 1 && ids[0] == NULL_ITEM_ID) MethodCriterion() else MethodCriterion(
            label,
            *ids
        )
    }

    companion object {
        fun newInstance(rowId: Long): SelectMethodDialogFragment {
            val dialogFragment = SelectMethodDialogFragment()
            dialogFragment.setArguments(rowId)
            return dialogFragment
        }
    }
}