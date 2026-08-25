package com.github.gekal.flowableeditor

import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import java.io.File

/** テストデータの置き場所。 */
internal const val TEST_DATA_PATH = "src/test/testData"

/**
 * テストデータを読み込んで、編集できるファイルとして開く。
 *
 * `configureByFile` は使わない。書き換えを行うテストが testData の実ファイルまで
 * 書き換えてしまい、以降のテストが汚染された入力を読むことになるため。
 * 内容だけを読み取って、その場でファイルを組み立てる。
 */
internal fun CodeInsightTestFixture.configureFromTestData(fileName: String) {
    configureByText(fileName, File(TEST_DATA_PATH, fileName).readText())
}
