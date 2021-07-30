package com.jetbrains.edu.learning.stepik.hyperskill.projectOpen

import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.util.ThrowableRunnable
import com.jetbrains.edu.learning.EduExperimentalFeatures
import com.jetbrains.edu.learning.configurators.FakeGradleBasedLanguage
import com.jetbrains.edu.learning.fileTree
import com.jetbrains.edu.learning.stepik.hyperskill.*
import com.jetbrains.edu.learning.stepik.hyperskill.courseGeneration.HyperskillOpenInIdeRequestHandler
import com.jetbrains.edu.learning.stepik.hyperskill.courseGeneration.HyperskillOpenStepRequest
import com.jetbrains.edu.learning.stepik.hyperskill.projectOpen.HyperskillProjectOpenerTestBase.Companion.StepInfo
import com.jetbrains.edu.learning.withFeature


class HyperskillProjectOpenNotRecommendedProblemsTest : HyperskillProjectOpenerTestBase() {
  override fun runTestRunnable(context: ThrowableRunnable<Throwable>) {
    loginFakeUser()
    configureMockResponsesForNotRecommendedProblem()
    withFeature(EduExperimentalFeatures.PROBLEMS_BY_TOPIC, true) {
      super.runTestRunnable(context)
    }
  }

  fun `test open not recommended problem in new project`() {
    configureMockResponsesForStages()
    mockProjectOpener.open(HyperskillOpenInIdeRequestHandler, HyperskillOpenStepRequest(1, step8146.id, "TEXT"))

    val fileTree = fileTree {
      dir(HYPERSKILL_PROBLEMS) {
        dir(step8146.title) {
          file("Task.txt")
          file("task.html")
        }
      }
    }
    fileTree.assertEquals(LightPlatformTestCase.getSourceRoot(), myFixture)
  }

  fun `test open not recommended problem in existing legacy code problems project`() {
    // set up existing project
    hyperskillCourseWithFiles(name = getLegacyProblemsProjectName("TEXT"), language = PlainTextLanguage.INSTANCE) {
      lesson(HYPERSKILL_PROBLEMS) {
        codeTask("code task", stepId = 4) {
          taskFile("task.txt", "file text")
        }
      }
    }

    mockProjectOpener.open(HyperskillOpenInIdeRequestHandler, HyperskillOpenStepRequest(1, step8146.id, "TEXT"))

    val fileTree = fileTree {
      dir(HYPERSKILL_PROBLEMS) {
        dir("code task") {
          file("task.txt", "file text")
          file("task.html")
        }
        dir(step8146.title) {
          file("Task.txt")
          file("task.html")
        }
      }
    }
    fileTree.assertEquals(LightPlatformTestCase.getSourceRoot(), myFixture)
  }

  fun `test open not recommended problem in existing problems project`() {
    // set up existing project
    hyperskillCourseWithFiles {
      section(HYPERSKILL_TOPICS) {
        lesson("Topic title") {
          theoryTask("Theory title") {
            taskFile("Task.txt", "file text")
            taskFile("task.html", "file text")
          }
          codeTask("Problem 1 title") {
            taskFile("Task.txt", "file text")
            taskFile("task.html", "file text")
          }
        }
      }
    }

    mockProjectOpener.open(HyperskillOpenInIdeRequestHandler, HyperskillOpenStepRequest(1, step8146.id, FakeGradleBasedLanguage.id))

    val fileTree = fileTree {
      dir(HYPERSKILL_TOPICS) {
        dir("Topic title") {
          dir("Theory title") {
            file("Task.txt")
            file("task.html")
          }
          dir("Problem 1 title") {
            file("Task.txt")
            file("task.html")
          }
        }
      }
      dir(HYPERSKILL_PROBLEMS) {
        dir(step8146.title) {
          dir("src") {
            file("Task.kt")
          }
          file("task.html")
        }
      }
      file("build.gradle")
      file("settings.gradle")
    }
    fileTree.assertEquals(LightPlatformTestCase.getSourceRoot(), myFixture)
  }

  fun `test open not recommended problem in existing project with stages`() {
    mockConnector.configureFromCourse(testRootDisposable, hyperskillCourse(projectId = null) {
      lesson(HYPERSKILL_PROBLEMS) {
        codeTask(stepId = 4) {
          taskFile("task.txt", "file text")
        }
      }
    })

    // set up existing project
    hyperskillCourseWithFiles {
      frameworkLesson(TEST_HYPERSKILL_PROJECT_NAME) {
        eduTask(testStageName(1), stepId = 1) {
          taskFile("src/Task.kt", "stage 1")
          taskFile("test/Tests1.kt", "stage 1 test")
        }
        eduTask(testStageName(2), stepId = 2) {
          taskFile("src/Task.kt", "stage 2")
          taskFile("test/Tests2.kt", "stage 2 test")
        }
      }
    }

    mockProjectOpener.open(HyperskillOpenInIdeRequestHandler, HyperskillOpenStepRequest(1, step8146.id, FakeGradleBasedLanguage.id))

    val fileTree = fileTree {
      dir(TEST_HYPERSKILL_PROJECT_NAME) {
        dir("task") {
          dir("src") {
            file("Task.kt", "stage 1")
          }
          dir("test") {
            file("Tests1.kt", "stage 1 test")
          }
        }
        dir(testStageName(1)) {
          file("task.html")
        }
        dir(testStageName(2)) {
          file("task.html")
        }
      }
      dir(HYPERSKILL_PROBLEMS) {
        dir(step8146.title) {
          dir("src") {
            file("Task.kt")
          }
          file("task.html")
        }
      }
      file("build.gradle")
      file("settings.gradle")
    }
    fileTree.assertEquals(LightPlatformTestCase.getSourceRoot(), myFixture)
  }

  private fun configureMockResponsesForNotRecommendedProblem() {
    mockConnector.withResponseHandler(testRootDisposable) { request ->
      when {
        request.path.endsWith(step8146.path) -> {
          mockResponse("step_${step8146.id}_response.json")
        }
        request.path.endsWith(step8139.path) -> {
          LOG.error("Unexpected request of getting theory step")
          null
        }
        else -> null
      }
    }
  }

  companion object {
    private val step8139 = StepInfo(8139, "Reading files")
    private val step8146 = StepInfo(8146, "Acronym")
  }
}