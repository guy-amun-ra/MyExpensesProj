package org.totschnig.myexpenses.activity

import android.accounts.AccountManager
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.SubMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.NotificationManagerCompat
import com.evernote.android.state.State
import eltos.simpledialogfragment.SimpleDialog.OnDialogResultListener
import eltos.simpledialogfragment.form.Input
import eltos.simpledialogfragment.form.SimpleFormDialog
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.feature.Feature
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.sync.BackendService
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.sync.json.AccountMetaData
import org.totschnig.myexpenses.util.PermissionHelper
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.SyncViewModel
import org.totschnig.myexpenses.viewmodel.SyncViewModel.Companion.KEY_QUERY_LOCAL_ACCOUNTS
import org.totschnig.myexpenses.viewmodel.SyncViewModel.Companion.KEY_RETURN_BACKUPS

abstract class SyncBackendSetupActivity : RestoreActivity(),
    OnDialogResultListener {

    private lateinit var backendProviders: List<BackendService>
    protected val viewModel: SyncViewModel by viewModels()
    private var isResumed = false

    @JvmField
    @State
    var selectedFactoryId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        backendProviders = BackendService.allAvailable(this)
        injector.inject(viewModel)
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        if (selectedFactoryId != 0) {
            startSetupCheckPrerequisites()
        }
    }

    override fun onPause() {
        super.onPause()
        isResumed = false
    }

    fun startSetup(itemId: Int) {
        selectedFactoryId = itemId
        if (isResumed) {
            startSetupCheckPrerequisites()
        }
    }

    private val startSetup =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                result.data?.let {
                    createAccount(
                        it.getStringExtra(AccountManager.KEY_ACCOUNT_NAME)!!,
                        it.getStringExtra(AccountManager.KEY_PASSWORD),
                        it.getStringExtra(AccountManager.KEY_AUTHTOKEN),
                        it.getBundleExtra(AccountManager.KEY_USERDATA)
                    )
                }
            }
        }

    private fun startSetupCheckPrerequisites() {
        val backendService = getBackendServiceById(selectedFactoryId)
        val feature = backendService?.feature
        if (feature == null || featureManager.isFeatureInstalled(feature, this)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                !NotificationManagerCompat.from(this).areNotificationsEnabled()
            ) {
                requestNotificationPermission(PermissionHelper.PERMISSIONS_REQUEST_NOTIFICATIONS_SYNC)
            } else {
                startSetupDo()
            }
        } else {
            featureManager.requestFeature(feature, this)
        }
    }

    private fun startSetupDo() {
        getBackendServiceById(selectedFactoryId)?.instantiate()?.getOrNull()?.setupActivityClass?.let {
            startSetup.launch(Intent(this, it))
        }
        selectedFactoryId = 0
    }

    override fun onFeatureAvailable(feature: Feature) {
        super.onFeatureAvailable(feature)
        if (selectedFactoryId != 0 && getBackendServiceByIdOrThrow(selectedFactoryId).feature == feature) {
            startSetupCheckPrerequisites()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: List<String>) {
        super.onPermissionsDenied(requestCode, perms)
        if (requestCode == PermissionHelper.PERMISSIONS_REQUEST_NOTIFICATIONS_SYNC) {
            startSetupDo()
        }
    }

    override fun onPermissionsGranted(requestCode: Int, perms: List<String>) {
        super.onPermissionsGranted(requestCode, perms)
        if (requestCode == PermissionHelper.PERMISSIONS_REQUEST_NOTIFICATIONS_SYNC) {
            startSetupDo()
        }
    }

    private fun createAccount(
        accountName: String?,
        password: String?,
        authToken: String?,
        bundle: Bundle?
    ) {
        val args = Bundle()
        args.putString(AccountManager.KEY_ACCOUNT_NAME, accountName)
        args.putString(AccountManager.KEY_PASSWORD, password)
        args.putString(AccountManager.KEY_AUTHTOKEN, authToken)
        args.putParcelable(AccountManager.KEY_USERDATA, bundle)
        args.putBoolean(
            KEY_RETURN_BACKUPS,
            createAccountTaskShouldReturnBackups
        )
        args.putBoolean(
            KEY_QUERY_LOCAL_ACCOUNTS,
            createAccountTaskShouldQueryLocalAccounts
        )
        SimpleFormDialog.build().msg(R.string.passphrase_for_synchronization)
            .fields(
                Input.password(GenericAccountService.KEY_PASSWORD_ENCRYPTION).required()
                    .hint(R.string.input_label_passphrase)
            )
            .extra(args)
            .neut(R.string.button_label_no_encryption)
            .show(this, DIALOG_TAG_PASSWORD)
    }

    private fun createAccountDo(args: Bundle) {
        showLoadingSnackBar()
        viewModel.createSyncAccount(args).observe(this) { result ->
            result.onSuccess {
                dismissSnackBar()
                recordUsage(ContribFeature.SYNCHRONIZATION)
                if ("xiaomi".equals(Build.MANUFACTURER, ignoreCase = true)) {
                    showMessage("On some Xiaomi devices, synchronization does not work without AutoStart permission. Visit <a href=\"https://github.com/mtotschnig/MyExpenses/wiki/FAQ:-Synchronization#q2\">MyExpenses FAQ</a> for more information.")
                }
                onReceiveSyncAccountData(it)
            }.onFailure { throwable ->
                showSnackBar("Unable to set up account: " + throwable.message)
            }
        }
    }

    abstract fun onReceiveSyncAccountData(data: SyncViewModel.SyncAccountData)

    fun checkForDuplicateUuids(data: List<AccountMetaData>) =
        data.map(AccountMetaData::uuid).distinct().count() < data.count()

    fun fetchAccountData(accountName: String) {
        showLoadingSnackBar()
        viewModel.fetchAccountData(accountName).observe(this) { result ->
            dismissSnackBar()
            result.onSuccess {
                onReceiveSyncAccountData(it)
            }.onFailure {
                showSnackBar(it.safeMessage)
            }
        }
    }

    fun showLoadingSnackBar() {
        showProgressSnackBar(getString(R.string.progress_dialog_fetching_data_from_sync_backend))
    }

    protected open val createAccountTaskShouldReturnBackups = false

    protected open val createAccountTaskShouldQueryLocalAccounts= true

    fun addSyncProviderMenuEntries(subMenu: SubMenu) {
        for (factory in backendProviders) {
            subMenu.add(Menu.NONE, factory.id, Menu.NONE, factory.label)
        }
    }

    fun getBackendServiceById(id: Int): BackendService? {
        return try {
            getBackendServiceByIdOrThrow(id)
        } catch (e: IllegalStateException) {
            null
        }
    }

    @Throws(IllegalStateException::class)
    fun getBackendServiceByIdOrThrow(id: Int): BackendService {
        for (backendService in backendProviders) {
            if (backendService.id == id) {
                return backendService
            }
        }
        throw IllegalStateException()
    }

    override fun onResult(dialogTag: String, which: Int, extras: Bundle): Boolean {
        if (DIALOG_TAG_PASSWORD == dialogTag) {
            if (which != OnDialogResultListener.BUTTON_POSITIVE || "" == extras.getString(
                    GenericAccountService.KEY_PASSWORD_ENCRYPTION
                )
            ) {
                extras.remove(GenericAccountService.KEY_PASSWORD_ENCRYPTION)
            }
            createAccountDo(extras)
        }
        return false
    }

    companion object {
        private const val DIALOG_TAG_PASSWORD = "password"
        const val REQUEST_CODE_RESOLUTION = 1
    }
}