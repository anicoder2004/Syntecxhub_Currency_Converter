package com.anisoft.currencyconverter.ui.selector

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.anisoft.currencyconverter.R
import com.anisoft.currencyconverter.data.model.Currency
import java.util.Locale

class CurrencyAdapter(
    context: Context,
    private val allCurrencies: List<Currency>
) : ArrayAdapter<Currency>(context, R.layout.item_currency) {

    private val inflater = LayoutInflater.from(context)
    private val noResultsItem = Currency(
        code = "",
        name = context.getString(R.string.no_currencies_found),
        symbol = ""
    )

    private var visibleCurrencies: List<Currency> = allCurrencies

    override fun getCount(): Int = visibleCurrencies.size

    override fun getItem(position: Int): Currency? = visibleCurrencies.getOrNull(position)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_currency, parent, false)
        val item = visibleCurrencies[position]

        val codeView = view.findViewById<TextView>(R.id.textCode)
        val nameView = view.findViewById<TextView>(R.id.textName)
        val symbolView = view.findViewById<TextView>(R.id.textSymbol)

        if (item.code.isEmpty()) {
            codeView.visibility = View.GONE
            symbolView.visibility = View.GONE
            nameView.text = item.name
        } else {
            codeView.visibility = View.VISIBLE
            codeView.text = item.code
            nameView.text = item.name
            symbolView.visibility = if (item.symbol.isBlank()) View.GONE else View.VISIBLE
            symbolView.text = item.symbol
        }

        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View =
        getView(position, convertView, parent)

    override fun getFilter(): Filter = currencyFilter

    private val currencyFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val query = constraint?.toString()?.trim()?.lowercase(Locale.ROOT).orEmpty()

            val matches = if (query.isEmpty()) {
                allCurrencies
            } else {
                allCurrencies.filter { it.searchKey.contains(query) }
            }

            val values = if (matches.isEmpty()) listOf(noResultsItem) else matches

            return FilterResults().apply {
                this.values = values
                this.count = values.size
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            visibleCurrencies = results?.values as? List<Currency> ?: emptyList()
            notifyDataSetChanged()
        }
    }
}
