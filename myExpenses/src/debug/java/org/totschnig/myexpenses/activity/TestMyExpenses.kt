package org.totschnig.myexpenses.activity

import org.totschnig.myexpenses.provider.CheckSealedHandler

class TestMyExpenses: MyExpenses() {

    override val helpContext: String
        get() = "MyExpenses"

    lateinit var decoratedCheckSealedHandler: CheckSealedHandler

    override fun buildCheckSealedHandler() = decoratedCheckSealedHandler

    override fun maybeRepairRequerySchema() {}
}