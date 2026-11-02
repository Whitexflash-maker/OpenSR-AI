package com.featherx.app.feature.upscaler

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Checks that the model file committed to the repository is the verified one. */
class ModelAssetTest {

    @Test
    fun modelAssetHasExpectedSizeAndSha256() {
        // Gradle runs unit tests with the module directory (app/) as working directory.
        val f = File("src/main/assets/${SuperResolutionEngine.MODEL_ASSET}")
        assertTrue("Model file missing: ${f.absolutePath}", f.isFile)
        assertEquals(240078L, f.length())
        val sha = MessageDigest.getInstance("SHA-256").digest(f.readBytes())
            .joinToString("") { "%02x".format(it) }
        assertEquals(SuperResolutionEngine.EXPECTED_SHA256, sha)
    }
}
