package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.inspection.BpmnDisconnectedElementInspection
import com.github.gekal.flowableeditor.inspection.BpmnDuplicateIdInspection
import com.github.gekal.flowableeditor.inspection.BpmnMissingIdInspection
import com.github.gekal.flowableeditor.inspection.BpmnProcessStructureInspection
import com.github.gekal.flowableeditor.inspection.BpmnTaskImplementationInspection
import com.github.gekal.flowableeditor.inspection.BpmnUnresolvedReferenceInspection
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BpmnInspectionTest : BasePlatformTestCase() {

    private fun highlight(inspection: LocalInspectionTool, body: String): List<String> {
        myFixture.enableInspections(inspection)
        myFixture.configureByText("test.bpmn20.xml", definitions(body))
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    private fun definitions(body: String) = """
        <?xml version="1.0" encoding="UTF-8"?>
        <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                     xmlns:flowable="http://flowable.org/bpmn"
                     targetNamespace="http://flowable.org/processdef">
        $body
        </definitions>
    """.trimIndent()

    fun `test reports a sequence flow that points at a missing element`() {
        val problems = highlight(
            BpmnUnresolvedReferenceInspection(),
            """
            <process id="p" isExecutable="true">
              <startEvent id="start"/>
              <sequenceFlow id="f1" sourceRef="start" targetRef="doesNotExist"/>
              <endEvent id="end"/>
            </process>
            """.trimIndent(),
        )

        assertTrue(problems.toString(), problems.any { it.contains("doesNotExist") })
        assertFalse(problems.toString(), problems.any { it.contains("'start'") })
    }

    fun `test accepts references that resolve`() {
        val problems = highlight(
            BpmnUnresolvedReferenceInspection(),
            """
            <process id="p" isExecutable="true">
              <startEvent id="start"/>
              <sequenceFlow id="f1" sourceRef="start" targetRef="end"/>
              <endEvent id="end"/>
            </process>
            """.trimIndent(),
        )

        assertTrue(problems.toString(), problems.none { it.contains("Cannot resolve") })
    }

    fun `test reports a duplicated id once`() {
        val problems = highlight(
            BpmnDuplicateIdInspection(),
            """
            <process id="p" isExecutable="true">
              <startEvent id="twice"/>
              <endEvent id="twice"/>
            </process>
            """.trimIndent(),
        )

        assertEquals(
            problems.toString(),
            1,
            problems.count { it.contains("twice") },
        )
    }

    fun `test reports a process without a start event`() {
        val problems = highlight(
            BpmnProcessStructureInspection(),
            """
            <process id="p" isExecutable="true">
              <userTask id="task"/>
              <endEvent id="end"/>
            </process>
            """.trimIndent(),
        )

        assertTrue(problems.toString(), problems.any { it.contains("no start event") })
        assertFalse(problems.toString(), problems.any { it.contains("no end event") })
    }

    fun `test reports flow elements with no incoming or outgoing flow`() {
        val problems = highlight(
            BpmnDisconnectedElementInspection(),
            """
            <process id="p" isExecutable="true">
              <startEvent id="start"/>
              <sequenceFlow id="f1" sourceRef="start" targetRef="end"/>
              <userTask id="orphan"/>
              <endEvent id="end"/>
            </process>
            """.trimIndent(),
        )

        assertTrue(problems.toString(), problems.any { it.contains("orphan") && it.contains("no incoming") })
        assertTrue(problems.toString(), problems.any { it.contains("orphan") && it.contains("no outgoing") })
        assertFalse(problems.toString(), problems.any { it.contains("'start'") && it.contains("no incoming") })
    }

    fun `test reports a service task without an implementation`() {
        val problems = highlight(
            BpmnTaskImplementationInspection(),
            """
            <process id="p" isExecutable="true">
              <serviceTask id="unconfigured"/>
              <serviceTask id="configured" flowable:class="com.example.Delegate"/>
            </process>
            """.trimIndent(),
        )

        assertEquals(
            problems.toString(),
            1,
            problems.count { it.contains("Service task") },
        )
    }

    fun `test generates an id for an element that has none`() {
        myFixture.enableInspections(BpmnMissingIdInspection())
        myFixture.configureByText(
            "test.bpmn20.xml",
            definitions(
                """
                <process id="p" isExecutable="true">
                  <userTask id="userTask_1"/>
                  <userTask name="no id yet"/>
                </process>
                """.trimIndent(),
            ),
        )

        val fix = myFixture.getAllQuickFixes().firstOrNull { it.text.contains("Generate element id") }
        assertNotNull("the quick fix is offered", fix)
        myFixture.launchAction(fix!!)

        // userTask_1 は使用済みなので次の空き番号が選ばれる
        assertTrue(myFixture.file.text, myFixture.file.text.contains("""id="userTask_2""""))
    }
}
