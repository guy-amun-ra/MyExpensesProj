package org.totschnig.myexpenses.service

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.ui.ContextHelper
import org.totschnig.myexpenses.util.NotificationBuilderWrapper
import org.totschnig.myexpenses.util.TextUtils

abstract class BaseWorker(context: Context, workerParameters: WorkerParameters) :
    CoroutineWorker(context, workerParameters) {
    val notificationManager: NotificationManager
        get() = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    val wrappedContext: Context
    val prefHandler: PrefHandler

    init {
        val app = context.applicationContext as MyApplication
        wrappedContext = app.wrapContext(context)
        prefHandler = app.appComponent.prefHandler()
    }

    private val notificationTitle: String
        get() = TextUtils.concatResStrings(
            wrappedContext,
            " ",
            R.string.app_name,
            notificationTitleResId
        )

    abstract val notificationTitleResId: Int
    abstract val channelId: String
    abstract val notificationId: Int

    protected fun buildMessage(message: CharSequence): NotificationBuilderWrapper =
        NotificationBuilderWrapper.bigTextStyleBuilder(applicationContext,
            channelId, notificationTitle, message)

    protected fun notify(notification: Notification) {
        notificationManager.notify(notificationId, notification)
    }
}