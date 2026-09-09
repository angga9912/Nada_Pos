package com.nada.kasir.core.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import com.nada.kasir.core.util.AppError
import com.nada.kasir.core.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class PairedPrinterInfo(val nama: String, val macAddress: String)

/**
 * Mengelola koneksi ke printer thermal Bluetooth via protokol SPP (poin 8).
 * Hanya mendukung perangkat yang SUDAH dipasangkan (paired) lewat pengaturan
 * Bluetooth Android - ini menghindari kebutuhan izin discovery/location yang
 * lebih rumit, dan sudah cukup untuk skenario 1 printer per toko.
 */
@Singleton
class BluetoothPrinterManager @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    // UUID standar Serial Port Profile - dipakai hampir semua printer thermal Bluetooth generik.
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val bluetoothAdapter: BluetoothAdapter?
        get() = BluetoothAdapter.getDefaultAdapter()

    @SuppressLint("MissingPermission") // caller wajib cek izin BLUETOOTH_CONNECT sebelum panggil (lihat PengaturanPrinterScreen)
    fun daftarPrinterTerpasang(): List<PairedPrinterInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        return try {
            adapter.bondedDevices?.map { PairedPrinterInfo(it.name ?: "Perangkat tanpa nama", it.address) } ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }
    }

    fun bluetoothTersedia(): Boolean = bluetoothAdapter != null && bluetoothAdapter?.isEnabled == true

    /**
     * Kirim data mentah ESC/POS ke printer dengan MAC address tertentu.
     * Kegagalan TIDAK BOLEH membatalkan transaksi yang sudah tersimpan (poin 8) -
     * pemanggil (ViewModel) yang memutuskan bagaimana pesan error ditampilkan.
     */
    @SuppressLint("MissingPermission")
    suspend fun cetak(macAddress: String, data: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        val adapter = bluetoothAdapter ?: return@withContext Result.Failure(AppError.PrinterTidakTerhubung)
        var socket: BluetoothSocket? = null
        try {
            val device: BluetoothDevice = adapter.getRemoteDevice(macAddress)
            socket = device.createRfcommSocketToServiceRecord(sppUuid)
            adapter.cancelDiscovery()
            socket.connect()
            socket.outputStream.write(data)
            socket.outputStream.flush()
            Result.Success(Unit)
        } catch (e: IOException) {
            Result.Failure(AppError.PrinterTidakTerhubung)
        } catch (e: SecurityException) {
            Result.Failure(AppError.PrinterTidakTerhubung)
        } finally {
            try { socket?.close() } catch (e: IOException) { /* abaikan */ }
        }
    }

    suspend fun testPrint(macAddress: String, ukuranKertas: String): Result<Unit> {
        val lebar = if (ukuranKertas == "80mm") 48 else 32
        val data = EscPosBuilder()
            .reset()
            .alignCenter()
            .bold(true)
            .textLine("TEST PRINT")
            .bold(false)
            .textLine("NADA POS")
            .garis(lebar)
            .alignLeft()
            .textLine("Printer terhubung dengan baik.")
            .textLine("Ukuran kertas: $ukuranKertas")
            .feedAndCut()
            .build()
        return cetak(macAddress, data)
    }
}
