package com.nada.kasir.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nada.kasir.core.session.SessionManager
import com.nada.kasir.feature.backup.BackupScreen
import com.nada.kasir.feature.dashboard.DashboardScreen
import com.nada.kasir.feature.info_paket.InfoPaketScreen
import com.nada.kasir.feature.kasir.KasirScreen
import com.nada.kasir.feature.laporan.LaporanScreen
import com.nada.kasir.feature.login.LoginScreen
import com.nada.kasir.feature.onboarding.OnboardingPreference
import com.nada.kasir.feature.onboarding.OnboardingScreen
import com.nada.kasir.feature.pengaturan_hub.PengaturanHubScreen
import com.nada.kasir.feature.pengaturan_printer.PengaturanPrinterScreen
import com.nada.kasir.feature.pengaturan_toko.PengaturanTokoScreen
import com.nada.kasir.feature.pengguna.PenggunaScreen
import com.nada.kasir.feature.produk.ProdukScreen
import com.nada.kasir.feature.riwayat.RiwayatScreen

sealed class NadaRoute(val route: String) {
    object Onboarding : NadaRoute("onboarding")
    object Login : NadaRoute("login")
    object MainShell : NadaRoute("main_shell") // berisi Home/Kasir/Produk/Riwayat/Pengaturan dengan bottom nav
    object PengaturanPrinter : NadaRoute("pengaturan_printer")
    object Backup : NadaRoute("backup")
    object Laporan : NadaRoute("laporan")
    object PengaturanToko : NadaRoute("pengaturan_toko")
    object Pengguna : NadaRoute("pengguna")
    object InfoPaket : NadaRoute("info_paket")
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
    val context = LocalContext.current
    // Onboarding cuma tampil SEKALI di pembukaan pertama - dicek dari SharedPreferences.
    // remember (bukan dicek ulang tiap recomposition) supaya nggak "lompat" balik ke
    // onboarding kalau layar lain memicu recomposition NavHost ini.
    val startDestination = remember {
        if (OnboardingPreference.sudahLihat(context)) NadaRoute.Login.route else NadaRoute.Onboarding.route
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(NadaRoute.Onboarding.route) {
            OnboardingScreen(onSelesai = {
                OnboardingPreference.tandaiSudahLihat(context)
                navController.navigate(NadaRoute.Login.route) {
                    popUpTo(NadaRoute.Onboarding.route) { inclusive = true }
                }
            })
        }
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
        composable(NadaRoute.InfoPaket.route) { InfoPaketScreen(onKembali = { navController.popBackStack() }) }
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
            // Bottom bar dengan tombol tengah melayang (floating) - meniru posisi tombol
            // "scan" bulat pada referensi desain, tapi di sini dipakai untuk akses cepat
            // ke tab Produk yang memang sudah berada di posisi tengah susunan tab.
            Box {
                NavigationBar {
                    TabUtama.values().forEach { tab ->
                        if (tab == TabUtama.PRODUK) {
                            // Slot dikosongkan di bar rata - tombol asli untuk tab ini
                            // ditampilkan sebagai FloatingActionButton bulat di atasnya.
                            NavigationBarItem(
                                selected = false,
                                onClick = {},
                                enabled = false,
                                icon = {},
                                label = {},
                                colors = NavigationBarItemDefaults.colors(
                                    unselectedIconColor = Color.Transparent,
                                    indicatorColor = Color.Transparent
                                )
                            )
                        } else {
                            NavigationBarItem(
                                selected = tabAktif == tab,
                                onClick = { tabAktif = tab },
                                icon = { Icon(tab.ikon, contentDescription = tab.label) },
                                label = { Text(tab.label) }
                            )
                        }
                    }
                }
                FloatingActionButton(
                    onClick = { tabAktif = TabUtama.PRODUK },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = (-26).dp)
                        .size(56.dp),
                    shape = CircleShape,
                    containerColor = if (tabAktif == TabUtama.PRODUK) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
                    contentColor = if (tabAktif == TabUtama.PRODUK) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
                ) {
                    Icon(TabUtama.PRODUK.ikon, contentDescription = TabUtama.PRODUK.label)
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
                    onBukaInfoPaket = { navController.navigate(NadaRoute.InfoPaket.route) },
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
