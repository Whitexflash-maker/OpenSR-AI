package com.featherx.app.feature.home

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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.featherx.app.BuildConfig
import com.featherx.app.ui.FxCard
import com.featherx.app.ui.InfoRow
import com.featherx.app.ui.theme.FxColors

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = buildAnnotatedString {
                append("Feather")
                withStyle(SpanStyle(color = FxColors.Blue)) { append("X") }
            },
            style = MaterialTheme.typography.displaySmall.copy(
                shadow = Shadow(color = FxColors.Blue.copy(alpha = 0.55f), blurRadius = 28f),
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Lightweight. Intelligent. Powerful.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        FxCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "Feature status · v${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                InfoRow("Pro Tools overlay (Step 5b)", "Built, needs testing on phone", FxColors.BlueSoft)
                InfoRow("Game Space", "Basic version (Step 5)", FxColors.BlueSoft)
                InfoRow("Performance modes", "Not built (Step 6)", FxColors.Warn)
                InfoRow("Device monitor", "Not built (Step 7)", FxColors.Warn)
                InfoRow("Thermal & memory safety", "Not built (Step 8)", FxColors.Warn)
            }
        }

        FxCard {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Principles",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Only public Android APIs. No changes to other apps or games, " +
                        "no clock-speed or thermal overrides, no FPS promises. " +
                        "A metric that cannot be measured is shown as unavailable, never invented.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
