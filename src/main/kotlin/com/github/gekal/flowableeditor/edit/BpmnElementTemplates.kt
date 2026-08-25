package com.github.gekal.flowableeditor.edit

import com.github.gekal.flowableeditor.model.BpmnElementKind

/**
 * パレットから置ける要素の定義。
 *
 * 種類ごとに「どのタグを作るか」「イベント定義の子要素が要るか」「既定の大きさ」を持つ。
 */
enum class BpmnPaletteItem(
    val kind: BpmnElementKind,
    val label: String,
    /** `timerEventDefinition` のような子要素。不要なら null。 */
    val eventDefinition: String? = null,
) {
    START_EVENT(BpmnElementKind.START_EVENT, "Start event"),
    END_EVENT(BpmnElementKind.END_EVENT, "End event"),
    USER_TASK(BpmnElementKind.USER_TASK, "User task"),
    SERVICE_TASK(BpmnElementKind.SERVICE_TASK, "Service task"),
    SCRIPT_TASK(BpmnElementKind.SCRIPT_TASK, "Script task"),
    EXCLUSIVE_GATEWAY(BpmnElementKind.EXCLUSIVE_GATEWAY, "Exclusive gateway"),
    PARALLEL_GATEWAY(BpmnElementKind.PARALLEL_GATEWAY, "Parallel gateway"),
    TIMER_CATCH_EVENT(
        BpmnElementKind.INTERMEDIATE_CATCH_EVENT,
        "Timer event",
        eventDefinition = "timerEventDefinition",
    ),
    SUB_PROCESS(BpmnElementKind.SUB_PROCESS, "Sub process"),
    ;

    val tagName: String get() = kind.tagName
}
