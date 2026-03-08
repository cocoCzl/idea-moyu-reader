package com.github.coco.reader.services

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * 阅读器数据持久化服务
 * 负责保存和恢复书签、阅读进度等数据
 */
@State(
    name = "ReaderPersistenceService",
    storages = [Storage("reader-persistence.xml")]
)
@Service(Service.Level.APP)
class ReaderPersistenceService : PersistentStateComponent<ReaderPersistenceService> {

    // 阅读进度：文件路径 -> (章节索引, 页索引)
    var readingProgress: MutableMap<String, ProgressState> = ConcurrentHashMap()

    // 书签列表：文件路径 -> 书签列表
    var bookmarks: MutableMap<String, MutableList<BookmarkState>> = ConcurrentHashMap()

    // 最近打开的文件列表
    var recentFiles: MutableList<String> = mutableListOf()

    override fun getState(): ReaderPersistenceService = this

    override fun loadState(state: ReaderPersistenceService) {
        XmlSerializerUtil.copyBean(state, this)
    }

    /**
     * 保存阅读进度
     */
    fun saveProgress(filePath: String, chapterIndex: Int, pageIndex: Int) {
        readingProgress[filePath] = ProgressState(chapterIndex, pageIndex)
        // 更新最近文件列表
        recentFiles.remove(filePath)
        recentFiles.add(0, filePath)
        // 最多保留 20 个最近文件
        if (recentFiles.size > 20) {
            recentFiles = recentFiles.take(20).toMutableList()
        }
    }

    /**
     * 获取阅读进度
     */
    fun getProgress(filePath: String): ProgressState? = readingProgress[filePath]

    /**
     * 添加书签
     */
    fun addBookmark(bookmark: BookmarkState): Boolean {
        val fileBookmarks = bookmarks.getOrPut(bookmark.novelFilePath) { mutableListOf() }
        // 检查是否已存在相同位置的书签
        val exists = fileBookmarks.any { 
            it.chapterIndex == bookmark.chapterIndex && it.pageIndex == bookmark.pageIndex 
        }
        if (exists) return false
        fileBookmarks.add(bookmark)
        return true
    }

    /**
     * 删除书签
     */
    fun removeBookmark(bookmark: BookmarkState): Boolean {
        return bookmarks[bookmark.novelFilePath]?.remove(bookmark) ?: false
    }

    /**
     * 获取指定文件的书签列表
     */
    fun getBookmarks(filePath: String): List<BookmarkState> {
        return bookmarks[filePath]?.sortedByDescending { it.timestamp } ?: emptyList()
    }

    /**
     * 获取所有书签
     */
    fun getAllBookmarks(): Map<String, List<BookmarkState>> = bookmarks.toMap()

    /**
     * 清除指定文件的所有数据
     */
    fun clearFileData(filePath: String) {
        readingProgress.remove(filePath)
        bookmarks.remove(filePath)
        recentFiles.remove(filePath)
    }

    /**
     * 获取最近打开的文件
     */
    fun getRecentFilesList(): List<String> = recentFiles.toList()
}

/**
 * 阅读进度状态
 */
data class ProgressState(
    var chapterIndex: Int = 0,
    var pageIndex: Int = 0
)

/**
 * 书签状态
 */
data class BookmarkState(
    var novelFilePath: String = "",
    var chapterIndex: Int = 0,
    var pageIndex: Int = 0,
    var title: String = "",
    var timestamp: Long = System.currentTimeMillis(),
    var chapterTitle: String = ""  // 添加章节标题，方便显示
)
