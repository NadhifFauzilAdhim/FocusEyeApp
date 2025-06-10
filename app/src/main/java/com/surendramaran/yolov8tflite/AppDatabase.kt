package com.surendramaran.yolov8tflite

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// --- PERBAIKAN: Tambahkan UnfocusedEvent & naikkan versi ---
@Database(entities = [FocusSession::class, UnfocusedEvent::class], version = 3, exportSchema = false)
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
                    // Migrasi destruktif akan menghapus data lama saat struktur berubah.
                    // Ini adalah cara termudah untuk pengembangan.
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
