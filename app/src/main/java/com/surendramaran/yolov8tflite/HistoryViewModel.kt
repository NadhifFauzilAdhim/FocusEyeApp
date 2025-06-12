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

    fun delete(session: FocusSession) = viewModelScope.launch(Dispatchers.IO) {
        val eventsToDelete = dao.getEventsListForSession(session.id)
        eventsToDelete.forEach { event ->
            try {
                File(event.imagePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        dao.delete(session)
    }

    fun clearAll() = viewModelScope.launch(Dispatchers.IO) {
        val allEvents = dao.getAllEventsList()

        allEvents.forEach { event ->
            try {
                File(event.imagePath).delete()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        dao.clearAll()
    }
}

class HistoryViewModelFactory(private val dao: FocusSessionDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HistoryViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}