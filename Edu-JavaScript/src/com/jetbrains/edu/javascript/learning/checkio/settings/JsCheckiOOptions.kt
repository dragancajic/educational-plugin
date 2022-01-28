package com.jetbrains.edu.javascript.learning.checkio.settings

import com.jetbrains.edu.javascript.learning.checkio.JsCheckiOSettings
import com.jetbrains.edu.javascript.learning.checkio.connectors.JsCheckiOOAuthConnector
import com.jetbrains.edu.javascript.learning.checkio.utils.profileUrl
import com.jetbrains.edu.learning.checkio.account.CheckiOAccount
import com.jetbrains.edu.learning.checkio.utils.CheckiONames
import com.jetbrains.edu.learning.settings.LoginOptions
import org.jetbrains.annotations.Nls
import javax.swing.event.HyperlinkEvent

class JsCheckiOOptions : LoginOptions<CheckiOAccount>() {
  override fun getCurrentAccount(): CheckiOAccount? = JsCheckiOSettings.getInstance().account

  override fun setCurrentAccount(account: CheckiOAccount?) {
    JsCheckiOSettings.getInstance().account = account
    if (account != null) {
      JsCheckiOOAuthConnector.notifyUserLoggedIn()
    }
    else {
      JsCheckiOOAuthConnector.notifyUserLoggedOut()
    }
  }

  override fun createAuthorizeListener(): LoginListener {
    return object : LoginListener() {
      override fun authorize(e: HyperlinkEvent?) {
        JsCheckiOOAuthConnector.doAuthorize(Runnable {
          lastSavedAccount = getCurrentAccount()
          updateLoginLabels()
        })
      }
    }
  }

  @Nls
  override fun getDisplayName(): String = CheckiONames.JS_CHECKIO

  override fun profileUrl(account: CheckiOAccount): String = account.profileUrl
}