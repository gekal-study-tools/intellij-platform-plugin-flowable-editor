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
    /**
     * アクティビティの縁に貼り付ける要素か。
     * 境界イベントは単独では置けず、貼り付け先が要る。
     */
    val attachesToActivity: Boolean = false,
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
    MESSAGE_CATCH_EVENT(
        BpmnElementKind.INTERMEDIATE_CATCH_EVENT,
        "Message event",
        BpmnPaletteGroup.EVENTS,
        "MessageEvent",
        eventDefinition = "messageEventDefinition",
    ),
    BOUNDARY_TIMER_EVENT(
        BpmnElementKind.BOUNDARY_EVENT,
        "Boundary timer (drop on an activity)",
        BpmnPaletteGroup.EVENTS,
        "BoundaryEvent",
        eventDefinition = "timerEventDefinition",
        attachesToActivity = true,
    ),

    USER_TASK(BpmnElementKind.USER_TASK, "User task", BpmnPaletteGroup.ACTIVITIES, "UserTask"),
    SERVICE_TASK(BpmnElementKind.SERVICE_TASK, "Service task", BpmnPaletteGroup.ACTIVITIES, "ServiceTask"),
    SCRIPT_TASK(BpmnElementKind.SCRIPT_TASK, "Script task", BpmnPaletteGroup.ACTIVITIES, "ScriptTask"),
    BUSINESS_RULE_TASK(
        BpmnElementKind.BUSINESS_RULE_TASK,
        "Business rule task",
        BpmnPaletteGroup.ACTIVITIES,
        "BusinessRuleTask",
    ),
    RECEIVE_TASK(BpmnElementKind.RECEIVE_TASK, "Receive task", BpmnPaletteGroup.ACTIVITIES, "ReceiveTask"),
    CALL_ACTIVITY(BpmnElementKind.CALL_ACTIVITY, "Call activity", BpmnPaletteGroup.ACTIVITIES, "CallActivity"),
    SUB_PROCESS(BpmnElementKind.SUB_PROCESS, "Sub process", BpmnPaletteGroup.ACTIVITIES, "SubProcess"),

    TEXT_ANNOTATION(
        BpmnElementKind.TEXT_ANNOTATION,
        "Text annotation",
        BpmnPaletteGroup.ACTIVITIES,
        "TextAnnotation",
    ),

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
    INCLUSIVE_GATEWAY(
        BpmnElementKind.INCLUSIVE_GATEWAY,
        "Inclusive gateway",
        BpmnPaletteGroup.GATEWAYS,
        "InclusiveGateway",
    ),
    EVENT_BASED_GATEWAY(
        BpmnElementKind.EVENT_BASED_GATEWAY,
        "Event based gateway",
        BpmnPaletteGroup.GATEWAYS,
        "EventGateway",
    ),
    POOL(BpmnElementKind.POOL, "Pool", BpmnPaletteGroup.CONTAINERS, "Pool"),
    LANE(BpmnElementKind.LANE, "Lane", BpmnPaletteGroup.CONTAINERS, "Lane"),
    ;

    val tagName: String get() = kind.tagName
}

/** パレットの区切り。BPMN の分類に合わせている。 */
enum class BpmnPaletteGroup(val label: String) {
    EVENTS("Events"),
    ACTIVITIES("Activities"),
    GATEWAYS("Gateways"),
    CONTAINERS("Pools and lanes"),
}
