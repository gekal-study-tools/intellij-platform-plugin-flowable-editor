package com.github.gekal.flowableeditor.inspection

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlTag

/**
 * シーケンスフローが繋がっていないフローノードを報告する。
 *
 * 判定はコンテナ (process / subProcess) 単位で行う。
 * 開始イベント・境界イベント・補償ハンドラは入力が無くて当然なので除外する。
 */
class BpmnDisconnectedElementInspection : BpmnInspectionBase() {

    override fun createVisitor(holder: ProblemsHolder): PsiElementVisitor = object : XmlElementVisitor() {
        override fun visitXmlTag(tag: XmlTag) {
            if (!BpmnTags.isContainer(tag)) return

            val children = BpmnTags.flowNodes(tag)
            if (children.size < 2) return

            val flows = BpmnTags.sequenceFlows(tag)
            val sources = flows.mapNotNull { it.getAttributeValue("sourceRef")?.trim() }.toSet()
            val targets = flows.mapNotNull { it.getAttributeValue("targetRef")?.trim() }.toSet()

            for (child in children) {
                val kind = BpmnTags.kindOf(child)
                if (!BpmnTags.participatesInFlow(kind)) continue

                val id = child.getAttributeValue("id")?.trim().orEmpty()
                if (id.isEmpty()) continue

                val isCompensation = child.getAttributeValue("isForCompensation") == "true"
                val needsIncoming = kind != BpmnElementKind.START_EVENT &&
                    kind != BpmnElementKind.BOUNDARY_EVENT &&
                    !isCompensation
                val needsOutgoing = kind != BpmnElementKind.END_EVENT && !isCompensation

                if (needsIncoming && id !in targets) {
                    holder.registerProblem(
                        anchorFor(child),
                        FlowableBundle.message("inspection.disconnected.no.incoming", id),
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
                if (needsOutgoing && id !in sources) {
                    holder.registerProblem(
                        anchorFor(child),
                        FlowableBundle.message("inspection.disconnected.no.outgoing", id),
                        ProblemHighlightType.WEAK_WARNING,
                    )
                }
            }
        }
    }
}
