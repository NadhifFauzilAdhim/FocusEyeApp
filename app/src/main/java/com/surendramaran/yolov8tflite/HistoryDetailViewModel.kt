package com.surendramaran.yolov8tflite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import java.lang.IllegalArgumentException

class HistoryDetailViewModel(dao: FocusSessionDao, sessionId: Long) : ViewModel() {
    val eventsForSession = dao.getEventsForSession(sessionId).asLiveData()
}

class HistoryDetailViewModelFactory(private val dao: FocusSessionDao, private val sessionId: Long) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryDetailViewModel(dao, sessionId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
