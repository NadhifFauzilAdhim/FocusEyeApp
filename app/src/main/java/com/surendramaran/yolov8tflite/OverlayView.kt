package com.surendramaran.yolov8tflite // Pastikan paket ini sesuai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

class OverlayView(context: Context?, attrs: AttributeSet?) : View(context, attrs) {

    // Hasil dari YOLO Detector
    private var yoloResults = listOf<BoundingBox>()
    private var boxPaint = Paint()
    private var textBackgroundPaint = Paint()
    private var textPaint = Paint()
    private var bounds = Rect()

    // Hasil dari FaceMeshProcessor
    private var processedFaceData: ProcessedFaceData? = null
    private var faceLandmarkPaint = Paint()
    private var headPoseArrowPaint = Paint()
    private var gazeArrowPaint = Paint()
    private var boardAreaPaint = Paint()
    private var focusStatusPaint = Paint() // Untuk teks status fokus

    // Contoh Area Papan Tulis (koordinat ternormalisasi [0,1])
    // Anda mungkin ingin mendapatkan ini dari MainActivity atau sumber lain nantinya
    private var boardRectNormalized = RectF(0.25f, 0.15f, 0.75f, 0.40f)

    // Variabel untuk menyimpan status fokus per BoundingBox YOLO (jika sudah dicocokkan)
    // Key: Indeks BoundingBox di yoloResults, Value: Pair<Boolean (isLookingAtBoard), HeadDirection>
    private var yoloFocusStates: Map<Int, Pair<Boolean, HeadDirection>> = emptyMap()


    init {
        initPaints()
    }

    fun clear() {
        textPaint.reset()
        textBackgroundPaint.reset()
        boxPaint.reset()
        faceLandmarkPaint.reset()
        headPoseArrowPaint.reset()
        gazeArrowPaint.reset()
        boardAreaPaint.reset()
        focusStatusPaint.reset()
        invalidate() // Panggil invalidate setelah mereset semua
        initPaints() // Re-inisialisasi paint
    }

    private fun initPaints() {
        // Paint untuk YOLO
        textBackgroundPaint.color = Color.BLACK
        textBackgroundPaint.style = Paint.Style.FILL
        textBackgroundPaint.textSize = 30f // Ukuran teks bisa disesuaikan

        textPaint.color = Color.WHITE
        textPaint.style = Paint.Style.FILL
        textPaint.textSize = 30f

        try {
            boxPaint.color = ContextCompat.getColor(context!!, R.color.bounding_box_color)
        } catch (e: Exception) {
            boxPaint.color = Color.RED // Fallback color
        }
        boxPaint.strokeWidth = 6F // Sedikit lebih tipis
        boxPaint.style = Paint.Style.STROKE

        // Paint untuk FaceMesh
        faceLandmarkPaint.color = Color.parseColor("#A000FF00") // Hijau dengan Alpha
        faceLandmarkPaint.style = Paint.Style.FILL
        faceLandmarkPaint.strokeWidth = 4F

        headPoseArrowPaint.color = Color.CYAN
        headPoseArrowPaint.strokeWidth = 5F

        gazeArrowPaint.color = Color.MAGENTA
        gazeArrowPaint.strokeWidth = 3F

        boardAreaPaint.color = Color.YELLOW
        boardAreaPaint.style = Paint.Style.STROKE
        boardAreaPaint.strokeWidth = 2F
        boardAreaPaint.alpha = 100

        focusStatusPaint.color = Color.YELLOW
        focusStatusPaint.style = Paint.Style.FILL
        focusStatusPaint.textSize = 28f
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Gambar Area Papan Tulis (untuk debugging posisi)
        val boardRectPx = RectF(
            boardRectNormalized.left * width,
            boardRectNormalized.top * height,
            boardRectNormalized.right * width,
            boardRectNormalized.bottom * height
        )
        canvas.drawRect(boardRectPx, boardAreaPaint)

        // 1. Gambar Hasil Deteksi Objek (YOLO) dan Status Fokus
        yoloResults.forEachIndexed { index, result ->
            val left = result.x1 * width
            val top = result.y1 * height
            val right = result.x2 * width
            val bottom = result.y2 * height

            // Set warna bounding box berdasarkan status fokus (jika ada)
            val focusState = yoloFocusStates[index]
            val currentBoxPaint = Paint(boxPaint) // Salin paint default
            if (focusState != null) {
                currentBoxPaint.color = if (focusState.first) Color.GREEN else Color.RED
            }

            canvas.drawRect(left, top, right, bottom, currentBoxPaint)
            val yoloLabelText = "${result.clsName} (${String.format("%.2f", result.cnf)})"

            textBackgroundPaint.getTextBounds(yoloLabelText, 0, yoloLabelText.length, bounds)
            val textWidth = bounds.width()
            val textHeight = bounds.height()

            val textBgLeft = left
            val textBgTop = top - textHeight - (BOUNDING_RECT_TEXT_PADDING * 2) // Pindahkan lebih ke atas
            val textBgRight = left + textWidth + BOUNDING_RECT_TEXT_PADDING
            val textBgBottom = top - BOUNDING_RECT_TEXT_PADDING

            if (textBgTop > 0) { // Pastikan background teks tidak keluar atas layar
                canvas.drawRect(textBgLeft, textBgTop, textBgRight, textBgBottom, textBackgroundPaint)
                canvas.drawText(yoloLabelText, textBgLeft + BOUNDING_RECT_TEXT_PADDING / 2, textBgBottom - BOUNDING_RECT_TEXT_PADDING / 2, textPaint)
            }


            // Tampilkan status fokus dan arah kepala jika ada
            if (focusState != null) {
                val statusText = if (focusState.first) "FOKUS" else "TIDAK FOKUS"
                val headDirText = "Arah: ${focusState.second}"
                val fullStatusText = "$statusText | $headDirText"

                val statusTextY = if (textBgTop > 0) textBgTop - BOUNDING_RECT_TEXT_PADDING else top + textHeight + BOUNDING_RECT_TEXT_PADDING
                canvas.drawText(fullStatusText, left, statusTextY, focusStatusPaint)
            }
        }

        // 2. Gambar Hasil Face Mesh (Panah dan Landmark jika diperlukan)
        processedFaceData?.allFaceAnalytics?.forEach { faceAnalytics ->
            // Gambar Landmarks (OPSIONAL: bisa sangat ramai, nonaktifkan jika tidak perlu)
            // faceAnalytics.landmarks.forEach { landmark ->
            //     canvas.drawCircle(landmark.x * width, landmark.y * height, faceLandmarkPaint.strokeWidth, faceLandmarkPaint)
            // }

            // Gambar Panah Pose Kepala
            faceAnalytics.headPoseArrowStart?.let { start ->
                faceAnalytics.headPoseArrowEnd?.let { end ->
                    val startX = start.x * width
                    val startY = start.y * height
                    val endX = end.x * width
                    val endY = end.y * height
                    canvas.drawLine(startX, startY, endX, endY, headPoseArrowPaint)
                    drawArrowHead(canvas, headPoseArrowPaint, startX, startY, endX, endY)
                }
            }

            // Gambar Panah Tatapan Mata (Gaze)
            faceAnalytics.gazeArrowStart?.let { start ->
                faceAnalytics.gazeArrowEnd?.let { end ->
                    val startX = start.x * width
                    val startY = start.y * height
                    val endX = end.x * width
                    val endY = end.y * height
                    canvas.drawLine(startX, startY, endX, endY, gazeArrowPaint)
                    drawArrowHead(canvas, gazeArrowPaint, startX, startY, endX, endY, 15f) // Kepala panah lebih kecil
                }
            }
        }
    }

    private fun drawArrowHead(canvas: Canvas, paint: Paint, startX: Float, startY: Float, endX: Float, endY: Float, arrowHeadLength: Float = 25f) {
        if (startX == endX && startY == endY) return // Hindari NaN jika start dan end sama

        val angle = atan2((endY - startY).toDouble(), (endX - startX).toDouble()).toFloat()
        val arrowAngle = (PI / 9).toFloat() // Sudut sedikit lebih sempit untuk kepala panah

        canvas.drawLine(endX, endY, (endX - arrowHeadLength * cos(angle - arrowAngle)), (endY - arrowHeadLength * sin(angle - arrowAngle)), paint)
        canvas.drawLine(endX, endY, (endX - arrowHeadLength * cos(angle + arrowAngle)), (endY - arrowHeadLength * sin(angle + arrowAngle)), paint)
    }

    fun setYoloResults(boundingBoxes: List<BoundingBox>) {
        this.yoloResults = boundingBoxes
        // Invalidate akan dipanggil oleh setCombinedResults atau setelah semua data di-set
    }

    fun setFaceMeshResults(results: ProcessedFaceData?) {
        this.processedFaceData = results
        // Invalidate akan dipanggil oleh setCombinedResults atau setelah semua data di-set
    }

    // Fungsi baru untuk menerima status fokus yang sudah diproses
    fun setProcessedFocusStates(focusStates: Map<Int, Pair<Boolean, HeadDirection>>) {
        this.yoloFocusStates = focusStates
        invalidate() // Panggil invalidate di sini setelah semua data siap
    }
    fun updateBoardArea(newArea: RectF) {
        this.boardRectNormalized = newArea
        invalidate() // PENTING: Memanggil invalidate() untuk memicu penggambaran ulang View
    }


    fun clearFaceMeshResults() {
        this.processedFaceData = null
        // invalidate() // Sama seperti di atas
    }

    companion object {
        private const val BOUNDING_RECT_TEXT_PADDING = 8
    }
}