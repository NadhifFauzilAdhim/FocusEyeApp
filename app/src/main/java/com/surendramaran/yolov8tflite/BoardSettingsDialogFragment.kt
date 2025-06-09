package com.surendramaran.yolov8tflite

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.surendramaran.yolov8tflite.databinding.DialogBoardSettingsBinding

class BoardSettingsDialogFragment : DialogFragment() {

    // --- PERBAIKAN 1: Tambahkan parameter phoneAlertEnabled ke interface ---
    interface BoardSettingsListener {
        fun onBoardSettingsSaved(
            x1: Float, y1: Float, x2: Float, y2: Float,
            detectionMode: String, scaleFactor: Float, skipFrames: Int,
            phoneAlertEnabled: Boolean // <-- Parameter ini sekarang ada
        )
    }

    private var listener: BoardSettingsListener? = null
    private lateinit var binding: DialogBoardSettingsBinding

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? BoardSettingsListener
            ?: throw ClassCastException("$context must implement BoardSettingsListener")
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogBoardSettingsBinding.inflate(LayoutInflater.from(context))

        setupViews()
        loadCurrentSettings()

        return AlertDialog.Builder(requireActivity())
            .setView(binding.root)
            .setTitle("Pengaturan Aplikasi")
            .setPositiveButton("Simpan") { _, _ -> saveSettings() }
            .setNegativeButton("Batal", null)
            .create()
    }

    private fun setupViews() {
        val detectionModes = resources.getStringArray(R.array.detection_modes)
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, detectionModes)
        binding.autoCompleteDetectionMode.setAdapter(adapter)
    }

    private fun loadCurrentSettings() {
        val sharedPrefs = activity?.getSharedPreferences("AppGlobalSettings", Context.MODE_PRIVATE) ?: return

        binding.editTextBoardX1.setText(sharedPrefs.getFloat("board_x1", 0.25f).toString())
        binding.editTextBoardY1.setText(sharedPrefs.getFloat("board_y1", 0.15f).toString())
        binding.editTextBoardX2.setText(sharedPrefs.getFloat("board_x2", 0.75f).toString())
        binding.editTextBoardY2.setText(sharedPrefs.getFloat("board_y2", 0.40f).toString())

        val savedDetectionMode = sharedPrefs.getString("detection_mode", "Both")
        binding.autoCompleteDetectionMode.setText(savedDetectionMode, false)

        val savedScaleFactor = sharedPrefs.getFloat("scale_factor", 1.0f)
        binding.sliderScaleFactor.value = savedScaleFactor

        binding.editTextSkipFrames.setText(sharedPrefs.getInt("skip_frames", 1).toString())

        // Muat status Switch dari SharedPreferences
        binding.switchPhoneAlert.isChecked = sharedPrefs.getBoolean("phone_alert_enabled", true)
    }

    private fun saveSettings() {
        val sharedPrefs = activity?.getSharedPreferences("AppGlobalSettings", Context.MODE_PRIVATE) ?: return

        try {
            val x1 = binding.editTextBoardX1.text.toString().toFloat()
            val y1 = binding.editTextBoardY1.text.toString().toFloat()
            val x2 = binding.editTextBoardX2.text.toString().toFloat()
            val y2 = binding.editTextBoardY2.text.toString().toFloat()
            val selectedDetectionMode = binding.autoCompleteDetectionMode.text.toString()
            val scaleFactor = binding.sliderScaleFactor.value
            val skipFrames = binding.editTextSkipFrames.text.toString().toInt()

            // --- PERBAIKAN 2: Ambil status dari Switch ---
            val phoneAlertEnabled = binding.switchPhoneAlert.isChecked

            if (x1 in 0.0..1.0 && y1 in 0.0..1.0 && x2 in 0.0..1.0 && y2 in 0.0..1.0 &&
                x1 < x2 && y1 < y2 &&
                scaleFactor in 0.5f..1.0f && skipFrames >= 1) {

                sharedPrefs.edit().apply {
                    putFloat("board_x1", x1)
                    putFloat("board_y1", y1)
                    putFloat("board_x2", x2)
                    putFloat("board_y2", y2)
                    putString("detection_mode", selectedDetectionMode)
                    putFloat("scale_factor", scaleFactor)
                    putInt("skip_frames", skipFrames)
                    // --- PERBAIKAN 3: Simpan status Switch baru ---
                    putBoolean("phone_alert_enabled", phoneAlertEnabled)
                    apply()
                }

                // --- PERBAIKAN 4: Kirim nilai baru (8 parameter) ke MainActivity ---
                listener?.onBoardSettingsSaved(x1, y1, x2, y2, selectedDetectionMode, scaleFactor, skipFrames, phoneAlertEnabled)
                Toast.makeText(context, "Pengaturan disimpan", Toast.LENGTH_SHORT).show()
                dismiss()
            } else {
                Toast.makeText(context, "Input tidak valid atau di luar jangkauan.", Toast.LENGTH_LONG).show()
            }
        } catch (e: NumberFormatException) {
            Toast.makeText(context, "Format angka pada input tidak valid", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDetach() {
        super.onDetach()
        listener = null
    }

    companion object {
        const val TAG = "BoardSettingsDialog"
    }
}
