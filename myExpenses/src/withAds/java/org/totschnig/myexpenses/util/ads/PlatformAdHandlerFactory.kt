package org.totschnig.myexpenses.util.ads

import android.app.Activity
import android.content.Context
import android.view.ViewGroup
import androidx.annotation.Keep
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.FormError
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import org.totschnig.myexpenses.util.licence.LicenceHandler
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


@Keep
class PlatformAdHandlerFactory(
    context: Context,
    prefHandler: PrefHandler,
    userCountry: String,
    licenceHandler: LicenceHandler
) : DefaultAdHandlerFactory(context, prefHandler, userCountry, licenceHandler) {
    private val isMobileAdsInitializeCalled = AtomicBoolean(false)

    override fun create(adContainer: ViewGroup, baseActivity: BaseActivity): AdHandler {
        val adHandler = if (isAdDisabled || !isMobileAdsInitializeCalled.get()) "NoOp" else {
            val remoteConfig = FirebaseRemoteConfig.getInstance()
            if (BuildConfig.DEBUG) {
                val configSettings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(0)
                    .build()
                remoteConfig.setConfigSettingsAsync(configSettings)
            }
            remoteConfig.setDefaultsAsync(R.xml.remote_config_defaults)
            remoteConfig.fetchAndActivate().addOnCompleteListener { task ->
                if (!task.isSuccessful) {
                    task.exception?.let {
                        Timber.w(it)
                    } ?: run {
                        Timber.w("Firebase Remote Config Fetch failed")
                    }
                }
            }
            remoteConfig.getString("ad_handling_waterfall").split(":".toRegex()).getOrNull(0)
                ?: "AdMob"
        }
        FirebaseAnalytics.getInstance(context).setUserProperty("AdHandler", adHandler)
        return instantiate(adHandler, adContainer, baseActivity)
    }

    private fun instantiate(
        handler: String,
        adContainer: ViewGroup,
        baseActivity: BaseActivity
    ): AdHandler {
        return when (handler) {
            "Custom" -> AdmobAdHandler(
                this, adContainer, baseActivity,
                R.string.admob_unitid_custom_banner, R.string.admob_unitid_custom_interstitial
            )

            "AdMob" -> AdmobAdHandler(
                this, adContainer, baseActivity,
                R.string.admob_unitid_mainscreen, R.string.admob_unitid_interstitial
            )

            else -> NoOpAdHandler
        }
    }

    private fun consentRequestParameters(): ConsentRequestParameters =
        ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
/*            .setConsentDebugSettings(
                if (BuildConfig.DEBUG)
                    ConsentDebugSettings.Builder(context)
                        .addTestDeviceHashedId("C972FBF2309407189542F2244481D500")
                        .setDebugGeography(ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA)
                        .build() else null
            )*/
            .build()

    override fun gdprConsent(context: Activity, forceShow: Boolean) {
        if (forceShow) {
            UserMessagingPlatform.showPrivacyOptionsForm(context) { loadAndShowError ->
                loadAndShowError?.report()
            }
        } else if (!isAdDisabled) {

            val consentInformation = UserMessagingPlatform.getConsentInformation(context)
            consentInformation.requestConsentInfoUpdate(
                context,
                consentRequestParameters(),
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(context) { loadAndShowError ->
                        loadAndShowError?.report()
                        consentInformation.maybeInitialize(context)

                    }
                }
            ) { requestConsentError: FormError -> requestConsentError.report() }
            consentInformation.maybeInitialize(context)
        }
    }

    private fun ConsentInformation.maybeInitialize(context: Context) {
        val canRequestAds = canRequestAds()
        Timber.d("canRequestAds : %b", canRequestAds)
        if (canRequestAds) {
            initializeMobileAdsSdk(context)
        }
    }

    private fun FormError.report() {
        CrashHandler.report(Exception("$errorCode: $message"))
    }

    override suspend fun isPrivacyOptionsRequired(activity: Activity) = suspendCoroutine { cont ->
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        consentInformation.requestConsentInfoUpdate(
            activity,
            consentRequestParameters(),
            {
                cont.resume(
                    consentInformation.privacyOptionsRequirementStatus ==
                            ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
                )
            },
            { requestConsentError: FormError ->
                requestConsentError.report()
                cont.resume(false)
            })
    }

    private fun initializeMobileAdsSdk(context: Context) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        // Initialize the Google Mobile Ads SDK.
        MobileAds.initialize(context)
    }

}