/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.service;

import static org.junit.Assert.*;

import java.util.Collections;

import org.apache.commons.lang3.StringUtils;
import org.exoplatform.agenda.constant.AvailabilitySharing;
import org.exoplatform.agenda.constant.ReminderPeriodType;
import org.exoplatform.agenda.model.AgendaConnectorAccount;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.EventReminderParameter;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.organization.OrganizationService;
import org.exoplatform.services.organization.UserProfile;
import org.junit.After;
import org.junit.Test;

public class AgendaUserSettingsServiceTest extends BaseAgendaEventTest {

  @After
  @Override
  public void tearDown() throws ObjectNotFoundException {
    super.tearDown();
    agendaUserSettingsService.removeEmbedMapProvider();
  }

  @Test
  public void testDefaultSettings() throws Exception { // NOSONAR
    AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(56111l);
    assertNotNull(agendaUserSettings);
    assertNotNull(agendaUserSettings.getRemoteProviders());
    assertNotNull(agendaUserSettings.getAgendaDefaultView());
    assertNotNull(agendaUserSettings.getAgendaWeekStartOn());
    assertTrue(StringUtils.isBlank(agendaUserSettings.getConnectedRemoteProvider()));
    assertTrue(StringUtils.isBlank(agendaUserSettings.getConnectedRemoteUserId()));
    assertTrue(StringUtils.isBlank(agendaUserSettings.getTimeZoneId()));
    assertNotNull(agendaUserSettings.getWorkingTimeEnd());
    assertNotNull(agendaUserSettings.getWorkingTimeStart());
  }

  @Test
  public void testSaveUserSettings() throws Exception { // NOSONAR
    long identityId = 2223l;
    AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);

    agendaUserSettings.setAgendaDefaultView("agendaDefaultView");
    agendaUserSettings.setAgendaWeekStartOn("agendaWeekStartOn");
    agendaUserSettings.setConnectedRemoteProvider("connectedRemoteProvider");
    agendaUserSettings.setConnectedRemoteUserId("connectedRemoteUserId");
    agendaUserSettings.setReminders(Collections.singletonList(new EventReminderParameter(5, ReminderPeriodType.DAY)));
    agendaUserSettings.setShowWorkingTime(false);
    agendaUserSettings.setWorkingTimeEnd("workingTimeEnd");
    agendaUserSettings.setWorkingTimeStart("workingTimeStart");
    agendaUserSettings.setTimeZoneId("timeZoneId");

    agendaUserSettingsService.saveAgendaUserSettings(identityId, agendaUserSettings);

    AgendaUserSettings storedAgendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);
    assertEquals(agendaUserSettings.getAgendaDefaultView(), storedAgendaUserSettings.getAgendaDefaultView());
    assertEquals(agendaUserSettings.getAgendaWeekStartOn(), storedAgendaUserSettings.getAgendaWeekStartOn());
    assertEquals(agendaUserSettings.getConnectedRemoteProvider(), storedAgendaUserSettings.getConnectedRemoteProvider());
    assertEquals(agendaUserSettings.getConnectedRemoteUserId(), storedAgendaUserSettings.getConnectedRemoteUserId());
    assertEquals(agendaUserSettings.getReminders(), storedAgendaUserSettings.getReminders());
    assertEquals(agendaUserSettings.getTimeZoneId(), storedAgendaUserSettings.getTimeZoneId());
    assertEquals(agendaUserSettings.getTimeZoneId(), storedAgendaUserSettings.getTimeZoneId());
    assertEquals(agendaUserSettings.getWorkingTimeEnd(), storedAgendaUserSettings.getWorkingTimeEnd());
    assertEquals(agendaUserSettings.getWorkingTimeStart(), storedAgendaUserSettings.getWorkingTimeStart());
  }

  /**
   * Connecting stores the account beside the ones already held — one per
   * provider — rather than overwriting a single pair of fields, and the
   * legacy fields keep mirroring the first held account for downgrade
   * safety. A disabled provider still refuses the connection.
   *
   * @throws Exception when the remote provider cannot be prepared
   */
  @Test
  public void testSaveUserConnector() throws Exception { // NOSONAR
    long identityId = 2223l;

    RemoteProvider remoteProvider = new RemoteProvider(0, "connectorName", "Client API Key", "Client Secret Key", true, true);
    remoteProvider = agendaRemoteEventService.saveRemoteProvider(remoteProvider);
    assertNotNull(remoteProvider);

    agendaUserSettingsService.saveUserConnector("connectorName", "connectorUserId", identityId);
    AgendaUserSettings agendaUserSettings = agendaUserSettingsService.getAgendaUserSettings(identityId);

    AgendaConnectorAccount account = agendaUserSettings.getConnectedConnectors()
                                                       .stream()
                                                       .filter(connectedAccount -> "connectorName".equals(connectedAccount.getProviderName()))
                                                       .findFirst()
                                                       .orElse(null);
    assertNotNull(account);
    assertEquals("connectorUserId", account.getRemoteUserId());
    // The legacy fields mirror the first held account, whichever provider it
    // is on, so an older platform reading this blob still sees one coherent
    // account
    assertEquals(agendaUserSettings.getConnectedConnectors().get(0).getProviderName(),
                 agendaUserSettings.getConnectedRemoteProvider());
    assertEquals(agendaUserSettings.getConnectedConnectors().get(0).getRemoteUserId(),
                 agendaUserSettings.getConnectedRemoteUserId());

    remoteProvider.setEnabled(false);
    agendaRemoteEventService.saveRemoteProvider(remoteProvider);
    try {
      agendaUserSettingsService.saveUserConnector("connectorName", "connectorUserId", identityId);
      fail();
    } catch (Exception e) {
      // Expected
    }
  }

  @Test
  public void testupdateUserTimeZone() throws Exception { // NOSONAR
    String timeZone = "UTC";
    agendaUserSettingsService.updateUserTimeZone("testuser1", timeZone);
    UserProfile userProfile = CommonsUtils.getService(OrganizationService.class).getUserProfileHandler().findUserProfileByName("testuser1");
    assertEquals("UTC", userProfile.getAttribute("user.timeZone"));
  }

  @Test
  public void testGetEmbedMapProviderWhenNotSet() {
    String embedMapProvider = agendaUserSettingsService.getEmbedMapProvider();
    assertNull(embedMapProvider);
  }

  @Test
  public void testSaveAndGetEmbedMapProvider() {
    agendaUserSettingsService.saveEmbedMapProvider("google-maps");

    String storedProviderId = agendaUserSettingsService.getEmbedMapProvider();
    assertEquals("google-maps", storedProviderId);
  }

  @Test
  public void testSaveEmbedMapProviderOverridesExisting() {
    agendaUserSettingsService.saveEmbedMapProvider("google-maps");
    agendaUserSettingsService.saveEmbedMapProvider("openStreet-map");

    String storedProviderId = agendaUserSettingsService.getEmbedMapProvider();
    assertEquals("openStreet-map", storedProviderId);
  }

  @Test
  public void testRemoveEmbedMapProvider() {
    agendaUserSettingsService.saveEmbedMapProvider("google-maps");
    agendaUserSettingsService.removeEmbedMapProvider();

    String storedProviderId = agendaUserSettingsService.getEmbedMapProvider();
    assertNull(storedProviderId);
  }

  /**
   * A user who never touched the setting shares with the people they are in a
   * space with. Pinned against the real settings store, not a mock: "nothing
   * stored" is a state only the store can produce, and it is the state every
   * user is in on the day this ships.
   *
   * @throws Exception when the container misbehaves
   */
  @Test
  public void testAvailabilitySharingDefaultsToSharedSpaces() throws Exception { // NOSONAR
    assertEquals(AvailabilitySharing.SHARED_SPACES, agendaUserSettingsService.getAvailabilitySharing(56222l));
  }

  /**
   * The choice survives a real write and a real read, and it is stored beside
   * the settings blob rather than inside it — so saving the blob afterwards
   * cannot take it with it.
   *
   * @throws Exception when the container misbehaves
   */
  @Test
  public void testSaveAndReadAvailabilitySharing() throws Exception { // NOSONAR
    agendaUserSettingsService.saveAvailabilitySharing(56223l, AvailabilitySharing.NOBODY);
    assertEquals(AvailabilitySharing.NOBODY, agendaUserSettingsService.getAvailabilitySharing(56223l));

    AgendaUserSettings settings = agendaUserSettingsService.getAgendaUserSettings(56223l);
    agendaUserSettingsService.saveAgendaUserSettings(56223l, settings);
    assertEquals("saving the settings blob must not reset a disclosure choice it does not carry",
                 AvailabilitySharing.NOBODY,
                 agendaUserSettingsService.getAvailabilitySharing(56223l));

    agendaUserSettingsService.saveAvailabilitySharing(56223l, AvailabilitySharing.EVERYONE);
    assertEquals(AvailabilitySharing.EVERYONE, agendaUserSettingsService.getAvailabilitySharing(56223l));
  }

  /**
   * A stored value nobody can make sense of is read as "nobody", not as the
   * default: absence means the user never chose, a corrupt value means the
   * store is broken, and a broken store must never be the reason someone's
   * calendar opens up.
   *
   * @throws Exception when the container misbehaves
   */
  @Test
  public void testUnknownStoredAvailabilitySharingIsReadAsNobody() throws Exception { // NOSONAR
    SettingService settingService = CommonsUtils.getService(SettingService.class);
    settingService.set(Context.USER.id("56224"),
                       Scope.APPLICATION.id("Agenda"),
                       "shareAvailability",
                       SettingValue.create("everybody-in-the-world"));

    assertEquals(AvailabilitySharing.NOBODY, agendaUserSettingsService.getAvailabilitySharing(56224l));
  }

  /**
   * The setting is per user, so one user's choice never answers for another.
   *
   * @throws Exception when the container misbehaves
   */
  @Test
  public void testAvailabilitySharingIsPerUser() throws Exception { // NOSONAR
    agendaUserSettingsService.saveAvailabilitySharing(56225l, AvailabilitySharing.NOBODY);

    assertEquals(AvailabilitySharing.NOBODY, agendaUserSettingsService.getAvailabilitySharing(56225l));
    assertEquals(AvailabilitySharing.SHARED_SPACES, agendaUserSettingsService.getAvailabilitySharing(56226l));
  }

}
