package com.wakeme.app.model

data class SelectedBusStop(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val routes: String? = null,
    val direction: String? = null
)
