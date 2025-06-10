package com.surendramaran.yolov8tflite

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Delete
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface FocusSessionDao {

    @Insert
    suspend fun insert(session: FocusSession)

    @Query("SELECT * FROM focus_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<FocusSession>>

    @Delete
    suspend fun delete(session: FocusSession)

    @Query("DELETE FROM focus_sessions")
    suspend fun clearAll()
}