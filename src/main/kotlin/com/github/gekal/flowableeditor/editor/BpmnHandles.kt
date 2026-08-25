package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.model.BpmnBounds

/** 選択中の図形に出るつまみ。 */
enum class BpmnHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_LEFT,
    BOTTOM_RIGHT,

    /** 右辺の外に出る、線を引き始めるためのつまみ。 */
    CONNECT,
    ;

    val isResize: Boolean get() = this != CONNECT
}

/**
 * つまみの位置と、そこから作られる新しい矩形の計算。
 * 画面上の見た目の大きさを保ちたいので、つまみの半径はビュー座標で扱う。
 */
object BpmnHandles {

    /** つまみの当たり判定の半径 (ビュー座標)。 */
    const val RADIUS = 4.0

    /** 線を引くつまみを図形の右辺からどれだけ離すか (モデル座標)。 */
    const val CONNECT_OFFSET = 14.0

    /** 図形が潰れないようにする最小の大きさ (モデル座標)。 */
    private const val MIN_SIZE = 16.0

    /** つまみの中心 (モデル座標)。 */
    fun center(bounds: BpmnBounds, handle: BpmnHandle): Pair<Double, Double> = when (handle) {
        BpmnHandle.TOP_LEFT -> bounds.x to bounds.y
        BpmnHandle.TOP_RIGHT -> bounds.right to bounds.y
        BpmnHandle.BOTTOM_LEFT -> bounds.x to bounds.bottom
        BpmnHandle.BOTTOM_RIGHT -> bounds.right to bounds.bottom
        BpmnHandle.CONNECT -> bounds.right + CONNECT_OFFSET to bounds.centerY
    }

    /** 角のつまみを [x], [y] まで動かしたときの新しい矩形。 */
    fun resize(bounds: BpmnBounds, handle: BpmnHandle, x: Double, y: Double): BpmnBounds {
        var left = bounds.x
        var top = bounds.y
        var right = bounds.right
        var bottom = bounds.bottom

        when (handle) {
            BpmnHandle.TOP_LEFT -> {
                left = x
                top = y
            }

            BpmnHandle.TOP_RIGHT -> {
                right = x
                top = y
            }

            BpmnHandle.BOTTOM_LEFT -> {
                left = x
                bottom = y
            }

            BpmnHandle.BOTTOM_RIGHT -> {
                right = x
                bottom = y
            }

            BpmnHandle.CONNECT -> return bounds
        }

        // 反転させずに、最小の大きさで止める
        if (right - left < MIN_SIZE) {
            if (handle == BpmnHandle.TOP_LEFT || handle == BpmnHandle.BOTTOM_LEFT) {
                left = right - MIN_SIZE
            } else {
                right = left + MIN_SIZE
            }
        }
        if (bottom - top < MIN_SIZE) {
            if (handle == BpmnHandle.TOP_LEFT || handle == BpmnHandle.TOP_RIGHT) {
                top = bottom - MIN_SIZE
            } else {
                bottom = top + MIN_SIZE
            }
        }
        return BpmnBounds(left, top, right - left, bottom - top)
    }
}
