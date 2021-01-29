package com.jetbrains.edu.coursecreator.actions.marketplace

import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.jetbrains.edu.coursecreator.CCNotificationUtils
import com.jetbrains.edu.coursecreator.CCNotificationUtils.UPDATE_NOTIFICATION_GROUP_ID
import com.jetbrains.edu.coursecreator.CCUtils.checkIfAuthorized
import com.jetbrains.edu.coursecreator.CCUtils.isCourseCreator
import com.jetbrains.edu.learning.EduExperimentalFeatures
import com.jetbrains.edu.learning.StudyTaskManager
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.isFeatureEnabled
import com.jetbrains.edu.learning.marketplace.MARKETPLACE
import com.jetbrains.edu.learning.marketplace.api.MarketplaceConnector
import com.jetbrains.edu.learning.marketplace.settings.MarketplaceSettings
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.messages.EduCoreBundle.message
import com.jetbrains.edu.learning.statistics.EduCounterUsageCollector
import java.io.File

@Suppress("ComponentNotRegistered") // Marketplace.xml
class MarketplacePushCourse(private val updateTitle: String = message("item.update.on.0.course.title", MARKETPLACE),
                            private val uploadTitle: String = message("item.upload.to.0.course.title", MARKETPLACE)) : DumbAwareAction(
  EduCoreBundle.lazyMessage("gluing.slash", updateTitle, uploadTitle)) {

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation
    val project = e.project
    presentation.isEnabledAndVisible = false
    if (project == null || !isCourseCreator(project) || !isFeatureEnabled(EduExperimentalFeatures.MARKETPLACE)) {
      return
    }
    val course = StudyTaskManager.getInstance(project).course as? EduCourse ?: return
    presentation.isEnabledAndVisible = true

    if (course.isMarketplaceRemote) {
      presentation.setText { updateTitle }
    }
    else {
      presentation.setText { uploadTitle }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    if (project == null || !isCourseCreator(project) || !isFeatureEnabled(EduExperimentalFeatures.MARKETPLACE)) {
      return
    }
    val course = StudyTaskManager.getInstance(project).course as? EduCourse ?: return
    val connector = MarketplaceConnector.getInstance()

    if (!checkIfAuthorized(project, MARKETPLACE, if (course.isMarketplaceRemote) "update course" else "post course",
                           MarketplaceSettings.INSTANCE.account != null) { connector.doAuthorize() }) {
      return
    }

    val tempFile = FileUtil.createTempFile("marketplace-${course.name}-${course.courseVersion}", ".zip", true)
    val errorMessage = CreateMarketplaceArchive().createCourseArchive(project, tempFile.absolutePath)
    if (errorMessage != null) {
      Messages.showErrorDialog(project, errorMessage, message("error.failed.to.create.course.archive"))
      return
    }

    doPush(project, connector, course, tempFile)
  }

  private fun doPush(project: Project, connector: MarketplaceConnector, course: EduCourse, tempFile: File) {
    if (course.isMarketplaceRemote) {
      val courseOnRemote = connector.searchCourse(course.marketplaceId)
      // courseOnRemote can be null if it was not validated yet
      if (courseOnRemote == null) {
        val notification = Notification(UPDATE_NOTIFICATION_GROUP_ID,
                                        message("error.failed.to.update"),
                                        message("error.failed.to.update.no.course",
                                                MARKETPLACE,
                                                message("item.upload.to.0.course.title", MARKETPLACE)),
                                        NotificationType.ERROR,
                                        CCNotificationUtils.createPostCourseNotificationListener(course)
                                        { connector.uploadNewCourseUnderProgress(project, course, tempFile) })
        notification.notify(project)
        return
      }

      val remoteCourseVersion = connector.getLatestCourseUpdateInfo(course.marketplaceId).version
      val courseVersion = course.courseVersion
      if (courseVersion < remoteCourseVersion) {
        CCNotificationUtils.showErrorNotification(project, message("marketplace.push.version.mismatch.title"),
                                                  message("marketplace.push.version.mismatch.details", courseVersion, remoteCourseVersion))
        return
      }
      connector.uploadCourseUpdateUnderProgress(project, course, tempFile)
      EduCounterUsageCollector.updateCourse()
    }
    else {
      connector.uploadNewCourseUnderProgress(project, course, tempFile)
      EduCounterUsageCollector.uploadCourse()
    }
  }
}