# idea-moyu-reader

![Build](https://github.com/cocoCzl/idea-moyu-reader/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

<!-- Plugin description -->
一个功能完善的 IntelliJ 平台小说阅读插件，支持多种电子书格式（TXT、EPUB、MOBI、AZW、AZW3、PDF），提供类似微信读书的阅读体验。具备分页阅读、智能章节识别、书签管理、阅读设置等功能，支持键盘快捷键操作，让您在 IDE 中也能享受流畅的阅读体验。
<!-- Plugin description end -->

## 功能特性

- **多格式支持**：TXT、EPUB、MOBI、AZW、AZW3、PDF
- **分页阅读**：每页约 50 行，支持键盘方向键翻页
- **智能章节识别**：自动识别中英文章节标题，支持快速跳转
- **书签管理**：添加、删除、批量管理，显示章节信息
- **阅读设置**：字体大小、字体名称、行间距、页边距、夜间模式、标题颜色
- **数据持久化**：书签和阅读进度自动保存，重启 IDE 后自动恢复
- **主题适配**：自动适配 IDE 主题背景色
- **键盘快捷键**：
  - `←` `→` `↑` `↓`：翻页
  - `Z`：隐藏工具栏，沉浸阅读
  - `X` / `Esc`：恢复工具栏

## 使用方式

1. **安装插件**：通过 IDE 插件市场或手动安装 zip 包

2. **打开小说**：
   - `View` > `Tool Windows` > `Reader`
   - 点击"打开文件"，选择小说文件

3. **阅读操作**：
   - 按钮或方向键翻页
   - 章节下拉框快速跳转

4. **书签管理**：
   - 添加书签（显示当前章节和页码）
   - 书签列表支持跳转和批量删除

5. **阅读设置**：点击"设置"按钮调整字体、行间距、夜间模式等

## 构建和安装插件

### 方法一：打包并安装插件（推荐用于生产环境）

1. **构建插件包**：
   ```bash
   ./gradlew buildPlugin
   ```
   执行此命令后，插件包将生成在 `build/distributions/` 目录中，文件格式为 `.zip`

2. **在 IntelliJ IDEA 中安装**：
   - 打开 IntelliJ IDEA
   - 进入 `File` > `Settings` (Windows/Linux) 或 `IntelliJ IDEA` > `Preferences` (macOS)
   - 选择 `Plugins` 选项卡
   - 点击右上角的齿轮图标，选择 `Install Plugin from Disk...`
   - 选择刚才生成的 `.zip` 文件，点击 `OK`
   - 重启 IntelliJ IDEA

### 方法二：直接运行插件进行测试（开发调试）

如果您只是想测试插件，可以直接运行：
```bash
./gradlew runIde
```
这将启动一个新的 IntelliJ IDEA 实例，其中已安装了您的插件。

### 方法三：开发模式运行

在开发过程中，您可以使用以下命令来快速测试更改：
```bash
./gradlew runIdeForUiTests
```

## 开发说明

本插件基于IntelliJ Platform Plugin Template开发，使用Kotlin语言编写，支持多种电子书格式的解析和渲染，提供流畅的阅读体验。

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
