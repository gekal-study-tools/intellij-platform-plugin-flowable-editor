package com.github.gekal.flowableeditor.psi

import com.github.gekal.flowableeditor.bpmn.BpmnNamespaces
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * ファイル内の `id` 属性を集めた索引。
 * 参照解決・補完・重複検査が同じ結果を見るように、ここに集約している。
 * 結果は PSI の変更まで [CachedValuesManager] にキャッシュされる。
 */
object BpmnIdIndex {

    private val UNIQUE_IDS: Key<CachedValue<Map<String, XmlAttributeValue>>> =
        Key.create("flowable.bpmn.unique.ids")

    private val ALL_IDS: Key<CachedValue<Map<String, List<XmlAttributeValue>>>> =
        Key.create("flowable.bpmn.all.ids")

    /** id → その id を宣言している属性値 PSI。同じ id が複数あれば最初のもの。 */
    fun ids(file: XmlFile): Map<String, XmlAttributeValue> =
        CachedValuesManager.getManager(file.project).getCachedValue(
            file,
            UNIQUE_IDS,
            {
                val result = LinkedHashMap<String, XmlAttributeValue>()
                forEachIdAttribute(file) { id, value -> result.putIfAbsent(id, value) }
                CachedValueProvider.Result.create<Map<String, XmlAttributeValue>>(result, file)
            },
            false,
        )

    /** id → 宣言箇所すべて。重複検査で使う。 */
    fun duplicates(file: XmlFile): Map<String, List<XmlAttributeValue>> =
        CachedValuesManager.getManager(file.project).getCachedValue(
            file,
            ALL_IDS,
            {
                val result = LinkedHashMap<String, MutableList<XmlAttributeValue>>()
                forEachIdAttribute(file) { id, value -> result.getOrPut(id) { mutableListOf() }.add(value) }
                CachedValueProvider.Result.create<Map<String, List<XmlAttributeValue>>>(result, file)
            },
            false,
        )

    fun resolve(file: XmlFile, id: String): XmlAttributeValue? = ids(file)[id]

    /** id を宣言しているタグ。補完の表示に使う。 */
    fun declaringTag(value: XmlAttributeValue): XmlTag? =
        PsiTreeUtil.getParentOfType(value, XmlTag::class.java)

    private inline fun forEachIdAttribute(file: XmlFile, action: (String, XmlAttributeValue) -> Unit) {
        for (tag in PsiTreeUtil.findChildrenOfType(file, XmlTag::class.java)) {
            // BPMNShape などの DI 要素も id を持つが、参照先として意味があるのは
            // セマンティックモデル側なのでそちらだけを索引する。
            if (!BpmnNamespaces.isModelNamespace(tag.namespace)) continue
            val value = tag.getAttribute("id")?.valueElement ?: continue
            val id = value.value
            if (id.isNotBlank()) action(id, value)
        }
    }
}
