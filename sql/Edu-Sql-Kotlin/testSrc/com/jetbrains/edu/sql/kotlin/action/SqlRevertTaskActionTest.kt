package com.jetbrains.edu.sql.kotlin.action

import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.ui.Messages
import com.intellij.sql.dialects.SqlDialectMappings
import com.intellij.sql.dialects.h2.H2Dialect
import com.intellij.sql.psi.SqlLanguage
import com.jetbrains.edu.learning.*
import com.jetbrains.edu.learning.actions.RevertTaskAction
import com.jetbrains.edu.learning.checker.CheckUtils
import com.jetbrains.edu.sql.jvm.gradle.SqlGradleCourseBuilderBase.Companion.INIT_SQL
import com.jetbrains.edu.sql.jvm.gradle.createDatabaseScriptConfiguration
import com.jetbrains.edu.sql.kotlin.SqlCourseGenerationTestBase

class SqlRevertTaskActionTest : SqlCourseGenerationTestBase() {

  @Suppress("SqlDialectInspection", "SqlNoDataSourceInspection")
  fun `test database recreation on task reversion`() {
    val course = course(language = SqlLanguage.INSTANCE, environment = "Kotlin") {
      lesson("lesson1") {
        eduTask("task1") {
          sqlTaskFile(INIT_SQL, """
            create table if not exists Students1;
          """)
          sqlTaskFile("src/task.sql", """
            drop table Students1;
            create table if not exists Students2;
          """)
        }
      }
    }

    createCourseStructure(course)

    val task = course.findTask("lesson1", "task1")

    checkTable(task, "Students1", shouldExist = true)
    checkTable(task, "Students2", shouldExist = false)

    val sqlFile = findFile("lesson1/task1/src/task.sql")
    // Needed only not to fail on `Application#assertReadAccessAllowed` during script execution.
    // The same as in `com.jetbrains.edu.sql.jvm.gradle.SqlUtilsKt.setSqlMappingForInitScripts`
    SqlDialectMappings.getInstance(project).setMapping(sqlFile, H2Dialect.INSTANCE)

    val configuration = task.createDatabaseScriptConfiguration(project, sqlFile)
      ?: error("Failed to create database script configuration for `${sqlFile}` file")
    computeUnderProgress(project, "") {
      CheckUtils.executeRunConfigurations(project, listOf(configuration), it)
    }

    checkTable(task, "Students1", shouldExist = false)
    checkTable(task, "Students2", shouldExist = true)

    val fileEditorManager = FileEditorManager.getInstance(project)
    fileEditorManager.openFile(sqlFile, true)
    withEduTestDialog(EduTestDialog(Messages.OK)) {
      testAction(RevertTaskAction.ACTION_ID)
    }

    checkTable(task, "Students1", shouldExist = true)
    checkTable(task, "Students2", shouldExist = false)
  }
}