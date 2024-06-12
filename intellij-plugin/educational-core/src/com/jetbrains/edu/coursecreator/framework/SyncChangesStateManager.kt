package com.jetbrains.edu.coursecreator.framework

import com.intellij.ide.projectView.ProjectView
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.ui.EditorNotifications
import com.intellij.util.ui.update.MergingUpdateQueue
import com.jetbrains.edu.coursecreator.CCUtils
import com.jetbrains.edu.learning.EduExperimentalFeatures
import com.jetbrains.edu.learning.FileInfo
import com.jetbrains.edu.learning.courseFormat.*
import com.jetbrains.edu.learning.courseFormat.tasks.Task
import com.jetbrains.edu.learning.framework.impl.visitFrameworkLessons
import com.jetbrains.edu.learning.isFeatureEnabled
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service(Service.Level.PROJECT)
class SyncChangesStateManager(private val project: Project) : Disposable.Default {
  private val taskFileStateStorage = ConcurrentHashMap<TaskFile, SyncChangesTaskFileState>()
  private val taskStateStorage = ConcurrentHashMap<Task, SyncChangesTaskFileState>()
  private val lessonStateStorage = ConcurrentHashMap<Lesson, SyncChangesTaskFileState>()

  private val dispatcher = MergingUpdateQueue(
    "EduSyncChangesTracker",
    syncChangesQueueDelay,
    true,
    null,
    this,
    null,
    false
  ).setRestartTimerOnAdd(true)

  fun getSyncChangesState(taskFile: TaskFile): SyncChangesTaskFileState? {
    if (!isCCFrameworkLesson(taskFile.task.lesson)) return null
    return taskFileStateStorage[taskFile]
  }

  fun getSyncChangesState(task: Task): SyncChangesTaskFileState? {
    if (!isCCFrameworkLesson(task.lesson)) return null
    return taskStateStorage[task]
  }

  fun getSyncChangesState(lesson: Lesson): SyncChangesTaskFileState? {
    if (!isCCFrameworkLesson(lesson)) return null
    return lessonStateStorage[lesson]
  }

  fun taskFileChanged(taskFile: TaskFile) = queueUpdate(taskFile)

  fun taskFileCreated(taskFile: TaskFile) = processTaskFilesCreated(taskFile.task, listOf(taskFile))

  fun filesDeleted(task: Task, taskFilesNames: List<String>) {
    // state of a current task might change from warning to info after deletion, so recalculate it
    queueUpdate(task, emptyList())

    queueSyncChangesStateForFilesInPrevTask(task, taskFilesNames)
  }

  fun taskDeleted(task: Task) = queueSyncChangesStateForFilesInPrevTask(task, null)

  fun fileMoved(file: VirtualFile, fileInfo: FileInfo.FileInTask, oldDirectoryInfo: FileInfo.FileInTask) {
    val task = fileInfo.task
    val oldTask = oldDirectoryInfo.task
    if (!isCCFrameworkLesson(task.lesson) && !isCCFrameworkLesson(oldTask.lesson)) return

    val (taskFiles, oldPaths) = if (file.isDirectory) {
      collectMovedDataInfoOfDirectory(file, fileInfo, oldDirectoryInfo)
    }
    else {
      collectMovedDataInfoOfSingleFile(file, fileInfo, oldDirectoryInfo)
    }

    if (oldTask.lesson is FrameworkLesson) {
      filesDeleted(oldTask, oldPaths)
    }
    if (task.lesson is FrameworkLesson) {
      processTaskFilesCreated(task, taskFiles)
    }
  }

  fun updateSyncChangesState(lessonContainer: LessonContainer) {
    lessonContainer.visitFrameworkLessons { queueUpdate(it) }
  }

  fun updateSyncChangesState(task: Task, taskFiles: List<TaskFile>) = queueUpdate(task, taskFiles)

  fun updateSyncChangesState(task: Task) = queueUpdate(task)

  @TestOnly
  fun waitForAllRequestsProcessed() {
    dispatcher.waitForAllExecuted(1, TimeUnit.SECONDS)
  }

  private fun collectSyncChangesState(lesson: Lesson) {
    val state = collectState(lesson.taskList) { taskStateStorage[it] }
    if (state != null) lessonStateStorage[lesson] = state
    else lessonStateStorage.remove(lesson)
  }

  private fun collectSyncChangesState(task: Task) {
    val state = collectState(task.taskFiles.values.toList()) {
      if (shouldUpdateSyncChangesState(it)) {
        taskFileStateStorage[it]
      }
      else {
        null
      }
    }
    if (state != null) taskStateStorage[task] = state
    else taskStateStorage.remove(task)
  }

  /**
   * Collects the SyncChangesTaskFileState based on the provided collect function.
   * The state represents the synchronization status of the files and will be displayed in the project view.
   */
  private fun <T> collectState(items: Iterable<T>, collect: (T) -> SyncChangesTaskFileState?): SyncChangesTaskFileState? {
    var resultState: SyncChangesTaskFileState? = null
    for (item in items) {
      val state = collect(item) ?: continue
      if (state == SyncChangesTaskFileState.WARNING) return SyncChangesTaskFileState.WARNING
      resultState = SyncChangesTaskFileState.INFO
    }
    return resultState
  }

  // In addition/deletion of files, framework lesson structure might break/restore,
  // so we need to recalculate the state for corresponding task files from a previous task
  // in case when a warning state is added/removed
  private fun processTaskFilesCreated(task: Task, taskFiles: List<TaskFile>) {
    queueUpdate(task, taskFiles)
    queueSyncChangesStateForFilesInPrevTask(task, taskFiles.map { it.name })
  }

  private fun queueUpdate(taskFile: TaskFile) = queueUpdate(taskFile.task, listOf(taskFile))

  private fun queueUpdate(task: Task, taskFiles: List<TaskFile>) {
    if (!isCCFrameworkLesson(task.lesson)) return
    dispatcher.queue(TaskFilesSyncChangesUpdate(task, taskFiles.toSet()) {
      recalcSyncChangesState(task, taskFiles)
    })
    queueTaskStateUpdate(task)
    queueLessonStateUpdate(task.lesson)
    queueProjectUpdate(project)
  }

  private fun queueUpdate(task: Task) {
    if (!isCCFrameworkLesson(task.lesson)) return
    dispatcher.queue(TaskFilesSyncChangesUpdate(task) {
      recalcSyncChangesState(task, task.taskFiles.values.toList())
    })
    queueTaskStateUpdate(task)
    queueLessonStateUpdate(task.lesson)
    queueProjectUpdate(project)
  }

  private fun queueUpdate(lesson: Lesson) {
    if (!isCCFrameworkLesson(lesson)) return
    for (task in lesson.taskList) {
      dispatcher.queue(TaskFilesSyncChangesUpdate(task) {
        recalcSyncChangesState(task, task.taskFiles.values.toList())
      })
      queueTaskStateUpdate(task)
    }
    queueLessonStateUpdate(lesson)
    queueProjectUpdate(project)
  }

  private fun queueTaskStateUpdate(task: Task) {
    dispatcher.queue(TaskSyncChangesUpdate(task) {
      collectSyncChangesState(task)
    })
  }

  private fun queueLessonStateUpdate(lesson: Lesson) {
    dispatcher.queue(LessonSyncChangesUpdate(lesson) {
      collectSyncChangesState(lesson)
    })
  }

  private fun queueProjectUpdate(project: Project) {
    dispatcher.queue(ProjectSyncChangesUpdate {
      ProjectView.getInstance(project).refresh()
      EditorNotifications.updateAll()
    })
  }

  /**
   * Collects task files in a moved directory and returns a map of task files with their old paths.
   *
   * @return a map of task files with their old paths
   */
  private fun collectMovedDataInfoOfDirectory(
    file: VirtualFile,
    fileInfo: FileInfo.FileInTask,
    oldDirectoryInfo: FileInfo.FileInTask
  ): MovedDataInfo {
    val task = fileInfo.task
    val taskFiles = mutableListOf<TaskFile>()
    val oldPaths = mutableListOf<String>()
    VfsUtil.visitChildrenRecursively(file, object : VirtualFileVisitor<Any?>(NO_FOLLOW_SYMLINKS) {
      override fun visitFile(childFile: VirtualFile): Boolean {
        if (!childFile.isDirectory) {
          val relativePath = VfsUtil.findRelativePath(file, childFile, VfsUtilCore.VFS_SEPARATOR_CHAR) ?: return true
          var oldPath = file.name + VfsUtilCore.VFS_SEPARATOR_CHAR + relativePath
          if (oldDirectoryInfo.pathInTask.isNotEmpty()) {
            oldPath = oldDirectoryInfo.pathInTask + VfsUtilCore.VFS_SEPARATOR_CHAR + oldPath
          }
          val newPath = fileInfo.pathInTask + VfsUtilCore.VFS_SEPARATOR_CHAR + relativePath
          val taskFile = task.taskFiles[newPath] ?: return true
          taskFiles.add(taskFile)
          oldPaths.add(oldPath)
        }
        return true
      }
    })

    return MovedDataInfo(taskFiles, oldPaths)
  }

  private fun collectMovedDataInfoOfSingleFile(
    file: VirtualFile,
    fileInfo: FileInfo.FileInTask,
    oldDirectoryInfo: FileInfo.FileInTask
  ): MovedDataInfo {
    val oldPath = if (oldDirectoryInfo.pathInTask.isNotEmpty()) {
      oldDirectoryInfo.pathInTask + VfsUtilCore.VFS_SEPARATOR_CHAR + file.name
    }
    else {
      file.name
    }
    val taskFile = fileInfo.task.taskFiles[fileInfo.pathInTask] ?: return MovedDataInfo()
    return MovedDataInfo(taskFile, oldPath)
  }

  private fun isCCFrameworkLesson(lesson: Lesson): Boolean {
    return CCUtils.isCourseCreator(project) && lesson is FrameworkLesson && isFeatureEnabled(EduExperimentalFeatures.CC_FL_SYNC_CHANGES)
  }

  // Process a batch of taskFiles in a certain task at once to minimize the number of accesses to the storage
  private fun recalcSyncChangesState(task: Task, taskFiles: List<TaskFile>) {
    for (taskFile in taskFiles) {
      taskFileStateStorage.remove(taskFile)
    }

    val updatableTaskFiles = taskFiles.filter { shouldUpdateSyncChangesState(it) }

    val (warningTaskFiles, otherTaskFiles) = updatableTaskFiles.partition { checkForAbsenceInNextTask(it) }

    for (taskFile in warningTaskFiles) {
      taskFileStateStorage[taskFile] = SyncChangesTaskFileState.WARNING
    }

    val changedTaskFiles = CCFrameworkLessonManager.getInstance(project).getChangedFiles(task)
    val infoTaskFiles = otherTaskFiles.intersect(changedTaskFiles.toSet())

    for (taskFile in infoTaskFiles) {
      taskFileStateStorage[taskFile] = SyncChangesTaskFileState.INFO
    }
  }

  // do not update state for the last framework lesson task and for non-propagatable files (invisible files)
  private fun shouldUpdateSyncChangesState(taskFile: TaskFile): Boolean {
    val task = taskFile.task
    return taskFile.isPropagatable && task.lesson.taskList.last() != task
  }

  // after deletion of files, the framework lesson structure might break,
  // so we need to recalculate state for a corresponding file from a previous task in case when a warning state is added/removed
  private fun queueSyncChangesStateForFilesInPrevTask(task: Task, filterTaskFileNames: List<String>?) {
    val prevTask = task.lesson.taskList.getOrNull(task.index - 2) ?: return
    if (filterTaskFileNames == null) {
      queueUpdate(prevTask)
      return
    }
    val correspondingTaskFiles = prevTask.taskFiles.filter { it.key in filterTaskFileNames }.values.toList()
    queueUpdate(prevTask, correspondingTaskFiles)
  }

  private fun checkForAbsenceInNextTask(taskFile: TaskFile): Boolean {
    val task = taskFile.task
    val nextTask = task.lesson.taskList.getOrNull(task.index) ?: return false
    return taskFile.name !in nextTask.taskFiles
  }

  /**
   * Represents information about task files that have been moved.
   * Contains a list of task files and their corresponding old paths.
   */
  private data class MovedDataInfo(val taskFiles: List<TaskFile> = emptyList(), val oldPaths: List<String> = emptyList()) {
    constructor(taskFile: TaskFile, oldPath: String) : this(listOf(taskFile), listOf(oldPath))
  }

  companion object {
    private val syncChangesQueueDelay = Registry.intValue("edu.course.creator.fl.sync.changes.merging.timespan")

    fun getInstance(project: Project): SyncChangesStateManager = project.service()
  }
}