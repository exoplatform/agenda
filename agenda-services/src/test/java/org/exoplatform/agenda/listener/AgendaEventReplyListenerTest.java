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
package org.exoplatform.agenda.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.WebNotificationFilter;
import org.exoplatform.commons.api.notification.service.WebNotificationService;

public class AgendaEventReplyListenerTest extends BaseAgendaEventTest {

  /**
   * An answer given on a date a series stores is told at that date, even when
   * the attendee's row on it was copied from a series row that starts on an
   * earlier date, as when the daily job stores the date after an answer given
   * from an earlier one on: the link opens the date answered.
   */
  @Test
  public void testReplyOnAStoredDateLinksToTheDateAnswered() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long attendeeId = Long.parseLong(testuser2Identity.getId());
    Event series = createEvent(newEventInstance(start, start.plusHours(1), false), creatorId, testuser2Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                             start.minusDays(1),
                                                                             start.plusDays(3),
                                                                             series.getTimeZoneId(),
                                                                             10);
    ZonedDateTime firstDate = occurrences.get(0).getOccurrence().getId();
    ZonedDateTime answeredDate = occurrences.get(1).getOccurrence().getId();
    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(), firstDate, attendeeId, EventAttendeeResponse.ACCEPTED);
    agendaEventService.saveEventExceptionalOccurrence(series.getId(), answeredDate);

    agendaEventAttendeeService.sendEventResponse(series.getId(), answeredDate, attendeeId, EventAttendeeResponse.DECLINED, false);
    Event date = agendaEventService.getExceptionalOccurrenceEvent(series.getId(), answeredDate);
    EventAttendee declined = rowOf(date.getId(), attendeeId);
    assertNotNull("the stored date's row keeps the range it was copied with", declined.getFromOccurrenceId());
    assertNotEquals(answeredDate.toInstant(), declined.getFromOccurrenceId().toInstant());
    replied(declined, EventAttendeeResponse.ACCEPTED);

    NotificationInfo notification = replyNotificationOf(date.getId());
    assertEquals(EventAttendeeResponse.DECLINED.name(),
                 notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_RESPONSE));
    String occurrenceId = AgendaDateUtils.toRFC3339Date(answeredDate, ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
    Event opened = agendaEventService.getEventOccurrence(series.getId(), answeredDate, ZoneOffset.UTC, creatorId);
    assertEquals(date.getId(), opened.getId());
  }

  /**
   * An answer given on the series from one of its dates on is told at that
   * date, the first one the answer covers.
   */
  @Test
  public void testUpcomingReplyOnTheSeriesLinksToItsFirstDate() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long attendeeId = Long.parseLong(testuser2Identity.getId());
    Event series = createEvent(newEventInstance(start, start.plusHours(1), false), creatorId, testuser2Identity);
    ZonedDateTime fromDate = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                            start.minusDays(1),
                                                                            start.plusDays(3),
                                                                            series.getTimeZoneId(),
                                                                            10)
                                               .get(1)
                                               .getOccurrence()
                                               .getId();

    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(), fromDate, attendeeId, EventAttendeeResponse.ACCEPTED);
    EventAttendee accepted = agendaEventAttendeeService.getEventAttendees(series.getId())
                                                       .getEventAttendees()
                                                       .stream()
                                                       .filter(row -> row.getIdentityId() == attendeeId)
                                                       .filter(row -> row.getResponse() == EventAttendeeResponse.ACCEPTED)
                                                       .findFirst()
                                                       .orElseThrow();
    replied(accepted, EventAttendeeResponse.NEEDS_ACTION);

    NotificationInfo notification = replyNotificationOf(series.getId());
    String occurrenceId = AgendaDateUtils.toRFC3339Date(fromDate, ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
  }

  private void replied(EventAttendee answered, EventAttendeeResponse previousResponse) throws Exception {
    EventAttendee before = new EventAttendee(answered.getId(), answered.getEventId(), answered.getIdentityId(), previousResponse);
    new AgendaEventReplyListener(container).onEvent(new org.exoplatform.services.listener.Event<>(Utils.POST_EVENT_RESPONSE_SENT,
                                                                                                   before,
                                                                                                   answered));
  }

  private EventAttendee rowOf(long eventId, long identityId) {
    return agendaEventAttendeeService.getEventAttendees(eventId)
                                     .getEventAttendees()
                                     .stream()
                                     .filter(row -> row.getIdentityId() == identityId)
                                     .findFirst()
                                     .orElseThrow();
  }

  private NotificationInfo replyNotificationOf(long eventId) {
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    NotificationInfo notification = webNotificationService.getNotificationInfos(new WebNotificationFilter(testuser1Identity.getRemoteId()),
                                                                                0,
                                                                                50)
                                                          .stream()
                                                          .filter(info -> NotificationUtils.AGENDA_REPLY_NOTIFICATION_PLUGIN.equals(info.getKey()
                                                                                                                                      .getId()))
                                                          .filter(info -> String.valueOf(eventId)
                                                                                .equals(info.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_ID)))
                                                          .findFirst()
                                                          .orElse(null);
    assertNotNull("the creator holds a reply notification on event " + eventId, notification);
    return notification;
  }
}
