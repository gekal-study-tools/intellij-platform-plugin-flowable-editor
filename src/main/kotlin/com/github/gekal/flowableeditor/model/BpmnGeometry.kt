package com.github.gekal.flowableeditor.model

import kotlin.math.abs

/** 図形と線の当たり位置を求める。 */
object BpmnGeometry {

    /**
     * [bounds] の中心から [toward] へ向かう線が、図形の縁と交わる点。
     *
     * 線の端をここに合わせておくと、図形を動かしても線が縁に吸い付いたままになる。
     * [toward] が図形の中にある場合は中心を返す。
     */
    fun dockPoint(bounds: BpmnBounds, toward: BpmnPoint): BpmnPoint {
        val cx = bounds.centerX
        val cy = bounds.centerY
        val dx = toward.x - cx
        val dy = toward.y - cy
        if (abs(dx) < 1e-6 && abs(dy) < 1e-6) return BpmnPoint(cx, cy)

        val halfWidth = bounds.width / 2
        val halfHeight = bounds.height / 2
        if (halfWidth <= 0 || halfHeight <= 0) return BpmnPoint(cx, cy)

        // 中心から縁までを 1 とする比を、横と縦で小さいほうに合わせる
        val scaleX = if (abs(dx) < 1e-6) Double.MAX_VALUE else halfWidth / abs(dx)
        val scaleY = if (abs(dy) < 1e-6) Double.MAX_VALUE else halfHeight / abs(dy)
        val scale = minOf(scaleX, scaleY)
        if (scale >= 1.0) return BpmnPoint(cx, cy)

        return BpmnPoint(cx + dx * scale, cy + dy * scale)
    }

    /**
     * 図形が動いたあとの折れ線を組み直す。
     *
     * 途中の折れ点は手で置かれたものかもしれないので残し、
     * 両端だけを図形の縁に付け直す。折れ点が 2 つしかない線は
     * 図形どうしを直に結び直す。
     */
    fun reroute(
        waypoints: List<BpmnPoint>,
        source: BpmnBounds?,
        target: BpmnBounds?,
    ): List<BpmnPoint> {
        if (source == null || target == null) return waypoints

        if (waypoints.size <= 2) {
            return listOf(
                dockPoint(source, BpmnPoint(target.centerX, target.centerY)),
                dockPoint(target, BpmnPoint(source.centerX, source.centerY)),
            )
        }

        val middle = waypoints.subList(1, waypoints.size - 1)
        return buildList {
            add(dockPoint(source, middle.first()))
            addAll(middle)
            add(dockPoint(target, middle.last()))
        }
    }
}
