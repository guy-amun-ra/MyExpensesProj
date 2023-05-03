package org.totschnig.myexpenses.util.ads

import android.content.Context
import android.view.ViewGroup
import androidx.annotation.Keep
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.licence.LicenceHandler
import timber.log.Timber

@Keep
class PlatformAdHandlerFactory(context: Context, prefHandler: PrefHandler, userCountry: String, licenceHandler: LicenceHandler) : DefaultAdHandlerFactory(context, prefHandler, userCountry, licenceHandler) {
    override fun create(adContainer: ViewGroup, baseActivity: BaseActivity): AdHandler {
        val adHandler = if (isAdDisabled) "NoOp" else {
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
}