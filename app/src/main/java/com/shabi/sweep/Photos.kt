package com.shabi.sweep

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One item in the gallery: a photo or a video. */
data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val takenAt: Long,
    val folderId: String,
    val folderName: String,
    val isFavorite: Boolean,
    val isVideo: Boolean,
    val duration: Long, // milliseconds, 0 for photos
)

data class Album(val id: String, val name: String, val photos: List<Photo>)

data class MonthGroup(val month: YearMonth, val photos: List<Photo>) {
    val label: String
        get() = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
}

const val ALL_ID = "all"
const val FAVORITES_ID = "favorites"
const val VIDEOS_ID = "videos"

class PhotoRepository(private val context: Context) {

    fun loadAlbums(): List<Album> {
        val items = mutableListOf<Photo>()
        query(MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), isVideo = false, items)
        query(MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL), isVideo = true, items)
        items.sortByDescending { it.takenAt }

        val albums = mutableListOf<Album>()
        if (items.isNotEmpty()) albums += Album(ALL_ID, "All", items)

        val favorites = items.filter { it.isFavorite }
        if (favorites.isNotEmpty()) albums += Album(FAVORITES_ID, "Favorites", favorites)

        val videos = items.filter { it.isVideo }
        if (videos.isNotEmpty()) albums += Album(VIDEOS_ID, "Videos", videos)

        // Camera and Screenshots first, then the other folders from biggest to smallest.
        val pinned = listOf("camera", "screenshots")
        albums += items
            .groupBy { it.folderId }
            .map { (id, list) -> Album(id, list.first().folderName, list) }
            .sortedWith(
                compareBy<Album> { pinned.indexOf(it.name.lowercase()).let { i -> if (i == -1) Int.MAX_VALUE else i } }
                    .thenByDescending { it.photos.size }
            )
        return albums
    }

    private fun query(collection: Uri, isVideo: Boolean, out: MutableList<Photo>) {
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.BUCKET_ID,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.IS_FAVORITE,
            MediaStore.MediaColumns.DURATION,
        )
        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
            val folderIdCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_ID)
            val folderNameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
            val favCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
            val durCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)
                out += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(nameCol) ?: if (isVideo) "Video" else "Photo",
                    size = c.getLong(sizeCol),
                    takenAt = taken,
                    folderId = c.getString(folderIdCol) ?: "0",
                    folderName = c.getString(folderNameCol) ?: "Other",
                    isFavorite = c.getInt(favCol) == 1,
                    isVideo = isVideo,
                    duration = if (isVideo) c.getLong(durCol) else 0L,
                )
            }
        }
    }
}

fun groupByMonth(photos: List<Photo>): List<MonthGroup> {
    val zone = ZoneId.systemDefault()
    return photos
        .groupBy { YearMonth.from(Instant.ofEpochMilli(it.takenAt).atZone(zone)) }
        .map { (month, list) -> MonthGroup(month, list.sortedBy { it.takenAt }) }
        .sortedByDescending { it.month }
}

object PhotoPermissions {
    fun required(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun granted(context: Context): Boolean {
        fun has(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE ->
                (has(Manifest.permission.READ_MEDIA_IMAGES) && has(Manifest.permission.READ_MEDIA_VIDEO)) ||
                    has(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ->
                has(Manifest.permission.READ_MEDIA_IMAGES) && has(Manifest.permission.READ_MEDIA_VIDEO)
            else -> has(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    else -> "${bytes / 1024} KB"
}

fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

fun itemCount(n: Int) = if (n == 1) "1 item" else "$n items"
