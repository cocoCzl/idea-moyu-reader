package com.github.coco.reader.model

import java.io.File

/**
 * 小说文件数据类
 */
data class NovelFile(
    val file: File,
    val title: String = file.nameWithoutExtension,
    val type: NovelType = NovelType.fromFile(file),
    var currentPosition: Long = 0, // 当前阅读位置
    var currentChapter: Int = 0    // 当前章节索引
)

/**
 * 小说文件类型枚举
 */
enum class NovelType {
    TXT, EPUB, MOBI, AZW, AZW3, PDF, UNKNOWN;

    companion object {
        fun fromFile(file: File): NovelType {
            return when (file.extension.lowercase()) {
                "txt" -> TXT
                "epub" -> EPUB
                "mobi" -> MOBI
                "azw" -> AZW
                "azw3" -> AZW3
                "pdf" -> PDF
                else -> UNKNOWN
            }
        }
    }
}