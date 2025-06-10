package com.surendramaran.yolov8tflite

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: FocusSession): Long

    @Update
    suspend fun update(session: FocusSession)

    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Delete
    suspend fun delete(session: FocusSession)

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAll() // Ganti nama agar konsisten (opsional)

    @Insert
    suspend fun insertUnfocusedEvent(event: UnfocusedEvent)

    @Query("SELECT * FROM unfocused_events WHERE sessionId = :sessionId")
    fun getEventsForSession(sessionId: Long): Flow<List<UnfocusedEvent>>

    // --- FUNGSI BARU YANG DIPERLUKAN VIEWMODEL ---
    @Query("SELECT * FROM unfocused_events WHERE sessionId = :sessionId")
    suspend fun getEventsListForSession(sessionId: Long): List<UnfocusedEvent>

    @Query("SELECT * FROM unfocused_events")
    suspend fun getAllEventsList(): List<UnfocusedEvent>

    // Fungsi deleteEventsForSession dan clearAllEvents tidak lagi diperlukan
    // karena sudah ditangani oleh ON DELETE CASCADE
}