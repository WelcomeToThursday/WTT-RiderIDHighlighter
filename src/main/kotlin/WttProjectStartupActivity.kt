package com.wtt.rideridhighlighter

import com.intellij.build.BuildViewManager
import com.intellij.build.events.FinishBuildEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class WttProjectStartupActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        val names = project.service<WttProjectNames>()
        project.service<WttProjectSettings>().ensureLoaded()
        withContext(Dispatchers.EDT) {
            project.service<BuildViewManager>().addListener({ _, event ->
                // Only the build's terminal event, never individual compiler messages.
                if (event is FinishBuildEvent) names.refreshFromIndex()
            }, names)
        }
        names.refreshFromIndex()
    }
}
