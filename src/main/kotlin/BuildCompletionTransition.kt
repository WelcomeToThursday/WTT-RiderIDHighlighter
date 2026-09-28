package com.wtt.rideridhighlighter

/** Ignore initial/duplicate idle notifications; emit only one event per real build. */
internal class BuildCompletionTransition(private val completed: () -> Unit) {
    private var building = false
    @Synchronized fun update(value: Boolean) {
        val finished = building && !value
        building = value
        if (finished) completed()
    }
}