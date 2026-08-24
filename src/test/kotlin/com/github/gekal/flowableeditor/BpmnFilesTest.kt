package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.bpmn.BpmnFiles
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BpmnFilesTest : BasePlatformTestCase() {

    fun `test recognises bpmn file names`() {
        assertTrue(BpmnFiles.hasBpmnFileName("order.bpmn"))
        assertTrue(BpmnFiles.hasBpmnFileName("order.bpmn20.xml"))
        assertTrue(BpmnFiles.hasBpmnFileName("Order.BPMN"))
        assertFalse(BpmnFiles.hasBpmnFileName("order.xml"))
        assertFalse(BpmnFiles.hasBpmnFileName("bpmn"))
    }

    fun `test recognises bpmn content in a plain xml file`() {
        val bpmn = myFixture.configureByText(
            "process.xml",
            """
            <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" targetNamespace="x">
              <process id="p"/>
            </definitions>
            """.trimIndent(),
        )
        assertTrue(BpmnFiles.isBpmnFile(bpmn))
        assertTrue(BpmnFiles.isBpmnFile(bpmn.virtualFile))
    }

    fun `test rejects unrelated xml`() {
        val other = myFixture.configureByText("beans.xml", "<beans><bean id=\"a\"/></beans>")
        assertFalse(BpmnFiles.isBpmnFile(other))
        assertFalse(BpmnFiles.isBpmnFile(other.virtualFile))
    }
}
