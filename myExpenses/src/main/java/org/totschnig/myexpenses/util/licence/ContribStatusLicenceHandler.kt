package org.totschnig.myexpenses.util.licence

import android.app.Application
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

/**
 * Common functionality defunct BlackberryLegacyLicenceHandler and [StoreLicenceHandler]
 */
abstract class ContribStatusLicenceHandler internal constructor(
    context: Application,
    preferenceObfuscator: PreferenceObfuscator,
    crashHandler: CrashHandler,
    prefHandler: PrefHandler,
    repository: Repository,
    currencyFormatter: CurrencyFormatter
) : LicenceHandler(
    context,
    preferenceObfuscator,
    crashHandler,
    prefHandler,
    repository,
    currencyFormatter
) {
    protected var contribStatus = 0
        @Synchronized get
        @Synchronized set(value) {
            field = value
            if (value >= STATUS_PROFESSIONAL) {
                maybeUpgradeLicence(LicenceStatus.PROFESSIONAL)
            } else if (value >= STATUS_EXTENDED_TEMPORARY) {
                maybeUpgradeLicence(LicenceStatus.EXTENDED)
            } else if (value > 0) {
                maybeUpgradeLicence(LicenceStatus.CONTRIB)
            } else {
                maybeUpgradeLicence(null)
            }
            d("valueSet")
        }

    abstract val legacyStatus: Int

    /**
     * @return true if licenceStatus has been upEd
     */
    override fun registerUnlockLegacy(): Boolean {
        return if (licenceStatus == null) {
            updateContribStatus(legacyStatus)
            licenseStatusPrefs.commit()
            true
        } else {
            false
        }
    }

    override fun isEnabledFor(licenceStatus: LicenceStatus): Boolean {
        return BuildConfig.UNLOCK_SWITCH || super.isEnabledFor(licenceStatus)
    }

    /**
     * Sets the licenceStatus from contribStatus and commits licenseStatusPrefs
     */
    fun updateContribStatus(contribStatus: Int) {
        licenseStatusPrefs.putString(licenceStatusKey(), contribStatus.toString())
        this.contribStatus = contribStatus
        update()
    }

    private fun licenceStatusKey(): String {
        return prefHandler.getKey(PrefKey.LICENSE_STATUS)
    }

    protected fun readContribStatusFromPrefs() {
        contribStatus = licenseStatusPrefs.getString(licenceStatusKey(), "0").toInt()
    }

    protected fun d(event: String?) {
        log().i("%s: %s-%s, contrib status %s", event, this, Thread.currentThread(), contribStatus)
    }

    companion object {
        //public static final String STATUS_ENABLED_LEGACY_FIRST = "1";
        /**
         * this status was used after the APP_GRATIS campaign in order to distinguish
         * between free riders and buyers
         */
        const val STATUS_ENABLED_LEGACY_SECOND = 2

        /**
         * user has recently purchased, and is inside a two days window
         */
        const val STATUS_ENABLED_TEMPORARY = 3
        //public static final String STATUS_ENABLED_VERIFICATION_NEEDED = "4";
        /**
         * recheck passed
         */
        const val STATUS_ENABLED_PERMANENT = 5
        const val STATUS_EXTENDED_TEMPORARY = 6
        const val STATUS_EXTENDED_PERMANENT = 7
        const val STATUS_PROFESSIONAL = 10
    }
}