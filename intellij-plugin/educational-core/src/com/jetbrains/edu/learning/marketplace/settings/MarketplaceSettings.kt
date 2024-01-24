package com.jetbrains.edu.learning.marketplace.settings

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.ui.JBAccountInfoService
import com.jetbrains.edu.learning.isUnitTestMode
import com.jetbrains.edu.learning.marketplace.api.MarketplaceAccount
import com.jetbrains.edu.learning.marketplace.api.MarketplaceSubmissionsConnector
import com.jetbrains.edu.learning.marketplace.getJBAUserInfo
import com.jetbrains.edu.learning.marketplace.toBoolean
import com.jetbrains.edu.learning.onError
import com.jetbrains.edu.learning.statistics.EduCounterUsageCollector
import com.jetbrains.edu.learning.submissions.isSolutionSharingAllowed
import com.jetbrains.edu.learning.taskToolWindow.ui.SolutionSharingInlineBanners
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.TestOnly

@Service(Service.Level.APP)
class MarketplaceSettings(private val scope: CoroutineScope) {

  private var account: MarketplaceAccount? = null

  @Volatile
  var solutionsSharing: Boolean? = null
    private set

  init {
    if (!isUnitTestMode) {
      scope.launch(Dispatchers.IO) {
        val marketplaceSubmissionsConnector = MarketplaceSubmissionsConnector.getInstance()
        if (!marketplaceSubmissionsConnector.getUserAgreementState().isSolutionSharingAllowed()) return@launch

        val sharingPreference = marketplaceSubmissionsConnector.getSharingPreference()
        solutionsSharing = sharingPreference.toBoolean()
      }
    }
  }

  fun getMarketplaceAccount(): MarketplaceAccount? {
    if (!isJBALoggedIn()) {
      account = null
      return null
    }
    val currentAccount = account
    val jbaUserInfo = getJBAUserInfo()
    if (jbaUserInfo == null) {
      val accountName = account?.userInfo?.name
      LOG.error("User info is null${if (accountName != null) " for $accountName account" else ""}")
      account = null
    }
    else if (currentAccount == null || !currentAccount.checkTheSameUserAndUpdate(jbaUserInfo)) {
      account = MarketplaceAccount(jbaUserInfo)
    }

    return account
  }

  fun setAccount(value: MarketplaceAccount?) {
    account = value
  }

  fun updateSharingPreference(state: Boolean, project: Project? = null) {
    scope.launch(Dispatchers.IO) {
      MarketplaceSubmissionsConnector.getInstance().changeSharingPreference(state).onError {
        SolutionSharingInlineBanners.showFailedToEnableSolutionSharing(project)
        return@launch
      }

      solutionsSharing = state
      if (state) {
        SolutionSharingInlineBanners.showSuccessSolutionSharingEnabling(project)
      }
      EduCounterUsageCollector.solutionSharingState(state)
    }
  }

  @TestOnly
  fun setSharingPreference(state: Boolean?) {
    solutionsSharing = state
  }

  companion object {
    private val LOG = logger<MarketplaceSettings>()

    fun isJBALoggedIn(): Boolean = JBAccountInfoService.getInstance()?.userData != null

    val INSTANCE: MarketplaceSettings
      get() = service()
  }
}