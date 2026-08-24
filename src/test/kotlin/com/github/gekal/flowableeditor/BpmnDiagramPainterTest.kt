package com.github.gekal.flowableeditor

import com.github.gekal.flowableeditor.editor.BpmnColors
import com.github.gekal.flowableeditor.editor.BpmnDiagramPainter
import com.github.gekal.flowableeditor.model.BpmnDiagram
import com.github.gekal.flowableeditor.model.BpmnModelParser
import com.intellij.psi.xml.XmlFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.ui.UIUtil
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.roundToInt

/**
 * 描画が例外なく走り、実際に何かが描かれることを確認する。
 *
 * 図形ごとの分岐が多いので、代表的な定義ファイルを最後まで描き切れるかどうかを
 * 見張っておく価値がある。生成した PNG は build/reports/bpmn-render/ に残すので、
 * 目視でも確認できる。
 */
class BpmnDiagramPainterTest : BasePlatformTestCase() {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun render(fileName: String): BufferedImage {
        myFixture.configureByFile(fileName)
        val diagram = BpmnModelParser.parse(myFixture.file as XmlFile)
        assertFalse("$fileName should produce a diagram", diagram.isEmpty)
        return render(diagram, fileName.substringBefore('.'))
    }

    private fun render(diagram: BpmnDiagram, name: String): BufferedImage {
        val extent = requireNotNull(diagram.extent()) { "diagram has no extent" }
        val scale = 2.0
        val margin = 24
        val image = BufferedImage(
            (extent.width * scale).roundToInt() + margin * 2,
            (extent.height * scale).roundToInt() + margin * 2,
            BufferedImage.TYPE_INT_RGB,
        )
        val g = image.createGraphics()
        try {
            g.color = BpmnColors.CANVAS
            g.fillRect(0, 0, image.width, image.height)
            g.translate(margin, margin)
            g.scale(scale, scale)
            g.translate(-extent.x, -extent.y)
            BpmnDiagramPainter(UIUtil.getLabelFont()).paint(g, diagram, null)
        } finally {
            g.dispose()
        }

        val output = File("build/reports/bpmn-render/$name.png")
        output.parentFile.mkdirs()
        ImageIO.write(image, "PNG", output)
        return image
    }

    /** 背景以外の色が使われている = 何かが描かれた。 */
    private fun assertNotBlank(image: BufferedImage) {
        val background = image.getRGB(0, 0)
        for (y in 0 until image.height step 3) {
            for (x in 0 until image.width step 3) {
                if (image.getRGB(x, y) != background) return
            }
        }
        fail("nothing was painted onto the image")
    }

    fun `test renders a definition that carries diagram interchange`() {
        assertNotBlank(render("orderProcessWithDi.bpmn20.xml"))
    }

    fun `test renders an auto laid out definition with a sub process`() {
        assertNotBlank(render("subProcessWithoutDi.bpmn20.xml"))
    }

    fun `test renders every element kind without failing`() {
        assertNotBlank(render("allElementKinds.bpmn20.xml"))
    }
}
