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
import com.featherx.app.ui.theme.FxColors

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
        ScreenHeader("Performance", "Modes and monitor")
        FxCard {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Coming next", style = MaterialTheme.typography.titleSmall)
                Text(
                    "LOW · MEDIUM · HIGH · ULTRA · AUTO",
                    style = MaterialTheme.typography.labelLarge,
                    color = FxColors.Cyan,
                )
                Text(
                    "Not built yet. Live battery, RAM and ping already run in the Pro Tools sidebar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
