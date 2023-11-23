package org.totschnig.myexpenses.test.screenshots

import androidx.test.filters.LargeTest
import org.junit.Test

//requires shell settings put global hidden_api_policy 1
@LargeTest
class JapaneseTest: TestMain("ja-JP") {

    @Test
    fun runJapanese() {
        runScenario("1")
    }
}