package com.example.ocrodds

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ocrodds.data.OcrOddsDataProvider
import com.example.ocrodds.model.OddsType
import com.example.ocrodds.ui.CameraScreen
import com.example.ocrodds.ui.OcrOddsViewModel
import com.example.ocrodds.ui.ResultScreen
import com.example.ocrodds.ui.theme.OcrOddsTheme
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {
    @RequiresApi(Build.VERSION_CODES.P)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (OpenCVLoader.initLocal()) {
            Toast.makeText(this, "OpenCV loaded successfully", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "OpenCV initialization failed", Toast.LENGTH_LONG).show()
        }
        setContent {
            OcrOddsTheme {
                val layoutDirection = LocalLayoutDirection.current
                Surface(
                    modifier = Modifier.padding(
                        top = WindowInsets.safeDrawing.asPaddingValues()
                            .calculateTopPadding(),
                        bottom = WindowInsets.safeDrawing.asPaddingValues()
                            .calculateBottomPadding(),
                        start = WindowInsets.safeDrawing.asPaddingValues()
                            .calculateStartPadding(layoutDirection),
                        end = WindowInsets.safeDrawing.asPaddingValues()
                            .calculateEndPadding(layoutDirection)
                    ),
                ) {
                    OcrOddsApp()
                }
            }
        }
    }
}

@SuppressLint("ContextCastToActivity")
@RequiresApi(Build.VERSION_CODES.P)
@Composable
fun OcrOddsApp() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val viewModel: OcrOddsViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity,
        factory = OcrOddsViewModel.Factory
    )
    val uiState by viewModel.uiState.collectAsState()
    val imageUiState by viewModel.imageUiState.collectAsState()

    NavHost(navController = navController, startDestination = "result") {
        composable("camera") {
            CameraScreen(
                isProcessing = imageUiState.isProcessing,
                onTakePicture = { imageCapture, context ->
                    viewModel.onTakePicture(
                        imageCapture = imageCapture,
                        context = context,
                        onRecognitionFailure = { Toast.makeText(context, "Text recognition failed", Toast.LENGTH_SHORT).show() }
                    ) {
                        navController.navigate("result")
                    }
                },
                onUpdateCamera = {
                    viewModel.updateCamera(it)
                },
                onCameraZoom = {
                    viewModel.onCameraZoom(it)
                },
                uiState = uiState
            )
        }
        composable("result") {
            Column {
                ResultScreen(
                    onReset = {
                        viewModel.onReset()
                    },
                    onSend = {
                        viewModel.uploadToSCP(context)
                    },
                    onSiteChange = {
                        viewModel.updateSite(it)
                    },
                    onRacedayChange = {
                        viewModel.updateRaceDay(it)
                    },
                    onRacenumChange = {
                        viewModel.updateRacenum(it)
                    },
                    onCellValueChange = { i, j, newValue ->
                        viewModel.updateOdds(i, j, newValue.replace("\n", ""))
                    },
                    onAddNewData = {
                        navController.navigate("camera")
                    },
                    onDeleteNewData = {
                        viewModel.onDeleteNewData()
                    },
                    onShowButtonBar = {
                        viewModel.toggleOddsTableButtonBarVisibility(it)
                    },
                    onDeleteOdds = {
                        viewModel.onDeleteOdds(it)
                    },
                    onInsertOdds = {
                        viewModel.onInsertOdds(it)
                    },
                    onOddsTypeChange = {
                        viewModel.updateOddsType(OddsType.valueOf(it))
                    },
                    onNavBarIndexChange = {
                        viewModel.updateNavBarIndex(it)
                    },
                    uiState = uiState,
                    tableHeader = when (uiState.oddsType) {
                        OddsType.WINPLACE -> OcrOddsDataProvider.getOddsTableHeader()
                        OddsType.QUINELLA ->  OcrOddsDataProvider.getQOddsTableHeader()
                    },
                    imageUiState = imageUiState
                )
            }

        }
    }
}

