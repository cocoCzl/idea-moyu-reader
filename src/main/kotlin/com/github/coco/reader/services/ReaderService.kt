package com.github.coco.reader.services

import com.github.coco.reader.model.Bookmark
import com.github.coco.reader.model.NovelFile
import com.github.coco.reader.util.NovelParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class ReaderService(project: Project) {

    private val novelFiles = mutableListOf<NovelFile>()
    private var currentNovelFile: NovelFile? = null
    private var currentChapterIndex = 0
    private var chapters = mutableListOf<String>()
    private var currentPageIndex = 0  // 当前页索引
    private var pages = mutableListOf<String>()  // 当前章节的分页内容
    private val readingProgress = ConcurrentHashMap<String, Triple<Int, Int, Long>>() // 文件路径 -> (章节索引, 页索引, 行号)
    private val bookmarks = ConcurrentHashMap<String, MutableList<Bookmark>>() // 文件路径 -> 书签列表

    init {
        thisLogger().info("ReaderService initialized for project: ${project.name}")
    }

    /**
     * 打开小说文件
     */
    fun openNovelFile(file: File): Boolean {
        try {
            val novelFile = NovelFile(file)
            currentNovelFile = novelFile
            
            // 读取小说内容并分割章节
            val content = NovelParser.readNovelContent(novelFile)
            chapters = NovelParser.splitIntoChapters(content).toMutableList()
            
            // 恢复阅读进度
            val filePath = file.absolutePath
            val savedProgress = readingProgress[filePath]
            if (savedProgress != null) {
                currentChapterIndex = savedProgress.first
                currentPageIndex = savedProgress.second
                // 这里可以进一步处理行号信息
            } else {
                currentChapterIndex = 0
                currentPageIndex = 0
            }
            
            // 对当前章节进行分页
            if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
                pages = splitChapterIntoPages(chapters[currentChapterIndex])
            }
            
            novelFiles.add(novelFile)
            return true
        } catch (e: Exception) {
            thisLogger().error("Failed to open novel file: ${e.message}")
            return false
        }
    }

    /**
     * 获取当前页面内容
     */
    fun getCurrentPage(): String? {
        return if (pages.isNotEmpty() && currentPageIndex >= 0 && currentPageIndex < pages.size) {
            val content = pages[currentPageIndex]
            // 转换为HTML格式以便在JEditorPane中显示
            convertToHtml(content)
        } else {
            null
        }
    }

    /**
     * 获取当前章节内容
     */
    fun getCurrentChapter(): String? {
        return if (chapters.isNotEmpty() && currentChapterIndex >= 0 && currentChapterIndex < chapters.size) {
            val content = chapters[currentChapterIndex]
            // 转换为HTML格式以便在JEditorPane中显示
            convertToHtml(content)
        } else {
            null
        }
    }

    /**
     * 获取下一章节内容
     */
    fun nextChapter(): String? {
        if (currentChapterIndex < chapters.size - 1) {
            currentChapterIndex++
            currentPageIndex = 0  // 重置到章节第一页
            // 对新章节进行分页
            if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
                pages = splitChapterIntoPages(chapters[currentChapterIndex])
            }
            saveReadingProgress()
            return getCurrentPage()
        }
        return null
    }

    /**
     * 获取上一章节内容
     */
    fun previousChapter(): String? {
        if (currentChapterIndex > 0) {
            currentChapterIndex--
            currentPageIndex = 0  // 重置到章节第一页
            // 对新章节进行分页
            if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
                pages = splitChapterIntoPages(chapters[currentChapterIndex])
            }
            saveReadingProgress()
            return getCurrentPage()
        }
        return null
    }

    /**
     * 获取下一页内容
     */
    fun nextPage(): String? {
        // 如果当前页不是最后一页，直接跳转到下一页
        if (currentPageIndex < pages.size - 1) {
            currentPageIndex++
            saveReadingProgress()
            return getCurrentPage()
        }
        // 如果当前页是最后一页，跳转到下一章
        else if (currentChapterIndex < chapters.size - 1) {
            return nextChapter()
        }
        return null
    }

    /**
     * 获取上一页内容
     */
    fun previousPage(): String? {
        // 如果当前页不是第一页，直接跳转到上一页
        if (currentPageIndex > 0) {
            currentPageIndex--
            saveReadingProgress()
            return getCurrentPage()
        }
        // 如果当前页是第一页，跳转到上一章的最后一页
        else if (currentChapterIndex > 0) {
            currentChapterIndex--
            // 对新章节进行分页
            if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
                pages = splitChapterIntoPages(chapters[currentChapterIndex])
                currentPageIndex = pages.size - 1  // 跳转到新章节的最后一页
            }
            saveReadingProgress()
            return getCurrentPage()
        }
        return null
    }

    /**
     * 获取指定页面内容
     */
    fun goToPage(pageIndex: Int): String? {
        if (pageIndex >= 0 && pageIndex < pages.size) {
            currentPageIndex = pageIndex
            saveReadingProgress()
            return getCurrentPage()
        }
        return null
    }

    /**
     * 获取指定章节内容并跳转到该章节的第一页
     */
    fun goToChapter(index: Int): String? {
        if (index >= 0 && index < chapters.size) {
            currentChapterIndex = index
            currentPageIndex = 0  // 重置到章节第一页
            // 对新章节进行分页
            if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
                pages = splitChapterIntoPages(chapters[currentChapterIndex])
            }
            saveReadingProgress()
            return getCurrentPage()
        }
        return null
    }

    /**
     * 保存阅读进度（按文件+章节索引+页索引）
     */
    private fun saveReadingProgress() {
        currentNovelFile?.file?.absolutePath?.let { filePath ->
            // 保存章节索引、页索引和行号
            readingProgress[filePath] = Triple(currentChapterIndex, currentPageIndex, 0L) // 简化的行号处理
        }
    }

    /**
     * 跳转到指定的阅读位置
     */
    fun goToPosition(chapterIndex: Int, lineNumber: Long): Boolean {
        if (chapterIndex >= 0 && chapterIndex < chapters.size) {
            currentChapterIndex = chapterIndex
            currentPageIndex = 0  // 跳转到章节第一页
            // 对目标章节进行分页
            if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
                pages = splitChapterIntoPages(chapters[currentChapterIndex])
            }
            saveReadingProgress()
            return true
        }
        return false
    }

    /**
     * 获取当前阅读进度
     */
    fun getCurrentProgress(): Triple<Int, Int, Long>? {
        return currentNovelFile?.file?.absolutePath?.let { filePath ->
            readingProgress[filePath]
        }
    }

    /**
     * 将章节内容分割为页面
     */
    private fun splitChapterIntoPages(content: String): MutableList<String> {
        val pages = mutableListOf<String>()
        val lines = content.lines()
        
        // 页面大小（可配置）
        val linesPerPage = 50 // 每页显示的行数
        
        for (i in lines.indices step linesPerPage) {
            val endIndex = minOf(i + linesPerPage, lines.size)
            val pageContent = lines.subList(i, endIndex).joinToString("\n")
            pages.add(pageContent)
        }
        
        return pages
    }

    /**
     * 添加书签
     */
    fun addBookmark(title: String): Boolean {
        currentNovelFile?.file?.absolutePath?.let { filePath ->
            val bookmark = Bookmark(
                novelFilePath = filePath,
                chapterIndex = currentChapterIndex,
                pageIndex = currentPageIndex, // 使用当前页索引
                title = title
            )
            
            val fileBookmarks = bookmarks.getOrPut(filePath) { mutableListOf() }
            fileBookmarks.add(bookmark)
            return true
        }
        return false
    }

    /**
     * 删除书签
     */
    fun removeBookmark(bookmark: Bookmark): Boolean {
        val fileBookmarks = bookmarks[bookmark.novelFilePath]
        return fileBookmarks?.remove(bookmark) ?: false
    }

    /**
     * 获取指定文件的所有书签
     */
    fun getBookmarks(novelFilePath: String): List<Bookmark> {
        return bookmarks[novelFilePath]?.toList() ?: emptyList()
    }

    /**
     * 获取当前文件的所有书签
     */
    fun getCurrentBookmarks(): List<Bookmark> {
        return currentNovelFile?.file?.absolutePath?.let { filePath ->
            getBookmarks(filePath)
        } ?: emptyList()
    }

    /**
     * 跳转到书签位置
     */
    fun goToBookmark(bookmark: Bookmark): Boolean {
        if (bookmark.novelFilePath == currentNovelFile?.file?.absolutePath) {
            // 跳转到指定章节
            if (bookmark.chapterIndex >= 0 && bookmark.chapterIndex < chapters.size) {
                currentChapterIndex = bookmark.chapterIndex
                // 对目标章节进行分页
                if (chapters.isNotEmpty() && currentChapterIndex < chapters.size) {
                    pages = splitChapterIntoPages(chapters[currentChapterIndex])
                }
                // 跳转到指定页码
                if (bookmark.pageIndex >= 0 && bookmark.pageIndex < pages.size) {
                    currentPageIndex = bookmark.pageIndex
                } else {
                    currentPageIndex = 0
                }
                saveReadingProgress()
                return true
            }
        }
        return false
    }

    /**
     * 获取总章节数
     */
    fun getTotalChapters(): Int = chapters.size

    /**
     * 获取当前章节索引
     */
    fun getCurrentChapterIndex(): Int = currentChapterIndex

    /**
     * 获取当前页索引
     */
    fun getCurrentPageIndex(): Int = currentPageIndex

    /**
     * 获取当前章节总页数
     */
    fun getCurrentPageCount(): Int = pages.size

    /**
     * 获取当前小说文件
     */
    fun getCurrentNovelFile(): NovelFile? = currentNovelFile

    /**
     * 搜索功能
     */
    fun searchInNovel(query: String): List<Pair<Int, Int>> { // 返回章节索引和行号
        val results = mutableListOf<Pair<Int, Int>>()
        for (i in chapters.indices) {
            val lines = chapters[i].lines()
            for (j in lines.indices) {
                if (lines[j].contains(query, ignoreCase = true)) {
                    results.add(Pair(i, j)) // (章节索引, 行号)
                }
            }
        }
        return results
    }

    /**
     * 获取所有章节标题
     */
    fun getChapterTitles(): List<String> {
        return chapters.mapIndexed { index, chapter ->
            // 提取章节标题
            val lines = chapter.lines()
            for (line in lines) {
                val trimmed = line.trim()
                // 支持多种章节标题格式
                if (trimmed.matches(Regex("""第\s*[一二三四五六七八九十百千万\d]+\s*[章节回卷集]""")) ||
                    trimmed.matches(Regex("""第\s*\d+\s*[章节回卷集]""")) ||
                    trimmed.matches(Regex("""Chapter\s*\d+""", RegexOption.IGNORE_CASE)) ||
                    trimmed.matches(Regex("""\d+\s*[章节回卷集]""")) ||
                    trimmed.matches(Regex("""[章节回卷集]\s*[一二三四五六七八九十百千万\d]+""")) ||
                    trimmed.matches(Regex("""[章节回卷集]\s*\d+"""))) {
                    return@mapIndexed trimmed
                }
            }
            "第${index + 1}章"
        }
    }

    /**
     * 转换文本内容为HTML格式
     */
    private fun convertToHtml(content: String): String {
        val settings = com.intellij.openapi.application.ApplicationManager.getApplication()
            .getService(com.github.coco.reader.settings.ReaderSettings::class.java)
            
        // 尝试获取IDEA主题的背景色和前景色
        val ideaBackgroundColor = try {
            val bgColor = javax.swing.UIManager.getColor("Panel.background")
                ?: javax.swing.UIManager.getColor("TextArea.background")
            bgColor?.let {
                "#%02x%02x%02x".format(it.red, it.green, it.blue)
            } ?: if (settings.nightMode) "#282c34" else "#ffffff"
        } catch (e: Exception) {
            if (settings.nightMode) "#282c34" else "#ffffff"
        }
        
        val ideaForegroundColor = try {
            val fgColor = javax.swing.UIManager.getColor("Panel.foreground")
                ?: javax.swing.UIManager.getColor("TextArea.foreground")
            fgColor?.let {
                "#%02x%02x%02x".format(it.red, it.green, it.blue)
            } ?: if (settings.nightMode) "#dcdcdc" else "#000000"
        } catch (e: Exception) {
            if (settings.nightMode) "#dcdcdc" else "#000000"
        }
        
        val htmlContent = content
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\n", "<br>")
            .replace("\t", "&nbsp;&nbsp;&nbsp;&nbsp;")
            .replace(Regex("""(第\s*\d+\s*章|Chapter\s*\d+)""", RegexOption.IGNORE_CASE)) { match ->
                "<h2 style='color: ${settings.themeColor}; margin-top: 30px; margin-bottom: 20px; font-weight: bold;'>${match.value}</h2>"
            }
        return """
            <html>
            <head>
                <style>
                    body { 
                        margin: ${settings.pageMargin}px; 
                        line-height: ${settings.lineSpacing};
                        background-color: ${ideaBackgroundColor};
                        color: ${ideaForegroundColor};
                    }
                    h2 { 
                        color: ${settings.themeColor}; 
                        margin-top: 30px; 
                        margin-bottom: 20px; 
                        font-weight: bold; 
                    }
                    p { 
                        line-height: ${settings.lineSpacing}; 
                        margin: 0 0 1em 0; 
                    }
                </style>
            </head>
            <body>
                $htmlContent
            </body>
            </html>
        """.trimIndent()
    }
}
