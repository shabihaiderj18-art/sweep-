package com.shabi.sweep

import android.net.Uri
import androidx.annotation.OptIn
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------- Albums (home) ----------

@Composable
fun AlbumsScreen(vm: SweepViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Sweep", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Pick an album to clean up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (vm.totalFreedBytes > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "You've cleared ${formatSize(vm.totalFreedBytes)} so far" +
                    if (vm.lastFreedBytes > 0) " (${formatSize(vm.lastFreedBytes)} just now)." else ".",
                color = KeepColor, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            vm.albums.isEmpty() -> Text("No photos or videos found on this phone.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(vm.albums, key = { it.id }) { album ->
                    AlbumTile(album, vm.remainingIn(album.photos)) { vm.openAlbum(album) }
                }
            }
        }
    }
}

@Composable
private fun AlbumTile(album: Album, remaining: Int, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)) {
        AsyncImage(
            model = album.photos.first().uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            album.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Text(
            "${itemCount(album.photos.size)}, ${formatSize(album.photos.sumOf { it.size })}",
            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
        )
        Text(
            if (remaining == 0) "All done" else "$remaining left to review",
            color = if (remaining == 0) KeepColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

// ---------- Inside an album: filters, sorting, months ----------

@Composable
fun MonthsScreen(vm: SweepViewModel) {
    BackHandler { vm.goBack() }
    val items = vm.albumItems
    val remaining = vm.remainingIn(items)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.goBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        vm.currentAlbum?.name.orEmpty(), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${itemCount(items.size)}, ${formatSize(items.sumOf { it.size })}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
                    )
                }
            }
        }

        item {
            Text("Show", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            ChipRow(TypeFilter.entries, vm.typeFilter, { it.label }) { vm.changeFilter(it) }
        }

        item {
            Text("Sort by", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            ChipRow(SortOrder.entries, vm.sort, { it.label }) { vm.changeSort(it) }
        }

        item {
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { vm.openAll() },
                enabled = items.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(54.dp),
            ) {
                Text(
                    if (remaining > 0) "Swipe all $remaining, ${vm.sort.label.lowercase()} first"
                    else "Review all again, ${vm.sort.label.lowercase()} first",
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                )
            }
            if (remaining < items.size) {
                TextButton(onClick = { vm.resetAlbum() }) {
                    Text("Reset: show items I already kept")
                }
            }
        }

        item {
            Text(
                "Or pick a month",
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (vm.months.isEmpty()) {
            item { Text("Nothing here.") }
        } else {
            items(vm.months, key = { it.month.toString() }) { month ->
                MonthRow(month, vm.remainingIn(month.photos)) { vm.openMonth(month) }
            }
        }
    }
}

@Composable
private fun <T> ChipRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            val isOn = option == selected
            Text(
                label(option),
                fontSize = 14.sp,
                fontWeight = if (isOn) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isOn) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isOn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(50))
                    .clickable { onSelect(option) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun MonthRow(month: MonthGroup, remaining: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = month.photos.first().uri,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(month.label, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "${itemCount(month.photos.size)}, ${formatSize(month.photos.sumOf { it.size })}",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
            )
            Text(
                if (remaining == 0) "All done" else "$remaining left to review",
                color = if (remaining == 0) KeepColor else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
        }
        if (remaining == 0) Icon(Icons.Default.Check, contentDescription = "Done", tint = KeepColor)
    }
}

// ---------- Swiping ----------

@Composable
fun SwipeScreen(vm: SweepViewModel) {
    BackHandler { vm.goBack() }
    val photo = vm.current ?: return
    // Set by the buttons: true = keep, false = delete. The card animates away, then reports back.
    var buttonChoice by remember(photo.id) { mutableStateOf<Boolean?>(null) }
    var playing by remember(photo.id) { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(vm.sessionTitle, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${vm.index + 1} of ${vm.queue.size}. " + if (photo.isVideo) "Tap the video to play it" else "Pinch or double-tap to zoom",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { vm.openReview() }, enabled = vm.toDelete.isNotEmpty()) {
                Text("Review (${vm.toDelete.size})")
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 14.dp)) {
            vm.next?.let { next ->
                AsyncImage(
                    model = next.uri,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; translationY = 28f; alpha = 0.55f }
                        .clip(RoundedCornerShape(24.dp))
                        .background(Color.Black),
                )
            }
            key(photo.id) {
                SwipeCard(photo, buttonChoice, onPlay = { playing = true }) { keep ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    vm.decide(if (keep) Choice.Keep else Choice.Delete)
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { vm.undo() }, enabled = vm.canUndo) { Text("Undo") }
            RoundAction(Icons.Default.Close, "Delete", TossColor) { buttonChoice = false }
            RoundAction(Icons.Default.Check, "Keep", KeepColor) { buttonChoice = true }
            TextButton(onClick = { vm.decide(Choice.Skip) }) { Text("Skip") }
        }
    }

    if (playing) {
        VideoPlayerOverlay(
            video = photo,
            onClose = { playing = false },
            onKeep = { playing = false; buttonChoice = true },
            onDelete = { playing = false; buttonChoice = false },
        )
    }
    }
}

@Composable
private fun SwipeCard(
    photo: Photo,
    buttonChoice: Boolean?,
    onPlay: () -> Unit,
    onSwiped: (keep: Boolean) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var gone by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var pan by remember { mutableStateOf(Offset.Zero) }

    // Load a sharper version so zoomed photos don't look blurry (max 2048 px keeps memory safe).
    val request = remember(photo.id) {
        ImageRequest.Builder(context).data(photo.uri).size(2048).build()
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val height = constraints.maxHeight.toFloat()
        val threshold = width * 0.28f

        fun clampPan(p: Offset, z: Float): Offset {
            val maxX = width * (z - 1f) / 2f
            val maxY = height * (z - 1f) / 2f
            return Offset(p.x.coerceIn(-maxX, maxX), p.y.coerceIn(-maxY, maxY))
        }

        val flyOut: suspend (Boolean) -> Unit = { keep ->
            if (!gone) {
                gone = true
                offsetX.animateTo(if (keep) width * 1.4f else -width * 1.4f, tween(220))
                onSwiped(keep)
            }
        }

        LaunchedEffect(buttonChoice) { buttonChoice?.let { flyOut(it) } }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    translationY = offsetY.value
                    rotationZ = offsetX.value / 30f
                }
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black)
                // Videos: tap to play. Photos: double-tap to zoom in where you tapped, or back out.
                .pointerInput(photo.id) {
                    if (photo.isVideo) detectTapGestures(onTap = { onPlay() })
                    else detectTapGestures(onDoubleTap = { tap ->
                        if (zoom > 1f) {
                            zoom = 1f
                            pan = Offset.Zero
                        } else {
                            val z = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            zoom = z
                            pan = clampPan((center - tap) * (z - 1f), z)
                        }
                    })
                }
                // Two fingers = zoom. One finger = move the zoomed photo, or swipe if not zoomed.
                .pointerInput(photo.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var usedTwoFingers = false
                        var dragging = false
                        var swiping = false
                        var moved = Offset.Zero
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            if (fingers >= 2) {
                                usedTwoFingers = true
                                if (!photo.isVideo) {
                                    val newZoom = (zoom * event.calculateZoom()).coerceIn(1f, 5f)
                                    pan = clampPan(pan + event.calculatePan(), newZoom)
                                    zoom = newZoom
                                    event.changes.forEach { it.consume() }
                                }
                            } else if (fingers == 1 && !usedTwoFingers) {
                                val delta = event.calculatePan()
                                moved += delta
                                if (!dragging && moved.getDistance() > viewConfiguration.touchSlop) dragging = true
                                if (dragging) {
                                    if (zoom > 1f) {
                                        pan = clampPan(pan + delta, zoom)
                                    } else if (!gone) {
                                        swiping = true
                                        scope.launch {
                                            offsetX.snapTo(offsetX.value + delta.x)
                                            offsetY.snapTo(offsetY.value + delta.y)
                                        }
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        if (zoom < 1.05f) {
                            zoom = 1f
                            pan = Offset.Zero
                        }
                        if (swiping) {
                            scope.launch {
                                if (abs(offsetX.value) > threshold) {
                                    flyOut(offsetX.value > 0)
                                } else {
                                    launch { offsetY.animateTo(0f, spring()) }
                                    offsetX.animateTo(0f, spring())
                                }
                            }
                        }
                    }
                },
        ) {
            AsyncImage(
                model = request,
                contentDescription = photo.name,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = zoom
                        scaleY = zoom
                        translationX = pan.x
                        translationY = pan.y
                    },
            )

            if (photo.isVideo) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(76.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(46.dp))
                }
                Text(
                    formatDuration(photo.duration),
                    color = Color.White, fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 22.dp)
                        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)
            Stamp(
                "KEEP", KeepColor,
                Modifier.align(Alignment.TopStart).padding(20.dp).rotate(-12f).alpha(progress.coerceAtLeast(0f)),
            )
            Stamp(
                "DELETE", TossColor,
                Modifier.align(Alignment.TopEnd).padding(20.dp).rotate(12f).alpha((-progress).coerceAtLeast(0f)),
            )

            if (zoom == 1f) {
                Column(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                        .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
                ) {
                    Text(
                        "${formatDate(photo.takenAt)}, ${formatSize(photo.size)}",
                        color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        photo.name,
                        color = Color.White.copy(alpha = 0.75f), fontSize = 12.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Full-screen player with a seek bar, 10-second rewind/fast-forward and play/pause. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayerOverlay(video: Photo, onClose: () -> Unit, onKeep: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val exo = remember(video.id) {
        ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                setMediaItem(MediaItem.fromUri(video.uri))
                prepare()
                playWhenReady = true
            }
    }
    DisposableEffect(exo) { onDispose { exo.release() } }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { exo.pause() }
    BackHandler { onClose() }

    // pointerInput here stops touches from reaching the swipe card underneath.
    Column(Modifier.fillMaxSize().background(Color.Black).pointerInput(Unit) { detectTapGestures { } }) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
            Text(
                video.name, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                formatSize(video.size), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp,
                modifier = Modifier.padding(end = 8.dp),
            )
        }
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setShowRewindButton(true)
                    setShowFastForwardButton(true)
                }
            },
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = onDelete,
                colors = ButtonDefaults.buttonColors(containerColor = TossColor, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Delete", fontWeight = FontWeight.SemiBold) }
            Button(
                onClick = onKeep,
                colors = ButtonDefaults.buttonColors(containerColor = KeepColor, contentColor = Color.White),
                modifier = Modifier.weight(1f).height(52.dp),
            ) { Text("Keep", fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun Stamp(text: String, color: Color, modifier: Modifier) {
    Text(
        text,
        color = color, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold,
        modifier = modifier
            .background(Color.White.copy(alpha = 0.85f), RoundedCornerShape(10.dp))
            .border(4.dp, color, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    )
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(2.dp, color.copy(alpha = 0.45f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = color, modifier = Modifier.size(32.dp))
    }
}

// ---------- Review ----------

@Composable
fun ReviewScreen(vm: SweepViewModel, onDelete: (List<Uri>) -> Unit) {
    BackHandler { vm.goBack() }
    val marked = vm.toDelete
    val bytes = marked.sumOf { it.size }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Text("Review", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        Text(formatSize(bytes), fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = TossColor)
        Text(
            if (marked.isEmpty()) "Nothing marked for deletion."
            else "${itemCount(marked.size)} marked for deletion. Tap one to keep it instead.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(14.dp))

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(marked, key = { it.id }) { photo ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { vm.restore(photo) },
                ) {
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (photo.isVideo) {
                        Row(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(5.dp)
                                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                            Text(formatDuration(photo.duration), color = Color.White, fontSize = 11.sp)
                        }
                    }
                    Text(
                        "Keep",
                        color = Color.White, fontSize = 12.sp,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(5.dp)
                            .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onDelete(marked.map { it.uri }) },
            enabled = marked.isNotEmpty(),
            colors = ButtonDefaults.buttonColors(containerColor = TossColor, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Delete ${itemCount(marked.size)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        if (vm.current != null) {
            OutlinedButton(onClick = { vm.goBack() }, modifier = Modifier.fillMaxWidth()) {
                Text("Keep swiping")
            }
        } else {
            OutlinedButton(onClick = { vm.leave() }, modifier = Modifier.fillMaxWidth()) {
                Text("Back to months without deleting")
            }
        }
    }
}
