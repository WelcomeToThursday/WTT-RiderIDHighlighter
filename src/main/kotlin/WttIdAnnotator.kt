package com.wtt.rideridhighlighter

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement

class WttIdAnnotator : Annotator, DumbAware {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        // Match a complete, literal ID; don't color substrings, escapes, or incomplete strings.
        val id = WttId.fromJsonLiteral(element) ?: return
        val settings = element.project.service<WttProjectSettings>()
        val definition = settings.definition(id, element)
        val name = definition?.displayName(id)
        val attributes = when {
            definition?.kind == WttDefinitionKind.HANDBOOK -> WttColorSettingsPage.WttColorSettingsPageObject.HANDBOOK_ID
            (definition == null || definition.kind == WttDefinitionKind.ITEM) && WttId.isRootItemId(element) -> WttColorSettingsPage.WttColorSettingsPageObject.ROOT_ID
            else -> WttColorSettingsPage.WttColorSettingsPageObject.ID
        }
        val tooltip = if (name != null && name != id) "SPT name: ${StringUtil.escapeXmlEntities(name)}<br><code>$id</code>"
            else "SPT ID: $id<br>No definition name found for this ID."
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(TextRange(element.textRange.startOffset + 1, element.textRange.endOffset - 1))
            .textAttributes(attributes)
            .tooltip("<html>$tooltip</html>")
            .create()
    }
}
