package com.github.coco.reader.services

import com.github.coco.reader.model.Bookmark
import com.github.coco.reader.model.NovelFile
import com.github.coco.reader.util.NovelParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import java.io.File

@Service(Service.Level.PROJECT)
class ReaderService(project: Project) {

    private val persistenceService = ApplicationManager.getApplication().service<ReaderPersistenceService>()

    private val novelFiles = mutableListOf<NovelFile>()
    private var currentNovelFile: NovelFile? = null
    private var currentChapterIndex = 0
    private var chapters = mutableListOf<String>()
    private var chapterTitles = mutableListOf<String>()
    private var currentPageIndex = 0
    private var pages = mutableListOf<String>()

    init {
        thisLogger().info("ReaderService initialized for project: ${project.name}")
    }

    /**
     * 打开小说文件
     */
    fun openNovelFile(file: File): Boolean {
        return try {
            val novelFile = NovelFile(file)
            currentNovelFile = novelFile

            // 读取小说内容并分割章节
            val content = NovelParser.readNovelContent(novelFile)
            chapters = NovelParser.splitIntoChapters(content).toMutableList()
            chapterTitles = getChapterTitlesInternal().toMutableList()

            // 恢复阅读进度
            val savedProgress = persistenceService.getProgress(file.absolutePath)
            currentChapterIndex = savedProgress?.chapterIndex ?: 0
            currentPageIndex = savedProgress?.pageIndex ?: 0

            // 确保索引有效
            if (currentChapterIndex >= chapters.size) {
                currentChapterIndex = 0
                currentPageIndex = 0
            }

            // 对当前章节进行分页
            updatePagesForCurrentChapter()

            novelFiles.add(novelFile)
            true
        } catch (e: Exception) {
            thisLogger().error("Failed to open novel file: ${e.message}")
            false
        }
    }

    /**
     * 更新当前章节的分页
     */
    private fun updatePagesForCurrentChapter() {
        if (chapters.isNotEmpty() && currentChapterIndex in chapters.indices) {
            pages = splitChapterIntoPages(chapters[currentChapterIndex])
            // 确保页索引有效
            if (currentPageIndex >= pages.size) {
                currentPageIndex = maxOf(0, pages.size - 1)
            }
        } else {
            pages = mutableListOf()
            currentPageIndex = 0
        }
    }

    /**
     * 跳转到指定章节并更新分页
     */
    private fun navigateToChapter(index: Int): Boolean {
        if (index in chapters.indices) {
            currentChapterIndex = index
            currentPageIndex = 0
            updatePagesForCurrentChapter()
            saveReadingProgress()
            return true
        }
        return false
    }

    /**
     * 获取当前页面内容
     */
    fun getCurrentPage(): String? {
        return if (pages.isNotEmpty() && currentPageIndex in pages.indices) {
            convertToHtml(pages[currentPageIndex])
        } else {
            null
        }
    }

    /**
     * 获取下一页内容
     */
    fun nextPage(): String? {
        if (currentPageIndex < pages.size - 1) {
            currentPageIndex++
            saveReadingProgress()
            return getCurrentPage()
        } else if (currentChapterIndex < chapters.size - 1) {
            return if (navigateToChapter(currentChapterIndex + 1)) getCurrentPage() else null
        }
        return null
    }

    /**
     * 获取上一页内容
     */
    fun previousPage(): String? {
        if (currentPageIndex > 0) {
            currentPageIndex--
            saveReadingProgress()
            return getCurrentPage()
        } else if (currentChapterIndex > 0) {
            if (navigateToChapter(currentChapterIndex - 1)) {
                currentPageIndex = maxOf(0, pages.size - 1)
                saveReadingProgress()
                return getCurrentPage()
            }
        }
        return null
    }

    /**
     * 跳转到指定章节
     */
    fun goToChapter(index: Int): String? {
        return if (navigateToChapter(index)) getCurrentPage() else null
    }

    /**
     * 保存阅读进度
     */
    private fun saveReadingProgress() {
        currentNovelFile?.file?.absolutePath?.let { filePath ->
            persistenceService.saveProgress(filePath, currentChapterIndex, currentPageIndex)
        }
    }

    /**
     * 添加书签
     */
    fun addBookmark(title: String): Boolean {
        val filePath = currentNovelFile?.file?.absolutePath ?: return false
        val chapterTitle = if (currentChapterIndex in chapterTitles.indices) {
            chapterTitles[currentChapterIndex]
        } else {
            "第${currentChapterIndex + 1}章"
        }
        
        val bookmarkState = BookmarkState(
            novelFilePath = filePath,
            chapterIndex = currentChapterIndex,
            pageIndex = currentPageIndex,
            title = title,
            chapterTitle = chapterTitle
        )
        
        return persistenceService.addBookmark(bookmarkState)
    }

    /**
     * 删除书签
     */
    fun removeBookmark(bookmark: Bookmark): Boolean {
        val bookmarkState = BookmarkState(
            novelFilePath = bookmark.novelFilePath,
            chapterIndex = bookmark.chapterIndex,
            pageIndex = bookmark.pageIndex,
            title = bookmark.title,
            timestamp = bookmark.timestamp
        )
        return persistenceService.removeBookmark(bookmarkState)
    }

    /**
     * 获取指定文件的所有书签
     */
    fun getBookmarks(novelFilePath: String): List<Bookmark> {
        return persistenceService.getBookmarks(novelFilePath).map { state ->
            Bookmark(
                novelFilePath = state.novelFilePath,
                chapterIndex = state.chapterIndex,
                pageIndex = state.pageIndex,
                title = state.title,
                timestamp = state.timestamp
            )
        }
    }

    /**
     * 跳转到书签位置
     */
    fun goToBookmark(bookmark: Bookmark): Boolean {
        if (bookmark.novelFilePath != currentNovelFile?.file?.absolutePath) {
            return false
        }
        
        if (bookmark.chapterIndex in chapters.indices) {
            currentChapterIndex = bookmark.chapterIndex
            updatePagesForCurrentChapter()
            if (bookmark.pageIndex in pages.indices) {
                currentPageIndex = bookmark.pageIndex
            }
            saveReadingProgress()
            return true
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
     * 获取所有章节标题
     */
    fun getChapterTitles(): List<String> = chapterTitles.toList()

    /**
     * 内部方法：提取章节标题
     */
    private fun getChapterTitlesInternal(): List<String> {
        return chapters.mapIndexed { index, chapter ->
            val lines = chapter.lines()
            for (line in lines) {
                val trimmed = line.trim()
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
     * 将章节内容分割为页面
     */
    private fun splitChapterIntoPages(content: String): MutableList<String> {
        val pages = mutableListOf<String>()
        val lines = content.lines()
        val linesPerPage = 50

        for (i in lines.indices step linesPerPage) {
            val endIndex = minOf(i + linesPerPage, lines.size)
            val pageContent = lines.subList(i, endIndex).joinToString("\n")
            pages.add(pageContent)
        }

        return if (pages.isEmpty()) mutableListOf("") else pages
    }

    /**
     * 转换文本内容为HTML格式
     */
    private fun convertToHtml(content: String): String {
        val settings = ApplicationManager.getApplication().getService(com.github.coco.reader.settings.ReaderSettings::class.java)

        val ideaBackgroundColor = try {
            val bgColor = javax.swing.UIManager.getColor("Panel.background")
                ?: javax.swing.UIManager.getColor("TextArea.background")
            bgColor?.let { "#%02x%02x%02x".format(it.red, it.green, it.blue) }
                ?: if (settings.nightMode) "#282c34" else "#ffffff"
        } catch (e: Exception) {
            if (settings.nightMode) "#282c34" else "#ffffff"
        }

        val ideaForegroundColor = try {
            val fgColor = javax.swing.UIManager.getColor("Panel.foreground")
                ?: javax.swing.UIManager.getColor("TextArea.foreground")
            fgColor?.let { "#%02x%02x%02x".format(it.red, it.green, it.blue) }
                ?: if (settings.nightMode) "#dcdcdc" else "#333333"
        } catch (e: Exception) {
            if (settings.nightMode) "#dcdcdc" else "#333333"
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
                        font-family: ${settings.fontFamily}, sans-serif;
                        font-size: ${settings.fontSize}px;
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