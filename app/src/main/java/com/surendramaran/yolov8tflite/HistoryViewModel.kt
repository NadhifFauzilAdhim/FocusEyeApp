package com.surendramaran.yolov8tflite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.lang.IllegalArgumentException

class HistoryViewModel(private val dao: FocusSessionDao) : ViewModel() {

    val allSessions = dao.getAllSessions().asLiveData()

    /**
     * --- PERUBAHAN UTAMA PADA FUNGSI DELETE ---
     * Menjalankan operasi hapus file gambar dan data DB di background thread.
     */
    fun delete(session: FocusSession) = viewModelScope.launch(Dispatchers.IO) {
        // 1. Ambil semua event terkait untuk mendapatkan path gambar
        val eventsToDelete = dao.getEventsListForSession(session.id)

        // 2. Hapus setiap file gambar dari penyimpanan perangkat
        eventsToDelete.forEach { event ->
            try {
                File(event.imagePath).delete()
            } catch (e: Exception) {
                // Log error jika diperlukan
                e.printStackTrace()
            }
        }

        // 3. Hapus sesi dari DB. CASCADE akan otomatis menghapus event-nya.
        dao.delete(session)
    }

    /**
     * --- PERUBAHAN UTAMA PADA FUNGSI CLEAR ALL ---
     * Menghapus semua file gambar sebelum membersihkan DB.
     */
    fun clearAll() = viewModelScope.launch(Dispatchers.IO) {
        // 1. Ambil SEMUA event untuk mendapatkan semua path gambar
        val allEvents = dao.getAllEventsList()

        // 2. Hapus semua file gambar yang terkait
        allEvents.forEach { event ->
            try {
                File(event.imagePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 3. Hapus semua sesi dari DB. CASCADE akan otomatis menghapus semua event.
        dao.clearAll()
    }
}

// Factory tidak perlu diubah
class HistoryViewModelFactory(private val dao: FocusSessionDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}