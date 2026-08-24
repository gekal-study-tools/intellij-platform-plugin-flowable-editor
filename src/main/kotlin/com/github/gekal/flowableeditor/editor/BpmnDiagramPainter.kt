package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.model.BpmnBounds
import com.github.gekal.flowableeditor.model.BpmnCategory
import com.github.gekal.flowableeditor.model.BpmnConnectionKind
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnEdge
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.github.gekal.flowableeditor.model.BpmnLabelMetrics
import com.github.gekal.flowableeditor.model.BpmnNode
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * [BpmnDiagram] をモデル座標系のまま Graphics2D に描く。
 * 拡大縮小は呼び出し側が [Graphics2D.scale] で与える前提なので、
 * 画面表示と PNG 書き出しで同じコードを共有できる。
 */
class BpmnDiagramPainter(private val baseFont: Font) {

    private val labelFont: Font = baseFont.deriveFont(11f)
    private val smallFont: Font = baseFont.deriveFont(10f)

    fun paint(g: Graphics2D, diagram: BpmnDiagram, selection: Any?) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        val drawable = diagram.nodes.filter { it.bounds != null }

        // 奥から手前へ: プール/レーン → サブプロセス枠 → 接続線 → 図形
        drawable.filter { it.kind.isPoolOrLane }
            .sortedByDescending { it.bounds!!.width * it.bounds!!.height }
            .forEach { drawPoolOrLane(g, it) }

        drawable.filter { it.kind.isSubProcess }
            .sortedByDescending { it.bounds!!.width * it.bounds!!.height }
            .forEach { drawSubProcess(g, it) }

        diagram.edges.filter { it.waypoints.size >= 2 }.forEach { drawEdge(g, it) }

        drawable.filter { !it.kind.isPoolOrLane && !it.kind.isSubProcess }
            .forEach { drawNode(g, it) }

        drawSelection(g, selection)
    }

    // --- ノード --------------------------------------------------------------

    private fun drawNode(g: Graphics2D, node: BpmnNode) {
        val bounds = node.bounds ?: return
        when (node.kind.category) {
            BpmnCategory.EVENT -> drawEvent(g, node, bounds)
            BpmnCategory.GATEWAY -> drawGateway(g, node, bounds)
            BpmnCategory.ACTIVITY -> drawActivity(g, node, bounds)
            BpmnCategory.DATA -> drawDataObject(g, node, bounds)
            BpmnCategory.ARTIFACT -> drawArtifact(g, node, bounds)
            else -> drawActivity(g, node, bounds)
        }
    }

    private fun drawEvent(g: Graphics2D, node: BpmnNode, bounds: BpmnBounds) {
        val color = when (node.kind) {
            BpmnElementKind.START_EVENT -> BpmnColors.START
            BpmnElementKind.END_EVENT -> BpmnColors.END
            else -> BpmnColors.INTERMEDIATE
        }
        val circle = Ellipse2D.Double(bounds.x, bounds.y, bounds.width, bounds.height)

        g.color = BpmnColors.SHAPE_FILL
        g.fill(circle)
        g.color = color

        // 終了イベントは太線、中間イベントは二重丸、非割り込みは破線という BPMN の慣習に従う
        val dashed = !node.isInterrupting
        g.stroke = when {
            node.kind == BpmnElementKind.END_EVENT -> stroke(3.0, dashed)
            else -> stroke(1.6, dashed)
        }
        g.draw(circle)

        if (node.kind == BpmnElementKind.INTERMEDIATE_CATCH_EVENT ||
            node.kind == BpmnElementKind.INTERMEDIATE_THROW_EVENT ||
            node.kind == BpmnElementKind.BOUNDARY_EVENT
        ) {
            val inset = 3.0
            g.draw(
                Ellipse2D.Double(
                    bounds.x + inset, bounds.y + inset,
                    bounds.width - 2 * inset, bounds.height - 2 * inset,
                ),
            )
        }

        val filled = node.kind == BpmnElementKind.INTERMEDIATE_THROW_EVENT ||
            node.kind == BpmnElementKind.END_EVENT
        drawEventMarker(g, node.eventDefinition, bounds, color, filled)

        drawLabelBelow(g, node.name, bounds)
    }

    private fun drawEventMarker(
        g: Graphics2D,
        definition: String?,
        bounds: BpmnBounds,
        color: Color,
        filled: Boolean,
    ) {
        if (definition == null) return
        val size = bounds.width * 0.44
        val box = BpmnBounds(bounds.centerX - size / 2, bounds.centerY - size / 2, size, size)
        g.color = color
        g.stroke = stroke(1.2, false)

        when (definition) {
            "timer" -> {
                g.draw(Ellipse2D.Double(box.x, box.y, box.width, box.height))
                g.draw(Line2D.Double(box.centerX, box.centerY, box.centerX, box.y + box.height * 0.2))
                g.draw(Line2D.Double(box.centerX, box.centerY, box.x + box.width * 0.78, box.centerY))
            }

            "message" -> {
                val envelope = Rectangle2D.Double(box.x, box.y + box.height * 0.15, box.width, box.height * 0.7)
                if (filled) g.fill(envelope) else g.draw(envelope)
                g.color = if (filled) BpmnColors.SHAPE_FILL else color
                g.draw(
                    Path2D.Double().apply {
                        moveTo(envelope.x, envelope.y)
                        lineTo(envelope.centerX, envelope.centerY)
                        lineTo(envelope.maxX, envelope.y)
                    },
                )
            }

            "error" -> {
                val path = Path2D.Double().apply {
                    moveTo(box.x, box.bottom)
                    lineTo(box.x + box.width * 0.38, box.y + box.height * 0.32)
                    lineTo(box.x + box.width * 0.62, box.y + box.height * 0.66)
                    lineTo(box.right, box.y)
                    if (filled) {
                        lineTo(box.x + box.width * 0.66, box.y + box.height * 0.5)
                        lineTo(box.x + box.width * 0.4, box.y + box.height * 0.2)
                        closePath()
                    }
                }
                if (filled) g.fill(path) else g.draw(path)
            }

            "signal" -> {
                val path = Path2D.Double().apply {
                    moveTo(box.centerX, box.y)
                    lineTo(box.right, box.bottom)
                    lineTo(box.x, box.bottom)
                    closePath()
                }
                if (filled) g.fill(path) else g.draw(path)
            }

            "escalation" -> {
                val path = Path2D.Double().apply {
                    moveTo(box.centerX, box.y)
                    lineTo(box.right, box.bottom)
                    lineTo(box.centerX, box.centerY)
                    lineTo(box.x, box.bottom)
                    closePath()
                }
                if (filled) g.fill(path) else g.draw(path)
            }

            "terminate" -> g.fill(Ellipse2D.Double(box.x, box.y, box.width, box.height))

            "cancel" -> {
                g.draw(Line2D.Double(box.x, box.y, box.right, box.bottom))
                g.draw(Line2D.Double(box.right, box.y, box.x, box.bottom))
            }

            "compensate" -> {
                val path = Path2D.Double().apply {
                    moveTo(box.centerX, box.y)
                    lineTo(box.centerX, box.bottom)
                    lineTo(box.x, box.centerY)
                    closePath()
                    moveTo(box.right, box.y)
                    lineTo(box.right, box.bottom)
                    lineTo(box.centerX, box.centerY)
                    closePath()
                }
                if (filled) g.fill(path) else g.draw(path)
            }

            "conditional" -> {
                g.draw(Rectangle2D.Double(box.x, box.y, box.width, box.height))
                for (i in 1..3) {
                    val y = box.y + box.height * i / 4
                    g.draw(Line2D.Double(box.x + box.width * 0.15, y, box.right - box.width * 0.15, y))
                }
            }

            "link" -> {
                g.draw(
                    Path2D.Double().apply {
                        moveTo(box.x, box.centerY)
                        lineTo(box.x + box.width * 0.6, box.centerY)
                        moveTo(box.x + box.width * 0.6, box.y + box.height * 0.2)
                        lineTo(box.right, box.centerY)
                        lineTo(box.x + box.width * 0.6, box.bottom - box.height * 0.2)
                    },
                )
            }

            else -> g.draw(Ellipse2D.Double(box.x, box.y, box.width, box.height))
        }
    }

    private fun drawGateway(g: Graphics2D, node: BpmnNode, bounds: BpmnBounds) {
        val diamond = Path2D.Double().apply {
            moveTo(bounds.centerX, bounds.y)
            lineTo(bounds.right, bounds.centerY)
            lineTo(bounds.centerX, bounds.bottom)
            lineTo(bounds.x, bounds.centerY)
            closePath()
        }
        g.color = BpmnColors.SHAPE_FILL
        g.fill(diamond)
        g.color = BpmnColors.GATEWAY
        g.stroke = stroke(1.6, false)
        g.draw(diamond)

        val r = bounds.width * 0.22
        val cx = bounds.centerX
        val cy = bounds.centerY
        g.stroke = stroke(2.0, false)
        when (node.kind) {
            BpmnElementKind.EXCLUSIVE_GATEWAY -> {
                // 斜めの X はひし形の辺に近づくと見分けが付かなくなるので少し内側に描く
                val d = r * 0.78
                g.draw(Line2D.Double(cx - d, cy - d, cx + d, cy + d))
                g.draw(Line2D.Double(cx + d, cy - d, cx - d, cy + d))
            }

            BpmnElementKind.PARALLEL_GATEWAY -> {
                g.draw(Line2D.Double(cx - r, cy, cx + r, cy))
                g.draw(Line2D.Double(cx, cy - r, cx, cy + r))
            }

            BpmnElementKind.INCLUSIVE_GATEWAY ->
                g.draw(Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2))

            BpmnElementKind.EVENT_BASED_GATEWAY -> {
                g.stroke = stroke(1.2, false)
                g.draw(Ellipse2D.Double(cx - r * 1.3, cy - r * 1.3, r * 2.6, r * 2.6))
                g.draw(Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2))
            }

            BpmnElementKind.COMPLEX_GATEWAY -> {
                g.draw(Line2D.Double(cx - r, cy, cx + r, cy))
                g.draw(Line2D.Double(cx, cy - r, cx, cy + r))
                val d = r * 0.7
                g.draw(Line2D.Double(cx - d, cy - d, cx + d, cy + d))
                g.draw(Line2D.Double(cx + d, cy - d, cx - d, cy + d))
            }

            else -> Unit
        }

        drawLabelBelow(g, node.name, bounds)
    }

    private fun drawActivity(g: Graphics2D, node: BpmnNode, bounds: BpmnBounds) {
        val rect = RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, 10.0, 10.0)
        g.color = BpmnColors.SHAPE_FILL
        g.fill(rect)
        g.color = BpmnColors.SHAPE_BORDER
        g.stroke = stroke(if (node.kind == BpmnElementKind.CALL_ACTIVITY) 3.0 else 1.3, false)
        g.draw(rect)

        drawTaskMarker(g, node, bounds)
        drawActivityDecorations(g, node, bounds)
        drawWrappedLabel(g, node.name ?: node.id, bounds, BpmnColors.TEXT)
    }

    /** タスク種別を表す左上の小さなマーカー。 */
    private fun drawTaskMarker(g: Graphics2D, node: BpmnNode, bounds: BpmnBounds) {
        val size = 11.0
        val box = BpmnBounds(bounds.x + 5, bounds.y + 5, size, size)
        g.color = BpmnColors.ACCENT
        g.stroke = stroke(1.1, false)

        when (node.kind) {
            BpmnElementKind.USER_TASK -> {
                g.draw(Ellipse2D.Double(box.centerX - size * 0.22, box.y, size * 0.44, size * 0.44))
                g.draw(
                    Arc2D.Double(
                        box.x, box.y + size * 0.45, size, size, 0.0, 180.0, Arc2D.OPEN,
                    ),
                )
            }

            BpmnElementKind.SERVICE_TASK -> {
                g.draw(Ellipse2D.Double(box.x + size * 0.2, box.y + size * 0.2, size * 0.6, size * 0.6))
                for (i in 0 until 6) {
                    val angle = Math.PI * i / 3
                    g.draw(
                        Line2D.Double(
                            box.centerX + cos(angle) * size * 0.32, box.centerY + sin(angle) * size * 0.32,
                            box.centerX + cos(angle) * size * 0.5, box.centerY + sin(angle) * size * 0.5,
                        ),
                    )
                }
            }

            BpmnElementKind.SCRIPT_TASK -> {
                g.draw(Rectangle2D.Double(box.x + size * 0.15, box.y, size * 0.7, size))
                for (i in 1..3) {
                    val y = box.y + size * i / 4
                    g.draw(Line2D.Double(box.x + size * 0.3, y, box.x + size * 0.7, y))
                }
            }

            BpmnElementKind.SEND_TASK, BpmnElementKind.RECEIVE_TASK -> {
                val envelope = Rectangle2D.Double(box.x, box.y + size * 0.2, size, size * 0.6)
                if (node.kind == BpmnElementKind.SEND_TASK) g.fill(envelope) else g.draw(envelope)
                g.color = if (node.kind == BpmnElementKind.SEND_TASK) BpmnColors.SHAPE_FILL else BpmnColors.ACCENT
                g.draw(
                    Path2D.Double().apply {
                        moveTo(envelope.x, envelope.y)
                        lineTo(envelope.centerX, envelope.centerY)
                        lineTo(envelope.maxX, envelope.y)
                    },
                )
            }

            BpmnElementKind.BUSINESS_RULE_TASK -> {
                g.draw(Rectangle2D.Double(box.x, box.y + size * 0.15, size, size * 0.7))
                g.draw(Line2D.Double(box.x, box.y + size * 0.4, box.right, box.y + size * 0.4))
                g.draw(Line2D.Double(box.centerX, box.y + size * 0.4, box.centerX, box.y + size * 0.85))
            }

            BpmnElementKind.MANUAL_TASK -> {
                g.draw(
                    Arc2D.Double(box.x, box.y + size * 0.2, size, size * 0.8, 20.0, 140.0, Arc2D.OPEN),
                )
                g.draw(Line2D.Double(box.x, box.bottom - size * 0.15, box.right, box.bottom - size * 0.15))
            }

            else -> Unit
        }
    }

    /** マルチインスタンス・補償など、下辺中央に付くマーカー。 */
    private fun drawActivityDecorations(g: Graphics2D, node: BpmnNode, bounds: BpmnBounds) {
        if (!node.isMultiInstance && !node.isForCompensation) return
        val size = 10.0
        var cx = bounds.centerX
        val y = bounds.bottom - size - 3
        g.color = BpmnColors.SHAPE_BORDER
        g.stroke = stroke(1.4, false)

        if (node.isMultiInstance) {
            if (node.isSequentialMultiInstance) {
                for (i in 0 until 3) {
                    val ly = y + size * i / 2.5
                    g.draw(Line2D.Double(cx - size / 2, ly, cx + size / 2, ly))
                }
            } else {
                for (i in 0 until 3) {
                    val lx = cx - size / 2 + size * i / 2.5
                    g.draw(Line2D.Double(lx, y, lx, y + size))
                }
            }
            cx += size + 4
        }
        if (node.isForCompensation) {
            g.draw(
                Path2D.Double().apply {
                    moveTo(cx, y)
                    lineTo(cx, y + size)
                    lineTo(cx - size / 2, y + size / 2)
                    closePath()
                    moveTo(cx + size / 2, y)
                    lineTo(cx + size / 2, y + size)
                    lineTo(cx, y + size / 2)
                    closePath()
                },
            )
        }
    }

    private fun drawSubProcess(g: Graphics2D, node: BpmnNode) {
        val bounds = node.bounds ?: return
        val rect = RoundRectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height, 10.0, 10.0)
        g.color = BpmnColors.CONTAINER_FILL
        g.fill(rect)
        g.color = BpmnColors.CONTAINER_BORDER
        g.stroke = stroke(if (node.kind == BpmnElementKind.TRANSACTION) 2.4 else 1.3, false)
        g.draw(rect)

        g.color = BpmnColors.MUTED_TEXT
        g.font = smallFont
        val label = node.displayLabel
        if (label.isNotEmpty()) {
            val clipped = clip(g, label, bounds.width - 16)
            g.drawString(clipped, (bounds.x + 8).toFloat(), (bounds.y + 14).toFloat())
        }
        drawActivityDecorations(g, node, bounds)
    }

    private fun drawPoolOrLane(g: Graphics2D, node: BpmnNode) {
        val bounds = node.bounds ?: return
        val rect = Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height)
        if (node.kind == BpmnElementKind.POOL) {
            g.color = BpmnColors.CONTAINER_FILL
            g.fill(rect)
        }
        g.color = BpmnColors.CONTAINER_BORDER
        g.stroke = stroke(1.2, false)
        g.draw(rect)

        // 左端の縦帯にプール/レーン名を回転して描く
        val bandWidth = 24.0
        g.draw(Line2D.Double(bounds.x + bandWidth, bounds.y, bounds.x + bandWidth, bounds.bottom))

        val label = node.displayLabel
        if (label.isEmpty()) return
        val transform = g.transform
        g.color = BpmnColors.MUTED_TEXT
        g.font = smallFont
        g.rotate(-Math.PI / 2, bounds.x + bandWidth / 2, bounds.centerY)
        val metrics = g.fontMetrics
        val clipped = clip(g, label, bounds.height - 12)
        g.drawString(
            clipped,
            (bounds.x + bandWidth / 2 - metrics.stringWidth(clipped) / 2.0).toFloat(),
            (bounds.centerY + metrics.ascent / 2.0 - 1).toFloat(),
        )
        g.transform = transform
    }

    private fun drawDataObject(g: Graphics2D, node: BpmnNode, bounds: BpmnBounds) {
        val fold = bounds.width * 0.3
        val shape = Path2D.Double().apply {
            moveTo(bounds.x, bounds.y)
            lineTo(bounds.right - fold, bounds.y)
            lineTo(bounds.right, bounds.y + fold)
            lineTo(bounds.right, bounds.bottom)
            lineTo(bounds.x, bounds.bottom)
            closePath()
        }
        g.color = BpmnColors.SHAPE_FILL
        g.fill(shape)
        g.color = BpmnColors.SHAPE_BORDER
        g.stroke = stroke(1.2, false)
        g.draw(shape)
        g.draw(
            Path2D.Double().apply {
                moveTo(bounds.right - fold, bounds.y)
                lineTo(bounds.right - fold, bounds.y + fold)
                lineTo(bounds.right, bounds.y + fold)
            },
        )
        drawLabelBelow(g, node.name, bounds)
    }

    private fun drawArtifact(g: Graphics2D, node: BpmnNode, bounds: BpmnBounds) {
        g.color = BpmnColors.MUTED_TEXT
        g.stroke = stroke(1.2, false)
        // テキスト注記は左側の角括弧だけを描くのが BPMN の表記
        g.draw(
            Path2D.Double().apply {
                moveTo(bounds.x + bounds.width * 0.15, bounds.y)
                lineTo(bounds.x, bounds.y)
                lineTo(bounds.x, bounds.bottom)
                lineTo(bounds.x + bounds.width * 0.15, bounds.bottom)
            },
        )
        drawWrappedLabel(
            g,
            node.name ?: node.id,
            bounds.translate(6.0, 0.0),
            BpmnColors.MUTED_TEXT,
        )
    }

    // --- 接続線 --------------------------------------------------------------

    private fun drawEdge(g: Graphics2D, edge: BpmnEdge) {
        val points = edge.waypoints
        val path = Path2D.Double().apply {
            moveTo(points.first().x, points.first().y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }

        val isMessage = edge.kind == BpmnConnectionKind.MESSAGE_FLOW
        val isAssociation = edge.kind == BpmnConnectionKind.ASSOCIATION
        g.color = if (isMessage) BpmnColors.MESSAGE_FLOW else BpmnColors.FLOW
        g.stroke = stroke(1.2, dashed = isMessage || isAssociation)
        g.draw(path)

        val last = points.last()
        val beforeLast = points[points.size - 2]
        if (!isAssociation) {
            drawArrowHead(g, beforeLast.x, beforeLast.y, last.x, last.y, filled = !isMessage)
        }

        // 条件付きフローは根元にひし形、デフォルトフローは斜線
        val first = points.first()
        val second = points[1]
        when {
            edge.hasCondition -> drawConditionDiamond(g, first.x, first.y, second.x, second.y)
            edge.isDefaultFlow -> drawDefaultSlash(g, first.x, first.y, second.x, second.y)
        }

        drawEdgeLabel(g, edge)
    }

    private fun drawArrowHead(g: Graphics2D, fromX: Double, fromY: Double, x: Double, y: Double, filled: Boolean) {
        val angle = atan2(y - fromY, x - fromX)
        val length = 9.0
        val spread = Math.toRadians(20.0)
        val head = Path2D.Double().apply {
            moveTo(x, y)
            lineTo(x - length * cos(angle - spread), y - length * sin(angle - spread))
            lineTo(x - length * cos(angle + spread), y - length * sin(angle + spread))
            closePath()
        }
        if (filled) g.fill(head) else g.draw(head)
    }

    private fun drawConditionDiamond(g: Graphics2D, x: Double, y: Double, toX: Double, toY: Double) {
        val angle = atan2(toY - y, toX - x)
        val length = 12.0
        val half = 4.5
        val cx = x + cos(angle) * length / 2
        val cy = y + sin(angle) * length / 2
        val diamond = Path2D.Double().apply {
            moveTo(x, y)
            lineTo(cx - sin(angle) * half, cy + cos(angle) * half)
            lineTo(x + cos(angle) * length, y + sin(angle) * length)
            lineTo(cx + sin(angle) * half, cy - cos(angle) * half)
            closePath()
        }
        g.color = BpmnColors.SHAPE_FILL
        g.fill(diamond)
        g.color = BpmnColors.FLOW
        g.draw(diamond)
    }

    private fun drawDefaultSlash(g: Graphics2D, x: Double, y: Double, toX: Double, toY: Double) {
        val angle = atan2(toY - y, toX - x)
        val at = 12.0
        val px = x + cos(angle) * at
        val py = y + sin(angle) * at
        val half = 5.0
        g.draw(
            Line2D.Double(
                px - cos(angle + Math.PI / 4) * half, py - sin(angle + Math.PI / 4) * half,
                px + cos(angle + Math.PI / 4) * half, py + sin(angle + Math.PI / 4) * half,
            ),
        )
    }

    private fun drawEdgeLabel(g: Graphics2D, edge: BpmnEdge) {
        val name = edge.name?.takeIf { it.isNotBlank() } ?: return
        val points = edge.waypoints
        // 折れ線の中点に近いセグメントを選んでラベルを置く
        val segment = points.size / 2
        val a = points[(segment - 1).coerceAtLeast(0)]
        val b = points[segment.coerceAtMost(points.size - 1)]
        val x = (a.x + b.x) / 2
        val y = (a.y + b.y) / 2

        g.font = smallFont
        val metrics = g.fontMetrics
        val text = clip(g, name, 140.0)
        val width = metrics.stringWidth(text).toDouble()
        val height = metrics.height.toDouble()

        g.color = BpmnColors.CANVAS
        g.fill(Rectangle2D.Double(x - width / 2 - 2, y - height / 2 - 1, width + 4, height))
        g.color = BpmnColors.MUTED_TEXT
        g.drawString(text, (x - width / 2).toFloat(), (y + metrics.ascent / 2.0 - 2).toFloat())
    }

    // --- 選択枠 --------------------------------------------------------------

    private fun drawSelection(g: Graphics2D, selection: Any?) {
        g.color = BpmnColors.SELECTION
        g.stroke = BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        when (selection) {
            is BpmnNode -> {
                val bounds = selection.bounds ?: return
                val margin = 4.0
                g.draw(
                    RoundRectangle2D.Double(
                        bounds.x - margin, bounds.y - margin,
                        bounds.width + margin * 2, bounds.height + margin * 2,
                        8.0, 8.0,
                    ),
                )
            }

            is BpmnEdge -> {
                val points = selection.waypoints
                if (points.size < 2) return
                g.draw(
                    Path2D.Double().apply {
                        moveTo(points.first().x, points.first().y)
                        points.drop(1).forEach { lineTo(it.x, it.y) }
                    },
                )
            }
        }
    }

    // --- テキスト ------------------------------------------------------------

    private fun drawLabelBelow(g: Graphics2D, name: String?, bounds: BpmnBounds) {
        val text = name?.takeIf { it.isNotBlank() } ?: return
        g.font = smallFont
        val metrics = g.fontMetrics
        val lines = wrap(text, BpmnLabelMetrics.BELOW_WIDTH) { g.fontMetrics.stringWidth(it).toDouble() }
        var y = bounds.bottom + metrics.ascent + 2
        g.color = BpmnColors.TEXT
        for (line in lines.take(2)) {
            g.drawString(line, (bounds.centerX - metrics.stringWidth(line) / 2.0).toFloat(), y.toFloat())
            y += metrics.height
        }
    }

    private fun drawWrappedLabel(g: Graphics2D, text: String, bounds: BpmnBounds, color: Color) {
        if (text.isBlank()) return
        g.font = labelFont
        val metrics = g.fontMetrics
        val maxWidth = bounds.width - 12
        val lines = wrap(text, maxWidth) { metrics.stringWidth(it).toDouble() }
            .take(((bounds.height - 8) / metrics.height).toInt().coerceAtLeast(1))
        val totalHeight = lines.size * metrics.height
        var y = bounds.centerY - totalHeight / 2.0 + metrics.ascent
        g.color = color
        for (line in lines) {
            g.drawString(line, (bounds.centerX - metrics.stringWidth(line) / 2.0).toFloat(), y.toFloat())
            y += metrics.height
        }
    }

    private fun wrap(text: String, maxWidth: Double, width: (String) -> Double): List<String> {
        if (width(text) <= maxWidth) return listOf(text)
        val lines = mutableListOf<String>()
        var current = StringBuilder()
        for (word in text.split(' ', '\n', '\t').filter { it.isNotEmpty() }) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (width(candidate) <= maxWidth || current.isEmpty()) {
                current = StringBuilder(candidate)
            } else {
                lines += current.toString()
                current = StringBuilder(word)
            }
        }
        if (current.isNotEmpty()) lines += current.toString()
        return lines
    }

    private fun clip(g: Graphics2D, text: String, maxWidth: Double): String {
        val metrics = g.fontMetrics
        if (metrics.stringWidth(text) <= maxWidth) return text
        var result = text
        while (result.length > 1 && metrics.stringWidth("$result…") > maxWidth) {
            result = result.dropLast(1)
        }
        return "$result…"
    }

    private fun stroke(width: Double, dashed: Boolean): BasicStroke =
        if (dashed) {
            BasicStroke(
                width.toFloat(), BasicStroke.CAP_BUTT, BasicStroke.JOIN_ROUND,
                10f, floatArrayOf(5f, 4f), 0f,
            )
        } else {
            BasicStroke(width.toFloat(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
        }
}
