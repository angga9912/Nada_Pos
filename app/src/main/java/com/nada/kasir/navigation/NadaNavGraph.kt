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
import com.nada.kasir.core.data.local.entity.UserRole
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
    KASIR("Kasir", Icons.Filled.PointOfSale),
    PRODUK("Produk", Icons.Filled.Inventory2),
    LAPORAN("Laporan", Icons.Filled.Assessment),
    TRANSAKSI("Transaksi", Icons.Filled.ReceiptLong),
    LAINNYA("Lainnya", Icons.Filled.Settings)
}

@Composable
fun NadaNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val sessionManager = hiltViewModelSession()

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
                sessionManager = sessionManager
            )
        }
        composable(NadaRoute.PengaturanPrinter.route) {
            AdminRouteGuard(sessionManager, navController) {
                PengaturanPrinterScreen()
            }
        }
        composable(NadaRoute.Backup.route) {
            AdminRouteGuard(sessionManager, navController) {
                BackupScreen()
            }
        }
        composable(NadaRoute.Laporan.route) {
            AdminRouteGuard(sessionManager, navController) {
                LaporanScreen()
            }
        }
        composable(NadaRoute.PengaturanToko.route) {
            AdminRouteGuard(sessionManager, navController) {
                PengaturanTokoScreen()
            }
        }
        composable(NadaRoute.Pengguna.route) {
            AdminRouteGuard(sessionManager, navController) {
                val currentUserId = sessionManager.currentUser.value?.id ?: 1L
                PenggunaScreen(currentUserId = currentUserId)
            }
        }
        composable(NadaRoute.InfoPaket.route) {
            InfoPaketScreen(onKembali = { navController.popBackStack() })
        }
    }
}

/**
 * Route Guard untuk membatasi akses halaman sensitif/administratif (RBAC).
 * Jika belum login -> diarahkan ke Login.
 * Jika login sebagai Kasir -> diblokir dengan pesan Akses Dibatasi.
 */
@Composable
private fun AdminRouteGuard(
    sessionManager: SessionManager,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val currentUser by sessionManager.currentUser.collectAsState()

    if (currentUser == null) {
        LaunchedEffect(Unit) {
            navController.navigate(NadaRoute.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    } else if (currentUser?.role != UserRole.ADMIN) {
        Box(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Akses Dibatasi",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Halaman ini memerlukan hak akses Administrator.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) {
                    Text("Kembali")
                }
            }
        }
    } else {
        content()
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
    var tabAktif by rememberSaveable { mutableStateOf(TabUtama.KASIR) }
    val isAdmin = sessionManager.isAdmin()
    val currentUserId = sessionManager.currentUser.value?.id ?: 1L

    fun logout() {
        sessionManager.logout()
        navController.navigate(NadaRoute.Login.route) { popUpTo(0) { inclusive = true } }
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color.White,
                tonalElevation = 4.dp
            ) {
                TabUtama.values().forEach { tab ->
                    NavigationBarItem(
                        selected = tabAktif == tab,
                        onClick = { tabAktif = tab },
                        icon = { Icon(tab.ikon, contentDescription = tab.label) },
                        label = {
                            Text(
                                tab.label,
                                fontWeight = if (tabAktif == tab) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color(0xFF1976D2),
                            selectedTextColor = Color(0xFF1976D2),
                            indicatorColor = Color(0xFFE3F2FD),
                            unselectedIconColor = Color(0xFF64748B),
                            unselectedTextColor = Color(0xFF64748B)
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (tabAktif) {
                TabUtama.KASIR -> KasirScreen(currentUserId = currentUserId, isAdmin = isAdmin)
                TabUtama.PRODUK -> ProdukScreen(isAdmin = isAdmin)
                TabUtama.LAPORAN -> {
                    if (isAdmin) {
                        LaporanScreen(onBukaRiwayat = { tabAktif = TabUtama.TRANSAKSI })
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Laporan hanya dapat diakses oleh Administrator.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                TabUtama.TRANSAKSI -> RiwayatScreen(isAdmin = isAdmin)
                TabUtama.LAINNYA -> PengaturanHubScreen(
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
