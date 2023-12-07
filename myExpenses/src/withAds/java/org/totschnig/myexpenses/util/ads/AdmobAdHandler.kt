package org.totschnig.myexpenses.util.ads

import android.os.Build
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.activity.BaseActivity
import timber.log.Timber

internal class AdmobAdHandler(factory: AdHandlerFactory, adContainer: ViewGroup, baseActivity: BaseActivity, private val bannerUnitId: Int, private val interstitialUnitId: Int) : BaseAdHandler(factory, adContainer, baseActivity) {
    private var admobView: AdView? = null
    private var admobInterstitialAd: InterstitialAd? = null
    private var mAdMobBannerShown = false
    private var mInterstitialShown = false
    private var interstitialCounter = 0

    override val shouldHideAd: Boolean
        get() = super.shouldHideAd ||
                ((Build.VERSION.SDK_INT in (Build.VERSION_CODES.S..Build.VERSION_CODES.S_V2)) &&
                        (Build.MODEL == "Redmi Note 9"))


    override fun startBannerInternal() {
        showBannerAdmob()
    }

    private fun showBannerAdmob() {
        if (bannerUnitId == 0) {
            hide()
            return
        }
        admobView = AdView(activity).apply {
            setAdSize(calculateAdSize)
            adUnitId = context.getString(if (isTest) R.string.admob_unitid_test_banner else bannerUnitId)
            adContainer.addView(this)
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            adListener = object : AdListener() {
                override fun onAdLoaded() {
                    trackBannerLoaded(PROVIDER_ADMOB)
                    mAdMobBannerShown = true
                    visibility = View.VISIBLE
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Timber.w(error.toString())
                    trackBannerFailed(PROVIDER_ADMOB, error.code.toString())
                    hide()
                }
            }
            loadAd(buildAdmobRequest())
        }
        trackBannerRequest(PROVIDER_ADMOB)
    }

    private val calculateAdSize: AdSize
        get() {
            val display = activity.windowManager.defaultDisplay
            val outMetrics = DisplayMetrics()
            display.getMetrics(outMetrics)
            val density = outMetrics.density
            val adWidth = (adContainer.width / density).toInt()
            return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth)
        }

    private fun buildAdmobRequest() = AdRequest.Builder().build()

    private val isTest: Boolean
        get() = BuildConfig.DEBUG

    override fun requestNewInterstitialDo() {
        mInterstitialShown = false
        interstitialCounter++
        //every 20th attempt (starting from 5) we try the non-smart unit
        val adUnitId = activity.getString(
            if (isTest) R.string.admob_unitid_test_interstitial else interstitialUnitId
        )
        trackInterstitialRequest(PROVIDER_ADMOB)
        InterstitialAd.load(activity, adUnitId, buildAdmobRequest(), object: InterstitialAdLoadCallback() {
            override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                trackInterstitialFailed(PROVIDER_ADMOB, loadAdError.code.toString())
                onInterstitialFailed()
            }

            override fun onAdLoaded(ad: InterstitialAd) {
                trackInterstitialLoaded(PROVIDER_ADMOB)
                admobInterstitialAd = ad
            }
        })
    }

    override fun maybeShowInterstitialDo() = if (mInterstitialShown) false else
        admobInterstitialAd?.let {
            trackInterstitialShown(PROVIDER_ADMOB)
            MobileAds.setAppVolume(0f)
            it.show(activity)
            mInterstitialShown = true
            true
        } ?: false

    override fun onResume() {
        if (mAdMobBannerShown) {
            //activity might have been resumed after user has bought contrib key
            if (shouldHideAd) {
                admobView?.destroy()
                hide()
                mAdMobBannerShown = false
            } else {
                admobView?.resume()
            }
        }
    }

    override fun onDestroy() {
        if (mAdMobBannerShown) {
            admobView?.destroy()
            mAdMobBannerShown = false
        }
    }

    override fun onPause() {
        if (mAdMobBannerShown) {
            admobView?.pause()
        }
    }

    companion object {
        private const val PROVIDER_ADMOB = "Admob"
    }
}