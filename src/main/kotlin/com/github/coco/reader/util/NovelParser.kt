package com.github.coco.reader.util

import com.github.coco.reader.model.NovelFile
import com.github.coco.reader.model.NovelType
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import nl.siegmann.epublib.domain.Book
import nl.siegmann.epublib.epub.EpubReader
import java.io.FileInputStream
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

/**
 * 小说文件解析工具类
 */
object NovelParser {
    
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
     * 读取TXT文件内容
     */
    private fun readTxtFile(file: File): String {
        return try {
            // 尝试不同的编码格式
            val encodings = listOf("UTF-8", "GBK", "GB2312")
            for (encoding in encodings) {
                try {
                    return Files.readString(file.toPath(), Charset.forName(encoding))
                } catch (e: Exception) {
                    continue
                }
            }
            // 如果都失败了，使用默认编码
            Files.readString(file.toPath())
        } catch (e: Exception) {
            "无法读取文件: ${e.message}"
        }
    }
    
    /**
     * 读取EPUB文件内容
     */
    private fun readEpubFile(file: File): String {
        return try {
            val epubReader = EpubReader()
            val book: Book = FileInputStream(file).use { epubReader.readEpub(it) }
            
            // 获取书名
            val title = book.title ?: file.nameWithoutExtension
            
            // 获取所有章节内容
            val contentBuilder = StringBuilder()
            contentBuilder.append("《$title》\n\n")
            
            // 添加作者信息
            if (book.metadata.authors.isNotEmpty()) {
                val authors = book.metadata.authors.joinToString(", ") { it.firstname + " " + it.lastname }
                contentBuilder.append("作者: $authors\n\n")
            }
            
            // 添加所有章节内容
            for (section in book.contents) {
                val content = String(section.data, Charsets.UTF_8)
                // 简单清理HTML标签
                val cleanContent = cleanHtmlContent(content)
                contentBuilder.append(cleanContent).append("\n\n")
            }
            
            contentBuilder.toString()
        } catch (e: Exception) {
            "无法解析EPUB文件: ${e.message}\n文件路径: ${file.absolutePath}"
        }
    }
    
    /**
     * 读取MOBI/AZW/AZW3文件内容（简化实现，因为这些格式比较复杂）
     */
    private fun readMobiFile(file: File): String {
        return "MOBI/AZW/AZW3格式支持待完善\n" +
               "文件路径: ${file.absolutePath}\n" +
               "文件大小: ${file.length()} 字节\n\n" +
               "提示：由于MOBI/AZW/AZW3格式的复杂性，需要专门的解析库支持，" +
               "当前版本暂时提供基础支持。"
    }
    
    /**
     * 读取PDF文件内容
     */
    private fun readPdfFile(file: File): String {
        return try {
            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                stripper.setStartPage(1)
                stripper.setEndPage(document.numberOfPages)
                val content = stripper.getText(document)
                
                // 简单清理PDF文本
                val cleanContent = content
                    .replace(Regex("[\n\r]{3,}"), "\n\n")  // 多个换行符替换为两个
                    .replace(Regex("[ \t]+"), " ")  // 多个空格或制表符替换为单个空格
                    .trim()
                
                "《${file.nameWithoutExtension}》\n\n$content"
            }
        } catch (e: Exception) {
            "无法解析PDF文件: ${e.message}\n文件路径: ${file.absolutePath}"
        }
    }
    
    /**
     * 简单清理HTML内容，移除标签
     */
    private fun cleanHtmlContent(html: String): String {
        return html
            .replace(Regex("<[^>]*>"), "")  // 移除HTML标签
            .replace("&nbsp;", " ")  // 替换特殊字符
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .trim()
    }
    
    /**
     * 按章节分割小说内容
     */
    fun splitIntoChapters(content: String): List<String> {
        // 更智能的章节分割逻辑，支持多种章节标题格式
        val chapterPatterns = listOf(
            """第\s*([一二三四五六七八九十百千万\d]+)\s*[章节回卷集]""",  // 中文数字章节
            """第\s*(\d+)\s*[章节回卷集]""",  // 阿拉伯数字章节
            """Chapter\s*(\d+)""",  // 英文数字章节
            """(\d+)\s*[章节回卷集]""",  // 纯数字章节
            """[章节回卷集]\s*([一二三四五六七八九十百千万\d]+)""",  // 反向中文数字章节
            """[章节回卷集]\s*(\d+)"""  // 反向阿拉伯数字章节
        )
        
        // 合并所有模式
        val combinedPattern = chapterPatterns.joinToString("|") { "($it)" }
        val chapterRegex = Regex(combinedPattern, setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE))
        val matches = chapterRegex.findAll(content).toList()
        
        if (matches.isEmpty()) {
            return listOf(content)
        }
        
        val chapters = mutableListOf<String>()
        var lastIndex = 0
        
        for (match in matches) {
            // 添加前一章节的内容
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
     * 获取文件编码格式
     */
    fun detectEncoding(file: File): String {
        return try {
            val bytes = file.readBytes()
            when {
                isUtf8(bytes) -> "UTF-8"
                isGbk(bytes) -> "GBK"
                else -> "UTF-8" // 默认返回UTF-8
            }
        } catch (e: Exception) {
            "UTF-8"
        }
    }
    
    private fun isUtf8(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val utf8Byte = bytes[i].toInt() and 0xFF
            when {
                utf8Byte and 0x80 == 0 -> i++ // ASCII
                utf8Byte and 0xE0 == 0xC0 -> i += 2 // 2-byte UTF-8
                utf8Byte and 0xF0 == 0xE0 -> i += 3 // 3-byte UTF-8
                utf8Byte and 0xF8 == 0xF0 -> i += 4 // 4-byte UTF-8
                else -> return false
            }
        }
        return true
    }
    
    private fun isGbk(bytes: ByteArray): Boolean {
        // 简单的GBK检测逻辑
        for (i in bytes.indices) {
            val byte = bytes[i].toInt() and 0xFF
            if (byte >= 0x81 && byte <= 0xFE) {
                if (i + 1 < bytes.size) {
                    val nextByte = bytes[i + 1].toInt() and 0xFF
                    if ((nextByte >= 0x40 && nextByte <= 0x7E) || (nextByte >= 0x80 && nextByte <= 0xFE)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}