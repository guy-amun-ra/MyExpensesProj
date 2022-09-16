package org.totschnig.myexpenses.util.tracking;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;

import com.google.firebase.analytics.FirebaseAnalytics;

import org.totschnig.myexpenses.util.distrib.DistributionHelper;
import org.totschnig.myexpenses.util.Preconditions;

@Keep
public class PlatformTracker implements Tracker {
  private FirebaseAnalytics firebaseAnalytics;
  @Override
  public void init(Context context) {
    firebaseAnalytics = FirebaseAnalytics.getInstance(context);
    firebaseAnalytics.setUserProperty("Distribution", DistributionHelper.getDistribution().name());
  }

  @Override
  public void logEvent(String eventName, Bundle params) {
    Preconditions.checkNotNull(firebaseAnalytics);
    firebaseAnalytics.logEvent(eventName, params);
  }

  @Override
  public void setEnabled(boolean enabled) {
    firebaseAnalytics.setAnalyticsCollectionEnabled(enabled);
  }
}
