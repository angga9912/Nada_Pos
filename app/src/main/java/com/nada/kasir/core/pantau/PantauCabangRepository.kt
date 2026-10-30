package com.nada.kasir.core.pantau

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Lokasi data di Firestore. Harus sama dengan firebase/firestore.rules. */
object PantauCabangPath {
    const val TOKO = "toko"
    const val REKAP = "rekap"
    const val ANGGOTA = "anggota"
}

/**
 * Satu-satunya pintu ke Firebase untuk modul Pantau Cabang. Semua fungsi aman dipanggil walau
 * google-services.json belum dipasang (build CI tanpa Firebase): hasilnya Failure berpesan jelas,
 * tidak crash.
 *
 * Catatan offline: Firestore menyimpan tulisan di antrean lokal dan mengirimnya saat online, tetapi
 * Task-nya baru selesai setelah server membalas. Karena itu [kirim] memakai batas waktu; bila habis,
 * data TETAP ada di antrean dan terkirim sendiri saat internet kembali.
 */
@Singleton
class PantauCabangRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** false bila aplikasi dibuild tanpa google-services.json. */
    fun firebaseSiap(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    fun uidSaatIni(): String? = if (firebaseSiap()) FirebaseAuth.getInstance().currentUser?.uid else null
    fun emailSaatIni(): String? = if (firebaseSiap()) FirebaseAuth.getInstance().currentUser?.email else null

    suspend fun masuk(email: String, password: String): Result<String> = amankan {
        if (!firebaseSiap()) return@amankan gagal(PESAN_FIREBASE_BELUM_SIAP)
        if (email.isBlank() || password.isEmpty()) return@amankan gagal("Email dan password wajib diisi.")
        val hasil = withTimeout(BATAS_WAKTU_MS) {
            FirebaseAuth.getInstance().signInWithEmailAndPassword(email.trim(), password).await()
        }
        val uid = hasil.user?.uid ?: return@amankan gagal("Login gagal. Silakan coba lagi.")
        Result.Success(uid)
    }

    fun keluar() {
        if (firebaseSiap()) FirebaseAuth.getInstance().signOut()
    }

    /** Kirim (atau timpa) rekap satu hari. Id dokumen sama = idempotent, aman dikirim berulang. */
    suspend fun kirim(tokoId: String, rekap: RekapHarian): Result<Unit> = amankan {
        if (!firebaseSiap()) return@amankan gagal(PESAN_FIREBASE_BELUM_SIAP)
        if (uidSaatIni() == null) return@amankan gagal(PESAN_BELUM_MASUK)
        RekapHarianLogic.validasiTokoId(tokoId)?.let { return@amankan gagal(it) }
        withTimeout(BATAS_WAKTU_MS) {
            FirebaseFirestore.getInstance()
                .collection(PantauCabangPath.TOKO).document(tokoId.trim())
                .collection(PantauCabangPath.REKAP)
                .document(RekapHarianLogic.idDokumen(rekap.tanggal, rekap.kodeDokumen))
                .set(RekapHarianLogic.keMap(rekap))
                .await()
        }
        Result.Success(Unit)
    }

    /** Untuk admin: rekap semua cabang pada rentang tanggal [dari]..[sampai], terbaru dulu. */
    suspend fun ambilRekap(tokoId: String, dari: LocalDate, sampai: LocalDate): Result<List<RekapHarian>> = amankan {
        if (!firebaseSiap()) return@amankan gagal(PESAN_FIREBASE_BELUM_SIAP)
        if (uidSaatIni() == null) return@amankan gagal(PESAN_BELUM_MASUK)
        RekapHarianLogic.validasiTokoId(tokoId)?.let { return@amankan gagal(it) }
        val snapshot = withTimeout(BATAS_WAKTU_MS) {
            FirebaseFirestore.getInstance()
                .collection(PantauCabangPath.TOKO).document(tokoId.trim())
                .collection(PantauCabangPath.REKAP)
                .whereGreaterThanOrEqualTo("tanggal", dari.toString())
                .whereLessThanOrEqualTo("tanggal", sampai.toString())
                .orderBy("tanggal", Query.Direction.DESCENDING)
                .get()
                .await()
        }
        Result.Success(snapshot.documents.mapNotNull { doc -> doc.data?.let { RekapHarianLogic.dariMap(it) } })
    }

    /**
     * Membungkus panggilan Firebase: kegagalan jadi pesan manusiawi. Pembatalan coroutine yang asli
     * (mis. worker dihentikan sistem) TETAP dilempar ulang; hanya batas waktu kita sendiri yang diubah jadi pesan.
     */
    private suspend inline fun <T> amankan(blok: () -> Result<T>): Result<T> = try {
        blok()
    } catch (e: TimeoutCancellationException) {
        gagal(pesanUntukPengguna(e))
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        gagal(pesanUntukPengguna(e))
    }

    private fun gagal(pesan: String): Result.Failure = Result.Failure(AppError.Lainnya(pesan))

    /** Pesan manusiawi; exception teknis tidak pernah ditampilkan (aturan AppError). */
    private fun pesanUntukPengguna(e: Exception): String = when (e) {
        is TimeoutCancellationException ->
            "Koneksi lambat. Rekap tersimpan di antrean dan akan terkirim otomatis saat internet stabil."
        is FirebaseNetworkException -> "Tidak ada koneksi internet."
        is FirebaseAuthException -> "Email atau password salah, atau akun belum terdaftar."
        is FirebaseFirestoreException ->
            if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                "Akun ini belum diizinkan untuk toko tersebut. Minta admin menambahkan akun Anda sebagai anggota."
            else "Gagal terhubung ke server. Coba lagi nanti."
        else -> "Gagal terhubung ke server. Coba lagi nanti."
    }

    private companion object {
        const val BATAS_WAKTU_MS = 20_000L
        const val PESAN_BELUM_MASUK =
            "Belum masuk akun Firebase. Masuk dulu di pengaturan Pantau Cabang."
        const val PESAN_FIREBASE_BELUM_SIAP =
            "Firebase belum dikonfigurasi pada aplikasi ini (google-services.json belum dipasang)."
    }
}
