package com.surendramaran.yolov8tflite

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.surendramaran.yolov8tflite.databinding.ActivityHistoryDetailBinding

class HistoryDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHistoryDetailBinding
    private var sessionId: Long = -1

    private val viewModel: HistoryDetailViewModel by viewModels {
        HistoryDetailViewModelFactory(
            (application as FocusEyeApplication).database.focusSessionDao(),
            sessionId
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHistoryDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sessionId = intent.getLongExtra("SESSION_ID", -1)
        if (sessionId == -1L) {
            finish()
            return
        }

        setSupportActionBar(binding.toolbarHistoryDetail)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val adapter = HistoryDetailAdapter()
        binding.recyclerViewDetail.adapter = adapter

        viewModel.eventsForSession.observe(this) { events ->
            adapter.submitList(events)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
