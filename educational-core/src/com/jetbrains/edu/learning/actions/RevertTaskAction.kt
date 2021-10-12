package com.jetbrains.edu.learning.actions

import com.intellij.ide.projectView.ProjectView
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.RightAlignedToolbarAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.EmptyIcon
import com.jetbrains.edu.EducationalCoreIcons
import com.jetbrains.edu.learning.EduUtils
import com.jetbrains.edu.learning.courseFormat.ext.revertTaskFiles
import com.jetbrains.edu.learning.courseFormat.ext.revertTaskParameters
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.messages.EduCoreBundle.lazyMessage
import com.jetbrains.edu.learning.placeholderDependencies.PlaceholderDependencyManager.updateDependentPlaceholders
import com.jetbrains.edu.learning.projectView.ProgressUtil.updateCourseProgress
import com.jetbrains.edu.learning.statistics.EduCounterUsageCollector.Companion.revertTask
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionView
import com.jetbrains.edu.learning.yaml.YamlFormatSynchronizer
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.VisibleForTesting

class RevertTaskAction : DumbAwareAction(lazyMessage("action.reset.request"),
                                         lazyMessage("action.reset.to.initial.state"),
                                         EducationalCoreIcons.ResetTask), RightAlignedToolbarAction {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val result = MessageDialogBuilder.yesNo(EduCoreBundle.message("action.reset.request"),
                                            EduCoreBundle.message("action.reset.progress.dropped")).ask(project)
    if (!result) return
    revert(project)
    revertTask()
  }

  override fun update(e: AnActionEvent) {
    EduUtils.updateAction(e)
    val project = e.project ?: return
    val task = EduUtils.getCurrentTask(project) ?: return

    if (!task.course.isStudy) {
      val presentation = e.presentation
      presentation.isEnabledAndVisible = false
    }
  }

  companion object {
    const val ACTION_ID: @NonNls String = "Educational.RefreshTask"

    @VisibleForTesting
    fun revert(project: Project) {
      val task = EduUtils.getCurrentTask(project) ?: return
      task.apply {
        revertTaskFiles(project)
        revertTaskParameters(project)
        YamlFormatSynchronizer.saveItem(this)
      }

      updateDependentPlaceholders(project, task)
      EditorNotifications.getInstance(project).updateAllNotifications()
      Notification("EduTools", EmptyIcon.ICON_16, "", "", EduCoreBundle.message("action.reset.result"),
                   NotificationType.INFORMATION, null).notify(project)
      ProjectView.getInstance(project).refresh()
      TaskDescriptionView.getInstance(project).updateTaskSpecificPanel()
      TaskDescriptionView.getInstance(project).readyToCheck()
      updateCourseProgress(project)
    }
  }
}