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
import com.nada.kasir.core.paket.PaketAplikasi
import com.nada.kasir.core.session.SessionManager
import com.nada.kasir.feature.backup.BackupScreen
import com.nada.kasir.feature.dashboard.DashboardScreen
import com.nada.kasir.feature.hutang_piutang.HutangPiutangScreen
import com.nada.kasir.feature.info_paket.InfoPaketScreen
import com.nada.kasir.feature.kasir.KasirScreen
import com.nada.kasir.feature.laporan.LaporanScreen
import com.nada.kasir.feature.login.LoginScreen
import com.nada.kasir.feature.onboarding.OnboardingPreference
import com.nada.kasir.feature.onboarding.OnboardingScreen
import com.nada.kasir.feature.outlet.OutletScreen
import com.nada.kasir.feature.pengaturan_hub.PengaturanHubScreen
import com.nada.kasir.feature.pengaturan_printer.PengaturanPrinterScreen
import com.nada.kasir.feature.pengaturan_toko.PengaturanTokoScreen
import com.nada.kasir.feature.produk.ProdukScreen
import com.nada.kasir.feature.riwayat.RiwayatScreen
import com.nada.kasir.feature.supplier.SupplierScreen

sealed class NadaRoute(val route: String) {
    object Onboarding : NadaRoute("onboarding")
    object Login : NadaRoute("login")
    object MainShell : NadaRoute("main_shell")
    object PengaturanPrinter : NadaRoute("pengaturan_printer")
    object Backup : NadaRoute("backup")
    object Laporan : NadaRoute("laporan")
    object PengaturanToko : NadaRoute("pengaturan_toko")
    object Pengguna : NadaRoute("pengguna")
    object Supplier : NadaRoute("supplier")
    object Outlet : NadaRoute("outlet")
    object HutangPiutang : NadaRoute("hutang_piutang")
    object InfoPaket : NadaRoute("info_paket")
}

private enum class TabUtama(val label: String, val ikon: androidx.compose.ui.graphics.vector.ImageVector) {
    KASIR("Kasir", Icons.Filled.PointOfSale),
    PRODUK("Produk", Icons.Filled.Inventory2),
    LAPORAN("Laporan", Icons.Filled.Assessment),
    TRANSAKSI("Transaksi", Icons.Filled.ReceiptLong),
    LAINNYA("Lainnya", Icons.Filled.Settings)
}

private val WarnaNavAktif = Color(0xFF1976D2)
private val WarnaNavIndikator = Color(0xFFE3F2FD)
private val WarnaNavNonaktif = Color(0xFF64748B)

@Composable
fun NadaNavGraph(navController: NavHostController = rememberNavController()) {
    val context = LocalContext.current
    val sessionHolder: SessionHolderViewModel = hiltViewModel()
    val sessionManager = sessionHolder.sessionManager
    val paketAktif by sessionHolder.paketAktif.collectAsState()

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
                sessionManager = sessionManager,
                paketAktif = paketAktif
            )
        }
        composable(NadaRoute.PengaturanPrinter.route) {
            AdminRouteGuard(sessionManager, navController) { PengaturanPrinterScreen() }
        }
        composable(NadaRoute.Backup.route) {
            AdminRouteGuard(sessionManager, navController) { BackupScreen() }
        }
        composable(NadaRoute.Laporan.route) {
            AdminRouteGuard(sessionManager, navController) {
                if (paketAktif.mencakup(PaketAplikasi.CUSTOM)) {
                    LaporanScreen()
                } else {
                    LaporanTerkunci(onBukaInfoPaket = { navController.navigate(NadaRoute.InfoPaket.route) })
                }
            }
        }
        composable(NadaRoute.PengaturanToko.route) {
            AdminRouteGuard(sessionManager, navController) { PengaturanTokoScreen() }
        }
        composable(NadaRoute.Pengguna.route) {
            AdminRouteGuard(sessionManager, navController) {
                val currentUserId = sessionManager.currentUser.value?.id ?: 1L
                com.nada.kasir.feature.pengguna.PenggunaScreen(currentUserId = currentUserId)
            }
        }
        composable(NadaRoute.Supplier.route) {
            AdminRouteGuard(sessionManager, navController) { SupplierScreen() }
        }
        composable(NadaRoute.Outlet.route) {
            AdminRouteGuard(sessionManager, navController) { OutletScreen() }
        }
        composable(NadaRoute.HutangPiutang.route) {
            AdminRouteGuard(sessionManager, navController) { HutangPiutangScreen() }
        }
        composable(NadaRoute.InfoPaket.route) {
            InfoPaketScreen(onKembali = { navController.popBackStack() })
        }
    }
}

@Composable
private fun AdminRouteGuard(
    sessionManager: SessionManager,
    navController: NavHostController,
    content: @Composable () -> Unit
) {
    val currentUser by sessionManager.currentUser.collectAsState()

    if (currentUser == null) {
        LaunchedEffect(Unit) {
            navController.navigate(NadaRoute.Login.route) { popUpTo(0) { inclusive = true } }
        }
    } else if (currentUser?.role != UserRole.ADMIN) {
        Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Akses Dibatasi", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Text("Halaman ini memerlukan hak akses Administrator.", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Button(onClick = { navController.popBackStack() }) { Text("Kembali") }
            }
        }
    } else {
        content()
    }
}

@Composable
private fun MainShell(
    navController: NavHostController,
    sessionManager: SessionManager,
    paketAktif: PaketAplikasi
) {
    var tabAktif by rememberSaveable { mutableStateOf(TabUtama.KASIR) }
    val penggunaAktif by sessionManager.currentUser.collectAsState()
    val pengguna = penggunaAktif
    if (pengguna == null) {
        LaunchedEffect(Unit) {
            navController.navigate(NadaRoute.Login.route) { popUpTo(0) { inclusive = true } }
        }
        return
    }
    val isAdmin = pengguna.role == UserRole.ADMIN
    val currentUserId = pengguna.id

    fun logout() { sessionManager.logout() }

    val isiTab: @Composable () -> Unit = {
        when (tabAktif) {
            TabUtama.KASIR -> KasirScreen(currentUserId = currentUserId, isAdmin = isAdmin, onBukaInfoPaket = { navController.navigate(NadaRoute.InfoPaket.route) })
            TabUtama.PRODUK -> ProdukScreen(isAdmin = isAdmin)
            TabUtama.LAPORAN -> {
                if (!isAdmin) {
                    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("Laporan hanya dapat diakses oleh Administrator.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    }
                } else if (!paketAktif.mencakup(PaketAplikasi.CUSTOM)) {
                    LaporanTerkunci(onBukaInfoPaket = { navController.navigate(NadaRoute.InfoPaket.route) })
                } else {
                    LaporanScreen(onBukaRiwayat = { tabAktif = TabUtama.TRANSAKSI })
                }
            }
            TabUtama.TRANSAKSI -> RiwayatScreen(isAdmin = isAdmin)
            TabUtama.LAINNYA -> PengaturanHubScreen(
                isAdmin = isAdmin,
                onBukaPengaturanPrinter = { navController.navigate(NadaRoute.PengaturanPrinter.route) },
                onBukaPengaturanToko = { navController.navigate(NadaRoute.PengaturanToko.route) },
                onBukaPengguna = { navController.navigate(NadaRoute.Pengguna.route) },
                onBukaSupplier = { navController.navigate(NadaRoute.Supplier.route) },
                onBukaOutlet = { navController.navigate(NadaRoute.Outlet.route) },
                onBukaHutangPiutang = { navController.navigate(NadaRoute.HutangPiutang.route) },
                onBukaBackup = { navController.navigate(NadaRoute.Backup.route) },
                onBukaInfoPaket = { navController.navigate(NadaRoute.InfoPaket.route) },
                onLogout = ::logout
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val layarPendek = maxHeight < 480.dp
        Scaffold(
            bottomBar = {
                if (!layarPendek) {
                    NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                        TabUtama.values().forEach { tab ->
                            NavigationBarItem(
                                selected = tabAktif == tab,
                                onClick = { tabAktif = tab },
                                icon = { Icon(tab.ikon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = WarnaNavAktif,
                                    selectedTextColor = WarnaNavAktif,
                                    indicatorColor = WarnaNavIndikator,
                                    unselectedIconColor = WarnaNavNonaktif,
                                    unselectedTextColor = WarnaNavNonaktif
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            if (layarPendek) {
                Row(modifier = Modifier.padding(padding).fillMaxSize()) {
                    NavigationRail(containerColor = Color.White) {
                        TabUtama.values().forEach { tab ->
                            NavigationRailItem(
                                selected = tabAktif == tab,
                                onClick = { tabAktif = tab },
                                icon = { Icon(tab.ikon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = WarnaNavAktif,
                                    selectedTextColor = WarnaNavAktif,
                                    indicatorColor = WarnaNavIndikator,
                                    unselectedIconColor = WarnaNavNonaktif,
                                    unselectedTextColor = WarnaNavNonaktif
                                )
                            )
                        }
                    }
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) { isiTab() }
                }
            } else {
                Box(modifier = Modifier.padding(padding)) { isiTab() }
            }
        }
    }
}

@Composable
private fun LaporanTerkunci(onBukaInfoPaket: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Laporan tersedia di paket Custom dan Pro", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Upgrade paket untuk melihat laporan penjualan harian, bulanan, dan stok.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onBukaInfoPaket) { Text("Lihat Paket") }
        }
    }
}
