package com.jetbrains.edu.learning.taskDescription.ui.check

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionToolbar
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.jetbrains.edu.learning.EduExperimentalFeatures.HYPERSKILL_DATA_TASKS_SUPPORT
import com.jetbrains.edu.learning.actions.EduActionUtils
import com.jetbrains.edu.learning.actions.LeaveCommentAction
import com.jetbrains.edu.learning.actions.NextTaskAction
import com.jetbrains.edu.learning.actions.RevertTaskAction
import com.jetbrains.edu.learning.checker.CheckResult
import com.jetbrains.edu.learning.checkio.courseFormat.CheckiOMission
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.courseFormat.tasks.TheoryTask
import com.jetbrains.edu.learning.courseFormat.tasks.data.DataTask
import com.jetbrains.edu.learning.isFeatureEnabled
import com.jetbrains.edu.learning.navigation.NavigationUtils
import com.jetbrains.edu.learning.stepik.hyperskill.actions.DownloadDatasetAction
import com.jetbrains.edu.learning.stepik.hyperskill.actions.RetryDataTaskAction
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.taskDescription.addActionLinks
import com.jetbrains.edu.learning.taskDescription.ui.TaskDescriptionView
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class CheckPanel(val project: Project, parentDisposable: Disposable) : JPanel(BorderLayout()) {
  private val checkFinishedPanel: JPanel = JPanel(BorderLayout())
  private val checkActionsPanel: JPanel = JPanel(BorderLayout())
  private val linkPanel = JPanel(BorderLayout())
  private val checkDetailsPlaceholder: JPanel = JPanel(BorderLayout())
  private val checkButtonWrapper = JPanel(BorderLayout())
  private val rightActionsToolbar = JPanel(HorizontalLayout(10))
  private val course = project.course
  private val checkTimeAlarm: Alarm = Alarm(parentDisposable)

  init {
    checkActionsPanel.add(checkButtonWrapper, BorderLayout.WEST)
    checkActionsPanel.add(checkFinishedPanel, BorderLayout.CENTER)
    checkActionsPanel.add(createRightActionsToolbar(), BorderLayout.EAST)
    checkActionsPanel.add(linkPanel, BorderLayout.SOUTH)
    add(checkActionsPanel, BorderLayout.CENTER)
    add(checkDetailsPlaceholder, BorderLayout.SOUTH)
  }

  private fun createRightActionsToolbar(): JPanel {
    rightActionsToolbar.add(createSingleActionToolbar(RevertTaskAction.ACTION_ID))
    rightActionsToolbar.add(createSingleActionToolbar(LeaveCommentAction.ACTION_ID))
    return rightActionsToolbar
  }

  private fun createSingleActionToolbar(actionId: String): JComponent {
    val action = ActionManager.getInstance().getAction(actionId)
    return createSingleActionToolbar(action)
  }

  private fun createSingleActionToolbar(action: AnAction): JComponent {
    val toolbar = ActionManager.getInstance().createActionToolbar(ACTION_PLACE, DefaultActionGroup(action), true)
    //these options affect paddings
    toolbar.layoutPolicy = ActionToolbar.NOWRAP_LAYOUT_POLICY
    toolbar.adjustTheSameSize(true)
    toolbar.setTargetComponent(this)

    val component = toolbar.component
    component.border = JBUI.Borders.empty(5, 0, 0, 0)
    return component
  }

  fun readyToCheck() {
    addActionLinks(course, linkPanel, 10, 3)
    checkFinishedPanel.removeAll()
    checkDetailsPlaceholder.removeAll()
    checkTimeAlarm.cancelAllRequests()
  }

  fun checkStarted() {
    readyToCheck()
    updateBackground()
  }

  fun updateCheckDetails(task: Task, result: CheckResult? = null) {
    checkFinishedPanel.removeAll()
    checkFinishedPanel.addNextTaskButton(task)

    val checkResult = result ?: restoreSavedResult(task)
    if (checkResult != null) {
      linkPanel.removeAll()
      checkDetailsPlaceholder.add(CheckDetailsPanel(project, task, checkResult, checkTimeAlarm), BorderLayout.SOUTH)
    }
    updateBackground()
  }

  private fun restoreSavedResult(task: Task): CheckResult? {
    /**
     * We are not showing old result for CheckiO because we store last successful attempt
     * @see com.jetbrains.edu.learning.checkio.courseFormat.CheckiOMission.setStatus
     */
    if (task is CheckiOMission) return null
    if (task.feedback == null && task.status == CheckStatus.Unchecked) return null

    val feedback = task.feedback ?: return CheckResult(task.status, "")
    return feedback.toCheckResult(task.status)
  }

  private fun updateBackground() {
    UIUtil.setBackgroundRecursively(checkFinishedPanel, TaskDescriptionView.getTaskDescriptionBackgroundColor())
    UIUtil.setBackgroundRecursively(checkDetailsPlaceholder, TaskDescriptionView.getTaskDescriptionBackgroundColor())
  }

  fun updateCheckPanel(task: Task) {
    updateCheckButtonWrapper(task)
    checkFinishedPanel.addNextTaskButton(task)
    updateRightActionsToolbar()
    updateCheckDetails(task)
  }

  private fun updateCheckButtonWrapper(task: Task) {
    checkButtonWrapper.removeAll()
    if (task is DataTask && isFeatureEnabled(HYPERSKILL_DATA_TASKS_SUPPORT)) {
      updateCheckButtonWrapper(task)
      return
    }
    val checkComponent = CheckPanelButtonComponent(task.checkAction, isDefault = true)
    checkButtonWrapper.add(checkComponent, BorderLayout.WEST)
  }

  private fun updateCheckButtonWrapper(task: DataTask) {
    when (task.status) {
      CheckStatus.Unchecked -> {
        val isRunning = task.isRunning()
        val component = if (task.isTimeLimited && isRunning) {
          val endDateTime = task.attempt?.endDateTime ?: error("EndDateTime is expected")
          CheckTimer(endDateTime) { updateCheckPanel(task) }
        }
        else {
          CheckPanelButtonComponent(EduActionUtils.getAction(DownloadDatasetAction.ACTION_ID) as DownloadDatasetAction)
        }
        checkButtonWrapper.add(component, BorderLayout.WEST)

        val checkComponent = CheckPanelButtonComponent(task.checkAction, isEnabled = isRunning, isDefault = isRunning)
        checkButtonWrapper.add(checkComponent, BorderLayout.CENTER)
      }
      CheckStatus.Failed -> {
        val retryComponent = CheckPanelButtonComponent(EduActionUtils.getAction(RetryDataTaskAction.ACTION_ID) as RetryDataTaskAction,
                                                       isDefault = true)
        checkButtonWrapper.add(retryComponent, BorderLayout.WEST)
      }
      CheckStatus.Solved -> Unit
    }
  }

  private fun updateRightActionsToolbar() {
    rightActionsToolbar.removeAll()
    createRightActionsToolbar()
  }

  private fun JPanel.addNextTaskButton(task: Task) {
    if (!(task.status == CheckStatus.Solved || task is TheoryTask || task.course is HyperskillCourse)) {
      return
    }

    if (NavigationUtils.nextTask(task) != null || (task.status == CheckStatus.Solved && NavigationUtils.isLastHyperskillProblem(task))) {
      val nextButton = CheckPanelButtonComponent(ActionManager.getInstance().getAction(NextTaskAction.ACTION_ID))
      add(nextButton, BorderLayout.WEST)
    }
  }

  fun checkTooltipPosition(): RelativePoint {
    return JBPopupFactory.getInstance().guessBestPopupLocation(checkButtonWrapper)
  }

  companion object {
    const val ACTION_PLACE = "CheckPanel"
  }
}
