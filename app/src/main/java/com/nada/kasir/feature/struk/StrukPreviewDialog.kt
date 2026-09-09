package com.nada.kasir.feature.struk

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Menampilkan preview struk sebelum benar-benar dicetak ke printer.
 * Layout teks memakai font monospace agar kolom kiri-kanan (nama produk,
 * harga, total) tetap rapi persis seperti hasil cetak fisik.
 */
@Composable
fun StrukPreviewDialog(
    teksStruk: String,
    sedangMencetak: Boolean,
    onCetak: () -> Unit,
    onTutup: () -> Unit
) {
    Dialog(onDismissRequest = onTutup, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Text(
                    "Preview Struk",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(20.dp, 20.dp, 20.dp, 8.dp)
                )
                Divider()

                Surface(
                    modifier = Modifier
                        .padding(20.dp)
                        .weight(1f)
                        .fillMaxWidth(),
                    color = androidx.compose.ui.graphics.Color(0xFFFAFAFA),
                    shape = RoundedCornerShape(8.dp),
                    tonalElevation = 0.dp,
                    shadowElevation = 1.dp
                ) {
                    Text(
                        text = teksStruk,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = androidx.compose.ui.graphics.Color.Black,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(20.dp, 8.dp, 20.dp, 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(onClick = onTutup, modifier = Modifier.weight(1f), enabled = !sedangMencetak) {
                        Text("Tutup")
                    }
                    Button(onClick = onCetak, modifier = Modifier.weight(1f), enabled = !sedangMencetak) {
                        Text(if (sedangMencetak) "Mencetak..." else "Cetak Sekarang")
                    }
                }
            }
        }
    }
}
