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

enum class Screen { Months, Swipe, Review }
enum class Choice { Keep, Delete }

class SweepViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PhotoRepository(app)
    private val prefs = app.getSharedPreferences("sweep", Context.MODE_PRIVATE)

    var hasPermission by mutableStateOf(false); private set
    var loading by mutableStateOf(false); private set
    var months by mutableStateOf<List<MonthGroup>>(emptyList()); private set
    var screen by mutableStateOf(Screen.Months); private set

    var currentMonth by mutableStateOf<MonthGroup?>(null); private set
    var queue by mutableStateOf<List<Photo>>(emptyList()); private set
    var index by mutableIntStateOf(0); private set
    var lastFreedBytes by mutableLongStateOf(0L); private set

    /** Photos marked for deletion, waiting for the user to confirm on the review screen. */
    val toDelete = mutableStateListOf<Photo>()
    private val history = mutableStateListOf<Pair<Photo, Choice>>()

    /** Photos the user already chose to keep, remembered between sessions so they don't come back. */
    private var keptIds by mutableStateOf(
        prefs.getStringSet("kept", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    )

    val current: Photo? get() = queue.getOrNull(index)
    val next: Photo? get() = queue.getOrNull(index + 1)
    val canUndo: Boolean get() = history.isNotEmpty()

    fun onPermissionChecked() {
        hasPermission = PhotoPermissions.granted(getApplication())
        if (hasPermission) load()
    }

    fun load() {
        viewModelScope.launch {
            loading = months.isEmpty()
            months = withContext(Dispatchers.IO) { repo.loadMonths() }
            loading = false
        }
    }

    fun remainingIn(month: MonthGroup) = month.photos.count { it.id !in keptIds }

    fun openMonth(month: MonthGroup) {
        currentMonth = month
        // If the whole month was already reviewed, let the user go through it again.
        queue = month.photos.filter { it.id !in keptIds }.ifEmpty { month.photos }
        index = 0
        history.clear()
        toDelete.clear()
        screen = Screen.Swipe
    }

    fun decide(choice: Choice) {
        val photo = current ?: return
        history.add(photo to choice)
        if (choice == Choice.Keep) setKept(photo.id, true) else toDelete.add(photo)
        index++
        if (index >= queue.size) screen = Screen.Review
    }

    fun undo() {
        if (history.isEmpty()) return
        val (photo, choice) = history.removeAt(history.lastIndex)
        if (choice == Choice.Keep) setKept(photo.id, false) else toDelete.remove(photo)
        index = queue.indexOf(photo).coerceAtLeast(0)
        screen = Screen.Swipe
    }

    /** Take a photo off the delete list from the review screen. */
    fun restore(photo: Photo) {
        toDelete.remove(photo)
        setKept(photo.id, true)
        val i = history.indexOfFirst { it.first.id == photo.id }
        if (i >= 0) history[i] = photo to Choice.Keep
    }

    fun openReview() { screen = Screen.Review }

    fun goBack() {
        when (screen) {
            Screen.Swipe -> if (toDelete.isNotEmpty()) screen = Screen.Review else leave()
            Screen.Review -> if (current != null) screen = Screen.Swipe else leave()
            Screen.Months -> Unit
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
        toDelete.clear()
        history.clear() // deleted photos can't be undone from here
        screen = if (current != null) Screen.Swipe else Screen.Months
        load()
    }

    private fun setKept(id: Long, kept: Boolean) {
        keptIds = if (kept) keptIds + id else keptIds - id
        prefs.edit().putStringSet("kept", keptIds.map { it.toString() }.toSet()).apply()
    }
}
