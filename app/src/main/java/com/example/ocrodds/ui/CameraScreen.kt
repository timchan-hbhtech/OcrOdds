package com.example.ocrodds.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.view.MotionEvent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ZoomState
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.ocrodds.imgp.eraseCircle
import com.example.ocrodds.imgp.preprocessImage
import com.example.ocrodds.model.OddsType
import com.google.mlkit.vision.text.Text
import java.io.ByteArrayOutputStream

@OptIn(ExperimentalGetImage::class)
@SuppressLint("ClickableViewAccessibility", "ContextCastToActivity")
@Composable
fun CameraScreen(
    modifier: Modifier = Modifier,
    isProcessing: Boolean,
    onTakePicture: (ImageCapture, Context) -> Unit = { _, _ -> },
    onUpdateCamera: (Camera) -> Unit,
    onCameraZoom: (Float) -> Unit,
    uiState: OcrOddsUiState
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var hasCameraPermission by remember { mutableStateOf(false) }
    val resolutionSelector = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setResolutionSelector(resolutionSelector)
            .build()
    }

    val viewModel: OcrOddsViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = OcrOddsViewModel.Factory
    )

    var textBlocks by remember { mutableStateOf<List<Text.TextBlock>>(emptyList()) }
    var previewViewSize by remember { mutableStateOf(Size(0f, 0f)) }
    var imageAnalysisSize by remember { mutableStateOf(Size(0f, 0f)) }
    var rotationDegrees by remember { mutableIntStateOf(0) }

    val imageAnalysis = ImageAnalysis.Builder()
        .setTargetAspectRatio(AspectRatio.RATIO_4_3)
        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
        .build()

    imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(context)) { imageProxy ->
        val mediaImage = imageProxy.image ?: return@setAnalyzer
        rotationDegrees = imageProxy.imageInfo.rotationDegrees
        imageAnalysisSize = Size(imageProxy.width.toFloat(), imageProxy.height.toFloat())
        try {
            // Get YUV data
            val yBuffer = mediaImage.planes[0].buffer // Y plane
            val uBuffer = mediaImage.planes[1].buffer // U plane
            val vBuffer = mediaImage.planes[2].buffer // V plane

            // Convert YUV planes to a single byte array
            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)

            // Copy Y data
            yBuffer.get(nv21, 0, ySize)
            // Copy U and V data (interleave for NV21 format)
            var pos = ySize
            for (i in 0 until uSize) {
                nv21[pos++] = vBuffer.get(i) // V
                nv21[pos++] = uBuffer.get(i) // U
            }

            // Create YuvImage
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, mediaImage.width, mediaImage.height, null)

            // Convert to Bitmap via JPEG compression
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, mediaImage.width, mediaImage.height), 100, out)
            val jpegBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(jpegBytes, 0, jpegBytes.size)

            // Apply rotation if needed
            val _rotationDegrees = imageProxy.imageInfo.rotationDegrees
            val rotatedBitmap = if (_rotationDegrees != 0) {
                val matrix = Matrix().apply { postRotate(_rotationDegrees.toFloat()) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            } else {
                bitmap
            }

            // Use rotatedBitmap as needed

            val maskRegion = org.opencv.core.Rect(
                ((rotatedBitmap.width - rotatedBitmap.width / 1.5) / 2).toInt(),
                ((rotatedBitmap.height - rotatedBitmap.height / 1.5) / 2).toInt(),
                (rotatedBitmap.width / 1.5).toInt(),
                (rotatedBitmap.height / 1.5).toInt()
            )
            val processedBitmap = rotatedBitmap
                .preprocessImage()
            val croppedBitmap = Bitmap.createBitmap(
                processedBitmap,
                maskRegion.x,
                maskRegion.y,
                maskRegion.width,
                maskRegion.height
            )


            val image = InputImage.fromBitmap(processedBitmap, 0)

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)



            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    textBlocks = visionText.textBlocks

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
                }
                .addOnFailureListener { e->
                    e.printStackTrace()
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } finally {
            imageProxy.close() // Always close the ImageProxy
        }
    }

    // Request camera permission
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (!isGranted) {
            Toast.makeText(context, "Camera permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        hasCameraPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (hasCameraPermission) {
        Column(
            modifier = modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Box(
                modifier = Modifier.weight(15f).aspectRatio(3f / 4f)
            ) {
                AndroidView(
                    factory = { PreviewView(context) }
                ) { previewView ->
                    previewView.post {
                        previewViewSize = Size(previewView.width.toFloat(), previewView.height.toFloat())
                        println("PreviewView Size: $previewViewSize")
                    }
                    previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .build()
                        .also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            // imageAnalysis,
                            imageCapture
                        )

                        onUpdateCamera(camera)

                        previewView.setOnTouchListener { _, event ->
                            if (event.action != MotionEvent.ACTION_DOWN) return@setOnTouchListener false

                            val factory = SurfaceOrientedMeteringPointFactory(
                                previewView.width.toFloat(), previewView.height.toFloat()
                            )
                            val point = factory.createPoint(event.x, event.y)
                            val action = FocusMeteringAction.Builder(point).build()
                            camera.cameraControl.startFocusAndMetering(action)

                            true
                        }

                    } catch (e: Exception) {
                        Toast.makeText(context, "Failed to start camera", Toast.LENGTH_SHORT).show()
                    }

                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    Log.e("image_ana", rotationDegrees.toString())
                    Log.e("image_ana", previewViewSize.toString())
                    Log.e("image_ana", imageAnalysisSize.toString())
                    if (previewViewSize.width == 0f || previewViewSize.height == 0f || imageAnalysisSize.width == 0f || imageAnalysisSize.height == 0f) {
                        return@Canvas
                    }

                    val scaleX = previewViewSize.width / imageAnalysisSize.width
                    val scaleY = previewViewSize.height / imageAnalysisSize.height
                    val scale = minOf(scaleX, scaleY)
                    val offsetX = (previewViewSize.width - imageAnalysisSize.width * scale) / 2
                    val offsetY = (previewViewSize.height - imageAnalysisSize.height * scale) / 2

                    textBlocks.forEach { block ->
                        block.boundingBox?.let { androidRect ->
                            var rect = androidx.compose.ui.geometry.Rect(
                                left = androidRect.left.toFloat(),
                                top = androidRect.top.toFloat(),
                                right = androidRect.right.toFloat(),
                                bottom = androidRect.bottom.toFloat()
                            )
                            // Handle rotation
                            rect = when (rotationDegrees) {
                                90 -> androidx.compose.ui.geometry.Rect(
                                    left = rect.top,
                                    top = imageAnalysisSize.width - rect.right,
                                    right = rect.bottom,
                                    bottom = imageAnalysisSize.width - rect.left
                                )

                                270 -> androidx.compose.ui.geometry.Rect(
                                    left = imageAnalysisSize.height - rect.bottom,
                                    top = rect.left,
                                    right = imageAnalysisSize.height - rect.top,
                                    bottom = rect.right
                                )

                                else -> rect
                            }
                            // Transform coordinates
                            val scaledRect = androidx.compose.ui.geometry.Rect(
                                left = rect.left * scale + offsetX,
                                top = rect.top * scale + offsetY,
                                right = rect.right * scale + offsetX,
                                bottom = rect.bottom * scale + offsetY
                            )
                            drawRect(
                                color = Color.Red,
                                topLeft = Offset(scaledRect.left, scaledRect.top),
                                size = Size(scaledRect.width, scaledRect.height),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                            )
                        }
                    }
                }

                // Overlay with rectangle and blur
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val canvasWidth = size.width
                    val canvasHeight = size.height
                    // Define rectangle size (e.g., 60% of the smaller dimension)
                    val rectSize = Size(canvasWidth / 1.5F, canvasHeight / 1.5F)
                    val rectLeft = (canvasWidth - rectSize.width) / 2
                    val rectTop = (canvasHeight - rectSize.height) / 2

                    // Apply blur to the entire canvas except the rectangle
                    clipRect(
                        left = rectLeft,
                        top = rectTop,
                        right = rectLeft + rectSize.width,
                        bottom = rectTop + rectSize.height,
                        clipOp = ClipOp.Difference
                    ) {
                        // Apply blur effect to the outer region
                        drawRect(
                            color = Color.Black.copy(alpha = 0.8f), // Semi-transparent for blur effect
                            topLeft = Offset(0f, 0f),
                            size = Size(canvasWidth, canvasHeight),
                            // BlurEffect requires API 31+; use a fallback for older APIs
                            style = androidx.compose.ui.graphics.drawscope.Fill,
                            // For API 31+: apply BlurEffect
                            // renderEffect = BlurEffect(25f, 25f, TileMode.Clamp)
                        )
                    }

                    // Draw rectangle outline
                    drawRect(
                        color = Color.White,
                        topLeft = Offset(rectLeft, rectTop),
                        size = rectSize,
                        style = Stroke(width = 4.dp.toPx())
                    )
                }

            }


            Column(
                modifier = Modifier.fillMaxSize().weight(6f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(OddsType.entries) { oddsType ->
                        FilterChip(
                            selected = uiState.oddsType == oddsType,
                            onClick = { viewModel.updateOddsType(oddsType) },
                            label = { Text(oddsType.name) },
                            leadingIcon = if (uiState.oddsType == oddsType) {
                                { Icon(Icons.Filled.Done, contentDescription = null) }
                            } else null
                        )
                    }
                }

                viewModel.camera.value?.let { camera ->
                    camera.cameraInfo.zoomState.value?.let {
                        val sliderPosition = uiState.zoomSliderPos
                        camera.cameraControl.setZoomRatio(sliderPosition)
                        CameraWithZoomSlider(
                            modifier = Modifier.fillMaxWidth(),
                            zoomState = it,
                            sliderPosition = sliderPosition,
                            onSliderPositionChange = { newSliderPosition ->
                                onCameraZoom(newSliderPosition)
                            }
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    CameraCaptureButton(
                        enabled = !isProcessing
                    ) {
                        onTakePicture(imageCapture, context)
                    }
                }
            }


        }
    } else {
        Text(
            text = "Camera permission is required",
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize()
        )
    }
}

private fun scaleRect(rect: Rect, scaleX: Float, scaleY: Float): Rect {
    return Rect(
        (rect.left * scaleX).toInt(),
        (rect.top * scaleY).toInt(),
        (rect.right * scaleX).toInt(),
        (rect.bottom * scaleY).toInt()
    )
}

@Composable
fun CameraWithZoomSlider(
    modifier: Modifier,
    zoomState: ZoomState,
    sliderPosition: Float,
    onSliderPositionChange: (Float) -> Unit
) {
    // var sliderPosition by remember { mutableFloatStateOf(zoomState.zoomRatio) }
    Log.d("slider", sliderPosition.toString())
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Slider(
            value = sliderPosition,
            onValueChange = {
                onSliderPositionChange(it)
            },
            valueRange = zoomState.minZoomRatio..zoomState.maxZoomRatio,
            modifier = Modifier.padding(16.dp)
        )
    }
}


@Composable
fun CameraCaptureButton(
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    var scale by remember { mutableStateOf(1f) }
    val coroutineScope = rememberCoroutineScope()

    // Animate scale when pressed, only if enabled
    LaunchedEffect(isPressed, enabled) {
        if (isPressed && enabled) {
            scale = 0.9f // Slightly shrink when pressed
            delay(100) // Duration of press effect
            scale = 1f // Return to original size
        }
    }

    Box(
        modifier = modifier
            .size(70.dp) // Size of the button
            .clip(CircleShape) // Circular shape
            .background(
                if (enabled) Color.White else Color.Gray.copy(alpha = 0.5f) // Grayed out when disabled
            )
            .border(2.dp, Color.Gray.copy(alpha = if (enabled) 0.5f else 0.3f), CircleShape) // Adjust border opacity
            .scale(scale) // Apply scale animation
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null // Disable default ripple for custom effect
                    ) {
                        coroutineScope.launch {
                            onClick() // Trigger the provided onClick action
                        }
                    }
                } else {
                    Modifier // No clickable modifier when disabled
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        // Inner circle for "shutter" effect
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(
                    if (enabled) Color.White else Color.Gray.copy(alpha = 0.5f) // Match disabled state
                )
        )
    }
}

/*@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
fun CameraScreenPreview() {
    OcrOddsTheme {
        CameraScreen()
    }
}*/