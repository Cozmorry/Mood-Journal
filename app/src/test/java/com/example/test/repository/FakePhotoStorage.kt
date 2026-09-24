package com.example.test.repository

import android.net.Uri
import java.io.File

class FakePhotoStorage : PhotoStorage {
    val copiedUris = mutableListOf<Uri>()
    val deletedPaths = mutableListOf<String>()
    private var nextId = 1

    override suspend fun copyFrom(uri: Uri): String {
        copiedUris += uri
        return "fake_photo_${nextId++}.jpg"
    }

    override fun newCameraCapture(): CameraCapture {
        val filename = "fake_photo_${nextId++}.jpg"
        return CameraCapture(uri = Uri.parse("content://fake/$filename"), filename = filename)
    }

    override fun delete(photoPath: String) {
        deletedPaths += photoPath
    }

    override fun resolve(photoPath: String): File = File("/fake/$photoPath")
}
