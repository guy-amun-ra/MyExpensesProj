package org.totschnig.myexpenses.util.licence

import com.google.android.vending.licensing.PreferenceObfuscator
import junit.framework.Assert
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.crashreporting.CrashHandler
import java.util.Objects

@RunWith(JUnitParamsRunner::class)
class LicenceHandlerTest {
    private var licenceHandler: LicenceHandler? = null
    @Before
    fun setUp() {
        licenceHandler = LicenceHandler(
            Mockito.mock(MyApplication::class.java),
            Mockito.mock(PreferenceObfuscator::class.java),
            Mockito.mock(CrashHandler::class.java),
            Mockito.mock(PrefHandler::class.java),
            Mockito.mock(Repository::class.java),
            Mockito.mock(CurrencyFormatter::class.java)
        )
    }

    @After
    fun tearDown() {
        licenceHandler!!.licenceStatus = null
    }

    @Test
    @Parameters(
        "null, CONTRIB, false",
        "null, EXTENDED, false",
        "null, PROFESSIONAL, false",
        "CONTRIB, CONTRIB, true",
        "CONTRIB, EXTENDED, false",
        "CONTRIB, PROFESSIONAL, false",
        "EXTENDED, CONTRIB, true",
        "EXTENDED, EXTENDED, true",
        "EXTENDED, PROFESSIONAL, false",
        "PROFESSIONAL, CONTRIB, true",
        "PROFESSIONAL, EXTENDED, true",
        "PROFESSIONAL, PROFESSIONAL, true"
    )
    fun isEnabledFor(hasStatus: String, requestedStatus: String?, expected: Boolean) {
        licenceHandler!!.licenceStatus = parse(hasStatus)
        Assert.assertEquals(
            expected, licenceHandler!!.isEnabledFor(
                LicenceStatus.valueOf(
                    requestedStatus!!
                )
            )
        )
    }

    @Test
    @Parameters("null, true", "CONTRIB, true", "EXTENDED, true", "PROFESSIONAL, false")
    fun isUpgradeable(hasStatus: String, expected: Boolean) {
        licenceHandler!!.licenceStatus = parse(hasStatus)
        Assert.assertEquals(expected, licenceHandler!!.isUpgradeable)
    }

    @Test
    @Parameters(
        "CONTRIB, null, true",
        "CONTRIB, CONTRIB, true",
        "CONTRIB, EXTENDED, false",
        "CONTRIB, PROFESSIONAL, false",
        "EXTENDED, null, true",
        "EXTENDED, CONTRIB, true",
        "EXTENDED, EXTENDED, true",
        "EXTENDED, PROFESSIONAL, false",
        "PROFESSIONAL, null, true",
        "PROFESSIONAL, CONTRIB, true",
        "PROFESSIONAL, EXTENDED, true",
        "PROFESSIONAL, PROFESSIONAL, true"
    )
    fun greaterOrEqual(self: String, other: String, expected: Boolean) {
        Assert.assertEquals(
            expected,
            Objects.requireNonNull(parse(self)).greaterOrEqual(parse(other))
        )
    }

    private fun parse(licenceStatus: String): LicenceStatus? {
        return try {
            LicenceStatus.valueOf(licenceStatus)
        } catch (e: IllegalArgumentException) {
            null
        }
    }
}