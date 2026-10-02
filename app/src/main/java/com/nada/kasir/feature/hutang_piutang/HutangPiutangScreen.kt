package com.nada.kasir.feature.hutang_piutang

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nada.kasir.core.data.local.entity.HutangPiutangEntity
import com.nada.kasir.core.data.repository.HutangPiutangRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HutangPiutangViewModel @Inject constructor(
    private val repository: HutangPiutangRepository
) : ViewModel() {
    private val _state = MutableStateFlow<List<HutangPiutangEntity>>(emptyList())
    val state: StateFlow<List<HutangPiutangEntity>> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.observeAll().collect { _state.value = it } }
    }

    fun simpan(entity: HutangPiutangEntity) = viewModelScope.launch { repository.save(entity) }
}

@Composable
fun HutangPiutangScreen(viewModel: HutangPiutangViewModel = hiltViewModel()) {
    val list by viewModel.state.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var pelaku by remember { mutableStateOf("") }
    var jumlah by remember { mutableStateOf("0") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah hutang/piutang")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Hutang / Piutang", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(list) { item ->
                    ListItem(
                        headlineContent = { Text(item.pelaku) },
                        supportingContent = { Text("${item.jenis} • Rp ${item.sisa}") }
                    )
                    Divider()
                }
            }
        }
    }

    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text("Catat Hutang/Piutang") },
            text = {
                Column {
                    OutlinedTextField(value = pelaku, onValueChange = { pelaku = it }, label = { Text("Pihak") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = jumlah, onValueChange = { jumlah = it }, label = { Text("Jumlah") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val nominal = jumlah.toDoubleOrNull() ?: 0.0
                        if (pelaku.isNotBlank() && nominal > 0) {
                            viewModel.simpan(HutangPiutangEntity(
                                jenis = "PIUTANG",
                                pelaku = pelaku.trim(),
                                jumlah = nominal,
                                sisa = nominal
                            ))
                            pelaku = ""; jumlah = "0"; showForm = false
                        }
                    }
                ) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showForm = false }) { Text("Batal") } }
        )
    }
}
