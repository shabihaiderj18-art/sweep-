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

data class Photo(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val takenAt: Long,
    val folderId: String,
    val folderName: String,
    val isFavorite: Boolean,
)

/** One album on the home screen: "All photos", "Favorites", or a real folder like Camera. */
data class Album(val id: String, val name: String, val photos: List<Photo>)

data class MonthGroup(val month: YearMonth, val photos: List<Photo>) {
    val label: String
        get() = month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))
}

const val ALL_ID = "all"
const val FAVORITES_ID = "favorites"

class PhotoRepository(private val context: Context) {

    fun loadAlbums(): List<Album> {
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.BUCKET_ID,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.IS_FAVORITE,
        )
        val photos = mutableListOf<Photo>()

        context.contentResolver.query(collection, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val sizeCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val takenCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val addedCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val folderIdCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_ID)
            val folderNameCol = c.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
            val favCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)

            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                val taken = c.getLong(takenCol).takeIf { it > 0 } ?: (c.getLong(addedCol) * 1000)
                photos += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(nameCol) ?: "Photo",
                    size = c.getLong(sizeCol),
                    takenAt = taken,
                    folderId = c.getString(folderIdCol) ?: "0",
                    folderName = c.getString(folderNameCol) ?: "Other",
                    isFavorite = c.getInt(favCol) == 1,
                )
            }
        }

        // Newest first, like the gallery.
        photos.sortByDescending { it.takenAt }

        val albums = mutableListOf<Album>()
        if (photos.isNotEmpty()) albums += Album(ALL_ID, "All photos", photos)

        val favorites = photos.filter { it.isFavorite }
        if (favorites.isNotEmpty()) albums += Album(FAVORITES_ID, "Favorites", favorites)

        // Camera and Screenshots first, then the other folders from biggest to smallest.
        val pinned = listOf("camera", "screenshots")
        albums += photos
            .groupBy { it.folderId }
            .map { (id, list) -> Album(id, list.first().folderName, list) }
            .sortedWith(
                compareBy<Album> { pinned.indexOf(it.name.lowercase()).let { i -> if (i == -1) Int.MAX_VALUE else i } }
                    .thenByDescending { it.photos.size }
            )
        return albums
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
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(Manifest.permission.READ_MEDIA_IMAGES)
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    fun granted(context: Context): Boolean = required().any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }
}

fun formatSize(bytes: Long): String = when {
    bytes >= 1_073_741_824 -> String.format(Locale.US, "%.2f GB", bytes / 1_073_741_824.0)
    bytes >= 1_048_576 -> String.format(Locale.US, "%.1f MB", bytes / 1_048_576.0)
    else -> "${bytes / 1024} KB"
}

fun photoCount(n: Int) = if (n == 1) "1 photo" else "$n photos"
