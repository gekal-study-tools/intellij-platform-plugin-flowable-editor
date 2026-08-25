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
    /** パレットでの並び。同じ組は続けて並び、間に区切りが入る。 */
    val group: BpmnPaletteGroup,
    /** アイコンのファイル名 (`/icons/palette<...>.svg`)。 */
    val iconName: String,
    /** `timerEventDefinition` のような子要素。不要なら null。 */
    val eventDefinition: String? = null,
) {
    START_EVENT(BpmnElementKind.START_EVENT, "Start event", BpmnPaletteGroup.EVENTS, "StartEvent"),
    END_EVENT(BpmnElementKind.END_EVENT, "End event", BpmnPaletteGroup.EVENTS, "EndEvent"),
    TIMER_CATCH_EVENT(
        BpmnElementKind.INTERMEDIATE_CATCH_EVENT,
        "Timer event",
        BpmnPaletteGroup.EVENTS,
        "TimerEvent",
        eventDefinition = "timerEventDefinition",
    ),

    USER_TASK(BpmnElementKind.USER_TASK, "User task", BpmnPaletteGroup.ACTIVITIES, "UserTask"),
    SERVICE_TASK(BpmnElementKind.SERVICE_TASK, "Service task", BpmnPaletteGroup.ACTIVITIES, "ServiceTask"),
    SCRIPT_TASK(BpmnElementKind.SCRIPT_TASK, "Script task", BpmnPaletteGroup.ACTIVITIES, "ScriptTask"),
    SUB_PROCESS(BpmnElementKind.SUB_PROCESS, "Sub process", BpmnPaletteGroup.ACTIVITIES, "SubProcess"),

    EXCLUSIVE_GATEWAY(
        BpmnElementKind.EXCLUSIVE_GATEWAY,
        "Exclusive gateway",
        BpmnPaletteGroup.GATEWAYS,
        "ExclusiveGateway",
    ),
    PARALLEL_GATEWAY(
        BpmnElementKind.PARALLEL_GATEWAY,
        "Parallel gateway",
        BpmnPaletteGroup.GATEWAYS,
        "ParallelGateway",
    ),
    ;

    val tagName: String get() = kind.tagName
}

/** パレットの区切り。BPMN の分類に合わせている。 */
enum class BpmnPaletteGroup(val label: String) {
    EVENTS("Events"),
    ACTIVITIES("Activities"),
    GATEWAYS("Gateways"),
}
