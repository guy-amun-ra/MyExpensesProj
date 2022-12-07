package org.totschnig.myexpenses.util.distrib

import android.content.Context
import android.text.format.DateUtils
import androidx.annotation.Keep
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewInfo
import com.google.android.play.core.review.ReviewManagerFactory
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.util.Utils

@Keep
class PlatformReviewManager(val prefHandler: PrefHandler): ReviewManager {
    private lateinit var reviewManager: com.google.android.play.core.review.ReviewManager
    private var reviewInfo: Deferred<ReviewInfo>? = null
    override fun init(context: Context) {
        reviewManager = ReviewManagerFactory.create(context)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun onEditTransactionResult(activity: FragmentActivity) {
        reviewInfo?.apply {
            if (isCompleted && !isCancelled) {
                getCompleted().also {
                    activity.lifecycleScope.launchWhenResumed { reviewManager.launchReview(activity, it) }
                    prefHandler.putLong(PrefKey.LAST_REQUEST_RATE_PLAY, System.currentTimeMillis())
                    prefHandler.putInt(PrefKey.REQUEST_RATE_PLAY_OFFSET_DAYS, prefHandler.getInt(PrefKey.REQUEST_RATE_PLAY_OFFSET_DAYS, OFFSET_START) + OFFSET_START)
                    reviewInfo = null
                }
            }
        } ?: run {
            if (shouldShow(activity)) {
                reviewInfo = activity.lifecycleScope.async { reviewManager.requestReview() }
            }
        }
    }

    private fun shouldShow(activity: Context): Boolean {
        val lastReminder = prefHandler.getLong(PrefKey.LAST_REQUEST_RATE_PLAY, 0L)
        val nextReminder = if (lastReminder == 0L) {
            val nextReminderLegacy = prefHandler.getLong(PrefKey.NEXT_REMINDER_RATE, 0L)
            nextReminderLegacy.takeIf { it > 0L } ?: (when (nextReminderLegacy) {
                0L -> Utils.getInstallTime(activity)
                else -> System.currentTimeMillis()
            } + DateUtils.DAY_IN_MILLIS * 30)
        } else lastReminder + DateUtils.DAY_IN_MILLIS * prefHandler.getInt(PrefKey.REQUEST_RATE_PLAY_OFFSET_DAYS, OFFSET_START)
        return nextReminder < System.currentTimeMillis()
    }
    companion object {
        const val OFFSET_START = 30
    }
}