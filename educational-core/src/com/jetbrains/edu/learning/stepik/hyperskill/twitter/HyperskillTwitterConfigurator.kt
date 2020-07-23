package com.jetbrains.edu.learning.stepik.hyperskill.twitter

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.BuildNumber
import com.intellij.util.io.exists
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.CheckStatus
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.messages.EduCoreBundle
import com.jetbrains.edu.learning.stepik.hyperskill.courseFormat.HyperskillCourse
import com.jetbrains.edu.learning.twitter.TwitterPluginConfigurator
import java.nio.file.Path

class HyperskillTwitterConfigurator : TwitterPluginConfigurator {

  override fun askToTweet(project: Project, solvedTask: Task, statusBeforeCheck: CheckStatus): Boolean {
    if (ApplicationInfo.getInstance().build < BUILD_202) return false
    val course = project.course as? HyperskillCourse ?: return false
    if (!course.isStudy) return false
    if (statusBeforeCheck == CheckStatus.Solved) return false

    val projectLesson = course.getProjectLesson() ?: return false
    if (solvedTask.lesson != projectLesson) return false

    var allProjectTaskSolved = true
    projectLesson.visitTasks {
      allProjectTaskSolved = allProjectTaskSolved && it.status == CheckStatus.Solved
    }
    return allProjectTaskSolved
  }

  override fun getDefaultMessage(solvedTask: Task): String {
    val course = solvedTask.course
    val courseName = (course as? HyperskillCourse)?.getProjectLesson()?.presentableName ?: course.presentableName
    return EduCoreBundle.message("hyperskill.twitter.message", courseName)
  }

  override fun getImagePath(solvedTask: Task): Path? {
    val path = PluginManagerCore.getPlugin(PluginId.getId(EduNames.PLUGIN_ID))?.pluginPath ?: return null
    return path.resolve("twitter/hyperskill/achievement0.gif").takeIf { it.exists() }
  }

  companion object {
    // BACKCOMPAT: 2020.1
    private val BUILD_202 = BuildNumber.fromString("202")!!
  }
}