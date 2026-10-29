package com.opensr.ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { OpenSrApp() }
            }
        }
    }
}

private fun secs(ms: Long): String = String.format(Locale.US, "%.2f s", ms / 1000.0)

@Composable
fun OpenSrApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var engine by remember { mutableStateOf<SuperResolutionEngine?>(null) }
    var modelMessage by remember { mutableStateOf("Loading model…") }
    var input by remember { mutableStateOf<LoadedImage?>(null) }
    var result by remember { mutableStateOf<UpscaleResult?>(null) }
    var busy by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("") }
    var tilesDone by remember { mutableIntStateOf(0) }
    var tilesTotal by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var job by remember { mutableStateOf<Job?>(null) }

    // Load the model once, off the main thread.
    LaunchedEffect(Unit) {
        val loaded = withContext(Dispatchers.IO) {
            runCatching { SuperResolutionEngine.load(context.applicationContext) }
        }
        loaded.onSuccess { e ->
            engine = e
            val i = e.info
            modelMessage = buildString {
                append("Model: ${i.assetName} (${i.sizeBytes} bytes)\n")
                append("Input:  ${i.inputName} ${i.inputShape}\n")
                append("Output: ${i.outputName} ${i.outputShape}\n")
                append("SHA-256: ${if (i.sha256Matches) "matches expected" else "MISMATCH (${i.sha256})"}\n")
                append("CPU threads: ${i.threads}")
            }
        }.onFailure { t ->
            modelMessage = "Model failed to load: ${t.message ?: t.javaClass.simpleName}"
        }
    }

    // On leaving the screen: stop work, then free the native session (waits for a running tile).
    DisposableEffect(Unit) {
        onDispose {
            job?.cancel()
            engine?.close()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            error = null
            result = null
            scope.launch {
                busy = true
                statusText = "Loading image…"
                tilesTotal = 0
                try {
                    input = withContext(Dispatchers.IO) { ImageLoader.load(context, uri) }
                } catch (e: OutOfMemoryError) {
                    error = "Not enough memory to open this image."
                } catch (e: Exception) {
                    error = "Could not open image: ${e.message ?: e.javaClass.simpleName}"
                } finally {
                    busy = false
                    statusText = ""
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("OpenSR AI (x3, CPU)", style = MaterialTheme.typography.titleLarge)

        Card(Modifier.fillMaxWidth()) {
            Text(modelMessage, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Text("Pick image") }

            Button(
                enabled = !busy && input != null && engine != null,
                onClick = {
                    val img = input ?: return@Button
                    val eng = engine ?: return@Button
                    val problem = MemoryGuard.check(context, img.bitmap.width, img.bitmap.height)
                    if (problem != null) {
                        error = problem
                        return@Button
                    }
                    error = null
                    result = null
                    tilesDone = 0
                    tilesTotal = 0
                    job = scope.launch {
                        busy = true
                        statusText = "Starting…"
                        try {
                            result = withContext(Dispatchers.Default) {
                                eng.upscale(
                                    img.bitmap,
                                    onProgress = { done, total ->
                                        tilesDone = done
                                        tilesTotal = total
                                        statusText = "Tile $done / $total"
                                    },
                                    shouldContinue = { isActive },
                                )
                            }
                        } catch (e: CancellationException) {
                            error = "Cancelled."
                            throw e
                        } catch (e: OutOfMemoryError) {
                            error = "Out of memory during upscaling. Close other apps and retry."
                        } catch (e: Exception) {
                            error = "Upscale failed: ${e.message ?: e.javaClass.simpleName}"
                        } finally {
                            busy = false
                            statusText = ""
                        }
                    }
                },
            ) { Text("Upscale x3") }
        }

        if (busy) {
            if (tilesTotal > 0) {
                LinearProgressIndicator(
                    progress = { tilesDone.toFloat() / tilesTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (statusText.isNotEmpty()) Text(statusText)
            if (tilesTotal > 0) {
                OutlinedButton(onClick = { job?.cancel() }) { Text("Cancel") }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        input?.let { img ->
            val note = if (img.wasDownscaled) {
                " (downscaled from ${img.originalWidth}×${img.originalHeight} to keep memory safe)"
            } else {
                ""
            }
            Text("Before: ${img.bitmap.width}×${img.bitmap.height}$note")
            Image(
                bitmap = img.bitmap.asImageBitmap(),
                contentDescription = "Original image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentScale = ContentScale.Fit,
            )
        }

        result?.let { r ->
            Text("After: ${r.bitmap.width}×${r.bitmap.height}")
            Text(
                "Total ${secs(r.totalMs)} · model inference ${secs(r.inferenceMs)} · ${r.tiles} tile(s)",
                style = MaterialTheme.typography.bodySmall,
            )
            Image(
                bitmap = r.bitmap.asImageBitmap(),
                contentDescription = "Upscaled image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
