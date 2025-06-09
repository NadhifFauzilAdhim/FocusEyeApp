package com.surendramaran.yolov8tflite

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.formatter.PercentFormatter
import com.surendramaran.yolov8tflite.databinding.ItemHistorySessionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HistoryAdapter : ListAdapter<FocusSession, HistoryAdapter.HistoryViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistorySessionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        val currentSession = getItem(position)
        holder.bind(currentSession)
    }

    class HistoryViewHolder(private val binding: ItemHistorySessionBinding) : RecyclerView.ViewHolder(binding.root) {
        private val dateFormatter = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())

        fun bind(session: FocusSession) {
            binding.textViewTimestamp.text = dateFormatter.format(Date(session.timestamp))
            binding.textViewSessionDetails.text = "Total ${session.totalStudents} Siswa"

            // Format dan tampilkan durasi dari detik ke menit dan detik
            val minutes = TimeUnit.SECONDS.toMinutes(session.durationInSeconds.toLong())
            val seconds = session.durationInSeconds % 60
            binding.textViewDuration.text = "Durasi: $minutes menit $seconds detik"

            val totalFrames = session.focusedCount + session.unfocusedCount
            val focusPercentage = if (totalFrames > 0) (session.focusedCount.toFloat() / totalFrames * 100) else 0f
            val unfocusedPercentage = if (totalFrames > 0) (session.unfocusedCount.toFloat() / totalFrames * 100) else 0f

            binding.textViewFocusedLabel.text = "Fokus (${String.format("%.0f", focusPercentage)}%)"
            binding.textViewUnfocusedLabel.text = "Tdk Fokus (${String.format("%.0f", unfocusedPercentage)}%)"

            setupPieChart(session)
        }

        private fun setupPieChart(session: FocusSession) {
            val totalFrames = session.focusedCount + session.unfocusedCount
            if (totalFrames <= 0) {
                binding.pieChartHistory.visibility = View.GONE
                return
            } else {
                binding.pieChartHistory.visibility = View.VISIBLE
            }

            val entries = ArrayList<PieEntry>()
            entries.add(PieEntry(session.focusedCount.toFloat(), "Fokus"))
            entries.add(PieEntry(session.unfocusedCount.toFloat(), "Tdk Fokus"))

            val dataSet = PieDataSet(entries, "").apply {
                colors = listOf(
                    ContextCompat.getColor(binding.root.context, R.color.focused_green),
                    ContextCompat.getColor(binding.root.context, R.color.unfocused_red)
                )
                setDrawValues(true)
                valueTextColor = Color.WHITE
                valueTextSize = 12f
                valueFormatter = PercentFormatter(binding.pieChartHistory)
            }

            val pieData = PieData(dataSet)

            binding.pieChartHistory.apply {
                data = pieData
                description.isEnabled = false
                legend.isEnabled = false
                isDrawHoleEnabled = true
                holeRadius = 58f
                transparentCircleRadius = 61f
                setDrawEntryLabels(false)
                setUsePercentValues(true)
                setHoleColor(Color.TRANSPARENT)
                centerText = "Sesi"
                setCenterTextSize(16f)
                setCenterTextColor(ContextCompat.getColor(context, R.color.focused_green))
                invalidate()
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<FocusSession>() {
        override fun areItemsTheSame(oldItem: FocusSession, newItem: FocusSession) =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: FocusSession, newItem: FocusSession) =
            oldItem == newItem
    }
}
