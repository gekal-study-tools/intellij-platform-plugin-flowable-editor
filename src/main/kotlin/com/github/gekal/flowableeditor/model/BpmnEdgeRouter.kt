package com.github.gekal.flowableeditor.model

import kotlin.math.abs

/**
 * BPMNDI に waypoint が無い接続線を、直交折れ線で結ぶ。
 * 左から右へ流れる [BpmnAutoLayout] の結果に合わせた単純なルーティング。
 */
object BpmnEdgeRouter {

    /** 折り返しのために図形から離す距離。 */
    private const val DETOUR = 24.0

    fun routeMissingEdges(diagram: BpmnDiagram) {
        for (edge in diagram.edges) {
            if (edge.waypoints.size >= 2) continue
            val source = diagram.nodesById[edge.sourceRef]?.bounds ?: continue
            val target = diagram.nodesById[edge.targetRef]?.bounds ?: continue
            edge.waypoints = route(source, target)
        }
    }

    private fun route(source: BpmnBounds, target: BpmnBounds): List<BpmnPoint> = when {
        // 右方向: 出口は右辺、入口は左辺
        target.x >= source.right -> {
            val startY = source.centerY
            val endY = target.centerY
            if (abs(startY - endY) < 1.0) {
                listOf(BpmnPoint(source.right, startY), BpmnPoint(target.x, endY))
            } else {
                val midX = (source.right + target.x) / 2
                listOf(
                    BpmnPoint(source.right, startY),
                    BpmnPoint(midX, startY),
                    BpmnPoint(midX, endY),
                    BpmnPoint(target.x, endY),
                )
            }
        }

        // 下方向: 出口は下辺、入口は上辺
        target.y >= source.bottom -> {
            val midY = (source.bottom + target.y) / 2
            listOf(
                BpmnPoint(source.centerX, source.bottom),
                BpmnPoint(source.centerX, midY),
                BpmnPoint(target.centerX, midY),
                BpmnPoint(target.centerX, target.y),
            )
        }

        // 上方向: 出口は上辺、入口は下辺
        target.bottom <= source.y -> {
            val midY = (source.y + target.bottom) / 2
            listOf(
                BpmnPoint(source.centerX, source.y),
                BpmnPoint(source.centerX, midY),
                BpmnPoint(target.centerX, midY),
                BpmnPoint(target.centerX, target.bottom),
            )
        }

        // 左方向 (ループバック): 下側を回り込ませる
        else -> {
            val detourY = maxOf(source.bottom, target.bottom) + DETOUR
            listOf(
                BpmnPoint(source.centerX, source.bottom),
                BpmnPoint(source.centerX, detourY),
                BpmnPoint(target.centerX, detourY),
                BpmnPoint(target.centerX, target.bottom),
            )
        }
    }
}
