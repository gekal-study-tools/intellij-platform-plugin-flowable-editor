package com.github.gekal.flowableeditor.edit

import com.github.gekal.flowableeditor.psi.BpmnIdIndex
import com.intellij.psi.xml.XmlFile

/**
 * ファイル内で使われていない id を作る。
 * 検査のクイックフィックスと同じ `<タグ名>_<連番>` の形に揃えている。
 */
internal object BpmnIdGenerator {

    fun generate(file: XmlFile, tagName: String): String = generate(BpmnIdIndex.ids(file).keys, tagName)

    fun generate(taken: Set<String>, tagName: String): String {
        var index = 1
        while ("${tagName}_$index" in taken) index++
        return "${tagName}_$index"
    }
}
