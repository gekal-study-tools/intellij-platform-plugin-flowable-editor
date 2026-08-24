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
        // 名前空間 URI (と schemaLocation に直接書かれがちな URL) を同梱 XSD に向ける。
        // Semantic.xsd は BPMN20.xsd が相対パスで include するため、個別の登録は要らない。
        val schemas = listOf(
            BpmnNamespaces.MODEL to "BPMN20.xsd",
            BpmnNamespaces.BPMN_DI to "BPMNDI.xsd",
            BpmnNamespaces.DC to "DC.xsd",
            BpmnNamespaces.DI to "DI.xsd",
            BpmnNamespaces.FLOWABLE to "flowable-bpmn-extensions.xsd",
            "${BpmnNamespaces.MODEL} BPMN20.xsd" to "BPMN20.xsd",
        )

        for ((namespace, fileName) in schemas) {
            registrar.addStdResource(namespace, "$SCHEMA_DIRECTORY/$fileName", javaClass.classLoader)
        }
    }

    private companion object {
        const val SCHEMA_DIRECTORY = "/schemas/bpmn"
    }
}
