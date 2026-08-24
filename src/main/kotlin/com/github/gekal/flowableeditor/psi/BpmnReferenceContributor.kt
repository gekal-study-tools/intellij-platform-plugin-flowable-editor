package com.github.gekal.flowableeditor.psi

import com.github.gekal.flowableeditor.bpmn.BpmnFiles
import com.intellij.openapi.util.TextRange
import com.intellij.patterns.XmlPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.util.ProcessingContext

/**
 * BPMN ファイル内の id 参照属性に [BpmnIdReference] を登録する。
 */
class BpmnReferenceContributor : PsiReferenceContributor() {

    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            XmlPatterns.xmlAttributeValue(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(
                    element: PsiElement,
                    context: ProcessingContext,
                ): Array<PsiReference> {
                    val value = element as? XmlAttributeValue ?: return PsiReference.EMPTY_ARRAY
                    val attribute = value.parent as? XmlAttribute ?: return PsiReference.EMPTY_ARRAY
                    if (!BpmnReferenceAttributes.isReferenceAttribute(attribute.localName)) {
                        return PsiReference.EMPTY_ARRAY
                    }
                    if (!BpmnFiles.isBpmnFile(value.containingFile)) return PsiReference.EMPTY_ARRAY

                    val text = value.value
                    if (text.isBlank()) return PsiReference.EMPTY_ARRAY

                    // 引用符を除いた内側だけを参照範囲にする
                    val range = TextRange(1, value.textLength - 1)
                    if (range.length <= 0) return PsiReference.EMPTY_ARRAY
                    return arrayOf(BpmnIdReference(value, range))
                }
            },
        )
    }
}
