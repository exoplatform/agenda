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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.notification.plugin.EventCapacityReachedNotificationPlugin;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;

public class EventCapacityReachedNotificationPluginTest extends BaseAgendaEventTest {

  private static final String SPACE_MANAGER = "root";

  /**
   * An event of a space is told to its creator and to the managers of the
   * space, and to nobody else; an event that is not recurring opens on
   * itself and names no date.
   */
  @Test
  public void testTheCreatorAndTheSpaceManagersAreTold() throws Exception {
    Event event = spaceEvent(Long.parseLong(testuser1Identity.getId()), false);

    NotificationInfo notification = notify(event.getId(), null);

    assertNotNull(notification);
    assertEquals(List.of(testuser1Identity.getRemoteId(), SPACE_MANAGER), notification.getSendToUserIds());
    assertEquals(event.getSummary(), notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_TITLE));
    assertEquals(String.valueOf(spaceCalendar.getOwnerId()),
                 notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OWNER_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL)
                           .endsWith("?eventId=" + event.getId()));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?eventId=" + event.getId()));
    assertNull(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
  }

  /**
   * A creator who manages the space is told once.
   */
  @Test
  public void testACreatorWhoManagesTheSpaceIsToldOnce() throws Exception {
    Identity manager = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, SPACE_MANAGER);
    Event event = spaceEvent(Long.parseLong(manager.getId()), false);

    NotificationInfo notification = notify(event.getId(), null);

    assertNotNull(notification);
    assertEquals(List.of(SPACE_MANAGER), notification.getSendToUserIds());
  }

  /**
   * A creator whose account is deleted is not told; the managers still are.
   */
  @Test
  public void testADeletedCreatorIsNotTold() throws Exception {
    Event event = spaceEvent(Long.parseLong(testuser3Identity.getId()), false);
    testuser3Identity.setDeleted(true);
    identityManager.updateIdentity(testuser3Identity);
    try {
      NotificationInfo notification = notify(event.getId(), null);

      assertNotNull(notification);
      assertEquals(List.of(SPACE_MANAGER), notification.getSendToUserIds());
    } finally {
      testuser3Identity.setDeleted(false);
      identityManager.updateIdentity(testuser3Identity);
    }
  }

  /**
   * An event of a personal calendar has no space to tell: its creator alone
   * is told. No report reaches one today, since only an event of a space
   * calendar takes a limit; the guard keeps the creator told should that
   * change.
   */
  @Test
  public void testAnEventOfAPersonalCalendarIsToldToItsCreatorAlone() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    NotificationInfo notification = notify(event.getId(), null);

    assertNotNull(notification);
    assertEquals(List.of(testuser1Identity.getRemoteId()), notification.getSendToUserIds());
  }

  /**
   * On a series, the links open the date that filled up, which is stored.
   */
  @Test
  public void testOnASeriesTheLinksOpenTheDateThatFilledUp() throws Exception {
    Event series = spaceEvent(Long.parseLong(testuser1Identity.getId()), true);
    ZonedDateTime filledDate = occurrenceIds(series).get(2);

    NotificationInfo notification = notify(series.getId(), filledDate);

    assertNotNull(notification);
    String occurrenceId = AgendaDateUtils.toRFC3339Date(filledDate, ZoneOffset.UTC);
    assertEquals(occurrenceId, notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OCCURRENCE_ID));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
    assertTrue(notification.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL)
                           .endsWith("?parentId=" + series.getId() + "&occurrenceId=" + occurrenceId));
  }

  /**
   * An event no longer confirmed when the notification is built has no
   * seats to tell about.
   */
  @Test
  public void testNobodyIsToldOnAnEventNoLongerConfirmed() throws Exception {
    long creatorId = Long.parseLong(testuser1Identity.getId());
    Event event = spaceEvent(creatorId, false);
    event.setStatus(EventStatus.CANCELLED);
    agendaEventService.updateEvent(event,
                                   agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees(),
                                   null,
                                   null,
                                   null,
                                   REMOTE_EVENT,
                                   false,
                                   creatorId);

    assertNull(notify(event.getId(), null));
  }

  /**
   * An event gone when the notification is built tells nobody, and nothing
   * fails.
   */
  @Test
  public void testNobodyIsToldOnAnEventGone() {
    long goneEventId = Long.MAX_VALUE;
    assertNull(agendaEventService.getEventById(goneEventId));

    assertNull(notify(goneEventId, null));
  }

  private Event spaceEvent(long creatorId, boolean recurring) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    if (!recurring) {
      event.setRecurrence(null);
    }
    return createEvent(event, creatorId, testuser2Identity, testuser3Identity);
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

  private NotificationInfo notify(long eventId, ZonedDateTime filledDate) {
    NotificationContext ctx = NotificationContextImpl.cloneInstance().append(NotificationUtils.CAPACITY_REACHED_EVENT_ID, eventId);
    if (filledDate != null) {
      ctx.append(NotificationUtils.EVENT_OCCURRENCE_ID, filledDate);
    }
    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName("agenda.notification.plugin.key");
    value.setValue(NotificationUtils.AGENDA_CAPACITY_REACHED_NOTIFICATION_PLUGIN);
    initParams.addParam(value);
    return new EventCapacityReachedNotificationPlugin(initParams,
                                                      identityManager,
                                                      spaceService,
                                                      agendaEventService,
                                                      agendaCalendarService).makeNotification(ctx);
  }
}
