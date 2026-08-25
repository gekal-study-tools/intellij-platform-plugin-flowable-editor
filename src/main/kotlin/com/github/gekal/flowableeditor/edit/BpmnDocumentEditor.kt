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
import com.github.gekal.flowableeditor.model.BpmnCategory
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.github.gekal.flowableeditor.model.BpmnGeometry
import com.github.gekal.flowableeditor.model.BpmnNode
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

    /** プールの左端にある、名前を縦書きする帯の幅。 */
    private const val POOL_LABEL_BAND = 30.0

    /** プールが中身との間に取る余白。 */
    private const val POOL_PADDING = 20.0

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
        val expanded = withCarriedElements(diagram, bounds)
        runCommand(project, file, commandName) {
            val root = file.rootTag ?: return@runCommand
            ensureDiagramInterchange(root, diagram)
            for ((id, box) in expanded) {
                writeShapeBounds(root, id, box)
            }
            rerouteConnected(root, diagram, expanded)
            updateProcessMembership(root, file, diagram, expanded)
            updateLaneMembership(root, diagram, expanded)
        }
    }

    /**
     * プールをまたいで動かした要素を、そのプールのプロセスへ移す。
     *
     * BPMN ではプールはプロセスそのもの。図で別のプールへ移したのに定義上は
     * 元のプロセスに居る、という状態は実行時の意味が変わってしまう。
     * 移した結果プロセスをまたぐことになったシーケンスフローは、
     * メッセージフローに直す。プールをまたぐシーケンスフローは BPMN では作れない。
     */
    private fun updateProcessMembership(
        root: XmlTag,
        file: XmlFile,
        diagram: BpmnDiagram,
        movedBounds: Map<String, BpmnBounds>,
    ) {
        val pools = diagram.nodes.filter { it.kind == BpmnElementKind.POOL && it.id.isNotEmpty() }
        if (pools.isEmpty()) return

        var moved = false
        for ((id, bounds) in movedBounds) {
            val node = diagram.nodesById[id] ?: continue
            if (node.kind.isPoolOrLane) continue

            val poolId = pools.firstOrNull { it.bounds?.contains(bounds.centerX, bounds.centerY) == true }?.id
                ?: continue
            val targetProcessId = findModelElement(file, poolId)?.getAttributeValue("processRef") ?: continue
            val target = root.subTags.firstOrNull {
                it.localName == "process" && it.getAttributeValue("id") == targetProcessId
            } ?: continue

            val element = findModelElement(file, id) ?: continue
            if (processOf(element)?.getAttributeValue("id") == targetProcessId) continue

            // 元の場所から切り離して、移した先へ入れ直す
            val copy = element.copy() as XmlTag
            element.delete()
            target.addSubTag(copy, false)
            moved = true
        }

        if (moved) fixCrossProcessFlows(root, file)
    }

    /** その要素を含んでいるプロセス。 */
    private fun processOf(element: XmlTag): XmlTag? {
        var current: XmlTag? = element.parentTag
        while (current != null) {
            if (current.localName == "process") return current
            current = current.parentTag
        }
        return null
    }

    /**
     * プロセスをまたいでしまった線を直す。
     *
     * シーケンスフローは 1 つのプロセスの中でしか使えないので、またぐものは
     * メッセージフローにする。逆に、同じプロセスに収まったメッセージフローは
     * シーケンスフローに戻す。
     */
    private fun fixCrossProcessFlows(root: XmlTag, file: XmlFile) {
        val collaboration = root.subTags.firstOrNull { it.localName == "collaboration" } ?: return

        for (flow in collectFlows(root)) {
            if (flow.localName == "association") continue
            val sourceProcess = flow.getAttributeValue("sourceRef")
                ?.let { findModelElement(file, it) }
                ?.let { processOf(it)?.getAttributeValue("id") }
            val targetProcess = flow.getAttributeValue("targetRef")
                ?.let { findModelElement(file, it) }
                ?.let { processOf(it)?.getAttributeValue("id") }
            if (sourceProcess == null || targetProcess == null) continue

            val crosses = sourceProcess != targetProcess
            when {
                crosses && flow.localName == "sequenceFlow" ->
                    retagFlow(flow, "messageFlow", collaboration)

                !crosses && flow.localName == "messageFlow" -> {
                    val process = root.subTags.firstOrNull {
                        it.localName == "process" && it.getAttributeValue("id") == sourceProcess
                    } ?: continue
                    retagFlow(flow, "sequenceFlow", process)
                }
            }
        }
    }

    /**
     * 線の種類を変える。id は変えないので、対応する図形情報 (BPMNEdge) はそのまま使える。
     */
    private fun retagFlow(flow: XmlTag, tagName: String, container: XmlTag) {
        val id = flow.getAttributeValue("id") ?: return
        val sourceRef = flow.getAttributeValue("sourceRef") ?: return
        val targetRef = flow.getAttributeValue("targetRef") ?: return
        val name = flow.getAttributeValue("name")

        flow.delete()
        val replacement = container.createChildTag(tagName, container.namespace, null, false)
        val added = container.addSubTag(replacement, false)
        added.setAttribute("id", id)
        added.setAttribute("sourceRef", sourceRef)
        added.setAttribute("targetRef", targetRef)
        name?.let { added.setAttribute("name", it) }
    }

    /**
     * レーンをまたいで動かした要素の所属を書き換える。
     *
     * `flowNodeRef` を直さないと、図では別のレーンに見えるのに定義上は元のまま、
     * という食い違いが残る。Flowable が見るのは定義のほうなので、
     * 見た目だけ合っている状態はいちばん厄介。
     */
    private fun updateLaneMembership(
        root: XmlTag,
        diagram: BpmnDiagram,
        movedBounds: Map<String, BpmnBounds>,
    ) {
        val lanes = diagram.nodes.filter { it.kind == BpmnElementKind.LANE && it.id.isNotEmpty() }
        if (lanes.isEmpty()) return

        for ((id, bounds) in movedBounds) {
            val node = diagram.nodesById[id] ?: continue
            // レーンやプール自身、区画に属さないものは対象外
            if (node.kind.isPoolOrLane) continue
            if (node.kind == BpmnElementKind.BOUNDARY_EVENT) continue

            val laneId = lanes
                .firstOrNull { it.bounds?.contains(bounds.centerX, bounds.centerY) == true }
                ?.id
            moveFlowNodeRef(root, id, laneId)
        }
    }

    /** [elementId] の `flowNodeRef` を [laneId] のレーンだけに置く。 */
    private fun moveFlowNodeRef(root: XmlTag, elementId: String, laneId: String?) {
        for (lane in collectLanes(root)) {
            val ownsIt = lane.getAttributeValue("id") == laneId
            val existing = lane.subTags.firstOrNull {
                it.localName == "flowNodeRef" && it.value.trimmedText == elementId
            }
            when {
                ownsIt && existing == null -> {
                    val ref = lane.createChildTag("flowNodeRef", lane.namespace, elementId, false)
                    lane.addSubTag(ref, false)
                }

                !ownsIt && existing != null -> existing.delete()
            }
        }
    }

    /** そのタグの下にある、id を持つ要素すべて。 */
    private fun collectIds(tag: XmlTag): List<String> {
        val result = mutableListOf<String>()
        fun walk(current: XmlTag) {
            for (child in current.subTags) {
                child.getAttributeValue("id")?.takeIf { it.isNotBlank() }?.let { result += it }
                walk(child)
            }
        }
        walk(tag)
        return result
    }

    private fun collectLanes(tag: XmlTag): List<XmlTag> {
        val result = mutableListOf<XmlTag>()
        for (child in tag.subTags) {
            if (child.localName == "lane" && BpmnNamespaces.isModelNamespace(child.namespace)) {
                result += child
            }
            result += collectLanes(child)
        }
        return result
    }

    /**
     * 一緒に動かすものを足す。
     *
     * プールやレーン、サブプロセスを動かしたときに中身が置き去りになると、
     * 図形と区画の対応が壊れる。アクティビティに張り付いた境界イベントも同じ。
     * 大きさを変えただけのときは中身を動かさない。
     */
    private fun withCarriedElements(
        diagram: BpmnDiagram,
        bounds: Map<String, BpmnBounds>,
    ): Map<String, BpmnBounds> {
        if (bounds.size != 1) return bounds
        val (movedId, target) = bounds.entries.first()
        val original = diagram.nodesById[movedId]?.bounds ?: return bounds

        val dx = target.x - original.x
        val dy = target.y - original.y
        if (dx == 0.0 && dy == 0.0) return bounds
        // 大きさが変わっているなら移動ではない
        if (target.width != original.width || target.height != original.height) return bounds

        val moved = diagram.nodesById[movedId] ?: return bounds
        val result = LinkedHashMap(bounds)

        for (other in diagram.nodes) {
            if (other.id.isEmpty() || other.id == movedId) continue
            val otherBounds = other.bounds ?: continue

            val carried = when {
                // 区画の中にすっぽり入っているもの
                isContainer(moved) && encloses(original, otherBounds) -> true

                // 張り付いた境界イベント
                other.attachedToRef == movedId -> true

                else -> false
            }
            if (carried) result[other.id] = otherBounds.translate(dx, dy)
        }
        return result
    }

    private fun isContainer(node: BpmnNode): Boolean = node.kind.isPoolOrLane || node.kind.isSubProcess

    /** [outer] が [inner] を包んでいるか。縁ぴったりも含める。 */
    private fun encloses(outer: BpmnBounds, inner: BpmnBounds): Boolean =
        inner.x >= outer.x - 0.5 &&
            inner.y >= outer.y - 0.5 &&
            inner.right <= outer.right + 0.5 &&
            inner.bottom <= outer.bottom + 0.5

    /**
     * 動かした図形に繋がる線を引き直す。
     *
     * これをしないと線の端が元の位置に取り残され、図形との対応が読めなくなる。
     * 途中の折れ点は手で置かれたものかもしれないので残し、端だけを付け直す。
     */
    private fun rerouteConnected(
        root: XmlTag,
        diagram: BpmnDiagram,
        movedBounds: Map<String, BpmnBounds>,
    ) {
        // 動かした図形は新しい位置、それ以外は元の位置で考える
        fun boundsOf(id: String?): BpmnBounds? {
            if (id == null) return null
            return movedBounds[id] ?: diagram.nodesById[id]?.bounds
        }

        for (edge in diagram.edges) {
            if (edge.id.isEmpty()) continue
            val touchesMoved = edge.sourceRef in movedBounds.keys || edge.targetRef in movedBounds.keys
            if (!touchesMoved) continue

            val source = boundsOf(edge.sourceRef) ?: continue
            val target = boundsOf(edge.targetRef) ?: continue
            val rerouted = BpmnGeometry.reroute(edge.waypoints, source, target)
            if (rerouted.size >= 2) writeEdgeWaypoints(root, edge.id, rerouted)
        }
    }

    /**
     * 線の折れ点を書き戻す。
     *
     * 両端は図形の縁に合わせ直す。途中の点だけが手で置かれたものなので、
     * 渡された位置をそのまま使う。
     */
    fun setWaypoints(
        project: Project,
        file: XmlFile,
        diagram: BpmnDiagram,
        edgeId: String,
        waypoints: List<BpmnPoint>,
        commandName: String,
    ) {
        if (waypoints.size < 2) return
        runCommand(project, file, commandName) {
            val root = file.rootTag ?: return@runCommand
            ensureDiagramInterchange(root, diagram)

            val edge = diagram.edges.firstOrNull { it.id == edgeId } ?: return@runCommand
            val source = diagram.nodesById[edge.sourceRef]?.bounds
            val target = diagram.nodesById[edge.targetRef]?.bounds
            val docked = BpmnGeometry.reroute(waypoints, source, target)
            writeEdgeWaypoints(root, edgeId, docked)
        }
    }

    /**
     * 配置し直した結果をまとめて書き戻す。
     *
     * 図形の座標と線の折れ点を全部入れ替える。1 つの取り消し単位にまとめてあるので、
     * 気に入らなければ一度の取り消しで元に戻せる。
     */
    fun applyLayout(project: Project, file: XmlFile, diagram: BpmnDiagram, commandName: String) {
        runCommand(project, file, commandName) {
            val root = file.rootTag ?: return@runCommand
            ensureDiagramInterchange(root, diagram)
            for (node in diagram.nodes) {
                val bounds = node.bounds ?: continue
                if (node.id.isEmpty()) continue
                writeShapeBounds(root, node.id, bounds)
            }
            for (edge in diagram.edges) {
                if (edge.id.isEmpty() || edge.waypoints.size < 2) continue
                writeEdgeWaypoints(root, edge.id, edge.waypoints)
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
        attachToId: String? = null,
    ): String? {
        // 境界イベントは貼り付け先が要る。無ければ何もしない。
        if (item.attachesToActivity && attachToId == null) return null

        var created: String? = null
        runCommand(project, file, commandName) {
            val root = file.rootTag ?: return@runCommand
            // 境界イベントは貼り付け先の中ではなく、その隣に置く
            val host = attachToId?.let { findModelElement(file, it) }
            val container = if (item.attachesToActivity) {
                host?.parentTag ?: return@runCommand
            } else {
                resolveContainer(file, root, containerId) ?: return@runCommand
            }

            // プールとレーンは置き場所も作り方も違うので、専用の道筋に分ける
            if (item.kind == BpmnElementKind.POOL) {
                created = createPool(file, root, diagram, bounds)
                return@runCommand
            }
            if (item.kind == BpmnElementKind.LANE) {
                created = createLane(file, root, diagram, bounds, containerId)
                return@runCommand
            }

            val id = BpmnIdGenerator.generate(file, item.tagName)
            val body = item.eventDefinition?.let { "<${childPrefix(container)}$it/>" }
            val element = container.createChildTag(item.tagName, container.namespace, body, false)
            val added = container.addSubTag(element, false)
            added.setAttribute("id", id)
            if (item.attachesToActivity && attachToId != null) {
                added.setAttribute("attachedToRef", attachToId)
            }

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
            // 何と何を結ぶかで線の種類が決まる
            val kind = connectionKindFor(diagram, sourceId, targetId)
            val container = containerFor(root, source, kind) ?: return@runCommand
            val id = BpmnIdGenerator.generate(file, kind.tagName)
            val flow = container.createChildTag(kind.tagName, container.namespace, null, false)
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

    /**
     * プールを作る。
     *
     * BPMN では、プールは collaboration の participant であり、プロセスを 1 つ指す。
     * まだ collaboration が無ければ作り、最初のプールは既にあるプロセスを指す。
     * 2 つ目からは新しい空のプロセスを起こす。図の面 (BPMNPlane) は
     * collaboration を指すよう付け替える。プールを持つ図の約束に合わせるため。
     */
    private fun createPool(file: XmlFile, root: XmlTag, diagram: BpmnDiagram, bounds: BpmnBounds): String? {
        val existing = root.subTags.firstOrNull { it.localName == "collaboration" }
        val collaboration = existing ?: run {
            val tag = root.createChildTag("collaboration", root.namespace, null, false)
            val added = root.addSubTag(tag, true)
            added.setAttribute("id", BpmnIdGenerator.generate(file, "collaboration"))
            added
        }

        val processId = if (existing == null) {
            root.subTags.firstOrNull { it.localName == "process" }?.getAttributeValue("id")
        } else {
            null
        } ?: createEmptyProcess(file, root)

        val id = BpmnIdGenerator.generate(file, "participant")
        val participant = collaboration.createChildTag("participant", collaboration.namespace, null, false)
        val added = collaboration.addSubTag(participant, false)
        added.setAttribute("id", id)
        added.setAttribute("processRef", processId)

        ensureDiagramInterchange(root, diagram)
        // 既にあるプロセスを包むプールだけ、その要素を囲む大きさにする。
        // 押した位置のままだと、自分が指すプロセスの外にプールが置かれてしまう。
        // 2 つ目以降は空のプロセスを指すので、押した位置と大きさをそのまま使う。
        val wrapsExistingProcess = existing == null
        val enclosing = if (wrapsExistingProcess) enclosingBounds(diagram) ?: bounds else bounds
        // プールがあるなら、図の面は collaboration を指す
        findPlane(root)?.setAttribute("bpmnElement", collaboration.getAttributeValue("id"))
        writeShapeBounds(root, id, enclosing)
        return id
    }

    /** 図にある要素をすべて囲む矩形。要素が無ければ null。 */
    private fun enclosingBounds(diagram: BpmnDiagram): BpmnBounds? {
        val boxes = diagram.nodes
            .filter { !it.kind.isPoolOrLane }
            .mapNotNull { it.bounds }
        if (boxes.isEmpty()) return null

        val left = boxes.minOf { it.x }
        val top = boxes.minOf { it.y }
        val right = boxes.maxOf { it.right }
        val bottom = boxes.maxOf { it.bottom }
        // 左に名前を書く帯のぶん、余分に取る
        return BpmnBounds(
            left - POOL_LABEL_BAND - POOL_PADDING,
            top - POOL_PADDING,
            right - left + POOL_LABEL_BAND + POOL_PADDING * 2,
            bottom - top + POOL_PADDING * 2,
        )
    }

    private fun createEmptyProcess(file: XmlFile, root: XmlTag): String {
        val id = BpmnIdGenerator.generate(file, "process")
        val process = root.createChildTag("process", root.namespace, null, false)
        val added = root.addSubTag(process, false)
        added.setAttribute("id", id)
        added.setAttribute("isExecutable", "false")
        return id
    }

    /**
     * レーンを作る。
     *
     * レーンはプロセスの laneSet に属する。落とした先のプールが指すプロセス、
     * 無ければ最初のプロセスに足す。laneSet が無ければ作る。
     */
    private fun createLane(
        file: XmlFile,
        root: XmlTag,
        diagram: BpmnDiagram,
        bounds: BpmnBounds,
        containerId: String?,
    ): String? {
        val process = processForLane(root, containerId) ?: return null
        val laneSet = process.subTags.firstOrNull { it.localName == "laneSet" } ?: run {
            val tag = process.createChildTag("laneSet", process.namespace, null, false)
            val added = process.addSubTag(tag, true)
            added.setAttribute("id", BpmnIdGenerator.generate(file, "laneSet"))
            added
        }

        val id = BpmnIdGenerator.generate(file, "lane")
        val lane = laneSet.createChildTag("lane", laneSet.namespace, null, false)
        val added = laneSet.addSubTag(lane, false)
        added.setAttribute("id", id)

        ensureDiagramInterchange(root, diagram)
        writeShapeBounds(root, id, bounds)
        return id
    }

    /** レーンを足すプロセス。プールの上に落とせばそのプールのプロセス。 */
    private fun processForLane(root: XmlTag, containerId: String?): XmlTag? {
        val processes = root.subTags.filter { it.localName == "process" }
        val poolProcessId = containerId
            ?.let { id -> findModelElement(root.containingFile as XmlFile, id) }
            ?.takeIf { it.localName == "participant" }
            ?.getAttributeValue("processRef")
        return processes.firstOrNull { it.getAttributeValue("id") == poolProcessId } ?: processes.firstOrNull()
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

            // プールを消すときは、それが指すプロセスと中身もまとめて片付ける。
            // 参照の切れたプロセスだけが残っても使い道がない。
            for (id in elementIds) {
                val participant = findModelElement(file, id) ?: continue
                if (participant.localName != "participant") continue
                val processId = participant.getAttributeValue("processRef") ?: continue
                val process = root.subTags.firstOrNull {
                    it.localName == "process" && it.getAttributeValue("id") == processId
                } ?: continue
                collectIds(process).forEach { doomed += it }
                doomed += processId
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

    /**
     * 結ぶ相手から線の種類を決める。
     *
     * 注記が絡めば関連、別のプールにまたがればメッセージフロー、
     * それ以外はシーケンスフロー。BPMN ではこの 3 つを取り違えると意味が変わる。
     */
    private fun connectionKindFor(diagram: BpmnDiagram, sourceId: String, targetId: String): ConnectionTag {
        val source = diagram.nodesById[sourceId]
        val target = diagram.nodesById[targetId]

        val touchesArtifact = source?.kind?.category == BpmnCategory.ARTIFACT ||
            target?.kind?.category == BpmnCategory.ARTIFACT
        if (touchesArtifact) return ConnectionTag.ASSOCIATION

        val sourcePool = enclosingPool(diagram, source)
        val targetPool = enclosingPool(diagram, target)
        if (sourcePool != null && targetPool != null && sourcePool != targetPool) {
            return ConnectionTag.MESSAGE_FLOW
        }
        return ConnectionTag.SEQUENCE_FLOW
    }

    /** その要素を包んでいるプールの id。プールが無ければ null。 */
    private fun enclosingPool(diagram: BpmnDiagram, node: BpmnNode?): String? {
        val bounds = node?.bounds ?: return null
        return diagram.nodes
            .filter { it.kind == BpmnElementKind.POOL && it.id.isNotEmpty() }
            .firstOrNull { it.bounds?.let { pool -> encloses(pool, bounds) } == true }
            ?.id
    }

    /** 線を置く場所。メッセージフローはプールをまたぐので collaboration に置く。 */
    private fun containerFor(root: XmlTag, source: XmlTag, kind: ConnectionTag): XmlTag? = when (kind) {
        ConnectionTag.MESSAGE_FLOW -> root.subTags.firstOrNull { it.localName == "collaboration" }
        else -> source.parentTag
    }

    /** 作れる線の種類。 */
    private enum class ConnectionTag(val tagName: String) {
        SEQUENCE_FLOW("sequenceFlow"),
        MESSAGE_FLOW("messageFlow"),
        ASSOCIATION("association"),
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
            val isConnection = child.localName in CONNECTION_TAGS &&
                BpmnNamespaces.isModelNamespace(child.namespace)
            if (isConnection) result += child
            result += collectFlows(child)
        }
        return result
    }

    private val CONNECTION_TAGS = setOf("sequenceFlow", "messageFlow", "association")

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
        val source = diagram.nodesById[sourceId]?.bounds ?: return emptyList()
        val target = diagram.nodesById[targetId]?.bounds ?: return emptyList()
        return BpmnGeometry.reroute(emptyList(), source, target)
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
