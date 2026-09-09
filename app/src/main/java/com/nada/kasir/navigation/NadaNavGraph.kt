package com.nada.kasir.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nada.kasir.core.session.SessionManager
import com.nada.kasir.feature.backup.BackupScreen
import com.nada.kasir.feature.dashboard.DashboardScreen
import com.nada.kasir.feature.kasir.KasirScreen
import com.nada.kasir.feature.laporan.LaporanScreen
import com.nada.kasir.feature.login.LoginScreen
import com.nada.kasir.feature.pengaturan_hub.PengaturanHubScreen
import com.nada.kasir.feature.pengaturan_printer.PengaturanPrinterScreen
import com.nada.kasir.feature.pengaturan_toko.PengaturanTokoScreen
import com.nada.kasir.feature.pengguna.PenggunaScreen
import com.nada.kasir.feature.produk.ProdukScreen
import com.nada.kasir.feature.riwayat.RiwayatScreen

sealed class NadaRoute(val route: String) {
    object Login : NadaRoute("login")
    object MainShell : NadaRoute("main_shell") // berisi Home/Kasir/Produk/Riwayat/Pengaturan dengan bottom nav
    object PengaturanPrinter : NadaRoute("pengaturan_printer")
    object Backup : NadaRoute("backup")
    object Laporan : NadaRoute("laporan")
    object PengaturanToko : NadaRoute("pengaturan_toko")
    object Pengguna : NadaRoute("pengguna")
}

private enum class TabUtama(val label: String, val ikon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Home", Icons.Filled.Home),
    KASIR("Kasir", Icons.Filled.PointOfSale),
    PRODUK("Produk", Icons.Filled.Inventory2),
    TRANSAKSI("Transaksi", Icons.Filled.ReceiptLong),
    PENGATURAN("Pengaturan", Icons.Filled.Settings)
}

@Composable
fun NadaNavGraph(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = NadaRoute.Login.route) {
        composable(NadaRoute.Login.route) {
            LoginScreen(onLoginBerhasil = {
                navController.navigate(NadaRoute.MainShell.route) {
                    popUpTo(NadaRoute.Login.route) { inclusive = true }
                }
            })
        }
        composable(NadaRoute.MainShell.route) {
            MainShell(
                navController = navController,
                sessionManager = hiltViewModelSession()
            )
        }
        composable(NadaRoute.PengaturanPrinter.route) { PengaturanPrinterScreen() }
        composable(NadaRoute.Backup.route) { BackupScreen() }
        composable(NadaRoute.Laporan.route) { LaporanScreen() }
        composable(NadaRoute.PengaturanToko.route) { PengaturanTokoScreen() }
        composable(NadaRoute.Pengguna.route) { PenggunaScreen() }
    }
}

/**
 * Shell dengan Bottom Navigation (poin 6 brief redesign). Tab di-switch dengan
 * state lokal (bukan back-stack terpisah) karena ini murni navigasi UI antar
 * tab utama - tidak ada perubahan pada logic/data di baliknya.
 * Menu administratif (Laporan, Pengaturan Printer/Toko, Pengguna, Backup)
 * tetap dibuka lewat NavController luar (poin 5: dikelompokkan, tapi tetap mudah ditemukan).
 */
@Composable
private fun MainShell(navController: NavHostController, sessionManager: SessionManager) {
    var tabAktif by rememberSaveable { mutableStateOf(TabUtama.HOME) }
    val isAdmin = sessionManager.isAdmin()
    val namaPengguna = sessionManager.currentUser.value?.nama ?: "Pengguna"
    val currentUserId = sessionManager.currentUser.value?.id ?: 1L

    fun logout() {
        sessionManager.logout()
        navController.navigate(NadaRoute.Login.route) { popUpTo(0) { inclusive = true } }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                TabUtama.values().forEach { tab ->
                    NavigationBarItem(
                        selected = tabAktif == tab,
                        onClick = { tabAktif = tab },
                        icon = { Icon(tab.ikon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        androidx.compose.foundation.layout.Box(modifier = androidx.compose.ui.Modifier.padding(padding)) {
            when (tabAktif) {
                TabUtama.HOME -> DashboardScreen(
                    isAdmin = isAdmin,
                    namaPengguna = namaPengguna,
                    onBukaKasir = { tabAktif = TabUtama.KASIR },
                    onBukaProduk = { tabAktif = TabUtama.PRODUK },
                    onBukaRiwayat = { tabAktif = TabUtama.TRANSAKSI },
                    onBukaPengaturanPrinter = { navController.navigate(NadaRoute.PengaturanPrinter.route) },
                    onBukaBackup = { navController.navigate(NadaRoute.Backup.route) },
                    onBukaLaporan = { navController.navigate(NadaRoute.Laporan.route) },
                    onBukaPengaturanToko = { navController.navigate(NadaRoute.PengaturanToko.route) },
                    onBukaPengguna = { navController.navigate(NadaRoute.Pengguna.route) },
                    onLogout = ::logout
                )
                TabUtama.KASIR -> KasirScreen(currentUserId = currentUserId)
                TabUtama.PRODUK -> ProdukScreen(isAdmin = isAdmin)
                TabUtama.TRANSAKSI -> RiwayatScreen(isAdmin = isAdmin)
                TabUtama.PENGATURAN -> PengaturanHubScreen(
                    isAdmin = isAdmin,
                    onBukaPengaturanPrinter = { navController.navigate(NadaRoute.PengaturanPrinter.route) },
                    onBukaPengaturanToko = { navController.navigate(NadaRoute.PengaturanToko.route) },
                    onBukaPengguna = { navController.navigate(NadaRoute.Pengguna.route) },
                    onBukaBackup = { navController.navigate(NadaRoute.Backup.route) },
                    onLogout = ::logout
                )
            }
        }
    }
}

@Composable
private fun hiltViewModelSession(): SessionManager {
    val holder: SessionHolderViewModel = hiltViewModel()
    return holder.sessionManager
}
