package com.example.hebrew.data

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAll(): LiveData<List<HistoryEntry>>

    @Insert
    suspend fun insert(entry: HistoryEntry)

    @Delete
    suspend fun delete(entry: HistoryEntry)

    @Query("DELETE FROM history")
    suspend fun deleteAll()

    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY timestamp DESC LIMIT :maxCount)")
    suspend fun trimToMax(maxCount: Int)

    @Query("UPDATE history SET russian = :russian WHERE id = :id")
    suspend fun updateRussianById(id: Int, russian: String)

    @Query("UPDATE history SET russian = :russian WHERE hebrew = :hebrew")
    suspend fun updateRussianByHebrew(hebrew: String, russian: String)
}
