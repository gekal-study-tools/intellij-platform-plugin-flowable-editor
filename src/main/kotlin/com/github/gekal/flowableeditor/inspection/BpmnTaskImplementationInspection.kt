package com.github.gekal.flowableeditor.inspection

import com.github.gekal.flowableeditor.FlowableBundle
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlTag

/**
 * Flowable 固有の設定漏れを見る。
 *
 * - `serviceTask` に実装 (class / expression / delegateExpression / type) が無い
 * - `userTask` に割り当て先も candidate も form も無い
 *
 * どちらもデプロイ自体は通ってしまい、実行時に初めて困る類の抜けなので
 * エディタ側で気付けるようにしている。
 */
class BpmnTaskImplementationInspection : BpmnInspectionBase() {

    override fun createVisitor(holder: ProblemsHolder): PsiElementVisitor = object : XmlElementVisitor() {
        override fun visitXmlTag(tag: XmlTag) {
            when {
                BpmnTags.isModelTag(tag, "serviceTask") -> checkServiceTask(tag, holder)
                BpmnTags.isModelTag(tag, "userTask") -> checkUserTask(tag, holder)
            }
        }
    }

    private fun checkServiceTask(tag: XmlTag, holder: ProblemsHolder) {
        val configured = BpmnTags.hasAnyExtensionAttribute(
            tag, "class", "expression", "delegateExpression", "type",
        )
        // extensionElements の中に flowable:field 等で構成する書き方もあるため、
        // 子要素がある場合は判断を控える
        if (configured || tag.subTags.any { it.localName == "extensionElements" && it.subTags.isNotEmpty() }) return

        holder.registerProblem(
            anchorFor(tag),
            FlowableBundle.message("inspection.task.implementation.service"),
            ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
        )
    }

    private fun checkUserTask(tag: XmlTag, holder: ProblemsHolder) {
        val assigned = BpmnTags.hasAnyExtensionAttribute(
            tag, "assignee", "candidateUsers", "candidateGroups", "formKey", "formFieldValidation",
        )
        // potentialOwner / humanPerformer で割り当てる標準的な書き方も許容する
        if (assigned || tag.subTags.any { it.localName == "potentialOwner" || it.localName == "humanPerformer" }) {
            return
        }

        holder.registerProblem(
            anchorFor(tag),
            FlowableBundle.message("inspection.task.implementation.user"),
            ProblemHighlightType.WEAK_WARNING,
        )
    }
}
