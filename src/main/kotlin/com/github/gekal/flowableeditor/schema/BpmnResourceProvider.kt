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
        registrar.addStdResource(BpmnNamespaces.MODEL, "/schemas/bpmn/BPMN20.xsd", javaClass.classLoader)
        registrar.addStdResource(BpmnNamespaces.BPMN_DI, "/schemas/bpmn/BPMNDI.xsd", javaClass.classLoader)
        registrar.addStdResource(BpmnNamespaces.DC, "/schemas/bpmn/DC.xsd", javaClass.classLoader)
        registrar.addStdResource(BpmnNamespaces.DI, "/schemas/bpmn/DI.xsd", javaClass.classLoader)
        registrar.addStdResource(BpmnNamespaces.FLOWABLE, "/schemas/bpmn/flowable-bpmn-extensions.xsd", javaClass.classLoader)

        // schemaLocation に直接書かれることのある URL も同じ XSD に向ける
        registrar.addStdResource(
            "http://www.omg.org/spec/BPMN/20100524/MODEL BPMN20.xsd",
            "/schemas/bpmn/BPMN20.xsd",
            javaClass.classLoader,
        )
    }
}
