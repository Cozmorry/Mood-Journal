package com.example.test.repository

import android.net.Uri
import java.io.File

data class CameraCapture(val uri: Uri, val filename: String)

interface PhotoStorage {
    suspend fun copyFrom(uri: Uri): String
    fun newCameraCapture(): CameraCapture
    fun delete(photoPath: String)
    fun resolve(photoPath: String): File
}
