/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.MailInvitation;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.metadata.MetadataService;

/**
 * Agenda claims a user's calendar only once it has read that they connected no
 * remote calendar account: settings that cannot be read claim nothing
 * (EXO-90866). The connected-account case itself runs on the real container in
 * {@code AgendaInvitationLandingServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaInvitationLandingSettingsTest {

  @Mock
  private IdentityManager                identityManager;

  @Mock
  private AgendaCalendarService          agendaCalendarService;

  @Mock
  private AgendaEventService             agendaEventService;

  @Mock
  private AgendaEventAttendeeService     agendaEventAttendeeService;

  @Mock
  private AgendaEventReminderService     agendaEventReminderService;

  @Mock
  private AgendaEventConferenceService   agendaEventConferenceService;

  @Mock
  private AgendaRemoteEventService       agendaRemoteEventService;

  @Mock
  private AgendaUserSettingsService      agendaUserSettingsService;

  @Mock
  private MetadataService                metadataService;

  @Mock
  private AgendaEventStorage             agendaEventStorage;

  private AgendaInvitationLandingService service;

  /**
   * An enabled user, identity 7.
   */
  @BeforeEach
  void setUp() {
    service = new AgendaInvitationLandingService(identityManager,
                                                 agendaCalendarService,
                                                 agendaEventService,
                                                 agendaEventAttendeeService,
                                                 agendaEventReminderService,
                                                 agendaEventConferenceService,
                                                 agendaRemoteEventService,
                                                 agendaUserSettingsService,
                                                 metadataService,
                                                 agendaEventStorage);
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, "john");
    identity.setId("7");
    when(identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "john")).thenReturn(identity);
  }

  /**
   * Settings read with no connected account: agenda's.
   */
  @Test
  void claimsAUserWithNoConnectedAccount() {
    when(agendaUserSettingsService.getAgendaUserSettings(anyLong())).thenReturn(new AgendaUserSettings());
    assertTrue(service.holdsCalendarFor("john"));
  }

  /**
   * Settings that cannot be read: not agenda's, and a landing asked anyway
   * lands nothing, before reading the message.
   */
  @Test
  void claimsNothingWhenTheSettingsCannotBeRead() {
    when(agendaUserSettingsService.getAgendaUserSettings(anyLong())).thenThrow(new IllegalStateException("settings down"));
    assertFalse(service.holdsCalendarFor("john"));
    assertNull(service.land(new MailInvitation("john", null, "REQUEST", "uid-1", null, 0, EventAttendeeResponse.ACCEPTED, "not read")));
  }
}
