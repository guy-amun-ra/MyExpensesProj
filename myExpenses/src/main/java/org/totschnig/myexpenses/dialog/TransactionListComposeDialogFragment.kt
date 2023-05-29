package org.totschnig.myexpenses.dialog

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.LocalTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import org.totschnig.myexpenses.compose.CompactTransactionRenderer
import org.totschnig.myexpenses.injector
import org.totschnig.myexpenses.util.CurrencyFormatter
import org.totschnig.myexpenses.util.Utils
import org.totschnig.myexpenses.util.asDateTimeFormatter
import org.totschnig.myexpenses.util.convAmount
import org.totschnig.myexpenses.viewmodel.KEY_LOADING_INFO
import org.totschnig.myexpenses.viewmodel.TransactionListViewModel
import org.totschnig.myexpenses.viewmodel.data.IIconInfo
import java.text.SimpleDateFormat
import javax.inject.Inject

class TransactionListComposeDialogFragment: ComposeBaseDialogFragment() {

    @Inject
    lateinit var currencyFormatter: CurrencyFormatter

    val viewModel by viewModels<TransactionListViewModel>()


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(requireActivity().injector) {
            inject(this@TransactionListComposeDialogFragment)
            inject(viewModel)
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.sum.collect {
                    val title = viewModel.loadingInfo.label + TABS +
                            currencyFormatter.convAmount(it, viewModel.loadingInfo.currency)
                    dialog!!.setTitle(title)
                }
            }
        }
    }

    override fun initBuilder(): AlertDialog.Builder = super.initBuilder().apply {
        setTitle(viewModel.loadingInfo.label)
        setPositiveButton(android.R.string.ok, null)
        viewModel.loadingInfo.icon?.let {
            IIconInfo.resolveIcon(it)?.asDrawable(requireContext())
        }?.let { setIcon(it) }
    }

    @Composable
    override fun BuildContent() {
        val data = viewModel.transactions.collectAsState(initial = emptyList())
        val renderer = CompactTransactionRenderer(
            dateTimeFormatInfo = Pair(
                (Utils.ensureDateFormatWithShortYear(context) as SimpleDateFormat).asDateTimeFormatter,
                with(LocalDensity.current) {
                    LocalTextStyle.current.fontSize.toDp()
                } * 4.6f
            ),
            withCategoryIcon = false
        )
        LazyColumn(modifier = Modifier.padding(top = dialogPadding, start = dialogPadding, end = dialogPadding)) {
            data.value.forEach {
                item {
                    Row(modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .clickable {
                            showDetails(it.parentId ?: it.id)
                        }
                    ) {
                        with(renderer) {
                            RenderInner(transaction = it)
                        }
                    }
                }
            }
        }
    }

    companion object {
        private const val TABS = "\u0009\u0009\u0009\u0009"

        @JvmStatic
        fun newInstance(
            loadingInfo: TransactionListViewModel.LoadingInfo
        ) = TransactionListComposeDialogFragment().apply {
            arguments = Bundle().apply {
                putParcelable(KEY_LOADING_INFO, loadingInfo)
            }
        }
    }
}