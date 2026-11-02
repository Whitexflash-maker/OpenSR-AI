package com.featherx.app.feature.upscaler

import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxCard
import com.featherx.app.ui.ScreenHeader
import java.util.Locale

private fun secs(ms: Long): String = String.format(Locale.US, "%.2f s", ms / 1000.0)

@Composable
fun UpscalerScreen(vm: UpscalerViewModel) {
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.loadImage(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("AI Upscaler", "x3 super-resolution · on-device · CPU")

        FxCard {
            Text(
                text = vm.modelMessage,
                modifier = Modifier.padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                enabled = !vm.busy,
                onClick = {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            ) { Text("Pick image") }

            Button(
                enabled = !vm.busy && vm.input != null && vm.modelState == ModelState.Ready,
                onClick = { vm.upscale() },
            ) { Text("Upscale x3") }
        }

        if (vm.busy) {
            if (vm.tilesTotal > 0) {
                LinearProgressIndicator(
                    progress = { vm.tilesDone.toFloat() / vm.tilesTotal.toFloat() },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            if (vm.statusText.isNotEmpty()) Text(vm.statusText)
            if (vm.tilesTotal > 0) {
                OutlinedButton(onClick = { vm.cancel() }) { Text("Cancel") }
            }
        }

        vm.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        vm.input?.let { img ->
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

        vm.result?.let { r ->
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
