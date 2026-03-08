package com.github.coco.reader.toolWindow

import com.github.coco.reader.model.Bookmark
import com.github.coco.reader.services.ReaderPersistenceService
import com.github.coco.reader.settings.ReaderSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.github.coco.reader.services.ReaderService
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.*
import javax.swing.border.EmptyBorder

class ReaderWindowFactory : ToolWindowFactory {

    init {
        thisLogger().info("ReaderWindowFactory initialized")
    }

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val readerPanel = ReaderPanel(toolWindow, project)
        val content = ContentFactory.getInstance().createContent(readerPanel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true
}

/**
 * 主阅读面板
 */
class ReaderPanel(
    private val toolWindow: ToolWindow,
    private val project: Project
) : JPanel(BorderLayout()) {

    private val service = project.service<ReaderService>()
    private val settings = ApplicationManager.getApplication().service<ReaderSettings>()
    private val persistenceService = ApplicationManager.getApplication().service<ReaderPersistenceService>()

    // UI 组件
    private val textPane = JEditorPane().apply {
        contentType = "text/html"
        isEditable = false
        putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
        border = EmptyBorder(10, 15, 10, 15)
    }

    private val scrollPane = JBScrollPane(textPane).apply {
        verticalScrollBarPolicy = JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED
        horizontalScrollBarPolicy = JScrollPane.HORIZONTAL_SCROLLBAR_NEVER
        border = JBUI.Borders.empty()
    }

    private val chapterComboBox = ComboBox<String>().apply {
        preferredSize = Dimension(200, preferredSize.height)
        prototypeDisplayValue = "第 一百二十三 章 这是一个很长的章节标题"
    }

    private val pageLabel = JBLabel("页: 0/0").apply {
        font = font.deriveFont(Font.PLAIN, 12f)
    }

    private val chapterLabel = JBLabel("章节: 0/0").apply {
        font = font.deriveFont(Font.PLAIN, 12f)
    }

    private val fontSizeSpinner = JSpinner(SpinnerNumberModel(settings.fontSize, 10, 48, 2)).apply {
        preferredSize = Dimension(60, preferredSize.height)
    }

    private var currentFile: VirtualFile? = null
    private var isToolbarVisible = true

    private val topToolbar: JPanel
    private val bottomToolbar: JPanel

    init {
        border = JBUI.Borders.empty(8)
        background = UIManager.getColor("Panel.background")

        // 顶部工具栏
        topToolbar = createTopToolbar()

        // 底部工具栏
        bottomToolbar = createBottomToolbar()

        // 内容区域
        val contentPanel = JPanel(BorderLayout()).apply {
            add(scrollPane, BorderLayout.CENTER)
        }

        // 添加组件
        add(topToolbar, BorderLayout.NORTH)
        add(contentPanel, BorderLayout.CENTER)
        add(bottomToolbar, BorderLayout.SOUTH)

        // 设置键盘事件
        setupKeyListener()

        // 初始化显示
        updateDisplay()
    }

    /**
     * 创建顶部工具栏
     */
    private fun createTopToolbar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.CENTER, 8, 6)).apply {
            background = UIManager.getColor("Panel.background")
            border = JBUI.Borders.emptyTop(4)

            // 打开文件按钮
            add(createToolBarButton("打开文件", AllIcons.Actions.MenuOpen) { openFile() })

            add(Box.createHorizontalStrut(16))

            // 导航按钮
            add(createToolBarButton("上一页", AllIcons.Actions.Back) { goToPreviousPage() })
            add(pageLabel)
            add(createToolBarButton("下一页", AllIcons.Actions.Forward) { goToNextPage() })

            add(Box.createHorizontalStrut(16))

            // 章节选择
            add(JBLabel("章节:").apply { font = font.deriveFont(Font.PLAIN, 12f) })
            chapterComboBox.addActionListener {
                val selected = chapterComboBox.selectedItem as? String
                if (selected != null && selected != "请先打开文件") {
                    val titles = service.getChapterTitles()
                    val index = titles.indexOf(selected)
                    if (index >= 0 && index != service.getCurrentChapterIndex()) {
                        service.goToChapter(index)
                        updateDisplay()
                    }
                }
            }
            add(chapterComboBox)
        }
    }

    /**
     * 创建底部工具栏
     */
    private fun createBottomToolbar(): JPanel {
        return JPanel(FlowLayout(FlowLayout.CENTER, 8, 6)).apply {
            background = UIManager.getColor("Panel.background")
            border = JBUI.Borders.emptyBottom(4)

            // 字体大小
            add(JBLabel("字体大小:").apply { font = font.deriveFont(Font.PLAIN, 12f) })
            add(fontSizeSpinner)
            add(createToolBarButton("应用", AllIcons.Actions.Checked) { applyFontSize() })

            add(Box.createHorizontalStrut(16))

            // 书签功能
            add(createToolBarButton("添加书签", AllIcons.General.Add) { addBookmark() })
            add(createToolBarButton("书签列表", AllIcons.Actions.ListFiles) { showBookmarkDialog() })

            add(Box.createHorizontalStrut(16))

            // 设置按钮
            add(createToolBarButton("设置", AllIcons.General.Settings) { showSettingsDialog() })

            // 章节信息
            add(Box.createHorizontalStrut(16))
            add(chapterLabel)
        }
    }

    /**
     * 创建工具栏按钮
     */
    private fun createToolBarButton(text: String, icon: Icon, action: () -> Unit): JButton {
        return JButton(text, icon).apply {
            margin = JBUI.insets(4, 8)
            font = font.deriveFont(Font.PLAIN, 12f)
            addActionListener { action() }
        }
    }

    /**
     * 设置键盘事件监听
     */
    private fun setupKeyListener() {
        textPane.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                when (e.keyCode) {
                    KeyEvent.VK_LEFT -> goToPreviousPage()
                    KeyEvent.VK_RIGHT -> goToNextPage()
                    KeyEvent.VK_UP -> goToPreviousPage()
                    KeyEvent.VK_DOWN -> goToNextPage()
                    KeyEvent.VK_Z -> toggleToolbars(false)
                    KeyEvent.VK_X -> toggleToolbars(true)
                    KeyEvent.VK_ESCAPE -> toggleToolbars(true)
                }
            }
        })
        textPane.isFocusable = true
        textPane.requestFocusInWindow()
    }

    /**
     * 打开文件
     */
    private fun openFile() {
        val descriptor = FileChooserDescriptor(true, false, false, false, false, false).apply {
            withFileFilter { file ->
                file.extension?.lowercase() in listOf("txt", "epub", "mobi", "azw", "azw3", "pdf")
            }
            title = "选择小说文件"
        }

        val chooser = FileChooserFactory.getInstance().createFileChooser(descriptor, project, null)
        val files = chooser.choose(project, null)

        if (files.isNotEmpty()) {
            val file = files[0]
            currentFile = file
            if (service.openNovelFile(File(file.path))) {
                updateDisplay()
                updateChapterComboBox()
                thisLogger().info("Successfully opened novel file: ${file.path}")
            } else {
                Messages.showErrorDialog("无法打开文件: ${file.path}", "错误")
            }
        }
    }

    /**
     * 上一页
     */
    private fun goToPreviousPage() {
        service.previousPage()?.let {
            updateDisplay()
        }
    }

    /**
     * 下一页
     */
    private fun goToNextPage() {
        service.nextPage()?.let {
            updateDisplay()
        }
    }

    /**
     * 应用字体大小设置
     */
    private fun applyFontSize() {
        settings.fontSize = fontSizeSpinner.value as Int
        updateDisplay()
    }

    /**
     * 添加书签
     */
    private fun addBookmark() {
        if (currentFile == null) {
            Messages.showWarningDialog("请先打开一个小说文件", "提示")
            return
        }

        val title = Messages.showInputDialog(
            project,
            "请输入书签名称:",
            "添加书签",
            AllIcons.General.Add,
            "第${service.getCurrentChapterIndex() + 1}章 第${service.getCurrentPageIndex() + 1}页",
            null
        )

        if (!title.isNullOrBlank()) {
            if (service.addBookmark(title.trim())) {
                Messages.showInfoMessage("书签已添加", "成功")
            } else {
                Messages.showWarningDialog("该位置已存在书签", "提示")
            }
        }
    }

    /**
     * 显示书签对话框
     */
    private fun showBookmarkDialog() {
        val filePath = currentFile?.path
        if (filePath == null) {
            Messages.showWarningDialog("请先打开一个小说文件", "提示")
            return
        }

        val bookmarks = persistenceService.getBookmarks(filePath)
        if (bookmarks.isEmpty()) {
            Messages.showInfoMessage("暂无书签", "书签列表")
            return
        }

        // 创建书签列表面板
        val listModel = DefaultListModel<String>()
        val bookmarkList = bookmarks.mapIndexed { index, bookmark ->
            val displayText = "${bookmark.title} - ${bookmark.chapterTitle} (第${bookmark.pageIndex + 1}页)"
            listModel.addElement(displayText)
            displayText to bookmark
        }.toMap()

        val bookmarkJList = JList(listModel).apply {
            selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            cellRenderer = object : DefaultListCellRenderer() {
                override fun getListCellRendererComponent(
                    list: JList<*>?, value: Any?, index: Int,
                    isSelected: Boolean, cellHasFocus: Boolean
                ): Component {
                    val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                    border = JBUI.Borders.empty(4, 8)
                    return c
                }
            }
        }

        val listScrollPane = JBScrollPane(bookmarkJList).apply {
            preferredSize = Dimension(450, 250)
        }

        // 创建按钮面板
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("跳转").apply {
                addActionListener {
                    val selected = bookmarkJList.selectedValue
                    if (selected != null) {
                        val bookmark = bookmarkList[selected]
                        if (bookmark != null) {
                            val bm = Bookmark(
                                novelFilePath = bookmark.novelFilePath,
                                chapterIndex = bookmark.chapterIndex,
                                pageIndex = bookmark.pageIndex,
                                title = bookmark.title,
                                timestamp = bookmark.timestamp
                            )
                            if (service.goToBookmark(bm)) {
                                updateDisplay()
                                SwingUtilities.getWindowAncestor(this@ReaderPanel)?.dispose()
                            }
                        }
                    }
                }
            })
            add(JButton("删除").apply {
                addActionListener {
                    val selectedValues = bookmarkJList.selectedValuesList
                    if (selectedValues.isNotEmpty()) {
                        val result = Messages.showYesNoDialog(
                            project,
                            "确定要删除选中的 ${selectedValues.size} 个书签吗？",
                            "确认删除",
                            Messages.getQuestionIcon()
                        )
                        if (result == Messages.YES) {
                            selectedValues.forEach { displayText ->
                                bookmarkList[displayText]?.let { bookmark ->
                                    persistenceService.removeBookmark(bookmark)
                                }
                                listModel.removeElement(displayText)
                            }
                        }
                    }
                }
            })
            add(JButton("关闭").apply {
                addActionListener {
                    SwingUtilities.getWindowAncestor(this@ReaderPanel)?.dispose()
                }
            })
        }

        // 创建对话框内容
        val dialogPanel = JPanel(BorderLayout(8, 8)).apply {
            border = JBUI.Borders.empty(12)
            add(JBLabel("书签列表 (${bookmarks.size})").apply {
                font = font.deriveFont(Font.BOLD, 14f)
            }, BorderLayout.NORTH)
            add(listScrollPane, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        // 显示对话框
        val dialog = JDialog(SwingUtilities.getWindowAncestor(this) as Frame?, "书签管理", true).apply {
            contentPane = dialogPanel
            pack()
            setLocationRelativeTo(this@ReaderPanel)
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        }
        dialog.isVisible = true
    }

    /**
     * 显示设置对话框
     */
    private fun showSettingsDialog() {
        val fontSizeSpinner = JSpinner(SpinnerNumberModel(settings.fontSize, 10, 48, 2)).apply {
            preferredSize = Dimension(80, preferredSize.height)
        }
        val fontFamilyField = JTextField(settings.fontFamily, 20)
        val lineSpacingSpinner = JSpinner(SpinnerNumberModel(settings.lineSpacing.toDouble(), 1.0, 3.0, 0.1)).apply {
            preferredSize = Dimension(80, preferredSize.height)
        }
        val pageMarginSpinner = JSpinner(SpinnerNumberModel(settings.pageMargin, 0, 100, 5)).apply {
            preferredSize = Dimension(80, preferredSize.height)
        }
        val nightModeCheckbox = JCheckBox().apply { isSelected = settings.nightMode }
        val themeColorField = JTextField(settings.themeColor, 10)

        val panel = JPanel(GridBagLayout()).apply {
            border = JBUI.Borders.empty(12)
            val gbc = GridBagConstraints().apply {
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(4, 0, 4, 12)
            }

            // 字体大小
            add(JBLabel("字体大小:"), gbc.apply { gridx = 0; gridy = 0 })
            add(fontSizeSpinner, gbc.apply { gridx = 1; gridy = 0 })

            // 字体名称
            add(JBLabel("字体名称:"), gbc.apply { gridx = 0; gridy = 1 })
            add(fontFamilyField, gbc.apply { gridx = 1; gridy = 1 })

            // 行间距
            add(JBLabel("行间距:"), gbc.apply { gridx = 0; gridy = 2 })
            add(lineSpacingSpinner, gbc.apply { gridx = 1; gridy = 2 })

            // 页边距
            add(JBLabel("页边距:"), gbc.apply { gridx = 0; gridy = 3 })
            add(pageMarginSpinner, gbc.apply { gridx = 1; gridy = 3 })

            // 夜间模式
            add(JBLabel("夜间模式:"), gbc.apply { gridx = 0; gridy = 4 })
            add(nightModeCheckbox, gbc.apply { gridx = 1; gridy = 4 })

            // 主题色
            add(JBLabel("标题颜色:"), gbc.apply { gridx = 0; gridy = 5 })
            add(themeColorField, gbc.apply { gridx = 1; gridy = 5 })
        }

        // 按钮面板
        var confirmed = false
        val buttonPanel = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
            add(JButton("应用").apply {
                addActionListener {
                    confirmed = true
                    SwingUtilities.getWindowAncestor(this)?.dispose()
                }
            })
            add(JButton("取消").apply {
                addActionListener {
                    SwingUtilities.getWindowAncestor(this)?.dispose()
                }
            })
        }

        val dialogPanel = JPanel(BorderLayout()).apply {
            add(panel, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }

        val dialog = JDialog(SwingUtilities.getWindowAncestor(this) as Frame?, "阅读设置", true).apply {
            contentPane = dialogPanel
            pack()
            setLocationRelativeTo(this@ReaderPanel)
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        }
        dialog.isVisible = true

        if (confirmed) {
            settings.fontSize = fontSizeSpinner.value as Int
            settings.fontFamily = fontFamilyField.text
            settings.lineSpacing = (lineSpacingSpinner.value as Double).toFloat()
            settings.pageMargin = pageMarginSpinner.value as Int
            settings.nightMode = nightModeCheckbox.isSelected
            settings.themeColor = themeColorField.text

            this@ReaderPanel.fontSizeSpinner.value = settings.fontSize
            updateDisplay()
        }
    }

    /**
     * 切换工具栏显示/隐藏
     */
    private fun toggleToolbars(visible: Boolean) {
        topToolbar.isVisible = visible
        bottomToolbar.isVisible = visible
        isToolbarVisible = visible

        if (visible) {
            textPane.requestFocusInWindow()
        }
    }

    /**
     * 更新显示内容
     */
    private fun updateDisplay() {
        val content = service.getCurrentPage()
        if (content != null) {
            textPane.text = content
        } else {
            textPane.text = """
                <html>
                <head>
                    <style>
                        body { 
                            font-family: sans-serif; 
                            padding: 40px; 
                            text-align: center;
                            color: #666;
                        }
                        h2 { color: #4a90d9; margin-bottom: 20px; }
                        p { line-height: 1.8; margin: 10px 0; }
                        .shortcut { 
                            background: #f5f5f5; 
                            padding: 2px 8px; 
                            border-radius: 4px; 
                            font-family: monospace;
                        }
                    </style>
                </head>
                <body>
                    <h2>欢迎使用摸鱼阅读器</h2>
                    <p>点击 <b>打开文件</b> 开始阅读</p>
                    <p>支持格式: TXT, EPUB, MOBI, AZW, AZW3, PDF</p>
                    <hr style="margin: 20px 0; border: none; border-top: 1px solid #eee;">
                    <p><b>快捷键:</b></p>
                    <p><span class="shortcut">←</span> <span class="shortcut">→</span> 翻页</p>
                    <p><span class="shortcut">Z</span> 隐藏工具栏</p>
                    <p><span class="shortcut">X</span> 显示工具栏</p>
                </body>
                </html>
            """.trimIndent()
        }

        // 更新页面和章节信息
        pageLabel.text = "页: ${service.getCurrentPageIndex() + 1}/${service.getCurrentPageCount()}"
        chapterLabel.text = "章节: ${service.getCurrentChapterIndex() + 1}/${service.getTotalChapters()}"

        // 滚动到顶部
        SwingUtilities.invokeLater {
            scrollPane.verticalScrollBar.value = 0
        }
    }

    /**
     * 更新章节下拉框
     */
    private fun updateChapterComboBox() {
        chapterComboBox.removeAllItems()
        val titles = service.getChapterTitles()

        if (titles.isEmpty()) {
            chapterComboBox.addItem("请先打开文件")
            return
        }

        titles.forEach { chapterComboBox.addItem(it) }

        // 设置当前章节
        val currentIndex = service.getCurrentChapterIndex()
        if (currentIndex in titles.indices) {
            chapterComboBox.selectedIndex = currentIndex
        }
    }
}