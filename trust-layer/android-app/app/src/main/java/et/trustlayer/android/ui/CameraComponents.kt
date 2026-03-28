package et.trustlayer.android.ui

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import et.trustlayer.android.ekyc.QrCodeAnalyzer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.Executors

private enum class CameraPermissionState {
    UNKNOWN, GRANTED, DENIED
}

/**
 * QR Scanner view — opens rear camera, scans for QR codes, and invokes [onQrCodeScanned].
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun QrScannerView(
    onQrCodeScanned: (sessionId: String, tenantId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(CameraPermissionState.UNKNOWN) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = if (granted) CameraPermissionState.GRANTED else CameraPermissionState.DENIED
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionState = CameraPermissionState.GRANTED
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when (permissionState) {
        CameraPermissionState.GRANTED -> {
            QrCameraPreview(
                onQrCodeScanned = onQrCodeScanned,
                modifier = modifier
            )
        }
        CameraPermissionState.DENIED -> {
            PermissionRationale(
                message = "Camera access is required to scan QR codes for eKYC verification.",
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = modifier
            )
        }
        CameraPermissionState.UNKNOWN -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Requesting camera permission...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(
    onQrCodeScanned: (sessionId: String, tenantId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    var scanned by remember { mutableStateOf(false) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val analyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor, QrCodeAnalyzer { params ->
                            if (!scanned) {
                                scanned = true
                                onQrCodeScanned(params.sessionId, params.tenantSlug)
                            }
                        })
                    }

                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, cameraSelector, preview, analyzer
                    )
                } catch (e: Exception) {
                    Log.e("QrScanner", "Camera bind failed", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

/**
 * Camera view for capturing a photo (ID card or selfie).
 * [facing] selects front vs rear camera.
 */
@Composable
fun PhotoCaptureView(
    facing: Int = CameraSelector.LENS_FACING_BACK,
    onPhotoCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var permissionState by remember { mutableStateOf(CameraPermissionState.UNKNOWN) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionState = if (granted) CameraPermissionState.GRANTED else CameraPermissionState.DENIED
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionState = CameraPermissionState.GRANTED
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    when (permissionState) {
        CameraPermissionState.GRANTED -> {
            PhotoCameraPreview(
                facing = facing,
                onPhotoCaptured = onPhotoCaptured,
                modifier = modifier
            )
        }
        CameraPermissionState.DENIED -> {
            PermissionRationale(
                message = "Camera permission is required to capture photos.",
                onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                modifier = modifier
            )
        }
        CameraPermissionState.UNKNOWN -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Requesting camera permission...", color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun PhotoCameraPreview(
    facing: Int,
    onPhotoCaptured: (Uri) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.surfaceProvider = previewView.surfaceProvider
                    }

                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build()
                    imageCapture = capture

                    val selector = CameraSelector.Builder()
                        .requireLensFacing(facing)
                        .build()

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner, selector, preview, capture
                        )
                    } catch (e: Exception) {
                        Log.e("PhotoCapture", "Camera bind failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            }
        )

        Button(
            onClick = {
                val capture = imageCapture ?: return@Button
                val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                    .format(System.currentTimeMillis())
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "TL_$name")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/TrustLayer")
                    }
                }
                val outputOptions = ImageCapture.OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).build()

                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                            output.savedUri?.let { onPhotoCaptured(it) }
                        }
                        override fun onError(exc: ImageCaptureException) {
                            Log.e("PhotoCapture", "Capture failed", exc)
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .fillMaxWidth(0.6f)
                .height(56.dp),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                if (facing == CameraSelector.LENS_FACING_BACK) "Capture ID Photo" else "Take Selfie",
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun PermissionRationale(
    message: String,
    onRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Camera Permission Required",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(16.dp))
        Text(
            message,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text("Grant Permission")
        }
    }
}
