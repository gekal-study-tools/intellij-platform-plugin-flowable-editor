package com.github.gekal.flowableeditor.structure

import com.github.gekal.flowableeditor.bpmn.BpmnFiles
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.structureView.xml.XmlStructureViewBuilderProvider
import com.intellij.openapi.editor.Editor
import com.intellij.psi.xml.XmlFile

/**
 * BPMN ファイルのときだけ独自の構造ビューを返す。
 * null を返せば通常の XML 構造ビューにフォールバックするので、
 * 他の XML には一切影響しない。
 */
class BpmnStructureViewBuilderProvider : XmlStructureViewBuilderProvider {

    override fun createStructureViewBuilder(file: XmlFile): StructureViewBuilder? {
        if (!BpmnFiles.isBpmnFile(file)) return null

        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                BpmnStructureViewModel(file)

            override fun isRootNodeShown(): Boolean = false
        }
    }
}
