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
