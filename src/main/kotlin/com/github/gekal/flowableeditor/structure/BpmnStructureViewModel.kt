package com.github.gekal.flowableeditor.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

class BpmnStructureViewModel(file: XmlFile) :
    StructureViewModelBase(file, BpmnFileTreeElement(file)),
    StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(XmlTag::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = false
}
