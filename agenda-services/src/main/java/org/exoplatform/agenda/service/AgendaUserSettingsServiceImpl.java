package org.exoplatform.agenda.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.EventReminderParameter;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ObjectParameter;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.UserProfile;
import org.exoplatform.services.organization.UserProfileHandler;

public class AgendaUserSettingsServiceImpl implements AgendaUserSettingsService {

  private static final String          AGENDA_USER_SETTINGS_PARAM_KEY = "agenda.user.settings";

  private static final Scope           AGENDA_USER_SETTING_SCOPE      = Scope.APPLICATION.id("Agenda");

  private static final String          AGENDA_USER_SETTING_KEY        = "AgendaSettings";

  private static final String          TIMEZONE                       = "user.timeZone";

  private static final String          EMBED_MAP_PROVIDER_KEY         = "embedMapProvider";

  private AgendaEventConferenceService agendaEventConferenceService;

  private AgendaRemoteEventService     agendaRemoteEventService;

  private SettingService               settingService;

  private OrganizationService          organizationService;

  private AgendaUserSettings           defaultUserSettings            = null;

  private List<EventReminderParameter> defaultReminders               = new ArrayList<>();

  public AgendaUserSettingsServiceImpl(AgendaEventConferenceService agendaEventConferenceService,
                                       AgendaRemoteEventService agendaRemoteEventService,
                                       SettingService settingService,
                                       OrganizationService organizationService,
                                       InitParams initParams) {
    this.agendaEventConferenceService = agendaEventConferenceService;
    this.agendaRemoteEventService = agendaRemoteEventService;
    this.settingService = settingService;
    this.organizationService = organizationService;

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
  public void updateUserTimeZone(String userName, String timeZone) throws ObjectNotFoundException {
    try {
      UserProfileHandler userProfileHandler = organizationService.getUserProfileHandler();
      UserProfile userProfile = userProfileHandler.findUserProfileByName(userName);
      userProfile.setAttribute(TIMEZONE, timeZone);
      userProfileHandler.saveUserProfile(userProfile, true);
    } catch (Exception e) {
      throw new ObjectNotFoundException("User profile wasn't found");
    }
  }

  @Override
  public List<EventReminderParameter> getDefaultReminders() {
    return Collections.unmodifiableList(defaultReminders);
  }

  @Override
  public String getEmbedMapProvider() {
    SettingValue<?> settingValue = this.settingService.get(Context.GLOBAL,
                                                           AGENDA_USER_SETTING_SCOPE,
                                                           EMBED_MAP_PROVIDER_KEY);
    return settingValue != null && settingValue.getValue() != null
        ? settingValue.getValue().toString()
        : null;
  }

  @Override
  public void saveEmbedMapProvider(String providerId) {
    if (StringUtils.isBlank(providerId)) {
      throw new IllegalArgumentException("providerId is mandatory");
    }
    this.settingService.set(Context.GLOBAL,
                            AGENDA_USER_SETTING_SCOPE,
                            EMBED_MAP_PROVIDER_KEY,
                            SettingValue.create(providerId));
  }

  @Override
  public void removeEmbedMapProvider() {
    this.settingService.remove(Context.GLOBAL,
                               AGENDA_USER_SETTING_SCOPE,
                               EMBED_MAP_PROVIDER_KEY);
  }

}
