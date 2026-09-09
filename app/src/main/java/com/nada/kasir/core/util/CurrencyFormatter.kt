package com.nada.kasir.core.util

import java.text.NumberFormat
import java.util.*

object CurrencyFormatter {
    /**
     * Format angka menjadi mata uang. Simbol mata uang diambil dari StoreEntity.mataUang
     * (jangan hardcode "Rp" di layer UI, meski default-nya "Rp").
     */
    fun format(amount: Double, simbol: String = "Rp"): String {
        val nf = NumberFormat.getNumberInstance(Locale("in", "ID"))
        nf.maximumFractionDigits = 0
        return "$simbol ${nf.format(amount)}"
    }
}
