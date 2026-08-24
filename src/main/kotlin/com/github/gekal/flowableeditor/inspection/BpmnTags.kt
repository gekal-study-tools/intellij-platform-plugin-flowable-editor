package com.github.gekal.flowableeditor.inspection

import com.github.gekal.flowableeditor.bpmn.BpmnNamespaces
import com.github.gekal.flowableeditor.model.BpmnCategory
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.intellij.psi.xml.XmlTag

/** 検査から使う XML タグまわりの小道具。 */
internal object BpmnTags {

    val CONTAINER_TAGS = setOf("process", "subProcess", "transaction", "adHocSubProcess")

    fun isModelTag(tag: XmlTag, localName: String): Boolean =
        tag.localName == localName && isModelElement(tag)

    fun isModelElement(tag: XmlTag): Boolean = BpmnNamespaces.isModelNamespace(tag.namespace)

    fun isContainer(tag: XmlTag): Boolean =
        tag.localName in CONTAINER_TAGS && BpmnNamespaces.isModelNamespace(tag.namespace)

    /** イベントサブプロセス (triggeredByEvent="true") か。 */
    fun isEventSubProcess(tag: XmlTag): Boolean =
        tag.localName == "subProcess" && tag.getAttributeValue("triggeredByEvent") == "true"

    /** コンテナ直下のフローノード。 */
    fun flowNodes(container: XmlTag): List<XmlTag> =
        container.subTags.filter {
            BpmnNamespaces.isModelNamespace(it.namespace) && BpmnElementKind.isFlowNodeTag(it.localName)
        }

    /** コンテナ直下のシーケンスフロー。 */
    fun sequenceFlows(container: XmlTag): List<XmlTag> =
        container.subTags.filter { isModelTag(it, "sequenceFlow") }

    fun kindOf(tag: XmlTag): BpmnElementKind = BpmnElementKind.fromTagName(tag.localName)

    /** 接続の有無を問う意味がある要素か (注記やデータオブジェクトは対象外)。 */
    fun participatesInFlow(kind: BpmnElementKind): Boolean = when (kind.category) {
        BpmnCategory.EVENT, BpmnCategory.ACTIVITY, BpmnCategory.GATEWAY, BpmnCategory.CONTAINER -> true
        else -> false
    }

    /** `flowable:` / `activiti:` いずれかの名前空間を持つ拡張属性を取る。 */
    fun extensionAttribute(tag: XmlTag, localName: String): String? =
        tag.attributes.firstOrNull {
            it.localName == localName && BpmnNamespaces.isExtensionNamespace(it.namespace)
        }?.value?.takeIf { it.isNotBlank() }

    fun hasAnyExtensionAttribute(tag: XmlTag, vararg localNames: String): Boolean =
        localNames.any { extensionAttribute(tag, it) != null }
}
