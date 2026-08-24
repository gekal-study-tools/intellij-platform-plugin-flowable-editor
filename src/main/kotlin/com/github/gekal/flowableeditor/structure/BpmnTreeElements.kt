package com.github.gekal.flowableeditor.structure

import com.github.gekal.flowableeditor.bpmn.BpmnNamespaces
import com.github.gekal.flowableeditor.bpmn.FlowableIcons
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag
import javax.swing.Icon

/**
 * 構造ビューのノードの土台。
 *
 * プラットフォームの `PsiTreeElementBase` は実装パッケージにあり、IDE の
 * バージョンによって参照できないことがあるため、公開 API だけで組み立てている。
 */
abstract class BpmnTreeElement<T : PsiElement>(private val psiElement: T) :
    StructureViewTreeElement,
    ItemPresentation {

    /** 無効化された PSI を掴んだままにしないよう、都度確認してから返す。 */
    protected val element: T?
        get() = psiElement.takeIf { it.isValid }

    override fun getValue(): Any = psiElement

    override fun getPresentation(): ItemPresentation = this

    override fun getChildren(): Array<TreeElement> = childElements().toTypedArray()

    protected abstract fun childElements(): List<TreeElement>

    override fun navigate(requestFocus: Boolean) {
        (element as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (element as? Navigatable)?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean = (element as? Navigatable)?.canNavigateToSource() ?: false

    override fun getLocationString(): String? = null
}

/**
 * 構造ビューのルート。`definitions` 直下のうち意味のあるものだけを見せる。
 */
class BpmnFileTreeElement(file: XmlFile) : BpmnTreeElement<XmlFile>(file) {

    override fun childElements(): List<TreeElement> {
        val root = element?.rootTag ?: return emptyList()
        return root.subTags
            .filter { BpmnNamespaces.isModelNamespace(it.namespace) && it.localName in TOP_LEVEL_TAGS }
            .map { BpmnTagTreeElement(it) }
    }

    override fun getPresentableText(): String? = element?.name

    override fun getIcon(unused: Boolean): Icon = FlowableIcons.BpmnFile

    companion object {
        private val TOP_LEVEL_TAGS = setOf(
            "process", "collaboration", "signal", "message", "error", "escalation", "dataStore", "category",
        )
    }
}

/**
 * プロセスやフローノードのノード。
 *
 * シーケンスフローは図で見るほうが分かりやすく、数も多いので木には並べない。
 * (ツリーが線だらけになるのを避けるための意図的な省略)
 */
class BpmnTagTreeElement(tag: XmlTag) : BpmnTreeElement<XmlTag>(tag) {

    override fun childElements(): List<TreeElement> {
        val tag = element ?: return emptyList()
        return tag.subTags
            .filter { BpmnNamespaces.isModelNamespace(it.namespace) && isStructural(it) }
            .map { BpmnTagTreeElement(it) }
    }

    override fun getPresentableText(): String {
        val tag = element ?: return ""
        val name = tag.getAttributeValue("name")?.takeIf { it.isNotBlank() }
        val id = tag.getAttributeValue("id")?.takeIf { it.isNotBlank() }
        return name ?: id ?: tag.localName
    }

    override fun getLocationString(): String? {
        val tag = element ?: return null
        val kind = BpmnElementKind.fromTagName(tag.localName)
        val label = if (kind == BpmnElementKind.UNKNOWN) tag.localName else kind.displayName
        val id = tag.getAttributeValue("id")?.takeIf { it.isNotBlank() }
        // 名前を主表示にしたときだけ id を併記する
        val showId = id != null && !tag.getAttributeValue("name").isNullOrBlank()
        return if (showId) "$label - $id" else label
    }

    override fun getIcon(unused: Boolean): Icon {
        val tag = element ?: return FlowableIcons.BpmnFile
        if (tag.localName == "process" || tag.localName == "collaboration") return FlowableIcons.Process
        return FlowableIcons.forKind(BpmnElementKind.fromTagName(tag.localName))
    }

    private fun isStructural(tag: XmlTag): Boolean =
        BpmnElementKind.isFlowNodeTag(tag.localName) ||
            tag.localName == "participant" ||
            tag.localName == "lane" ||
            tag.localName == "laneSet"
}
