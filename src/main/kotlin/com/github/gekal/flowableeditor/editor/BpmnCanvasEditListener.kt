package com.github.gekal.flowableeditor.editor

import com.github.gekal.flowableeditor.edit.BpmnPaletteItem
import com.github.gekal.flowableeditor.model.BpmnBounds

/**
 * キャンバス上の操作を受け取る側。
 *
 * キャンバス自身は XML を知らない。ここで受けた要求を
 * [com.github.gekal.flowableeditor.edit.BpmnDocumentEditor] が書き戻し、
 * 図はドキュメントの変更をきっかけに組み直される。
 * 未設定なら図は読み取り専用として振る舞う。
 */
interface BpmnCanvasEditListener {

    /** 図形を動かした / 大きさを変えた。 */
    fun onBoundsChanged(elementId: String, bounds: BpmnBounds, isResize: Boolean)

    /** 図形から図形へ線を引いた。 */
    fun onConnect(sourceId: String, targetId: String)

    /**
     * パレットの要素を置いた。
     *
     * [containerId] は落とした先のサブプロセスなど。
     * [attachToId] は境界イベントの貼り付け先。
     */
    fun onCreate(item: BpmnPaletteItem, bounds: BpmnBounds, containerId: String?, attachToId: String? = null)

    /** 要素を消した。 */
    fun onDelete(elementIds: List<String>)

    /** 名前を変えた。 */
    fun onRename(elementId: String, name: String)
}
