package org.totschnig.myexpenses.sync;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.material.snackbar.Snackbar;

import org.totschnig.myexpenses.R;
import org.totschnig.myexpenses.activity.DriveSetup2;
import org.totschnig.myexpenses.activity.ManageSyncBackends;
import org.totschnig.myexpenses.activity.ProtectedFragmentActivity;
import org.totschnig.myexpenses.util.Result;

import java.io.Serializable;
import java.util.Locale;

import androidx.annotation.NonNull;

import static org.totschnig.myexpenses.activity.ConstantsKt.SYNC_BACKEND_SETUP_REQUEST;

public class GoogleDriveBackendProviderFactory extends SyncBackendProviderFactory {
  public static final String LABEL = "Drive";

  @NonNull
  @Override
  protected SyncBackendProvider fromAccount(Context context, Account account, AccountManager accountManager) throws SyncBackendProvider.SyncParseException {
    return new GoogleDriveBackendProvider(context, account, accountManager);
  }

  @NonNull
  @Override
  public String getLabel() {
    return LABEL;
  }

  @Override
  public void startSetup(ProtectedFragmentActivity activity) {
    GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
    int result = googleApiAvailability.isGooglePlayServicesAvailable(activity);
    if (result == ConnectionResult.SUCCESS) {
      activity.startActivityForResult(new Intent(activity, DriveSetup2.class),
          SYNC_BACKEND_SETUP_REQUEST);
    } else if (googleApiAvailability.isUserResolvableError(result)) {
      googleApiAvailability.getErrorDialog(activity, result, 0).show();
    } else {
      activity.showSnackbar(String.format(Locale.ROOT, "Google Play Services error %d", result), Snackbar.LENGTH_LONG);
    }
  }

  @Override
  public boolean isEnabled(Context context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) return false;
    GoogleApiAvailability googleApiAvailability = GoogleApiAvailability.getInstance();
    int result = googleApiAvailability.isGooglePlayServicesAvailable(context);
    return result == ConnectionResult.SUCCESS || googleApiAvailability.isUserResolvableError(result);
  }

  @Override
  public int getId() {
    return R.id.SYNC_BACKEND_DRIVE;
  }

  @Override
  public Intent getRepairIntent(Activity activity) {
    return null;
  }

  @Override
  public boolean startRepairTask(ManageSyncBackends activity, Intent data) {
    return false;
  }

  @Override
  public Result handleRepairTask(Serializable mExtra) {
   return null;
  }

  @Override
  public void init() {

  }
}
