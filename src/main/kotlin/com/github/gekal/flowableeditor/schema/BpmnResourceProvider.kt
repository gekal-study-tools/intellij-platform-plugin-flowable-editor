package com.github.gekal.flowableeditor.schema

import com.github.gekal.flowableeditor.bpmn.BpmnNamespaces
import com.intellij.javaee.ResourceRegistrar
import com.intellij.javaee.StandardResourceProvider

/**
 * BPMN 2.0 と Flowable 拡張の XSD をプラグインに同梱し、名前空間 URI から
 * ローカルのファイルへ解決させる。
 *
 * これにより、ネットワークに出ずにタグ・属性の補完とスキーマ検証が効く。
 * XSD は flowable-bpmn-converter (Apache License 2.0) に含まれるものと同じ。
 */
class BpmnResourceProvider : StandardResourceProvider {

    override fun registerResources(registrar: ResourceRegistrar) {
        for ((namespace, path) in SCHEMAS) {
            registrar.addStdResource(namespace, path, javaClass.classLoader)
        }
    }

    companion object {
        /**
         * 同梱 XSD の置き場所。
         *
         * 先頭にスラッシュを付けないこと。プラットフォームはここで渡したパスを
         * [ClassLoader.getResource] へそのまま渡すが、[Class.getResource] と違って
         * 絶対パス表記を受け付けず、インデックス作成時にリソースを見失う。
         */
        private const val SCHEMA_DIRECTORY = "schemas/bpmn"

        /**
         * 名前空間 URI (と schemaLocation に直接書かれがちな URL) から同梱 XSD への対応。
         *
         * Semantic.xsd は BPMN20.xsd が相対パスで include するため、個別の登録は要らない。
         */
        val SCHEMAS: List<Pair<String, String>> = listOf(
            BpmnNamespaces.MODEL to "BPMN20.xsd",
            BpmnNamespaces.BPMN_DI to "BPMNDI.xsd",
            BpmnNamespaces.DC to "DC.xsd",
            BpmnNamespaces.DI to "DI.xsd",
            BpmnNamespaces.FLOWABLE to "flowable-bpmn-extensions.xsd",
            "${BpmnNamespaces.MODEL} BPMN20.xsd" to "BPMN20.xsd",
        ).map { (namespace, fileName) -> namespace to "$SCHEMA_DIRECTORY/$fileName" }
    }
}
