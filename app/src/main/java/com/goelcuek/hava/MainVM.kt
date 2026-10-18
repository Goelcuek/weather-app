package com.goelcuek.hava

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goelcuek.hava.data.Api
import com.goelcuek.hava.data.Place
import com.goelcuek.hava.data.Repo
import com.goelcuek.hava.data.Store
import com.goelcuek.hava.data.Weather
import com.goelcuek.hava.widget.WidgetUpdater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class UiState(
    val weather: Weather? = null,
    val place: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val unit: String = "c",
    val style: String = "dynamic",
    val useGps: Boolean = true,
    val updatedAt: Long = 0L,
)

class MainVM(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)

    private val _state = MutableStateFlow(snapshot())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val _results = MutableStateFlow<List<Place>>(emptyList())
    val results: StateFlow<List<Place>> = _results.asStateFlow()

    private val _searching = MutableStateFlow(false)
    val searching: StateFlow<Boolean> = _searching.asStateFlow()

    private var searchJob: Job? = null

    val hasLocation: Boolean get() = store.hasLocation
    val lastFetch: Long get() = store.lastFetch

    private fun snapshot(loading: Boolean = false, error: String? = null) = UiState(
        weather = Repo.cached(getApplication()),
        place = store.placeName,
        loading = loading,
        error = error,
        unit = store.unit,
        style = store.widgetStyle,
        useGps = store.useGps,
        updatedAt = store.lastFetch,
    )

    fun refresh(allowLocation: Boolean = true) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            val r = Repo.refresh(getApplication(), allowLocation)
            _state.value = snapshot(error = r.exceptionOrNull()?.let { it.message ?: it.javaClass.simpleName })
        }
    }

    fun search(q: String) {
        searchJob?.cancel()
        val query = q.trim()
        if (query.length < 2) {
            _results.value = emptyList()
            _searching.value = false
            return
        }
        searchJob = viewModelScope.launch {
            delay(350)
            _searching.value = true
            try {
                _results.value = withContext(Dispatchers.IO) {
                    runCatching { Api.search(query, Locale.getDefault().language) }.getOrDefault(emptyList())
                }
            } finally {
                _searching.value = false
            }
        }
    }

    fun choose(p: Place) {
        store.useGps = false
        store.setLocation(p.lat, p.lon, p.name)
        _results.value = emptyList()
        _state.value = _state.value.copy(place = p.name, useGps = false)
        refresh(allowLocation = false)
    }

    fun useGps() {
        store.useGps = true
        store.placeName = ""
        _state.value = _state.value.copy(useGps = true)
        refresh(allowLocation = true)
    }

    fun setUnit(u: String) {
        if (u == store.unit) return
        store.unit = u
        _state.value = _state.value.copy(unit = u)
        refresh(allowLocation = false)
    }

    fun setStyle(s: String) {
        store.widgetStyle = s
        WidgetUpdater.updateAll(getApplication())
        _state.value = _state.value.copy(style = s)
    }
}
