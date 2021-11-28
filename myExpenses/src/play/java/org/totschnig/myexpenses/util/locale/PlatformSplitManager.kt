package org.totschnig.myexpenses.util.locale

import android.app.Activity
import android.content.Context
import androidx.annotation.Keep
import com.google.android.play.core.splitcompat.SplitCompat
import com.google.android.play.core.splitinstall.SplitInstallManager
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory
import com.google.android.play.core.splitinstall.SplitInstallRequest
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.MyApplication.DEFAULT_LANGUAGE
import org.totschnig.myexpenses.activity.BaseActivity
import org.totschnig.myexpenses.feature.Callback
import org.totschnig.myexpenses.feature.Feature
import org.totschnig.myexpenses.feature.FeatureManager
import org.totschnig.myexpenses.feature.getUserConfiguredMlkitScriptModule
import org.totschnig.myexpenses.feature.getUserConfiguredOcrEngine
import org.totschnig.myexpenses.preference.PrefHandler
import timber.log.Timber
import java.util.*

@Keep
class PlatformSplitManager(private val userLocaleProvider: UserLocaleProvider, private val prefHandler: PrefHandler) : FeatureManager() {
    private lateinit var manager: SplitInstallManager
    private var mySessionId = 0
    var listener: SplitInstallStateUpdatedListener? = null

    override fun initApplication(application: MyApplication) {
        super.initApplication(application)
        SplitCompat.install(application)
        manager = SplitInstallManagerFactory.create(application)
    }

    override fun initActivity(activity: Activity) {
        SplitCompat.installActivity(activity)
    }

    override fun requestLocale(context: Context) {
        val userLanguage = userLocaleProvider.getPreferredLanguage()
        if (userLanguage == DEFAULT_LANGUAGE) {
            Timber.i("userLanguage == DEFAULT_LANGUAGE")
            super.requestLocale(context)
        } else {
            val userPreferredLocale = userLocaleProvider.getUserPreferredLocale()
            if (userPreferredLocale.language.equals("en") ||
                    manager.installedLanguages.contains(userPreferredLocale.language)) {
                Timber.i("Already installed")
                callback?.onLanguageAvailable()
            } else {
                callback?.onAsyncStartedLanguage(userPreferredLocale.displayLanguage)
                val request = SplitInstallRequest.newBuilder()
                        .addLanguage(userPreferredLocale)
                        .build()
                manager.startInstall(request)
                        .addOnSuccessListener { sessionId -> mySessionId = sessionId }
                        .addOnFailureListener { exception -> callback?.onError(exception) }

            }
        }
    }

    override fun registerCallback(callback: Callback) {
        super.registerCallback(callback)
        listener = SplitInstallStateUpdatedListener { state ->
            if (state.sessionId() == mySessionId) {
                if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                    if (state.languages().size > 0) {
                        this.callback?.onLanguageAvailable()
                    }
                    if (state.moduleNames().size > 0) {
                        this.callback?.onFeatureAvailable(state.moduleNames())
                    }
                }
            }
        }.also { manager.registerListener(it) }
    }

    override fun unregister() {
        super.unregister()
        listener?.let { manager.unregisterListener(it) }
    }

    override fun isFeatureInstalled(feature: Feature, context: Context) =
             areModulesInstalled(feature, context) && super.isFeatureInstalled(feature, context)

    private fun areModulesInstalled(feature: Feature, context: Context) = isModuleInstalled(feature) &&
            subFeatures(feature, context).all { isModuleInstalled(it) }

    private fun isModuleInstalled(feature: Feature) = manager.installedModules.contains(feature.moduleName)

    override fun requestFeature(feature: Feature, activity: BaseActivity) {
        val isModuleInstalled = isModuleInstalled(feature)
        val subFeatureToInstall = subFeatures(feature, activity).filter { !isModuleInstalled(it) }
        if (isModuleInstalled && subFeatureToInstall.isEmpty()) {
            super.requestFeature(feature, activity)
        } else {
            callback?.onAsyncStartedFeature(feature)
            val request = SplitInstallRequest
                    .newBuilder()
                    .apply {
                        if (!isModuleInstalled) {
                            addModule(feature.moduleName)
                        }
                        subFeatureToInstall.forEach { addModule(it.moduleName) }
                    }
                    .build()

            manager.startInstall(request)
                    .addOnSuccessListener { sessionId -> mySessionId = sessionId }
                    .addOnFailureListener { exception ->
                        callback?.onError(exception)
                    }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun subFeatures(feature: Feature, context: Context) = buildList<Feature> {
            if (feature == Feature.OCR) {
                getUserConfiguredOcrEngine(context, prefHandler).also {
                    add(it)
                    if (it == Feature.MLKIT) {
                        add(getUserConfiguredMlkitScriptModule(context, prefHandler))
                    }
                }
            }
        }

    override fun installedFeatures() = manager.installedModules

    override fun installedLanguages() = manager.installedLanguages

    override fun uninstallFeatures(features: Set<String>) {
        manager.deferredUninstall(features.toList())
    }

    override fun uninstallLanguages(languages: Set<String>) {
        manager.deferredLanguageUninstall(languages.map { language -> Locale(language) })
    }

    override fun allowsUninstall() = true
}