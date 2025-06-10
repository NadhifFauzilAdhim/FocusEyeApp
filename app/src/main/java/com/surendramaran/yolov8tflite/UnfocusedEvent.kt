package com.surendramaran.yolov8tflite

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "unfocused_events",
    // --- PERUBAHAN UTAMA DI SINI ---
    // Menambahkan Foreign Key dengan aturan onDelete CASCADE
    foreignKeys = [
        ForeignKey(
            entity = FocusSession::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE // <-- Ini akan menghapus event saat sesi induknya dihapus
        )
    ]
)
data class UnfocusedEvent(
    // Sebaiknya gunakan Long untuk ID yang auto-generate
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long, // Foreign key ke FocusSession
    val timestamp: Long,
    val imagePath: String
)