package com.example.test.repository

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

class FilePhotoStorage(private val context: Context) : PhotoStorage {
    private val photosDir: File
        get() = File(context.filesDir, "photos").apply { mkdirs() }

    private fun newFilename() = "photo_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg"

    override suspend fun copyFrom(uri: Uri): String {
        val filename = newFilename()
        val destination = File(photosDir, filename)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Unable to open input stream for $uri")
        input.use { stream -> destination.outputStream().use { stream.copyTo(it) } }
        return filename
    }

    override fun newCameraCapture(): CameraCapture {
        val filename = newFilename()
        val destination = File(photosDir, filename)
        destination.createNewFile()
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            destination,
        )
        return CameraCapture(uri = uri, filename = filename)
    }

    override fun delete(photoPath: String) {
        File(photosDir, photoPath).delete()
    }

    override fun resolve(photoPath: String): File = File(photosDir, photoPath)
}
