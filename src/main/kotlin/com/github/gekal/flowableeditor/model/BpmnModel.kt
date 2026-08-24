package com.github.gekal.flowableeditor.model

import java.awt.geom.Rectangle2D

/** 図形の外接矩形 (BPMN DI の座標系。左上原点、Y 軸下向き)。 */
data class BpmnBounds(val x: Double, val y: Double, val width: Double, val height: Double) {
    val centerX: Double get() = x + width / 2
    val centerY: Double get() = y + height / 2
    val right: Double get() = x + width
    val bottom: Double get() = y + height

    fun contains(px: Double, py: Double): Boolean =
        px >= x && px <= right && py >= y && py <= bottom

    fun toRectangle2D(): Rectangle2D = Rectangle2D.Double(x, y, width, height)

    fun translate(dx: Double, dy: Double) = BpmnBounds(x + dx, y + dy, width, height)
}

data class BpmnPoint(val x: Double, val y: Double)

/**
 * 図形の下に書くラベルの想定サイズ。
 * 描画 ([com.github.gekal.flowableeditor.editor.BpmnDiagramPainter]) と
 * 図の外接矩形計算がずれるとラベルが切れるので、ここで共有する。
 */
object BpmnLabelMetrics {
    /** 折り返し幅。 */
    const val BELOW_WIDTH = 100.0

    /** 最大 2 行分の高さ。 */
    const val BELOW_HEIGHT = 26.0
}

/** 図形の大分類。描画の分岐と構造ビューのグルーピングに使う。 */
enum class BpmnCategory { EVENT, ACTIVITY, GATEWAY, CONTAINER, ARTIFACT, DATA, OTHER }

/**
 * 描画対象として扱う BPMN 要素の種類。
 * [tagName] は BPMN 名前空間でのローカル名。
 */
enum class BpmnElementKind(
    val tagName: String,
    val displayName: String,
    val category: BpmnCategory,
) {
    START_EVENT("startEvent", "Start Event", BpmnCategory.EVENT),
    END_EVENT("endEvent", "End Event", BpmnCategory.EVENT),
    INTERMEDIATE_CATCH_EVENT("intermediateCatchEvent", "Intermediate Catch Event", BpmnCategory.EVENT),
    INTERMEDIATE_THROW_EVENT("intermediateThrowEvent", "Intermediate Throw Event", BpmnCategory.EVENT),
    BOUNDARY_EVENT("boundaryEvent", "Boundary Event", BpmnCategory.EVENT),

    TASK("task", "Task", BpmnCategory.ACTIVITY),
    USER_TASK("userTask", "User Task", BpmnCategory.ACTIVITY),
    SERVICE_TASK("serviceTask", "Service Task", BpmnCategory.ACTIVITY),
    SCRIPT_TASK("scriptTask", "Script Task", BpmnCategory.ACTIVITY),
    SEND_TASK("sendTask", "Send Task", BpmnCategory.ACTIVITY),
    RECEIVE_TASK("receiveTask", "Receive Task", BpmnCategory.ACTIVITY),
    MANUAL_TASK("manualTask", "Manual Task", BpmnCategory.ACTIVITY),
    BUSINESS_RULE_TASK("businessRuleTask", "Business Rule Task", BpmnCategory.ACTIVITY),
    CALL_ACTIVITY("callActivity", "Call Activity", BpmnCategory.ACTIVITY),

    SUB_PROCESS("subProcess", "Sub Process", BpmnCategory.CONTAINER),
    TRANSACTION("transaction", "Transaction", BpmnCategory.CONTAINER),
    AD_HOC_SUB_PROCESS("adHocSubProcess", "Ad-Hoc Sub Process", BpmnCategory.CONTAINER),

    EXCLUSIVE_GATEWAY("exclusiveGateway", "Exclusive Gateway", BpmnCategory.GATEWAY),
    PARALLEL_GATEWAY("parallelGateway", "Parallel Gateway", BpmnCategory.GATEWAY),
    INCLUSIVE_GATEWAY("inclusiveGateway", "Inclusive Gateway", BpmnCategory.GATEWAY),
    EVENT_BASED_GATEWAY("eventBasedGateway", "Event Based Gateway", BpmnCategory.GATEWAY),
    COMPLEX_GATEWAY("complexGateway", "Complex Gateway", BpmnCategory.GATEWAY),

    TEXT_ANNOTATION("textAnnotation", "Text Annotation", BpmnCategory.ARTIFACT),
    GROUP("group", "Group", BpmnCategory.ARTIFACT),

    DATA_OBJECT_REFERENCE("dataObjectReference", "Data Object", BpmnCategory.DATA),
    DATA_STORE_REFERENCE("dataStoreReference", "Data Store", BpmnCategory.DATA),

    POOL("participant", "Pool", BpmnCategory.CONTAINER),
    LANE("lane", "Lane", BpmnCategory.CONTAINER),

    UNKNOWN("", "Element", BpmnCategory.OTHER),
    ;

    val isSubProcess: Boolean
        get() = this == SUB_PROCESS || this == TRANSACTION || this == AD_HOC_SUB_PROCESS

    val isPoolOrLane: Boolean
        get() = this == POOL || this == LANE

    companion object {
        private val BY_TAG = entries.filter { it.tagName.isNotEmpty() }.associateBy { it.tagName }

        fun fromTagName(tagName: String): BpmnElementKind = BY_TAG[tagName] ?: UNKNOWN

        /** 図に描くフローノードとして扱うタグか。 */
        fun isFlowNodeTag(tagName: String): Boolean {
            val kind = BY_TAG[tagName] ?: return false
            return !kind.isPoolOrLane
        }
    }
}

/** 図に描かれる 1 つのノード。 */
data class BpmnNode(
    val id: String,
    val name: String?,
    val kind: BpmnElementKind,
    val tagName: String,
    /** `timerEventDefinition` なら "timer"。イベントのマーカー描画に使う。 */
    val eventDefinition: String? = null,
    /** 境界イベントが張り付いているアクティビティの id。 */
    val attachedToRef: String? = null,
    /** 直近の親コンテナ (process / subProcess / lane) の id。トップレベルは null。 */
    val parentId: String? = null,
    val isExpanded: Boolean = true,
    val isMultiInstance: Boolean = false,
    val isSequentialMultiInstance: Boolean = false,
    val isInterrupting: Boolean = true,
    val isForCompensation: Boolean = false,
    /** XML 上のこの要素の範囲。図 → エディタのジャンプに使う。 */
    val textOffset: Int = 0,
    val textLength: Int = 0,
    var bounds: BpmnBounds? = null,
) {
    val displayLabel: String get() = name?.takeIf { it.isNotBlank() } ?: id

    /** イベントやゲートウェイは図形が小さいので、名前を図形の下に書く。 */
    val drawsLabelBelow: Boolean
        get() = kind.category == BpmnCategory.EVENT ||
            kind.category == BpmnCategory.GATEWAY ||
            kind.category == BpmnCategory.DATA

    fun containsOffset(offset: Int): Boolean =
        offset >= textOffset && offset <= textOffset + textLength
}

enum class BpmnConnectionKind { SEQUENCE_FLOW, MESSAGE_FLOW, ASSOCIATION }

/** 図に描かれる 1 本の接続線。 */
data class BpmnEdge(
    val id: String,
    val name: String?,
    val sourceRef: String?,
    val targetRef: String?,
    val kind: BpmnConnectionKind,
    val hasCondition: Boolean = false,
    val isDefaultFlow: Boolean = false,
    val textOffset: Int = 0,
    val textLength: Int = 0,
    var waypoints: List<BpmnPoint> = emptyList(),
) {
    val displayLabel: String get() = name?.takeIf { it.isNotBlank() } ?: id

    fun containsOffset(offset: Int): Boolean =
        offset >= textOffset && offset <= textOffset + textLength
}

/**
 * 1 ファイル分の図。
 *
 * [hasDiagramInterchange] が false のときは XML に BPMNDI が無く、
 * [BpmnAutoLayout] が座標を計算したことを意味する。
 */
class BpmnDiagram(
    val nodes: List<BpmnNode>,
    val edges: List<BpmnEdge>,
    val hasDiagramInterchange: Boolean,
    val processNames: List<String> = emptyList(),
) {
    val nodesById: Map<String, BpmnNode> = nodes.filter { it.id.isNotEmpty() }.associateBy { it.id }

    val isEmpty: Boolean get() = nodes.isEmpty() && edges.isEmpty()

    /** 全図形を含む矩形。空なら null。 */
    fun extent(): Rectangle2D? {
        var result: Rectangle2D? = null
        fun add(r: Rectangle2D) {
            result = result?.createUnion(r) ?: r
        }
        nodes.forEach { node ->
            val bounds = node.bounds ?: return@forEach
            add(bounds.toRectangle2D())
            // 図形の外側に書かれるラベルも図の一部として数える
            if (node.drawsLabelBelow && !node.name.isNullOrBlank()) {
                add(
                    Rectangle2D.Double(
                        bounds.centerX - BpmnLabelMetrics.BELOW_WIDTH / 2,
                        bounds.bottom,
                        BpmnLabelMetrics.BELOW_WIDTH,
                        BpmnLabelMetrics.BELOW_HEIGHT,
                    ),
                )
            }
        }
        edges.forEach { edge ->
            edge.waypoints.forEach { add(Rectangle2D.Double(it.x, it.y, 0.0, 0.0)) }
        }
        return result
    }

    /**
     * 指定座標にある要素。手前 (子・小さい図形) が優先されるよう、
     * 面積の小さいものから探す。
     */
    fun nodeAt(x: Double, y: Double): BpmnNode? =
        nodes.filter { it.bounds?.contains(x, y) == true }
            .minByOrNull { it.bounds!!.width * it.bounds!!.height }

    /** 指定座標の近くを通る接続線。[tolerance] は図の座標系での距離。 */
    fun edgeAt(x: Double, y: Double, tolerance: Double): BpmnEdge? =
        edges.minByOrNull { edge -> distanceToPolyline(edge.waypoints, x, y) }
            ?.takeIf { distanceToPolyline(it.waypoints, x, y) <= tolerance }

    private fun distanceToPolyline(points: List<BpmnPoint>, x: Double, y: Double): Double {
        if (points.size < 2) return Double.MAX_VALUE
        var min = Double.MAX_VALUE
        for (i in 0 until points.size - 1) {
            val d = java.awt.geom.Line2D.ptSegDist(
                points[i].x, points[i].y, points[i + 1].x, points[i + 1].y, x, y,
            )
            if (d < min) min = d
        }
        return min
    }

    /** キャレット位置に対応する要素を探す。範囲が最も狭いものを返す。 */
    fun elementAtOffset(offset: Int): Any? {
        val node = nodes.filter { it.containsOffset(offset) }.minByOrNull { it.textLength }
        val edge = edges.filter { it.containsOffset(offset) }.minByOrNull { it.textLength }
        return when {
            node == null -> edge
            edge == null -> node
            else -> if (node.textLength <= edge.textLength) node else edge
        }
    }

    companion object {
        val EMPTY = BpmnDiagram(emptyList(), emptyList(), hasDiagramInterchange = false)
    }
}
