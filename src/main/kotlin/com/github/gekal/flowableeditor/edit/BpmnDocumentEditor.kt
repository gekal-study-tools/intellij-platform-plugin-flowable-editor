package com.github.gekal.flowableeditor.edit

import com.github.gekal.flowableeditor.bpmn.BpmnNamespaces
import com.github.gekal.flowableeditor.edit.BpmnXmlSupport.ensurePrefix
import com.github.gekal.flowableeditor.edit.BpmnXmlSupport.findEdge
import com.github.gekal.flowableeditor.edit.BpmnXmlSupport.findModelElement
import com.github.gekal.flowableeditor.edit.BpmnXmlSupport.findPlane
import com.github.gekal.flowableeditor.edit.BpmnXmlSupport.findShape
import com.github.gekal.flowableeditor.edit.BpmnXmlSupport.formatCoordinate
import com.github.gekal.flowableeditor.edit.BpmnXmlSupport.qualify
import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.github.gekal.flowableeditor.model.BpmnPoint
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.XmlElementFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

/**
 * 図の編集を XML に書き戻す。
 *
 * すべての変更は [WriteCommandAction] の中で行うので、IDE の取り消し/やり直しが
 * そのまま効き、XML が常に唯一の正となる。図はドキュメントの変更を受けて
 * 組み直されるため、ここでは描画のことを気にしなくてよい。
 *
 * 座標を扱う操作の前には [ensureDiagramInterchange] が走る。BPMNDI を持たない
 * 定義は座標の置き場所が無いので、自動レイアウトの結果を DI として書き出してから
 * 編集を始める。
 */
object BpmnDocumentEditor {

    /** 取り消し履歴でまとめる単位。連続したドラッグを 1 つにまとめたい場合に使う。 */
    private const val UNDO_GROUP = "flowable.bpmn.diagram.edit"

    // --- 幾何 ----------------------------------------------------------------

    /** 図形の位置と大きさを書き戻す。 */
    fun setBounds(
        project: Project,
        file: XmlFile,
        diagram: BpmnDiagram,
        bounds: Map<String, BpmnBounds>,
        commandName: String,
    ) {
        if (bounds.isEmpty()) return
        runCommand(project, file, commandName) {
            val root = file.rootTag ?: return@runCommand
            ensureDiagramInterchange(root, diagram)
            for ((id, box) in bounds) {
                writeShapeBounds(root, id, box)
            }
        }
    }

    // --- 名前 ----------------------------------------------------------------

    /** `name` 属性を書き換える。空文字なら属性ごと外す。 */
    fun setName(project: Project, file: XmlFile, elementId: String, name: String, commandName: String) {
        runCommand(project, file, commandName) {
            val element = findModelElement(file, elementId) ?: return@runCommand
            if (name.isBlank()) {
                element.getAttribute("name")?.delete()
            } else {
                element.setAttribute("name", name)
            }
        }
    }

    // --- 追加 ----------------------------------------------------------------

    /**
     * 新しい要素をコンテナに追加し、その id を返す。
     * [containerId] が null か見つからない場合は最初のプロセスに入れる。
     */
    fun createElement(
        project: Project,
        file: XmlFile,
        diagram: BpmnDiagram,
        item: BpmnPaletteItem,
        bounds: BpmnBounds,
        containerId: String?,
        commandName: String,
    ): String? {
        var created: String? = null
        runCommand(project, file, commandName) {
            val root = file.rootTag ?: return@runCommand
            val container = resolveContainer(file, root, containerId) ?: return@runCommand

            val id = BpmnIdGenerator.generate(file, item.tagName)
            val body = item.eventDefinition?.let { "<${childPrefix(container)}$it/>" }
            val element = container.createChildTag(item.tagName, container.namespace, body, false)
            val added = container.addSubTag(element, false)
            added.setAttribute("id", id)

            ensureDiagramInterchange(root, diagram)
            writeShapeBounds(root, id, bounds)
            reformat(project, added)
            created = id
        }
        return created
    }

    /**
     * 2 つの要素を sequenceFlow で結び、その id を返す。
     * 同じ組をすでに結んでいる場合は何もしない。
     */
    fun connect(
        project: Project,
        file: XmlFile,
        diagram: BpmnDiagram,
        sourceId: String,
        targetId: String,
        commandName: String,
    ): String? {
        if (sourceId == targetId) return null
        var created: String? = null
        runCommand(project, file, commandName) {
            val root = file.rootTag ?: return@runCommand
            val source = findModelElement(file, sourceId) ?: return@runCommand
            findModelElement(file, targetId) ?: return@runCommand
            if (hasFlowBetween(file, sourceId, targetId)) return@runCommand

            // 分岐元と同じコンテナに置く。境界イベント発の線は貼り付け先の親に置く。
            val container = source.parentTag ?: return@runCommand
            val id = BpmnIdGenerator.generate(file, "flow")
            val flow = container.createChildTag("sequenceFlow", container.namespace, null, false)
            val added = container.addSubTag(flow, false)
            added.setAttribute("id", id)
            added.setAttribute("sourceRef", sourceId)
            added.setAttribute("targetRef", targetId)

            ensureDiagramInterchange(root, diagram)
            writeEdgeWaypoints(root, id, connectionWaypoints(diagram, sourceId, targetId))
            reformat(project, added)
            created = id
        }
        return created
    }

    // --- 削除 ----------------------------------------------------------------

    /**
     * 要素を消す。ぶら下がるものも一緒に片付けないと壊れた定義になるので、
     * 接続するシーケンスフロー・境界イベント・対応する図形情報もまとめて消す。
     */
    fun delete(project: Project, file: XmlFile, elementIds: Collection<String>, commandName: String) {
        if (elementIds.isEmpty()) return
        runCommand(project, file, commandName) {
            val root = file.rootTag ?: return@runCommand

            // 消す対象を広げる: 指定要素 + 張り付いた境界イベント + 接続するフロー
            val doomed = LinkedHashSet(elementIds)
            for (id in elementIds) {
                collectAttachedEvents(file, id).forEach { doomed += it }
            }
            for (id in doomed.toList()) {
                collectConnectedFlows(file, id).forEach { doomed += it }
            }

            for (id in doomed) {
                findShape(root, id)?.delete()
                findEdge(root, id)?.delete()
                findModelElement(file, id)?.delete()
            }
        }
    }

    // --- BPMNDI ---------------------------------------------------------------

    /**
     * BPMNDI が無ければ、いま表示している座標をそのまま図形情報として書き出す。
     *
     * 自動レイアウトで見えていた図がファイルに固定されるので、以降の移動は
     * 単なる座標の更新になる。すでに DI があるときは何もしない。
     */
    private fun ensureDiagramInterchange(root: XmlTag, diagram: BpmnDiagram) {
        if (findPlane(root) != null) return

        val diPrefix = ensurePrefix(root, BpmnNamespaces.BPMN_DI, "bpmndi")
        ensurePrefix(root, BpmnNamespaces.DC, "omgdc")
        ensurePrefix(root, BpmnNamespaces.DI, "omgdi")

        val processId = diagram.processNames.firstOrNull()?.let { _ ->
            root.subTags.firstOrNull { it.localName == "process" }?.getAttributeValue("id")
        }.orEmpty()

        val diagramTag = root.createChildTag("BPMNDiagram", BpmnNamespaces.BPMN_DI, null, false)
        val addedDiagram = root.addSubTag(diagramTag, false)
        addedDiagram.setAttribute("id", "BPMNDiagram_1")

        val plane = addedDiagram.createChildTag("BPMNPlane", BpmnNamespaces.BPMN_DI, null, false)
        val addedPlane = addedDiagram.addSubTag(plane, false)
        addedPlane.setAttribute("id", "BPMNPlane_1")
        if (processId.isNotEmpty()) addedPlane.setAttribute("bpmnElement", processId)

        // 図形が先、線が後。BPMNDI の慣習であり、読むときも追いやすい。
        for (node in diagram.nodes) {
            val bounds = node.bounds ?: continue
            if (node.id.isEmpty()) continue
            appendShape(addedPlane, diPrefix, node.id, bounds)
        }
        for (edge in diagram.edges) {
            if (edge.id.isEmpty() || edge.waypoints.size < 2) continue
            appendEdge(addedPlane, diPrefix, edge.id, edge.waypoints)
        }
    }

    private fun appendShape(plane: XmlTag, diPrefix: String, elementId: String, bounds: BpmnBounds) {
        val dcPrefix = plane.getPrefixByNamespace(BpmnNamespaces.DC).orEmpty()
        val text = buildString {
            append("<").append(qualify(diPrefix, "BPMNShape"))
            append(" id=\"").append(elementId).append("_di\"")
            append(" bpmnElement=\"").append(elementId).append("\">")
            append(boundsText(dcPrefix, bounds))
            append("</").append(qualify(diPrefix, "BPMNShape")).append(">")
        }
        plane.add(createTag(plane, text))
    }

    private fun appendEdge(plane: XmlTag, diPrefix: String, elementId: String, waypoints: List<BpmnPoint>) {
        val diPointPrefix = plane.getPrefixByNamespace(BpmnNamespaces.DI).orEmpty()
        val text = buildString {
            append("<").append(qualify(diPrefix, "BPMNEdge"))
            append(" id=\"").append(elementId).append("_di\"")
            append(" bpmnElement=\"").append(elementId).append("\">")
            waypoints.forEach { append(waypointText(diPointPrefix, it)) }
            append("</").append(qualify(diPrefix, "BPMNEdge")).append(">")
        }
        plane.add(createTag(plane, text))
    }

    private fun boundsText(dcPrefix: String, bounds: BpmnBounds): String = buildString {
        append("<").append(qualify(dcPrefix, "Bounds"))
        append(" x=\"").append(formatCoordinate(bounds.x)).append("\"")
        append(" y=\"").append(formatCoordinate(bounds.y)).append("\"")
        append(" width=\"").append(formatCoordinate(bounds.width)).append("\"")
        append(" height=\"").append(formatCoordinate(bounds.height)).append("\"/>")
    }

    private fun waypointText(diPrefix: String, point: BpmnPoint): String =
        "<${qualify(diPrefix, "waypoint")} x=\"${formatCoordinate(point.x)}\" " +
            "y=\"${formatCoordinate(point.y)}\"/>"

    /** 図形の座標を書く。BPMNShape が無ければ作る。 */
    private fun writeShapeBounds(root: XmlTag, elementId: String, bounds: BpmnBounds) {
        val plane = findPlane(root) ?: return
        val diPrefix = root.getPrefixByNamespace(BpmnNamespaces.BPMN_DI).orEmpty()
        val shape = findShape(root, elementId) ?: run {
            appendShape(plane, diPrefix, elementId, bounds)
            return
        }

        val dcPrefix = root.getPrefixByNamespace(BpmnNamespaces.DC).orEmpty()
        val boundsTag = shape.subTags.firstOrNull { it.localName == "Bounds" }
            ?: shape.add(createTag(shape, boundsText(dcPrefix, bounds))) as? XmlTag
            ?: return
        boundsTag.setAttribute("x", formatCoordinate(bounds.x))
        boundsTag.setAttribute("y", formatCoordinate(bounds.y))
        boundsTag.setAttribute("width", formatCoordinate(bounds.width))
        boundsTag.setAttribute("height", formatCoordinate(bounds.height))
    }

    private fun writeEdgeWaypoints(root: XmlTag, elementId: String, waypoints: List<BpmnPoint>) {
        val plane = findPlane(root) ?: return
        val diPrefix = root.getPrefixByNamespace(BpmnNamespaces.BPMN_DI).orEmpty()
        findEdge(root, elementId)?.delete()
        appendEdge(plane, diPrefix, elementId, waypoints)
    }

    // --- 補助 ----------------------------------------------------------------

    private fun createTag(context: XmlTag, text: String): XmlTag =
        XmlElementFactory.getInstance(context.project).createTagFromText(text, context.language)

    private fun childPrefix(container: XmlTag): String {
        val prefix = container.namespacePrefix
        return if (prefix.isEmpty()) "" else "$prefix:"
    }

    private fun resolveContainer(file: XmlFile, root: XmlTag, containerId: String?): XmlTag? {
        containerId?.let { id ->
            findModelElement(file, id)?.let { candidate ->
                if (candidate.localName == "process" || BpmnElementKind.fromTagName(candidate.localName).isSubProcess) {
                    return candidate
                }
            }
        }
        return root.subTags.firstOrNull { it.localName == "process" }
    }

    private fun hasFlowBetween(file: XmlFile, sourceId: String, targetId: String): Boolean {
        val root = file.rootTag ?: return false
        return collectFlows(root).any {
            it.getAttributeValue("sourceRef") == sourceId && it.getAttributeValue("targetRef") == targetId
        }
    }

    private fun collectFlows(tag: XmlTag): List<XmlTag> {
        val result = mutableListOf<XmlTag>()
        for (child in tag.subTags) {
            if (child.localName == "sequenceFlow" && BpmnNamespaces.isModelNamespace(child.namespace)) {
                result += child
            }
            result += collectFlows(child)
        }
        return result
    }

    private fun collectConnectedFlows(file: XmlFile, elementId: String): List<String> {
        val root = file.rootTag ?: return emptyList()
        return collectFlows(root)
            .filter {
                it.getAttributeValue("sourceRef") == elementId || it.getAttributeValue("targetRef") == elementId
            }
            .mapNotNull { it.getAttributeValue("id") }
    }

    private fun collectAttachedEvents(file: XmlFile, hostId: String): List<String> {
        val root = file.rootTag ?: return emptyList()
        val result = mutableListOf<String>()
        fun walk(tag: XmlTag) {
            for (child in tag.subTags) {
                if (child.localName == "boundaryEvent" && child.getAttributeValue("attachedToRef") == hostId) {
                    child.getAttributeValue("id")?.let { result += it }
                }
                walk(child)
            }
        }
        walk(root)
        return result
    }

    /** 新しい線の折れ点。図形の縁どうしを結ぶ。 */
    private fun connectionWaypoints(diagram: BpmnDiagram, sourceId: String, targetId: String): List<BpmnPoint> {
        val source = diagram.nodesById[sourceId]?.bounds
        val target = diagram.nodesById[targetId]?.bounds
        if (source == null || target == null) return emptyList()

        return if (target.x >= source.right) {
            listOf(BpmnPoint(source.right, source.centerY), BpmnPoint(target.x, target.centerY))
        } else if (target.right <= source.x) {
            listOf(BpmnPoint(source.x, source.centerY), BpmnPoint(target.right, target.centerY))
        } else if (target.y >= source.bottom) {
            listOf(BpmnPoint(source.centerX, source.bottom), BpmnPoint(target.centerX, target.y))
        } else {
            listOf(BpmnPoint(source.centerX, source.y), BpmnPoint(target.centerX, target.bottom))
        }
    }

    private fun reformat(project: Project, element: PsiElement) {
        CodeStyleManager.getInstance(project).reformat(element)
    }

    private fun runCommand(project: Project, file: XmlFile, commandName: String, action: () -> Unit) {
        WriteCommandAction.writeCommandAction(project, file)
            .withName(commandName)
            .withGroupId(UNDO_GROUP)
            .run<RuntimeException> { action() }
    }
}
