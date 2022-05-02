package org.totschnig.myexpenses.util.licence

import android.app.Application
import android.content.Context
import com.google.android.vending.licensing.PreferenceObfuscator
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import java.util.*

class StoreLicenceHandler(context: Application, preferenceObfuscator: PreferenceObfuscator, crashHandler: CrashHandler, prefHandler: PrefHandler) :
        PlayStoreLicenceHandler(context, preferenceObfuscator, crashHandler, prefHandler) {
    override val needsKeyEntry: Boolean
        get() = true

    override val doesUseIAP: Boolean
        get() = false

    override fun getFormattedPrice(aPackage: Package) = getFormattedPriceWithExtra(aPackage, false)

    override fun getProLicenceStatus(context: Context) = getProValidUntil(context)

}