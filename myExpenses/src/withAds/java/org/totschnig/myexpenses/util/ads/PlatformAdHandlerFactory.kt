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
import org.totschnig.myexpenses.util.licence.LicenceHandler
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean


@Keep
class PlatformAdHandlerFactory(context: Context, prefHandler: PrefHandler, userCountry: String, licenceHandler: LicenceHandler) : DefaultAdHandlerFactory(context, prefHandler, userCountry, licenceHandler) {
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
            remoteConfig.getString("ad_handling_waterfall").split(":".toRegex()).getOrNull(0) ?: "AdMob"
        }
        FirebaseAnalytics.getInstance(context).setUserProperty("AdHandler", adHandler)
        return instantiate(adHandler, adContainer, baseActivity)
    }

    private fun instantiate(handler: String, adContainer: ViewGroup, baseActivity: BaseActivity): AdHandler {
        return when (handler) {
            "Custom" -> AdmobAdHandler(this, adContainer, baseActivity,
                    R.string.admob_unitid_custom_banner, R.string.admob_unitid_custom_interstitial)
            "AdMob" -> AdmobAdHandler(this, adContainer, baseActivity,
                    R.string.admob_unitid_mainscreen, R.string.admob_unitid_interstitial)
            else -> NoOpAdHandler
        }
    }

    override fun gdprConsent(context: Activity, forceShow: Boolean) {
        if (forceShow || !isAdDisabled) {
            val params = ConsentRequestParameters.Builder()
                .setTagForUnderAgeOfConsent(false)
                .build()

            val consentInformation = UserMessagingPlatform.getConsentInformation(context)
            consentInformation.requestConsentInfoUpdate(
                context,
                params,
                {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(context) { loadAndShowError ->
                        if (loadAndShowError != null) {
                            Timber.w(
                                "%s: %s",
                                loadAndShowError.errorCode,
                                loadAndShowError.message
                            )

                        }
                        consentInformation.maybeInitialize(context)

                    }
                },
                { requestConsentError: FormError ->
                    // Consent gathering failed.
                    Timber.w("%s: %s", requestConsentError.errorCode, requestConsentError.message)
                })
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

    private fun initializeMobileAdsSdk(context: Context) {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return
        }

        // Initialize the Google Mobile Ads SDK.
        MobileAds.initialize(context)
    }

}