// MediaViewerActivity.kt
package com.example.hcp_camera_demo

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.example.hcp_camera_demo.ui.theme.HCPCamerademoTheme

class MediaViewerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HCPCamerademoTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val uri = intent.getParcelableExtra<Uri>("mediaUri")
                    uri?.let {
                        MediaContent(uri = it)
                    }
                }
            }
        }
    }
}

@Composable
fun MediaContent(uri: Uri) {
    val context = LocalContext.current
    val contentResolver = context.contentResolver
    val mimeType = contentResolver.getType(uri)

    if (mimeType != null && mimeType.startsWith("video/")) {
        // Mostrar VideoView para reproducir el video
        AndroidView(
            factory = { ctx ->
                VideoView(ctx).apply {
                    setVideoURI(uri)
                    setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = true
                        start()
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Mostrar ImageView para visualizar la imagen
        AndroidView(
            factory = { ctx ->
                ImageView(ctx).apply {
                    Glide.with(ctx)
                        .load(uri)
                        .into(this)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
