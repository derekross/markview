package com.derekross.markview.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

internal val Context.markviewDataStore: DataStore<Preferences> by preferencesDataStore(name = "markview")
