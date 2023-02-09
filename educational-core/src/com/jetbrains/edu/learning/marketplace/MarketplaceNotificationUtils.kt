package com.jetbrains.edu.learning.marketplace

import com.intellij.ide.plugins.PluginManagerConfigurable
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.jetbrains.edu.coursecreator.CCNotificationUtils
import com.jetbrains.edu.learning.courseFormat.EduCourse
import com.jetbrains.edu.learning.messages.EduCoreBundle

object MarketplaceNotificationUtils {
  fun showLoginFailedNotification(loginProviderName: String) {
    Notification(
      "EduTools",
      EduCoreBundle.message("error.login.failed"),
      EduCoreBundle.message("error.failed.login.to.subsystem", loginProviderName),
      NotificationType.ERROR
    ).notify(null)
  }

  fun showReloginToJBANeededNotification(action: AnAction) {
    val notification = Notification(
      "EduTools",
      EduCoreBundle.message("jba.relogin.needed.title"),
      EduCoreBundle.message("jba.relogin.text"),
      NotificationType.ERROR
    )
    notification.addAction(action)
    notification.notify(null)
  }

  @Suppress("DialogTitleCapitalization")
  fun showInstallMarketplacePluginNotification(notificationTitle: String, notificationType: NotificationType) {
    val notification = Notification(
      "EduTools",
      notificationTitle,
      EduCoreBundle.message("notification.marketplace.install.licensing.plugin"),
      notificationType
    )

    notification.addAction(object : AnAction(EduCoreBundle.message("action.install.plugin.in.settings")) {
      override fun actionPerformed(e: AnActionEvent) {
        ShowSettingsUtil.getInstance().showSettingsDialog(ProjectManager.getInstance().defaultProject,
                                                          PluginManagerConfigurable::class.java)
      }
    })
    notification.notify(null)
  }

  fun showFailedToFindMarketplaceCourseOnRemoteNotification(project: Project, action: AnAction) {
    CCNotificationUtils.showErrorNotification(project,
                                              EduCoreBundle.message("error.failed.to.update"),
                                              EduCoreBundle.message("marketplace.failed.to.update.no.course"),
                                              action)
  }

  fun showAcceptDeveloperAgreementNotification(project: Project, action: () -> AnAction) {
    CCNotificationUtils.showErrorNotification(project,
                                              EduCoreBundle.message("notification.course.creator.failed.to.upload.course.title"),
                                              EduCoreBundle.message("marketplace.plugin.development.agreement.not.accepted"),
                                              action()
    )
  }

  fun showNoRightsToUpdateNotification(project: Project, course: EduCourse, action: () -> Unit) {
    CCNotificationUtils.showErrorNotification(project,
                                              EduCoreBundle.message("notification.course.creator.access.denied.title"),
                                              EduCoreBundle.message("notification.course.creator.access.denied.content"),
                                              NotificationAction.createSimpleExpiring(
                                                EduCoreBundle.message("notification.course.creator.access.denied.action")) {
                                                course.convertToLocal()
                                                action()
                                              })
  }
}