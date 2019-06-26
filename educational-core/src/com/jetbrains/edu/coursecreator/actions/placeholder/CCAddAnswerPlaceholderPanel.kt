package com.jetbrains.edu.coursecreator.actions.placeholder

import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import java.awt.event.FocusAdapter
import java.awt.event.FocusEvent
import javax.swing.*

class CCAddAnswerPlaceholderPanel(placeholderText: String) : JPanel() {
  private val HELP_TEXT = "Placeholder is shown to a student in place of selected code"
  private val panel: JPanel
  private val textArea: JTextArea = JTextArea(placeholderText, 3, 1)

  init {
    layout = BoxLayout(this, BoxLayout.PAGE_AXIS)

    val label = JLabel(HELP_TEXT)
    label.foreground = JBColor.GRAY
    label.border = JBUI.Borders.emptyTop(5)

    textArea.border = BorderFactory.createLineBorder(JBColor.border())
    textArea.addFocusListener(object : FocusAdapter() {
      override fun focusGained(e: FocusEvent?) {
        textArea.selectAll()
      }
    })
    textArea.font = UIUtil.getLabelFont()

    val scrollPane = JBScrollPane(textArea)
    scrollPane.border = null
    scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER

    panel = panel {
      row { scrollPane() }
      row { label() }
    }
    panel.minimumSize = JBUI.size(430, 100)
    panel.alignmentX = Component.LEFT_ALIGNMENT
    add(panel)
  }

  fun getAnswerPlaceholderText(): String {
    return textArea.text
  }

  fun getPreferredFocusedComponent(): JComponent {
    return textArea
  }
}