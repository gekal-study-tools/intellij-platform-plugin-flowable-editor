package com.github.gekal.flowableeditor.inspection

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.psi.BpmnIdIndex
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile

/**
 * 同じ id が複数の要素に付いている状態を報告する。
 * BPMN では id はファイル内で一意でなければならず、重複していると
 * 参照がどちらに解決されるか分からなくなる。
 */
class BpmnDuplicateIdInspection : BpmnInspectionBase() {

    override fun createVisitor(holder: ProblemsHolder): PsiElementVisitor = object : XmlElementVisitor() {
        override fun visitXmlAttribute(attribute: XmlAttribute) {
            if (attribute.localName != "id") return

            val valueElement = attribute.valueElement ?: return
            val value = attribute.value?.trim().orEmpty()
            if (value.isEmpty()) return

            val file = attribute.containingFile as? XmlFile ?: return
            val declarations = BpmnIdIndex.duplicates(file)[value] ?: return
            if (declarations.size <= 1) return
            // 最初の宣言は「正」とみなし、2 つ目以降だけを報告する
            if (declarations.firstOrNull() === valueElement) return

            holder.registerProblem(
                valueElement,
                FlowableBundle.message("inspection.duplicate.id.message", value),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
            )
        }
    }
}
