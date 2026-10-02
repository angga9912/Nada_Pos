package com.nada.kasir.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.backup.BackupManager
import com.nada.kasir.core.backup.JenisBackup
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** File restore yang sudah dipilih dan menunggu konfirmasi (dan password, bila terenkripsi). */
data class RestoreMenunggu(val uri: Uri, val terenkripsi: Boolean)

data class BackupUiState(
    val sedangProses: Boolean = false,
    val pesan: String? = null,
    val fileBackupTerakhir: File? = null,
    val restoreMenunggu: RestoreMenunggu? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    /** Membuat backup TERENKRIPSI dengan [password] pilihan pemilik toko (F-08). */
    fun buatBackup(password: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangProses = true)
            when (val result = backupManager.backup(password)) {
                is Result.Success -> _uiState.value = BackupUiState(
                    pesan = "Backup terenkripsi berhasil dibuat: ${result.data.name}\n\n" +
                        "Catat password-nya. Tanpa password, file ini tidak bisa dibuka lagi oleh siapa pun.",
                    fileBackupTerakhir = result.data
                )
                is Result.Failure -> _uiState.value = _uiState.value.copy(
                    sedangProses = false,
                    pesan = result.error.pesan
                )
            }
        }
    }

    /**
     * Simpan file backup terakhir ke [uri] yang dipilih pengguna lewat file picker sistem
     * Android (Storage Access Framework) - lihat BackupScreen.kt. [uri] bisa mengarah ke
     * Google Drive kalau pengguna memilih "Drive" di daftar lokasi pada picker tersebut,
     * TANPA aplikasi ini perlu integrasi/API key Google Drive apa pun secara langsung.
     * File yang disalin sudah terenkripsi, jadi aman disimpan di penyimpanan online.
     */
    fun simpanBackupKeUri(context: Context, uri: Uri) {
        val file = _uiState.value.fileBackupTerakhir ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangProses = true)
            val berhasil = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().use { input -> input.copyTo(output) }
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            _uiState.value = _uiState.value.copy(
                sedangProses = false,
                pesan = if (berhasil) "Backup berhasil disimpan ke lokasi yang dipilih."
                        else "Gagal menyimpan backup ke lokasi tersebut. Silakan coba lagi."
            )
        }
    }

    /**
     * Langkah 1 restore: periksa jenis file yang dipilih. Backup terenkripsi akan diminta passwordnya
     * di dialog konfirmasi; backup .zip lama (sebelum enkripsi) tetap bisa dipulihkan tanpa password.
     */
    fun periksaFileRestore(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangProses = true)
            when (backupManager.jenisBackup(uri)) {
                JenisBackup.TERENKRIPSI -> _uiState.value = _uiState.value.copy(
                    sedangProses = false, restoreMenunggu = RestoreMenunggu(uri, terenkripsi = true)
                )
                JenisBackup.ZIP_LAMA -> _uiState.value = _uiState.value.copy(
                    sedangProses = false, restoreMenunggu = RestoreMenunggu(uri, terenkripsi = false)
                )
                JenisBackup.TIDAK_DIKENAL -> _uiState.value = _uiState.value.copy(
                    sedangProses = false, pesan = "File yang dipilih bukan file backup NADA POS."
                )
            }
        }
    }

    fun batalRestore() { _uiState.value = _uiState.value.copy(restoreMenunggu = null) }

    /** Langkah 2 restore: jalankan pemulihan. [password] hanya dipakai untuk backup terenkripsi. */
    fun restoreDariUri(uri: Uri, password: String?) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangProses = true)
            // Pembacaan, dekripsi, dan validasi seluruh isi dilakukan BackupManager SEBELUM data lama
            // disentuh - lihat komentar restore() di sana.
            when (val result = backupManager.restore(uri, password)) {
                is Result.Success -> _uiState.value = BackupUiState(
                    pesan = "Restore berhasil. Data lama (termasuk foto produk & logo toko) telah digantikan dengan data dari backup."
                )
                is Result.Failure ->
                    if (result.error === AppError.PasswordBackupSalah || result.error === AppError.PasswordBackupDiperlukan) {
                        // Biarkan dialog tetap terbuka supaya pengguna bisa mengetik ulang passwordnya.
                        _uiState.value = _uiState.value.copy(sedangProses = false, pesan = result.error.pesan)
                    } else {
                        _uiState.value = BackupUiState(pesan = result.error.pesan)
                    }
            }
        }
    }

    fun clearPesan() { _uiState.value = _uiState.value.copy(pesan = null) }
}
