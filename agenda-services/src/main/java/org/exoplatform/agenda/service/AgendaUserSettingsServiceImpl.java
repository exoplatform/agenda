package org.exoplatform.agenda.service;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.EventReminderParameter;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.container.xml.InitParams;

public class AgendaUserSettingsServiceImpl implements AgendaUserSettingsService {

  private static final String        AGENDA_USER_SETTINGS_PARAM_KEY = "agenda.user.settings";

  private static final Scope         AGENDA_USER_SETTING_SCOPE      = Scope.APPLICATION.id("Agenda");

  private static final String        AGENDA_USER_SETTING_KEY        = "AgendaSettings";

  private AgendaEventReminderService agendaEventReminderService;

  private SettingService             settingService;

  private AgendaUserSettings         defaultUserSettings            = null;

  public AgendaUserSettingsServiceImpl(AgendaEventReminderService agendaEventReminderService,
                                       SettingService settingService,
                                       InitParams initParams) {
    this.agendaEventReminderService = agendaEventReminderService;
    this.settingService = settingService;

    if (initParams.containsKey(AGENDA_USER_SETTINGS_PARAM_KEY)) {
      defaultUserSettings = (AgendaUserSettings) initParams.getObjectParam(AGENDA_USER_SETTINGS_PARAM_KEY).getObject();
    }
    if (defaultUserSettings == null) {
      defaultUserSettings = new AgendaUserSettings();
    }
  }

  @Override
  public void saveAgendaUserSettings(long userIdentityId, AgendaUserSettings agendaUserSettings) {
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("User identity id is mandatory");
    }
    if (agendaUserSettings == null) {
      throw new IllegalArgumentException("Agenda settings are empty");
    }

    this.settingService.set(Context.USER.id(String.valueOf(userIdentityId)),
                            AGENDA_USER_SETTING_SCOPE,
                            AGENDA_USER_SETTING_KEY,
                            SettingValue.create(agendaUserSettings.toString()));
  }

  @Override
  public AgendaUserSettings getAgendaUserSettings(long userIdentityId) {
    SettingValue<?> settingValue = this.settingService.get(Context.USER.id(String.valueOf(userIdentityId)),
                                                           AGENDA_USER_SETTING_SCOPE,
                                                           AGENDA_USER_SETTING_KEY);

    if (settingValue == null || settingValue.getValue() == null || StringUtils.isBlank(settingValue.getValue().toString())) {
      AgendaUserSettings agendaUserSettings = defaultUserSettings.clone();
      List<EventReminderParameter> defaultReminders = agendaEventReminderService.getDefaultReminders();
      agendaUserSettings.setReminders(defaultReminders);
      return agendaUserSettings;
    } else {
      return AgendaUserSettings.fromString(settingValue.getValue().toString());
    }
  }
}
