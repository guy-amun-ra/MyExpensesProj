package org.totschnig.myexpenses.sync

import android.accounts.Account
import android.accounts.AccountManager
import android.content.Context
import android.net.Uri
import androidx.annotation.Keep
import org.totschnig.myexpenses.activity.SafSetup


@Keep
class StorageAccessFrameworkBackendProviderFactory : SyncBackendProviderFactory() {

    override fun fromAccount(
        context: Context,
        account: Account,
        accountManager: AccountManager
    ) = StorageAccessFrameworkBackendProvider(
        context,
        Uri.parse(accountManager.getSyncProviderUrl(account))
    )

    override val setupActivityClass = SafSetup::class.java

}