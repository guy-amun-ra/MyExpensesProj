package org.totschnig.myexpenses.util.licence

import android.content.SharedPreferences
import com.android.billingclient.api.Purchase
import com.google.android.vending.licensing.PreferenceObfuscator
import junitparams.JUnitParamsRunner
import junitparams.Parameters
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.db2.Repository
import org.totschnig.myexpenses.preference.PrefHandler
import org.totschnig.myexpenses.util.crashreporting.CrashHandler

@RunWith(JUnitParamsRunner::class)
class StoreLicenceHandlerTest {

    private lateinit var licenceHandler: StoreLicenceHandler

    @Before
    @Throws(Exception::class)
    fun setUp() {
        val context = mock(MyApplication::class.java)
        `when`(
            context.getSharedPreferences(
                ArgumentMatchers.any(),
                ArgumentMatchers.anyInt()
            )
        ).thenReturn(mock(SharedPreferences::class.java))
        licenceHandler = StoreLicenceHandler(
            context,
            mock(PreferenceObfuscator::class.java),
            mock(CrashHandler::class.java),
            mock(PrefHandler::class.java),
            mock(Repository::class.java)
        )
    }

    private fun m(sku: String) = mock(Purchase::class.java).also {
        `when`(it.products).thenReturn(listOf(sku))
    }

    @Test
    @Parameters(
        "sku_premium, CONTRIB",
        "sku_extended, EXTENDED",
        "sku_premium2extended, EXTENDED",
        "sku_professional, PROFESSIONAL",
        "sku_professional_monthly, PROFESSIONAL",
        "sku_professional_yearly, PROFESSIONAL",
        "sku_extended2professional, PROFESSIONAL",
        "sku_extended2professional_monthly, PROFESSIONAL",
        "sku_extended2professional_yearly, PROFESSIONAL"
    )
    fun extractLicenceStatusFromSku(sku: String, licenceStatus: String) {
        val expected = parse(licenceStatus)
        val actual = licenceHandler.extractLicenceStatusFromSku(sku)
        assertNotNull(expected)
        assertNotNull(actual)
        assertEquals(expected, actual)
    }

    @Test
    @Parameters(
        "sku_premium",
        "sku_extended",
        "sku_premium2extended",
        "sku_professional",
        "sku_professional_monthly",
        "sku_professional_yearly",
        "sku_extended2professional",
        "sku_extended2professional_monthly",
        "sku_extended2professional_yearly"
    )
    fun singleValidSkuIsHighest(provided: String) {
        val mock = m(provided)
        assertEquals(mock, licenceHandler.findHighestValidPurchase(listOf(mock)))
    }

    @Test
    fun invalidSkusAreIgnored() {
        val mock1 = m("bogus1")
        val mock2 = m("bogus2")
        val mock3 = m("sku_premium")

        assertNull(licenceHandler.findHighestValidPurchase(listOf(mock1)))
        assertNull(licenceHandler.findHighestValidPurchase(listOf(mock1, mock2)))
        assertEquals(mock3, licenceHandler.findHighestValidPurchase(listOf(mock1, mock2, mock3)))
    }

    @Test
    @Parameters
    fun upgradesAreIdentified(inventory: List<Purchase>, sku: Purchase) {
        assertEquals(sku, licenceHandler.findHighestValidPurchase(inventory))
    }

    private fun parametersForUpgradesAreIdentified(): Array<Any> {
        val premium = m("sku_premium")
        val premium2extended = m("sku_premium2extended")
        val extended2professionalMonthly = m("sku_extended2professional_monthly")
        val extended = m("sku_extended")
        val extended2professionalYearly = m("sku_extended2professional_yearly")
        return arrayOf(
            arrayOf(listOf(premium, premium2extended), premium2extended),
            arrayOf(listOf(premium2extended, premium), premium2extended),
            arrayOf(
                listOf(premium, premium2extended, extended2professionalMonthly),
                extended2professionalMonthly
            ),
            arrayOf(
                listOf(premium, extended2professionalMonthly, premium2extended),
                extended2professionalMonthly
            ),
            arrayOf(
                listOf(premium2extended, premium, extended2professionalMonthly),
                extended2professionalMonthly
            ),
            arrayOf(
                listOf(premium2extended, extended2professionalMonthly, premium),
                extended2professionalMonthly
            ),
            arrayOf(
                listOf(extended2professionalMonthly, premium, premium2extended),
                extended2professionalMonthly
            ),
            arrayOf(
                listOf(extended2professionalMonthly, premium2extended, premium),
                extended2professionalMonthly
            ),
            arrayOf(listOf(extended, extended2professionalYearly), extended2professionalYearly),
            arrayOf(listOf(extended2professionalYearly, extended), extended2professionalYearly)
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