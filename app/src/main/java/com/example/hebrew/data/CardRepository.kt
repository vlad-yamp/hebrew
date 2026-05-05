package com.example.hebrew.data

import kotlinx.coroutines.flow.Flow

class CardRepository(private val dao: CardDao) {

    val allCards: Flow<List<Card>> = dao.getAllCards()

    suspend fun getLearningCards(): List<Card> = dao.getLearningCards()

    suspend fun insert(card: Card) = dao.insert(card)

    suspend fun update(card: Card) = dao.update(card)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun resetAllKnownCounts() = dao.resetAllKnownCounts()
}
