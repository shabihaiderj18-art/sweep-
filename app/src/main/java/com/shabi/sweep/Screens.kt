package com.shabi.sweep

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.abs

// ---------- Month list ----------

@Composable
fun MonthsScreen(vm: SweepViewModel) {
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Spacer(Modifier.height(20.dp))
        Text("Sweep", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Text("Pick a month to clean up.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (vm.lastFreedBytes > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Cleared ${formatSize(vm.lastFreedBytes)} in your last session.",
                color = KeepColor, fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(16.dp))
        when {
            vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            vm.months.isEmpty() -> Text("No photos found on this phone.")
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(vm.months, key = { it.month.toString() }) { month ->
                    MonthRow(month, vm.remainingIn(month)) { vm.openMonth(month) }
                }
            }
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
                if (remaining == 0) "All done, ${photoCount(month.photos.size)}"
                else "$remaining of ${photoCount(month.photos.size)} left",
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.goBack() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(Modifier.weight(1f)) {
                Text(vm.currentMonth?.label.orEmpty(), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "${vm.index + 1} of ${vm.queue.size}",
                    fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { scaleX = 0.94f; scaleY = 0.94f; translationY = 28f; alpha = 0.55f }
                        .clip(RoundedCornerShape(24.dp)),
                )
            }
            key(photo.id) {
                SwipeCard(photo, buttonChoice) { keep ->
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
            Text(
                formatSize(photo.size),
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(64.dp),
            )
        }
    }
}

@Composable
private fun SwipeCard(photo: Photo, buttonChoice: Boolean?, onSwiped: (keep: Boolean) -> Unit) {
    val scope = rememberCoroutineScope()
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var gone by remember { mutableStateOf(false) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = constraints.maxWidth.toFloat()
        val threshold = width * 0.28f

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
                .background(MaterialTheme.colorScheme.surface)
                .pointerInput(width) {
                    detectDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (abs(offsetX.value) > threshold) {
                                    flyOut(offsetX.value > 0)
                                } else {
                                    launch { offsetY.animateTo(0f, spring()) }
                                    offsetX.animateTo(0f, spring())
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                launch { offsetY.animateTo(0f, spring()) }
                                offsetX.animateTo(0f, spring())
                            }
                        },
                    ) { change, drag ->
                        change.consume()
                        if (!gone) scope.launch {
                            offsetX.snapTo(offsetX.value + drag.x)
                            offsetY.snapTo(offsetY.value + drag.y)
                        }
                    }
                },
        ) {
            AsyncImage(
                model = photo.uri,
                contentDescription = photo.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)
            Stamp(
                "KEEP", KeepColor,
                Modifier.align(Alignment.TopStart).padding(20.dp).rotate(-12f).alpha(progress.coerceAtLeast(0f)),
            )
            Stamp(
                "DELETE", TossColor,
                Modifier.align(Alignment.TopEnd).padding(20.dp).rotate(12f).alpha((-progress).coerceAtLeast(0f)),
            )

            Text(
                photo.name,
                color = Color.White, fontSize = 14.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
                    .padding(start = 16.dp, end = 16.dp, top = 28.dp, bottom = 14.dp),
            )
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
            else "${photoCount(marked.size)} marked for deletion. Tap a photo to keep it instead.",
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
            Text("Delete ${photoCount(marked.size)}", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
