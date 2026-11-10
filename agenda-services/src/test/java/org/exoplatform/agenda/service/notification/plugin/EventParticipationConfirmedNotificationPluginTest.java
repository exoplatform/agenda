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
package org.exoplatform.agenda.service.notification.plugin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeePromotion;
import org.exoplatform.agenda.notification.plugin.EventParticipationConfirmedNotificationPlugin;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.identity.model.Identity;

public class EventParticipationConfirmedNotificationPluginTest extends BaseAgendaEventTest {

  /**
   * A user who got a seat is told through their account, and they alone; an
   * event that is not recurring opens on itself and names no date.
   */
  @Test
  public void testAUserIsToldThroughTheirAccount() throws Exception {
    Event event = singleEvent(testuser2Identity);

    NotificationInfo notification = notify(event.getId(), testuser2Identity, null, 0, false);

    assertNotNull(notification);
    assertEquals(List.of(testuser2Identity.getRemoteId()), notification.getSendToUserIds());
    assertEquals(event.getSummary(), notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_TITLE));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL)
                           .endsWith("?eventId=" + event.getId()));
    assertNull(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertEquals("false", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_IS_GUEST));
  }

  /**
   * A guest without an account who got a seat is told through the address
   * the invitation went to, marked as a guest so that only the mail reaches
   * them.
   */
  @Test
  public void testAGuestIsToldThroughTheAddressTheInvitationWentTo() throws Exception {
    String guestEmail = "promoted.guest@example.com";
    Identity guest = identityManager.getOrCreateIdentity(AgendaGuestUserIdentityProvider.NAME, guestEmail);
    assertNotNull("the guest identity provider must be registered", guest);
    Event event = singleEvent(guest);

    NotificationInfo notification = notify(event.getId(), guest, null, 0, false);

    assertNotNull(notification);
    assertEquals(List.of(guestEmail), notification.getSendToUserIds());
    assertEquals("true", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_IS_GUEST));
  }

  /**
   * An event no longer confirmed when the notification is built gives no
   * seat to tell about.
   */
  @Test
  public void testNobodyIsToldOnAnEventNoLongerConfirmed() throws Exception {
    Event event = singleEvent(testuser2Identity);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    event.setStatus(EventStatus.CANCELLED);
    agendaEventService.updateEvent(event,
                                   agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees(),
                                   null,
                                   null,
                                   null,
                                   REMOTE_EVENT,
                                   false,
                                   creatorId);

    assertNull(notify(event.getId(), testuser2Identity, null, 0, false));
  }

  /**
   * An event gone when the notification is built tells nobody, and nothing
   * fails.
   */
  @Test
  public void testNobodyIsToldOnAnEventGone() {
    long goneEventId = Long.MAX_VALUE;
    assertNull(agendaEventService.getEventById(goneEventId));

    assertNull(notify(goneEventId, testuser2Identity, null, 0, false));
  }

  /**
   * The first date promoted on an all-day series is stored as its day, the
   * one the occurrence list gives, even west of UTC, where the date's id read
   * as an instant in the series' zone would be the day before.
   */
  @Test
  public void testAnAllDayDateIsStoredAsItsDayWestOfUtc() throws Exception {
    ZoneId newYork = ZoneId.of("America/New_York");
    ZonedDateTime start = ZonedDateTime.now(newYork).plusDays(2).withNano(0);
    Event allDay = newEventInstance(start, start, true);
    allDay.setTimeZoneId(newYork);
    Event series = createEvent(allDay, Long.parseLong(testuser1Identity.getId()), testuser2Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                             start.minusDays(1),
                                                                             start.plusDays(3),
                                                                             newYork,
                                                                             10);
    Event firstPromoted = occurrences.get(1);

    NotificationInfo notification = notify(series.getId(), testuser2Identity, firstPromoted.getOccurrence().getId(), 1, false);

    assertNotNull(notification);
    assertEquals("true", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_ALL_DAY));
    assertEquals(firstPromoted.getStart().toLocalDate().toString(),
                 notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_START_DATE));
    assertEquals("1", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_OTHER_DATES));
  }

  private Event singleEvent(Identity attendee) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    return createEvent(event, Long.parseLong(testuser1Identity.getId()), attendee);
  }

  private NotificationInfo notify(long eventId, Identity identity, ZonedDateTime firstDate, int otherDates, boolean openEnded) {
    EventAttendee promoted = new EventAttendee(0, eventId, Long.parseLong(identity.getId()), EventAttendeeResponse.ACCEPTED);
    promoted.setFromOccurrenceId(firstDate);
    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_PROMOTION,
                                                             new EventAttendeePromotion(promoted, otherDates, openEnded));
    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName("agenda.notification.plugin.key");
    value.setValue(NotificationUtils.AGENDA_PARTICIPATION_CONFIRMED_NOTIFICATION_PLUGIN);
    initParams.addParam(value);
    return new EventParticipationConfirmedNotificationPlugin(initParams,
                                                             identityManager,
                                                             agendaEventService,
                                                             agendaCalendarService).makeNotification(ctx);
  }
}
