package com.surendramaran.yolov8tflite

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.surendramaran.yolov8tflite.databinding.ActivityHistoryBinding

class HistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryBinding
    private val historyViewModel: HistoryViewModel by viewModels {
        HistoryViewModelFactory((application as FocusEyeApplication).database.focusSessionDao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbarHistory)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)

        val adapter = HistoryAdapter()
        binding.recyclerViewHistory.adapter = adapter

        historyViewModel.allSessions.observe(this) { sessions ->
            if (sessions.isNullOrEmpty()) {
                binding.recyclerViewHistory.visibility = View.GONE
                binding.textViewNoData.visibility = View.VISIBLE
            } else {
                binding.recyclerViewHistory.visibility = View.VISIBLE
                binding.textViewNoData.visibility = View.GONE
                adapter.submitList(sessions)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
