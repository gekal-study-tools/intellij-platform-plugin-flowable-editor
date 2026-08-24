package com.github.gekal.flowableeditor.actions

import com.github.gekal.flowableeditor.FlowableBundle
import com.github.gekal.flowableeditor.bpmn.FlowableIcons
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory

/**
 * 「New」メニューから Flowable のプロセス定義を作る。
 * テンプレートは `fileTemplates/internal/Flowable BPMN Process.bpmn20.xml.ft`。
 */
class NewBpmnFileAction : CreateFileFromTemplateAction(), DumbAware {

    override fun buildDialog(project: Project, directory: PsiDirectory, builder: CreateFileFromTemplateDialog.Builder) {
        builder
            .setTitle(FlowableBundle.message("action.new.bpmn.dialog.title"))
            .addKind(
                FlowableBundle.message("action.new.bpmn.kind"),
                FlowableIcons.BpmnFile,
                TEMPLATE_NAME,
            )
    }

    override fun getActionName(directory: PsiDirectory?, newName: String, templateName: String?): String =
        FlowableBundle.message("action.new.bpmn.text")

    companion object {
        const val TEMPLATE_NAME = "Flowable BPMN Process"
    }
}
