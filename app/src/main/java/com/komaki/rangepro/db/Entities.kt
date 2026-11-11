package com.komaki.rangepro.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "rides")
data class RideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long,
    val distanceM: Double,
    val movingMs: Long,
    val maxSpeedMps: Float,
    val startSoc: Float?,
    val endSoc: Float?,
    val passenger: Boolean,
    /** True if recorded while demo mode was on. Never used for range prediction. */
    val isDemo: Boolean,
)

@Entity(tableName = "ride_points", indices = [Index("rideId")])
data class RidePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rideId: Long,
    val tMs: Long,
    val lat: Double,
    val lon: Double,
    val speedMps: Float,
)

@Entity(tableName = "charge_sessions")
data class ChargeSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startMs: Long,
    val endMs: Long? = null,
    val startSoc: Float? = null,
    val endSoc: Float? = null,
    val maxCurrentA: Float? = null,
    val energyWh: Float? = null,
)
