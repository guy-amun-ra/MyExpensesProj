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
package org.totschnig.myexpenses.fragment

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import eltos.simpledialogfragment.SimpleDialog
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener.BUTTON_POSITIVE
import eltos.simpledialogfragment.form.Hint
import eltos.simpledialogfragment.form.SimpleFormDialog
import eltos.simpledialogfragment.form.Spinner
import eltos.simpledialogfragment.input.SimpleInputDialog
import icepick.Icepick
import icepick.State
import org.totschnig.myexpenses.ACTION_MANAGE
import org.totschnig.myexpenses.ACTION_SELECT_FILTER
import org.totschnig.myexpenses.ACTION_SELECT_MAPPING
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.DebtEdit
import org.totschnig.myexpenses.activity.ManageParties
import org.totschnig.myexpenses.adapter.CategoryTreeBaseAdapter
import org.totschnig.myexpenses.adapter.ChoiceCapableAdapter
import org.totschnig.myexpenses.adapter.MultiChoiceMode
import org.totschnig.myexpenses.databinding.PartiesListBinding
import org.totschnig.myexpenses.databinding.PayeeRowBinding
import org.totschnig.myexpenses.dialog.DebtDetailsDialogFragment
import org.totschnig.myexpenses.provider.DatabaseConstants
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PAYEEID
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_PAYEE_NAME
import org.totschnig.myexpenses.provider.DatabaseConstants.KEY_ROWID
import org.totschnig.myexpenses.util.configureSearch
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.prepareSearch
import org.totschnig.myexpenses.viewmodel.PartyListViewModel
import org.totschnig.myexpenses.viewmodel.data.Party
import java.util.*

class PartiesList : Fragment(), OnDialogResultListener {
    val manageParties: ManageParties?
        get() = (activity as? ManageParties)

    inner class ViewHolder(val binding: PayeeRowBinding, private val itemCallback: ItemCallback) :
        RecyclerView.ViewHolder(binding.root),
        View.OnClickListener, CompoundButton.OnCheckedChangeListener {
        init {
            binding.checkBox.setOnCheckedChangeListener(this)
        }

        override fun onClick(view: View) {
            itemCallback.onItemClick(view, bindingAdapterPosition)
        }

        override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
            itemCallback.onCheckedChanged(isChecked, bindingAdapterPosition)
        }

        fun bind(name: String, checked: Boolean, hasDebts: Boolean) {
            binding.Payee.text = name
            with(binding.checkBox) {
                visibility = if (hasSelectMultiple()) View.VISIBLE else View.GONE
                isChecked = checked
            }
            binding.Debt.visibility = if (hasDebts) View.VISIBLE else View.GONE
            binding.root.setOnClickListener(if (hasSelectMultiple()) null else this)
        }
    }

    interface ItemCallback {
        fun onItemClick(view: View, position: Int)
        fun onCheckedChanged(isChecked: Boolean, position: Int)
    }

    inner class PayeeAdapter :
        ChoiceCapableAdapter<Party, ViewHolder>(MultiChoiceMode(), DIFF_CALLBACK), ItemCallback {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(PayeeRowBinding.inflate(LayoutInflater.from(context), parent, false), this)

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            with(getItem(position)) {
                holder.bind(name, isChecked(position), mappedDebts > 0)
            }
        }

        fun getParty(position: Int): Party = getItem(position)

        override fun onItemClick(view: View, position: Int) {
            val index2IdMap: MutableMap<Int, Long> = mutableMapOf()
            with(PopupMenu(requireContext(), view)) {
                if (action == ACTION_SELECT_MAPPING) {
                    menu.add(Menu.NONE, SELECT_COMMAND, Menu.NONE, R.string.select)
                        .setIcon(R.drawable.ic_menu_done)
                }
                menu.add(Menu.NONE, EDIT_COMMAND, Menu.NONE, R.string.menu_edit)
                    .setIcon(R.drawable.ic_menu_edit)
                menu.add(Menu.NONE, DELETE_COMMAND, Menu.NONE, R.string.menu_delete)
                    .setIcon(R.drawable.ic_menu_delete)
                if (action == ACTION_MANAGE) {
                    val subMenu =
                        menu.addSubMenu(Menu.NONE, DEBT_SUB_MENU, Menu.NONE, R.string.debt)
                            .setIcon(R.drawable.balance_scale)
                    viewModel.getDebts(getItem(position).id)?.forEachIndexed { index, debt ->
                        index2IdMap[index] = debt.id
                        val item = subMenu.add(Menu.NONE, index, Menu.NONE, debt.label)
                        if (debt.isSealed) {
                            item.setIcon(R.drawable.ic_lock)
                        }
                    }
                    subMenu.add(Menu.NONE, NEW_DEBT_COMMAND, Menu.NONE, R.string.menu_new_debt)
                        .setIcon(R.drawable.ic_menu_add)
                }

                setOnMenuItemClickListener { item ->
                    val party = getItem(position)
                    when (item.itemId) {
                        EDIT_COMMAND -> {
                            SimpleInputDialog.build()
                                .title(R.string.menu_edit_party)
                                .cancelable(false)
                                .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                                .hint(R.string.full_name)
                                .text(party.name)
                                .pos(R.string.menu_save)
                                .neut()
                                .extra(Bundle().apply {
                                    putLong(KEY_ROWID, party.id)
                                })
                                .show(this@PartiesList, DIALOG_EDIT_PARTY)
                        }
                        DELETE_COMMAND -> {
                            if (party.mappedTransactions || party.mappedTemplates) {
                                var message = ""
                                if (party.mappedTransactions) {
                                    message += resources.getQuantityString(
                                        R.plurals.not_deletable_mapped_transactions, 1, 1
                                    )
                                }
                                if (party.mappedTemplates) {
                                    message += resources.getQuantityString(
                                        R.plurals.not_deletable_mapped_templates, 1, 1
                                    )
                                }
                                manageParties?.showSnackbar(message)
                            } else if (party.mappedDebts > 0) {
                                SimpleDialog.build()
                                    .title(R.string.dialog_title_warning_delete_party)
                                    .extra(Bundle().apply {
                                        putLong(KEY_ROWID, party.id)
                                    })
                                    .msg(
                                        org.totschnig.myexpenses.util.TextUtils.concatResStrings(
                                            context,
                                            " ",
                                            R.string.warning_party_delete_debt,
                                            R.string.continue_confirmation
                                        )
                                    )
                                    .pos(R.string.response_yes)
                                    .neg(R.string.response_no)
                                    .show(this@PartiesList, DIALOG_DELETE_PARTY)
                            } else {
                                doDelete(party.id)
                            }
                        }
                        SELECT_COMMAND -> {
                            doSingleSelection(party)
                        }
                        DEBT_SUB_MENU -> { /*submenu*/
                        }
                        NEW_DEBT_COMMAND -> {
                            startActivity(Intent(context, DebtEdit::class.java).apply {
                                putExtra(KEY_PAYEEID, party.id)
                                putExtra(KEY_PAYEE_NAME, party.name)
                            })
                        }
                        else -> {
                            index2IdMap[item.itemId]?.also {
                                DebtDetailsDialogFragment.newInstance(it).show(
                                    parentFragmentManager, "DEBT_DETAILS")
                            } ?: run {
                                CrashHandler.report(IllegalStateException("debtId not found in map"))
                            }
                        }
                    }
                    true
                }
                //noinspection RestrictedApi
                (menu as? MenuBuilder)?.setOptionalIconsVisible(true)
                show()
            }
        }

        override fun onCheckedChanged(isChecked: Boolean, position: Int) {
            onChecked(position, isChecked)
            manageParties?.setFabEnabled(
                checkedCount >=
                        if (mergeMode) 2 else if (action == ACTION_SELECT_FILTER) 1 else 0
            )

        }
    }

    private fun doDelete(partyId: Long) {
        manageParties?.showSnackbar(R.string.progress_dialog_deleting)
        viewModel.deleteParty(partyId)
            .observe(viewLifecycleOwner) { result ->
                result.onSuccess { count ->
                    manageParties?.let {
                        it.showSnackbar(
                            it.resources.getQuantityString(
                                R.plurals.delete_success,
                                count,
                                count
                            )
                        )
                    }
                }.onFailure {
                    manageParties?.showDeleteFailureFeedback(
                        it.message
                    )
                }
            }
    }

    lateinit var adapter: PayeeAdapter
    lateinit var viewModel: PartyListViewModel
    private var _binding: PartiesListBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    @State
    @JvmField
    var filter: String? = null

    @State
    @JvmField
    var mergeMode: Boolean = false


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        viewModel = ViewModelProvider(this).get(PartyListViewModel::class.java)
        (requireActivity().application as MyApplication).appComponent.inject(viewModel)
        viewModel.loadDebts()
        Icepick.restoreInstanceState(this, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (mergeMode) {
            updateUiMergeMode()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        Icepick.saveInstanceState(this, outState)
        adapter.onSaveInstanceState(outState)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        if (activity == null) return
        inflater.inflate(R.menu.search, menu)
        if (action == ACTION_MANAGE) {
            menu.add(Menu.NONE, R.id.MERGE_COMMAND, 0, R.string.menu_merge)
                .setIcon(R.drawable.ic_menu_split_transaction)
                .setCheckable(true)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        }
        configureSearch(requireActivity(), menu) { newText: String? -> onQueryTextChange(newText) }
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (item.itemId == R.id.MERGE_COMMAND) {
            mergeMode = !mergeMode
            updateUiMergeMode()
            resetAdapter()
            true
        } else
            super.onOptionsItemSelected(item)

    private fun updateUiMergeMode() {
        with(manageParties!!) {
            invalidateOptionsMenu()
            configureFabMergeMode(mergeMode)
            setFabEnabled(!mergeMode)
        }
    }

    private fun resetAdapter() {
        adapter.clearChecks()
        //noinspection NotifyDataSetChanged
        adapter.notifyDataSetChanged()
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        menu.findItem(R.id.MERGE_COMMAND)?.let {
            it.isVisible = adapter.itemCount >= 2
            it.isChecked = mergeMode
        }
        prepareSearch(menu, filter)
    }

    private fun onQueryTextChange(newText: String?): Boolean {
        filter = if (TextUtils.isEmpty(newText)) "" else newText
        loadParties()
        return true
    }

    private fun loadParties() {
        viewModel.loadParties(
            filter,
            requireActivity().intent.getLongExtra(DatabaseConstants.KEY_ACCOUNTID, 0)
        )
    }

    private val action: String
        get() = manageParties!!.action

    private fun doSingleSelection(party: Party) {
        requireActivity().apply {
            setResult(Activity.RESULT_OK, Intent().apply {
                putExtra(KEY_PAYEEID, party.id)
                putExtra(DatabaseConstants.KEY_LABEL, party.name)
            })
            finish()
        }
    }

    @SuppressLint("InlinedApi")
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = PartiesListBinding.inflate(inflater, container, false)
        adapter = PayeeAdapter()
        savedInstanceState?.let { adapter.onRestoreInstanceState(it) }
        binding.list.adapter = adapter
        viewModel.getParties().observe(viewLifecycleOwner, { parties: List<Party> ->
            if (parties.size < 2 && mergeMode) {
                mergeMode = false
                updateUiMergeMode()
            }
            binding.empty.visibility = if (parties.isEmpty()) View.VISIBLE else View.GONE
            binding.list.visibility = if (parties.isEmpty()) View.GONE else View.VISIBLE
            adapter.submitList(
                if (action == ACTION_SELECT_FILTER)
                    listOf(
                        Party(
                            CategoryTreeBaseAdapter.NULL_ITEM_ID, getString(R.string.unmapped),
                            mappedTransactions = false, mappedTemplates = false, mappedDebts = 0
                        )
                    ).plus(parties)
                else
                    parties
            )
            activity?.invalidateOptionsMenu()
        })
        loadParties()
        return binding.root
    }

    private fun hasSelectMultiple(): Boolean {
        return action == ACTION_SELECT_FILTER || mergeMode
    }

    companion object {
        const val DIALOG_NEW_PARTY = "dialogNewParty"
        const val DIALOG_EDIT_PARTY = "dialogEditParty"
        const val DIALOG_MERGE_PARTY = "dialogMergeParty"
        const val DIALOG_DELETE_PARTY = "dialogDeleteParty"
        const val KEY_POSITION = "position"
        const val SELECT_COMMAND = -1
        const val EDIT_COMMAND = -2
        const val DELETE_COMMAND = -3
        const val NEW_DEBT_COMMAND = -4
        const val DEBT_SUB_MENU = -5

        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Party>() {
            override fun areItemsTheSame(oldItem: Party, newItem: Party): Boolean {
                return oldItem.id == newItem.id
            }

            override fun areContentsTheSame(oldItem: Party, newItem: Party): Boolean {
                return oldItem == newItem
            }
        }
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (which == BUTTON_POSITIVE) {
            when (dialogTag) {
                DIALOG_NEW_PARTY, DIALOG_EDIT_PARTY -> {
                    val name = extras.getString(SimpleInputDialog.TEXT)!!
                    viewModel.saveParty(
                        extras.getLong(KEY_ROWID),
                        name
                    ).observe(this) {
                        if (it == null)
                            manageParties?.showSnackbar(
                                getString(
                                    R.string.already_defined,
                                    name
                                )
                            )
                    }
                    return true
                }
                DIALOG_MERGE_PARTY -> {
                    mergeMode = false
                    updateUiMergeMode()
                    val selectedItemIds = adapter.checkedPositions.map { adapter.getParty(it).id }
                    viewModel.mergeParties(
                        selectedItemIds.toLongArray(),
                        selectedItemIds[extras.getInt(KEY_POSITION)]
                    )
                    resetAdapter()
                    return true
                }
                DIALOG_DELETE_PARTY -> {
                    doDelete(extras.getLong(KEY_ROWID))
                }
            }
        }
        return false
    }

    fun dispatchFabClick() {
        if (action == ACTION_SELECT_FILTER) {
            val selected = adapter.checkedPositions.map { adapter.getParty(it) }
            val itemIds = selected.map { it.id }
            val labels = selected.map { it.name }
            if (itemIds.size != 1 && itemIds.contains(CategoryTreeBaseAdapter.NULL_ITEM_ID)) {
                manageParties?.showSnackbar(R.string.unmapped_filter_only_single)
            } else {
                requireActivity().apply {
                    setResult(Activity.RESULT_FIRST_USER, Intent().apply {
                        putExtra(KEY_PAYEEID, itemIds.toLongArray())
                        putExtra(DatabaseConstants.KEY_LABEL, labels.joinToString(separator = ","))
                    })
                    finish()
                }
            }
        } else if (mergeMode) {
            val selected = adapter.checkedPositions.map { adapter.getParty(it).name }.toTypedArray()
            SimpleFormDialog.build()
                .fields(
                    Hint.plain(R.string.merge_parties_select),
                    Spinner.plain(KEY_POSITION).items(*selected).required().preset(0)
                )
                .autofocus(false)
                .show(this, DIALOG_MERGE_PARTY)
        } else {
            SimpleInputDialog.build()
                .title(R.string.menu_create_party)
                .cancelable(false)
                .inputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
                .hint(R.string.full_name)
                .pos(R.string.dialog_button_add)
                .neut()
                .show(this, DIALOG_NEW_PARTY)
        }
    }
}