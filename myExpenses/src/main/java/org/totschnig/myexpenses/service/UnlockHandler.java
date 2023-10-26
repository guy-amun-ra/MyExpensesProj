package org.totschnig.myexpenses.service;

import static org.totschnig.myexpenses.util.TextUtils.concatResStrings;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Message;

import org.totschnig.myexpenses.MyApplication;
import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.MyExpenses;
import org.totschnig.myexpenses.util.NotificationBuilderWrapper;
import org.totschnig.myexpenses.util.distrib.DistributionHelper;
import org.totschnig.myexpenses.util.licence.LicenceHandler;

import timber.log.Timber;

/**
 * This handler is used in <s>two different</s> one scenario<s>s</s>:
 * 1) MyExpensesContrib calls MyExpenses.MyService when having retrieved the license status and posts a message to this handler
 * 2) <s>MyExpenses.MyApplication onStartup calls MyExpensesContrib.MyService and sets this handler as replyTo to retrieve the license status</s>
 * <s>this handler is subclassed in MyApplication, so that we can handle unbinding from the service there</s>
 */
public class UnlockHandler extends Handler {
  private static final int STATUS_TEMPORARY = 3;
  private static final int STATUS_PERMANENT = 4;
  private static final int STATUS_FINAL = 7;

  @Override
  public void handleMessage(Message msg) {
    MyApplication app = MyApplication.Companion.getInstance();
    Timber.i("Now handling answer from license verification service; got status %d.", msg.what);
    switch (msg.what) {
      case STATUS_FINAL:
        doUnlock(msg.what);
        break;
      case STATUS_TEMPORARY:
      case STATUS_PERMANENT:
        if (!DistributionHelper.isPlay()) {
          doUnlock(msg.what);
        } else {
          showNotif(concatResStrings(app, " ", R.string.licence_validation_failure,
              R.string.licence_validation_upgrade_needed));
        }
        break;
    }
  }

  private void doUnlock(int status) {
    MyApplication app = MyApplication.Companion.getInstance();
    LicenceHandler licenceHandler = app.getLicenceHandler();
    boolean unlocked = licenceHandler.registerUnlockLegacy();
    if (unlocked) {
      showNotif(String.format("%s (%s) %s", app.getString(R.string.licence_validation_premium),
          app.getString(licenceHandler.getLicenceStatus().getResId()), app.getString(R.string.thank_you)));
    }
  }

  private void showNotif(String text) {
    MyApplication app = MyApplication.Companion.getInstance();
    NotificationManager notificationManager =
        (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
    String title = concatResStrings(app, " ", R.string.app_name, R.string.contrib_key);
    //noinspection InlinedApi
    NotificationBuilderWrapper builder =
            NotificationBuilderWrapper.defaultBigTextStyleBuilder(app, title, text)
            .setContentIntent(PendingIntent.getActivity(app, 0, new Intent(app, MyExpenses.class), PendingIntent.FLAG_IMMUTABLE));
    Notification notification = builder.build();
    notification.flags = Notification.FLAG_AUTO_CANCEL;
    notificationManager.notify(0, notification);
  }
}
