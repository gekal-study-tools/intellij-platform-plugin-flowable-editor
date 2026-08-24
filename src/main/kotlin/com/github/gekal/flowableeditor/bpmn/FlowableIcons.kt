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

    /** 構造ビューで要素の種類に応じたアイコンを選ぶ。 */
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
