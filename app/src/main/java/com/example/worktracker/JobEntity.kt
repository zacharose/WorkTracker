package com.example.worktracker

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "jobs")
data class JobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val dateMillis: Long,
    val elapsedMillis: Long,
    val ticketTotal: Double,
    val cost: Double
) {
    val profit: Double get() = ticketTotal - cost
}
