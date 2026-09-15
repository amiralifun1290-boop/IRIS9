package com.iris.assistant.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.iris.assistant.tools.LastCapture
import com.iris.assistant.ui.theme.Black
import com.iris.assistant.ui.theme.IrisTeal
import java.io.File
import java.util.concurrent.Executors

@Composable
fun CameraScreen() {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().build() }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    if (!hasPermission) {
        val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { hasPermission = it }
        LaunchedEffect(Unit) { launcher.launch(Manifest.permission.CAMERA) }
        Box(modifier = Modifier.fillMaxSize().background(Black), contentAlignment = Alignment.Center) {
            Text("درخواست دسترسی به دوربین...", color = Color.White)
        }
        return
    }

    var captureStatus by remember { mutableStateOf<String?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Black)) {
        AndroidView(
            factory = { context ->
                val previewView = PreviewView(context)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageCapture
                    )
                }, ContextCompat.getMainExecutor(context))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        captureStatus?.let {
            Text(it, color = Color.White, modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp))
        }

        Button(
            onClick = {
                val file = File(ctx.externalMediaDirs.first(), "IRIS_${System.currentTimeMillis()}.jpg")
                val output = ImageCapture.OutputFileOptions.Builder(file).build()
                imageCapture.takePicture(output, executor, object : ImageCapture.OnImageSavedCallback {
                    override fun onError(exc: ImageCaptureException) {
                        captureStatus = "خطا در گرفتن عکس: ${exc.message}"
                    }
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        // Previously discarded entirely — now the OCR and
                        // object-detection tools can actually see this photo.
                        LastCapture.file = file
                        captureStatus = "عکس ذخیره شد ✓"
                    }
                })
            },
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = IrisTeal),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 32.dp)
                .size(72.dp)
        ) {
            Text("📷", fontSize = 28.sp)
        }
    }

    DisposableEffect(Unit) {
        onDispose { executor.shutdown() }
    }
}
