package com.github.gekal.flowableeditor.psi

import com.github.gekal.flowableeditor.bpmn.FlowableIcons
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile

/**
 * `targetRef="approveTask"` のような属性値から、同じファイルの
 * `id="approveTask"` へ張る参照。
 *
 * 未解決時のハイライトは [com.github.gekal.flowableeditor.inspection.BpmnUnresolvedReferenceInspection]
 * が担当するため、この参照自体は soft にしてある。
 */
class BpmnIdReference(element: XmlAttributeValue, range: TextRange) :
    PsiReferenceBase<XmlAttributeValue>(element, range, true) {

    override fun resolve(): PsiElement? {
        val file = element.containingFile as? XmlFile ?: return null
        return BpmnIdIndex.resolve(file, value)
    }

    override fun getVariants(): Array<Any> {
        val file = element.containingFile as? XmlFile ?: return emptyArray()
        return BpmnIdIndex.ids(file).map { (id, declaration) ->
            val tagName = BpmnIdIndex.declaringTag(declaration)?.localName.orEmpty()
            val name = BpmnIdIndex.declaringTag(declaration)?.getAttributeValue("name")
            LookupElementBuilder.create(id)
                .withIcon(FlowableIcons.BpmnFile)
                .withTypeText(tagName, true)
                .withTailText(name?.takeIf { it.isNotBlank() }?.let { " ($it)" }, true)
        }.toTypedArray()
    }
}
