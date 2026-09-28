package com.wtt.rideridhighlighter

import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.openapi.project.Project
import java.awt.BorderLayout
import java.awt.GridLayout
import javax.swing.JButton
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextField
import javax.swing.Timer

class WttSettingsConfigurable(project: Project) : Configurable {
    private val settings = project.service<WttProjectSettings>()
    private val path = JTextField(45)
    private val localeField = JTextField(8)
    private val status = JLabel()
    private var timer: Timer? = null

    override fun getDisplayName() = "WTT ID Highlighter"

    override fun createComponent(): JPanel {
        val browse = JButton("Browse…").apply {
            addActionListener {
                val chooser = JFileChooser().apply {
                    fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                }
                if (chooser.showOpenDialog(path) == JFileChooser.APPROVE_OPTION) {
                    path.text = chooser.selectedFile.absolutePath
                }
            }
        }
        val reload = JButton("Reload database").apply {
            addActionListener {
                val saved = settings.state
                settings.configure(saved.databasePath, saved.locale)
                status.text = settings.status
            }
        }
        val fields = JPanel(GridLayout(0, 1, 0, 8)).apply {
            add(JLabel("Optional SPT database folder (leave blank to use bundled names):"))
            add(JPanel(BorderLayout(8, 0)).apply {
                add(path, BorderLayout.CENTER); add(browse, BorderLayout.EAST)
            })
            add(JLabel("Locale code (for example en, fr, or de):"))
            add(localeField)
            add(JLabel("Apply folder/locale changes first. Reload rereads the saved database."))
            add(reload)
            add(status)
        }
        reset()
        timer?.stop()
        timer = Timer(500) { status.text = settings.status }.apply { start() }
        settings.ensureLoaded()
        return JPanel(BorderLayout()).apply {
            add(fields, BorderLayout.NORTH)
        }
    }

    override fun isModified(): Boolean = settings.state.let {
        path.text.trim() != it.databasePath || localeField.text.trim() != it.locale
    }

    override fun apply() {
        if (!localeField.text.trim().matches(Regex("[a-zA-Z0-9_-]+"))) {
            throw ConfigurationException("Enter a locale code such as en or fr.")
        }
        settings.configure(path.text, localeField.text)
        status.text = settings.status
    }

    override fun reset() {
        path.text = settings.state.databasePath
        localeField.text = settings.state.locale
        status.text = settings.status
    }

    override fun disposeUIResources() {
        timer?.stop(); timer = null
    }
}
