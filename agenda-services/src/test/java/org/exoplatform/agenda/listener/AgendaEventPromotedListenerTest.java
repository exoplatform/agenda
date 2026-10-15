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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeePromotion;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.WebNotificationFilter;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.services.listener.Asynchronous;

public class AgendaEventPromotedListenerTest extends BaseAgendaEventTest {

  /**
   * A promotion is told to the event's creator with the "Answer to an
   * invitation" notification, worded as a Yes: the promoted attendee will
   * participate.
   */
  @Test
  public void testPromotionTellsTheCreatorTheAttendeeWillParticipate() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int initialNotificationsSize = webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId());

    promote(event.getId(), Long.parseLong(testuser4Identity.getId()), null);

    assertEquals(initialNotificationsSize + 1l, webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId()));
    NotificationInfo notification = replyNotificationOf(webNotificationService, event.getId());
    assertEquals(EventAttendeeResponse.ACCEPTED.name(),
                 notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_RESPONSE));
    assertEquals(testuser4Identity.getProfile().getFullName(),
                 notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_PARTICIPANT_NAME));
  }

  /**
   * A promotion is told to the promoted attendee with the "Participation
   * confirmation" notification, which opens the event; an event that is not
   * recurring names no date.
   */
  @Test
  public void testPromotionConfirmsTheParticipationOfTheAttendee() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int initialNotificationsSize = webNotificationService.getNumberOnBadge(testuser4Identity.getRemoteId());

    promote(event.getId(), Long.parseLong(testuser4Identity.getId()), null, 0, false);

    assertEquals(initialNotificationsSize + 1l, webNotificationService.getNumberOnBadge(testuser4Identity.getRemoteId()));
    NotificationInfo notification = confirmationOf(webNotificationService, event.getId());
    assertEquals(event.getSummary(), notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_TITLE));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?eventId=" + event.getId()));
    assertNull(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertEquals("false", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_IS_GUEST));
  }

  /**
   * On a series, the confirmation opens the first date the attendee got a
   * seat for, gives its own start, and counts the other dates promoted.
   */
  @Test
  public void testConfirmationOnASeriesNamesTheFirstDatePromoted() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0).plusDays(1);
    Event series = createEvent(newEventInstance(start, start.plusHours(1), false),
                               Long.parseLong(testuser1Identity.getId()),
                               testuser4Identity,
                               testuser5Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                             start.minusDays(1),
                                                                             start.plusDays(3),
                                                                             series.getTimeZoneId(),
                                                                             10);
    Event firstPromoted = occurrences.get(1);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);

    promote(series.getId(), Long.parseLong(testuser4Identity.getId()), firstPromoted.getOccurrence().getId(), 3, false);

    NotificationInfo notification = confirmationOf(webNotificationService, series.getId());
    String occurrenceId = AgendaDateUtils.toRFC3339Date(firstPromoted.getOccurrence().getId(), ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
    assertEquals("the start of the date promoted, not of the series",
                 firstPromoted.getStart().toInstant(),
                 ZonedDateTime.parse(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_START_DATE))
                              .toInstant());
    assertEquals("3", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_OTHER_DATES));
    assertEquals("false", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_OPEN_ENDED));
  }

  /**
   * On a date the series stores, the confirmation opens that date, at the
   * start the date's own row gives it.
   */
  @Test
  public void testConfirmationOnAStoredDateOpensThatDate() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0).plusDays(1);
    Event series = createEvent(newEventInstance(start, start.plusHours(1), false),
                               Long.parseLong(testuser1Identity.getId()),
                               testuser4Identity,
                               testuser5Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                             start.minusDays(1),
                                                                             start.plusDays(3),
                                                                             series.getTimeZoneId(),
                                                                             10);
    ZonedDateTime promotedDate = occurrences.get(1).getOccurrence().getId();
    Event date = agendaEventService.saveEventExceptionalOccurrence(series.getId(), promotedDate);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);

    promote(date.getId(), Long.parseLong(testuser4Identity.getId()), occurrences.get(0).getOccurrence().getId(), 1, true);

    NotificationInfo notification = confirmationOf(webNotificationService, date.getId());
    String occurrenceId = AgendaDateUtils.toRFC3339Date(promotedDate, ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
    assertEquals(date.getStart().toInstant(),
                 ZonedDateTime.parse(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_START_DATE))
                              .toInstant());
    assertEquals("true", notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_OPEN_ENDED));
  }

  /**
   * On a series, the notification opens the first date the attendee got a seat
   * for, which the promoted row carries as its start.
   */
  @Test
  public void testPromotionOnASeriesOpensTheFirstDatePromoted() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event series = createEvent(newEventInstance(start, start.plusHours(1), false),
                               Long.parseLong(testuser1Identity.getId()),
                               testuser4Identity,
                               testuser5Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                             start.minusDays(1),
                                                                             start.plusDays(3),
                                                                             series.getTimeZoneId(),
                                                                             10);
    ZonedDateTime firstPromotedDate = occurrences.get(1).getOccurrence().getId();
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);

    promote(series.getId(), Long.parseLong(testuser4Identity.getId()), firstPromotedDate);

    NotificationInfo notification = replyNotificationOf(webNotificationService, series.getId());
    String occurrenceId = AgendaDateUtils.toRFC3339Date(firstPromotedDate, ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
  }

  /**
   * On a date the series stores, the notification opens that date, even when
   * the promoted row was copied onto it from a series row starting earlier.
   */
  @Test
  public void testPromotionOnAStoredDateOpensThatDate() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event series = createEvent(newEventInstance(start, start.plusHours(1), false),
                               Long.parseLong(testuser1Identity.getId()),
                               testuser4Identity,
                               testuser5Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                             start.minusDays(1),
                                                                             start.plusDays(3),
                                                                             series.getTimeZoneId(),
                                                                             10);
    ZonedDateTime rangeStart = occurrences.get(0).getOccurrence().getId();
    ZonedDateTime promotedDate = occurrences.get(1).getOccurrence().getId();
    Event date = agendaEventService.saveEventExceptionalOccurrence(series.getId(), promotedDate);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);

    promote(date.getId(), Long.parseLong(testuser4Identity.getId()), rangeStart);

    NotificationInfo notification = replyNotificationOf(webNotificationService, date.getId());
    String occurrenceId = AgendaDateUtils.toRFC3339Date(promotedDate, ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
  }

  /**
   * An event that is not confirmed has nobody to promote to: nobody is told.
   */
  @Test
  public void testPromotionOnAnEventNotConfirmedTellsNobody() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, creatorId, testuser4Identity, testuser5Identity);
    event.setStatus(EventStatus.CANCELLED);
    agendaEventService.updateEvent(event,
                                   agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees(),
                                   null,
                                   null,
                                   null,
                                   REMOTE_EVENT,
                                   false,
                                   creatorId);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int initialNotificationsSize = webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId());
    int initialAttendeeNotificationsSize = webNotificationService.getNumberOnBadge(testuser4Identity.getRemoteId());

    promote(event.getId(), Long.parseLong(testuser4Identity.getId()), null);

    assertEquals(initialNotificationsSize, webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId()));
    assertEquals(initialAttendeeNotificationsSize, webNotificationService.getNumberOnBadge(testuser4Identity.getRemoteId()));
  }

  /**
   * The listener runs after the promotion's commit, by which time the event may
   * be gone: nobody is told, and nothing fails.
   */
  @Test
  public void testPromotionOnAnEventGoneTellsNobody() throws Exception {
    long goneEventId = Long.MAX_VALUE;
    assertNull(agendaEventService.getEventById(goneEventId));
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int initialNotificationsSize = webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId());

    promote(goneEventId, Long.parseLong(testuser4Identity.getId()), null);

    assertEquals(initialNotificationsSize, webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId()));
  }

  /**
   * The listener runs out of the thread that decided the seat: kills the
   * mutant that removes {@link Asynchronous}, which a test calling
   * {@link AgendaEventPromotedListener#onEvent} by hand cannot see.
   */
  @Test
  public void testListenerIsAsynchronous() {
    assertTrue(AgendaEventPromotedListener.class.isAnnotationPresent(Asynchronous.class));
  }

  private void promote(long eventId, long identityId, ZonedDateTime firstPromotedDate) throws Exception {
    promote(eventId, identityId, firstPromotedDate, 0, false);
  }

  private void promote(long eventId,
                       long identityId,
                       ZonedDateTime firstPromotedDate,
                       int otherDates,
                       boolean openEnded) throws Exception {
    EventAttendee waiting = new EventAttendee(0, eventId, identityId, EventAttendeeResponse.WAITING);
    EventAttendee promoted = new EventAttendee(0, eventId, identityId, EventAttendeeResponse.ACCEPTED);
    promoted.setFromOccurrenceId(firstPromotedDate);
    new AgendaEventPromotedListener(container).onEvent(new org.exoplatform.services.listener.Event<>(Utils.POST_EVENT_ATTENDEE_PROMOTED,
                                                                                                      waiting,
                                                                                                      new EventAttendeePromotion(promoted,
                                                                                                                                 otherDates,
                                                                                                                                 openEnded)));
  }

  private NotificationInfo confirmationOf(WebNotificationService webNotificationService, long eventId) {
    NotificationInfo notification = webNotificationService.getNotificationInfos(new WebNotificationFilter(testuser4Identity.getRemoteId()),
                                                                                0,
                                                                                50)
                                                          .stream()
                                                          .filter(info -> NotificationUtils.AGENDA_PARTICIPATION_CONFIRMED_NOTIFICATION_PLUGIN.equals(info.getKey()
                                                                                                                                                       .getId()))
                                                          .filter(info -> String.valueOf(eventId)
                                                                                .equals(info.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_ID)))
                                                          .findFirst()
                                                          .orElse(null);
    assertNotNull("the promoted attendee holds a confirmation on event " + eventId, notification);
    return notification;
  }

  private NotificationInfo replyNotificationOf(WebNotificationService webNotificationService, long eventId) {
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
