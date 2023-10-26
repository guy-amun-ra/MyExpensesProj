/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.totschnig.myexpenses.util.ads.customevent;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.DisplayMetrics;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdRequest;
import com.google.android.gms.ads.mediation.customevent.CustomEventBanner;
import com.google.android.gms.ads.mediation.customevent.CustomEventBannerListener;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitial;
import com.google.android.gms.ads.mediation.customevent.CustomEventInterstitialListener;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.di.AppComponent;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import androidx.core.util.Pair;

/**
 * A custom event for the Sample ad network. Custom events allow publishers to write their own
 * mediation adapter.
 * <p>
 * Since the custom event is not directly referenced by the Google Mobile Ads SDK and is instead
 * instantiated with reflection, it's possible that ProGuard might remove it. Use the {@link Keep}}
 * annotation to make sure that the adapter is not removed when minifying the project.
 */
@Keep
public class CustomEvent implements CustomEventBanner, CustomEventInterstitial {
  protected static final String TAG = CustomEvent.class.getSimpleName();

  /**
   * The {@link AdView} representing a banner ad.
   */
  private AdView adView;

  /**
   * Represents a {@link Interstitial}.
   */
  private Interstitial interstitial;

  /**
   * The event is being destroyed. Perform any necessary cleanup here.
   */
  @Override
  public void onDestroy() {
    if (adView != null) {
      adView.destroy();
    }
  }

  /**
   * The app is being paused. This call will only be forwarded to the adapter if the developer
   * notifies mediation that the app is being paused.
   */
  @Override
  public void onPause() {
    // The sample ad network doesn't have an onPause method, so it does nothing.
  }

  /**
   * The app is being resumed. This call will only be forwarded to the adapter if the developer
   * notifies mediation that the app is being resumed.
   */
  @Override
  public void onResume() {
    // The sample ad network doesn't have an onResume method, so it does nothing.
  }

  @Override
  public void requestBannerAd(Context context,
                              CustomEventBannerListener listener,
                              String serverParameter,
                              AdSize size,
                              MediationAdRequest mediationAdRequest,
                              Bundle customEventExtras) {

    final List<PartnerProgram> partnerPrograms = parsePrograms(serverParameter);

    final AppComponent appComponent = MyApplication.Companion.getInstance().getAppComponent();
    if (size == null || partnerPrograms.isEmpty()) {
      listener.onAdFailedToLoad(com.google.android.gms.ads.AdRequest.ERROR_CODE_INVALID_REQUEST);
    } else {
      int widthInPixels = size.getWidthInPixels(context);
      DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
      int widthInDp = Math.round(widthInPixels / displayMetrics.density);
      Pair<PartnerProgram, String> contentProvider = PartnerProgram.Companion.pickContent(partnerPrograms,
          appComponent.userCountry(), appComponent.prefHandler(), context, widthInDp);
      if (contentProvider == null) {
        listener.onAdFailedToLoad(com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL);
      } else {
        adView = new AdView(context, appComponent.prefHandler());
        // Implement a SampleAdListener and forward callbacks to mediation. The callback forwarding
        // is handled by SampleBannerEventForwarder.
        adView.setAdListener(new CustomEventForwarder(listener));

        // Make an ad request.
        adView.fetchAd(contentProvider);
      }
    }
  }

  @VisibleForTesting
  protected List<PartnerProgram> parsePrograms(String serverParameter) {
    List<PartnerProgram> result = new ArrayList<>();
    if (serverParameter != null) {
      for (String parameter : serverParameter.split(",")) {
        try {
          result.add(PartnerProgram.valueOf(parameter.trim()));
        } catch (IllegalArgumentException ignored) {
        }
      }
    }
    return result;
  }

  /**
   * Helper method to create a {@link AdRequest}.
   *
   * @param mediationAdRequest The mediation request with targeting information.
   * @return The created {@link AdRequest}.
   */
  private AdRequest createSampleRequest(MediationAdRequest mediationAdRequest) {
    AdRequest request = new AdRequest();
    request.setTestMode(mediationAdRequest.isTesting());
    request.setKeywords(mediationAdRequest.getKeywords());
    return request;
  }

  @Override
  public void requestInterstitialAd(Context context,
                                    CustomEventInterstitialListener listener,
                                    String serverParameter,
                                    MediationAdRequest mediationAdRequest,
                                    Bundle customEventExtras) {

    final List<PartnerProgram> partnerPrograms = parsePrograms(serverParameter);

    final AppComponent appComponent = MyApplication.Companion.getInstance().getAppComponent();
    if (partnerPrograms.isEmpty()) {
      listener.onAdFailedToLoad(com.google.android.gms.ads.AdRequest.ERROR_CODE_INVALID_REQUEST);
    } else {
      Pair<PartnerProgram, String> contentProvider = PartnerProgram.Companion.pickContent(partnerPrograms,
          appComponent.userCountry(), appComponent.prefHandler(), context, -1);
      if (contentProvider == null) {
        listener.onAdFailedToLoad(com.google.android.gms.ads.AdRequest.ERROR_CODE_NO_FILL);
      } else {
        interstitial = new Interstitial(context, appComponent.prefHandler());

        interstitial.setContentProvider(contentProvider);

        interstitial.setAdListener(new CustomEventForwarder(listener));

        interstitial.fetchAd();
        listener.onAdLoaded();
      }
    }
  }

  @Override
  public void showInterstitial() {
    // Show your interstitial ad.
    interstitial.show();
  }
}
