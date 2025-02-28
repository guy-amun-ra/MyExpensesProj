package org.totschnig.myexpenses.activity

import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.PluralsRes
import androidx.appcompat.view.ActionMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.compose.*
import org.totschnig.myexpenses.compose.MenuEntry.Companion.delete
import org.totschnig.myexpenses.compose.MenuEntry.Companion.edit
import org.totschnig.myexpenses.compose.MenuEntry.Companion.select
import org.totschnig.myexpenses.databinding.ActivityComposeFabBinding
import org.totschnig.myexpenses.dialog.MessageDialogFragment
import org.totschnig.myexpenses.dialog.SelectCategoryMoveTargetDialogFragment
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.Sort
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.provider.DatabaseConstants.*
import org.totschnig.myexpenses.provider.filter.NULL_ITEM_ID
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.util.*
import org.totschnig.myexpenses.viewmodel.CategoryViewModel
import org.totschnig.myexpenses.viewmodel.CategoryViewModel.DeleteResult.OperationComplete
import org.totschnig.myexpenses.viewmodel.CategoryViewModel.DeleteResult.OperationPending
import org.totschnig.myexpenses.viewmodel.LoadingState
import org.totschnig.myexpenses.viewmodel.data.Category
import java.io.Serializable

class ManageCategories : ProtectedFragmentActivity(),
    ContribIFace {

    private var actionMode: ActionMode? = null
    private val viewModel: CategoryViewModel by viewModels()
    private lateinit var binding: ActivityComposeFabBinding
    private lateinit var sortDelegate: SortDelegate
    private lateinit var choiceMode: ChoiceMode
    private val parentSelectionOnTap: MutableState<Boolean> = mutableStateOf(false)

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val action = intent.asAction
        if (action != Action.SELECT_FILTER) {
            menuInflater.inflate(R.menu.categories, menu)
            val exportMenu = menu.findItem(R.id.EXPORT_COMMAND)
            exportMenu.setEnabledAndVisible(action == Action.MANAGE)
            exportMenu.title = getString(R.string.export_to_format, "QIF")
            menu.findItem(R.id.TOGGLE_PARENT_CATEGORY_SELECTION_ON_TAP).setEnabledAndVisible(
                action == Action.SELECT_MAPPING
            )
            menu.findItem(R.id.TYPE_FILTER_COMMAND).isChecked = viewModel.typeFilter != null
        }
        menuInflater.inflate(R.menu.search, menu)
        configureSearch(this, menu, this::onQueryTextChange)
        super.onCreateOptionsMenu(menu)
        return true
    }

    private fun onQueryTextChange(newText: String): Boolean {
        viewModel.filter = newText
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        sortDelegate.onPrepareOptionsMenu(menu)
        if (intent.asAction != Action.SELECT_FILTER) {
            menu.findItem(R.id.TOGGLE_PARENT_CATEGORY_SELECTION_ON_TAP)?.let {
                it.isChecked = parentSelectionOnTap.value
            }
            checkMenuIcon(menu.findItem(R.id.TYPE_FILTER_COMMAND))
        }
        prepareSearch(menu, viewModel.filter)
        val accountNames = GenericAccountService.getAccountNames(this)
        menu.findItem(R.id.SYNC_COMMAND)?.let { item ->
            item.setEnabledAndVisible(accountNames.isNotEmpty())
            item.subMenu?.let { subMenu1 ->
                fun populateMenu(command: Int) {
                    subMenu1.findItem(command)?.subMenu?.let {
                        it.clear()
                        for (account in accountNames) {
                            it.add(command, Menu.NONE, Menu.NONE, account)
                        }
                    }
                }
                populateMenu(R.id.SYNC_COMMAND_EXPORT_CATEGORIES)
                populateMenu(R.id.SYNC_COMMAND_IMPORT_CATEGORIES)
            }
        }
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) =
        if (sortDelegate.onOptionsItemSelected(item)) {
            invalidateOptionsMenu()
            viewModel.setSortOrder(sortDelegate.currentSortOrder)
            true
        } else if (item.itemId == Menu.NONE) {
            when (item.groupId) {
                R.id.SYNC_COMMAND_EXPORT_CATEGORIES -> viewModel.syncCatsExport(item.title.toString())
                R.id.SYNC_COMMAND_IMPORT_CATEGORIES -> viewModel.syncCatsImport(item.title.toString())
            }
            true
        } else super.onOptionsItemSelected(item)

    override val fabDescription: Int?
        get() {
            val action = intent.asAction
            return if (action == Action.SELECT_MAPPING || action == Action.MANAGE)
                R.string.menu_create_main_cat else null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        val action = intent.asAction
        super.onCreate(savedInstanceState)
        binding = ActivityComposeFabBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupToolbar(true)
        injector.inject(viewModel)
        val (helpVariant, title) = when (action) {
            Action.MANAGE ->
                HelpVariant.manage to R.string.pref_manage_categories_title

            Action.SELECT_FILTER ->
                HelpVariant.select_filter to R.string.search_category

            Action.SELECT_MAPPING ->
                HelpVariant.select_mapping to R.string.select_category
        }
        setHelpVariant(helpVariant, true)
        if (title != 0) supportActionBar!!.setTitle(title)
        if (action == Action.SELECT_FILTER) {
            floatingActionButton.visibility = View.GONE
        }
        sortDelegate = SortDelegate(
            defaultSortOrder = viewModel.defaultSort,
            prefKey = PrefKey.SORT_ORDER_CATEGORIES,
            options = arrayOf(Sort.LABEL, Sort.USAGES, Sort.LAST_USED),
            prefHandler = prefHandler,
            collate = collate
        )
        parentSelectionOnTap.value = prefHandler.getBoolean(
            PrefKey.PARENT_CATEGORY_SELECTION_ON_TAP,
            false
        )
        viewModel.setSortOrder(sortDelegate.currentSortOrder)
        observeDeleteResult()
        observeMoveResult()
        observeImportResult()
        observeExportResult()
        observeSyncResult()
        binding.composeView.setContent {
            AppTheme {
                choiceMode = when (action) {
                    Action.SELECT_MAPPING -> {
                        val selectionState: MutableState<Category?> = remember {
                            mutableStateOf(null)
                        }
                        LaunchedEffect(selectionState.value) {
                            selectionState.value?.let {
                                if (it.level > 2) {
                                    contribFeatureRequested(
                                        ContribFeature.CATEGORY_TREE,
                                        R.id.SELECT_COMMAND to it
                                    )
                                } else {
                                    doSingleSelection(it)
                                }
                            }
                        }
                        ChoiceMode.SingleChoiceMode(selectionState, parentSelectionOnTap.value)
                    }

                    Action.MANAGE, Action.SELECT_FILTER -> {
                        val selectionState = rememberMutableStateListOf<Category>()
                        LaunchedEffect(selectionState.size) {
                            if (selectionState.isNotEmpty()) {
                                startActionMode(selectionState)
                                updateActionModeTitle(selectionState)
                            } else {
                                finishActionMode()
                            }
                        }
                        ChoiceMode.MultiChoiceMode(selectionState, true)
                    }
                }
                (viewModel.dialogState as? CategoryViewModel.Show)?.let {
                    CategoryEdit(
                        dialogState = it,
                        onDismissRequest = { viewModel.dialogState = CategoryViewModel.NoShow },
                        onSave = viewModel::saveCategory
                    )
                }
                viewModel.categoryTree.collectAsState(initial = Category.LOADING).value.let { state ->
                    val typeFlags = viewModel.typeFilterLiveData.observeAsState(null).value
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (typeFlags != null) {
                            TypeConfiguration(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(color = colorResource(id = R.color.cardBackground)),
                                typeFlags = typeFlags,
                                onCheckedChange = { viewModel.typeFilter = it }
                            )
                        }
                        Box(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)) {
                            when (state) {
                                LoadingState.Loading -> {
                                    CircularProgressIndicator(
                                        modifier = Modifier
                                            .size(96.dp)
                                            .align(Alignment.Center)
                                    )
                                }

                                is LoadingState.Empty -> {
                                    Column(
                                        modifier = Modifier.align(Alignment.Center),
                                        verticalArrangement = Arrangement.spacedBy(5.dp),
                                        horizontalAlignment = CenterHorizontally
                                    ) {
                                        Text(text = stringResource(id = R.string.no_categories))
                                        if (!state.hasUnfiltered) {
                                            Button(onClick = { importCats() }) {
                                                Column(horizontalAlignment = CenterHorizontally) {
                                                    Icon(
                                                        imageVector = Icons.Filled.PlaylistAdd,
                                                        contentDescription = null
                                                    )
                                                    Text(text = stringResource(id = R.string.menu_categories_setup_default))
                                                }
                                            }
                                        }
                                    }
                                }

                                is LoadingState.Data -> {

                                    Category(
                                        category = if (action == Action.SELECT_FILTER)
                                            state.data.copy(children = buildList {
                                                add(
                                                    Category(
                                                        id = NULL_ITEM_ID,
                                                        label = stringResource(id = R.string.unmapped),
                                                        level = 1
                                                    )
                                                )
                                                addAll(state.data.children)
                                            })
                                        else state.data,
                                        expansionMode = ExpansionMode.DefaultCollapsed(
                                            rememberMutableStateListOf()
                                        ),
                                        menuGenerator = remember {
                                            {
                                                if (action == Action.SELECT_FILTER) null else Menu(
                                                    listOfNotNull(
                                                        if ((choiceMode as? ChoiceMode.SingleChoiceMode)?.selectParentOnClick == false) {
                                                            select("SELECT_CATEGORY") {
                                                                doSingleSelection(
                                                                    it
                                                                )
                                                            }
                                                        } else null,
                                                        edit("EDIT_CATEGORY") { editCat(it) },
                                                        delete("DELETE_CATEGORY") {
                                                            val flatList = it.flatten()
                                                            val defaultTransferCategory =
                                                                flatList.find {
                                                                    it.id == prefHandler.defaultTransferCategory
                                                                }
                                                            when {
                                                                flatList.map { it.id }
                                                                    .contains(protectionInfo?.id) -> {
                                                                    showSnackBar(
                                                                        resources.getQuantityString(
                                                                            if (protectionInfo!!.isTemplate) R.plurals.not_deletable_mapped_templates else R.plurals.not_deletable_mapped_transactions,
                                                                            1,
                                                                            1
                                                                        )
                                                                    )
                                                                }

                                                                defaultTransferCategory != null -> {
                                                                    showSnackBar(
                                                                        getString(
                                                                            R.string.warning_delete_default_transfer_category,
                                                                            defaultTransferCategory.path
                                                                        )
                                                                    )
                                                                }

                                                                else -> {
                                                                    viewModel.deleteCategories(
                                                                        listOf(it)
                                                                    )
                                                                }
                                                            }
                                                        },
                                                        MenuEntry(
                                                            icon = Icons.Filled.Add,
                                                            label = R.string.subcategory,
                                                            command = "CREATE_SUBCATEGORY"
                                                        ) {
                                                            if (it.level > 1) {
                                                                contribFeatureRequested(
                                                                    ContribFeature.CATEGORY_TREE,
                                                                    R.id.CREATE_SUB_COMMAND to it
                                                                )
                                                            } else {
                                                                createCat(it)
                                                            }
                                                        },
                                                        MenuEntry(
                                                            icon = myiconpack.ArrowsAlt,
                                                            label = R.string.menu_move,
                                                            command = "MOVE_CATEGORY"
                                                        ) { showMoveTargetDialog(it) }
                                                    )
                                                )
                                            }
                                        },
                                        choiceMode = choiceMode
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun doSingleSelection(category: Category) {
        val intent = Intent().apply {
            putExtra(KEY_ACCOUNTID, intent.getLongExtra(KEY_ACCOUNTID, 0))
            putExtra(KEY_ROWID, category.id)
            putExtra(KEY_LABEL, category.path)
            putExtra(KEY_ICON, category.icon)
        }
        setResult(RESULT_OK, intent)
        finish()
    }

    fun doMultiSelection() {
        val selected = (choiceMode as ChoiceMode.MultiChoiceMode).selectionState
        if (selected.size == 1 || !selected.any { it.id == NULL_ITEM_ID }) {
            val label = (viewModel.categoryTree.value as LoadingState.Data).data.flatten()
                .filter { selected.any { category -> category.id == it.id } }
                .joinToString(separator = ",") { it.label }
            setResult(RESULT_FIRST_USER, Intent().apply {
                putExtra(KEY_ACCOUNTID, intent.getLongExtra(KEY_ACCOUNTID, 0))
                putExtra(KEY_ROWID, selected.map { it.id }.toLongArray())
                putExtra(KEY_LABEL, label)
            })
            finish()
        } else {
            showSnackBar(R.string.unmapped_filter_only_single)
        }
    }

    private fun showMoveTargetDialog(category: Category) {
        SelectCategoryMoveTargetDialogFragment.newInstance(category)
            .show(supportFragmentManager, "SELECT_TARGET")
    }

    private fun finishActionMode() {
        actionMode?.finish()
    }

    private fun updateActionModeTitle(selectionState: SnapshotStateList<Category>) {
        actionMode?.title = "${selectionState.size}"
    }

    private fun startActionMode(selectionState: SnapshotStateList<Category>) {
        if (actionMode == null) {
            actionMode = startSupportActionMode(object : ActionMode.Callback {
                override fun onCreateActionMode(
                    mode: ActionMode,
                    menu: Menu
                ): Boolean {
                    val action = intent.asAction
                    if (action == Action.MANAGE) {
                        menu.add(
                            Menu.NONE,
                            R.id.DELETE_COMMAND,
                            0,
                            R.string.menu_delete
                        ).setIcon(R.drawable.ic_menu_delete)
                    } else if (action == Action.SELECT_FILTER) {
                        menu.add(
                            Menu.NONE,
                            R.id.SELECT_COMMAND,
                            0,
                            R.string.menu_select
                        ).setIcon(R.drawable.ic_menu_done)
                    }
                    return true
                }

                override fun onPrepareActionMode(
                    mode: ActionMode,
                    menu: Menu
                ): Boolean = true

                override fun onActionItemClicked(
                    mode: ActionMode,
                    item: MenuItem
                ): Boolean = when (item.itemId) {
                    R.id.DELETE_COMMAND -> {
                        viewModel.deleteCategories(selectionState)
                        true
                    }

                    R.id.SELECT_COMMAND -> {
                        doMultiSelection()
                        true
                    }

                    else -> false
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    actionMode = null
                    selectionState.clear()
                }

            })
        }
    }

    private fun MutableList<String>.mapToMessage(quantity: Int, @PluralsRes resId: Int) {
        if (quantity > 0) add(
            resources.getQuantityString(
                resId,
                quantity,
                quantity
            )
        )
    }

    private val dismissCallback = object : Snackbar.Callback() {
        override fun onDismissed(
            transientBottomBar: Snackbar,
            event: Int
        ) {
            if (event == DISMISS_EVENT_SWIPE || event == DISMISS_EVENT_ACTION || event == DISMISS_EVENT_TIMEOUT)
                viewModel.messageShown()
        }
    }

    private fun observeMoveResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.moveResult.collect { result ->
                    result?.let {
                        showDismissibleSnackBar(
                            if (it) R.string.move_category_success else R.string.move_category_failure,
                            dismissCallback
                        )
                    }
                }
            }
        }
    }

    private fun observeDeleteResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.deleteResult.collect { result ->
                    result?.onSuccess {
                        when (it) {
                            is OperationComplete -> {
                                finishActionMode()
                                val messages = buildList {
                                    mapToMessage(it.deleted, R.plurals.delete_success)
                                    mapToMessage(
                                        it.mappedToTransactions,
                                        R.plurals.not_deletable_mapped_transactions
                                    )
                                    mapToMessage(
                                        it.mappedToTemplates,
                                        R.plurals.not_deletable_mapped_templates
                                    )
                                }
                                showDismissibleSnackBar(
                                    messages.joinToString(" "),
                                    dismissCallback
                                )
                            }

                            is OperationPending -> {
                                val messages = buildList {
                                    if (it.hasDescendants > 0) {
                                        mapToMessage(
                                            it.hasDescendants,
                                            R.plurals.warning_delete_main_category
                                        )
                                    }
                                    if (it.mappedToBudgets > 0) {
                                        add(getString(R.string.warning_delete_category_with_budget))
                                    }
                                    add(getString(R.string.continue_confirmation))
                                }
                                val labels = it.categories.joinToString { it.label }
                                MessageDialogFragment.newInstance(
                                    getString(R.string.dialog_title_warning_delete_category) +
                                            " ($labels)",
                                    messages.joinToString(" "),
                                    MessageDialogFragment.Button(
                                        R.string.response_yes,
                                        R.id.DELETE_COMMAND_DO,
                                        it.categories.map { it.id }.toTypedArray()
                                    ),
                                    null,
                                    MessageDialogFragment.Button(
                                        R.string.response_no,
                                        R.id.CANCEL_CALLBACK_COMMAND,
                                        null
                                    )
                                )
                                    .show(supportFragmentManager, "DELETE_CATEGORY")
                                viewModel.messageShown()
                            }
                        }
                    }?.onFailure {
                        showDeleteFailureFeedback(it.message, dismissCallback)
                    }
                }
            }
        }
    }

    private fun observeExportResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.exportResult.collect { result ->
                    result?.onSuccess { pair ->
                        updateDismissibleSnackBar(
                            getString(
                                R.string.export_sdcard_success,
                                pair.second
                            )
                        )
                        if (prefHandler.getBoolean(PrefKey.PERFORM_SHARE, false)) {
                            shareViewModel.share(
                                this@ManageCategories, listOf(pair.first),
                                prefHandler.requireString(PrefKey.SHARE_TARGET, "").trim(),
                                "text/qif"
                            )
                        }
                    }?.onFailure {
                        updateDismissibleSnackBar(it.safeMessage)
                    }
                }
            }
        }
    }

    private fun observeImportResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.importResult.collect { pair ->
                    pair?.let {
                        showDismissibleSnackBar(
                            if (pair.first == 0 && pair.second == 0) {
                                getString(R.string.import_categories_none)
                            } else {
                                buildList {
                                    pair.first.takeIf { it != 0 }?.let {
                                        add(getString(R.string.import_categories_success, it))
                                    }
                                    pair.second.takeIf { it != 0 }?.let {
                                        add(
                                            resources.getQuantityString(
                                                R.plurals.import_categories_icons_updated,
                                                it,
                                                it
                                            )
                                        )
                                    }
                                }.joinToString(separator = " ")
                            },
                            dismissCallback
                        )
                    }
                }
            }
        }
    }

    private fun observeSyncResult() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.syncResult.collect {
                    showSnackBar(it, callback = dismissCallback)
                }
            }
        }
    }

    override val fabActionName = "CREATE_CATEGORY"

    override fun onFabClicked() {
        super.onFabClicked()
        createCat(null)
    }

    override fun dispatchCommand(command: Int, tag: Any?) =
        if (super.dispatchCommand(command, tag)) {
            true
        } else when (command) {
            R.id.CANCEL_CALLBACK_COMMAND -> {
                finishActionMode()
                true
            }

            R.id.DELETE_COMMAND_DO -> {
                showSnackBarIndefinite(R.string.progress_dialog_deleting)
                @Suppress("UNCHECKED_CAST")
                viewModel.deleteCategoriesDo((tag as Array<Long>).toList())
                true
            }

            R.id.SETUP_CATEGORIES_DEFAULT_COMMAND -> {
                importCats()
                true
            }

            R.id.EXPORT_CATEGORIES_COMMAND_ISO88591 -> {
                exportCats("ISO-8859-1")
                true
            }

            R.id.EXPORT_CATEGORIES_COMMAND_UTF8 -> {
                exportCats("UTF-8")
                true
            }

            R.id.TOGGLE_PARENT_CATEGORY_SELECTION_ON_TAP -> {
                val value = tag as Boolean
                parentSelectionOnTap.value = value
                prefHandler.putBoolean(PrefKey.PARENT_CATEGORY_SELECTION_ON_TAP, value)
                true
            }

            R.id.TYPE_FILTER_COMMAND -> {
                viewModel.toggleTypeFilterIsShown()
                invalidateOptionsMenu()
                true
            }

            else -> false
        }

    private val protectionInfo: ProtectionInfo?
        get() = intent.getParcelableExtra(KEY_PROTECTION_INFO)

    private fun importCats() {
        showSnackBarIndefinite(R.string.menu_categories_setup_default)
        viewModel.importCats()
    }

    private fun exportCats(encoding: String) {
        showDismissibleSnackBar(getString(R.string.export_to_format, "QIF"))
        viewModel.exportCats(encoding)
    }

    /**
     * presents AlertDialog for adding a new category
     * if label is already used, shows an error
     */
    open fun createCat(parent: Category?) {
        viewModel.dialogState = CategoryViewModel.Show(
            parent = parent,
            category = if (parent == null) Category(
                typeFlags = viewModel.typeFilter
            ) else null
        )
    }

    /**
     * presents AlertDialog for editing an existing category
     */
    open fun editCat(category: Category) {
        viewModel.dialogState =
            CategoryViewModel.Show(category = category)
    }

    override fun contribFeatureCalled(feature: ContribFeature, tag: Serializable?) {
        if (feature == ContribFeature.CATEGORY_TREE) {
            val (command, category) = tag as Pair<Int, Category>
            if (command == R.id.CREATE_SUB_COMMAND) {
                createCat(category)
            } else if (command == R.id.SELECT_COMMAND) {
                doSingleSelection(category)
            }
        }
    }

    companion object {
        const val KEY_PROTECTION_INFO = "protection_info"
    }

    @Parcelize
    data class ProtectionInfo(val id: Long, val isTemplate: Boolean) : Parcelable
}