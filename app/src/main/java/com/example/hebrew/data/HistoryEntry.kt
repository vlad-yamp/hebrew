package com.example.hebrew.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "history")
data class HistoryEntry(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val hebrew: String,
    val russian: String,
    val timestamp: Long = System.currentTimeMillis()
)
