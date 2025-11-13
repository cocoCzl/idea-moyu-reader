package com.github.coco.reader.model

/**
 * 书签数据类
 */
data class Bookmark(
    val novelFilePath: String,
    val chapterIndex: Int,
    val pageIndex: Int, // 页码索引
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)