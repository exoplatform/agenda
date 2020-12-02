package org.exoplatform.agenda.service;

import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaUserSettingsService {

  /**
   * Save user agenda settings to use in events by default
   * 
   * @param identityId technical identifier of {@link Identity}
   * @param agendaUserSettings object of {@link AgendaUserSettings}
   */
  void saveAgendaUserSettings(long identityId, AgendaUserSettings agendaUserSettings);

  /**
   * Get list of user settings to use in events by default
   * 
   * @param identityId technical identifier of {@link Identity}
   * @return {@link AgendaUserSettings}
   */
  AgendaUserSettings getAgendaUserSettings(long identityId);

  /**
   * Saves the new connected user settings on remote events provider
   * 
   * @param connectorName connector identifier
   * @param connectorUserId user identifier on remote provider
   * @param userIdentityId user social identifier
   */
  void saveUserConnector(String connectorName, String connectorUserId, long userIdentityId);

}
