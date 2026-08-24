package com.github.gekal.flowableeditor.bpmn

/**
 * XML 名前空間の定数。BPMN 2.0 標準のものに加え、Flowable / Activiti の拡張名前空間を扱う。
 */
object BpmnNamespaces {
    /** BPMN 2.0 セマンティックモデル (definitions, process, task, ...) */
    const val MODEL = "http://www.omg.org/spec/BPMN/20100524/MODEL"

    /** BPMN Diagram Interchange (BPMNDiagram, BPMNShape, BPMNEdge) */
    const val BPMN_DI = "http://www.omg.org/spec/BPMN/20100524/DI"

    /** Diagram Common (Bounds, Font) */
    const val DC = "http://www.omg.org/spec/DD/20100524/DC"

    /** Diagram Interchange (waypoint) */
    const val DI = "http://www.omg.org/spec/DD/20100524/DI"

    /** Flowable 拡張 (flowable:assignee など) */
    const val FLOWABLE = "http://flowable.org/bpmn"

    /** Activiti 拡張。Flowable は後方互換で受け付けるのでここでも認識する。 */
    const val ACTIVITI = "http://activiti.org/bpmn"

    /** 拡張属性の名前空間かどうか。 */
    fun isExtensionNamespace(namespace: String): Boolean =
        namespace == FLOWABLE || namespace == ACTIVITI

    /**
     * BPMN セマンティックモデルの名前空間として扱ってよいか。
     * 名前空間宣言のない手書きファイルも救うため、空文字列も許容する。
     */
    fun isModelNamespace(namespace: String): Boolean =
        namespace == MODEL || namespace.isEmpty()
}
