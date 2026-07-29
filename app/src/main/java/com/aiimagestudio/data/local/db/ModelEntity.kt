package com.aiimagestudio.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "models")
data class ModelEntity(
    @PrimaryKey val component: String,
    val isInstalled: Boolean = false,
    val downloadProgress: Float = 0f,
    val isDownloading: Boolean = false,
    val isPaused: Boolean = false,
    val bytesDownloaded: Long = 0,
    val localUri: String? = null
)
