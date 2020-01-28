package com.jetbrains.edu.learning.projectView

import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.jetbrains.edu.learning.EduNames
import com.jetbrains.edu.learning.courseFormat.FrameworkLesson

class FrameworkLessonNode private constructor(
  project: Project,
  value: PsiDirectory,
  viewSettings: ViewSettings,
  lesson: FrameworkLesson
) : LessonNode(project, value, viewSettings, lesson) {

  override val item: FrameworkLesson
    get() = super.item as FrameworkLesson

  override fun modifyChildNode(childNode: AbstractTreeNode<*>): AbstractTreeNode<*>? {
    val task = item.currentTask()
    return CourseViewUtils.modifyTaskChildNode(myProject, childNode, task) { dir -> DirectoryNode(myProject, dir, settings, task) }
  }

  companion object {

    @JvmStatic
    fun createFrameworkLessonNode(
      project: Project,
      lessonDirectory: PsiDirectory,
      viewSettings: ViewSettings,
      lesson: FrameworkLesson
    ): FrameworkLessonNode? {
      val task = lesson.currentTask()
      val taskBaseDirectory = lessonDirectory.findSubdirectory(EduNames.TASK) ?: return null
      val taskDirectory = CourseViewUtils.findTaskDirectory(project, taskBaseDirectory, task) ?: return null
      return FrameworkLessonNode(project, taskDirectory, viewSettings, lesson)
    }
  }
}
