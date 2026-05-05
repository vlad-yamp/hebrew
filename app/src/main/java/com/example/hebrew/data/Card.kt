package com.example.hebrew.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val hebrew: String,
    val russian: String,
    val knownCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
