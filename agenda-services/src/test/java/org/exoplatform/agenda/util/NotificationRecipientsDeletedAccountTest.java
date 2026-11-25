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
package org.exoplatform.agenda.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins that a deleted account is left out of the recipients of an event's
 * e-mail while it stays in the e-mail's attendees line, and that an active
 * user and a guest invited by e-mail are kept (EXO-92245). The invitation,
 * change and cancellation e-mails share
 * {@code NotificationUtils#setNotificationRecipients}
 * (AgendaNotificationPlugin and DatePollNotificationPlugin call it); the
 * reminder has its own method.
 */
class NotificationRecipientsDeletedAccountTest {

  private static final long ACTIVE  = 1;

  private static final long DELETED = 2;

  private static final long GUEST   = 3;

  private IdentityManager   identityManager;

  private SpaceService      spaceService;

  @BeforeEach
  void setUp() {
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    identity(ACTIVE, OrganizationIdentityProvider.NAME, "active", false);
    identity(DELETED, OrganizationIdentityProvider.NAME, "gone", true);
    identity(GUEST, AgendaGuestUserIdentityProvider.NAME, "guest@example.org", false);
  }

  @Test
  void deletedAttendeeIsLeftOutOfEventRecipients() {
    NotificationInfo notification = new NotificationInfo();
    NotificationUtils.setNotificationRecipients(identityManager,
                                                notification,
                                                spaceService,
                                                List.of(attendee(ACTIVE), attendee(DELETED), attendee(GUEST)),
                                                new Event(),
                                                "ADDED",
                                                ACTIVE);
    assertEquals(Set.of("active", "guest@example.org"), Set.copyOf(notification.getSendToUserIds()));
  }

  @Test
  void deletedAttendeeStaysInEventAttendeesLine() {
    NotificationInfo notification = new NotificationInfo();
    NotificationUtils.setNotificationRecipients(identityManager,
                                                notification,
                                                spaceService,
                                                List.of(attendee(ACTIVE), attendee(DELETED), attendee(GUEST)),
                                                new Event(),
                                                "ADDED",
                                                ACTIVE);
    String attendees = notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_ATTENDEES);
    assertEquals(Set.of("active", "gone", "guest@example.org"), Set.of(attendees.split(", ")));
  }

  @Test
  void deletedAttendeeAloneYieldsNoEventRecipient() {
    NotificationInfo notification = new NotificationInfo();
    NotificationUtils.setNotificationRecipients(identityManager,
                                                notification,
                                                spaceService,
                                                List.of(attendee(DELETED)),
                                                new Event(),
                                                "ADDED",
                                                ACTIVE);
    assertTrue(notification.getSendToUserIds().isEmpty());
  }

  @Test
  void deletedReceiverIsLeftOutOfReminderRecipients() {
    NotificationInfo notification = new NotificationInfo();
    NotificationUtils.setEventReminderNotificationRecipients(identityManager, notification, ACTIVE, DELETED);
    assertEquals(List.of("active"), notification.getSendToUserIds());
  }

  private void identity(long id, String provider, String remoteId, boolean deleted) {
    Identity identity = new Identity(String.valueOf(id));
    identity.setProviderId(provider);
    identity.setRemoteId(remoteId);
    identity.setDeleted(deleted);
    Profile profile = mock(Profile.class);
    when(profile.getFullName()).thenReturn(remoteId);
    identity.setProfile(profile);
    when(identityManager.getIdentity(String.valueOf(id))).thenReturn(identity);
  }

  private static EventAttendee attendee(long identityId) {
    EventAttendee attendee = new EventAttendee();
    attendee.setIdentityId(identityId);
    return attendee;
  }
}
