package com.maximerollin.aqualog.shared

import androidx.room3.Room
import androidx.room3.RoomDatabase

fun createJvmDatabaseBuilder(
    databasePath: String,
): RoomDatabase.Builder<AquaLogDatabase> = Room.databaseBuilder<AquaLogDatabase>(
    name = databasePath,
)
