package com.nada.kasir.feature.outlet

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
import com.nada.kasir.core.data.local.entity.OutletEntity
import com.nada.kasir.core.data.repository.OutletRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OutletViewModel @Inject constructor(
    private val repository: OutletRepository
) : ViewModel() {
    private val _state = MutableStateFlow<List<OutletEntity>>(emptyList())
    val state: StateFlow<List<OutletEntity>> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.observeAll().collect { _state.value = it } }
    }

    fun simpan(entity: OutletEntity) = viewModelScope.launch { repository.save(entity) }
}

@Composable
fun OutletScreen(viewModel: OutletViewModel = hiltViewModel()) {
    val list by viewModel.state.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var nama by remember { mutableStateOf("") }
    var kode by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah outlet")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Outlet / Cabang", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(list) { outlet ->
                    ListItem(
                        headlineContent = { Text(outlet.nama) },
                        supportingContent = { Text("${outlet.kode} • ${if (outlet.aktif) "Aktif" else "Nonaktif"}") }
                    )
                    Divider()
                }
            }
        }
    }

    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text("Tambah Outlet") },
            text = {
                Column {
                    OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama outlet") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = kode, onValueChange = { kode = it }, label = { Text("Kode outlet") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nama.isNotBlank() && kode.isNotBlank()) {
                            viewModel.simpan(OutletEntity(nama = nama.trim(), kode = kode.trim()))
                            nama = ""; kode = ""; showForm = false
                        }
                    }
                ) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showForm = false }) { Text("Batal") } }
        )
    }
}
