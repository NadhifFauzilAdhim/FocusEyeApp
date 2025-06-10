package com.surendramaran.yolov8tflite

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: Long,
    val focusedCount: Int,
    val unfocusedCount: Int,
    val totalStudents: Int,
    val durationInSeconds: Int
)