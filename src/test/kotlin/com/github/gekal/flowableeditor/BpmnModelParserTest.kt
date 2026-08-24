package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.model.BpmnConnectionKind
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnElementKind
import com.github.gekal.flowableeditor.model.BpmnModelParser
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BpmnModelParserTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun parse(fileName: String): BpmnDiagram {
        myFixture.configureByFile(fileName)
        return BpmnModelParser.parse(myFixture.file as XmlFile)
    }

    fun `test reads elements and flows from a process`() {
        val diagram = parse("orderProcessWithDi.bpmn20.xml")

        assertEquals(listOf("Order"), diagram.processNames)
        assertEquals(5, diagram.nodes.size)
        assertEquals(5, diagram.edges.size)

        val approve = diagram.nodesById.getValue("approve")
        assertEquals(BpmnElementKind.USER_TASK, approve.kind)
        assertEquals("Approve order", approve.name)
        assertEquals("orderProcess", approve.parentId)

        assertEquals(BpmnElementKind.EXCLUSIVE_GATEWAY, diagram.nodesById.getValue("decision").kind)
        assertEquals(BpmnElementKind.SERVICE_TASK, diagram.nodesById.getValue("ship").kind)
    }

    fun `test uses diagram interchange coordinates when present`() {
        val diagram = parse("orderProcessWithDi.bpmn20.xml")

        assertTrue(diagram.hasDiagramInterchange)
        val approve = diagram.nodesById.getValue("approve").bounds!!
        assertEquals(180.0, approve.x)
        assertEquals(75.0, approve.y)
        assertEquals(100.0, approve.width)
        assertEquals(80.0, approve.height)
    }

    fun `test marks conditional and default sequence flows`() {
        val diagram = parse("orderProcessWithDi.bpmn20.xml")
        val byId = diagram.edges.associateBy { it.id }

        assertTrue("flow3 has a conditionExpression", byId.getValue("flow3").hasCondition)
        assertTrue("flow4 is the gateway default", byId.getValue("flow4").isDefaultFlow)
        assertFalse(byId.getValue("flow3").isDefaultFlow)
        assertEquals(BpmnConnectionKind.SEQUENCE_FLOW, byId.getValue("flow1").kind)
    }

    fun `test routes edges that have no waypoints in the file`() {
        val diagram = parse("orderProcessWithDi.bpmn20.xml")

        // flow1 だけ BPMNEdge がある。残りはルータが補う。
        assertEquals(2, diagram.edges.first { it.id == "flow1" }.waypoints.size)
        assertTrue(diagram.edges.all { it.waypoints.size >= 2 })
    }

    fun `test lays out definitions that have no diagram interchange`() {
        val diagram = parse("subProcessWithoutDi.bpmn20.xml")

        assertFalse(diagram.hasDiagramInterchange)
        assertTrue("every node gets a position", diagram.nodes.all { it.bounds != null })

        // 自動レイアウトは左から右に並べる
        val start = diagram.nodesById.getValue("start").bounds!!
        val review = diagram.nodesById.getValue("review").bounds!!
        val end = diagram.nodesById.getValue("end").bounds!!
        assertTrue("start is left of the sub process", start.x < review.x)
        assertTrue("sub process is left of the end event", review.x < end.x)
    }

    fun `test nests sub process children inside their container`() {
        val diagram = parse("subProcessWithoutDi.bpmn20.xml")

        val review = diagram.nodesById.getValue("review").bounds!!
        val innerTask = diagram.nodesById.getValue("innerTask")

        assertEquals("review", innerTask.parentId)
        val inner = innerTask.bounds!!
        assertTrue("child sits inside the sub process box", inner.x >= review.x && inner.right <= review.right)
        assertTrue(inner.y >= review.y && inner.bottom <= review.bottom)
    }

    fun `test attaches boundary events to their host activity`() {
        val diagram = parse("subProcessWithoutDi.bpmn20.xml")

        val timeout = diagram.nodesById.getValue("timeout")
        assertEquals(BpmnElementKind.BOUNDARY_EVENT, timeout.kind)
        assertEquals("review", timeout.attachedToRef)
        assertEquals("timer", timeout.eventDefinition)

        val host = diagram.nodesById.getValue("review").bounds!!
        val bounds = timeout.bounds!!
        assertTrue("boundary event sits on the host border", bounds.centerX in host.x..host.right)
        assertEquals(host.bottom, bounds.centerY, 0.5)
    }

    fun `test ignores files that are not bpmn`() {
        myFixture.configureByText("plain.xml", "<root><child/></root>")
        val diagram = BpmnModelParser.parse(myFixture.file as XmlFile)

        assertTrue(diagram.isEmpty)
    }
}
