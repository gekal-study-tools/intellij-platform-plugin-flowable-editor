package com.github.gekal.flowableeditor.bpmn

import com.github.gekal.flowableeditor.model.BpmnCategory
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object FlowableIcons {
    @JvmField
    val BpmnFile: Icon = load("bpmnFile")

    @JvmField
    val BpmnDiagram: Icon = load("bpmnDiagram")

    @JvmField
    val Process: Icon = load("bpmnProcess")

    @JvmField
    val Event: Icon = load("bpmnEvent")

    @JvmField
    val Task: Icon = load("bpmnTask")

    @JvmField
    val Gateway: Icon = load("bpmnGateway")

    @JvmField
    val SubProcess: Icon = load("bpmnSubProcess")

    @JvmField
    val Flow: Icon = load("bpmnFlow")

    @JvmField
    val PaletteSelect: Icon = load("paletteSelect")

    /**
     * パレットの要素に対応する図。
     *
     * 図の描画をそのまま縮めると、タスクの種別マーカーが潰れて見分けが付かない。
     * 小さい寸法に合わせて描き起こした専用のアイコンを使う。
     */
    fun forPaletteItem(iconName: String): Icon = load("palette$iconName")

    /**
     * 要素の種類 (と、あれば `timer` などのイベント定義) に対応するアイコン。
     *
     * パレットと構造ビューで同じ絵を使うための入口。描き起こしたアイコンが
     * ある組み合わせはそれを、無ければ分類ごとの汎用アイコンを返す。
     */
    fun forElement(kind: BpmnElementKind, eventDefinition: String? = null): Icon {
        specificIconName(kind, eventDefinition)?.let { return forPaletteItem(it) }
        return forKind(kind)
    }

    private fun specificIconName(kind: BpmnElementKind, eventDefinition: String?): String? = when (kind) {
        BpmnElementKind.START_EVENT -> "StartEvent"

        BpmnElementKind.END_EVENT -> "EndEvent"

        BpmnElementKind.BOUNDARY_EVENT -> "BoundaryEvent"

        BpmnElementKind.INTERMEDIATE_CATCH_EVENT, BpmnElementKind.INTERMEDIATE_THROW_EVENT ->
            when (eventDefinition) {
                "timer" -> "TimerEvent"
                "message" -> "MessageEvent"
                else -> null
            }

        BpmnElementKind.USER_TASK -> "UserTask"

        BpmnElementKind.SERVICE_TASK -> "ServiceTask"

        BpmnElementKind.SCRIPT_TASK -> "ScriptTask"

        BpmnElementKind.BUSINESS_RULE_TASK -> "BusinessRuleTask"

        BpmnElementKind.RECEIVE_TASK -> "ReceiveTask"

        BpmnElementKind.CALL_ACTIVITY -> "CallActivity"

        BpmnElementKind.SUB_PROCESS -> "SubProcess"

        BpmnElementKind.EXCLUSIVE_GATEWAY -> "ExclusiveGateway"

        BpmnElementKind.PARALLEL_GATEWAY -> "ParallelGateway"

        BpmnElementKind.INCLUSIVE_GATEWAY -> "InclusiveGateway"

        BpmnElementKind.EVENT_BASED_GATEWAY -> "EventGateway"

        else -> null
    }

    /** 分類ごとの汎用アイコン。描き起こしたものが無いときの受け皿。 */
    fun forKind(kind: BpmnElementKind): Icon = when {
        kind.isSubProcess -> SubProcess
        kind.isPoolOrLane -> Process
        kind.category == BpmnCategory.EVENT -> Event
        kind.category == BpmnCategory.GATEWAY -> Gateway
        kind.category == BpmnCategory.ACTIVITY -> Task
        else -> BpmnFile
    }

    private fun load(name: String): Icon = IconLoader.getIcon("/icons/$name.svg", FlowableIcons::class.java)
}
