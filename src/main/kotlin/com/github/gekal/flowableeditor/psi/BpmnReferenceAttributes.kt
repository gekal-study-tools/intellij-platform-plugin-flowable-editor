package com.github.gekal.flowableeditor.psi

/**
 * 「同じファイル内の id を指す」属性の一覧。
 * ここに載っている属性だけが参照解決・補完・未解決検査の対象になる。
 */
object BpmnReferenceAttributes {

    val ID_REFERENCES: Set<String> = setOf(
        // シーケンスフロー / メッセージフロー / 関連
        "sourceRef",
        "targetRef",
        // 境界イベントの貼り付け先
        "attachedToRef",
        // ゲートウェイ・アクティビティのデフォルトフロー
        "default",
        // ルート要素への参照
        "errorRef",
        "signalRef",
        "messageRef",
        "escalationRef",
        "processRef",
        // データ関連
        "dataObjectRef",
        "categoryValueRef",
        // BPMNDI から図形が指す業務要素
        "bpmnElement",
    )

    fun isReferenceAttribute(name: String): Boolean = name in ID_REFERENCES
}
