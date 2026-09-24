package com.nada.kasir.feature.kasir

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import com.nada.kasir.core.data.local.entity.MetodePembayaran
import com.nada.kasir.core.util.BeepPlayer
import com.nada.kasir.core.util.CurrencyFormatter
import com.nada.kasir.core.util.HandheldScannerDetector
import com.nada.kasir.feature.kasir.barcode.BarcodeScannerScreen
import com.nada.kasir.feature.produk.ProdukFormDialog
import com.nada.kasir.feature.struk.StrukPreviewDialog

/**
 * Halaman Kasir - fitur utama aplikasi (poin 4).
 * Layout mobile-first: grid produk full-width di atas, keranjang + ringkasan +
 * tombol BAYAR SELALU terbuka langsung di bagian bawah layar begitu ada item
 * (bukan lagi bar ringkas yang perlu disentuh dulu untuk dibuka) - sesuai revisi
 * desain mockup, supaya kasir bisa langsung lihat & revisi pesanan tanpa ekstra
