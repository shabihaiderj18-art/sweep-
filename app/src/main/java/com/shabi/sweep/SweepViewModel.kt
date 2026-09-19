package com.shabi.sweep

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Screen { Albums, Months, Swipe, Review }
enum class Choice { Keep, Delete, Skip }
enum class SortOrder(val label: String) { Oldest("Oldest"), Newest("Newest"), Largest("Largest"), Smallest("Smallest") }
enum class TypeFilter(val label: String) { All("All"), Photos("Photos"), Videos("Videos") }

class SweepViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PhotoRepository(app)
    private val prefs = app.getSharedPreferences("sweep", Context.MODE_PRIVATE)

    var hasPermission by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var albums by mutableStateOf<List<Album>>(emptyList()); private set
    var screen by mutableStateOf(Screen.Albums); private set

    var currentAlbum by mutableStateOf<Album?>(null); private set
    var months by mutableStateOf<List<MonthGroup>>(emptyList()); private set
    var sessionTitle by mutableStateOf(""); private set
    var queue by mutableStateOf<List<Photo>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set

    var lastFreedBytes by mutableLongStateOf(0L); private set
    var totalFreedBytes by mutableLongStateOf(prefs.getLong("totalFreed", 0L)); private set

    var sort by mutableStateOf(
        runCatching { SortOrder.valueOf(prefs.getString("sort", null) ?: "") }.getOrDefault(SortOrder.Oldest)
    ); private set
    var typeFilter by mutableStateOf(
        runCatching { TypeFilter.valueOf(prefs.getString("filter", null) ?: "") }.getOrDefault(TypeFilter.All)
    ); private set

    val toDelete = mutableStateListOf<Photo>()
    private val history = mutableStateListOf<Pair<Photo, Choice>>()

    private var keptIds by mutableStateOf(
        prefs.getStringSet("kept", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    )

    val current: Photo? get() = queue.getOrNull(index)
    val next: Photo? get() = queue.getOrNull(index + 1)
    val canUndo: Boolean get() = history.isNotEmpty()

    /** Items in the open album after the Photos/Videos filter. */
    val albumItems: List<Photo> get() = filtered(currentAlbum?.photos ?: emptyList())

    fun onPermissionChecked() {
        hasPermission = PhotoPermissions.granted(getApplication())
        if (hasPermission) load()
    }

    fun load() {
        viewModelScope.launch {
            loading = albums.isEmpty()
            albums = withContext(Dispatchers.IO) { repo.loadAlbums() }
            currentAlbum?.let { open -> currentAlbum = albums.find { it.id == open.id } }
            refreshMonths()
            loading = false
        }
    }

    fun remainingIn(photos: List<Photo>) = photos.count { it.id !in keptIds }

    fun openAlbum(album: Album) {
        currentAlbum = album
        refreshMonths()
        screen = Screen.Months
    }

    fun changeSort(order: SortOrder) {
        sort = order
        prefs.edit().putString("sort", order.name).apply()
    }

    fun changeFilter(filter: TypeFilter) {
        typeFilter = filter
        prefs.edit().putString("filter", filter.name).apply()
        refreshMonths()
    }

    /** Swipe through the whole album in the chosen order. */
    fun openAll() = start(albumItems, currentAlbum?.name.orEmpty())

    fun openMonth(month: MonthGroup) = start(month.photos, month.label)

    private fun start(items: List<Photo>, title: String) {
        if (items.isEmpty()) return
        sessionTitle = title
        queue = sorted(items.filter { it.id !in keptIds }.ifEmpty { items })
        index = 0
        history.clear()
        toDelete.clear()
        screen = Screen.Swipe
    }

    fun decide(choice: Choice) {
        val photo = current ?: return
        history.add(photo to choice)
        when (choice) {
            Choice.Keep -> setKept(photo.id, true)
            Choice.Delete -> toDelete.add(photo)
            Choice.Skip -> Unit // not remembered, so it shows up again next time
        }
        index++
        if (index >= queue.size) screen = Screen.Review
    }

    fun undo() {
        if (history.isEmpty()) return
        val (photo, choice) = history.removeAt(history.lastIndex)
        when (choice) {
            Choice.Keep -> setKept(photo.id, false)
            Choice.Delete -> toDelete.remove(photo)
            Choice.Skip -> Unit
        }
        index = queue.indexOf(photo).coerceAtLeast(0)
        screen = Screen.Swipe
    }

    fun restore(photo: Photo) {
        toDelete.remove(photo)
        setKept(photo.id, true)
        val i = history.indexOfFirst { it.first.id == photo.id }
        if (i >= 0) history[i] = photo to Choice.Keep
    }

    /** Forget "kept" decisions for this album so everything can be reviewed again. */
    fun resetAlbum() {
        val ids = currentAlbum?.photos?.map { it.id }?.toSet() ?: return
        keptIds = keptIds - ids
        saveKept()
    }

    fun openReview() { screen = Screen.Review }

    fun goBack() {
        when (screen) {
            Screen.Swipe -> if (toDelete.isNotEmpty()) screen = Screen.Review else leave()
            Screen.Review -> if (current != null) screen = Screen.Swipe else leave()
            Screen.Months -> screen = Screen.Albums
            Screen.Albums -> Unit
        }
    }

    fun leave() {
        toDelete.clear()
        history.clear()
        screen = Screen.Months
    }

    fun onDeleteResult(confirmed: Boolean) {
        if (!confirmed) return
        lastFreedBytes = toDelete.sumOf { it.size }
        totalFreedBytes += lastFreedBytes
        prefs.edit().putLong("totalFreed", totalFreedBytes).apply()
        toDelete.clear()
        history.clear()
        screen = if (current != null) Screen.Swipe else Screen.Months
        load()
    }

    private fun refreshMonths() {
        months = groupByMonth(albumItems)
    }

    private fun filtered(list: List<Photo>) = when (typeFilter) {
        TypeFilter.All -> list
        TypeFilter.Photos -> list.filter { !it.isVideo }
        TypeFilter.Videos -> list.filter { it.isVideo }
    }

    private fun sorted(list: List<Photo>) = when (sort) {
        SortOrder.Oldest -> list.sortedBy { it.takenAt }
        SortOrder.Newest -> list.sortedByDescending { it.takenAt }
        SortOrder.Largest -> list.sortedByDescending { it.size }
        SortOrder.Smallest -> list.sortedBy { it.size }
    }

    private fun setKept(id: Long, kept: Boolean) {
        keptIds = if (kept) keptIds + id else keptIds - id
        saveKept()
    }

    private fun saveKept() {
        prefs.edit().putStringSet("kept", keptIds.map { it.toString() }.toSet()).apply()
    }
}
