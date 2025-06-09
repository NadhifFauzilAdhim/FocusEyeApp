package com.surendramaran.yolov8tflite

import android.app.Application

class FocusEyeApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getDatabase(this) }
}
