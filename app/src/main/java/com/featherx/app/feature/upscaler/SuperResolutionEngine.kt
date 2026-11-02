package com.featherx.app.feature.upscaler

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import com.featherx.app.feature.upscaler.TilePlanner.MARGIN
import com.featherx.app.feature.upscaler.TilePlanner.OUT_TILE
import com.featherx.app.feature.upscaler.TilePlanner.SCALE
import com.featherx.app.feature.upscaler.TilePlanner.TILE
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.concurrent.CancellationException

data class ModelInfo(
    val assetName: String,
    val sizeBytes: Int,
    val sha256: String,
    val sha256Matches: Boolean,
    val inputName: String,
    val inputShape: List<Long>,
    val outputName: String,
    val outputShape: List<Long>,
    val threads: Int,
)

data class UpscaleResult(
    val bitmap: Bitmap,
    val inputWidth: Int,
    val inputHeight: Int,
    val tiles: Int,
    val inferenceMs: Long,
    val totalMs: Long,
)

class SuperResolutionEngine private constructor(
    private val env: OrtEnvironment,
    private val options: OrtSession.SessionOptions,
    private val session: OrtSession,
    val info: ModelInfo,
) : AutoCloseable {

    // Guards the native session: close() waits for a running tile instead of freeing it mid-inference.
    private val lock = Any()
    private var closed = false

    // Direct buffer reused for every tile (224*224 floats = ~200 KB).
    private val inputBuffer: FloatBuffer =
        ByteBuffer.allocateDirect(TILE * TILE * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val tileOut = FloatArray(OUT_TILE * OUT_TILE)

    /**
     * Runs the real ONNX model on the Y channel of every tile, then recombines with
     * chroma taken from a bilinear x3 upscale of the original (the model only enhances Y).
     * Blocking: call from a background thread.
     */
    fun upscale(
        src: Bitmap,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
        shouldContinue: () -> Boolean = { true },
    ): UpscaleResult {
        val t0 = System.nanoTime()
        require(src.config == Bitmap.Config.ARGB_8888) { "Bitmap must be ARGB_8888 (software)" }
        val w = src.width
        val h = src.height
        val outW = w * SCALE
        val outH = h * SCALE
        require(outW.toLong() * outH <= MAX_OUTPUT_PIXELS) {
            "Image too large: output would be ${outW}x$outH"
        }

        // 1) Y plane (0..1) of the source.
        val yPlane = FloatArray(w * h)
        run {
            val srcPixels = IntArray(w * h)
            src.getPixels(srcPixels, 0, w, 0, 0, w, h)
            for (i in srcPixels.indices) {
                val p = srcPixels[i]
                yPlane[i] = YCbCr.luma((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF) / 255f
            }
        }

        // 2) Tiled model inference -> upscaled Y plane.
        val yOut = FloatArray(outW * outH)
        val tiles = TilePlanner.plan(w, h)
        var inferNanos = 0L
        for ((index, t) in tiles.withIndex()) {
            if (!shouldContinue()) throw CancellationException("Cancelled")

            // Fill 224x224 window; coordinates outside the image are edge-replicated.
            for (ty in 0 until TILE) {
                val rowBase = (t.cy - MARGIN + ty).coerceIn(0, h - 1) * w
                val ibase = ty * TILE
                for (tx in 0 until TILE) {
                    val sx = (t.cx - MARGIN + tx).coerceIn(0, w - 1)
                    inputBuffer.put(ibase + tx, yPlane[rowBase + sx])
                }
            }
            inputBuffer.rewind()

            val ti = System.nanoTime()
            synchronized(lock) {
                check(!closed) { "Engine is closed" }
                OnnxTensor.createTensor(
                    env, inputBuffer, longArrayOf(1, 1, TILE.toLong(), TILE.toLong())
                ).use { tensor ->
                    session.run(mapOf(info.inputName to tensor)).use { result ->
                        val out = result.get(0) as OnnxTensor
                        val fb = out.floatBuffer
                        check(fb.remaining() == OUT_TILE * OUT_TILE) {
                            "Unexpected output size ${fb.remaining()}"
                        }
                        fb.get(tileOut)
                    }
                }
            }
            inferNanos += System.nanoTime() - ti

            // Copy only the core region (drop the margin that had incomplete context).
            val rowLen = t.cw * SCALE
            for (oy in 0 until t.ch * SCALE) {
                val srcOff = (MARGIN * SCALE + oy) * OUT_TILE + MARGIN * SCALE
                val dstOff = (t.cy * SCALE + oy) * outW + t.cx * SCALE
                System.arraycopy(tileOut, srcOff, yOut, dstOff, rowLen)
            }
            onProgress(index + 1, tiles.size)
        }

        // 3) Chroma from bilinear x3 upscale, recombine with model Y.
        val scaled = Bitmap.createScaledBitmap(src, outW, outH, true)
        val pixels = IntArray(outW * outH)
        scaled.getPixels(pixels, 0, outW, 0, 0, outW, outH)
        if (scaled !== src) scaled.recycle()
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            pixels[i] = YCbCr.toArgb(p ushr 24, yOut[i] * 255f, YCbCr.cb(r, g, b), YCbCr.cr(r, g, b))
        }
        val bmp = Bitmap.createBitmap(pixels, outW, outH, Bitmap.Config.ARGB_8888)

        val totalMs = (System.nanoTime() - t0) / 1_000_000
        return UpscaleResult(bmp, w, h, tiles.size, inferNanos / 1_000_000, totalMs)
    }

    override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            session.close()
            options.close()
        }
    }

    companion object {
        const val MODEL_ASSET = "super-resolution-10.onnx"
        const val EXPECTED_SHA256 = "85f36ff88cc504a24af5e0602148bc56a8aa09a58eca8c0da2756f3e8186035e"
        private const val INPUT_NAME = "input"
        private const val OUTPUT_NAME = "output"
        const val MAX_OUTPUT_PIXELS = 4_000_000L

        /** Blocking; call from a background thread. Throws with a readable message on failure. */
        fun load(context: Context): SuperResolutionEngine {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val sha = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }

            val env = OrtEnvironment.getEnvironment()
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 4)
            val options = OrtSession.SessionOptions()
            options.setIntraOpNumThreads(threads)

            var session: OrtSession? = null
            try {
                val created = env.createSession(bytes, options)
                session = created
                val inT = created.inputInfo[INPUT_NAME]?.info as? TensorInfo
                    ?: error("Model has no tensor input '$INPUT_NAME'")
                val outT = created.outputInfo[OUTPUT_NAME]?.info as? TensorInfo
                    ?: error("Model has no tensor output '$OUTPUT_NAME'")
                val inShape = inT.shape.toList()
                val outShape = outT.shape.toList()
                check(
                    inShape.size == 4 && inShape[1] == 1L &&
                        inShape[2] == TILE.toLong() && inShape[3] == TILE.toLong()
                ) { "Unexpected input shape $inShape" }
                check(
                    outShape.size == 4 && outShape[1] == 1L &&
                        outShape[2] == OUT_TILE.toLong() && outShape[3] == OUT_TILE.toLong()
                ) { "Unexpected output shape $outShape" }

                val info = ModelInfo(
                    MODEL_ASSET, bytes.size, sha, sha == EXPECTED_SHA256,
                    INPUT_NAME, inShape, OUTPUT_NAME, outShape, threads,
                )
                return SuperResolutionEngine(env, options, created, info)
            } catch (t: Throwable) {
                session?.close()
                options.close()
                throw t
            }
        }
    }
}
