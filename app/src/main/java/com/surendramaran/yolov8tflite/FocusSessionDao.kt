package com.surendramaran.yolov8tflite

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    // Fungsi untuk memasukkan data baru. `suspend` berarti harus dijalankan di background thread.
    @Insert
    suspend fun insert(session: FocusSession)

    // Fungsi untuk mengambil semua data, diurutkan dari yang terbaru (opsional, berguna untuk nanti)
    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<FocusSession>>
}