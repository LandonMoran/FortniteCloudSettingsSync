package com.fortnitecloudsync.data.model

data class CloudFile(
    val uniqueFilename: String,
    val length: Long,
    val lastModified: String?
)
