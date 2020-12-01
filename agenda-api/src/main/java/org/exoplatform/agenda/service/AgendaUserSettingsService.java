package org.exoplatform.agenda.service;

import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.social.core.identity.model.Identity;

import java.util.List;

public interface AgendaUserSettingsService {

  /**
   * Save user agenda settings to use in events by default
   * @param identityId technical identifier of {@link Identity}
   * @param agendaUserSettings object of {@link AgendaUserSettings}
   */
  void saveAgendaUserSettings(long identityId, AgendaUserSettings agendaUserSettings);

  /**
   * Get list of user settings to use in events by default
   * @param identityId technical identifier of {@link Identity}
   * @return
   */
  List<AgendaUserSettings> getDefaultAgendaUserSettings(long identityId);
}
