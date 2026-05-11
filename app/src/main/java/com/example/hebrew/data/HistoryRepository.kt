package com.example.hebrew.data

import androidx.lifecycle.LiveData

class HistoryRepository(private val dao: HistoryDao) {

    val allEntries: LiveData<List<HistoryEntry>> = dao.getAll()

    suspend fun insert(entry: HistoryEntry) = dao.insert(entry)

    suspend fun delete(entry: HistoryEntry) = dao.delete(entry)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun trimToMax(maxCount: Int) = dao.trimToMax(maxCount)
}
