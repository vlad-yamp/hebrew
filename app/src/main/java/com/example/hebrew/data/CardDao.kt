package com.example.hebrew.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {

    @Query("SELECT * FROM cards ORDER BY addedAt DESC")
    fun getAllCards(): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE knownCount < :threshold ORDER BY addedAt ASC")
    suspend fun getLearningCards(threshold: Int): List<Card>

    @Query("SELECT COUNT(*) FROM cards")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(card: Card)

    @Update
    suspend fun update(card: Card)

    @Delete
    suspend fun delete(card: Card)

    @Query("SELECT * FROM cards WHERE hebrew = :hebrew LIMIT 1")
    suspend fun findByHebrew(hebrew: String): Card?

    @Query("DELETE FROM cards")
    suspend fun deleteAll()

    @Query("UPDATE cards SET knownCount = 0")
    suspend fun resetAllKnownCounts()
}
