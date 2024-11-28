package com.example.hcp_camera_demo

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CenterCrop
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraPreviewScreen() {
    var lensFacing by remember { mutableStateOf(CameraSelector.LENS_FACING_BACK) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val preview = Preview.Builder().build()
    val previewView = remember { PreviewView(context) }
    val imageCapture = remember { ImageCapture.Builder().build() }

    // Mutable list to store the URIs of captured media
    val mediaUris = remember { mutableStateListOf<Uri>() }

    // Create QualitySelector for 720p
    val qualitySelector = QualitySelector.fromOrderedList(
        listOf(Quality.HD),
        FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
    )

    // Building Recorder with QualitySelector
    val recorder = remember {
        Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()
    }

    val videoCapture = remember { VideoCapture.withOutput(recorder) }
    var recording: Recording? by remember { mutableStateOf(null) }
    val coroutineScope = rememberCoroutineScope()

    // Status for elapsed time and counter visibility
    var elapsedTime by remember { mutableStateOf(0) }
    var isTimerVisible by remember { mutableStateOf(false) }

    // List of available flash modes
    val flashModes = listOf(
        ImageCapture.FLASH_MODE_AUTO,
        ImageCapture.FLASH_MODE_ON,
        ImageCapture.FLASH_MODE_OFF
    )

    // Status for the current flash mode index
    var currentFlashModeIndex by remember { mutableStateOf(0) }

    // Function to get the name of the flash mode
    fun getFlashModeName(flashMode: Int): String {
        return when (flashMode) {
            ImageCapture.FLASH_MODE_AUTO -> "Auto"
            ImageCapture.FLASH_MODE_ON -> "ON"
            ImageCapture.FLASH_MODE_OFF -> "OFF"
            else -> "Unknown"
        }
    }

    LaunchedEffect(lensFacing) {
        val cameraProvider = context.getCameraProvider()
        cameraProvider.unbindAll()
        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        cameraProvider.bindToLifecycle(
            lifecycleOwner,
            cameraSelector,
            preview,
            imageCapture,
            videoCapture
        )
        preview.surfaceProvider = previewView.surfaceProvider
    }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier.fillMaxSize()
    ) {
        AndroidView(
            { previewView },
            modifier = Modifier.fillMaxSize()
        )
        if (isTimerVisible) {
            Text(
                text = String.format("%02d:%02d", elapsedTime / 60, elapsedTime % 60),
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = Modifier.fillMaxSize()
        ) {
            if (mediaUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(mediaUris) { uri ->
                        MediaThumbnail(uri = uri)
                    }
                }
            }
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                            CameraSelector.LENS_FACING_FRONT
                        } else {
                            CameraSelector.LENS_FACING_BACK
                        }
                    },
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(text = "Flip Cam")
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .padding(4.dp)
                        .background(
                            MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = {
                                    // Action for short click
                                    captureImage(imageCapture, context) { uri ->
                                        mediaUris.add(uri)
                                    }
                                },
                                onPress = {
                                    // Action for pulsation
                                    val pressScope = this
                                    coroutineScope.launch {
                                        pressScope.awaitRelease()
                                        // Action on release of the press
                                        recording?.stop()
                                        isTimerVisible = false
                                        elapsedTime = 0
                                    }
                                },
                                onLongPress = {
                                    // Action for long press
                                    coroutineScope.launch(Dispatchers.Main) {
                                        startRecording(
                                            context = context,
                                            recorder = recorder,
                                            onRecordingStarted = { rec ->
                                                recording = rec
                                                isTimerVisible = true
                                                elapsedTime = 0
                                                // Start counter
                                                coroutineScope.launch {
                                                    while (isTimerVisible && elapsedTime < 10) {
                                                        delay(1000L)
                                                        elapsedTime++
                                                    }
                                                    if (elapsedTime >= 10) {
                                                        recording?.stop()
                                                        isTimerVisible = false
                                                    }
                                                }
                                            },
                                            onMediaCaptured = { uri ->
                                                mediaUris.add(uri)
                                            }
                                        )
                                    }
                                }
                            )
                        }
                        .padding(16.dp)
                ) {
                    Text(
                        text = "Capture",
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
                // Button to toggle flash mode
                Button(
                    onClick = {
                        // Increase flash mode index
                        currentFlashModeIndex = (currentFlashModeIndex + 1) % flashModes.size

                        // Get the current flash mode
                        val currentFlashMode = flashModes[currentFlashModeIndex]

                        // Setting up ImageCapture with the selected flash mode
                        imageCapture.flashMode = currentFlashMode

                        // Display a Toast with the selected flash mode
                        Toast.makeText(
                            context,
                            "Flash mode: ${getFlashModeName(currentFlashMode)}",
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.padding(4.dp)
                ) {
                    Text(text = "Flash")
                }
            }
        }
    }
}

// Function to capture image and update the list of URIs
private fun captureImage(
    imageCapture: ImageCapture,
    context: Context,
    onMediaCaptured: (Uri) -> Unit
) {
    val name = "CameraxImage_${System.currentTimeMillis()}.jpeg"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/CameraX-Image")
        }
    }
    val outputOptions = ImageCapture.OutputFileOptions
        .Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        )
        .build()
    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                outputFileResults.savedUri?.let { uri ->
                    onMediaCaptured(uri)
                }
            }

            override fun onError(exception: ImageCaptureException) {
                println("Error saving image: $exception")
            }
        })
}

// Function to start video recording and update the URI list
@SuppressLint("MissingPermission")
private fun startRecording(
    context: Context,
    recorder: Recorder,
    onRecordingStarted: (Recording) -> Unit,
    onMediaCaptured: (Uri) -> Unit
) {
    val name = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSS", Locale.US)
        .format(System.currentTimeMillis()) + ".mp4"
    val contentValues = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, name)
        put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/CameraX-Video")
        }
    }
    val mediaStoreOutput = MediaStoreOutputOptions
        .Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
        .setContentValues(contentValues)
        .build()
    val recording = recorder.prepareRecording(context, mediaStoreOutput)
        .withAudioEnabled() // Enable audio recording
        .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
            when (recordEvent) {
                is VideoRecordEvent.Start -> {
                    println("Recording started")
                }

                is VideoRecordEvent.Finalize -> {
                    if (recordEvent.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                        recordEvent.outputResults.outputUri?.let { uri ->
                            onMediaCaptured(uri)
                        }
                    } else {
                        println("Error during recording: ${recordEvent.error}")
                    }
                }
            }
        }
    onRecordingStarted(recording)
}

// Composable to display the thumbnail of an image or video
@Composable
fun MediaThumbnail(uri: Uri) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            ImageView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(200.dp.toPx(ctx), 200.dp.toPx(ctx))
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener {
                    // Launch MediaViewerActivity when thumbnail is clicked
                    val intent = Intent(ctx, MediaViewerActivity::class.java).apply {
                        putExtra("mediaUri", uri)
                    }
                    ctx.startActivity(intent)
                }
            }
        },
        update = { imageView ->
            Glide.with(context)
                .load(uri)
                .override(200, 200) // Sets the maximum thumbnail size
                .transform(
                    CenterCrop(),
                    RoundedCorners(16)
                ) // Apply transformations if necessary
                .into(imageView)
        },
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Gray)
    )
}

// Extension function to convert dp to pixels
fun Dp.toPx(context: Context): Int {
    return (this.value * context.resources.displayMetrics.density).toInt()
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider =
    suspendCoroutine { continuation ->
        ProcessCameraProvider.getInstance(this).also { cameraProvider ->
            cameraProvider.addListener({
                continuation.resume(cameraProvider.get())
            }, ContextCompat.getMainExecutor(this))
        }
    }

@androidx.compose.ui.tooling.preview.Preview
@Composable
fun PreviewCameraPreviewScreen() {
    CameraPreviewScreen()
}
