package com.featherx.app.feature.gamespace

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.featherx.app.feature.protools.OverlayController
import com.featherx.app.ui.FxButton
import com.featherx.app.ui.FxCard
import com.featherx.app.ui.SectionLabel
import com.featherx.app.ui.ScreenHeader
import com.featherx.app.ui.theme.FxColors

@Composable
fun GameSpaceScreen(vm: GameSpaceViewModel) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }

    // Re-read installed apps every time this tab is opened.
    LaunchedEffect(Unit) { vm.refresh() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Games", "Library · ${vm.library.size}") }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FxButton("Add game", onClick = { showAddDialog = true })
                FxButton("Refresh", onClick = { if (!vm.loading) vm.refresh() }, filled = false)
            }
        }

        if (vm.loading) {
            item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        }

        vm.message?.let { text ->
            item { Text(text, color = MaterialTheme.colorScheme.error) }
        }

        if (vm.recents.isNotEmpty()) {
            item { SectionLabel("Recent") }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(vm.recents, key = { it.packageName }) { app ->
                        RecentTile(app, vm, onClick = { vm.launch(app.packageName) })
                    }
                }
            }
        }

        item { SectionLabel("Library") }

        if (vm.library.isEmpty() && !vm.loading) {
            item {
                FxCard {
                    Text(
                        text = "No games found. Tap Add game and pick yours.",
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            items(vm.library, key = { it.app.packageName }) { entry ->
                FxCard {
                    LibraryRow(
                        entry = entry,
                        vm = vm,
                        onLaunch = { vm.launch(entry.app.packageName) },
                        onRemove = { vm.remove(entry.app.packageName) },
                    )
                }
            }
        }

        item {
            Text(
                text = "Reads app names and icons only. Nothing leaves your phone.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showAddDialog) {
        AddGameDialog(
            vm = vm,
            onDismiss = { showAddDialog = false },
        )
    }
}

@Composable
private fun LibraryRow(
    entry: LibraryEntry,
    vm: GameSpaceViewModel,
    onLaunch: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onLaunch)
            .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(entry.app.packageName, vm, size = 48.dp)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = entry.app.label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val profiled by OverlayController.profiled.collectAsState()
            val hasProfile = entry.app.packageName in profiled
            Text(
                text = if (hasProfile) "Profile saved" else if (entry.addedByUser) "Added by you" else "Detected",
                style = MaterialTheme.typography.labelSmall,
                color = if (hasProfile) FxColors.Cyan else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}

@Composable
private fun RecentTile(app: InstalledApp, vm: GameSpaceViewModel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(76.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppIcon(app.packageName, vm, size = 52.dp)
        Spacer(Modifier.height(4.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AppIcon(packageName: String, vm: GameSpaceViewModel, size: Dp) {
    // Loaded off the main thread; a plain dark square shows until the icon is ready.
    val icon: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = vm.loadIcon(packageName)
    }
    val image = icon
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(
            Modifier
                .size(size)
                .background(FxColors.Navy, RoundedCornerShape(10.dp)),
        )
    }
}

@Composable
private fun AddGameDialog(vm: GameSpaceViewModel, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val candidates = remember(vm.installed, vm.library, query) {
        LibraryLogic.addCandidates(vm.installed, vm.library, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FxColors.Navy,
        title = { Text("Add a game") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Search apps") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (candidates.isEmpty()) {
                    Text(
                        text = "No matching apps.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    LazyColumn(Modifier.heightIn(max = 320.dp)) {
                        items(candidates, key = { it.packageName }) { app ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        vm.add(app.packageName)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AppIcon(app.packageName, vm, size = 40.dp)
                                Text(
                                    text = app.label,
                                    modifier = Modifier.padding(start = 12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
