package com.featherx.app.feature.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.featherx.app.BuildConfig
import com.featherx.app.ui.FxCard
import com.featherx.app.ui.InfoRow
import com.featherx.app.ui.ScreenHeader

/** Read-only information for now. There are no settings to change yet, so there are no controls. */
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Settings", "About")

        FxCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                InfoRow("Version", BuildConfig.VERSION_NAME)
                InfoRow("Licence (original code)", "Apache-2.0")
                InfoRow("Device", deviceName(Build.MANUFACTURER, Build.MODEL))
                InfoRow("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            }
        }

        FxCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                InfoRow("AndroidX / Jetpack Compose", "Apache-2.0")
            }
        }
    }
}
