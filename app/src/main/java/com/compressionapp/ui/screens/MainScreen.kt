package com.compressionapp.ui.screens

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.compressionapp.util.VideoCompressor
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Image", "Video")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Compressor") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }
            when (selectedTab) {
                0 -> ImageCompressionScreen()
                1 -> VideoCompressionScreen()
            }
        }
    }
}

@Composable
fun ImageCompressionScreen() {
    var originalBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var compressedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var compressedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var compressionQuality by remember { mutableFloatStateOf(80f) }
    var originalSize by remember { mutableStateOf(0L) }

    val context = LocalContext.current

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                val source = ImageDecoder.createSource(context.contentResolver, it)
                originalBitmap = ImageDecoder.decodeBitmap(source)
                context.contentResolver.openInputStream(it)?.use { stream ->
                    originalSize = stream.available().toLong()
                }
            }
        }
    )

    LaunchedEffect(originalBitmap, compressionQuality) {
        originalBitmap?.let { bmp ->
            val outputStream = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, compressionQuality.toInt(), outputStream)
            compressedImageBytes = outputStream.toByteArray()
            compressedBitmap = android.graphics.BitmapFactory.decodeByteArray(compressedImageBytes, 0, compressedImageBytes!!.size)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = { photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
            Text("Select Image")
        }

        AnimatedContent(
            targetState = compressedBitmap,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "ImagePreviewAnimation",
            modifier = Modifier.weight(1f)
        ) { targetBitmap ->
            if (targetBitmap != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        bitmap = targetBitmap.asImageBitmap(),
                        contentDescription = "Compressed Image",
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Quality: ${compressionQuality.toInt()}%")
                        Slider(value = compressionQuality, onValueChange = { compressionQuality = it }, valueRange = 0f..100f)
                        if (compressedImageBytes != null) {
                            Text("Original Size: ${originalSize / 1024} KB")
                            Text("Compressed Size: ${compressedImageBytes!!.size / 1024} KB", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select an image to start.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = {
                    compressedImageBytes?.let { bytes ->
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "compressed_image_${System.currentTimeMillis()}.jpg")
                            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CompressionApp")
                        }
                        resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                            resolver.openOutputStream(uri)?.use { stream -> stream.write(bytes) }
                        }
                    }
                },
                enabled = compressedImageBytes != null
            ) { Text("Download") }
            Button(
                onClick = {
                    compressedImageBytes?.let { bytes ->
                        val file = File(context.cacheDir, "shared_image.jpg").apply { writeBytes(bytes) }
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Image"))
                    }
                },
                enabled = compressedImageBytes != null
            ) { Text("Share") }
        }
    }
}

@Composable
fun VideoCompressionScreen() {
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var previewVideoUri by remember { mutableStateOf<Uri?>(null) }
    var compressionQuality by remember { mutableFloatStateOf(50f) }
    var isCompressing by remember { mutableStateOf(false) }
    var originalSize by remember { mutableStateOf(0L) }
    var compressedSize by remember { mutableStateOf(0L) }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            uri?.let {
                selectedVideoUri = it
                previewVideoUri = it
                context.contentResolver.openInputStream(it)?.use { stream ->
                    originalSize = stream.available().toLong()
                }
            }
        }
    )

    fun runCompression() {
        selectedVideoUri?.let { uri ->
            isCompressing = true
            coroutineScope.launch {
                val outputFile = File(context.cacheDir, "preview_compressed.mp4")
                try {
                    VideoCompressor.compressVideo(context, uri, outputFile, compressionQuality.toInt())
                    previewVideoUri = Uri.fromFile(outputFile)
                    compressedSize = outputFile.length()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isCompressing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Button(onClick = { videoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)) }) {
            Text("Select Video")
        }

        AnimatedContent(
            targetState = previewVideoUri,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "VideoPreviewAnimation",
            modifier = Modifier.weight(1f)
        ) { targetUri ->
            if (targetUri != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    VideoPlayer(uri = targetUri, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (isCompressing) {
                            CircularProgressIndicator()
                            Text("Compressing preview...")
                        } else {
                            Text("Quality: ${compressionQuality.toInt()}%")
                        }
                        Slider(
                            value = compressionQuality,
                            onValueChange = { compressionQuality = it },
                            valueRange = 10f..100f,
                            onValueChangeFinished = { runCompression() }
                        )
                        Text("Original Size: ${originalSize / (1024 * 1024)} MB")
                        if (compressedSize > 0) {
                            Text("Estimated New Size: ${compressedSize / (1024 * 1024)} MB", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a video to start.", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = {
                    previewVideoUri?.let { uri ->
                        val resolver = context.contentResolver
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, "compressed_video_${System.currentTimeMillis()}.mp4")
                            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                            put(MediaStore.MediaColumns.RELATIVE_PATH, "Movies/CompressionApp")
                        }
                        val destinationUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                        destinationUri?.let { destUri ->
                            resolver.openInputStream(uri)?.use { input ->
                                resolver.openOutputStream(destUri)?.use { output ->
                                    input.copyTo(output)
                                }
                            }
                        }
                    }
                },
                enabled = compressedSize > 0 && !isCompressing
            ) { Text("Download") }
            Button(
                onClick = {
                    previewVideoUri?.let { uri ->
                        val file = File(uri.path!!)
                        val contentUri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "video/mp4"
                            putExtra(Intent.EXTRA_STREAM, contentUri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share Video"))
                    }
                },
                enabled = compressedSize > 0 && !isCompressing
            ) { Text("Share") }
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val exoPlayer = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }

    DisposableEffect(Unit) {
        onDispose { exoPlayer.release() }
    }

    AndroidView(
        factory = { PlayerView(it).apply { player = exoPlayer } },
        modifier = modifier
    )
}
