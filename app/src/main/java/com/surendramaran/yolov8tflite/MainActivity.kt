package com.surendramaran.yolov8tflite // Pastikan paket ini sesuai

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.RectF
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.surendramaran.yolov8tflite.Constants.FACEMESH_MODEL_PATH
import com.surendramaran.yolov8tflite.Constants.LABELS_PATH
import com.surendramaran.yolov8tflite.Constants.MODEL_PATH
import com.surendramaran.yolov8tflite.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(),
    Detector.DetectorListener,
    FaceMeshProcessor.FaceMeshListener,
    BoardSettingsDialogFragment.BoardSettingsListener {

    private data class TrackedStudent(
        val id: Int,
        var bbox: RectF,
        var lastSeenTime: Long = System.currentTimeMillis(),
        var isFocused: Boolean = true,
        var unfocusedStartTime: Long = 0L,
        var alarmPlayedForThisUnfocusedPeriod: Boolean = false
    )

    private lateinit var binding: ActivityMainBinding
    private var isFrontCamera = false

    private val trackedStudents = mutableMapOf<Int, TrackedStudent>()
    private var nextTrackId = 0
    private val UNFOCUSED_ALARM_THRESHOLD_MS = 3000L

    private var isAnalysisRunning = false
    private var blinkingAnimation: Animation? = null
    private var analysisSessionStartTime: Long = 0L
    private var currentSessionId: Long? = null
    private var lastRotatedBitmap: Bitmap? = null

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var detector: Detector
    private lateinit var faceMeshProcessor: FaceMeshProcessor
    private lateinit var cameraExecutor: ExecutorService
    private var lastYoloResults: List<BoundingBox> = emptyList()
    private var lastFaceData: ProcessedFaceData? = null // <-- PERBAIKAN: Variabel untuk menyimpan hasil FaceMesh
    private var yoloInferenceTime: Long = 0
    private var faceMeshInferenceTime: Long = 0
    private lateinit var barChart: BarChart
    private var accumulatedFocusedFrames: Long = 0
    private var accumulatedUnfocusedFrames: Long = 0
    private var currentBoardArea = RectF(0.25f, 0.15f, 0.75f, 0.40f)
    private var currentDetectionMode = "Both"
    private var currentScaleFactor = 1.0f
    private var currentSkipFrames = 1
    private var analyzerFrameCounter = 0
    private var phoneAlertPlayer: MediaPlayer? = null
    private var unfocusedAlertPlayer: MediaPlayer? = null
    private var isPhoneDetectedInPreviousFrame = false
    private var isPhoneAlertEnabled = true
    private var isUnfocusedAlertEnabled = true

    private val db by lazy { AppDatabase.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        barChart = binding.barChartFocus
        setupBarChart()
        updateFocusChart()
        setupUIListeners()

        blinkingAnimation = AnimationUtils.loadAnimation(this, R.anim.blinking_rec)
        loadAppSettings()

        detector = Detector(baseContext, MODEL_PATH, LABELS_PATH, this)
        detector.setup()

        faceMeshProcessor = FaceMeshProcessor(baseContext, FACEMESH_MODEL_PATH, this)
        applyCurrentSettings()

        cameraExecutor = Executors.newSingleThreadExecutor()
        try {
            phoneAlertPlayer = MediaPlayer.create(this, R.raw.alert)
            unfocusedAlertPlayer = MediaPlayer.create(this, R.raw.alert)
        } catch (e: Exception) {
            Log.e(TAG, "Error creating MediaPlayer: ${e.message}")
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun setupUIListeners() {
        binding.buttonSwitchCamera.setOnClickListener {
            stopCurrentAnalysis()
            isFrontCamera = !isFrontCamera
            bindCameraUseCases()
        }
        binding.buttonSettings.setOnClickListener {
            stopCurrentAnalysis()
            BoardSettingsDialogFragment().show(supportFragmentManager, BoardSettingsDialogFragment.TAG)
        }
        binding.buttonHistory.setOnClickListener {
            startActivity(Intent(this, HistoryActivity::class.java))
        }
        binding.buttonToggleChart.setOnClickListener {
            binding.cardChart.visibility = if (binding.cardChart.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        binding.fabStartStop.setOnClickListener {
            if (isAnalysisRunning) {
                stopCurrentAnalysis()
            } else {
                startNewAnalysis()
            }
        }
    }

    private fun startNewAnalysis() {
        isAnalysisRunning = true
        resetChartAccumulators()
        analysisSessionStartTime = System.currentTimeMillis()

        lifecycleScope.launch {
            val session = FocusSession(timestamp = analysisSessionStartTime, durationInSeconds = 0, focusedCount = 0, unfocusedCount = 0, totalStudents = 0)
            withContext(Dispatchers.IO) {
                currentSessionId = db.focusSessionDao().insert(session)
            }
        }

        binding.fabStartStop.text = "Hentikan Analisis"
        binding.fabStartStop.setIconResource(R.drawable.baseline_adjust_24)
        binding.recIndicator.visibility = View.VISIBLE
        binding.recIndicator.startAnimation(blinkingAnimation)
        Toast.makeText(this, "Analisis dimulai...", Toast.LENGTH_SHORT).show()
    }

    private fun stopCurrentAnalysis() {
        if (!isAnalysisRunning) return
        isAnalysisRunning = false
        val durationInMillis = System.currentTimeMillis() - analysisSessionStartTime
        val durationInSeconds = (durationInMillis / 1000).toInt()
        currentSessionId?.let {
            updateFinalSessionInDatabase(it, trackedStudents.size, durationInSeconds)
        }
        currentSessionId = null
        binding.fabStartStop.text = "Mulai Analisis"
        binding.fabStartStop.setIconResource(R.drawable.baseline_play_arrow_24)
        binding.recIndicator.clearAnimation()
        binding.recIndicator.visibility = View.GONE
        Toast.makeText(this, "Analisis dihentikan. Data tersimpan.", Toast.LENGTH_SHORT).show()
    }

    private fun playPhoneSound() {
        if (phoneAlertPlayer?.isPlaying == false) {
            phoneAlertPlayer?.start()
        }
    }

    private fun playUnfocusedSound() {
        if (unfocusedAlertPlayer?.isPlaying == false) {
            unfocusedAlertPlayer?.start()
        }
    }

    private fun resetChartAccumulators() {
        accumulatedFocusedFrames = 0
        accumulatedUnfocusedFrames = 0
        trackedStudents.clear()
        nextTrackId = 0
        updateFocusChart()
    }

    private fun loadAppSettings() {
        val sharedPrefs = getSharedPreferences("AppGlobalSettings", Context.MODE_PRIVATE)
        currentBoardArea = RectF(
            sharedPrefs.getFloat("board_x1", 0.25f),
            sharedPrefs.getFloat("board_y1", 0.15f),
            sharedPrefs.getFloat("board_x2", 0.75f),
            sharedPrefs.getFloat("board_y2", 0.40f)
        )
        currentDetectionMode = sharedPrefs.getString("detection_mode", "Both") ?: "Both"
        currentScaleFactor = sharedPrefs.getFloat("scale_factor", 1.0f)
        currentSkipFrames = sharedPrefs.getInt("skip_frames", 1).coerceAtLeast(1)
        isPhoneAlertEnabled = sharedPrefs.getBoolean("phone_alert_enabled", true)
        isUnfocusedAlertEnabled = sharedPrefs.getBoolean("unfocused_alert_enabled", true)
    }

    private fun applyCurrentSettings() {
        faceMeshProcessor.updateBoardArea(currentBoardArea)
        binding.overlay.updateBoardArea(currentBoardArea)
        val focusMode = when(currentDetectionMode) {
            "Iris Detection Only" -> DetectionFocusMode.IRIS_ONLY
            "Head Pose Only" -> DetectionFocusMode.HEAD_ONLY
            else -> DetectionFocusMode.BOTH
        }
        faceMeshProcessor.setDetectionFocusMode(focusMode)
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val cameraProvider = cameraProvider ?: return
        val rotation = binding.viewFinder.display.rotation
        val cameraSelector = CameraSelector.Builder().requireLensFacing(if (isFrontCamera) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK).build()
        preview = Preview.Builder().setTargetAspectRatio(AspectRatio.RATIO_16_9).setTargetRotation(rotation).build()
        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setTargetRotation(rotation)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()

        analyzerFrameCounter = 0
        imageAnalyzer?.setAnalyzer(cameraExecutor) { imageProxy ->
            analyzerFrameCounter++
            if (analyzerFrameCounter % currentSkipFrames != 0) {
                imageProxy.close()
                return@setAnalyzer
            }

            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )
            imageProxy.use { it.planes[0].buffer.rewind(); bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer) }

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
                if (isFrontCamera) {
                    postScale(-1f, 1f, imageProxy.width / 2f, imageProxy.height / 2f)
                }
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
            )

            val finalBitmap = if (currentScaleFactor in 0.1f..0.99f) {
                val newWidth = (rotatedBitmap.width * currentScaleFactor).toInt()
                val newHeight = (rotatedBitmap.height * currentScaleFactor).toInt()
                if (newWidth > 0 && newHeight > 0) Bitmap.createScaledBitmap(rotatedBitmap, newWidth, newHeight, true) else rotatedBitmap
            } else {
                rotatedBitmap
            }

            lastRotatedBitmap = finalBitmap

            // --- PERBAIKAN KEDIPAN (FLICKER) ---
            if (analyzerFrameCounter % 2 == 0) {
                detector.detect(finalBitmap)
                // Langsung panggil updateStatsAndOverlay, yang akan menggunakan lastFaceData
                updateStatsAndOverlay()
            } else {
                faceMeshProcessor.process(finalBitmap)
                // Hasil FaceMesh akan memanggil updateStatsAndOverlay dari callback-nya
            }
        }

        cameraProvider.unbindAll()
        try {
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            preview?.setSurfaceProvider(binding.viewFinder.surfaceProvider)
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    // Fungsi ini tidak lagi digunakan karena logika rotasi sudah inline di bindCameraUseCases.
    // Anda bisa menghapusnya untuk membersihkan kode.
    /*
    private fun rotateBitmap(bitmap: Bitmap): Bitmap {
        ...
    }
    */

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        this.lastYoloResults = boundingBoxes
        this.yoloInferenceTime = inferenceTime
        // Tidak perlu panggil update di sini, karena sudah ditangani oleh analyzer loop
    }

    override fun onEmptyDetect() {
        this.lastYoloResults = emptyList()
        this.yoloInferenceTime = 0
        updateStatsAndOverlay() // PERBAIKAN: Panggil fungsi tanpa parameter
    }

    override fun onFaceMeshResults(processedData: ProcessedFaceData?, inferenceTime: Long) {
        this.faceMeshInferenceTime = inferenceTime
        this.lastFaceData = processedData // PERBAIKAN: Simpan hasil FaceMesh terakhir
        updateStatsAndOverlay() // PERBAIKAN: Panggil fungsi tanpa parameter
    }

    override fun onFaceMeshError(error: String) {
        runOnUiThread { Toast.makeText(this, "FaceMesh Error: $error", Toast.LENGTH_SHORT).show() }
        this.lastFaceData = null // PERBAIKAN: Reset data jika error
        updateStatsAndOverlay() // PERBAIKAN: Panggil fungsi tanpa parameter
    }

    // --- PERBAIKAN KEDIPAN (FLICKER): Fungsi diubah menjadi tanpa parameter ---
    private fun updateStatsAndOverlay() {
        // Langsung gunakan variabel class: lastYoloResults dan lastFaceData
        val processedStates = updateTrackedStudents(lastYoloResults, lastFaceData)

        val totalStudents = trackedStudents.size
        val focusedStudents = trackedStudents.values.count { it.isFocused }

        runOnUiThread {
            binding.inferenceTime.text = "${yoloInferenceTime + faceMeshInferenceTime}ms"
            binding.textViewTotalStudents.text = "Siswa: $totalStudents"
            binding.textViewFocusedStudents.text = "Fokus: $focusedStudents"

            if (isAnalysisRunning) {
                accumulatedFocusedFrames += focusedStudents
                accumulatedUnfocusedFrames += (totalStudents - focusedStudents)
                updateFocusChart()
            }

            binding.overlay.setYoloResults(lastYoloResults)
            binding.overlay.setFaceMeshResults(lastFaceData) // Gunakan lastFaceData
            binding.overlay.setProcessedFocusStates(processedStates)
        }
    }

    private fun updateTrackedStudents(yoloResults: List<BoundingBox>, faceData: ProcessedFaceData?): Map<Int, Pair<Boolean, HeadDirection>> {
        val yoloStudentBoxes = yoloResults.withIndex().filter { isStudent(it.value) }
        val matchedTrackIds = mutableSetOf<Int>()
        val currentFrameStates = mutableMapOf<Int, Pair<Boolean, HeadDirection>>()
        val MATCHING_THRESHOLD = 0.2f

        yoloStudentBoxes.forEach { (yoloIndex, box) ->
            val boxCenter = PointF(box.cx, box.cy)
            val bestMatch = trackedStudents.minByOrNull { distance(boxCenter, PointF(it.value.bbox.centerX(), it.value.bbox.centerY())) }

            val student: TrackedStudent
            if (bestMatch != null && distance(boxCenter, PointF(bestMatch.value.bbox.centerX(), bestMatch.value.bbox.centerY())) < MATCHING_THRESHOLD) {
                student = bestMatch.value
                student.bbox = RectF(box.x1, box.y1, box.x2, box.y2)
                student.lastSeenTime = System.currentTimeMillis()
                matchedTrackIds.add(student.id)
            } else {
                student = TrackedStudent(nextTrackId, RectF(box.x1, box.y1, box.x2, box.y2))
                trackedStudents[nextTrackId] = student
                matchedTrackIds.add(nextTrackId)
                nextTrackId++
            }

            val faceMatch = faceData?.allFaceAnalytics?.find {
                val nose = it.landmarks.getOrNull(FaceMeshProcessor.NOSE_TIP)
                nose != null && student.bbox.contains(nose.x, nose.y)
            }

            // Dengan `lastFaceData`, `faceMatch` tidak akan selalu null di frame YOLO,
            // sehingga `isFocused` tidak lagi berkedip.
            student.isFocused = faceMatch?.isLookingAtBoard ?: false
            currentFrameStates[yoloIndex] = student.isFocused to (faceMatch?.headDirection ?: HeadDirection.UNKNOWN)

            if (isAnalysisRunning && isUnfocusedAlertEnabled && !student.isFocused) {
                if (student.unfocusedStartTime == 0L) {
                    student.unfocusedStartTime = System.currentTimeMillis()
                    student.alarmPlayedForThisUnfocusedPeriod = false
                } else {
                    val unfocusedDuration = System.currentTimeMillis() - student.unfocusedStartTime
                    if (unfocusedDuration > UNFOCUSED_ALARM_THRESHOLD_MS && !student.alarmPlayedForThisUnfocusedPeriod) {
                        playUnfocusedSound()
                        student.alarmPlayedForThisUnfocusedPeriod = true
                        lastRotatedBitmap?.let { captureUnfocusedEvent(it, student.bbox) }
                    }
                }
            } else {
                student.unfocusedStartTime = 0L
                student.alarmPlayedForThisUnfocusedPeriod = false
            }
        }

        trackedStudents.entries.removeIf { !matchedTrackIds.contains(it.key) && System.currentTimeMillis() - it.value.lastSeenTime > 2000L }
        val isPhoneDetected = yoloResults.any { it.clsName.equals("cell phone", ignoreCase = true) }
        if (isAnalysisRunning && isPhoneAlertEnabled && isPhoneDetected && !isPhoneDetectedInPreviousFrame) {
            playPhoneSound()
        }
        isPhoneDetectedInPreviousFrame = isPhoneDetected

        return currentFrameStates
    }

    private fun captureUnfocusedEvent(frameToSave: Bitmap, studentBbox: RectF) {
        val sessionId = currentSessionId ?: return
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val left = (studentBbox.left * frameToSave.width).toInt().coerceAtLeast(0)
                    val top = (studentBbox.top * frameToSave.height).toInt().coerceAtLeast(0)
                    val width = (studentBbox.width() * frameToSave.width).toInt()
                    val height = (studentBbox.height() * frameToSave.height).toInt()

                    if (left + width > frameToSave.width || top + height > frameToSave.height || width <= 0 || height <= 0) {
                        Log.w(TAG, "Invalid crop coordinates, skipping capture. ($left, $top, $width, $height) in ${frameToSave.width}x${frameToSave.height}")
                        return@withContext
                    }
                    val croppedBitmap = Bitmap.createBitmap(frameToSave, left, top, width, height)
                    val imagePath = saveImageToInternalStorage(croppedBitmap)
                    if (imagePath != null) {
                        val event = UnfocusedEvent(sessionId = sessionId, timestamp = System.currentTimeMillis(), imagePath = imagePath)
                        db.focusSessionDao().insertUnfocusedEvent(event)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error capturing unfocused event: ${e.message}", e)
                }
            }
        }
    }

    private fun saveImageToInternalStorage(bitmap: Bitmap): String? {
        val filename = "unfocused_${System.currentTimeMillis()}.jpg"
        val file = File(filesDir, filename)
        return try {
            FileOutputStream(file).use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
            }
            file.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun updateFinalSessionInDatabase(sessionId: Long, totalStudents: Int, durationInSeconds: Int) {
        if (accumulatedFocusedFrames > 0 || accumulatedUnfocusedFrames > 0) {
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val session = FocusSession(
                        id = sessionId,
                        timestamp = analysisSessionStartTime,
                        focusedCount = accumulatedFocusedFrames.toInt(),
                        unfocusedCount = accumulatedUnfocusedFrames.toInt(),
                        totalStudents = totalStudents,
                        durationInSeconds = durationInSeconds
                    )
                    db.focusSessionDao().update(session)
                }
            }
        }
    }

    private fun distance(p1: PointF, p2: PointF): Float = sqrt((p1.x - p2.x).pow(2) + (p1.y - p2.y).pow(2))
    private fun isStudent(box: BoundingBox): Boolean = box.clsName.equals("person", ignoreCase = true)

    override fun onBoardSettingsSaved(
        x1: Float, y1: Float, x2: Float, y2: Float,
        detectionMode: String, scaleFactor: Float, skipFrames: Int,
        phoneAlertEnabled: Boolean,
        unfocusedAlertEnabled: Boolean
    ) {
        currentBoardArea = RectF(x1, y1, x2, y2)
        currentDetectionMode = detectionMode
        currentScaleFactor = scaleFactor
        currentSkipFrames = skipFrames
        this.isPhoneAlertEnabled = phoneAlertEnabled
        this.isUnfocusedAlertEnabled = unfocusedAlertEnabled
        applyCurrentSettings()
        resetChartAccumulators()
    }

    private fun setupBarChart() {
        barChart.apply {
            description.isEnabled = false
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setFitBars(true)
            isDragEnabled = false
            setScaleEnabled(false)
            setPinchZoom(false)
            legend.isEnabled = false
            axisRight.isEnabled = false
            xAxis.apply {
                position = com.github.mikephil.charting.components.XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                granularity = 1f
                valueFormatter = IndexAxisValueFormatter(listOf("Fokus", "Tdk Fokus"))
                textColor = ContextCompat.getColor(this@MainActivity, R.color.md_theme_onSurface)
            }
            axisLeft.apply {
                setDrawGridLines(true)
                axisMinimum = 0f
                granularity = 1f
                textColor = ContextCompat.getColor(this@MainActivity, R.color.md_theme_onSurface)
            }
        }
    }

    private fun updateFocusChart() {
        val total = accumulatedFocusedFrames + accumulatedUnfocusedFrames
        if (total <= 0) {
            barChart.data = null
            barChart.invalidate()
            return
        }
        val entries = arrayListOf(BarEntry(0f, accumulatedFocusedFrames.toFloat()), BarEntry(1f, accumulatedUnfocusedFrames.toFloat()))
        val dataSet = BarDataSet(entries, "Status Fokus").apply {
            colors = listOf(ContextCompat.getColor(this@MainActivity, R.color.focused_green), ContextCompat.getColor(this@MainActivity, R.color.unfocused_red))
            valueTextColor = ContextCompat.getColor(this@MainActivity, R.color.md_theme_onSurface)
            valueTextSize = 12f
        }
        barChart.data = BarData(dataSet).apply { barWidth = 0.5f }
        barChart.axisLeft.axisMaximum = total.toFloat()
        barChart.invalidate()
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (it[Manifest.permission.CAMERA] == true) startCamera() else Toast.makeText(this, "Izin kamera diperlukan.", Toast.LENGTH_LONG).show()
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }

    override fun onResume() { super.onResume(); if (allPermissionsGranted()) startCamera() }

    override fun onPause() { super.onPause(); cameraProvider?.unbindAll(); stopCurrentAnalysis() }

    override fun onDestroy() { super.onDestroy(); detector.clear(); faceMeshProcessor.clear(); cameraExecutor.shutdown(); phoneAlertPlayer?.release(); unfocusedAlertPlayer?.release() }

    companion object { private const val TAG = "MainActivity"; private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA) }
}