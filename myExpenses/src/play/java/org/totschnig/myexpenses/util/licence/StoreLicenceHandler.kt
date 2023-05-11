package org.totschnig.myexpenses.util.licence

import android.app.Application
import android.content.Context
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.contrib.Config
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class StoreLicenceHandler(
    context: Application,
    preferenceObfuscator: PreferenceObfuscator,
    crashHandler: CrashHandler,
    prefHandler: PrefHandler,
    repository: Repository,
    currencyFormatter: CurrencyFormatter
) :
        PlayStoreLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler, repository, currencyFormatter) {

    override val proPackages: Array<ProfessionalPackage>
        get() = arrayOf(ProfessionalPackage.Professional_1, ProfessionalPackage.Professional_12)

    override fun getExtendedUpgradeGoodyMessage(selectedPackage: ProfessionalPackage): String? = null

    override val proPackagesForExtendOrSwitch: Array<ProfessionalPackage>?
        get() = getPackageForSwitch()?.let { arrayOf(it) }

    private fun getPackageForSwitch() = when (licenseStatusPrefs.getString(KEY_CURRENT_SUBSCRIPTION_SKU, null)) {
        Config.SKU_PROFESSIONAL_1 -> ProfessionalPackage.Professional_12
        Config.SKU_PROFESSIONAL_12, Config.SKU_EXTENDED2PROFESSIONAL_12 -> ProfessionalPackage.Professional_1
        else -> null
    }

    override fun getExtendOrSwitchMessage(aPackage: ProfessionalPackage) = when (aPackage) {
        ProfessionalPackage.Professional_12 -> R.string.switch_to_yearly
        ProfessionalPackage.Professional_1 -> R.string.switch_to_monthly
        else -> 0
    }.takeIf { it != 0 }?.let { context.getString(it) } ?: ""

    override fun getProLicenceAction(context: Context) =
            getPackageForSwitch()?.let { getExtendOrSwitchMessage(it) } ?: ""
}