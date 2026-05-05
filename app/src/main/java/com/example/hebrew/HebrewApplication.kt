package com.example.hebrew

import android.app.Application
import com.example.hebrew.data.AppDatabase
import com.example.hebrew.data.CardRepository

class HebrewApplication : Application() {

    val database by lazy { AppDatabase.getInstance(this) }
    val repository by lazy { CardRepository(database.cardDao()) }
}
