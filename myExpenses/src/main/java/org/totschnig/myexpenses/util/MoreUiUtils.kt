package org.totschnig.myexpenses.util

import android.content.Context
import android.graphics.Color
import android.view.View
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils.calculateContrast
import com.google.android.material.chip.ChipGroup
import org.totschnig.myexpenses.R
import org.totschnig.myexpenses.model.AccountType
import org.totschnig.myexpenses.model.Grouping
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.preference.PrefKey
import org.totschnig.myexpenses.ui.filter.ScrollingChip
import org.totschnig.myexpenses.util.UiUtils.DateMode
import org.totschnig.myexpenses.viewmodel.data.FullAccount
import java.text.SimpleDateFormat
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun <T> ChipGroup.addChipsBulk(chips: Iterable<T>, closeFunction: ((T) -> Unit)? = null) {
    removeAllViews()
    for (chip in chips) {
        addView(ScrollingChip(context).also { scrollingChip ->
            scrollingChip.text = chip.toString()
            closeFunction?.let {
                scrollingChip.isCloseIconVisible = true
                scrollingChip.setOnCloseIconClickListener {
                    removeView(scrollingChip)
                    it(chip)
                }
            }
        })
    }
}

fun ScrollView.postScrollToBottom() {
    post {
        fullScroll(View.FOCUS_DOWN)
    }
}

fun setNightMode(prefHandler: PrefHandler, context: Context) {
    AppCompatDelegate.setDefaultNightMode(
        when (prefHandler.getString(
            PrefKey.UI_THEME_KEY,
            context.getString(R.string.pref_ui_theme_default)
        )) {
            "dark" -> AppCompatDelegate.MODE_NIGHT_YES
            "light" -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
    )
}

fun getBestForeground(color: Int) =
    arrayOf(Color.BLACK, Color.WHITE).maxByOrNull { calculateContrast(color, it) }!!

inline fun <reified E : Enum<E>> getEnumFromPreferencesWithDefault(
    prefHandler: PrefHandler,
    prefKey: PrefKey,
    defaultValue: E
) = enumValueOrDefault(prefHandler.getString(prefKey, null), defaultValue)

fun <T : View> findParentWithTypeRecursively(view: View, type: Class<T>): T? {
    if (type.isInstance(view)) {
        @Suppress("UNCHECKED_CAST")
        return view as T
    }
    val parent = view.parent
    return if (parent is View) findParentWithTypeRecursively(parent as View, type) else null
}

fun getDateMode(accountType: AccountType?, prefHandler: PrefHandler) = when {
    (accountType == null || accountType != AccountType.CASH) &&
            prefHandler.getBoolean(PrefKey.TRANSACTION_WITH_VALUE_DATE, false)
    -> DateMode.BOOKING_VALUE
    prefHandler.getBoolean(PrefKey.TRANSACTION_WITH_TIME, true) -> DateMode.DATE_TIME
    else -> DateMode.DATE
}

private fun timeFormatter(accountType: AccountType?, prefHandler: PrefHandler, context: Context) =
    if (getDateMode(accountType, prefHandler) == DateMode.DATE_TIME) {
        android.text.format.DateFormat.getTimeFormat(context) as SimpleDateFormat
    } else null

private val SimpleDateFormat.asDateTimeFormatter: DateTimeFormatter
    get() = DateTimeFormatter.ofPattern(this.toPattern())

fun dateTimeFormatter(account: FullAccount, prefHandler: PrefHandler, context: Context) =
    when (account.grouping) {
        Grouping.DAY -> timeFormatter(account.type, prefHandler, context)?.asDateTimeFormatter
        else -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    }

fun dateTimeFormatterLegacy(account: FullAccount, prefHandler: PrefHandler, context: Context) =
    when (account.grouping) {
        Grouping.DAY -> timeFormatter(account.type, prefHandler, context)
        Grouping.MONTH ->
            if (prefHandler.getString(PrefKey.GROUP_MONTH_STARTS, "1")!!.toInt() == 1) {
                SimpleDateFormat("dd", Utils.localeFromContext(context))
            } else {
                Utils.localizedYearLessDateFormat(context)
            }
        Grouping.WEEK -> SimpleDateFormat("EEE", Utils.localeFromContext(context))
        Grouping.YEAR -> Utils.localizedYearLessDateFormat(context)
        Grouping.NONE -> Utils.ensureDateFormatWithShortYear(context)
    }?.let { (it as SimpleDateFormat).asDateTimeFormatter }