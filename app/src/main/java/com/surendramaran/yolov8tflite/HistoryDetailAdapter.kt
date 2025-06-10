package com.surendramaran.yolov8tflite

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.surendramaran.yolov8tflite.databinding.ItemUnfocusedCaptureBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HistoryDetailAdapter : ListAdapter<UnfocusedEvent, HistoryDetailAdapter.EventViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EventViewHolder {
        val binding = ItemUnfocusedCaptureBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return EventViewHolder(binding)
    }

    override fun onBindViewHolder(holder: EventViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class EventViewHolder(private val binding: ItemUnfocusedCaptureBinding) : RecyclerView.ViewHolder(binding.root) {
        private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

        fun bind(event: UnfocusedEvent) {
            binding.textViewCaptureTimestamp.text = timeFormatter.format(Date(event.timestamp))
            Glide.with(binding.root.context)
                .load(File(event.imagePath))
                .into(binding.imageViewCapture)
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<UnfocusedEvent>() {
        override fun areItemsTheSame(oldItem: UnfocusedEvent, newItem: UnfocusedEvent) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: UnfocusedEvent, newItem: UnfocusedEvent) = oldItem == newItem
    }
}
