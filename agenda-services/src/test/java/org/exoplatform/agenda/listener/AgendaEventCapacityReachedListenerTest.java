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

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.api.notification.model.WebNotificationFilter;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.exoplatform.services.listener.Asynchronous;

public class AgendaEventCapacityReachedListenerTest extends BaseAgendaEventTest {

  private static final String SPACE_MANAGER = "root";

  /**
   * The listener runs off the thread that decided the seat: no decision waits
   * for a notification. The pin names the mutant that drops the annotation,
   * which no call of the listener by hand can tell apart.
   */
  @Test
  public void testTheListenerIsAsynchronous() {
    assertTrue(AgendaEventCapacityReachedListener.class.isAnnotationPresent(Asynchronous.class));
  }

  /**
   * An event of a space that filled up is told to its creator and to the
   * manager of the space, once each, as "Event capacity reached", which
   * opens the event; an event that is not recurring names no date.
   */
  @Test
  public void testTheCreatorAndTheSpaceManagerAreToldTheEventFilledUp() throws Exception {
    Event event = spaceEvent(false);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int creatorBefore = webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId());
    int managerBefore = webNotificationService.getNumberOnBadge(SPACE_MANAGER);

    reached(event.getId(), null);

    assertEquals(creatorBefore + 1l, webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId()));
    assertEquals(managerBefore + 1l, webNotificationService.getNumberOnBadge(SPACE_MANAGER));
    NotificationInfo notification = capacityReachedOf(webNotificationService, testuser1Identity.getRemoteId(), event.getId());
    assertEquals(event.getSummary(), notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_TITLE));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?eventId=" + event.getId()));
    assertNull(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertNotNull(capacityReachedOf(webNotificationService, SPACE_MANAGER, event.getId()));
  }

  /**
   * On a series, the notification opens the date that filled up, the one
   * the pool reports.
   */
  @Test
  public void testOnASeriesTheNotificationOpensTheDateThatFilledUp() throws Exception {
    Event series = spaceEvent(true);
    ZonedDateTime filledDate = occurrenceIds(series).get(1);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);

    reached(series.getId(), filledDate);

    NotificationInfo notification = capacityReachedOf(webNotificationService, testuser1Identity.getRemoteId(), series.getId());
    String occurrenceId = AgendaDateUtils.toRFC3339Date(filledDate, ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
  }

  /**
   * On a date the series stores on a row of its own, reported by that row,
   * the notification names the series and the date: the page opens a date on
   * its series, never on its row.
   */
  @Test
  public void testOnAStoredDateTheNotificationNamesTheSeriesAndTheDate() throws Exception {
    Event series = spaceEvent(true);
    ZonedDateTime filledDate = occurrenceIds(series).get(1);
    Event date = agendaEventService.saveEventExceptionalOccurrence(series.getId(), filledDate);
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);

    reached(date.getId(), filledDate);

    NotificationInfo notification = capacityReachedOf(webNotificationService, testuser1Identity.getRemoteId(), date.getId());
    String occurrenceId = AgendaDateUtils.toRFC3339Date(filledDate, ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
  }

  /**
   * An event no longer confirmed when the notification runs is told to
   * nobody.
   */
  @Test
  public void testNobodyIsToldOnAnEventNoLongerConfirmed() throws Exception {
    Event event = spaceEvent(false);
    event.setStatus(EventStatus.CANCELLED);
    agendaEventService.updateEvent(event,
                                   agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees(),
                                   null,
                                   null,
                                   null,
                                   REMOTE_EVENT,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int creatorBefore = webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId());
    int managerBefore = webNotificationService.getNumberOnBadge(SPACE_MANAGER);

    reached(event.getId(), null);

    assertEquals(creatorBefore, webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId()));
    assertEquals(managerBefore, webNotificationService.getNumberOnBadge(SPACE_MANAGER));
  }

  /**
   * An event gone when the notification runs is told to nobody, and nothing
   * fails.
   */
  @Test
  public void testNobodyIsToldOnAnEventGone() throws Exception {
    long goneEventId = Long.MAX_VALUE;
    assertNull(agendaEventService.getEventById(goneEventId));
    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int creatorBefore = webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId());
    int managerBefore = webNotificationService.getNumberOnBadge(SPACE_MANAGER);

    reached(goneEventId, null);

    assertEquals(creatorBefore, webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId()));
    assertEquals(managerBefore, webNotificationService.getNumberOnBadge(SPACE_MANAGER));
  }

  /**
   * @return an event of the space's calendar, created by the first test user,
   *         a daily series or a single date
   */
  private Event spaceEvent(boolean recurring) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    if (!recurring) {
      event.setRecurrence(null);
    }
    return createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);
  }

  private List<ZonedDateTime> occurrenceIds(Event series) {
    return agendaEventService.getEventOccurrencesInPeriod(series,
                                                          series.getStart().minusDays(1),
                                                          series.getStart().plusDays(3),
                                                          series.getTimeZoneId(),
                                                          10)
                             .stream()
                             .map(occurrence -> occurrence.getOccurrence().getId())
                             .toList();
  }

  private void reached(long eventId, ZonedDateTime filledDate) throws Exception {
    new AgendaEventCapacityReachedListener(container).onEvent(new org.exoplatform.services.listener.Event<>(Utils.POST_EVENT_CAPACITY_REACHED,
                                                                                                             eventId,
                                                                                                             filledDate));
  }

  private NotificationInfo capacityReachedOf(WebNotificationService webNotificationService, String username, long eventId) {
    return webNotificationService.getNotificationInfos(new WebNotificationFilter(username), 0, 50)
                                 .stream()
                                 .filter(info -> NotificationUtils.AGENDA_CAPACITY_REACHED_NOTIFICATION_PLUGIN.equals(info.getKey()
                                                                                                                          .getId()))
                                 .filter(info -> String.valueOf(eventId)
                                                       .equals(info.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_ID)))
                                 .findFirst()
                                 .orElse(null);
  }
}
