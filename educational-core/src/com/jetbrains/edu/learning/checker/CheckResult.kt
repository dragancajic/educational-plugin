package com.jetbrains.edu.learning.checker

import com.intellij.openapi.util.text.StringUtil
import com.jetbrains.edu.learning.courseFormat.CheckStatus

class CheckResult @JvmOverloads constructor(
  val status: CheckStatus,
  val message: String,
  val details: String? = null,
  val diff: CheckResultDiff? = null,
  private val needEscape: Boolean = true
) {

  val escapedMessage: String get() = message.escaped
  val escapedDetails: String? get() = details?.escaped

  val isSolved: Boolean get() = status == CheckStatus.Solved

  private val String.escaped: String get() = if (needEscape) StringUtil.escapeXmlEntities(this) else this

  companion object {
    @JvmField val NO_LOCAL_CHECK = CheckResult(CheckStatus.Unchecked, "Local check isn't available")
    @JvmField val FAILED_TO_CHECK = CheckResult(CheckStatus.Unchecked, CheckUtils.FAILED_TO_CHECK_MESSAGE)
    @JvmField val LOGIN_NEEDED = CheckResult(CheckStatus.Unchecked, CheckUtils.LOGIN_NEEDED_MESSAGE)
    @JvmField val CONNECTION_FAILED = CheckResult(CheckStatus.Unchecked, "Connection failed")
    @JvmField val SOLVED = CheckResult(CheckStatus.Solved, "")
    @JvmField val NO_TESTS_RUN = CheckResult(CheckStatus.Unchecked, "No tests have run")
    @JvmField val CANCELED = CheckResult(CheckStatus.Unchecked, "Canceled")
  }
}

data class CheckResultDiff(val expected: String, val actual: String, val title: String = "", val message: String = "")
