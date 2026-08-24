package com.github.gekal.flowableeditor

import com.intellij.psi.xml.XmlAttributeValue
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BpmnReferenceTest : BasePlatformTestCase() {

    private val definition = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     targetNamespace="http://flowable.org/processdef">
          <process id="p" isExecutable="true">
            <startEvent id="start"/>
            <sequenceFlow id="f1" sourceRef="start" targetRef="appro<caret>ve"/>
            <userTask id="approve"/>
            <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
            <endEvent id="end"/>
          </process>
        </definitions>
    """.trimIndent()

    fun `test targetRef resolves to the id declaration`() {
        myFixture.configureByText("test.bpmn20.xml", definition)

        val reference = myFixture.getReferenceAtCaretPosition()
        assertNotNull("targetRef carries a reference", reference)

        val resolved = reference!!.resolve()
        assertTrue("resolves to an attribute value", resolved is XmlAttributeValue)
        assertEquals("approve", (resolved as XmlAttributeValue).value)
    }

    fun `test targetRef completes element ids declared in the file`() {
        myFixture.configureByText(
            "test.bpmn20.xml",
            definition.replace("appro<caret>ve", "<caret>"),
        )

        val variants = myFixture.getCompletionVariants("test.bpmn20.xml").orEmpty()
        assertTrue(variants.toString(), variants.containsAll(listOf("start", "approve", "end", "f1", "f2", "p")))
    }
}
