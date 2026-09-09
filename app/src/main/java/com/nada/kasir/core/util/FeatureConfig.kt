package com.nada.kasir.core.util

import com.nada.kasir.core.data.local.dao.SettingDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feature flags per pelanggan (poin 22 & 28). Fitur custom (hutang, supplier, multi-cabang, dll)
 * dinyalakan/dimatikan lewat tabel 'settings' TANPA mengubah core system.
 */
@Singleton
class FeatureConfig @Inject constructor(
    private val settingDao: SettingDao
) {
    suspend fun isEnabled(featureKey: String, default: Boolean = false): Boolean {
        return settingDao.get(featureKey)?.toBooleanStrictOrNull() ?: default
    }

    companion object {
        const val FEATURE_BARCODE = "feature_barcode"
        const val FEATURE_STOCK = "feature_stock"
        const val FEATURE_DISCOUNT = "feature_discount"
        const val FEATURE_PRINTER = "feature_printer"
        const val FEATURE_REPORTS = "feature_reports"
        const val FEATURE_EXCEL = "feature_excel"
        const val FEATURE_CUSTOMER = "feature_customer"
        const val FEATURE_HUTANG = "feature_hutang"
        const val FEATURE_SUPPLIER = "feature_supplier"
    }
}
