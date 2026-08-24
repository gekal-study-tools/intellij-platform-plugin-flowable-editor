package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.bpmn.BpmnNamespaces
import com.github.gekal.flowableeditor.schema.BpmnResourceProvider
import junit.framework.TestCase

/**
 * 同梱 XSD が「登録したとおりのパス」で本当に読み出せるかを見る。
 *
 * プラットフォームは登録されたパスを [ClassLoader.getResource] にそのまま渡す。
 * [Class.getResource] と違い先頭のスラッシュを受け付けないため、うっかり付けると
 * インデックス作成時に PluginException になる。ここで固定しておく。
 */
class BpmnResourceProviderTest : TestCase() {

    private val classLoader = BpmnResourceProvider::class.java.classLoader

    fun `test registered paths are class loader paths, not absolute ones`() {
        for ((namespace, path) in BpmnResourceProvider.SCHEMAS) {
            assertFalse(
                "$namespace のパスは先頭のスラッシュを含んではいけない: $path",
                path.startsWith("/"),
            )
        }
    }

    fun `test every registered schema is bundled and readable`() {
        for ((namespace, path) in BpmnResourceProvider.SCHEMAS) {
            val url = classLoader.getResource(path)
            assertNotNull("$namespace 用の $path が見つからない", url)
        }
    }

    fun `test schemas cover the namespaces the plugin understands`() {
        val namespaces = BpmnResourceProvider.SCHEMAS.map { it.first }

        assertTrue(namespaces.contains(BpmnNamespaces.MODEL))
        assertTrue(namespaces.contains(BpmnNamespaces.BPMN_DI))
        assertTrue(namespaces.contains(BpmnNamespaces.DC))
        assertTrue(namespaces.contains(BpmnNamespaces.DI))
        assertTrue(namespaces.contains(BpmnNamespaces.FLOWABLE))
    }

    fun `test Semantic xsd is bundled for the relative include from BPMN20 xsd`() {
        // BPMN20.xsd が schemaLocation="Semantic.xsd" で相対参照する。
        // 登録はしないが、同梱されていないと解決できない。
        assertNotNull(classLoader.getResource("schemas/bpmn/Semantic.xsd"))
    }
}
