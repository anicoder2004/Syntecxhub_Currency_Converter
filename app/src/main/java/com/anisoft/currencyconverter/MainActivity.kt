package com.anisoft.currencyconverter

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.anisoft.currencyconverter.data.local.CurrencyDatabase
import com.anisoft.currencyconverter.data.model.Currency
import com.anisoft.currencyconverter.data.remote.NetworkModule
import com.anisoft.currencyconverter.data.repository.CurrencyRepository
import com.anisoft.currencyconverter.data.repository.FailureReason
import com.anisoft.currencyconverter.databinding.ActivityMainBinding
import com.anisoft.currencyconverter.ui.main.CurrencyViewModel
import com.anisoft.currencyconverter.ui.selector.CurrencyAdapter
import com.anisoft.currencyconverter.ui.state.CurrencyUiState
import com.anisoft.currencyconverter.utils.Constants
import com.anisoft.currencyconverter.utils.CurrencyCatalog
import com.anisoft.currencyconverter.utils.Formatters
import com.anisoft.currencyconverter.utils.PreferencesStore
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var baseAdapter: CurrencyAdapter
    private lateinit var targetAdapter: CurrencyAdapter

    private var confirmedBase: Currency? = null
    private var confirmedTarget: Currency? = null
    private var baseJustSelected = false
    private var targetJustSelected = false

    private val viewModel: CurrencyViewModel by viewModels {
        val repository = CurrencyRepository(
            dao = CurrencyDatabase.getInstance(applicationContext).currencyDao(),
            frankfurterApi = NetworkModule.frankfurterApi,
            exchangeRateApi = NetworkModule.exchangeRateApi
        )

        CurrencyViewModel.factory(
            repository = repository,
            preferences = PreferencesStore(applicationContext),
            catalog = CurrencyCatalog.getInstance(applicationContext)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyWindowInsets()

        val catalog = CurrencyCatalog.getInstance(this)
        val currencies = catalog.currencies()

        baseAdapter = CurrencyAdapter(this, currencies)
        targetAdapter = CurrencyAdapter(this, currencies)

        setupSelector(
            layout = binding.baseCurrencyLayout,
            field = binding.baseCurrencyInput,
            adapter = baseAdapter,
            isBase = true
        )
        setupSelector(
            layout = binding.targetCurrencyLayout,
            field = binding.targetCurrencyInput,
            adapter = targetAdapter,
            isBase = false
        )

        binding.amountInput.doAfterTextChanged { editable ->
            viewModel.onAmountChanged(editable?.toString().orEmpty())
        }

        binding.swapButton.setOnClickListener {
            binding.baseCurrencyInput.clearFocus()
            binding.targetCurrencyInput.clearFocus()
            hideKeyboard(binding.amountInput)
            viewModel.onSwap()
        }

        binding.retryButton.setOnClickListener {
            viewModel.onRetry()
        }

        observeUiState()
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: CurrencyUiState) {
        when (state) {
            is CurrencyUiState.Idle -> {
                bindCurrencies(state.base, state.target)
                showRatePlaceholder(state.base, state.target)
                binding.convertedValue.text = getString(R.string.empty_value)
                binding.resultCode.text = state.target.code
                binding.statusChip.visibility = View.GONE
                binding.rateProgress.visibility = View.GONE
                binding.errorGroup.visibility = View.GONE
                binding.rateMeta.text = ""
            }

            is CurrencyUiState.Loading -> {
                bindCurrencies(state.base, state.target)
                binding.rateValue.text = getString(R.string.rate_loading)
                binding.rateMeta.text = ""
                binding.convertedValue.text = getString(R.string.empty_value)
                binding.resultCode.text = state.target.code
                binding.rateProgress.visibility = View.VISIBLE
                binding.errorGroup.visibility = View.GONE
                showStatus(
                    textRes = R.string.status_loading,
                    backgroundRes = R.color.secondary_container,
                    textColorRes = R.color.on_secondary_container
                )
            }

            is CurrencyUiState.Success -> {
                bindCurrencies(state.base, state.target)

                binding.rateValue.text = getString(
                    R.string.rate_format,
                    state.base.code,
                    Formatters.formatRate(state.rate),
                    state.target.code
                )
                binding.rateMeta.text = getString(
                    R.string.status_updated_prefix,
                    Formatters.cacheAge(this, state.rateTimestamp)
                ) + " · " + sourceLabel(state.sourceApi)

                binding.convertedValue.text = when {
                    state.convertedAmount != null -> Formatters.formatAmount(state.convertedAmount)
                    state.amountText.isBlank() -> getString(R.string.conversion_empty)
                    else -> getString(R.string.empty_value)
                }
                binding.resultCode.text = state.target.code

                when {
                    state.isStale -> showStatus(
                        textRes = R.string.status_offline,
                        backgroundRes = R.color.status_offline_container,
                        textColorRes = R.color.status_offline
                    )

                    state.isCached -> showStatus(
                        textRes = R.string.status_cached,
                        backgroundRes = R.color.secondary_container,
                        textColorRes = R.color.on_secondary_container
                    )

                    else -> showStatus(
                        textRes = R.string.status_live,
                        backgroundRes = R.color.status_live_container,
                        textColorRes = R.color.status_live
                    )
                }

                binding.rateProgress.visibility =
                    if (state.isRefreshing) View.VISIBLE else View.GONE
                binding.errorGroup.visibility = View.GONE
            }

            is CurrencyUiState.Error -> {
                bindCurrencies(state.base, state.target)
                showRatePlaceholder(state.base, state.target)
                binding.convertedValue.text = getString(R.string.empty_value)
                binding.resultCode.text = state.target.code
                binding.rateMeta.text = ""
                binding.statusChip.visibility = View.GONE
                binding.rateProgress.visibility = View.GONE
                binding.errorGroup.visibility = View.VISIBLE
                binding.errorMessage.text = errorMessage(state)
            }
        }
    }

    private fun bindCurrencies(base: Currency, target: Currency) {
        confirmedBase = base
        confirmedTarget = target

        if (!binding.baseCurrencyInput.hasFocus()) {
            binding.baseCurrencyInput.setText(base.displayName, false)
        }
        if (!binding.targetCurrencyInput.hasFocus()) {
            binding.targetCurrencyInput.setText(target.displayName, false)
        }
    }

    private fun showRatePlaceholder(base: Currency, target: Currency) {
        binding.rateValue.text = getString(R.string.rate_placeholder, base.code, target.code)
    }

    private fun errorMessage(state: CurrencyUiState.Error): String = when (state.reason) {
        FailureReason.UNSUPPORTED_PAIR -> getString(R.string.error_invalid_pair)
        FailureReason.NETWORK -> getString(R.string.error_message_default)
        FailureReason.UNKNOWN -> state.message?.takeIf { it.isNotBlank() }
            ?: getString(R.string.error_message_default)
    }

    private fun sourceLabel(sourceApi: String): String = when (sourceApi) {
        Constants.SOURCE_EXCHANGE_RATE -> getString(R.string.status_source_exchangerate)
        else -> getString(R.string.status_source_frankfurter)
    }

    private fun showStatus(
        @StringRes textRes: Int,
        @ColorRes backgroundRes: Int,
        @ColorRes textColorRes: Int
    ) {
        binding.statusChip.visibility = View.VISIBLE
        binding.statusChip.setText(textRes)
        binding.statusChip.chipBackgroundColor =
            ColorStateList.valueOf(ContextCompat.getColor(this, backgroundRes))
        binding.statusChip.setTextColor(ContextCompat.getColor(this, textColorRes))
    }

    private fun setupSelector(
        layout: TextInputLayout,
        field: MaterialAutoCompleteTextView,
        adapter: CurrencyAdapter,
        isBase: Boolean
    ) {
        field.threshold = 0
        field.setAdapter(adapter)

        layout.setEndIconOnClickListener {
            adapter.filter.filter("")
            field.showDropDown()
        }

        field.setOnClickListener {
            if (!field.isPopupShowing) {
                resetJustSelected(isBase)
                adapter.filter.filter("")
                field.post {
                    if (!isFinishing && !field.isPopupShowing) {
                        field.showDropDown()
                    }
                }
            }
        }

        field.setOnItemClickListener { parent, _, position, _ ->
            val currency = parent.getItemAtPosition(position) as? Currency
            if (currency == null || currency.code.isEmpty()) {
                restoreSelector(field, isBase)
                return@setOnItemClickListener
            }

            if (isBase) {
                confirmedBase = currency
                baseJustSelected = true
                viewModel.onBaseSelected(currency)
            } else {
                confirmedTarget = currency
                targetJustSelected = true
                viewModel.onTargetSelected(currency)
            }

            field.setText(currency.displayName, false)
            field.setSelection(field.text.length)
            hideKeyboard(field)
        }

        field.setOnDismissListener {
            if (hasJustSelected(isBase)) {
                resetJustSelected(isBase)
            } else {
                restoreSelector(field, isBase)
            }
        }

        field.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                adapter.filter.filter("")
            } else {
                if (!hasJustSelected(isBase)) {
                    restoreSelector(field, isBase)
                }
                resetJustSelected(isBase)
                hideKeyboard(field)
            }
        }

        field.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboard(field)
                true
            } else {
                false
            }
        }
    }

    private fun restoreSelector(field: MaterialAutoCompleteTextView, isBase: Boolean) {
        val currency = (if (isBase) confirmedBase else confirmedTarget) ?: return
        val displayName = currency.displayName
        if (field.text?.toString() != displayName) {
            field.setText(displayName, false)
        }
        field.setSelection(field.text.length)
    }

    private fun hasJustSelected(isBase: Boolean): Boolean =
        if (isBase) baseJustSelected else targetJustSelected

    private fun resetJustSelected(isBase: Boolean) {
        if (isBase) {
            baseJustSelected = false
        } else {
            targetJustSelected = false
        }
    }

    private fun applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun hideKeyboard(view: View) {
        val inputMethodManager =
            getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
