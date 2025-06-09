package com.surendramaran.yolov8tflite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import java.lang.IllegalArgumentException

class HistoryViewModel(private val dao: FocusSessionDao) : ViewModel() {

    // Mengubah Flow dari Room menjadi LiveData yang bisa diobservasi oleh UI
    val allSessions = dao.getAllSessions().asLiveData()
}

// Factory diperlukan untuk membuat instance ViewModel dengan parameter (dao)
class HistoryViewModelFactory(private val dao: FocusSessionDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
