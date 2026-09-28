package com.wtt.rideridhighlighter

import com.intellij.json.psi.JsonFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.smartReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.openapi.util.SimpleModificationTracker
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.indexing.FileBasedIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds

@Service(Service.Level.PROJECT)
class WttProjectNames(private val project: Project, private val scope: CoroutineScope) : Disposable {
    private var refreshJob: Job? = null
    private var indexRefreshJob: Job? = null
    @Volatile internal var completedIndexRefreshes = 0
        private set
    private data class LookupKey(val id: String, val locale: String, val localFile: VirtualFile?)
    private val changes = SimpleModificationTracker()
    private val results = CachedValuesManager.getManager(project).createCachedValue {
        // Each generation owns a different cache. A lookup already in flight can
        // only populate the old generation, never put stale names into the new one.
        CachedValueProvider.Result.create(
            WttResultCache<LookupKey, WttDefinition>(),
            PsiModificationTracker.MODIFICATION_COUNT,
            ProjectRootModificationTracker.getInstance(project),
            DumbService.getInstance(project).modificationTracker,
            changes,
        )
    }

    init {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val file = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (isJson(file.name)) {
                    queueRefresh()
                }
            }
        }, this)
        val connection = project.messageBus.connect(this)
        connection.subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
            override fun after(events: List<VFileEvent>) {
                if (events.any { isJson(it.path) 
                            || it.file?.isDirectory == true 
                            || (it is VFilePropertyChangeEvent && it.propertyName == "name" && isJson(it.oldValue.toString())) 
                }) queueRefresh()
            }
        })
        connection.subscribe(DumbService.DUMB_MODE, object : DumbService.DumbModeListener {
            override fun exitDumbMode() = queueRefresh()
        })
    }

    fun itemName(id: String, locale: String, context: PsiElement): String? {
        return definition(id, locale, context)?.displayName(id)
    }

    internal fun definition(id: String, locale: String, context: PsiElement): WttDefinition? {
        val file = context.containingFile as? JsonFile
        val localDefinition = if (file != null) {
            val definitions = CachedValuesManager.getCachedValue(file) {
                CachedValueProvider.Result.create(WttItemNameIndex.WttItemNameIndexObject.readDefinitions(file), file)
            }
            definitions[id]
        } else null
        if (DumbService.isDumb(project)) {
            return localDefinition?.let {
                resolveDefinition(id, it, locale, useIndex = false)
            }
        }
        return try {
            // Keep same-file overrides separate, including their declared locale order.
            val localFile = if (localDefinition != null) file!!.viewProvider.virtualFile else null
            results.value.getOrCompute(LookupKey(id, locale, localFile)) {
                if (localDefinition != null) resolveDefinition(id, localDefinition, locale)
                else {
                    // Identical duplicates are harmless; conflicting names stay unresolved.
                    resolveProjectDefinition(id, locale)
                }
            }
        } catch (_: IndexNotReadyException) {
            // A temporarily unavailable index is not a genuine miss and is never cached.
            localDefinition?.let { resolveDefinition(id, it, locale, useIndex = false) }
        }
    }

    private fun resolveProjectDefinition(id: String, locale: String): WttDefinition? =
        FileBasedIndex.getInstance().getValues(WttItemNameIndex.WttItemNameIndexObject.NAME, id, GlobalSearchScope.projectScope(project))
            .mapNotNull { resolveDefinition(id, it, locale) }.distinct().singleOrNull()

    /** One coalesced query of the project index per open/build event, with no polling. */
    @Synchronized
    internal fun refreshFromIndex(): Job {
        changes.incModificationCount()
        indexRefreshJob?.cancel()
        return scope.launch {
            delay(300.milliseconds)
            smartReadAction(project) {
                val keys = mutableListOf<String>()
                FileBasedIndex.getInstance().processAllKeys(WttItemNameIndex.WttItemNameIndexObject.NAME, { key ->
                    if (WttId.normalize(key) != null) {
                        keys.add(key)
                    }
                    true
                }, GlobalSearchScope.projectScope(project), null)
                val locale = project.service<WttProjectSettings>().state.locale
                val cache = results.value
                // Collect keys first: do not nest index access inside processAllKeys.
                for (id in keys) {
                    com.intellij.openapi.progress.ProgressManager.checkCanceled()
                    cache.getOrCompute(LookupKey(id, locale, null)) {
                        resolveProjectDefinition(id, locale)
                    }
                }
            }
            withContext(Dispatchers.EDT) {
                if (!project.isDisposed) {
                    completedIndexRefreshes++
                    project.service<WttProjectSettings>().refreshEditorHints()
                }
            }
        }.also { indexRefreshJob = it }
    }

    private fun resolveDefinition(id: String, entry: Map<String, String>, locale: String, useIndex: Boolean = true): WttDefinition? {
        val kind = WttDefinitionKind.entries.firstOrNull { it.label != null && it.label == entry["!kind"] }
            ?: return localizedName(entry, locale)?.let { WttDefinition(it, WttDefinitionKind.ITEM) }
        val fallback = entry["!default"] ?: id
        val localeKey = entry["!localeKey"]
        if (!useIndex || localeKey == null) return WttDefinition(localizedName(entry, locale) ?: fallback, kind)
        val locales = FileBasedIndex.getInstance().getValues(WttItemNameIndex.WttItemNameIndexObject.NAME, "@locale:$localeKey", GlobalSearchScope.projectScope(project))
        val selected = locales.mapNotNull { it[locale] }.distinct()
        val english = locales.mapNotNull { it["en"] }.distinct()
        val name = when {
            selected.isNotEmpty() -> selected.singleOrNull()
            entry[locale] != null -> entry[locale]
            english.isNotEmpty() -> english.singleOrNull()
            entry["en"] != null -> entry["en"]
            else -> locales.flatMap { it.values }.distinct().singleOrNull()
        } ?: localizedName(entry, locale) ?: fallback
        return WttDefinition(name, kind)
    }

    private fun localizedName(names: Map<String, String>?, locale: String): String? =
        names?.get(locale) ?: names?.entries?.firstOrNull { !it.key.startsWith('!') }?.value

    @Synchronized
    private fun queueRefresh() {
        changes.incModificationCount()
        refreshJob?.cancel()
        refreshJob = scope.launch {
            delay(300.milliseconds)
            withContext(Dispatchers.EDT) {
                if (!project.isDisposed) PsiDocumentManager.getInstance(project).performWhenAllCommitted {
                    if (!project.isDisposed) project.service<WttProjectSettings>().refreshEditorHints()
                }
            }
        }
    }

    private fun isJson(path: String) = path.endsWith(".json", true) || path.endsWith(".jsonc", true)
    override fun dispose() {
        refreshJob?.cancel()
        indexRefreshJob?.cancel()
    }
}
