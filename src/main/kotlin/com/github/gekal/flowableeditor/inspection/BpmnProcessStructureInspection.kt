package com.github.gekal.flowableeditor.inspection

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.XmlElementVisitor
import com.intellij.psi.xml.XmlTag

/**
 * プロセス / 埋め込みサブプロセスに開始イベント・終了イベントがあるかを見る。
 *
 * 開始イベントが無いプロセスは Flowable で起動できない。
 * 終了イベントの欠落は動作はするものの、たいていは書き忘れなので弱い警告にしている。
 */
class BpmnProcessStructureInspection : BpmnInspectionBase() {

    override fun createVisitor(holder: ProblemsHolder): PsiElementVisitor = object : XmlElementVisitor() {
        override fun visitXmlTag(tag: XmlTag) {
            if (!BpmnTags.isContainer(tag)) return
            // アドホックサブプロセスは開始・終了イベントを持たなくてよい
            if (tag.localName == "adHocSubProcess") return

            val children = BpmnTags.flowNodes(tag)
            // 中身が空のコンテナは「書きかけ」なので別途報告しない
            if (children.isEmpty()) return

            val anchor = anchorFor(tag)
            val kinds = children.map { BpmnTags.kindOf(it) }

            if (BpmnElementKind.START_EVENT !in kinds) {
                holder.registerProblem(
                    anchor,
                    FlowableBundle.message("inspection.process.structure.no.start", tag.localName),
                    ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
                )
            }
            if (BpmnElementKind.END_EVENT !in kinds) {
                holder.registerProblem(
                    anchor,
                    FlowableBundle.message("inspection.process.structure.no.end", tag.localName),
                    ProblemHighlightType.WEAK_WARNING,
                )
            }
        }
    }
}
