package com.example.hebrew.data

import kotlinx.coroutines.flow.Flow

class CardRepository(private val dao: CardDao) {

    val allCards: Flow<List<Card>> = dao.getAllCards()

    suspend fun getLearningCards(threshold: Int): List<Card> = dao.getLearningCards(threshold)

    suspend fun getMemorizedCount(threshold: Int): Int = dao.getMemorizedCount(threshold)

    suspend fun findByHebrew(hebrew: String): Card? = dao.findByHebrew(hebrew)

    suspend fun insert(card: Card) = dao.insert(card)

    suspend fun update(card: Card) = dao.update(card)

    suspend fun delete(card: Card) = dao.delete(card)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun resetAllKnownCounts() = dao.resetAllKnownCounts()

    suspend fun updateRussianById(id: Long, russian: String) = dao.updateRussianById(id, russian)
}
