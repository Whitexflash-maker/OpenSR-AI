package com.featherx.app.feature.upscaler

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ModelState { Loading, Ready, Failed }

/**
 * Owns the ONNX engine and all upscaler state, so it survives tab switches and rotation.
 * (Surviving process death, e.g. keeping the picked image, is not implemented yet.)
 */
class UpscalerViewModel(application: Application) : AndroidViewModel(application) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: SuperResolutionEngine? = null
    private var job: Job? = null

    var modelState by mutableStateOf(ModelState.Loading)
        private set
    var modelMessage by mutableStateOf("Loading model…")
        private set
    var input by mutableStateOf<LoadedImage?>(null)
        private set
    var result by mutableStateOf<UpscaleResult?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var statusText by mutableStateOf("")
        private set
    var tilesDone by mutableIntStateOf(0)
        private set
    var tilesTotal by mutableIntStateOf(0)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    init {
        loadModel()
    }

    private fun loadModel() {
        scope.launch {
            val loaded = withContext(Dispatchers.IO) {
                runCatching { SuperResolutionEngine.load(getApplication()) }
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
                modelState = ModelState.Ready
            }.onFailure { t ->
                modelMessage = "Model failed to load: ${t.message ?: t.javaClass.simpleName}"
                modelState = ModelState.Failed
            }
        }
    }

    fun loadImage(uri: Uri) {
        if (busy) return
        error = null
        result = null
        busy = true
        statusText = "Loading image…"
        tilesTotal = 0
        tilesDone = 0
        scope.launch {
            try {
                input = withContext(Dispatchers.IO) { ImageLoader.load(getApplication(), uri) }
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

    fun upscale() {
        if (busy) return
        val img = input ?: return
        val eng = engine ?: return
        val problem = MemoryGuard.check(getApplication(), img.bitmap.width, img.bitmap.height)
        if (problem != null) {
            error = problem
            return
        }
        error = null
        result = null
        tilesDone = 0
        tilesTotal = 0
        busy = true
        statusText = "Starting…"
        job = scope.launch {
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
    }

    fun cancel() {
        job?.cancel()
    }

    override fun onCleared() {
        job?.cancel()
        scope.cancel()
        val e = engine
        engine = null
        if (e != null) {
            // close() waits for a tile that may still be running, so keep it off the main thread.
            Thread({ e.close() }, "featherx-engine-close").start()
        }
    }
}
