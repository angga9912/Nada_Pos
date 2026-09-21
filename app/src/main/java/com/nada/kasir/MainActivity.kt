package com.nada.kasir

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import com.nada.kasir.branding.BrandingViewModel
import com.nada.kasir.branding.ThemeConfig
import com.nada.kasir.core.util.CrashReporter
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
                LaporanCrashDialog()
            }
        }
    }
}

/** Muncul hanya kalau aplikasi sebelumnya berhenti karena error; isinya bisa disalin & dikirim ke pengembang. */
@Composable
private fun LaporanCrashDialog() {
    val context = LocalContext.current
    var laporan by remember { mutableStateOf(CrashReporter.ambil(context)) }
    val isi = laporan ?: return

    AlertDialog(
        onDismissRequest = { },
        title = { Text("Aplikasi sempat berhenti") },
        text = {
            Column {
                Text(
                    "Salin laporan di bawah ini lalu kirim ke pengembang agar masalahnya bisa diperbaiki.",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    isi,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Laporan crash", isi))
                Toast.makeText(context, "Laporan disalin", Toast.LENGTH_SHORT).show()
            }) { Text("Salin laporan") }
        },
        dismissButton = {
            TextButton(onClick = {
                CrashReporter.hapus(context)
                laporan = null
            }) { Text("Tutup") }
        }
    )
}
