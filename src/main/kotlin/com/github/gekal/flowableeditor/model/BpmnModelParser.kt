package com.github.gekal.flowableeditor.model

import com.github.gekal.flowableeditor.bpmn.BpmnNamespaces
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * BPMN XML の PSI から描画用モデルを組み立てる。
 *
 * 座標は BPMNDI (`bpmndi:BPMNDiagram`) があればそれをそのまま使い、
 * 無ければ [BpmnAutoLayout] が自動配置する。Flowable では図情報を持たない
 * 手書きの定義ファイルも珍しくないため、後者は実用上重要。
 *
 * 読み取り専用のパースなので read action の中から呼ぶこと。
 */
object BpmnModelParser {

    fun parse(xmlFile: XmlFile): BpmnDiagram {
        val root = xmlFile.rootTag ?: return BpmnDiagram.EMPTY
        if (root.localName != "definitions" || !BpmnNamespaces.isModelNamespace(root.namespace)) {
            return BpmnDiagram.EMPTY
        }

        val nodes = mutableListOf<BpmnNode>()
        val rawEdges = mutableListOf<BpmnEdge>()
        val defaultFlowIds = mutableSetOf<String>()
        val processNames = mutableListOf<String>()

        for (child in root.subTags) {
            if (!BpmnNamespaces.isModelNamespace(child.namespace)) continue
            when (child.localName) {
                "process" -> {
                    val id = child.getAttributeValue("id").orEmpty()
                    processNames += child.getAttributeValue("name")?.takeIf { it.isNotBlank() } ?: id
                    parseContainer(child, id, nodes, rawEdges, defaultFlowIds)
                }

                "collaboration" -> parseCollaboration(child, nodes, rawEdges)
            }
        }

        val edges = rawEdges.map { edge ->
            if (edge.id.isNotEmpty() && edge.id in defaultFlowIds) edge.copy(isDefaultFlow = true) else edge
        }

        val diagram = BpmnDiagram(nodes, edges, hasDiagramInterchange = false, processNames = processNames)
        val hasDi = applyDiagramInterchange(root, diagram)

        if (hasDi) {
            placeNodesWithoutBounds(diagram)
        } else {
            BpmnAutoLayout.layout(diagram)
        }
        BpmnEdgeRouter.routeMissingEdges(diagram)

        return BpmnDiagram(nodes, edges, hasDiagramInterchange = hasDi, processNames = processNames)
    }

    // --- セマンティックモデル -------------------------------------------------

    private fun parseContainer(
        container: XmlTag,
        containerId: String?,
        nodes: MutableList<BpmnNode>,
        edges: MutableList<BpmnEdge>,
        defaultFlowIds: MutableSet<String>,
    ) {
        for (child in container.subTags) {
            if (!BpmnNamespaces.isModelNamespace(child.namespace)) continue
            when (val local = child.localName) {
                "sequenceFlow" -> edges += toEdge(child, BpmnConnectionKind.SEQUENCE_FLOW)
                "association" -> edges += toEdge(child, BpmnConnectionKind.ASSOCIATION)
                "laneSet" -> parseLaneSet(child, containerId, nodes)
                else -> {
                    val kind = BpmnElementKind.fromTagName(local)
                    if (kind == BpmnElementKind.UNKNOWN || kind.isPoolOrLane) continue
                    val node = toNode(child, kind, containerId)
                    nodes += node
                    // ゲートウェイ/アクティビティの default 属性が指す分岐はデフォルトフロー
                    child.getAttributeValue("default")?.takeIf { it.isNotBlank() }?.let { defaultFlowIds += it }
                    if (kind.isSubProcess) {
                        parseContainer(child, node.id.ifEmpty { containerId }, nodes, edges, defaultFlowIds)
                    }
                }
            }
        }
    }

    private fun parseCollaboration(
        collaboration: XmlTag,
        nodes: MutableList<BpmnNode>,
        edges: MutableList<BpmnEdge>,
    ) {
        for (child in collaboration.subTags) {
            if (!BpmnNamespaces.isModelNamespace(child.namespace)) continue
            when (child.localName) {
                "participant" -> nodes += toNode(child, BpmnElementKind.POOL, null)
                "messageFlow" -> edges += toEdge(child, BpmnConnectionKind.MESSAGE_FLOW)
            }
        }
    }

    private fun parseLaneSet(laneSet: XmlTag, containerId: String?, nodes: MutableList<BpmnNode>) {
        for (lane in laneSet.subTags) {
            if (lane.localName != "lane") continue
            nodes += toNode(lane, BpmnElementKind.LANE, containerId)
            // ネストしたレーンセットも拾う
            lane.subTags.filter { it.localName == "laneSet" }
                .forEach { parseLaneSet(it, containerId, nodes) }
        }
    }

    private fun toNode(tag: XmlTag, kind: BpmnElementKind, containerId: String?): BpmnNode {
        val range = tag.textRange
        return BpmnNode(
            id = tag.getAttributeValue("id").orEmpty(),
            name = displayNameOf(tag, kind),
            kind = kind,
            tagName = tag.localName,
            eventDefinition = findEventDefinition(tag),
            attachedToRef = tag.getAttributeValue("attachedToRef"),
            parentId = containerId,
            isExpanded = true,
            isMultiInstance = findSubTag(tag, "multiInstanceLoopCharacteristics") != null,
            isSequentialMultiInstance =
                findSubTag(tag, "multiInstanceLoopCharacteristics")?.getAttributeValue("isSequential") == "true",
            isInterrupting = isInterrupting(tag, kind),
            isForCompensation = tag.getAttributeValue("isForCompensation") == "true",
            textOffset = range.startOffset,
            textLength = range.length,
        )
    }

    private fun toEdge(tag: XmlTag, kind: BpmnConnectionKind): BpmnEdge {
        val range = tag.textRange
        val condition = findSubTag(tag, "conditionExpression")
        return BpmnEdge(
            id = tag.getAttributeValue("id").orEmpty(),
            name = tag.getAttributeValue("name"),
            sourceRef = tag.getAttributeValue("sourceRef"),
            targetRef = tag.getAttributeValue("targetRef"),
            kind = kind,
            hasCondition = condition != null && condition.value.trimmedText.isNotBlank(),
            textOffset = range.startOffset,
            textLength = range.length,
        )
    }

    private fun isInterrupting(tag: XmlTag, kind: BpmnElementKind): Boolean = when (kind) {
        // 境界イベントは cancelActivity="false" で非割り込みになる
        BpmnElementKind.BOUNDARY_EVENT -> tag.getAttributeValue("cancelActivity") != "false"
        // イベントサブプロセスの開始イベントは isInterrupting="false" で非割り込み
        BpmnElementKind.START_EVENT -> tag.getAttributeValue("isInterrupting") != "false"
        else -> true
    }

    /** テキスト注記は name 属性ではなく `<text>` 子要素に本文を持つ。 */
    private fun displayNameOf(tag: XmlTag, kind: BpmnElementKind): String? {
        tag.getAttributeValue("name")?.takeIf { it.isNotBlank() }?.let { return it }
        if (kind == BpmnElementKind.TEXT_ANNOTATION) {
            return findSubTag(tag, "text")?.value?.trimmedText?.takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun findEventDefinition(tag: XmlTag): String? =
        tag.subTags.firstOrNull { it.localName.endsWith("EventDefinition") }
            ?.localName
            ?.removeSuffix("EventDefinition")
            ?.takeIf { it.isNotEmpty() }

    private fun findSubTag(tag: XmlTag, localName: String): XmlTag? =
        tag.subTags.firstOrNull { it.localName == localName }

    // --- Diagram Interchange -------------------------------------------------

    /** BPMNDI から座標を取り込む。1 つでも図形が見つかれば true。 */
    private fun applyDiagramInterchange(root: XmlTag, diagram: BpmnDiagram): Boolean {
        val shapes = mutableMapOf<String, BpmnBounds>()
        val waypoints = mutableMapOf<String, List<BpmnPoint>>()

        for (bpmnDiagram in root.subTags.filter { it.localName == "BPMNDiagram" }) {
            for (plane in bpmnDiagram.subTags.filter { it.localName == "BPMNPlane" }) {
                for (element in plane.subTags) {
                    val target = element.getAttributeValue("bpmnElement")?.takeIf { it.isNotBlank() } ?: continue
                    when (element.localName) {
                        "BPMNShape" -> readBounds(element)?.let { shapes[target] = it }
                        "BPMNEdge" -> readWaypoints(element).takeIf { it.size >= 2 }?.let { waypoints[target] = it }
                    }
                }
            }
        }

        if (shapes.isEmpty() && waypoints.isEmpty()) return false

        diagram.nodes.forEach { node -> shapes[node.id]?.let { node.bounds = it } }
        diagram.edges.forEach { edge -> waypoints[edge.id]?.let { edge.waypoints = it } }
        return shapes.isNotEmpty()
    }

    private fun readBounds(shape: XmlTag): BpmnBounds? {
        val bounds = shape.subTags.firstOrNull { it.localName == "Bounds" } ?: return null
        val width = bounds.doubleAttr("width") ?: return null
        val height = bounds.doubleAttr("height") ?: return null
        return BpmnBounds(bounds.doubleAttr("x") ?: 0.0, bounds.doubleAttr("y") ?: 0.0, width, height)
    }

    private fun readWaypoints(edge: XmlTag): List<BpmnPoint> =
        edge.subTags.filter { it.localName == "waypoint" }
            .mapNotNull { wp ->
                val x = wp.doubleAttr("x") ?: return@mapNotNull null
                val y = wp.doubleAttr("y") ?: return@mapNotNull null
                BpmnPoint(x, y)
            }

    private fun XmlTag.doubleAttr(name: String): Double? =
        getAttributeValue(name)?.trim()?.toDoubleOrNull()

    /**
     * BPMNDI はあるがこのノードの図形だけ欠けている、という壊れかけの
     * ファイルでも要素を見失わないよう、図の下に一列で並べる。
     */
    private fun placeNodesWithoutBounds(diagram: BpmnDiagram) {
        val orphans = diagram.nodes.filter { it.bounds == null && !it.kind.isPoolOrLane }
        if (orphans.isEmpty()) return

        val extent = diagram.extent()
        var x = extent?.x ?: 0.0
        val y = (extent?.maxY ?: 0.0) + 80.0
        for (node in orphans) {
            val size = BpmnAutoLayout.defaultSize(node.kind)
            node.bounds = BpmnBounds(x, y, size.first, size.second)
            x += size.first + 40.0
        }
    }
}
