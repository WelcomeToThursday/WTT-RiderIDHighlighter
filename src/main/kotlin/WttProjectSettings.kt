package com.wtt.rideridhighlighter

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.hints.declarative.impl.DeclarativeInlayHintsPassFactory
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.service
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.project.Project
import com.intellij.openapi.editor.EditorFactory
import com.intellij.psi.PsiElement
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Service(Service.Level.PROJECT)
@State(name = "SptIdHighlighter", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
class WttProjectSettings(private val project: Project, private val scope: CoroutineScope) : PersistentStateComponent<WttProjectSettings.Options> {
    data class Options(var databasePath: String = "", var locale: String = "en")

    @Volatile private var options = Options()
    @Volatile private var index = WttDatabaseLoader.Index()
    @Volatile var status: String = "Bundled item names are ready to load."
        private set
    private var generation = 0
    private var requested = false

    override fun getState(): Options = options.copy()

    @Synchronized
    override fun loadState(state: Options) {
        options = state.copy()
        generation++
        index = WttDatabaseLoader.Index()
        requested = false
        status = if (state.databasePath.isBlank()) "Bundled item names are ready to load." else "Not loaded yet."
    }

    fun itemName(id: String, context: PsiElement? = null): String? {
        return definition(id, context)?.displayName(id)
    }

    internal fun definition(id: String, context: PsiElement? = null): WttDefinition? {
        ensureLoaded()
        if (context != null) project.service<WttProjectNames>().definition(id, options.locale, context)?.let { return it }
        return index.definitions[id]
    }

    fun isHandbookCategory(id: String): Boolean {
        ensureLoaded()
        return id in index.handbookIds
    }

    fun ensureLoaded() {
        val (request, currentGeneration) = synchronized(this) {
            if (requested || project.isDisposed) {
                return
            }
            requested = true
            status = "Loading item names…"
            options.copy() to generation
        }
        // The injected scope belongs to the project, not the short-lived annotator pass.
        // It also keeps all file IO outside the highlighting read action.
        scope.launch(Dispatchers.IO) {
            val bundled = request.databasePath.isBlank()
            var loaded = WttDatabaseLoader.Index()
            var failure: String? = null
            try {
                val checkCanceled = { coroutineContext.ensureActive() }
                loaded = if (bundled) {
                    WttDatabaseLoader.loadBundledIndex(request.locale, checkCanceled)
                } else {
                    WttDatabaseLoader.loadIndex(request.databasePath, request.locale, checkCanceled)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.intellij.openapi.progress.ProcessCanceledException) {
                throw e
            } catch (e: Exception) {
                failure = "Unable to load item names: ${e.message}"
            }
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) {
                    return@withContext
                }
                synchronized(this@WttProjectSettings) {
                    if (generation != currentGeneration) {
                        return@withContext
                    }
                    index = loaded
                    refreshEditorHints()
                    status = failure ?: "Loaded ${loaded.names.size} item/category names, ${loaded.quests.size} quests and ${loaded.traders.size} traders (${request.locale}, ${if (bundled) "bundled" else "local database"})."
                }
            }
        }
    }

    fun configure(path: String, locale: String) {
        loadState(Options(path.trim(), locale.trim()))
        ensureLoaded()
        if (!project.isDisposed) {
            refreshEditorHints()
        }
    }

    internal fun refreshEditorHints() {
        // Inlay passes cache the PSI modification count. A database change does not
        // edit the file, so invalidate the hints as well as restarting annotations.
        for (editor in EditorFactory.getInstance().allEditors) {
            if (editor.project == project) {
                DeclarativeInlayHintsPassFactory.scheduleRecompute(editor, project)
            }
        }
        DaemonCodeAnalyzer.getInstance(project).restart(this)
    }
}
