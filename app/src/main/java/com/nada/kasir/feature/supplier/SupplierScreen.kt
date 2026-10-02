package com.nada.kasir.feature.supplier

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
import com.nada.kasir.core.data.local.entity.SupplierEntity
import com.nada.kasir.core.data.repository.SupplierRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SupplierViewModel @Inject constructor(
    private val repository: SupplierRepository
) : ViewModel() {
    private val _state = MutableStateFlow<List<SupplierEntity>>(emptyList())
    val state: StateFlow<List<SupplierEntity>> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.observeAll().collect { _state.value = it } }
    }

    fun simpan(entity: SupplierEntity) = viewModelScope.launch { repository.save(entity) }
}

@Composable
fun SupplierScreen(viewModel: SupplierViewModel = hiltViewModel()) {
    val list by viewModel.state.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    var nama by remember { mutableStateOf("") }
    var kontak by remember { mutableStateOf("") }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { showForm = true }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah supplier")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Supplier", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(12.dp))
            LazyColumn {
                items(list) { supplier ->
                    ListItem(
                        headlineContent = { Text(supplier.nama) },
                        supportingContent = { Text("${supplier.kontak} • ${supplier.telepon}") }
                    )
                    Divider()
                }
            }
        }
    }

    if (showForm) {
        AlertDialog(
            onDismissRequest = { showForm = false },
            title = { Text("Tambah Supplier") },
            text = {
                Column {
                    OutlinedTextField(value = nama, onValueChange = { nama = it }, label = { Text("Nama supplier") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = kontak, onValueChange = { kontak = it }, label = { Text("Kontak") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (nama.isNotBlank()) {
                            viewModel.simpan(SupplierEntity(nama = nama.trim(), kontak = kontak.trim()))
                            nama = ""; kontak = ""; showForm = false
                        }
                    }
                ) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { showForm = false }) { Text("Batal") } }
        )
    }
}
