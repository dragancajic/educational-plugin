package com.jetbrains.edu.python.learning

import com.intellij.util.PlatformUtils

class PyIdeaConfigurator : PyConfiguratorBase(PyIdeaCourseBuilder()) {
  override fun isEnabled(): Boolean = isPythonConfiguratorEnabled()
}

internal fun isPythonConfiguratorEnabled(): Boolean = !(PlatformUtils.isPyCharm() || PlatformUtils.isCLion())
