package org.totschnig.myexpenses.util.licence

import android.app.Application
import android.content.SharedPreferences
import com.google.android.vending.licensing.PreferenceObfuscator
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.prefHandler
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

class BaseStoreLicenceHandlerTest {
    private lateinit var licenceHandler: StoreLicenceHandler

    @Before
    fun setUp() {
        val application = Mockito.mock(Application::class.java)
        Mockito.`when`(
            application.getSharedPreferences(
                ArgumentMatchers.any(),
                ArgumentMatchers.anyInt()
            )
        ).thenReturn(
            Mockito.mock(
                SharedPreferences::class.java
            )
        )

        whenever(prefHandler.getKey(any())).thenReturn("key")
        val obfuscator = Mockito.mock(
            PreferenceObfuscator::class.java
        )
        Mockito.`when`(obfuscator.getString(ArgumentMatchers.any(), ArgumentMatchers.any()))
            .thenReturn("0")
        licenceHandler = StoreLicenceHandler(
            application,
            obfuscator,
            Mockito.mock(CrashHandler::class.java),
            prefHandler,
            Mockito.mock(Repository::class.java),
            Mockito.mock(CurrencyFormatter::class.java)
        )
    }

    @Test
    fun contribPurchaseShouldBeRegistered() {
        licenceHandler.registerPurchase(false)
        Assert.assertTrue(licenceHandler.isContribEnabled)
        Assert.assertFalse(licenceHandler.isExtendedEnabled)
    }

    @Test
    fun extendedPurchaseShouldBeRegistered() {
        licenceHandler.registerPurchase(true)
        Assert.assertTrue(licenceHandler.isContribEnabled)
        Assert.assertTrue(licenceHandler.isExtendedEnabled)
    }

    @Test
    fun legacyUnlockShouldBeRegistered() {
        licenceHandler.registerUnlockLegacy()
        Assert.assertTrue(licenceHandler.isContribEnabled)
        Assert.assertFalse(licenceHandler.isExtendedEnabled)
    }
}