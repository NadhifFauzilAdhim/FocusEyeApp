package com.surendramaran.yolov8tflite

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
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

        // --- PERBAIKAN: Tentukan tipe parameter lambda secara eksplisit ---
        val adapter = HistoryAdapter { session: FocusSession ->
            showDeleteConfirmationDialog(session)
        }
        // --------------------------------------------------------------------

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

    // Fungsi untuk menampilkan dialog konfirmasi penghapusan satu item
    private fun showDeleteConfirmationDialog(session: FocusSession) {
        AlertDialog.Builder(this)
            .setTitle("Hapus Sesi")
            .setMessage("Apakah Anda yakin ingin menghapus riwayat sesi ini?")
            .setPositiveButton("Hapus") { _, _ ->
                historyViewModel.delete(session)
                Toast.makeText(this, "Sesi dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Fungsi untuk menampilkan dialog konfirmasi penghapusan semua item
    private fun showClearAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Hapus Semua Riwayat")
            .setMessage("Apakah Anda yakin ingin menghapus semua riwayat analisis? Tindakan ini tidak dapat dibatalkan.")
            .setPositiveButton("Hapus Semua") { _, _ ->
                historyViewModel.clearAll()
                Toast.makeText(this, "Semua riwayat dihapus", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    // Tampilkan menu di toolbar
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.history_menu, menu)
        return true
    }

    // Tangani klik pada item menu
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_all -> {
                showClearAllConfirmationDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}
