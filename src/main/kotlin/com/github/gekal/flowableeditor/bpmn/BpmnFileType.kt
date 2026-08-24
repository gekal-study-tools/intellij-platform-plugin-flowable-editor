package com.github.gekal.flowableeditor.bpmn

import com.github.gekal.flowableeditor.FlowableBundle
import com.intellij.ide.highlighter.XmlLikeFileType
import com.intellij.lang.xml.XMLLanguage
import javax.swing.Icon

/**
 * `*.bpmn` / `*.bpmn20.xml` 用のファイルタイプ。
 *
 * 言語は XML のままにしてあるので、補完・整形・スキーマ検証といった
 * プラットフォーム標準の XML サポートはそのまま効く。独自タイプにしているのは
 * アイコンと「New」メニューでの見分けのため。
 */
class BpmnFileType private constructor() : XmlLikeFileType(XMLLanguage.INSTANCE) {

    override fun getName(): String = "Flowable BPMN"

    override fun getDescription(): String = FlowableBundle.message("filetype.bpmn.description")

    override fun getDefaultExtension(): String = "bpmn"

    override fun getIcon(): Icon = FlowableIcons.BpmnFile

    companion object {
        @JvmField
        val INSTANCE = BpmnFileType()
    }
}
