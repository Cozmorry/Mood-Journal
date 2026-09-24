package com.example.test.repository

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FilePhotoStorageTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val storage = FilePhotoStorage(context)

    @Test
    fun copyFrom_copiesSourceBytesIntoAStoredFile() = runBlocking {
        val source = File.createTempFile("source", ".jpg", context.cacheDir)
        source.writeBytes(byteArrayOf(1, 2, 3, 4))

        val filename = storage.copyFrom(Uri.fromFile(source))

        val stored = storage.resolve(filename)
        assertTrue(stored.exists())
        assertEquals(listOf<Byte>(1, 2, 3, 4), stored.readBytes().toList())
    }

    @Test
    fun newCameraCapture_returnsAWritableUriAndMatchingFilename() {
        val capture = storage.newCameraCapture()

        val resolved = storage.resolve(capture.filename)
        assertTrue(resolved.exists())
        context.contentResolver.openOutputStream(capture.uri)?.use { it.write(byteArrayOf(9)) }
        assertEquals(listOf<Byte>(9), resolved.readBytes().toList())
    }

    @Test
    fun delete_removesTheStoredFile() = runBlocking {
        val source = File.createTempFile("source", ".jpg", context.cacheDir)
        source.writeBytes(byteArrayOf(1))
        val filename = storage.copyFrom(Uri.fromFile(source))

        storage.delete(filename)

        assertFalse(storage.resolve(filename).exists())
    }
}
