package com.featherx.app.feature.performance

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
import androidx.compose.ui.unit.dp
import com.featherx.app.ui.FxCard
import com.featherx.app.ui.ScreenHeader

/** Not implemented yet: this screen only says so. Profiles are Step 6, monitoring Step 7. */
@Composable
fun PerformanceScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScreenHeader("Performance", "Modes and device monitor")
        FxCard {
            Text(
                text = "Not built yet. Step 6 adds the LOW / MEDIUM / HIGH / ULTRA / AUTO modes " +
                    "(they will control only FeatherX's own processing). Step 7 adds the real " +
                    "device monitor and Step 8 the thermal and memory protection.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
