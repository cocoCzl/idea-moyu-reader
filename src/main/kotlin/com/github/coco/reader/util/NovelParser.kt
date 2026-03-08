package com.github.coco.reader.util

import com.github.coco.reader.model.NovelFile
import com.github.coco.reader.model.NovelType
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper
import java.io.File
import java.io.FileInputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

/**
 * 小说文件解析工具类
 */
object NovelParser {

    private val CHAPTER_PATTERNS = listOf(
        Regex("""第\s*[一二三四五六七八九十百千万\d]+\s*[章节回卷集]"""),
        Regex("""第\s*\d+\s*[章节回卷集]"""),
        Regex("""Chapter\s*\d+""", RegexOption.IGNORE_CASE),
        Regex("""\d+\s*[章节回卷集]"""),
        Regex("""[章节回卷集]\s*[一二三四五六七八九十百千万\d]+"""),
        Regex("""[章节回卷集]\s*\d+""")
    )

    private val COMBINED_PATTERN = CHAPTER_PATTERNS.map { "($it)" }.joinToString("|").toRegex(
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /**
     * 读取小说文件内容
     */
    fun readNovelContent(novelFile: NovelFile): String {
        return when (novelFile.type) {
            NovelType.TXT -> readTxtFile(novelFile.file)
            NovelType.EPUB -> readEpubFile(novelFile.file)
            NovelType.MOBI, NovelType.AZW, NovelType.AZW3 -> readMobiFile(novelFile.file)
            NovelType.PDF -> readPdfFile(novelFile.file)
            NovelType.UNKNOWN -> throw IllegalArgumentException("Unsupported file type: ${novelFile.file.extension}")
        }
    }

    /**
     * 读取TXT文件内容 - 优化的编码检测
     */
    private fun readTxtFile(file: File): String {
        return try {
            val bytes = file.readBytes()
            val encoding = detectEncoding(bytes)
            String(bytes, Charset.forName(encoding))
        } catch (e: Exception) {
            "无法读取文件: ${e.message}"
        }
    }

    /**
     * 检测文件编码 - 更高效的方式
     */
    private fun detectEncoding(bytes: ByteArray): String {
        // 检查 BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return "UTF-8"
        }

        // 尝试 UTF-8 解码
        if (isValidUtf8(bytes)) {
            return "UTF-8"
        }

        // 尝试 GBK
        if (isValidGbk(bytes)) {
            return "GBK"
        }

        // 默认 UTF-8
        return "UTF-8"
    }

    private fun isValidUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            when {
                b and 0x80 == 0 -> i++ // ASCII
                b and 0xE0 == 0xC0 -> if (i + 1 >= bytes.size || bytes[i + 1].toInt() and 0xC0 != 0x80) return false else i += 2
                b and 0xF0 == 0xE0 -> if (i + 2 >= bytes.size || bytes[i + 1].toInt() and 0xC0 != 0x80 || bytes[i + 2].toInt() and 0xC0 != 0x80) return false else i += 3
                b and 0xF8 == 0xF0 -> if (i + 3 >= bytes.size || bytes[i + 1].toInt() and 0xC0 != 0x80 || bytes[i + 2].toInt() and 0xC0 != 0x80 || bytes[i + 3].toInt() and 0xC0 != 0x80) return false else i += 4
                else -> return false
            }
        }
        return true
    }

    private fun isValidGbk(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val b = bytes[i].toInt() and 0xFF
            if (b in 0x81..0xFE && i + 1 < bytes.size) {
                val b2 = bytes[i + 1].toInt() and 0xFF
                if (b2 !in 0x40..0xFE || b2 == 0x7F) {
                    return false
                }
                i += 2
            } else if (b < 0x80) {
                i++
            } else {
                return false
            }
        }
        return true
    }

    /**
     * 读取EPUB文件内容
     */
    private fun readEpubFile(file: File): String {
        return try {
            FileInputStream(file).use { fis ->
                val epubReader = EpubReader()
                val book: Book = epubReader.readEpub(fis)
                val contentBuilder = StringBuilder()

                // 书名和作者
                book.title?.let { contentBuilder.append("《$it》\n\n") }
                if (book.metadata.authors.isNotEmpty()) {
                    val authors = book.metadata.authors.joinToString(", ") { 
                        "${it.firstname ?: ""} ${it.lastname ?: ""}".trim() 
                    }
                    if (authors.isNotBlank()) {
                        contentBuilder.append("作者: $authors\n\n")
                    }
                }

                // 章节内容
                book.contents.forEach { section ->
                    val content = String(section.data, StandardCharsets.UTF_8)
                    contentBuilder.append(cleanHtmlContent(content)).append("\n\n")
                }

                contentBuilder.toString()
            }
        } catch (e: Exception) {
            "无法解析EPUB文件: ${e.message}\n文件路径: ${file.absolutePath}"
        }
    }

    /**
     * 读取MOBI/AZW/AZW3文件内容
     */
    private fun readMobiFile(file: File): String {
        return buildString {
            append("MOBI/AZW/AZW3格式支持待完善\n\n")
            append("文件路径: ${file.absolutePath}\n")
            append("文件大小: ${formatFileSize(file.length())}\n\n")
            append("提示：由于MOBI/AZW/AZW3格式的复杂性，建议转换为EPUB或TXT格式获得更好体验。")
        }
    }

    /**
     * 读取PDF文件内容
     */
    private fun readPdfFile(file: File): String {
        return try {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                val content = stripper.getText(document)
                
                buildString {
                    append("《${file.nameWithoutExtension}》\n\n")
                    append(content
                        .replace(Regex("[\n\r]{3,}"), "\n\n")
                        .replace(Regex("[ \t]+"), " ")
                        .trim())
                }
            }
        } catch (e: Exception) {
            "无法解析PDF文件: ${e.message}\n文件路径: ${file.absolutePath}"
        }
    }

    /**
     * 清理HTML内容
     */
    private fun cleanHtmlContent(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * 按章节分割小说内容
     */
    fun splitIntoChapters(content: String): List<String> {
        val matches = COMBINED_PATTERN.findAll(content).toList()

        if (matches.isEmpty()) {
            return listOf(content)
        }

        val chapters = mutableListOf<String>()
        var lastIndex = 0

        for (match in matches) {
            if (lastIndex < match.range.first) {
                val chapterContent = content.substring(lastIndex, match.range.first).trim()
                if (chapterContent.isNotEmpty()) {
                    chapters.add(chapterContent)
                }
            }
            lastIndex = match.range.first
        }

        // 添加最后一章
        if (lastIndex < content.length) {
            val chapterContent = content.substring(lastIndex).trim()
            if (chapterContent.isNotEmpty()) {
                chapters.add(chapterContent)
            }
        }

        return chapters
    }

    /**
     * 格式化文件大小
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes 字节"
            bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        }
    }
}
