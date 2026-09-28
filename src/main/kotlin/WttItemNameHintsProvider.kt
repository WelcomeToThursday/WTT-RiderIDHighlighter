package com.wtt.rideridhighlighter

import com.intellij.codeInsight.hints.declarative.HintColorKind
import com.intellij.codeInsight.hints.declarative.HintFormat
import com.intellij.codeInsight.hints.declarative.InlayHintsProvider
import com.intellij.codeInsight.hints.declarative.InlayTreeSink
import com.intellij.codeInsight.hints.declarative.EndOfLinePosition
import com.intellij.codeInsight.hints.declarative.SharedBypassCollector
import com.intellij.json.psi.JsonFile
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.DumbAware
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class WttItemNameHintsProvider : InlayHintsProvider, DumbAware {
    override fun createCollector(file: PsiFile, editor: Editor): SharedBypassCollector? {
        if (file !is JsonFile) return null
        val database = file.project.service<WttProjectSettings>()
        database.ensureLoaded()
        return object : SharedBypassCollector {
            override fun collectFromElement(element: PsiElement, sink: InlayTreeSink) {
                val id = WttId.fromJsonLiteral(element) ?: return
                val name = database.itemName(id, element)?.takeUnless { it == id } ?: return
                val offset = element.textRange.startOffset
                val line = editor.document.getLineNumber(offset)
                sink.addPresentation(
                    // After-line-end inlays keep all JSON and comments together.
                    // Earlier IDs have higher priority so shared-line hints follow source order.
                    EndOfLinePosition(line, priority = -offset),
                    tooltip = "SPT name: $name ($id)",
                    hintFormat = HintFormat.default.withColorKind(HintColorKind.TextWithoutBackground)
                ) {
                    // The platform truncates long text nodes. Separate words retain
                    // the full item name while using the standard inlay renderer.
                    name.split(' ').forEachIndexed { index, word ->
                        text(if (index == 0) word else " $word")
                    }
                }
            }
        }
    }
}
