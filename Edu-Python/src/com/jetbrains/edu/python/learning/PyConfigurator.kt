package com.jetbrains.edu.python.learning

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.edu.learning.EduCourseBuilder
import com.jetbrains.edu.learning.checker.TaskCheckerProvider
import com.jetbrains.edu.learning.configuration.EduConfiguratorWithSubmissions
import com.jetbrains.edu.python.learning.checker.PyTaskCheckerProvider
import com.jetbrains.python.newProject.PyNewProjectSettings
import icons.PythonIcons
import javax.swing.Icon

open class PyConfigurator : EduConfiguratorWithSubmissions<PyNewProjectSettings>() {
  override val courseBuilder: EduCourseBuilder<PyNewProjectSettings>
    get() = PyCourseBuilder()

  override fun getMockFileName(text: String): String = TASK_PY

  override val testFileName: String
    get() = TESTS_PY

  override fun excludeFromArchive(project: Project, file: VirtualFile): Boolean =
    super.excludeFromArchive(project, file) || excludeFromArchive(file)

  override fun isTestFile(project: Project, file: VirtualFile): Boolean = testFileName == file.name

  override val taskCheckerProvider: TaskCheckerProvider
    get() = PyTaskCheckerProvider()

  override val logo: Icon
    get() = PythonIcons.Python.Python

  override val isCourseCreatorEnabled: Boolean
    get() = false

  companion object {
    const val TESTS_PY = "tests.py"
    const val TASK_PY = "task.py"
    const val MAIN_PY = "main.py"
  }
}