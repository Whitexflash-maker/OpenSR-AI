package com.superscale

import android.opengl.GLES30
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Small OpenGL ES 3.0 helper layer. */
object GlUtil {

    private const val TAG = "SuperScale/GL"
    private const val FLOAT_SIZE = 4

    /** A linked shader program plus cached uniform locations. */
    class Program(vertexSrc: String, fragmentSrc: String, name: String) {
        val id: Int = build(vertexSrc, fragmentSrc, name)
        private val uniforms = HashMap<String, Int>()

        fun use() = GLES30.glUseProgram(id)

        fun u(name: String): Int = uniforms.getOrPut(name) {
            GLES30.glGetUniformLocation(id, name)
        }

        fun set1f(name: String, v: Float) = GLES30.glUniform1f(u(name), v)
        fun set1i(name: String, v: Int) = GLES30.glUniform1i(u(name), v)
        fun set2f(name: String, x: Float, y: Float) = GLES30.glUniform2f(u(name), x, y)
        fun setTex(name: String, unit: Int) = GLES30.glUniform1i(u(name), unit)

        fun release() = GLES30.glDeleteProgram(id)
    }

    /** Full-screen quad, two triangles, clip space, with UV from position. */
    fun fullScreenQuad(): FloatBuffer {
        val v = floatArrayOf(
            -1f, -1f,
             1f, -1f,
            -1f,  1f,
             1f,  1f
        )
        val bb = ByteBuffer.allocateDirect(v.size * FLOAT_SIZE).order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        fb.put(v).position(0)
        return fb
    }

    fun createTex(width: Int, height: Int, linear: Boolean = true): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLES30.glTexImage2D(
            GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, width, height, 0,
            GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null
        )
        val filter = if (linear) GLES30.GL_LINEAR else GLES30.GL_NEAREST
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, filter)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        return ids[0]
    }

    fun createFbo(texId: Int): Int {
        val ids = IntArray(1)
        GLES30.glGenFramebuffers(1, ids, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, ids[0])
        GLES30.glFramebufferTexture2D(
            GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0,
            GLES30.GL_TEXTURE_2D, texId, 0
        )
        val status = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER)
        if (status != GLES30.GL_FRAMEBUFFER_COMPLETE) {
            Log.e(TAG, "Incomplete framebuffer: 0x" + Integer.toHexString(status))
        }
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        return ids[0]
    }

    private fun compile(type: Int, src: String, name: String): Int {
        val sh = GLES30.glCreateShader(type)
        GLES30.glShaderSource(sh, src)
        GLES30.glCompileShader(sh)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(sh, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(sh)
            GLES30.glDeleteShader(sh)
            throw RuntimeException("Shader compile failed [$name]: $log")
        }
        return sh
    }

    private fun build(vs: String, fs: String, name: String): Int {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs, "$name.vert")
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs, "$name.frag")
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glBindAttribLocation(p, 0, "aPos")
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            GLES30.glDeleteProgram(p)
            throw RuntimeException("Program link failed [$name]: $log")
        }
        GLES30.glDeleteShader(v)
        GLES30.glDeleteShader(f)
        return p
    }

    /** Draws the full-screen quad using attrib 0. Caller binds program first. */
    fun drawQuad(quad: FloatBuffer, aPosLoc: Int = 0) {
        GLES30.glEnableVertexAttribArray(aPosLoc)
        GLES30.glVertexAttribPointer(aPosLoc, 2, GLES30.GL_FLOAT, false, 0, quad)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4)
        GLES30.glDisableVertexAttribArray(aPosLoc)
    }
}
