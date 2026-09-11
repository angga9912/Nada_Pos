package com.nada.kasir.feature.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.backup.BackupManager
import com.nada.kasir.core.util.Result
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

data class BackupUiState(
    val sedangProses: Boolean = false,
    val pesan: String? = null,
    val fileBackupTerakhir: File? = null
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupUiState())
    val uiState: StateFlow<BackupUiState> = _uiState

    fun buatBackup() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangProses = true)
            when (val result = backupManager.backup()) {
                is Result.Success -> _uiState.value = BackupUiState(
                    pesan = "Backup berhasil dibuat: ${result.data.name}",
                    fileBackupTerakhir = result.data
                )
                is Result.Failure -> _uiState.value = BackupUiState(pesan = result.error.pesan)
            }
        }
    }

    /**
     * Simpan file backup terakhir ke [uri] yang dipilih pengguna lewat file picker sistem
     * Android (Storage Access Framework) - lihat BackupScreen.kt. [uri] bisa mengarah ke
     * Google Drive kalau pengguna memilih "Drive" di daftar lokasi pada picker tersebut,
     * TANPA aplikasi ini perlu integrasi/API key Google Drive apa pun secara langsung.
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

    fun restoreDariUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sedangProses = true)
            val teks = withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                } catch (e: Exception) { null }
            }
            if (teks == null) {
                _uiState.value = BackupUiState(pesan = "File backup tidak dapat dibaca.")
                return@launch
            }
            when (val result = backupManager.restore(teks)) {
                is Result.Success -> _uiState.value = BackupUiState(pesan = "Restore berhasil. Data lama telah digantikan dengan data dari backup.")
                is Result.Failure -> _uiState.value = BackupUiState(pesan = result.error.pesan)
            }
        }
    }

    fun clearPesan() { _uiState.value = _uiState.value.copy(pesan = null) }
}
