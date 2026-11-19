package com.featherx.app.feature.gamespace

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.featherx.app.feature.protools.OverlayController
import com.featherx.app.ui.FxButton
import com.featherx.app.ui.fxClickable
import com.featherx.app.ui.theme.FxColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Horizontal "game deck": a snapping carousel of floating game cards and one big LAUNCH button under the
 * centred card. Add / Refresh / Remove are small icon pills. On short screens (landscape) the deck fills the
 * height and the pills + LAUNCH move into a column on the right. The space scene itself is drawn by the app
 * behind the status bar and the bottom bar ([SpaceBackdrop]).
 */
@Composable
fun GameSpaceScreen(vm: GameSpaceViewModel) {
    var showAddDialog by rememberSaveable { mutableStateOf(false) }
    val library = vm.library
    val pagerState = rememberPagerState(pageCount = { library.size })
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Re-read installed apps every time this tab is opened.
    LaunchedEffect(Unit) { vm.refresh() }

    // First time the deck is filled, start on the game launched most recently from FeatherX.
    var positioned by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(library.size, vm.loading) {
        if (!positioned && !vm.loading && library.isNotEmpty()) {
            positioned = true
            val recent = vm.recents.firstOrNull()?.packageName
            val index = library.indexOfFirst { it.app.packageName == recent }
            if (index > 0) pagerState.scrollToPage(index)
        }
    }

    // A light tick every time the deck snaps to another game.
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val bob = rememberInfiniteTransition(label = "float").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "bob",
    )

    val launchSelected = {
        library.getOrNull(pagerState.currentPage)?.let { vm.launch(it.app.packageName) }
        Unit
    }
    val removeSelected = {
        library.getOrNull(pagerState.currentPage)?.let { vm.remove(it.app.packageName) }
        Unit
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth > maxHeight && maxHeight < 380.dp

        if (library.isEmpty()) {
            Column(Modifier.fillMaxSize()) {
                if (!compact) TopBar(0, vm.loading, false, { showAddDialog = true }, { if (!vm.loading) vm.refresh() }, removeSelected)
                EmptyStage(loading = vm.loading, onAdd = { showAddDialog = true })
            }
        } else if (compact) {
            // ---- landscape: deck on the left, controls on the right
            Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                    val cardH = (maxHeight - 12.dp).coerceIn(90.dp, 300.dp)
                    val cardW = (cardH * 0.72f).coerceAtMost(maxWidth * 0.5f)
                    DeckPager(
                        pagerState, library, vm, bob, cardW, cardH, (maxWidth - cardW) / 2,
                        Modifier.fillMaxSize(),
                    ) { page -> scope.launch { pagerState.animateScrollToPage(page) } }
                }
                Column(
                    modifier = Modifier.width(176.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterVertically),
                ) {
                    Text(
                        "${pagerState.currentPage + 1} / ${library.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = FxColors.Muted,
                    )
                    ActionPills(vm.loading, true, { showAddDialog = true }, { if (!vm.loading) vm.refresh() }, removeSelected)
                    LaunchButton(launchSelected, compact = true)
                    vm.message?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                    }
                }
            }
        } else {
            // ---- portrait: top bar, deck, LAUNCH
            Column(Modifier.fillMaxSize()) {
                TopBar(library.size, vm.loading, true, { showAddDialog = true }, { if (!vm.loading) vm.refresh() }, removeSelected)
                vm.message?.let { text ->
                    Text(
                        text,
                        modifier = Modifier.padding(horizontal = 16.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val cardH = (maxHeight - 130.dp).coerceIn(150.dp, 330.dp)
                    val cardW = (cardH * 0.72f).coerceAtMost(maxWidth * 0.62f)
                    val sidePad = (maxWidth - cardW) / 2 // read here: maxWidth is not reachable inside Column {}
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DeckPager(
                            pagerState, library, vm, bob, cardW, cardH, sidePad,
                            Modifier.fillMaxWidth().height(cardH + 28.dp),
                        ) { page -> scope.launch { pagerState.animateScrollToPage(page) } }
                        Spacer(Modifier.height(14.dp))
                        LaunchButton(launchSelected, compact = false)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${pagerState.currentPage + 1} / ${library.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = FxColors.Muted,
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddGameDialog(vm = vm, onDismiss = { showAddDialog = false })
    }
}

@Composable
private fun DeckPager(
    pagerState: PagerState,
    library: List<LibraryEntry>,
    vm: GameSpaceViewModel,
    bob: State<Float>,
    cardW: Dp,
    cardH: Dp,
    sidePadding: Dp,
    modifier: Modifier,
    onTapPage: (Int) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = sidePadding),
        pageSize = PageSize.Fixed(cardW),
        pageSpacing = 14.dp,
        verticalAlignment = Alignment.CenterVertically,
        key = { library.getOrNull(it)?.app?.packageName ?: it },
    ) { page ->
        val entry = library.getOrNull(page)
        if (entry != null) {
            // 1 when centred, 0 one page away.
            val distance = abs((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
            GameCard(
                entry = entry,
                vm = vm,
                bob = bob,
                selected = (1f - distance).coerceIn(0f, 1f),
                modifier = Modifier.width(cardW).height(cardH),
                onTap = { onTapPage(page) },
            )
        }
    }
}

// ------------------------------------------------------------------ bars

@Composable
private fun TopBar(
    count: Int,
    loading: Boolean,
    canRemove: Boolean,
    onAdd: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Games", style = MaterialTheme.typography.headlineSmall, color = FxColors.Text)
            Text(
                if (count == 1) "1 game" else "$count games",
                style = MaterialTheme.typography.bodySmall,
                color = FxColors.Muted,
            )
        }
        ActionPills(loading, canRemove, onAdd, onRefresh, onRemove)
    }
}

@Composable
private fun ActionPills(loading: Boolean, canRemove: Boolean, onAdd: () -> Unit, onRefresh: () -> Unit, onRemove: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconPill(onClick = onAdd) { Icon(Icons.Filled.Add, contentDescription = "Add game", tint = FxColors.Cyan) }
        IconPill(onClick = onRefresh) {
            if (loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = FxColors.Cyan)
            else Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = FxColors.Text)
        }
        if (canRemove) {
            IconPill(onClick = onRemove) {
                Icon(Icons.Filled.Delete, contentDescription = "Remove selected game", tint = FxColors.Danger)
            }
        }
    }
}

@Composable
private fun IconPill(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .fxClickable(onClick)
            .clip(CircleShape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, FxColors.Border, CircleShape),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun EmptyStage(loading: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp, color = FxColors.Cyan)
        } else {
            Text("No games yet", style = MaterialTheme.typography.titleMedium, color = FxColors.Text)
            Spacer(Modifier.height(4.dp))
            Text("Add one to build your deck", style = MaterialTheme.typography.bodySmall, color = FxColors.Muted)
            Spacer(Modifier.height(16.dp))
            FxButton("Add game", onClick = onAdd)
        }
    }
}

// ------------------------------------------------------------------ deck

/**
 * One translucent glass pedestal. [selected] is 1 for the centred card and fades to 0 for the others:
 * it drives size, brightness, the cyan rim and the glow. The icon bobs up and down forever ([bob]).
 * The icon size follows the card height, so cards also work on short (landscape) screens.
 */
@Composable
private fun GameCard(
    entry: LibraryEntry,
    vm: GameSpaceViewModel,
    bob: State<Float>,
    selected: Float,
    modifier: Modifier,
    onTap: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    val profiled by OverlayController.profiled.collectAsState()
    val hasProfile = entry.app.packageName in profiled

    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer {
                val scale = 0.84f + 0.16f * selected
                scaleX = scale
                scaleY = scale
                alpha = 0.50f + 0.50f * selected
            }
            .drawBehind {
                // soft cyan glow behind the glass, strongest on the centred card
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(FxColors.Cyan.copy(alpha = 0.30f * selected), Color.Transparent),
                        center = center,
                        radius = size.maxDimension * 0.75f,
                    ),
                    radius = size.maxDimension * 0.75f,
                )
            }
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF))))
            .border(
                width = (1f + 1.2f * selected).dp,
                brush = Brush.linearGradient(
                    listOf(
                        FxColors.Cyan.copy(alpha = 0.25f + 0.75f * selected),
                        Color(0x14FFFFFF),
                        FxColors.Blue.copy(alpha = 0.20f + 0.60f * selected),
                    ),
                ),
                shape = shape,
            )
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        val iconSize = (maxHeight * 0.40f).coerceIn(44.dp, 84.dp)
        val roomy = maxHeight >= 220.dp
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(if (roomy) 16.dp else 8.dp),
        ) {
            Box(Modifier.size(width = iconSize + 36.dp, height = iconSize + 44.dp), contentAlignment = Alignment.Center) {
                // contact glow on the "pedestal": shrinks as the icon rises
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .size(width = iconSize, height = 12.dp)
                        .graphicsLayer {
                            val s = 1f - 0.25f * bob.value
                            scaleX = s
                            scaleY = s
                            alpha = (0.35f + 0.65f * selected) * (1f - 0.35f * bob.value)
                        }
                        .clip(CircleShape)
                        .background(Brush.radialGradient(listOf(FxColors.Cyan.copy(alpha = 0.55f), Color.Transparent))),
                )
                AppIcon(
                    packageName = entry.app.packageName,
                    vm = vm,
                    size = iconSize,
                    modifier = Modifier.offset {
                        // levitation: -7 dp .. +7 dp, read in the layout phase (no recomposition per frame)
                        IntOffset(0, ((bob.value - 0.5f) * -14.dp.toPx()).roundToInt())
                    },
                )
            }
            Spacer(Modifier.height(if (roomy) 10.dp else 2.dp))
            Text(
                text = entry.app.label,
                style = if (roomy) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = FxColors.Text,
                textAlign = TextAlign.Center,
                maxLines = if (roomy) 2 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (roomy) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (hasProfile) "Profile saved" else if (entry.addedByUser) "Added by you" else "Detected",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasProfile) FxColors.Cyan else FxColors.Muted,
                )
            }
        }
    }
}

/** Big glowing LAUNCH button: springy press + haptic (from fxClickable). */
@Composable
private fun LaunchButton(onClick: () -> Unit, compact: Boolean) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .width(if (compact) 150.dp else 220.dp)
            .height(if (compact) 46.dp else 56.dp)
            .fxClickable(onClick)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        listOf(FxColors.Cyan.copy(alpha = 0.35f), Color.Transparent),
                        center = center,
                        radius = size.width * 0.65f,
                    ),
                    radius = size.width * 0.65f,
                )
            }
            .shadow(16.dp, shape, ambientColor = FxColors.Cyan, spotColor = FxColors.Cyan)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(FxColors.Cyan, FxColors.Blue))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "LAUNCH",
            color = SpaceBlack,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 15.sp else 17.sp,
            letterSpacing = 3.sp,
        )
    }
}

@Composable
private fun AppIcon(packageName: String, vm: GameSpaceViewModel, size: Dp, modifier: Modifier = Modifier) {
    // Loaded off the main thread; a plain dark square shows until the icon is ready.
    val icon: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, packageName) {
        value = vm.loadIcon(packageName)
    }
    val image = icon
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).background(Color(0x14FFFFFF), RoundedCornerShape(18.dp)))
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
