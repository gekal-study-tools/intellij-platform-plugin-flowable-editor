package com.github.gekal.flowableeditor.model

/**
 * BPMNDI を持たない定義ファイル向けの自動レイアウト。
 *
 * シーケンスフローを有向グラフとみなして左から右へ階層配置する
 * (いわゆる layered / Sugiyama 風の簡易版)。サブプロセスは中身を再帰的に
 * 配置してから、その外接矩形を親の 1 ノードとして扱う。
 */
object BpmnAutoLayout {

    private const val H_GAP = 60.0
    private const val V_GAP = 40.0

    /** サブプロセス枠の内側余白。 */
    private const val PADDING = 24.0

    /** サブプロセス枠の上部、名前を書く帯の高さ。 */
    private const val HEADER = 24.0
    private const val ORIGIN_X = 40.0
    private const val ORIGIN_Y = 40.0

    /** 複数プロセスを縦に並べるときの間隔。 */
    private const val PROCESS_GAP = 80.0
    private const val MIN_CONTAINER_WIDTH = 200.0
    private const val MIN_CONTAINER_HEIGHT = 120.0

    /** 循環参照でスタックを掘り進まないための保険。 */
    private const val MAX_DEPTH = 200

    /** 種類ごとの既定サイズ (幅, 高さ)。BPMN の慣習的な寸法に合わせている。 */
    fun defaultSize(kind: BpmnElementKind): Pair<Double, Double> = when {
        kind.category == BpmnCategory.EVENT -> 36.0 to 36.0
        kind.category == BpmnCategory.GATEWAY -> 50.0 to 50.0
        kind.category == BpmnCategory.DATA -> 36.0 to 50.0
        kind == BpmnElementKind.TEXT_ANNOTATION -> 120.0 to 50.0
        kind.isSubProcess -> MIN_CONTAINER_WIDTH to MIN_CONTAINER_HEIGHT
        else -> 100.0 to 80.0
    }

    /**
     * いまの座標を捨てて配置し直す。
     *
     * 手で並べた図を整えるための入口。線の折れ点も作り直すので、
     * 図形の位置に合わない折れ点が残ることはない。
     *
     * プールやレーンがある図では何もしない ([canRelayout] を先に見ること)。
     * この配置はプールの区画を理解しないため、動かすと要素が区画からはみ出す。
     */
    fun relayout(diagram: BpmnDiagram) {
        if (!canRelayout(diagram)) return
        diagram.nodes.forEach { it.bounds = null }
        diagram.edges.forEach { it.waypoints = emptyList() }
        layout(diagram)
        BpmnEdgeRouter.routeMissingEdges(diagram)
    }

    /** 配置し直せる図か。プールやレーンがあると区画を壊すので触らない。 */
    fun canRelayout(diagram: BpmnDiagram): Boolean =
        diagram.nodes.none { it.kind.isPoolOrLane } && diagram.nodes.any { it.id.isNotEmpty() }

    fun layout(diagram: BpmnDiagram) {
        val context = LayoutContext(diagram)
        var cursorY = ORIGIN_Y

        for (root in context.rootContainerIds) {
            val size = layoutContainer(root, context, 0)
            translateSubtree(root, ORIGIN_X, cursorY, context)
            cursorY += size.second + PROCESS_GAP
        }

        placeBoundaryEvents(diagram)
    }

    // --- 内部 ----------------------------------------------------------------

    private class LayoutContext(diagram: BpmnDiagram) {
        /** レーン/プールは自動レイアウトの対象外 (座標情報が無いと意味を成さないため)。 */
        val layoutNodes = diagram.nodes.filter { !it.kind.isPoolOrLane }

        val byParent: Map<String?, List<BpmnNode>> = layoutNodes.groupBy { it.parentId }

        val flows = diagram.edges.filter { it.kind == BpmnConnectionKind.SEQUENCE_FLOW }

        /**
         * 境界イベントの id → 貼り付け先アクティビティの id。
         * 境界イベントは自分では列を持たない (ホストの縁に置く) ので、
         * そこから出るフローはホストから出ているものとして階層を決める。
         */
        val boundaryHosts: Map<String, String> = diagram.nodes
            .filter { it.kind == BpmnElementKind.BOUNDARY_EVENT }
            .mapNotNull { event ->
                val host = event.attachedToRef ?: return@mapNotNull null
                event.id.takeIf { it.isNotEmpty() }?.let { it to host }
            }
            .toMap()

        private val nodeIds = layoutNodes.mapNotNull { it.id.takeIf(String::isNotEmpty) }.toSet()

        /** どのノードの子でもないコンテナ = プロセス本体。 */
        val rootContainerIds: List<String?> =
            byParent.keys.filter { it == null || it !in nodeIds }
    }

    /**
     * [containerId] の直下のノードを (0, 0) 基準で配置し、占有サイズを返す。
     * サブプロセスの中身も再帰的に配置済みになる。
     */
    private fun layoutContainer(
        containerId: String?,
        context: LayoutContext,
        depth: Int,
    ): Pair<Double, Double> {
        val children = context.byParent[containerId].orEmpty()
            .filter { it.kind != BpmnElementKind.BOUNDARY_EVENT }
        if (children.isEmpty() || depth > MAX_DEPTH) {
            return MIN_CONTAINER_WIDTH to MIN_CONTAINER_HEIGHT
        }

        val sizes = LinkedHashMap<BpmnNode, Pair<Double, Double>>()
        for (child in children) {
            sizes[child] = if (child.kind.isSubProcess && context.byParent.containsKey(child.id)) {
                val inner = layoutContainer(child.id, context, depth + 1)
                (inner.first + 2 * PADDING) to (inner.second + PADDING + HEADER)
            } else {
                defaultSize(child.kind)
            }
        }

        val connections = connectionsAmong(children, context)
        val layers = assignLayers(children, connections)
        val ordered = orderWithinLayers(children, layers, connections)

        // 列ごとの X 座標
        val layerWidths = ordered.map { layer -> layer.maxOf { sizes.getValue(it).first } }
        val layerX = DoubleArray(ordered.size)
        var x = 0.0
        for (i in ordered.indices) {
            layerX[i] = x
            x += layerWidths[i] + H_GAP
        }
        val totalWidth = if (ordered.isEmpty()) MIN_CONTAINER_WIDTH else x - H_GAP

        // 列ごとの高さを求め、いちばん高い列に合わせて上下中央寄せ
        val layerHeights = ordered.map { layer ->
            layer.sumOf { sizes.getValue(it).second } + V_GAP * (layer.size - 1)
        }
        val totalHeight = layerHeights.maxOrNull() ?: MIN_CONTAINER_HEIGHT

        for (i in ordered.indices) {
            var y = (totalHeight - layerHeights[i]) / 2
            for (node in ordered[i]) {
                val (w, h) = sizes.getValue(node)
                val nodeX = layerX[i] + (layerWidths[i] - w) / 2
                node.bounds = BpmnBounds(nodeX, y, w, h)
                if (node.kind.isSubProcess && context.byParent.containsKey(node.id)) {
                    translateSubtree(node.id, nodeX + PADDING, y + HEADER, context)
                }
                y += h + V_GAP
            }
        }

        return totalWidth.coerceAtLeast(MIN_CONTAINER_WIDTH) to
            totalHeight.coerceAtLeast(MIN_CONTAINER_HEIGHT)
    }

    /** [containerId] 配下すべてを平行移動する。 */
    private fun translateSubtree(containerId: String?, dx: Double, dy: Double, context: LayoutContext) {
        translateSubtree(containerId, dx, dy, context, 0)
    }

    private fun translateSubtree(
        containerId: String?,
        dx: Double,
        dy: Double,
        context: LayoutContext,
        depth: Int,
    ) {
        if (depth > MAX_DEPTH) return
        for (child in context.byParent[containerId].orEmpty()) {
            child.bounds = child.bounds?.translate(dx, dy)
            if (child.kind.isSubProcess) {
                translateSubtree(child.id, dx, dy, context, depth + 1)
            }
        }
    }

    /**
     * このコンテナ直下のノード同士を結ぶシーケンスフローを (始点, 終点) の組にする。
     * 境界イベントはホストに読み替えるので、`boundaryEvent -> handler` のフローも
     * 「ホストの後ろに handler を置く」という配置に効く。
     */
    private fun connectionsAmong(
        children: List<BpmnNode>,
        context: LayoutContext,
    ): List<Pair<BpmnNode, BpmnNode>> {
        val byId = children.filter { it.id.isNotEmpty() }.associateBy { it.id }

        fun resolve(ref: String?): BpmnNode? {
            if (ref.isNullOrEmpty()) return null
            byId[ref]?.let { return it }
            return context.boundaryHosts[ref]?.let { byId[it] }
        }

        return context.flows.mapNotNull { flow ->
            val source = resolve(flow.sourceRef) ?: return@mapNotNull null
            val target = resolve(flow.targetRef) ?: return@mapNotNull null
            if (source === target) null else source to target
        }
    }

    /**
     * 各ノードの階層 (X 方向の列番号) を、入力エッジからの最長パスで決める。
     * 循環があっても止まらないよう、探索中のノードは 0 として打ち切る。
     */
    private fun assignLayers(
        children: List<BpmnNode>,
        connections: List<Pair<BpmnNode, BpmnNode>>,
    ): Map<BpmnNode, Int> {
        val incoming = HashMap<BpmnNode, MutableList<BpmnNode>>()
        for ((source, target) in connections) {
            incoming.getOrPut(target) { mutableListOf() }.add(source)
        }

        val layers = HashMap<BpmnNode, Int>()
        val visiting = HashSet<BpmnNode>()

        fun compute(node: BpmnNode): Int {
            layers[node]?.let { return it }
            if (!visiting.add(node)) return 0
            val value = incoming[node]?.maxOfOrNull { compute(it) + 1 } ?: 0
            visiting.remove(node)
            layers[node] = value
            return value
        }

        children.forEach(::compute)
        return layers
    }

    /**
     * 列ごとにノードを並べる。列内の順序は「前段の接続元の平均位置」で決め、
     * 決め手がない場合はドキュメント順を保つ (安定ソート)。
     */
    private fun orderWithinLayers(
        children: List<BpmnNode>,
        layers: Map<BpmnNode, Int>,
        connections: List<Pair<BpmnNode, BpmnNode>>,
    ): List<List<BpmnNode>> {
        val maxLayer = layers.values.maxOrNull() ?: 0
        val result = MutableList(maxLayer + 1) { mutableListOf<BpmnNode>() }
        for (node in children) {
            result[layers[node] ?: 0].add(node)
        }

        val positions = HashMap<BpmnNode, Double>()
        for (index in result.indices) {
            val layer = result[index]
            if (index > 0) {
                val sorted = layer.sortedBy { node ->
                    val predecessors = connections.asSequence()
                        .filter { it.second === node }
                        .mapNotNull { positions[it.first] }
                        .toList()
                    if (predecessors.isEmpty()) Double.MAX_VALUE else predecessors.average()
                }
                layer.clear()
                layer.addAll(sorted)
            }
            layer.forEachIndexed { position, node -> positions[node] = position.toDouble() }
        }
        return result.filter { it.isNotEmpty() }
    }

    /** 境界イベントをホストアクティビティの下辺に貼り付ける。 */
    private fun placeBoundaryEvents(diagram: BpmnDiagram) {
        val boundaries = diagram.nodes.filter { it.kind == BpmnElementKind.BOUNDARY_EVENT }
        if (boundaries.isEmpty()) return

        val perHost = boundaries.groupBy { it.attachedToRef }
        for ((hostId, attached) in perHost) {
            val host = diagram.nodesById[hostId]?.bounds ?: continue
            val size = defaultSize(BpmnElementKind.BOUNDARY_EVENT)
            // ホスト下辺に等間隔で並べる
            val step = host.width / (attached.size + 1)
            attached.forEachIndexed { index, event ->
                event.bounds = BpmnBounds(
                    host.x + step * (index + 1) - size.first / 2,
                    host.bottom - size.second / 2,
                    size.first,
                    size.second,
                )
            }
        }
    }
}
