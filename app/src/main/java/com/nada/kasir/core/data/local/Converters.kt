package com.nada.kasir.core.data.local

import androidx.room.TypeConverter
import com.nada.kasir.core.data.local.entity.*

class Converters {
    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name
    @TypeConverter
    fun toUserRole(value: String): UserRole = UserRole.valueOf(value)

    @TypeConverter
    fun fromTransactionStatus(value: TransactionStatus): String = value.name
    @TypeConverter
    fun toTransactionStatus(value: String): TransactionStatus = TransactionStatus.valueOf(value)

    @TypeConverter
    fun fromMetodePembayaran(value: MetodePembayaran): String = value.name
    @TypeConverter
    fun toMetodePembayaran(value: String): MetodePembayaran = MetodePembayaran.valueOf(value)

    @TypeConverter
    fun fromTipeMutasiStok(value: TipeMutasiStok): String = value.name
    @TypeConverter
    fun toTipeMutasiStok(value: String): TipeMutasiStok = TipeMutasiStok.valueOf(value)
}
