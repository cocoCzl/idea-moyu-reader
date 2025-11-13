package com.github.coco.reader.toolWindow

import com.github.coco.reader.model.Bookmark
import com.github.coco.reader.settings.ReaderSettings
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
import com.github.coco.reader.services.ReaderService
import java.awt.*
import javax.swing.*
import javax.swing.border.EmptyBorder

class ReaderWindowFactory : ToolWindowFactory {

    init {
        thisLogger().info("ReaderWindowFactory initialized")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val readerWindow = ReaderWindow(toolWindow, project)
        val content = ContentFactory.getInstance().createContent(readerWindow.getContent(), null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    class ReaderWindow(toolWindow: ToolWindow, private val project: Project) {

        private val service = project.service<ReaderService>()
        private val settings = project.service<ReaderSettings>()
        private val textArea = JEditorPane().apply {
            contentType = "text/html"
            isEditable = false
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        }
        private val scrollPane = JScrollPane(textArea).apply {
            verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
            horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        }
        private val chapterLabel = JLabel("章节: 0/0").apply {
            font = Font("SansSerif", Font.PLAIN, 12)
        }
        private val pageLabel = JLabel("页: 0/0").apply {
            font = Font("SansSerif", Font.PLAIN, 12)
        }
        private var currentFile: VirtualFile? = null
        private var chapterComboBox: JComboBox<String>? = null
        private var topToolbar: JPanel? = null
        private var bottomToolbar: JPanel? = null
        private var isToolbarVisible = true
        
        // 添加键盘事件监听器
        init {
            textArea.addKeyListener(object : java.awt.event.KeyAdapter() {
                override fun keyPressed(e: java.awt.event.KeyEvent) {
                    when (e.keyCode) {
                        java.awt.event.KeyEvent.VK_LEFT -> {
                            // 向左方向键，上一页
                            service.previousPage()?.let { page ->
                                textArea.text = page
                                updatePageInfo()
                                scrollToTop()
                            }
                        }
                        java.awt.event.KeyEvent.VK_RIGHT -> {
                            // 向右方向键，下一页
                            service.nextPage()?.let { page ->
                                textArea.text = page
                                updatePageInfo()
                                scrollToTop()
                            }
                        }
                        java.awt.event.KeyEvent.VK_Z -> {
                            // Z键，隐藏所有按钮
                            toggleToolbars(false)
                        }
                        java.awt.event.KeyEvent.VK_X -> {
                            // X键，显示所有按钮
                            toggleToolbars(true)
                        }
                    }
                }
            })
            
            // 确保组件能够接收键盘焦点
            textArea.isFocusable = true
        }
        
        fun getContent() = JPanel().apply {
            layout = BorderLayout()
            border = EmptyBorder(10, 10, 10, 10)

            // 创建顶部工具栏
            val topToolbarPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 5)).apply {
                background = Color(245, 245, 245)
                border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
                
                add(JButton("打开文件").apply {
                    addActionListener {
                        openFile(project)
                    }
                })
                
                add(Box.createHorizontalStrut(20))
                
                add(JButton("上一页").apply {
                    addActionListener {
                        service.previousPage()?.let { page ->
                            textArea.text = page
                            updatePageInfo()
                            scrollToTop()
                        }
                    }
                })

                add(pageLabel)

                add(JButton("下一页").apply {
                    addActionListener {
                        service.nextPage()?.let { page ->
                            textArea.text = page
                            updatePageInfo()
                            scrollToTop()
                        }
                    }
                })
                
                add(Box.createHorizontalStrut(20))
                
                // 章节选择下拉框
                val comboBox = JComboBox<String>(arrayOf("章节选择"))
                comboBox.addActionListener {
                    val selected = comboBox.selectedItem as? String
                    if (selected != null && selected != "章节选择" && selected != "无章节") {
                        // 获取章节索引
                        val chapterTitles = service.getChapterTitles()
                        val chapterIndex = chapterTitles.indexOf(selected)
                        if (chapterIndex >= 0) {
                            // 跳转到指定章节
                            if (service.goToChapter(chapterIndex) != null) {
                                textArea.text = service.getCurrentPage()
                                updateChapterInfo()
                                updatePageInfo()
                                scrollToTop()
                            }
                        }
                    }
                }
                chapterComboBox = comboBox
                add(comboBox)
            }
            topToolbar = topToolbarPanel

            // 创建底部工具栏
            val bottomToolbarPanel = JPanel(FlowLayout(FlowLayout.CENTER, 10, 5)).apply {
                background = Color(245, 245, 245)
                border = BorderFactory.createEmptyBorder(5, 0, 5, 0)
                
                // 字体大小设置
                add(JLabel("大小:"))
                val fontSizeSpinner = JSpinner(SpinnerNumberModel(settings.fontSize, 8, 72, 1))
                fontSizeSpinner.preferredSize = Dimension(60, fontSizeSpinner.preferredSize.height)
                add(fontSizeSpinner)

                add(JButton("应用").apply {
                    addActionListener {
                        settings.fontSize = fontSizeSpinner.value as Int
                        applySettings()
                    }
                })
                
                add(JButton("添加书签").apply {
                    addActionListener {
                        val bookmarkTitle = JOptionPane.showInputDialog(
                            null,
                            "请输入书签名称:",
                            "添加书签",
                            JOptionPane.PLAIN_MESSAGE
                        )
                        
                        if (bookmarkTitle != null && bookmarkTitle.trim().isNotEmpty()) {
                            val title = bookmarkTitle.trim()
                            if (service.addBookmark(title)) {
                                JOptionPane.showMessageDialog(this, "书签已添加")
                            } else {
                                JOptionPane.showMessageDialog(this, "添加书签失败")
                            }
                        } else if (bookmarkTitle != null) {
                            JOptionPane.showMessageDialog(this, "书签名称不能为空")
                        }
                    }
                })

                add(JButton("书签列表").apply {
                    addActionListener {
                        val currentFilePath = currentFile?.path
                        if (currentFilePath != null) {
                            val bookmarks = service.getBookmarks(currentFilePath)
                            showBookmarkDialog(bookmarks)
                        } else {
                            JOptionPane.showMessageDialog(this, "请先打开一个小说文件")
                        }
                    }
                })
            }
            bottomToolbar = bottomToolbarPanel

            add(topToolbarPanel, BorderLayout.NORTH)
            add(scrollPane, BorderLayout.CENTER)
            add(bottomToolbarPanel, BorderLayout.SOUTH)

            // 初始化显示和应用设置
            textArea.text = service.getCurrentPage() ?: "<html><body><div style='font-family: SansSerif; font-size: 16px; padding: 20px;'>请打开小说开始摸鱼</div></body></html>"
            updateChapterInfo()
            updatePageInfo()
            applySettings()
        }

        private fun openFile(project: Project) {
            val descriptor = FileChooserDescriptor(true, false, false, false, false, false)
            descriptor.withFileFilter { file -> 
                file.extension?.lowercase() in listOf("txt", "epub", "mobi", "azw", "azw3", "pdf") 
            }
            descriptor.title = "选择小说文件"

            val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
            val files = chooser.choose(project, null)

            if (files.isNotEmpty()) {
                val file = files[0]
                currentFile = file
                if (service.openNovelFile(java.io.File(file.path))) {
                    textArea.text = service.getCurrentPage()
                    updateChapterInfo()
                    updatePageInfo()
                    // 更新章节选择下拉框
                    SwingUtilities.invokeLater {
                        chapterComboBox?.let { updateChapterComboBox(it) }
                    }
                    thisLogger().info("Successfully opened novel file: ${file.path}")
                } else {
                    JOptionPane.showMessageDialog(null, "无法打开文件: ${file.path}")
                }
            }
        }

        private fun updateChapterInfo() {
            val current = service.getCurrentChapterIndex() + 1
            val total = service.getTotalChapters()
            chapterLabel.text = "章节: $current/$total"
        }

        private fun updatePageInfo() {
            val currentPage = service.getCurrentPageIndex() + 1
            val totalPages = service.getCurrentPageCount()
            pageLabel.text = "页: $currentPage/$totalPages"
        }

        private fun showBookmarkDialog(bookmarks: List<Bookmark>) {
            if (bookmarks.isEmpty()) {
                JOptionPane.showMessageDialog(null, "暂无书签")
                return
            }

            // 创建自定义对话框支持多选
            val dialog = JDialog().apply {
                title = "书签管理"
                isModal = true
                defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
            }
            
            // 创建列表模型和列表
            val listModel = DefaultListModel<String>()
            bookmarks.forEach { listModel.addElement(it.title) }
            val bookmarkList = JList(listModel).apply {
                selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            }
            
            // 创建主面板
            val mainPanel = JPanel(BorderLayout()).apply {
                border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
            }
            
            // 创建列表滚动面板
            val scrollPane = JScrollPane(bookmarkList).apply {
                preferredSize = Dimension(300, 200)
            }
            
            // 创建按钮面板
            val buttonPanel = JPanel(FlowLayout())
            
            val jumpButton = JButton("跳转").apply {
                addActionListener {
                    val selectedIndices = bookmarkList.selectedIndices
                    if (selectedIndices.isNotEmpty()) {
                        val selectedIndex = selectedIndices[0]  // 只跳转到第一个选中的书签
                        val selectedBookmark = bookmarks[selectedIndex]
                        if (service.goToBookmark(selectedBookmark)) {
                            textArea.text = service.getCurrentPage()
                            updateChapterInfo()
                            updatePageInfo()
                            scrollToTop()
                            dialog.dispose()
                        } else {
                            JOptionPane.showMessageDialog(dialog, "跳转到书签失败")
                        }
                    } else {
                        JOptionPane.showMessageDialog(dialog, "请选择一个书签")
                    }
                }
            }
            
            val deleteButton = JButton("删除").apply {
                addActionListener {
                    val selectedIndices = bookmarkList.selectedIndices
                    if (selectedIndices.isNotEmpty()) {
                        val selectedBookmarks = selectedIndices.map { bookmarks[it] }
                        val confirm = JOptionPane.showConfirmDialog(
                            dialog,
                            "确定要删除选中的 ${selectedBookmarks.size} 个书签吗？",
                            "确认删除",
                            JOptionPane.YES_NO_OPTION
                        )
                        
                        if (confirm == JOptionPane.YES_OPTION) {
                            var successCount = 0
                            selectedBookmarks.forEach { bookmark ->
                                if (service.removeBookmark(bookmark)) {
                                    successCount++
                                }
                            }
                            // 从列表模型中移除已删除的书签
                            selectedIndices.sortedDescending().forEach { index ->
                                listModel.remove(index)
                            }
                            JOptionPane.showMessageDialog(dialog, "成功删除 $successCount 个书签")
                        }
                    } else {
                        JOptionPane.showMessageDialog(dialog, "请选择要删除的书签")
                    }
                }
            }
            
            val cancelButton = JButton("取消").apply {
                addActionListener {
                    dialog.dispose()
                }
            }
            
            buttonPanel.add(jumpButton)
            buttonPanel.add(deleteButton)
            buttonPanel.add(cancelButton)
            
            mainPanel.add(scrollPane, BorderLayout.CENTER)
            mainPanel.add(buttonPanel, BorderLayout.SOUTH)
            
            dialog.contentPane = mainPanel
            dialog.pack()
            dialog.setLocationRelativeTo(null)
            dialog.isVisible = true
        }
        
        private fun scrollToTop() {
            SwingUtilities.invokeLater {
                scrollPane.verticalScrollBar.value = 0
            }
        }
        
        private fun toggleToolbars(visible: Boolean) {
            topToolbar?.isVisible = visible
            bottomToolbar?.isVisible = visible
            isToolbarVisible = visible
            
            // 重新验证布局
            topToolbar?.revalidate()
            bottomToolbar?.revalidate()
            topToolbar?.parent?.revalidate()
            bottomToolbar?.parent?.revalidate()
        }
        
        private fun getChapterComboBox(): JComboBox<String>? {
            return chapterComboBox
        }
        
        private fun updateChapterComboBox(comboBox: JComboBox<String>) {
            // 清空现有选项
            comboBox.removeAllItems()
            
            // 获取章节标题
            val chapterTitles = service.getChapterTitles()
            
            // 添加章节选项
            if (chapterTitles.isNotEmpty()) {
                for (title in chapterTitles) {
                    comboBox.addItem(title)
                }
            } else {
                comboBox.addItem("无章节")
            }
        }
        
        private fun applySettings() {
            // 应用字体设置到JEditorPane组件
            val font = Font(settings.fontFamily, Font.PLAIN, settings.fontSize)
            textArea.font = font
            
            // 重新加载当前内容以应用新的字体和样式设置
            val currentContent = service.getCurrentPage()
            if (currentContent != null) {
                textArea.text = currentContent
            }
            
            // 重新应用滚动位置
            scrollToTop()
        }
    }
}
