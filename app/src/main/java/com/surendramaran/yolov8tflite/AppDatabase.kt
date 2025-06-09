package com.surendramaran.yolov8tflite

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [FocusSession::class], version = 2, exportSchema = false) // <-- NAIKKAN VERSI KE 2
abstract class AppDatabase : RoomDatabase() {

    abstract fun focusSessionDao(): FocusSessionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "focus_eye_database"
                )
                    .fallbackToDestructiveMigration() // <-- TAMBAHKAN BARIS INI
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}