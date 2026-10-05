package com.maximerollin.aqualog.shared

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabase

const val AQUA_LOG_DATABASE_NAME = "aqualog.db"

fun createAndroidDatabaseBuilder(
    context: Context,
    databaseName: String = AQUA_LOG_DATABASE_NAME,
): RoomDatabase.Builder<AquaLogDatabase> {
    val applicationContext = context.applicationContext
    val databaseFile = applicationContext.getDatabasePath(databaseName)
    return Room.databaseBuilder<AquaLogDatabase>(
        context = applicationContext,
        name = databaseFile.absolutePath,
    )
}
