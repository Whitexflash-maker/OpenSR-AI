package com.superscale

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils

/**
 * Offscreen, GPU-accelerated image upscaler.
 *
 * Pipeline:  source bitmap -> texture -> edge-directed upscale (16-tap Lanczos
 * with bilateral range weighting) -> RCAS sharpening -> read back to Bitmap.
 * Runs on a private EGL context, so it is safe to call from a worker thread.
 */
object ImageUpscaler {

    private const val MAX_TEX = 4096

    /**
     * @param src        source bitmap (ARGB_8888 preferred)
     * @param scale      output scale factor, e.g. 2.0
     * @param sharpness  0.0 .. 1.0 RCAS strength
     */
    fun upscale(src: Bitmap, scale: Float, sharpness: Float): Bitmap {
        val srcW = src.width
        val srcH = src.height

        var dstW = (srcW * scale).toInt().coerceAtLeast(1)
        var dstH = (srcH * scale).toInt().coerceAtLeast(1)
        val maxSide = maxOf(dstW, dstH)
        if (maxSide > MAX_TEX) {
            val k = MAX_TEX.toFloat() / maxSide
            dstW = (dstW * k).toInt().coerceAtLeast(1)
            dstH = (dstH * k).toInt().coerceAtLeast(1)
        }

        val egl = EglCore()
        egl.makeCurrent()
        try {
            val quad = GlUtil.fullScreenQuad()
            val upProg = GlUtil.Program(Shaders.VERT_QUAD, Shaders.FRAG_UPSCALE, "image.up")
            val rcasProg = GlUtil.Program(Shaders.VERT_QUAD, Shaders.FRAG_RCAS, "image.rcas")

            // --- upload source -------------------------------------------------
            val srcTex = GlUtil.createTex(srcW, srcH, linear = true)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, src, 0)

            // --- pass 1: upscale ----------------------------------------------
            val upTex = GlUtil.createTex(dstW, dstH, linear = true)
            val upFbo = GlUtil.createFbo(upTex)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, upFbo)
            GLES30.glViewport(0, 0, dstW, dstH)
            GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)

            upProg.use()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, srcTex)
            upProg.setTex("uTex", 0)
            upProg.set2f("uSrcTexel", 1f / srcW, 1f / srcH)
            upProg.set2f("uSrcSize", srcW.toFloat(), srcH.toFloat())
            upProg.set1f("uEdge", 1.0f)
            GlUtil.drawQuad(quad)

            // --- pass 2: RCAS -------------------------------------------------
            val outTex = GlUtil.createTex(dstW, dstH, linear = false)
            val outFbo = GlUtil.createFbo(outTex)

            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, outFbo)
            GLES30.glViewport(0, 0, dstW, dstH)

            rcasProg.use()
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, upTex)
            rcasProg.setTex("uTex", 0)
            rcasProg.set2f("uTexel", 1f / dstW, 1f / dstH)
            rcasProg.set1f("uSharp", sharpness)
            GlUtil.drawQuad(quad)

            // --- read back ----------------------------------------------------
            val buf = java.nio.ByteBuffer.allocateDirect(dstW * dstH * 4)
                .order(java.nio.ByteOrder.nativeOrder())
            GLES30.glReadPixels(
                0, 0, dstW, dstH,
                GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, buf
            )
            buf.position(0)

            val raw = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(buf)

            // GL origin is bottom-left; flip to top-left for Bitmap
            val matrix = android.graphics.Matrix().apply { preScale(1f, -1f) }
            val flipped = Bitmap.createBitmap(raw, 0, 0, dstW, dstH, matrix, false)

            // --- cleanup ------------------------------------------------------
            GLES30.glDeleteFramebuffers(2, intArrayOf(upFbo, outFbo), 0)
            GLES30.glDeleteTextures(3, intArrayOf(srcTex, upTex, outTex), 0)
            upProg.release()
            rcasProg.release()

            return flipped
        } finally {
            egl.release()
        }
    }
}
