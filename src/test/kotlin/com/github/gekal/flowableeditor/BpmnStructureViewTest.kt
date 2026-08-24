package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.structure.BpmnStructureViewBuilderProvider
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class BpmnStructureViewTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun rootChildren(fileName: String): List<TreeElement> {
        myFixture.configureByFile(fileName)
        val builder = BpmnStructureViewBuilderProvider()
            .createStructureViewBuilder(myFixture.file as XmlFile)
        assertTrue("a tree based builder is returned", builder is TreeBasedStructureViewBuilder)

        val root = (builder as TreeBasedStructureViewBuilder).createStructureViewModel(null).root
        return root.children.toList()
    }

    private fun labels(elements: List<TreeElement>): List<String?> =
        elements.map { (it as StructureViewTreeElement).presentation.presentableText }

    fun `test shows processes and their flow elements`() {
        val processes = rootChildren("subProcessWithoutDi.bpmn20.xml")

        assertEquals(listOf("Review"), labels(processes))

        val flowElements = labels(processes.single().children.toList())
        // シーケンスフローは意図的に出さない
        assertEquals(listOf("start", "Review", "timeout", "end"), flowElements)
    }

    fun `test nests sub process children`() {
        val process = rootChildren("subProcessWithoutDi.bpmn20.xml").single()
        val subProcess = process.children.first { (it as StructureViewTreeElement).presentation.presentableText == "Review" }

        assertEquals(listOf("innerStart", "Check", "innerEnd"), labels(subProcess.children.toList()))
    }

    fun `test describes the element kind next to the name`() {
        val process = rootChildren("orderProcessWithDi.bpmn20.xml").single()
        val approve = process.children
            .map { it as StructureViewTreeElement }
            .first { it.presentation.presentableText == "Approve order" }

        assertEquals("User Task - approve", approve.presentation.locationString)
    }

    fun `test leaves other xml files to the default structure view`() {
        myFixture.configureByText("beans.xml", "<beans><bean id=\"a\"/></beans>")

        assertNull(
            BpmnStructureViewBuilderProvider().createStructureViewBuilder(myFixture.file as XmlFile),
        )
    }
}
