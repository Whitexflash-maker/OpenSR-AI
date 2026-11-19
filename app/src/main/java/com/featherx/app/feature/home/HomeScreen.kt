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
import androidx.compose.foundation.layout.Row
import com.featherx.app.ui.FxCard
import com.featherx.app.ui.FxChip
import com.featherx.app.ui.SectionLabel
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
                withStyle(SpanStyle(color = FxColors.Cyan)) { append("X") }
            },
            style = MaterialTheme.typography.displaySmall.copy(
                shadow = Shadow(color = FxColors.Cyan.copy(alpha = 0.5f), blurRadius = 32f),
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "Lightweight. Intelligent. Powerful.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("Status")
        FxCard {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                InfoRow("Games", "Ready", FxColors.Ok)
                InfoRow("Pro Tools sidebar", "Beta", FxColors.Cyan)
                InfoRow("Performance modes", "Planned", FxColors.Muted)
                InfoRow("Thermal & RAM guard", "Badge in sidebar", FxColors.Cyan)
            }
        }

        FxCard {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Public APIs only", "No root", "No FPS claims").forEach { FxChip(it, false) {} }
            }
        }
    }
}
