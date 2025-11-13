package com.github.coco.reader.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 阅读器设置数据类
 */
@State(
    name = "NovelReaderSettings",
    storages = [Storage("novel-reader.xml")]
)
@Service
class ReaderSettings : PersistentStateComponent<ReaderSettings> {

    var fontSize: Int = 16
    var fontFamily: String = "微软雅黑"
    var backgroundColor: String = "#FFFFFF"
    var textColor: String = "#000000"
    var lineSpacing: Float = 1.8f
    var nightMode: Boolean = false
    var pageMargin: Int = 30
    var themeColor: String = "#333333"

    override fun getState(): ReaderSettings {
        return this
    }

    override fun loadState(state: ReaderSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}