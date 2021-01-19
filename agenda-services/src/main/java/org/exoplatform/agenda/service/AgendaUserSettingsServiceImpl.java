package org.exoplatform.agenda.service;

import java.util.*;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ObjectParameter;

public class AgendaUserSettingsServiceImpl implements AgendaUserSettingsService {

  private static final String          AGENDA_USER_SETTINGS_PARAM_KEY = "agenda.user.settings";

  private static final Scope           AGENDA_USER_SETTING_SCOPE      = Scope.APPLICATION.id("Agenda");

  private static final String          AGENDA_USER_SETTING_KEY        = "AgendaSettings";

  private AgendaEventConferenceService agendaEventConferenceService;

  private AgendaRemoteEventService     agendaRemoteEventService;

  private SettingService               settingService;

  private AgendaUserSettings           defaultUserSettings            = null;

  private List<EventReminderParameter> defaultReminders               = new ArrayList<>();

  public AgendaUserSettingsServiceImpl(AgendaEventConferenceService agendaEventConferenceService,
                                       AgendaRemoteEventService agendaRemoteEventService,
                                       SettingService settingService,
                                       InitParams initParams) {
    this.agendaEventConferenceService = agendaEventConferenceService;
    this.agendaRemoteEventService = agendaRemoteEventService;
    this.settingService = settingService;

    Iterator<ObjectParameter> objectParamIterator = initParams.getObjectParamIterator();
    if (objectParamIterator != null) {
      while (objectParamIterator.hasNext()) {
        ObjectParameter objectParameter = objectParamIterator.next();
        Object objectParam = objectParameter.getObject();
        if (objectParam instanceof EventReminderParameter) {
          EventReminderParameter eventReminderParameter = (EventReminderParameter) objectParam;
          defaultReminders.add(eventReminderParameter);
        }
      }
    }

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
    List<RemoteProvider> remoteProviders = agendaRemoteEventService.getRemoteProviders();
    AgendaUserSettings agendaUserSettings = null;
    if (settingValue == null || settingValue.getValue() == null || StringUtils.isBlank(settingValue.getValue().toString())) {
      agendaUserSettings = defaultUserSettings.clone();
      agendaUserSettings.setReminders(getDefaultReminders());
      agendaUserSettings.setRemoteProviders(remoteProviders);
    } else {
      agendaUserSettings = AgendaUserSettings.fromString(settingValue.getValue().toString());
      agendaUserSettings.setRemoteProviders(remoteProviders);
    }

    List<String> enabledWebConferenceProviders = agendaEventConferenceService.getEnabledWebConferenceProviders();
    agendaUserSettings.setWebConferenceProviders(enabledWebConferenceProviders);
    return agendaUserSettings;
  }

  @Override
  public void saveUserConnector(String connectorName, String connectorUserId, long userIdentityId) {
    if (StringUtils.isBlank(connectorName)) {
      throw new IllegalArgumentException("connectorName parameter is mandatory");
    }
    if (StringUtils.isBlank(connectorUserId)) {
      throw new IllegalArgumentException("connectorUserId parameter is mandatory");
    }
    if (userIdentityId <= 0) {
      throw new IllegalArgumentException("userIdentityId parameter is mandatory");
    }

    AgendaUserSettings agendaUserSettings = getAgendaUserSettings(userIdentityId);

    List<RemoteProvider> remoteProviders = agendaUserSettings.getRemoteProviders();
    boolean enabledRemoteProvider = remoteProviders.stream()
                                                   .anyMatch(remoteProvider -> StringUtils.equals(remoteProvider.getName(),
                                                                                                  connectorName)
                                                       && remoteProvider.isEnabled());

    if (!enabledRemoteProvider) {
      throw new IllegalStateException("Connector " + connectorName + " is not enabled");
    }

    agendaUserSettings.setConnectedRemoteUserId(connectorUserId);
    agendaUserSettings.setConnectedRemoteProvider(connectorName);
    saveAgendaUserSettings(userIdentityId, agendaUserSettings);
  }

  @Override
  public List<EventReminderParameter> getDefaultReminders() {
    return Collections.unmodifiableList(defaultReminders);
  }

}
