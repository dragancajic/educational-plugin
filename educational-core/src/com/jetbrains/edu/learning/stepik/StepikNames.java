package com.jetbrains.edu.learning.stepik;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.application.ApplicationManager;

public class StepikNames {
  public static final String STEPIK = "Stepik";
  public static final String STEPIK_TYPE = STEPIK;
  public static final String ARE_SOLUTIONS_UPDATED_PROPERTY = "Educational.StepikSolutionUpdated";
  public static final String STEPIK_URL_PROPERTY = "Stepik URL";
  public static final String STEPIK_DEFAULT_URL = "https://stepik.org";
  public static final String STEPIK_RELEASE_URL = "https://release.stepik.org";
  public static final String STEPIK_DEV_URL = "https://dev.stepik.org";
  public static final String STEPIK_URL = ApplicationManager.getApplication().isUnitTestMode() ? "https://release.stepik.org" :
                                          PropertiesComponent.getInstance().getValue(STEPIK_URL_PROPERTY, STEPIK_DEFAULT_URL);
  public static final String TOKEN_URL = STEPIK_URL + "/oauth2/token/";
  public static final String STEPIK_API_URL = STEPIK_URL + "/api/";
  public static final String STEPIK_PROFILE_PATH = STEPIK_URL + "/users/";

  public static final String PYCHARM_PREFIX = "pycharm";
  public static final String EDU_STEPIK_SERVICE_NAME = "edu/stepik";
  public static final String LINK = "link";
  public static final String CLIENT_ID = STEPIK_URL == STEPIK_DEFAULT_URL
                                         ? StepikOAuthBundle.INSTANCE.valueOrDefault("stepikClientId", "")
                                         : StepikOAuthBundle.INSTANCE.valueOrDefault("stepikTestClientId", "");
  public static final String CLIENT_SECRET = STEPIK_URL == STEPIK_DEFAULT_URL
                                             ? StepikOAuthBundle.INSTANCE.valueOrDefault("stepikClientSecret", "")
                                             : StepikOAuthBundle.INSTANCE.valueOrDefault("stepikTestClientSecret", "");
  public static final String OAUTH_SERVICE_NAME = "edu/stepik/oauth";
  public static final String EXTERNAL_REDIRECT_URL = "https://example.com";
  public static final String PYCHARM_ADDITIONAL = "PyCharm additional materials";
  public static final String ADDITIONAL_INFO = "additional_files.json";

  public static final String PLUGIN_NAME = "EduTools";
}
