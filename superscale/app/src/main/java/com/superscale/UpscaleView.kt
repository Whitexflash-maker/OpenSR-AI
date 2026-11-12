package com.superscale

import android.content.Context
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Real-time upscaling demo.
 *
 * Renders a procedurally generated 3D-ish scene at `renderScale` times the
 * surface resolution, then upscales it back to full resolution using the
 * selected pipeline. A substance-free but demanding test scene: thin grid
 * lines, single-pixel stars, hard sphere rims and 1px HUD bars.
 */
class UpscaleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    enum class Mode { SMART, BILINEAR }

    interface Listener {
        /** Called on the GL thread. */
        fun onStats(fps: Float, internalW: Int, internalH: Int)
        fun onTapToggleMode()
    }

    var listener: Listener? = null

    /** Fraction of native resolution the scene is rendered at (0.25 .. 1.0). */
    @Volatile
    var renderScale: Float = 0.5f
        set(value) {
            field = value.coerceIn(0.25f, 1.0f)
            renderer?.needsResize = true
            requestRender()
        }

    /** RCAS strength, 0 .. 1. */
    @Volatile
    var sharpness: Float = 0.35f
        set(value) {
            field = value.coerceIn(0f, 1f)
            renderRequested = true
            requestRender()
        }

    @Volatile
    var mode: Mode = Mode.SMART

    @Volatile
    private var renderRequested = false

    private var renderer: Renderer? = null

    init {
        setEGLContextClientVersion(3)
        preserveEGLContextOnPause = true
        val r = Renderer()
        renderer = r
        setRenderer(r)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            listener?.onTapToggleMode()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private inner class Renderer : GLSurfaceView.Renderer {

        private val quad = GlUtil.fullScreenQuad()

        private var sceneProg: GlUtil.Program? = null
        private var upProg: GlUtil.Program? = null
        private var rcasProg: GlUtil.Program? = null
        private var copyProg: GlUtil.Program? = null

        @Volatile
        var needsResize = true

        private var surfaceW = 1
        private var surfaceH = 1
        private var loW = 1
        private var loH = 1

        private var loTex = 0
        private var loFbo = 0
        private var midTex = 0
        private var midFbo = 0

        private var startNanos = System.nanoTime()
        private var frames = 0
        private var lastStatNanos = 0L
        private var fps = 0f

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            sceneProg = GlUtil.Program(Shaders.VERT_QUAD, Shaders.FRAG_SCENE, "scene")
            upProg = GlUtil.Program(Shaders.VERT_QUAD, Shaders.FRAG_UPSCALE, "up")
            rcasProg = GlUtil.Program(Shaders.VERT_QUAD, Shaders.FRAG_RCAS, "rcas")
            copyProg = GlUtil.Program(Shaders.VERT_QUAD, Shaders.FRAG_BILINEAR, "copy")
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glDisable(GLES30.GL_BLEND)
            needsResize = true
            startNanos = System.nanoTime()
            frames = 0
            lastStatNanos = startNanos
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            surfaceW = width
            surfaceH = height
            needsResize = true
        }

        private fun releaseTargets() {
            if (loTex != 0) {
                GLES30.glDeleteFramebuffers(2, intArrayOf(loFbo, midFbo), 0)
                GLES30.glDeleteTextures(2, intArrayOf(loTex, midTex), 0)
                loTex = 0; midTex = 0
            }
        }

        private fun ensureTargets() {
            if (!needsResize) return
            releaseTargets()
            loW = (surfaceW * renderScale).toInt().coerceAtLeast(2)
            loH = (surfaceH * renderScale).toInt().coerceAtLeast(2)
            loTex = GlUtil.createTex(loW, loH, linear = true)
            loFbo = GlUtil.createFbo(loTex)
            midTex = GlUtil.createTex(surfaceW, surfaceH, linear = true)
            midFbo = GlUtil.createFbo(midTex)
            needsResize = false
        }

        override fun onDrawFrame(gl: GL10?) {
            ensureTargets()
            val now = System.nanoTime()
            val t = (now - startNanos) / 1_000_000_000f

            // ---- pass 1: render scene at internal (low) resolution ----------
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, loFbo)
            GLES30.glViewport(0, 0, loW, loH)
            sceneProg!!.use()
            sceneProg!!.set2f("uRes", loW.toFloat(), loH.toFloat())
            sceneProg!!.set1f("uTime", t)
            GlUtil.drawQuad(quad)

            // ---- pass 2: upscale to native resolution ----------------------
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, midFbo)
            GLES30.glViewport(0, 0, surfaceW, surfaceH)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)

            if (mode == Mode.SMART) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, loTex)
                upProg!!.use()
                upProg!!.setTex("uTex", 0)
                upProg!!.set2f("uSrcTexel", 1f / loW, 1f / loH)
                upProg!!.set2f("uSrcSize", loW.toFloat(), loH.toFloat())
                upProg!!.set1f("uEdge", 1.0f)
            } else {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, loTex)
                copyProg!!.use()
                copyProg!!.setTex("uTex", 0)
            }
            GlUtil.drawQuad(quad)

            // ---- pass 3: present (RCAS in smart mode) ----------------------
            GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
            GLES30.glViewport(0, 0, surfaceW, surfaceH)

            if (mode == Mode.SMART) {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, midTex)
                rcasProg!!.use()
                rcasProg!!.setTex("uTex", 0)
                rcasProg!!.set2f("uTexel", 1f / surfaceW, 1f / surfaceH)
                rcasProg!!.set1f("uSharp", sharpness)
            } else {
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, midTex)
                copyProg!!.use()
                copyProg!!.setTex("uTex", 0)
            }
            GlUtil.drawQuad(quad)

            // ---- stats ------------------------------------------------------
            frames++
            val dt = now - lastStatNanos
            if (dt >= 500_000_000L) {
                fps = frames * 1_000_000_000f / dt
                frames = 0
                lastStatNanos = now
                listener?.onStats(fps, loW, loH)
            }
        }
    }
}
