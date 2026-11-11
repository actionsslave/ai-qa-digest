package app.aiqadigest

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UiState(
    val items: List<DigestItem> = emptyList(),
    /** ids that arrived since the user last left the app */
    val newIds: Set<String> = emptySet(),
    val lastUpdatedMillis: Long = 0L,
    val error: String? = null,
    val loading: Boolean = false,
)

class DigestViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _themeId = MutableStateFlow(repo.themeId())
    val themeId: StateFlow<String> = _themeId.asStateFlow()

    fun setTheme(id: String) {
        repo.setThemeId(id)
        _themeId.value = id
    }

    /** Ids seen up to the last time the app was left. */
    private var seenAtStart: Set<String> = emptySet()

    /** Called when the app comes to the foreground. */
    fun onStart() {
        seenAtStart = repo.seenIds()
        val cached = repo.loadCached()
        publish(cached?.items ?: emptyList(), loading = true)
        viewModelScope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { repo.refresh() }
                publish(fresh.items, loading = false)
            } catch (_: Exception) {
                publish(cached?.items ?: emptyList(), loading = false)
            }
        }
    }

    /** Called when the app is left: everything shown counts as seen from now on. */
    fun onStop() {
        repo.markSeen(_state.value.items.map { it.id })
    }

    private fun publish(items: List<DigestItem>, loading: Boolean) {
        _state.value = UiState(
            items = items.sortedByDescending { it.addedAt },
            newIds = items.map { it.id }.filter { it !in seenAtStart }.toSet(),
            lastUpdatedMillis = repo.lastUpdated(),
            error = if (loading) null else repo.lastError(),
            loading = loading,
        )
    }
}
