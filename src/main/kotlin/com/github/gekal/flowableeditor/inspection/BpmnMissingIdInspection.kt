package com.github.gekal.flowableeditor.inspection

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.psi.BpmnIdIndex
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * id を持たないフローノードを報告し、一意な id を補うクイックフィックスを提供する。
 *
 * id が無い要素はシーケンスフローから参照できず、Flowable のデプロイでも
 * 弾かれる。まだ誰からも参照されていない要素なので、id の追加は安全に行える。
 */
class BpmnMissingIdInspection : BpmnInspectionBase() {

    override fun createVisitor(holder: ProblemsHolder): PsiElementVisitor = object : XmlElementVisitor() {
        override fun visitXmlTag(tag: XmlTag) {
            if (!BpmnTags.isModelElement(tag)) return
            if (!BpmnTags.participatesInFlow(BpmnTags.kindOf(tag))) return
            // process の id はプロセス定義キーそのもので、自動生成すべきではない
            if (tag.localName == "process") return
            if (!tag.getAttributeValue("id").isNullOrBlank()) return

            holder.registerProblem(
                anchorFor(tag),
                FlowableBundle.message("inspection.missing.id.message", tag.localName),
                ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                GenerateIdFix(),
            )
        }
    }

    private class GenerateIdFix : LocalQuickFix {

        override fun getFamilyName(): String = FlowableBundle.message("inspection.missing.id.fix")

        override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
            val tag = PsiTreeUtil.getParentOfType(descriptor.psiElement, XmlTag::class.java, false) ?: return
            val file = tag.containingFile as? XmlFile ?: return
            tag.setAttribute("id", generateId(tag.localName, BpmnIdIndex.ids(file).keys))
        }

        /** `userTask_1`, `userTask_2` ... と空いている番号を探す。 */
        private fun generateId(tagName: String, taken: Set<String>): String {
            var index = 1
            while ("${tagName}_$index" in taken) index++
            return "${tagName}_$index"
        }
    }
}
