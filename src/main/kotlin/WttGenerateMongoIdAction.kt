package com.wtt.rideridhighlighter

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorWriteActionHandler

class WttGenerateMongoIdAction : EditorAction(object : EditorWriteActionHandler(true) {
    override fun executeWriteAction(editor: Editor, caret: Caret?, dataContext: DataContext) {
        val currentCaret = caret ?: editor.caretModel.currentCaret
        val start = if (currentCaret.hasSelection()) { currentCaret.selectionStart } else { currentCaret.offset }
        val end = if (currentCaret.hasSelection()) { currentCaret.selectionEnd } else { currentCaret.offset }
        val id = WttMongoIdGenerator.next()
        editor.document.replaceString(start, end, id)
        currentCaret.removeSelection()
        currentCaret.moveToOffset(start + id.length)
    }
})
