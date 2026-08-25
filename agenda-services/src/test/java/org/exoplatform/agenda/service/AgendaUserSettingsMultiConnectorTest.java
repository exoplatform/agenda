/*
 * Copyright (C) 2026 eXo Platform SAS.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.exoplatform.agenda.model.AgendaConnectorAccount;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.RemoteProvider;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.organization.OrganizationService;

/**
 * A user must be able to hold one CalDAV account plus one or more remote
 * accounts (Google, Office 365) at the same time: the connected account
 * becomes a per-provider list, legacy single-connector blobs must keep
 * parsing unchanged with no migration job, and connecting or disconnecting
 * one provider must never touch another provider's account.
 */
@ExtendWith(MockitoExtension.class)
class AgendaUserSettingsMultiConnectorTest {

  private static final long           USER_IDENTITY_ID = 2223L;

  private static final String         CALDAV           = "agenda.caldavCalendar";

  private static final String         GOOGLE           = "agenda.googleCalendar";

  @Mock
  private AgendaEventConferenceService agendaEventConferenceService;

  @Mock
  private AgendaRemoteEventService     agendaRemoteEventService;

  @Mock
  private SettingService               settingService;

  @Mock
  private OrganizationService          organizationService;

  private AgendaUserSettingsService    agendaUserSettingsService;

  /**
   * The blob the setting store currently holds for the test user, updated by
   * the mocked save so that a save followed by a read behaves like the real
   * store.
   */
  private String                       storedSettingsBlob;

  /**
   * Builds the service against a mocked setting store that reads and writes
   * {@link #storedSettingsBlob}, with the CalDAV and Google providers both
   * enabled.
   *
   * @throws Exception when the InitParams stub cannot be built
   */
  @BeforeEach
  void setUp() throws Exception {
    agendaUserSettingsService = new AgendaUserSettingsServiceImpl(agendaEventConferenceService,
                                                                  agendaRemoteEventService,
                                                                  settingService,
                                                                  organizationService,
                                                                  new InitParams());
    lenient().when(agendaRemoteEventService.getRemoteProviders())
             .thenReturn(Arrays.asList(new RemoteProvider(1, CALDAV, "apiKey", "secretKey", true, true),
                                       new RemoteProvider(2, GOOGLE, "apiKey", "secretKey", true, true)));
    lenient().when(settingService.get(any(Context.class), any(Scope.class), anyString()))
             .thenAnswer(invocation -> storedSettingsBlob == null ? null : SettingValue.create(storedSettingsBlob));
    lenient().doAnswer(invocation -> {
      SettingValue<?> value = invocation.getArgument(3);
      storedSettingsBlob = value.getValue().toString();
      return null;
    }).when(settingService).set(any(Context.class), any(Scope.class), anyString(), any(SettingValue.class));
  }

  /**
   * A blob written before several accounts could coexist carries only the two
   * legacy fields: it must parse unchanged and its one account must
   * materialise in the list, opted into receiving copies.
   */
  @Test
  void testLegacyBlobMapsLazilyIntoConnectedConnectors() {
    AgendaUserSettings settings =
                                AgendaUserSettings.fromString("{\"agendaDefaultView\":\"week\",\"connectedRemoteProvider\":\""
                                    + GOOGLE + "\",\"connectedRemoteUserId\":\"user@gmail.com\"}");
    assertNotNull(settings);
    List<AgendaConnectorAccount> accounts = settings.getConnectedConnectors();
    assertEquals(1, accounts.size());
    assertEquals(GOOGLE, accounts.get(0).getProviderName());
    assertEquals("user@gmail.com", accounts.get(0).getRemoteUserId());
    assertTrue(accounts.get(0).isPushEnabled());
  }

  /**
   * Several accounts, including a copy opt-out, must survive the JSON
   * round-trip the settings store performs, and the legacy fields must mirror
   * the first entry so a downgraded platform still sees one coherent account.
   */
  @Test
  void testMultiAccountRoundTripAndLegacyMirror() {
    AgendaUserSettings settings = new AgendaUserSettings();
    settings.addOrUpdateConnectedConnector(CALDAV, "user@caldav.example");
    settings.addOrUpdateConnectedConnector(GOOGLE, "user@gmail.com");
    settings.getConnectedConnectors().get(1).setPushEnabled(false);

    AgendaUserSettings reread = AgendaUserSettings.fromString(settings.toString());
    assertNotNull(reread);
    List<AgendaConnectorAccount> accounts = reread.getConnectedConnectors();
    assertEquals(2, accounts.size());
    assertEquals(CALDAV, accounts.get(0).getProviderName());
    assertTrue(accounts.get(0).isPushEnabled());
    assertEquals(GOOGLE, accounts.get(1).getProviderName());
    assertFalse(accounts.get(1).isPushEnabled());
    assertEquals(CALDAV, reread.getConnectedRemoteProvider());
    assertEquals("user@caldav.example", reread.getConnectedRemoteUserId());
  }

  /**
   * Removing one provider's account must leave the other's untouched, keep
   * the legacy mirror pointing at what remains, and removing the last account
   * must clear the legacy fields so the lazy mapping cannot resurrect it.
   */
  @Test
  void testRemoveConnectedConnectorIsPerProvider() {
    AgendaUserSettings settings = new AgendaUserSettings();
    settings.addOrUpdateConnectedConnector(CALDAV, "user@caldav.example");
    settings.addOrUpdateConnectedConnector(GOOGLE, "user@gmail.com");

    settings.removeConnectedConnector(CALDAV);
    assertEquals(1, settings.getConnectedConnectors().size());
    assertEquals(GOOGLE, settings.getConnectedConnectors().get(0).getProviderName());
    assertEquals(GOOGLE, settings.getConnectedRemoteProvider());
    assertEquals("user@gmail.com", settings.getConnectedRemoteUserId());

    settings.removeConnectedConnector(GOOGLE);
    assertTrue(settings.getConnectedConnectors().isEmpty());
    assertNull(settings.getConnectedRemoteProvider());
    assertNull(settings.getConnectedRemoteUserId());
  }

  /**
   * A blank provider name must remove every account at once — the behaviour
   * the reset endpoint had when only one account could exist.
   */
  @Test
  void testRemoveConnectedConnectorBlankClearsAll() {
    AgendaUserSettings settings = new AgendaUserSettings();
    settings.addOrUpdateConnectedConnector(CALDAV, "user@caldav.example");
    settings.addOrUpdateConnectedConnector(GOOGLE, "user@gmail.com");

    settings.removeConnectedConnector(null);
    assertTrue(settings.getConnectedConnectors().isEmpty());
    assertNull(settings.getConnectedRemoteProvider());
  }

  /**
   * Connecting a second provider must add its account beside the first, not
   * replace it: this is the exact single-connector eviction this change
   * removes.
   */
  @Test
  void testSaveUserConnectorUpsertsPerProvider() {
    agendaUserSettingsService.saveUserConnector(CALDAV, "user@caldav.example", USER_IDENTITY_ID);
    agendaUserSettingsService.saveUserConnector(GOOGLE, "user@gmail.com", USER_IDENTITY_ID);

    AgendaUserSettings settings = agendaUserSettingsService.getAgendaUserSettings(USER_IDENTITY_ID);
    List<AgendaConnectorAccount> accounts = settings.getConnectedConnectors();
    assertEquals(2, accounts.size());
    assertEquals(CALDAV, accounts.get(0).getProviderName());
    assertEquals(GOOGLE, accounts.get(1).getProviderName());
  }

  /**
   * Reconnecting a provider that already holds an account must replace that
   * account's remote user id in place, keeping one account per provider.
   */
  @Test
  void testSaveUserConnectorReplacesSameProviderAccount() {
    agendaUserSettingsService.saveUserConnector(GOOGLE, "first@gmail.com", USER_IDENTITY_ID);
    agendaUserSettingsService.saveUserConnector(GOOGLE, "second@gmail.com", USER_IDENTITY_ID);

    AgendaUserSettings settings = agendaUserSettingsService.getAgendaUserSettings(USER_IDENTITY_ID);
    List<AgendaConnectorAccount> accounts = settings.getConnectedConnectors();
    assertEquals(1, accounts.size());
    assertEquals("second@gmail.com", accounts.get(0).getRemoteUserId());
  }

  /**
   * A user whose stored blob predates the list must be able to connect a
   * second account without losing the legacy one: the lazy mapping and the
   * upsert must compose.
   */
  @Test
  void testSaveUserConnectorPreservesLegacyAccount() {
    storedSettingsBlob = "{\"connectedRemoteProvider\":\"" + CALDAV
        + "\",\"connectedRemoteUserId\":\"user@caldav.example\"}";

    agendaUserSettingsService.saveUserConnector(GOOGLE, "user@gmail.com", USER_IDENTITY_ID);

    AgendaUserSettings settings = agendaUserSettingsService.getAgendaUserSettings(USER_IDENTITY_ID);
    List<AgendaConnectorAccount> accounts = settings.getConnectedConnectors();
    assertEquals(2, accounts.size());
    assertEquals(CALDAV, accounts.get(0).getProviderName());
    assertEquals("user@caldav.example", accounts.get(0).getRemoteUserId());
    assertEquals(GOOGLE, accounts.get(1).getProviderName());
  }

  /**
   * Disconnecting one provider through the service must leave the other
   * provider's account in the stored blob, and a blank name must clear every
   * account.
   */
  @Test
  void testRemoveUserConnectorIsPerProvider() {
    agendaUserSettingsService.saveUserConnector(CALDAV, "user@caldav.example", USER_IDENTITY_ID);
    agendaUserSettingsService.saveUserConnector(GOOGLE, "user@gmail.com", USER_IDENTITY_ID);

    agendaUserSettingsService.removeUserConnector(GOOGLE, USER_IDENTITY_ID);
    AgendaUserSettings settings = agendaUserSettingsService.getAgendaUserSettings(USER_IDENTITY_ID);
    assertEquals(1, settings.getConnectedConnectors().size());
    assertEquals(CALDAV, settings.getConnectedConnectors().get(0).getProviderName());

    agendaUserSettingsService.removeUserConnector(null, USER_IDENTITY_ID);
    settings = agendaUserSettingsService.getAgendaUserSettings(USER_IDENTITY_ID);
    assertTrue(settings.getConnectedConnectors().isEmpty());
  }

  /**
   * Cloning settings must deep-copy the accounts: the default-settings
   * template is cloned per user, and a shared account list would leak one
   * user's accounts into another's defaults.
   */
  @Test
  void testCloneDeepCopiesAccounts() {
    AgendaUserSettings settings = new AgendaUserSettings();
    settings.addOrUpdateConnectedConnector(GOOGLE, "user@gmail.com");

    AgendaUserSettings clonedSettings = settings.clone();
    clonedSettings.getConnectedConnectors().get(0).setRemoteUserId("other@gmail.com");

    assertEquals("user@gmail.com", settings.getConnectedConnectors().get(0).getRemoteUserId());
  }

}
