package com.example.ocrodds.ui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.ocrodds.OcrOddsApplication
import com.example.ocrodds.Utils
import com.example.ocrodds.data.OcrOddsDataProvider
import com.example.ocrodds.data.OcrOddsRepository
import com.example.ocrodds.imgp.correctImageOrientation
import com.example.ocrodds.imgp.eraseCircle
import com.example.ocrodds.imgp.maskBitmapExceptRect
import com.example.ocrodds.imgp.preprocessImage
import com.example.ocrodds.imgp.rotateBitmap
import com.example.ocrodds.model.Odds
import com.example.ocrodds.model.OddsType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.opencv.core.Rect
import java.io.File
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

class OcrOddsViewModel(
    private val ocrOddsRepository: OcrOddsRepository
): ViewModel() {
    private val _uiState = MutableStateFlow(OcrOddsUiState(
        odds = Odds(
            raceday = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
            race = OcrOddsDataProvider.getRacenums()[0],
            sitecode = OcrOddsDataProvider.getSites()[0],
            data = emptyList<List<String>>(),
            qData = emptyList<List<String>>()
        )
    ))
    val uiState = _uiState.asStateFlow()

    private val _imageUiState = MutableStateFlow(ImageUiState())
    val imageUiState = _imageUiState.asStateFlow()

    private val _camera = mutableStateOf<Camera?>(null)
    val camera: State<Camera?> = _camera

    fun toggleOddsTableButtonBarVisibility(oddsTableButtonBarVisibleIndex: Int) {
        updateOddsTableButtonBarVisibleIndex(
            if (uiState.value.oddsTableButtonBarVisibleIndex == oddsTableButtonBarVisibleIndex) -1 else oddsTableButtonBarVisibleIndex
        )
    }

    private fun updateOddsTableButtonBarVisibleIndex(oddsTableButtonBarVisibleIndex: Int) {
        _uiState.update {
            it.copy(
                oddsTableButtonBarVisibleIndex = oddsTableButtonBarVisibleIndex
            )
        }
    }

    fun updateCamera(camera: Camera) {
        _camera.value = camera
    }

    fun onCameraZoom(zoomSliderPos: Float) {
        _uiState.update {
            it.copy(
                zoomSliderPos = zoomSliderPos
            )
        }
        _camera.value?.cameraControl?.setZoomRatio(zoomSliderPos)
    }

    fun deleteOdds(deleteIndex: Int) {
        _uiState.update {
            when(uiState.value.oddsType) {
                OddsType.WINPLACE -> {
                    it.copy(
                        odds = it.odds.copy(
                            data = it.odds.data.filterIndexed { index, _ -> index != deleteIndex }
                        )
                    )
                }
                OddsType.QUINELLA -> {
                    it.copy(
                        odds = it.odds.copy(
                            qData = it.odds.qData.filterIndexed { index, _ -> index != deleteIndex }
                        )
                    )
                }
            }

        }
        updateOddsTableButtonBarVisibleIndex(-1)
        updateTrackTake()
    }

    fun insertOdds(index: Int) {
        _uiState.update {
            when(uiState.value.oddsType) {
                OddsType.WINPLACE -> {
                    it.copy(
                        odds = it.odds.copy(
                            data = it.odds.data.toMutableList().apply {
                                add(index, List(it.odds.data[0].size) {""})
                            }
                        )
                    )
                }
                OddsType.QUINELLA -> {
                    it.copy(
                        odds = it.odds.copy(
                            qData = it.odds.qData.toMutableList().apply {
                                add(index, List(it.odds.qData[0].size) {""})
                            }
                        )
                    )
                }
            }

        }
        updateOddsTableButtonBarVisibleIndex(-1)
        updateTrackTake()
    }

    private fun sortTextBlock(visionTextList: MutableList<Text.TextBlock>): List<String> {
        val sortedInput = visionTextList.sortedBy { it.boundingBox?.top ?: 0 }
        val grouped = mutableListOf<MutableList<Text.TextBlock>>()

        val groupYPosDiffThreshold = 50

        for (item in sortedInput) {
            // Find group by compare y pos of item and y pos of each group last element
            val group = grouped.find {
                abs((it.last().boundingBox?.top ?: 0) - (item.boundingBox?.top ?: 0)) < groupYPosDiffThreshold
            }

            if (group != null) {
                group.add(item)
            } else {
                grouped.add(mutableListOf(item))
            }
        }

        Log.d("sortTextBlock", grouped
            .map { group -> group.sortedBy { it.boundingBox?.left ?: 0 }.joinToString("@@") {
                it.text
            } }.toString()
        )

        return grouped
            .map { group -> group.sortedBy { it.boundingBox?.left ?: 0 }.joinToString(" ") {
                it.text
            } }
    }

    private fun splitTextIntoTwoColumns(visionText: Text): List<List<String>> {
        // Get all text blocks
        val blocks = visionText.textBlocks

        if (blocks.isEmpty()) return listOf(emptyList<String>(), emptyList<String>())

        val midPoint = blocks.getOrNull(0)?.boundingBox?.centerX() ?: 0

        // Separate blocks into left and right columns
        val leftColumn = mutableListOf<Text.TextBlock>()
        val rightColumn = mutableListOf<Text.TextBlock>()

        for (block in blocks) {
            val centerX = block.boundingBox?.centerX() ?: 0
            if (abs(centerX - midPoint) < 300) {
                leftColumn.add(block)
            } else {
                rightColumn.add(block)
            }
        }

        return listOf(
            sortTextBlock(leftColumn),
            sortTextBlock(rightColumn)
        )
    }

    private fun splitTextIntoThreeColumns(visionText: Text): List<List<String>> {
        val blocks = visionText.textBlocks
        if (blocks.isEmpty()) {
            return listOf(emptyList(), emptyList(), emptyList())
        }

        // ---------------------------------------------------------------------
        // 1. Determine the overall horizontal span of the page
        // ---------------------------------------------------------------------
        val allX = blocks.mapNotNull { it.boundingBox?.let { b -> b.left to b.right } }
        if (allX.isEmpty()) {
            return listOf(emptyList(), emptyList(), emptyList())
        }

        val minX = allX.minOf { it.first }.toFloat()
        val maxX = allX.maxOf { it.second }.toFloat()
        val pageWidth = maxX - minX

        // Divide the page into three roughly equal zones
        val third = pageWidth / 3f
        val leftEdge   = minX + third          // right border of the left column
        val centreEdge = minX + 2 * third      // right border of the centre column

        // ---------------------------------------------------------------------
        // 2. Bucket each block into the appropriate column
        // ---------------------------------------------------------------------
        val left   = mutableListOf<Text.TextBlock>()
        val centre = mutableListOf<Text.TextBlock>()
        val right  = mutableListOf<Text.TextBlock>()

        for (block in blocks) {
            val box = block.boundingBox ?: continue
            val centreX = box.centerX().toFloat()

            when {
                centreX < leftEdge               -> left.add(block)
                centreX < centreEdge            -> centre.add(block)
                else                             -> right.add(block)
            }
        }

        // ---------------------------------------------------------------------
        // 3. Sort each column top-to-bottom and extract lines
        // ---------------------------------------------------------------------
        return listOf(
            sortTextBlock(left),
            sortTextBlock(centre),
            sortTextBlock(right)
        )
    }

    private fun updateOddsDataFromRecognitionResult(recognitionResult: Text) {
        val tableData = when(uiState.value.oddsType) {
            OddsType.WINPLACE -> handleWinPlaceRecognitionResult(recognitionResult)
            OddsType.QUINELLA -> handleQuinellaRecognitionResult(recognitionResult)
        }

        addOdds(tableData)
    }

    private fun handleWinPlaceRecognitionResult(recognitionResult: Text): List<List<String>> {
        val columns: List<List<String>> = splitTextIntoTwoColumns(recognitionResult).map { eachRecognitionResult ->
            val cleanValues = eachRecognitionResult.map {
                it.trim()
                    .replace("\n", " ")
                    .replace(Regex("[^\\d\\s]+"), "")
            }
            cleanValues
        }

        val leftColumn = columns.getOrNull(0) ?: emptyList()
        val rightColumn = columns.getOrNull(1) ?: emptyList()
        val paddedRightColumn = rightColumn + List(maxOf(0, leftColumn.size - rightColumn.size)) { "" }
        val zippedColumns = leftColumn.zip(paddedRightColumn)

        val tableData = zippedColumns.map{
            it.toList()
        }.map { row ->
            val (tab1, win) = if(row[0].contains(" ")) {
                if(row[0].split(" ").distinct().size > 1)
                    row[0].split(" ").distinct()
                else
                    row[0].split(" ")
            } else listOf("", row[0])

            val (tab2, place) = if(row[1].contains(" ")) {
                if(row[1].split(" ").distinct().size > 1)
                    row[1].split(" ").distinct()
                else
                    row[1].split(" ")
            } else listOf("", row[1])

            listOf(
                tab1,
                if(win.length > 1) win.substring(0, win.length - 1) + "." + win.takeLast(1) else win,
                tab2,
                if(place.length > 1) place.substring(0, place.length - 1) + "." + place.takeLast(1) else place,
            )
        }

        return tableData
    }

    private fun handleQuinellaRecognitionResult(recognitionResult: Text): List<List<String>> {
        val columns: List<List<String>> = splitTextIntoThreeColumns(recognitionResult).map { eachRecognitionResult ->
            val cleanValues = eachRecognitionResult.map {
                it.trim()
                    .replace("\n", " ")
                    .replace(Regex("[^\\d\\s]+"), "")
            }
            cleanValues
        }

        val tableData = Utils.flattenColumnWise(columns).map {
            val (tab, qodds) = if (it.contains(" ")) {
                val data = it.split(" ")
                if(data.size == 2) {
                    if(data[0].length > 1) {
                        if(data[0].length == 2)
                            Pair(data[0].substring(0, data[0].length - 1) + "+" + data[0].takeLast(1), data[1])
                        else
                            Pair(data[0].substring(0, data[0].length - 2) + "+" + data[0].takeLast(2), data[1])
                    } else {
                        Pair(data[0], data[1])
                    }
                } else {
                    Pair("${data[0]}+${data[1]}", data[2])
                }
            } else {
                Pair(it, "")
            }

            listOf(
                tab,
                if(qodds.length > 1) qodds.substring(0, qodds.length - 1) + "." + qodds.takeLast(1) else qodds
            )
        }

        return tableData
    }

    fun updateRaceDay(raceday: String) {
        _uiState.update {
            it.copy(
                odds = it.odds.copy(
                    raceday = raceday
                )
            )
        }
    }

    fun updateRacenum(racenum: String) {
        _uiState.update {
            it.copy(
                odds = it.odds.copy(
                    race = racenum
                )
            )
        }
    }

    fun updateSite(site: String) {
        _uiState.update {
            it.copy(
                odds = it.odds.copy(
                    sitecode = site
                )
            )
        }
    }

    private fun addOdds(tableData: List<List<String>>) {
        when(uiState.value.oddsType) {
            OddsType.WINPLACE -> updateOdds(Utils.mergeTables(uiState.value.odds.data, tableData))
            OddsType.QUINELLA -> updateOdds(Utils.mergeTables(uiState.value.odds.qData, tableData))
        }
    }

    private fun updateOdds(tableData: List<List<String>>) {
        _uiState.update {
            when(uiState.value.oddsType) {
                OddsType.WINPLACE -> {
                    it.copy(
                        odds = it.odds.copy(
                            data = tableData
                        )
                    )
                }
                OddsType.QUINELLA -> {
                    it.copy(
                        odds = it.odds.copy(
                            qData = tableData
                        )
                    )
                }
            }
        }
        updateTrackTake()
    }

    fun updateOdds(rowIndex: Int, colIndex: Int, newValue: String) {
        _uiState.update {
            when(uiState.value.oddsType) {
                OddsType.WINPLACE -> {
                    it.copy(
                        odds = it.odds.copy(
                            data = it.odds.data.mapIndexed { i, rowData ->
                                rowData.mapIndexed{ j, value ->
                                    if(rowIndex == i && colIndex == j) newValue else value
                                }
                            }
                        )
                    )
                }
                OddsType.QUINELLA -> {
                    it.copy(
                        odds = it.odds.copy(
                            qData = it.odds.qData.mapIndexed { i, rowData ->
                                rowData.mapIndexed{ j, value ->
                                    if(rowIndex == i && colIndex == j) newValue else value
                                }
                            }
                        )
                    )
                }
            }
        }
        updateTrackTake()
    }

    private fun clearOdds() {
        _uiState.update {
            it.copy(
                odds = it.odds.copy(
                    data = emptyList<List<String>>(),
                    qData = emptyList<List<String>>()
                )
            )
        }
    }

    fun onReset() {
        clearOdds()
        updateOddsTableButtonBarVisibleIndex(-1)
        updateTrackTake()
    }

    fun updateOddsType(oddsType: OddsType) {
        _uiState.update {
            it.copy(
                oddsType = oddsType
            )
        }
        when(uiState.value.oddsType) {
            OddsType.WINPLACE -> updateNavBarIndex(0)
            OddsType.QUINELLA -> updateNavBarIndex(1)
        }

    }

    fun updateNavBarIndex(navBarIndex: Int) {
        _uiState.update {
            it.copy(
                navBarIndex = navBarIndex
            )
        }
        updateTrackTake()
    }

    @SuppressLint("DefaultLocale")
    fun updateTrackTake() {
        val oddsIndex = when (uiState.value.oddsType) {
            OddsType.WINPLACE -> 1
            OddsType.QUINELLA -> 1
        }

        val oddsData = when (uiState.value.oddsType) {
            OddsType.WINPLACE -> uiState.value.odds.data
            OddsType.QUINELLA -> uiState.value.odds.qData
        }
        val canCalculateTrackTake = if(oddsData.isNotEmpty()) {
            oddsData.all { row ->
                row.getOrNull(oddsIndex)?.isNotBlank() == true
            }
        } else {
            false
        }
        val trackTake = if(canCalculateTrackTake) {
            val trackTakeRaw = 100 - 100 / oddsData.sumOf { 1 / (it[oddsIndex].toDoubleOrNull() ?: 9999.0) }
            String.format("%.4f",  trackTakeRaw)
        } else {
            "-"
        }

        _uiState.update {
            it.copy(
                trackTake = trackTake
            )
        }
    }

    fun uploadToSCP(context: Context) {
        viewModelScope.launch {
            val dateFolder = uiState.value.odds.raceday
            val filename = when(uiState.value.oddsType) {
                OddsType.WINPLACE -> "${uiState.value.odds.sitecode}-${uiState.value.odds.race}.json"
                OddsType.QUINELLA -> "${uiState.value.odds.sitecode}-${uiState.value.odds.race}.json"
            }
            val result = ocrOddsRepository.uploadToSCP(
                context,
                Json.encodeToString(uiState.value.odds),
                dateFolder,
                filename
            )
            result.onSuccess {
                Toast.makeText(context, "Upload successful", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                Toast.makeText(context, "Upload failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun updateOriginalBitmap(originalBitmap: Bitmap?) {
        _imageUiState.update {
            it.copy(
                originalBitmap = originalBitmap
            )
        }
    }

    fun updateProcessedBitmap(processedBitmap: Bitmap?) {
        _imageUiState.update {
            it.copy(
                processedBitmap = processedBitmap
            )
        }
    }

    fun updateImageProcessingState(isProcessing: Boolean) {
        _imageUiState.update {
            it.copy(
                isProcessing = isProcessing
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    fun onTakePicture(
        imageCapture: ImageCapture,
        context: Context,
        onRecognitionFailure: () -> Unit = {},
        onRecognitionFinish: () -> Unit = {}
    ) {
        updateImageProcessingState(true)
        val photoFile = File(
            context.getExternalFilesDir(null),
            SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
                .format(System.currentTimeMillis()) + ".jpg"
        )

        if (!photoFile.parentFile?.exists()!! && !photoFile.parentFile?.mkdirs()!!) {
            Toast.makeText(context, "Failed to create directory", Toast.LENGTH_SHORT).show()
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(
            outputOptions,
            context.mainExecutor,
            object : ImageCapture.OnImageSavedCallback {
                @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // Load image from file
                    //val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                    val bitmap = photoFile.correctImageOrientation()
                    updateOriginalBitmap(bitmap)

                    // Apply preprocessing
                    val maskRegion = Rect(
                        ((bitmap.width - bitmap.width / 1.5) / 2).toInt(),
                        ((bitmap.height - bitmap.height / 1.5) / 2).toInt(),
                        (bitmap.width / 1.5).toInt(),
                        (bitmap.height / 1.5).toInt()
                    )
                    val processedBitmap = when(uiState.value.oddsType) {
                        OddsType.WINPLACE -> bitmap
                            .preprocessImage()
                            .maskBitmapExceptRect(maskRegion)
                            .eraseCircle(350, 80, 120)
                            //.sharpen()
                        OddsType.QUINELLA -> bitmap
                            .preprocessImage()
                            .maskBitmapExceptRect(maskRegion)
                            .rotateBitmap(-90f)
                            .eraseCircle(120, 80, 0)
                    }

                    updateProcessedBitmap(processedBitmap)

                    recognizeProcessedBitmap(
                        onRecognitionFailure,
                        onRecognitionFinish
                    )
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.message?.let { Log.e("OcrOdds", it) }
                    Toast.makeText(context, "Capture failed" + exception.message, Toast.LENGTH_SHORT).show()
                }
            }
        )
    }

    fun recognizeProcessedBitmap(
        onRecognitionFailure: () -> Unit = {},
        onRecognitionFinish: () -> Unit = {}
    ) {
        // Convert processed image to ML Kit's InputImage
        if(_imageUiState.value.processedBitmap == null) {
            return
        }

        val image = InputImage.fromBitmap(_imageUiState.value.processedBitmap!!, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        //val recognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                Log.d("visiontext_all", visionText.text)
                for (block in visionText.textBlocks) {
                    Log.d("visiontext_b", "Detected: ${block.text} at ${block.boundingBox}")
                    for (line in block.lines) {
                        //Log.d("visiontext_l", "Detected: ${line.text} at ${line.boundingBox}")
                        for (element in line.elements) {
                            //Log.d("visiontext_e", "Detected: ${element.text} at ${element.boundingBox}")
                        }
                    }
                }

                updateOddsDataFromRecognitionResult(visionText)
                onRecognitionFinish()
                updateImageProcessingState(false)
            }
            .addOnFailureListener {
                onRecognitionFailure()
                updateImageProcessingState(false)
            }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                        as OcrOddsApplication)
                val ocrOddsRepository = application.container.ocrOddsRepository
                OcrOddsViewModel(ocrOddsRepository = ocrOddsRepository)
            }
        }
    }
}

data class OcrOddsUiState(
    val odds: Odds,
    val oddsType: OddsType = OddsType.WINPLACE,
    val trackTake: String = "-",
    val oddsTableButtonBarVisibleIndex: Int = -1,
    val navBarIndex: Int = 0,
    val zoomSliderPos: Float = 1.0f
)

data class ImageUiState(
    val originalBitmap: Bitmap? = null,
    val processedBitmap: Bitmap? = null,
    val isProcessing: Boolean = false,
    val error: String? = null
)