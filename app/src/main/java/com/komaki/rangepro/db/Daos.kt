package com.komaki.rangepro.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RideDao {
    @Insert suspend fun insert(ride: RideEntity): Long
    @Insert suspend fun insertPoints(points: List<RidePointEntity>)
    @Query("SELECT * FROM rides ORDER BY startMs DESC") fun observeAll(): Flow<List<RideEntity>>
    @Query("SELECT * FROM ride_points WHERE rideId = :rideId ORDER BY tMs") suspend fun points(rideId: Long): List<RidePointEntity>
    @Query("DELETE FROM ride_points WHERE rideId = :rideId") suspend fun deletePoints(rideId: Long)
    @Query("DELETE FROM rides WHERE id = :rideId") suspend fun deleteRide(rideId: Long)
}

@Dao
interface ChargeDao {
    @Insert suspend fun insert(s: ChargeSessionEntity): Long
    @Update suspend fun update(s: ChargeSessionEntity)
    @Query("SELECT * FROM charge_sessions ORDER BY startMs DESC") fun observeAll(): Flow<List<ChargeSessionEntity>>
    @Query("DELETE FROM charge_sessions WHERE id = :id") suspend fun delete(id: Long)
}
