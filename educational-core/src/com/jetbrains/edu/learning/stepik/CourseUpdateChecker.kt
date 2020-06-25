package com.jetbrains.edu.learning.stepik

import com.google.common.annotations.VisibleForTesting
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.ActionCallback
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.Alarm
import com.intellij.util.text.DateFormatUtil
import com.jetbrains.edu.learning.EduUtils.isNewlyCreated
import com.jetbrains.edu.learning.course
import com.jetbrains.edu.learning.courseFormat.Course
import com.jetbrains.edu.learning.isUnitTestMode
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.atomic.AtomicInteger

abstract class CourseUpdateChecker(protected val project: Project) : Disposable {

  private val checkRunnable = Runnable { (checkIsUpToDate()).doWhenDone { queueNextCheck(getCheckInterval()) } }
  private val checkForAlarm by lazy { Alarm(Alarm.ThreadToUse.POOLED_THREAD, this) }
  val course: Course? get() = project.course

  private val invocationCounter: AtomicInteger = AtomicInteger()
  var invocationNumber: Int
    @TestOnly
    get() = invocationCounter.get()
    @TestOnly
    set(value) = invocationCounter.set(value)

  fun check() {
    if (!courseCanBeUpdated()) {
      return
    }
    cancelCheckRequests()
    if (isNewlyCreated(project)) {
      queueNextCheck(getCheckInterval())
    }
    else {
      checkRunnable.run()
    }
  }

  @VisibleForTesting
  fun cancelCheckRequests() {
    LOG.info("Is course up to date check requests for ${course?.name} canceled, " +
             "queue contained ${checkForAlarm.activeRequestCount} request(s)")
    checkForAlarm.cancelAllRequests()
  }

  private fun queueNextCheck(interval: Long) {
    LOG.info("Scheduled next is course up to date check for ${course?.name} with check interval $interval milliseconds")
    checkForAlarm.addRequest(checkRunnable, interval)
  }

  private fun checkIsUpToDate(): ActionCallback {
    val actionCallback = ActionCallback()
    doCheckIsUpToDate {
      if (isUnitTestMode) {
        invocationCounter.incrementAndGet()
      }
      actionCallback.setDone()
    }
    return actionCallback
  }

  protected abstract fun doCheckIsUpToDate(onFinish: () -> Unit)

  protected abstract fun courseCanBeUpdated(): Boolean

  //default value is 18 000 seconds (5 hours), set in educational-core.xml
  private fun getCheckInterval(): Long = DateFormatUtil.SECOND * Registry.intValue(REGISTRY_KEY)

  override fun dispose() {}

  companion object {
    const val REGISTRY_KEY = "edu.course.update.check.interval"

    @JvmStatic
    protected val LOG = Logger.getInstance(CourseUpdateChecker::class.java)
  }
}