@file:JvmName("TaskYamlUtil")

package com.jetbrains.edu.coursecreator.configuration.mixins

import com.fasterxml.jackson.annotation.JsonAutoDetect
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonPropertyOrder
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.util.StdConverter
import com.jetbrains.edu.learning.courseFormat.FeedbackLink
import com.jetbrains.edu.learning.courseFormat.TaskFile

@Suppress("UNUSED_PARAMETER", "unused") // used for yaml serialization
@JsonAutoDetect(getterVisibility = JsonAutoDetect.Visibility.NONE,
                isGetterVisibility = JsonAutoDetect.Visibility.NONE,
                fieldVisibility = JsonAutoDetect.Visibility.NONE)
@JsonPropertyOrder("type", "feedback_link")
abstract class TaskYamlMixin {
  @JsonProperty("type")
  fun getTaskType(): String {
    throw NotImplementedInMixin()
  }

  @JsonProperty("task_files")
  @JsonSerialize(contentConverter = TaskFileConverter::class)
  open fun getTaskFileValues(): Collection<TaskFile> {
    throw NotImplementedInMixin()
  }

  @JsonProperty("task_files")
  open fun setTaskFileValues(taskFiles: List<TaskFile>) {
    throw NotImplementedInMixin()
  }

  @JsonSerialize(converter = FeedbackLinkConverter::class)
  @JsonProperty(value = "feedback_link", access = JsonProperty.Access.READ_WRITE) lateinit var myFeedbackLink: FeedbackLink
}

private class TaskFileConverter : StdConverter<TaskFile, TaskFileWithoutPlaceholders>() {
  override fun convert(value: TaskFile): TaskFileWithoutPlaceholders {
    return TaskFileWithoutPlaceholders(value.name)
  }
}

private class TaskFileWithoutPlaceholders(@JsonProperty("name") val name: String)

private class FeedbackLinkConverter: StdConverter<FeedbackLink, FeedbackLink>() {
  override fun convert(value: FeedbackLink?): FeedbackLink? {
    if (value?.link.isNullOrBlank()) {
      return null
    }

    return value
  }

}