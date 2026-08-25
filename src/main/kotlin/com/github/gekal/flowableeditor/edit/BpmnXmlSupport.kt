package com.github.gekal.flowableeditor.edit

import com.github.gekal.flowableeditor.bpmn.BpmnNamespaces
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * BPMN の XML を書き換えるときの下ごしらえ。
 *
 * 名前空間の接頭辞はファイルによって違う (`bpmndi:` / `di:` / 既定名前空間) ので、
 * 既にある宣言を尊重し、無いときだけ足す。
 */
internal object BpmnXmlSupport {

    /** 名前空間が未宣言なら [preferred] を接頭辞として宣言し、実際に使う接頭辞を返す。 */
    fun ensurePrefix(root: XmlTag, namespace: String, preferred: String): String {
        root.getPrefixByNamespace(namespace)?.let { return it }
        root.setAttribute("xmlns:$preferred", namespace)
        return preferred
    }

    /** 接頭辞を付けたタグ名。既定名前空間なら接頭辞なし。 */
    fun qualify(prefix: String, localName: String): String =
        if (prefix.isEmpty()) localName else "$prefix:$localName"

    fun modelChildren(tag: XmlTag): List<XmlTag> =
        tag.subTags.filter { BpmnNamespaces.isModelNamespace(it.namespace) }

    /** ファイル内のすべてのタグから、指定 id を持つセマンティックモデルの要素を探す。 */
    fun findModelElement(file: XmlFile, id: String): XmlTag? {
        val root = file.rootTag ?: return null
        return findModelElement(root, id)
    }

    private fun findModelElement(tag: XmlTag, id: String): XmlTag? {
        for (child in tag.subTags) {
            if (BpmnNamespaces.isModelNamespace(child.namespace) &&
                child.getAttributeValue("id") == id
            ) {
                return child
            }
            findModelElement(child, id)?.let { return it }
        }
        return null
    }

    /** 要素を直接包んでいるコンテナ (process / subProcess など)。 */
    fun containerOf(element: XmlTag): XmlTag? = element.parentTag

    fun rootTag(file: XmlFile): XmlTag? = file.rootTag

    /** `bpmndi:BPMNDiagram` を返す。無ければ null。 */
    fun findDiagram(root: XmlTag): XmlTag? =
        root.subTags.firstOrNull { it.localName == "BPMNDiagram" }

    /** `bpmndi:BPMNPlane` を返す。無ければ null。 */
    fun findPlane(root: XmlTag): XmlTag? =
        findDiagram(root)?.subTags?.firstOrNull { it.localName == "BPMNPlane" }

    /** [bpmnElement] を指す BPMNShape。 */
    fun findShape(root: XmlTag, bpmnElement: String): XmlTag? =
        findPlane(root)?.subTags?.firstOrNull {
            it.localName == "BPMNShape" && it.getAttributeValue("bpmnElement") == bpmnElement
        }

    /** [bpmnElement] を指す BPMNEdge。 */
    fun findEdge(root: XmlTag, bpmnElement: String): XmlTag? =
        findPlane(root)?.subTags?.firstOrNull {
            it.localName == "BPMNEdge" && it.getAttributeValue("bpmnElement") == bpmnElement
        }

    /** 座標は小数を残さない。BPMN の図はピクセル単位で十分で、差分も読みやすくなる。 */
    fun formatCoordinate(value: Double): String = Math.round(value).toString()
}
