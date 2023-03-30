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
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.feature.*
import org.totschnig.myexpenses.preference.PrefHandler
import timber.log.Timber
import java.util.*

@Keep
class PlatformSplitManager(
    private val prefHandler: PrefHandler
) : FeatureManager() {
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

    override fun requestLocale(language: String) {
        Timber.i("requestLocale %s", language)
        if (language == "en" ||
            manager.installedLanguages.contains(language)
        ) {
            Timber.i("Already installed")
            callback?.onLanguageAvailable(language)
        } else {
            callback?.onAsyncStartedLanguage(language)
            val request = SplitInstallRequest.newBuilder()
                .addLanguage(Locale.forLanguageTag(language))
                .build()
            Timber.i("startInstall")
            manager.startInstall(request)
                .addOnSuccessListener { sessionId -> mySessionId = sessionId }
                .addOnFailureListener { exception -> callback?.onError(exception) }

        }
    }

    override fun registerCallback(callback: Callback) {
        super.registerCallback(callback)
        listener = SplitInstallStateUpdatedListener { state ->
            if (state.sessionId() == mySessionId) {
                if (state.status() == SplitInstallSessionStatus.INSTALLED) {
                    if (state.languages().size > 0) {
                        this.callback?.onLanguageAvailable(state.languages().first())
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

    private fun areModulesInstalled(feature: Feature, context: Context) =
        isModuleInstalled(feature) &&
                subFeatures(feature, context).all { isModuleInstalled(it) }

    private fun isModuleInstalled(feature: Feature) =
        manager.installedModules.contains(feature.moduleName)

    override fun requestFeature(feature: Feature, context: Context) {
        val isModuleInstalled = isModuleInstalled(feature)
        val subFeatureToInstall = subFeatures(feature, context).filter { !isModuleInstalled(it) }
        if (isModuleInstalled && subFeatureToInstall.isEmpty()) {
            super.requestFeature(feature, context)
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

    private fun subFeatures(feature: Feature, context: Context) = buildList {
        if (feature == Feature.OCR) {
            getUserConfiguredOcrEngine(context, prefHandler).also {
                add(it)
                if (it == Feature.MLKIT) {
                    add(getUserConfiguredMlkitScriptModule(context, prefHandler))
                }
            }
        }
    }

    private val installedModules: Set<String>
        get() = if (BuildConfig.DEBUG) Feature.values()
            .mapTo(mutableSetOf()) { it.moduleName } else manager.installedModules

    override fun installedFeatures(
        context: Context,
        prefHandler: PrefHandler,
        onlyUninstallable: Boolean
    ) =
        with(installedModules) {
            if (onlyUninstallable) this.filterTo(mutableSetOf()) {
                Feature.fromModuleName(it)?.canUninstall(context, prefHandler) ?: false
            }
            else this
        }

    override fun installedLanguages(): MutableSet<String> = manager.installedLanguages

    override fun uninstallFeatures(features: Set<String>) {
        manager.deferredUninstall(features.toList())
    }

    override fun uninstallLanguages(languages: Set<String>) {
        manager.deferredLanguageUninstall(languages.map { language -> Locale(language) })
    }

    override fun allowsUninstall() = true
}