package com.github.gekal.flowableeditor.bpmn

import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.xml.XmlFile
import java.util.Locale

/**
 * 「このファイルは Flowable の BPMN 定義か」を判定する。
 *
 * ファイル名だけでは判断できない (`*.xml` に BPMN を書く運用も多い) ため、
 * 名前で決まらない XML は先頭部分を読んで BPMN 名前空間の有無を見る。
 */
object BpmnFiles {

    /** 内容スニッフィングで読む最大文字数。定義の名前空間は必ず先頭付近にある。 */
    private const val SNIFF_LENGTH = 8 * 1024

    /** スニッフィング対象とするファイルサイズの上限。 */
    private const val MAX_SNIFF_FILE_SIZE = 5L * 1024 * 1024

    private val BPMN_EXTENSIONS = listOf(".bpmn", ".bpmn20.xml", ".bpmn.xml")

    /** 拡張子から BPMN だと分かるか。 */
    fun hasBpmnFileName(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return BPMN_EXTENSIONS.any { lower.endsWith(it) }
    }

    /** PSI から判定する。ルートタグが BPMN の `definitions` なら BPMN とみなす。 */
    fun isBpmnFile(file: PsiFile?): Boolean {
        val xmlFile = file as? XmlFile ?: return false
        val root = xmlFile.rootTag ?: return false
        return root.localName == "definitions" && BpmnNamespaces.isModelNamespace(root.namespace)
    }

    /**
     * PSI を作らずに判定する。エディタプロバイダの `accept` のように
     * PSI を組み立てたくない場所で使う。
     */
    fun isBpmnFile(file: VirtualFile): Boolean {
        if (file.isDirectory || !file.isValid) return false
        if (hasBpmnFileName(file.name)) return true
        if (!file.name.lowercase(Locale.ROOT).endsWith(".xml")) return false
        if (file.length > MAX_SNIFF_FILE_SIZE) return false
        return containsBpmnNamespace(file)
    }

    private fun containsBpmnNamespace(file: VirtualFile): Boolean =
        try {
            VfsUtilCore.loadText(file, SNIFF_LENGTH).contains(BpmnNamespaces.MODEL)
        } catch (_: Exception) {
            // 読めないファイル (バイナリ・削除済みなど) は対象外として扱う。
            false
        }
}
