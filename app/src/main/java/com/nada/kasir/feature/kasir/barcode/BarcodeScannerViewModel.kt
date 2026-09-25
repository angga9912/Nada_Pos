package com.nada.kasir.feature.kasir.barcode

import androidx.lifecycle.ViewModel
import com.nada.kasir.core.data.local.entity.ProductEntity
import com.nada.kasir.core.data.repository.ProductRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Pendukung BarcodeScannerScreen - HANYA untuk kartu konfirmasi visual (nama & harga
 * produk yang baru terbaca). Read-only, memakai method yang SAMA dengan
 * KasirViewModel.tambahDariBarcode (ProductRepository.cariByBarcode), supaya hasil
 * yang ditampilkan di kartu konfirmasi selalu konsisten dengan produk yang benar-benar
 * ditambahkan ke keranjang - tidak ada logic tambah-ke-keranjang yang diduplikasi di sini.
 */
@HiltViewModel
class BarcodeScannerViewModel @Inject constructor(
    private val productRepository: ProductRepository
) : ViewModel() {
    suspend fun cariProduk(barcode: String): ProductEntity? = productRepository.cariByBarcode(barcode)
}
