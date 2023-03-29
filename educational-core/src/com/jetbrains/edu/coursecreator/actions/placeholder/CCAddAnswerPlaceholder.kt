package com.jetbrains.edu.coursecreator.actions.placeholder

import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.DocumentUtil
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.learning.EduState
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.PlaceholderPainter.hidePlaceholder
import com.jetbrains.edu.learning.PlaceholderPainter.showPlaceholder
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholder
import com.jetbrains.edu.learning.courseFormat.AnswerPlaceholderDependency.Companion.create
import com.jetbrains.edu.learning.courseFormat.TaskFile
import com.jetbrains.edu.learning.courseFormat.ext.configurator
import com.jetbrains.edu.learning.messages.EduCoreBundle.lazyMessage
import com.jetbrains.edu.learning.messages.EduCoreBundle.message

open class CCAddAnswerPlaceholder : CCAnswerPlaceholderAction(
  lazyMessage("action.add.answer.placeholder.text"),
  lazyMessage("action.add.answer.placeholder.description")
) {
  private fun addPlaceholder(project: Project, state: EduState) {
    val editor = state.editor
    val document = editor.document
    FileDocumentManager.getInstance().saveDocument(editor.document)
    val model = editor.selectionModel
    val offset = if (model.hasSelection()) model.selectionStart else editor.caretModel.offset
    val taskFile = state.taskFile
    val answerPlaceholder = AnswerPlaceholder()
    val index = taskFile.answerPlaceholders.size
    answerPlaceholder.index = index
    answerPlaceholder.taskFile = taskFile
    answerPlaceholder.offset = offset
    val defaultPlaceholderText = defaultPlaceholderText(project)
    answerPlaceholder.placeholderText = defaultPlaceholderText
    val dlg = createDialog(project, answerPlaceholder)
    if (!dlg.showAndGet()) {
      return
    }
    val answerPlaceholderText = dlg.getPlaceholderText()
    var possibleAnswer = if (model.hasSelection()) model.selectedText else defaultPlaceholderText
    if (possibleAnswer == null) {
      possibleAnswer = defaultPlaceholderText
    }
    answerPlaceholder.placeholderText = answerPlaceholderText
    answerPlaceholder.length = possibleAnswer.length
    val dependencyInfo = dlg.getDependencyInfo()
    if (dependencyInfo != null) {
      answerPlaceholder.placeholderDependency = create(answerPlaceholder, dependencyInfo.dependencyPath, dependencyInfo.isVisible)
    }
    if (!model.hasSelection()) {
      DocumentUtil.writeInRunUndoTransparentAction { document.insertString(offset, defaultPlaceholderText) }
    }
    val action = AddAction(project, answerPlaceholder, taskFile, editor)
    EduUtils.runUndoableAction(project, message("action.add.answer.placeholder.text"), action)
  }

  open fun createDialog(project: Project, answerPlaceholder: AnswerPlaceholder): CCCreateAnswerPlaceholderDialog {
    return CCCreateAnswerPlaceholderDialog(project, false, answerPlaceholder)
  }

  internal open class AddAction(
    project: Project,
    private val placeholder: AnswerPlaceholder,
    taskFile: TaskFile,
    editor: Editor
  ) : TaskFileUndoableAction(project, taskFile, editor) {
    override fun performUndo(): Boolean {
      if (taskFile.answerPlaceholders.contains(placeholder)) {
        taskFile.removeAnswerPlaceholder(placeholder)
        taskFile.sortAnswerPlaceholders()
        hidePlaceholder(placeholder)
        return true
      }
      return false
    }

    override fun performRedo() {
      taskFile.addAnswerPlaceholder(placeholder)
      taskFile.sortAnswerPlaceholders()
      showPlaceholder(project, placeholder)
    }
  }

  override fun performAnswerPlaceholderAction(project: Project, state: EduState) {
    addPlaceholder(project, state)
  }

  override fun updatePresentation(eduState: EduState, presentation: Presentation) {
    presentation.isVisible = true
    if (canAddPlaceholder(eduState)) {
      presentation.isEnabled = true
    }
  }

  companion object {
    private fun arePlaceholdersIntersect(taskFile: TaskFile, start: Int, end: Int): Boolean {
      val answerPlaceholders = taskFile.answerPlaceholders
      for (existingAnswerPlaceholder in answerPlaceholders) {
        val twStart = existingAnswerPlaceholder.offset
        val twEnd = existingAnswerPlaceholder.possibleAnswer.length + twStart
        if (start in twStart until twEnd || end in (twStart + 1)..twEnd ||
            twStart in start until end || twEnd in (start + 1)..end) {
          return true
        }
      }
      return false
    }

    private fun defaultPlaceholderText(project: Project): String {
      val course = StudyTaskManager.getInstance(project).course ?: return CCUtils.DEFAULT_PLACEHOLDER_TEXT
      val configurator = course.configurator ?: return CCUtils.DEFAULT_PLACEHOLDER_TEXT
      return configurator.defaultPlaceholderText
    }

    private fun canAddPlaceholder(state: EduState): Boolean {
      val editor = state.editor
      val selectionModel = editor.selectionModel
      val taskFile = state.taskFile
      if (selectionModel.hasSelection()) {
        val start = selectionModel.selectionStart
        val end = selectionModel.selectionEnd
        return !arePlaceholdersIntersect(taskFile, start, end)
      }
      val offset = editor.caretModel.offset
      return taskFile.getAnswerPlaceholder(offset) == null
    }
  }
}
