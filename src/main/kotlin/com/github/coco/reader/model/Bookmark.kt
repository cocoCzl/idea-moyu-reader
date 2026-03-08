package com.github.coco.reader.model

/**
 * 书签数据类（用于运行时传递）
 */
data class Bookmark(
    val novelFilePath: String,
    val chapterIndex: Int,
    val pageIndex: Int,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)
