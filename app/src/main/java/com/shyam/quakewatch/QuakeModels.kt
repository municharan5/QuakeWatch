package com.shyam.quakewatch

/**
 * USGS GeoJSON Response Data Models
 */
data class QuakeResponse(
    val type: String?,
    val features: List<QuakeFeature>?
)

data class QuakeFeature(
    val id: String?,
    val properties: QuakeProperties?,
    val geometry: QuakeGeometry?
)

data class QuakeProperties(
    val mag: Double?,
    val place: String?,
    val time: Long?
)

data class QuakeGeometry(
    val type: String?,
    // GeoJSON coordinates array format: [longitude, latitude, depth]
    // Index 0: Longitude, Index 1: Latitude, Index 2: Depth
    val coordinates: List<Double>?
)

/**
 * Domain UI Model representing a parsed and distance-calculated earthquake item.
 */
data class Earthquake(
    val id: String,
    val magnitude: Double,
    val place: String,
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    val distanceKm: Float? = null,
    val isNearby: Boolean = false
)
