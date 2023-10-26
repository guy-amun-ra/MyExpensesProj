package org.totschnig.myexpenses.util.licence

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.annotation.VisibleForTesting
import com.google.android.vending.licensing.PreferenceObfuscator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.apache.commons.lang3.time.DateUtils
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.IapActivity
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.db2.countAccounts
import org.totschnig.myexpenses.model.ContribFeature
import org.totschnig.myexpenses.model.CurrencyUnit
import org.totschnig.myexpenses.model.Template
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.sync.GenericAccountService
import org.totschnig.myexpenses.util.EU_COUNTRIES
import org.totschnig.myexpenses.util.ICurrencyFormatter
import org.totschnig.myexpenses.util.ShortcutHelper
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.enumValueOrNull
import timber.log.Timber
import java.time.Clock
import java.time.LocalTime
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit

open class LicenceHandler(
    protected val context: Application,
    var licenseStatusPrefs: PreferenceObfuscator,
    private val crashHandler: CrashHandler,
    protected val prefHandler: PrefHandler,
    private val repository: Repository,
    private val currencyFormatter: ICurrencyFormatter,
    private val clock: Clock = Clock.systemUTC()
) {
    private var hasOurLicence = false
    private val isSandbox = BuildConfig.DEBUG
    var licenceStatus: LicenceStatus? = null
        internal set(value) {
            crashHandler.putCustomData("Licence", value?.name ?: "null")
            field = value
        }
    val addOnFeatures: MutableSet<ContribFeature> = mutableSetOf()

    val currencyUnit: CurrencyUnit = CurrencyUnit("EUR", "€", 2)
    fun hasValidKey(): Boolean {
        return hasOurLicence
    }

    //called from PlayStoreLicenceHandler
    fun maybeUpgradeAddonFeatures(features: List<ContribFeature>, newPurchase: Boolean) {
        log().i("maybeUpgradeAddonFeatures ${features.joinToString()}, newPurchase: $newPurchase")
        if (!hasOurLicence && !newPurchase) {
            addOnFeatures.clear()
        }
        addFeatures(features)
    }

    private fun addFeatures(features: List<ContribFeature>) {
        addOnFeatures.addAll(features)
        persistAddonFeatures()
    }

    //called from PlayStoreLicenceHandler
    fun maybeUpgradeLicence(licenceStatus: LicenceStatus?) {
        if (!hasOurLicence || this.licenceStatus?.greaterOrEqual(licenceStatus) != true) {
            this.licenceStatus = licenceStatus
        }
    }

    val isContribEnabled: Boolean
        get() = isEnabledFor(LicenceStatus.CONTRIB)

    @get:VisibleForTesting
    val isExtendedEnabled: Boolean
        get() = isEnabledFor(LicenceStatus.EXTENDED)
    val isProfessionalEnabled: Boolean
        get() = isEnabledFor(LicenceStatus.PROFESSIONAL)

    /**
     * @return user either has access through licence or through trial
     */
    fun hasTrialAccessTo(feature: ContribFeature): Boolean {
        return hasAccessTo(feature) || usagesLeft(feature)
    }

    fun hasAccessTo(feature: ContribFeature): Boolean {
        return isEnabledFor(feature.licenceStatus) || addOnFeatures.contains(feature)
    }

    open fun isEnabledFor(licenceStatus: LicenceStatus) =
        (this.licenceStatus?.compareTo(licenceStatus) ?: -1) >= 0

    val isUpgradeable: Boolean
        get() = licenceStatus?.isUpgradeable ?: true

    open fun init() {
        this.licenceStatus = enumValueOrNull<LicenceStatus>(
            licenseStatusPrefs.getString(LICENSE_STATUS_KEY, null)
        )?.also {
            hasOurLicence = true
        }
        restoreAddOnFeatures()
    }

    fun update() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Template.updateNewPlanEnabled()
                updateNewAccountEnabled()
            } catch (_: Exception) {}
            GenericAccountService.updateAccountsIsSyncable(
                context,
                this@LicenceHandler,
                prefHandler
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
                ShortcutHelper.configureSplitShortcut(context, isContribEnabled)
            }
        }
    }

    open fun voidLicenceStatus(keepFeatures: Boolean) {
        this.licenceStatus = null
        licenseStatusPrefs.remove(LICENSE_STATUS_KEY)
        licenseStatusPrefs.remove(LICENSE_VALID_SINCE_KEY)
        licenseStatusPrefs.remove(LICENSE_VALID_UNTIL_KEY)
        if (!keepFeatures) {
            addOnFeatures.clear()
            licenseStatusPrefs.remove(LICENSE_FEATURES)
        }
        if (addOnFeatures.isEmpty()) {
            hasOurLicence = false
        }
        licenseStatusPrefs.commit()
    }

    open fun updateLicenceStatus(licence: Licence) {
        hasOurLicence = true
        this.licenceStatus = licence.type
        licenseStatusPrefs.putString(LICENSE_STATUS_KEY, licence.type?.name ?: "null")
        addFeatures(licence.featureList)
        if (licence.validSince != null) {
            val validSince =
                licence.validSince.atTime(LocalTime.MAX).atZone(ZoneId.of("Etc/GMT-14"))
            licenseStatusPrefs.putString(
                LICENSE_VALID_SINCE_KEY,
                (validSince.toEpochSecond() * 1000).toString()
            )
        }
        if (licence.validUntil != null) {
            val validUntil =
                licence.validUntil.atTime(LocalTime.MAX).atZone(ZoneId.of("Etc/GMT+12"))
            licenseStatusPrefs.putString(
                LICENSE_VALID_UNTIL_KEY,
                (validUntil.toEpochSecond() * 1000).toString()
            )
        } else {
            licenseStatusPrefs.remove(LICENSE_VALID_UNTIL_KEY)
        }
        licenseStatusPrefs.commit()
        update()
    }

    fun reset() {
        init()
        update()
    }

    open fun getFormattedPrice(aPackage: Package): String? {
        return getFormattedPriceWithExtra(aPackage, false)
    }

    @Suppress("RedundantNullableReturnType")
    fun getFormattedPriceWithExtra(aPackage: Package, withExtra: Boolean): String? {
        return aPackage.getFormattedPrice(context, currencyFormatter, currencyUnit, withExtra)
    }

    fun getFormattedPriceWithSaving(aPackage: ProfessionalPackage): String? {
        val withExtra = licenceStatus === LicenceStatus.EXTENDED
        val formattedPrice = getFormattedPriceWithExtra(aPackage, withExtra)
        val base = ProfessionalPackage.Professional_6
        return if (aPackage == base) formattedPrice else String.format(
            Locale.ROOT, "%s (- %d %%)", formattedPrice,
            100 - aPackage.defaultPrice * 100 * base.getDuration(withExtra) /
                    (aPackage.getDuration(withExtra) * base.defaultPrice)
        )
    }

    open fun getExtendOrSwitchMessage(aPackage: ProfessionalPackage): String {
        val extendedDate = DateUtils.addMonths(
            Date(validUntilMillis.coerceAtLeast(clock.millis())),
            aPackage.getDuration(false)
        )
        return context.getString(
            R.string.extend_until,
            Utils.getDateFormatSafe(context).format(extendedDate),
            aPackage.getFormattedPriceRaw(currencyUnit, currencyFormatter)
        )
    }

    open fun getProLicenceStatus(context: Context) = getProValidUntil(context)

    @Suppress("MemberVisibilityCanBePrivate") //used from Huawei
    fun getProValidUntil(context: Context): String? {
        return validUntilMillis.takeIf { it != 0L }?.let {
            context.getString(
                R.string.valid_until,
                Utils.getDateFormatSafe(this.context).format(Date(it))
            )
        }
    }

    val validUntilMillis: Long
        get() = licenseStatusPrefs.getString(LICENSE_VALID_UNTIL_KEY, "0").toLong()

    @Suppress("MemberVisibilityCanBePrivate")
    val validSinceMillis: Long
        get() = licenseStatusPrefs.getString(LICENSE_VALID_SINCE_KEY, "0").toLong()

    open val proPackages: Array<ProfessionalPackage>
        get() = arrayOf(
            ProfessionalPackage.Professional_6,
            ProfessionalPackage.Professional_12,
            ProfessionalPackage.Professional_24
        )

    open fun getExtendedUpgradeGoodyMessage(selectedPackage: ProfessionalPackage): String? {
        return context.getString(R.string.extended_upgrade_goodie_github, 3)
    }

    open val professionalPriceShortInfo: String
        get() = joinPriceInformation(*proPackages)

    @Suppress("MemberVisibilityCanBePrivate") //used from Amazon
    protected fun joinPriceInformation(vararg packages: Package) =
        packages.map(::getFormattedPrice)
            .joinToString(" ${context.getString(R.string.joining_or)} ")

    open val proPackagesForExtendOrSwitch: Array<ProfessionalPackage>?
        get() = proPackages

    open fun getProLicenceAction(context: Context): String {
        return context.getString(R.string.extend_validity)
    }

    open val purchaseExtraInfo: String?
        get() = null

    open fun buildRoadmapVoteKey(): String {
        return UUID.randomUUID().toString()
    }

    /**
     * @return true if licenceStatus has been upEd
     */
    open fun registerUnlockLegacy(): Boolean {
        return false
    }

    fun getPaymentOptions(aPackage: Package, userCountry: String) = listOfNotNull(
        R.string.donate_button_paypal,
        if (aPackage.defaultPrice >= 500 && EU_COUNTRIES.contains(userCountry))
            R.string.donate_button_invoice else null
    )

    open val doesUseIAP: Boolean
        get() = false

    open val needsKeyEntry: Boolean
        get() = true

    fun getPaypalUri(aPackage: Package): String {
        val host = if (isSandbox) "www.sandbox.paypal.com" else "www.paypal.com"
        var uri = "https://$host/cgi-bin/webscr?cmd=_s-xclick&hosted_button_id=${aPackage.payPalButtonId(isSandbox)}&on0=${aPackage.optionName}&os0=${aPackage::class.java.simpleName}&lc=$paypalLocale&currency_code=EUR"
        prefHandler.getString(PrefKey.LICENCE_EMAIL, null)?.let {
            uri += "&custom=" + Uri.encode(it)
        }
        Timber.d("Paypal URI: %s", uri)
        return uri
    }

    val backendUri = when {
        isSandbox -> "http://10.0.2.2:3000/"
        else -> "https://licencedb.myexpenses.mobi/"
    }

    private val paypalLocale: String
        get() {
            val locale = Locale.getDefault()
            return when (locale.language) {
                "en" -> "en_US"
                "fr" -> "fr_FR"
                "es" -> "es_ES"
                "zh" -> "zh_CN"
                "ar" -> "ar_EG"
                "de" -> "de_DE"
                "nl" -> "nl_NL"
                "pt" -> "pt_PT"
                "da" -> "da_DK"
                "ru" -> "ru_RU"
                "id" -> "id_ID"
                "iw", "he" -> "he_IL"
                "it" -> "it_IT"
                "ja" -> "ja_JP"
                "no" -> "no_NO"
                "pl" -> "pl_PL"
                "ko" -> "ko_KO"
                "sv" -> "sv_SE"
                "th" -> "th_TH"
                else -> "en_US"
            }
        }

    fun handleExpiration() {
        val licenceDuration = validUntilMillis - validSinceMillis
        if (TimeUnit.MILLISECONDS.toDays(licenceDuration) > 240) { // roughly eight months
            licenceStatus = LicenceStatus.EXTENDED_FALLBACK
            licenseStatusPrefs.putString(LICENSE_STATUS_KEY, LicenceStatus.EXTENDED_FALLBACK.name)
            licenseStatusPrefs.remove(LICENSE_VALID_UNTIL_KEY)
            licenseStatusPrefs.commit()
        } else {
            voidLicenceStatus(true)
        }
    }

    fun prettyPrintStatus(context: Context): String? {
        var result = licenceStatus?.let { context.getString(it.resId) }
        addOnFeatures.takeIf { it.isNotEmpty() }
            ?.joinToString { context.getString(it.labelResId) }?.let {
                if (result == null) {
                    result = ""
                } else {
                    result += " "
                }
                result += "(+ $it)"
            }
        if (licenceStatus == LicenceStatus.PROFESSIONAL) {
            getProLicenceStatus(context)?.let {
                result += String.format(" (%s)", it)
            }
        }
        return result
    }

    fun getButtonLabel(aPackage: Package): String {
        val resId = when (aPackage) {
            Package.Contrib -> LicenceStatus.CONTRIB.resId
            Package.Upgrade -> R.string.pref_contrib_purchase_title_upgrade
            Package.Extended -> LicenceStatus.EXTENDED.resId
            is ProfessionalPackage -> LicenceStatus.PROFESSIONAL.resId
            is AddOnPackage -> aPackage.feature.labelResId
        }
        return String.format(
            "%s (%s)",
            context.getString(resId),
            getFormattedPriceWithExtra(aPackage, licenceStatus === LicenceStatus.EXTENDED)
        )
    }

    open fun initBillingManager(activity: IapActivity, query: Boolean): BillingManager? {
        return null
    }

    open suspend fun launchPurchase(
        aPackage: Package,
        shouldReplaceExisting: Boolean,
        billingManager: BillingManager
    ) {
    }

    private fun persistAddonFeatures() {
        val joinToString = addOnFeatures.joinToString(",", transform = ContribFeature::name)
        licenseStatusPrefs.putString(LICENSE_FEATURES, joinToString)
        crashHandler.putCustomData("AddOns", joinToString)
    }

    private fun restoreAddOnFeatures() {
        licenseStatusPrefs.getString(LICENSE_FEATURES, null)?.split(',')?.mapNotNull {
            try {
                ContribFeature.valueOf(it)
            } catch (e: Exception) {
                null
            }
        }?.let { addFeatures(it) }
    }

    fun updateNewAccountEnabled() {
        val newAccountEnabled =
            hasAccessTo(ContribFeature.ACCOUNTS_UNLIMITED) || repository.countAccounts(
                null,
                null
            ) < ContribFeature.FREE_ACCOUNTS
        prefHandler.putBoolean(PrefKey.NEW_ACCOUNT_ENABLED, newAccountEnabled)
    }

    fun recordUsage(feature: ContribFeature) {
        if (!hasAccessTo(feature) &&
            feature.trialMode == ContribFeature.TrialMode.DURATION &&
            !prefHandler.isSet(feature.prefKey)
        ) {
            prefHandler.putLong(feature.prefKey, clock.millis())
        }
    }


    fun usagesLeft(feature: ContribFeature) = when (feature.trialMode) {
        ContribFeature.TrialMode.DURATION -> {
            val now = clock.millis()
            getEndOfTrial(feature) > now
        }

        ContribFeature.TrialMode.UNLIMITED -> true
        else -> false
    }

    private fun getStartOfTrial(feature: ContribFeature): Long {
        return prefHandler.getLong(feature.prefKey, clock.millis())
    }

    fun getEndOfTrial(feature: ContribFeature): Long {
        val trialDurationMillis = TimeUnit.DAYS.toMillis(TRIAL_DURATION_DAYS)
        return getStartOfTrial(feature) + trialDurationMillis
    }

    companion object {
        protected const val LICENSE_STATUS_KEY = "licence_status"
        protected const val LICENSE_FEATURES = "licence_features"
        private const val LICENSE_VALID_SINCE_KEY = "licence_valid_since"
        private const val LICENSE_VALID_UNTIL_KEY = "licence_valid_until"
        const val TAG = "LicenceHandler"
        const val TRIAL_DURATION_DAYS = 60L
        fun log(): Timber.Tree {
            return Timber.tag(TAG)
        }
    }
}