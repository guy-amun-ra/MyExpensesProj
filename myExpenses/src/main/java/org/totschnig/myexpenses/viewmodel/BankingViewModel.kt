package org.totschnig.myexpenses.viewmodel

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.kapott.hbci.GV.HBCIJob
import org.kapott.hbci.GV_Result.GVRKUms
import org.kapott.hbci.GV_Result.GVRSaldoReq
import org.kapott.hbci.callback.AbstractHBCICallback
import org.kapott.hbci.exceptions.HBCI_Exception
import org.kapott.hbci.manager.BankInfo
import org.kapott.hbci.manager.HBCIHandler
import org.kapott.hbci.manager.HBCIUtils
import org.kapott.hbci.manager.HBCIVersion
import org.kapott.hbci.passport.AbstractHBCIPassport
import org.kapott.hbci.passport.HBCIPassport
import org.kapott.hbci.status.HBCIExecStatus
import org.kapott.hbci.structures.Konto
import org.totschnig.myexpenses.BuildConfig
import org.totschnig.myexpenses.MyApplication
import org.totschnig.myexpenses.db2.addBank
import org.totschnig.myexpenses.db2.loadBanks
import org.totschnig.myexpenses.model2.Bank
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.safeMessage
import org.totschnig.myexpenses.viewmodel.data.BankingCredentials
import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.Properties

class BankingViewModel(application: Application) : ContentResolvingAndroidViewModel(application) {

    init {
        System.setProperty(
            "javax.xml.parsers.DocumentBuilderFactory",
            "org.apache.xerces.jaxp.DocumentBuilderFactoryImpl"
        )
    }

    private val hbciProperties = Properties().also {
        it["client.product.name"] = "02F84CA8EC793B72255C747B4"
        if (BuildConfig.DEBUG) {
            it["log.loglevel.default"] = HBCIUtils.LOG_INTERN.toString()
        }
    }

    private val tanFuture: CompletableDeferred<String?> = CompletableDeferred()

    private val _tanRequested = MutableLiveData(false)

    val tanRequested: LiveData<Boolean> = _tanRequested

    private val _workState: MutableStateFlow<WorkState> =
        MutableStateFlow(WorkState.Initial)
    val workState: StateFlow<WorkState> = _workState

    sealed class WorkState {
        object Initial : WorkState()
        data class Loading(val messsage: String) : WorkState()

        data class Error(val messsage: String) : WorkState()

        data class AccountsLoaded(val konten: List<Konto>) : WorkState()

        object Done : WorkState()
    }

    fun submitTan(tan: String?) {
        tanFuture.complete(tan)
        _tanRequested.postValue(false)
    }

    private fun log(msg: String) {
        Timber.tag("FinTS").i(msg)
    }

    private fun error(msg: String) {
        _workState.value = WorkState.Error(msg)
    }

    private fun initHBCI(bankingCredentials: BankingCredentials): BankInfo? {
        HBCIUtils.init(hbciProperties, MyHBCICallback(bankingCredentials))
        HBCIUtils.setParam("client.passport.default", "PinTan")
        HBCIUtils.setParam("client.passport.PinTan.init", "1")

        val info = HBCIUtils.getBankInfo(bankingCredentials.blz)

        if (info == null) {
            HBCIUtils.doneThread()
            error("${bankingCredentials.blz} not found in the list of banks that support FinTS")
        }
        return info
    }

    private fun buildPassportFile(info: BankInfo, user: String) =
        File(
            getApplication<MyApplication>().filesDir,
            "testpassport_${info.blz}_${user}.dat"
        )

    private fun buildPassport(info: BankInfo, file: File) =
        AbstractHBCIPassport.getInstance(file).apply {
            country = "DE"
            host = info.pinTanAddress
            port = 443
            filterType = "Base64"
        }

    private fun doHBCI(
        bankingCredentials: BankingCredentials,
        work: (BankInfo, HBCIPassport, HBCIHandler) -> Unit
    ) {
        viewModelScope.launch(context = coroutineContext()) {

            val info = initHBCI(bankingCredentials) ?: return@launch

            val passportFile = buildPassportFile(info, bankingCredentials.user)

            val passport = buildPassport(info, passportFile)

            val handle = try {
                HBCIHandler(HBCIVersion.HBCI_300.id, passport)

            } catch (e: Exception) {
                if (BuildConfig.DEBUG) {
                    Timber.e(e)
                }
                passport.close()
                passportFile.delete()
                HBCIUtils.doneThread()
                error(Utils.getCause(e).safeMessage)
                return@launch
            }
            try {
                work(info, passport, handle)
            } finally {
                handle.close()
                passport.close()
                HBCIUtils.doneThread()
            }
        }
    }

    fun addBank(bankingCredentials: BankingCredentials) {
        _workState.value = WorkState.Loading("Loading information")

        if (banks.value.any { it.blz == bankingCredentials.blz && it.userId == bankingCredentials.user }) {
            error("Bank has already been added")
            return
        }

        doHBCI(bankingCredentials) { info, passport, _ ->
            repository.addBank(Bank(info.blz, info.bic, info.name, bankingCredentials.user))
            val konten = passport.accounts
            if (konten == null || konten.isEmpty()) {
                error("Keine Konten ermittelbar")
            } else {
                _workState.value = WorkState.AccountsLoaded(konten.asList())
            }
        }
    }

    fun importAccounts(bankingCredentials: BankingCredentials, accounts: List<Konto>) {
        doHBCI(bankingCredentials) { _, _, handle ->
            val k = accounts[0]
            _workState.value = WorkState.Loading("Importing account ${k.iban}")
            val saldoJob: HBCIJob = handle.newJob("SaldoReq")
            saldoJob.setParam("my", k)

            saldoJob.addToQueue()

            val umsatzJob: HBCIJob = handle.newJob("KUmsAll")
            umsatzJob.setParam("my", k)

            umsatzJob.addToQueue()

            val status: HBCIExecStatus = handle.execute()

            if (!status.isOK) {
                error(status.toString())
                return@doHBCI
            }

            val saldoResult = saldoJob.jobResult as GVRSaldoReq
            if (!saldoResult.isOK) {
                error(saldoResult.toString())
                return@doHBCI
            }

            val s = saldoResult.entries[0].ready.value
            log("Saldo: $s")

            val result = umsatzJob.jobResult as GVRKUms

            if (!result.isOK) {
                error(result.toString())
                return@doHBCI
            }

            val buchungen = result.flatData
            for (buchung in buchungen) {
                val sb = StringBuilder()
                sb.append(buchung.valuta)
                val v = buchung.value
                if (v != null) {
                    sb.append(": ")
                    sb.append(v)
                }
                val zweck = buchung.usage
                if (zweck != null && zweck.size > 0) {
                    sb.append(" - ")
                    sb.append(zweck[0])
                }

                log(sb.toString())
            }
            _workState.value = WorkState.Done
        }
    }

    fun resetAddBankState() {
        _workState.value = WorkState.Initial
    }


    inner class MyHBCICallback(private val bankingCredentials: BankingCredentials) :
        AbstractHBCICallback() {
        override fun log(msg: String, level: Int, date: Date, trace: StackTraceElement) {
            Timber.tag("FinTS").d(msg)
        }

        override fun callback(
            passport: HBCIPassport?,
            reason: Int,
            msg: String,
            datatype: Int,
            retData: StringBuffer
        ) {
            Timber.tag("FinTS").i("callback:%d", reason)
            when (reason) {
                NEED_PASSPHRASE_LOAD, NEED_PASSPHRASE_SAVE -> {
                    retData.replace(0, retData.length, bankingCredentials.password!!)
                }

                NEED_PT_PIN -> retData.replace(0, retData.length, bankingCredentials.password!!)
                NEED_BLZ -> retData.replace(0, retData.length, bankingCredentials.bankLeitZahl)
                NEED_USERID -> retData.replace(0, retData.length, bankingCredentials.user)
                NEED_CUSTOMERID -> retData.replace(0, retData.length, bankingCredentials.user)
                NEED_PT_PHOTOTAN ->
                    try {
                        TODO()
                    } catch (e: Exception) {
                        throw HBCI_Exception(e)
                    }

                NEED_PT_QRTAN ->
                    try {
                        TODO()
                    } catch (e: Exception) {
                        throw HBCI_Exception(e)
                    }

                NEED_PT_SECMECH -> {
                    val options = retData.toString().split("|")
                    if (options.size > 1) {
                        Timber.e(
                            "SecMech Selection Dialog not yet implemented ().",
                            retData.toString()
                        )
                    }
                    val firstOption = options[0]
                    retData.replace(
                        0,
                        retData.length,
                        firstOption.substring(0, firstOption.indexOf(":"))
                    )
                }

                NEED_PT_TAN -> {
                    val flicker = retData.toString()
                    if (flicker.isNotEmpty()) {
                        TODO()
                    } else {
                        _tanRequested.postValue(true)
                        retData.replace(0, retData.length, runBlocking {
                            val result =
                                tanFuture.await() ?: throw HBCI_Exception("TAN entry cancelled")
                            result
                        })
                    }
                }

                NEED_PT_TANMEDIA -> {}
                HAVE_ERROR -> Timber.d(msg)
                else -> {}
            }
        }

        override fun status(passport: HBCIPassport, statusTag: Int, o: Array<Any>?) {
            Timber.tag("FinTS").i("status:%d", statusTag)
            o?.forEach {
                Timber.tag("FinTS").i(it.toString())
            }
        }
    }

    val banks: StateFlow<List<Bank>> by lazy {
        repository.loadBanks().stateIn(
            viewModelScope,
            SharingStarted.Lazily,
            emptyList()
        )
    }
}