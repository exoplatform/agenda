package org.exoplatform.agenda.service;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.agenda.model.AgendaUserSettings;

import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ObjectParameter;

import java.util.*;
import java.util.stream.Collectors;

public class AgendaUserSettingsServiceImpl implements AgendaUserSettingsService {

  private static final String      AGENDA_USER_SETTING_SEPARATOR = "@@";

  private static final String      AGENDA_USER_SETTING_KEY       = "Reminders";

  private static final Scope       AGENDA_USER_SETTING_SCOPE     = Scope.GLOBAL;

  private SettingService           settingService;

  private List<AgendaUserSettings> defaultUserSettings           = new ArrayList<>();

  public AgendaUserSettingsServiceImpl(SettingService settingService, InitParams initParams) {
    this.settingService = settingService;

    Iterator<ObjectParameter> objectParamIterator = initParams.getObjectParamIterator();
    if (objectParamIterator != null) {
      while (objectParamIterator.hasNext()) {
        ObjectParameter objectParameter = objectParamIterator.next();
        Object objectParam = objectParameter.getObject();
        if (objectParam instanceof AgendaUserSettings) {
          AgendaUserSettings agendaUserSettings = (AgendaUserSettings) objectParam;
          defaultUserSettings.add(agendaUserSettings);
        }
      }
    }
  }

  @Override
  public void saveAgendaUserSettings(long identityId, AgendaUserSettings agendaUserSettings) {
    if (agendaUserSettings == null) {
      this.settingService.set(Context.USER.id(String.valueOf(identityId)),
                              AGENDA_USER_SETTING_SCOPE,
                              AGENDA_USER_SETTING_KEY,
                              SettingValue.create(""));
    } else {
      String userSettingString = StringUtils.join(agendaUserSettings, AGENDA_USER_SETTING_SEPARATOR);
      this.settingService.set(Context.USER.id(String.valueOf(identityId)),
                              AGENDA_USER_SETTING_SCOPE,
                              AGENDA_USER_SETTING_KEY,
                              SettingValue.create(userSettingString));

    }
  }

  @Override
  public List<AgendaUserSettings> getDefaultAgendaUserSettings(long identityId) {
    SettingValue<?> settingValue = this.settingService.get(Context.USER.id(String.valueOf(identityId)),
                                                           AGENDA_USER_SETTING_SCOPE,
                                                           AGENDA_USER_SETTING_KEY);

    if (settingValue == null) {
      return Collections.unmodifiableList(defaultUserSettings);
    } else if (settingValue.getValue() == null || StringUtils.isBlank(settingValue.getValue().toString())) {
      return Collections.emptyList();
    } else {
      String remindersSettingString = settingValue.getValue().toString();
      String[] values = StringUtils.split(remindersSettingString, AGENDA_USER_SETTING_SEPARATOR);
      return Arrays.stream(values).map(AgendaUserSettings::fromString).collect(Collectors.toList());
    }
  }
}
