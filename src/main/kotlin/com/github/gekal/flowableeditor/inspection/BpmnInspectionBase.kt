package com.github.gekal.flowableeditor.inspection

import com.github.gekal.flowableeditor.bpmn.BpmnFiles
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.xml.XmlTag
import com.intellij.xml.util.XmlTagUtil

/**
 * BPMN ファイル以外では何もしない、検査の共通土台。
 * 検査は XML 言語に対して登録されるので、この足切りが無いと
 * すべての XML でビジターが走ってしまう。
 */
abstract class BpmnInspectionBase : LocalInspectionTool() {

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        if (!BpmnFiles.isBpmnFile(holder.file)) return PsiElementVisitor.EMPTY_VISITOR
        return createVisitor(holder)
    }

    protected abstract fun createVisitor(holder: ProblemsHolder): PsiElementVisitor

    /** 問題を報告する位置。開始タグの名前部分だけを下線対象にする。 */
    protected fun anchorFor(tag: XmlTag): PsiElement = XmlTagUtil.getStartTagNameElement(tag) ?: tag
}
