package com.github.gekal.flowableeditor.inspection

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.psi.BpmnIdIndex
import com.github.gekal.flowableeditor.psi.BpmnReferenceAttributes
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlFile

/**
 * `targetRef="typo"` のように、同じファイルに存在しない id を指している参照を報告する。
 *
 * Flowable はデプロイ時にこの種の誤りで例外を投げるため、
 * 編集中に気付けると効果が大きい。
 */
class BpmnUnresolvedReferenceInspection : BpmnInspectionBase() {

    override fun createVisitor(holder: ProblemsHolder): PsiElementVisitor = object : XmlElementVisitor() {
        override fun visitXmlAttribute(attribute: XmlAttribute) {
            if (!BpmnReferenceAttributes.isReferenceAttribute(attribute.localName)) return

            val valueElement = attribute.valueElement ?: return
            val value = attribute.value?.trim().orEmpty()
            if (value.isEmpty()) return

            val file = attribute.containingFile as? XmlFile ?: return
            if (BpmnIdIndex.resolve(file, value) != null) return

            holder.registerProblem(
                valueElement,
                FlowableBundle.message("inspection.unresolved.reference.message", value),
                ProblemHighlightType.LIKE_UNKNOWN_SYMBOL,
            )
        }
    }
}
