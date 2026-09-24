package com.example.test.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val dbName = "migration-test.db"
    private var database: AppDatabase? = null

    @After
    fun cleanUp() {
        database?.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate1To2_preservesExistingRowsAndAddsNullablePhotoPath() = runBlocking {
        context.deleteDatabase(dbName)
        val v1Database = SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(dbName), null)
        v1Database.execSQL(
            "CREATE TABLE journal_entries (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, text TEXT NOT NULL, " +
                "mood TEXT NOT NULL, intensity INTEGER NOT NULL)",
        )
        v1Database.execSQL(
            "INSERT INTO journal_entries (createdAt, updatedAt, text, mood, intensity) " +
                "VALUES (1, 1, 'Pre-migration entry', 'OKAY', 3)",
        )
        v1Database.version = 1
        v1Database.close()

        val migrated = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_1_2)
            .build()
        database = migrated

        val entries = migrated.journalEntryDao().getAll().first()

        assertEquals(1, entries.size)
        assertEquals("Pre-migration entry", entries[0].text)
        assertEquals(Mood.OKAY, entries[0].mood)
        assertNull(entries[0].photoPath)
    }
}
