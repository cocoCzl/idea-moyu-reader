package com.github.coco.reader

import com.intellij.openapi.components.service
import com.intellij.testFramework.TestDataPath
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.github.coco.reader.services.ReaderService
import com.github.coco.reader.model.NovelFile
import java.io.File

@TestDataPath("\$CONTENT_ROOT/src/test/testData")
class MyPluginTest : BasePlatformTestCase() {

    fun testReaderService() {
        val projectService = project.service<ReaderService>()
        
        // 测试 ReaderService 是否能正确初始化
        assertNotNull(projectService)
    }

    fun testNovelFileModel() {
        // 测试 NovelFile 数据类
        val testFile = File("test.txt")
        val novelFile = NovelFile(testFile)
        
        assertEquals(testFile, novelFile.file)
        assertEquals("test", novelFile.title)
        assertEquals(0L, novelFile.currentPosition)
        assertEquals(0, novelFile.currentChapter)
    }

    override fun getTestDataPath() = "src/test/testData"
}
