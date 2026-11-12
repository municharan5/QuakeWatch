package com.shyam.quakewatch

import android.annotation.SuppressLint
import android.app.Application
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * UI State for the QuakeWatch screen.
 */
data class QuakeUiState(
    val quakes: List<Earthquake> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val userLocation: Location? = null,
    val radiusKm: Float = 1000f,
    val hasLocationPermission: Boolean = false,
    val locationMessage: String? = null
)

/**
 * ViewModel responsible for managing location, fetching USGS earthquake data,
 * calculating distances, and filtering nearby quakes.
 */
class QuakeViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(QuakeUiState())
    val uiState: StateFlow<QuakeUiState> = _uiState.asStateFlow()

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(application)
    private var rawFeatures: List<QuakeFeature> = emptyList()

    init {
        checkLocationPermissionAndFetch()
    }

    /**
     * Permission flow check: checks runtime location permission status and initiates location & data fetch.
     */
    fun checkLocationPermissionAndFetch() {
        val context = getApplication<Application>().applicationContext
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val hasPermission = fineGranted || coarseGranted
        _uiState.update { it.copy(hasLocationPermission = hasPermission) }

        refreshData(hasPermission = hasPermission)
    }

    fun onPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasLocationPermission = granted,
                locationMessage = if (!granted) "Location permission denied. Distances cannot be calculated." else null
            )
        }
        refreshData(hasPermission = granted)
    }

    /**
     * Refreshes both user location (if permission granted) and earthquake list.
     */
    fun refreshData(hasPermission: Boolean = _uiState.value.hasLocationPermission) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            // 1. Attempt location retrieval if permitted
            var location: Location? = null
            if (hasPermission) {
                location = fetchUserLocation()
            } else {
                _uiState.update {
                    it.copy(locationMessage = "Permission denied. Highlight & distance sorting disabled.")
                }
            }

            _uiState.update { it.copy(userLocation = location) }

            // 2. Fetch GeoJSON earthquake data from USGS API
            try {
                val response = RetrofitInstance.api.getEarthquakes()
                rawFeatures = response.features ?: emptyList()

                val processed = processQuakes(
                    features = rawFeatures,
                    location = location,
                    radius = _uiState.value.radiusKm
                )

                _uiState.update {
                    it.copy(
                        quakes = processed,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = e.localizedMessage ?: "Failed to fetch earthquake data."
                    )
                }
            }
        }
    }

    /**
     * Retrieves current location using FusedLocationProviderClient with lastLocation fallback.
     */
    @SuppressLint("MissingPermission")
    private suspend fun fetchUserLocation(): Location? {
        return try {
            val currentLocation = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                CancellationTokenSource().token
            ).await()

            currentLocation ?: fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            _uiState.update {
                it.copy(locationMessage = "Location unavailable. Please enable GPS or set mock location.")
            }
            null
        }
    }

    /**
     * Updates radius slider state and updates highlighting & distance sorting.
     */
    fun updateRadius(newRadiusKm: Float) {
        _uiState.update { currentState ->
            val updatedList = processQuakes(
                features = rawFeatures,
                location = currentState.userLocation,
                radius = newRadiusKm
            )
            currentState.copy(
                radiusKm = newRadiusKm,
                quakes = updatedList
            )
        }
    }

    /**
     * Process raw USGS features:
     * - Order: GeoJSON coordinates are [longitude, latitude, depth] -> Index 0 is Longitude, Index 1 is Latitude
     * - Calculates distance in km using Location.distanceBetween()
     * - Highlights quakes within radius
     * - Sorts list by distance
     */
    private fun processQuakes(
        features: List<QuakeFeature>,
        location: Location?,
        radius: Float
    ): List<Earthquake> {
        val processed = features.mapNotNull { feature ->
            val id = feature.id ?: return@mapNotNull null
            val props = feature.properties ?: return@mapNotNull null
            val coords = feature.geometry?.coordinates

            // Safe parsing of null magnitude or place
            val mag = props.mag ?: 0.0
            val place = props.place ?: "Unknown location"
            val time = props.time ?: System.currentTimeMillis()

            // GeoJSON coordinates array: [longitude, latitude, depth]
            // lon = index 0, lat = index 1
            if (coords == null || coords.size < 2) return@mapNotNull null
            val lon = coords[0]
            val lat = coords[1]

            var distanceKm: Float? = null
            var isNearby = false

            if (location != null) {
                // Key part: Location.distanceBetween calculates distance in meters between two lat/lon coordinates
                val results = FloatArray(1)
                Location.distanceBetween(
                    location.latitude,
                    location.longitude,
                    lat,
                    lon,
                    results
                )
                // Convert distance from meters to kilometers
                val distMeters = results[0]
                distanceKm = distMeters / 1000f
                isNearby = distanceKm <= radius
            }

            Earthquake(
                id = id,
                magnitude = mag,
                place = place,
                time = time,
                latitude = lat,
                longitude = lon,
                distanceKm = distanceKm,
                isNearby = isNearby
            )
        }

        // Sort by distance if user location is available; fallback to date/time
        return if (location != null) {
            processed.sortedBy { it.distanceKm ?: Float.MAX_VALUE }
        } else {
            processed.sortedByDescending { it.time }
        }
    }
}
