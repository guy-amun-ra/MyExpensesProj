package org.totschnig.myexpenses.activity

import android.content.Intent
import android.app.Activity
import android.content.Context
import android.net.Uri
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import android.widget.Toast
import org.totschnig.myexpenses.util.safeMessage
import java.lang.Exception

interface ViewIntentProvider {
    fun getViewIntent(context: Context, uri: Uri): Intent
    fun startViewAction(activity: Activity, uri: Uri) {
        try {
            activity.startActivity(getViewIntent(activity, uri))
        } catch (e: Exception) {
            CrashHandler.report(e, "uri", uri.toString())
            Toast.makeText(activity, e.safeMessage, Toast.LENGTH_LONG).show()
        }
    }
}