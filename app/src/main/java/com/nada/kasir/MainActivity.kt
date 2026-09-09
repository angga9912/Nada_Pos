package com.nada.kasir

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.nada.kasir.branding.BrandingViewModel
import com.nada.kasir.branding.ThemeConfig
import com.nada.kasir.navigation.NadaNavGraph

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val brandingViewModel: BrandingViewModel = hiltViewModel()
            val store by brandingViewModel.store.collectAsState()

            // Warna aplikasi mengikuti data toko - satu source code untuk banyak pelanggan (poin 2 & 21)
            val colorScheme = ThemeConfig.buatColorScheme(store?.warnaUtama ?: "#2E7D32")

            MaterialTheme(colorScheme = colorScheme) {
                Surface(modifier = Modifier) {
                    NadaNavGraph()
                }
            }
        }
    }
}
