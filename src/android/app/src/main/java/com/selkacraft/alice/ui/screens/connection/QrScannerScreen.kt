package com.selkacraft.alice.ui.screens.connection

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject

private const val TAG = "QrScannerScreen"

@Composable
fun QrScannerScreen(
    onConnectionData: (ip: String, port: Int, token: String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var scannedData by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (hasCameraPermission) {
            // Camera preview with QR scanner
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()

                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }

                        val barcodeScanner = BarcodeScanning.getClient()

                        @androidx.annotation.OptIn(ExperimentalGetImage::class)
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(
                                    ContextCompat.getMainExecutor(ctx)
                                ) { imageProxy ->
                                    if (isProcessing || scannedData != null) {
                                        imageProxy.close()
                                        return@setAnalyzer
                                    }

                                    val mediaImage = imageProxy.image
                                    if (mediaImage != null) {
                                        isProcessing = true
                                        val image = InputImage.fromMediaImage(
                                            mediaImage,
                                            imageProxy.imageInfo.rotationDegrees
                                        )
                                        barcodeScanner.process(image)
                                            .addOnSuccessListener { barcodes ->
                                                for (barcode in barcodes) {
                                                    if (barcode.valueType == Barcode.TYPE_TEXT) {
                                                        val raw = barcode.rawValue ?: continue
                                                        try {
                                                            val json = JSONObject(raw)
                                                            val ip = json.getString("ip")
                                                            val port = json.getInt("port")
                                                            val token = json.getString("token")
                                                            scannedData = raw
                                                            onConnectionData(ip, port, token)
                                                        } catch (e: Exception) {
                                                            Log.w(TAG, "Invalid QR data: $raw")
                                                        }
                                                    }
                                                }
                                                isProcessing = false
                                            }
                                            .addOnFailureListener {
                                                isProcessing = false
                                            }
                                            .addOnCompleteListener {
                                                imageProxy.close()
                                            }
                                    } else {
                                        imageProxy.close()
                                    }
                                }
                            }

                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA,
                                preview, analysis
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Camera bind failed", e)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Viewfinder guide + status text below it
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(250.dp)
                        .border(2.dp, Color(0xFFD0BCFF), RoundedCornerShape(12.dp))
                )

                Text(
                    text = if (scannedData != null) "Connected!" else "Point at the QR code on your PC",
                    color = Color.White,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color(0xCC000000), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Camera permission required", color = Color.White, fontSize = 16.sp)
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
    }
}
