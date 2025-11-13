# idea-moyu-reader

![Build](https://github.com/cocoCzl/idea-moyu-reader/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
一个强大的小说阅读插件（摸鱼），支持多种电子书格式（TXT、EPUB、MOBI、AZW、AZW3、PDF），提供类似微信读书的阅读体验。具备分页阅读、章节导航、书签管理、字体大小调整等功能，同时支持键盘快捷键操作，让您在IDE中也能享受流畅的阅读体验。
<!-- Plugin description end -->

## 功能特性

- **多格式支持**：支持TXT、EPUB、MOBI、AZW、AZW3、PDF等多种电子书格式
- **分页阅读**：支持按页阅读，可使用键盘方向键翻页
- **章节导航**：智能章节识别，支持章节选择和跳转
- **书签管理**：支持添加、删除、多选书签，方便快速跳转
- **界面定制**：支持字体大小调整，适配IDE主题背景
- **键盘快捷键**：
  - 左右方向键：翻页
  - Z键：隐藏所有按钮，全屏阅读
  - X键：恢复所有按钮
- **阅读进度保存**：自动保存阅读进度，下次继续阅读

## 使用方式

1. **安装插件**：
   - 通过IDE内置插件系统安装
   - 或从JetBrains Marketplace下载安装

2. **打开小说**：
   - 在IDE中打开"View" > "Tool Windows" > "Reader"（或直接搜索"Reader"）
   - 点击"打开文件"按钮，选择您的小说文件

3. **阅读操作**：
   - 使用"上一页"/"下一页"按钮或键盘方向键翻页
   - 使用章节选择下拉框快速跳转到指定章节
   - 调整字体大小后点击"应用"按钮生效

4. **书签管理**：
   - 点击"添加书签"按钮，输入书签名称
   - 点击"书签列表"查看和管理所有书签
   - 支持多选书签并批量删除

5. **快捷键操作**：
   - 点击阅读区域获得焦点后
   - 按Z键：隐藏所有按钮，进入全屏阅读模式
   - 按X键：恢复所有按钮，退出全屏阅读模式

## 开发说明

本插件基于IntelliJ Platform Plugin Template开发，使用Kotlin语言编写，支持多种电子书格式的解析和渲染，提供流畅的阅读体验。

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
