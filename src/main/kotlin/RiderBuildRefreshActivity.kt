package com.wtt.rideridhighlighter

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rider's .NET builds use its RD build host rather than the platform BuildViewManager. */
class RiderBuildRefreshActivity : ProjectActivity {
    override suspend fun execute(project: Project) = withContext(Dispatchers.EDT) {
        try {
            // Loaded only through the optional Rider descriptor. Keep the main
            // plugin compilable against the Platform/JSON SDK used by IDEA too.
            val loader = javaClass.classLoader
            val hostClass = Class.forName("com.jetbrains.rider.build.BuildHost", true, loader)
            val lifetimeClass = Class.forName("com.jetbrains.rd.util.lifetime.Lifetime", true, loader)
            val definitionClass = Class.forName("com.jetbrains.rd.util.lifetime.LifetimeDefinition", true, loader)
            val companion = hostClass.getField("Companion").get(null)
            val host = companion.javaClass.getMethod("getInstance", Project::class.java).invoke(companion, project)
            val building = hostClass.getMethod("getBuilding").invoke(host)
            val lifetime = definitionClass.getConstructor().newInstance()
            val terminate = definitionClass.getMethod("terminate", Boolean::class.javaPrimitiveType)
            val names = project.service<WttProjectNames>()
            Disposer.register(names) { terminate.invoke(lifetime, false) }
            val transition = BuildCompletionTransition { names.refreshFromIndex() }
            val handler: (Boolean) -> Unit = transition::update
            building.javaClass.getMethod("advise", lifetimeClass, Function1::class.java).invoke(building, lifetime, handler)
        } catch (e: ReflectiveOperationException) {
            Logger.getInstance(RiderBuildRefreshActivity::class.java).warn("Unable to subscribe to Rider build completion", e)
        }
        Unit
    }
}

