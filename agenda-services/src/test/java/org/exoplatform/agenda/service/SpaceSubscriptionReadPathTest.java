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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarPermission;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.search.AgendaSearchConnector;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;

/**
 * Pins that the events of a calendar a space subscribes to (EXO-90373) are read
 * as the space's calendar is, and written by nobody: no read path gained a
 * predicate of its own for them, so these pins catch a later change to the
 * inheritance. A member lists and reads them; an outsider is refused the
 * space's listing and each event; nobody — the space's manager included —
 * edits, moves, deletes or creates one; and the calendar grants the manager
 * nothing through the calendar API.
 */
class SpaceSubscriptionReadPathTest {

  private static final long      MANAGER  = 1;

  private static final long      MEMBER   = 2;

  private static final long      OUTSIDER = 3;

  private static final long      SPACE    = 100;

  private static final long      CALENDAR = 77;

  private IdentityManager        identityManager;

  private SpaceService           spaceService;

  private AgendaCalendarService  calendarService;

  private AgendaEventStorage     eventStorage;

  private AgendaEventServiceImpl eventService;

  private AgendaCalendarStorage  calendarStorage;

  private boolean                subscribed = true;

  /**
   * Wires the event and calendar services over a space whose subscribed
   * calendar holds one event.
   */
  @BeforeEach
  void setUp() {
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    user(MANAGER, "manager");
    user(MEMBER, "member");
    user(OUTSIDER, "outsider");
    Identity spaceIdentity = new Identity(SpaceIdentityProvider.NAME, "team");
    spaceIdentity.setId(String.valueOf(SPACE));
    when(identityManager.getIdentity(String.valueOf(SPACE))).thenReturn(spaceIdentity);
    Space space = new Space();
    space.setPrettyName("team");
    when(spaceService.getSpaceByPrettyName("team")).thenReturn(space);
    for (String member : List.of("manager", "member")) {
      when(spaceService.canViewSpace(space, member)).thenReturn(true);
      when(spaceService.isMember(space, member)).thenReturn(true);
      when(spaceService.canRedactOnSpace(space, member)).thenReturn(true);
    }
    when(spaceService.isManager(space, "manager")).thenReturn(true);
    when(spaceService.canManageSpace(space, "manager")).thenReturn(true);

    calendarService = mock(AgendaCalendarService.class);
    when(calendarService.getCalendarById(CALENDAR)).thenAnswer(invocation -> calendar());
    eventStorage = mock(AgendaEventStorage.class);
    when(eventStorage.getEventById(anyLong())).thenAnswer(invocation -> event());
    when(eventStorage.getEventIds(any())).thenReturn(new ArrayList<>());
    AgendaEventAttendeeService attendeeService = mock(AgendaEventAttendeeService.class);
    eventService = new AgendaEventServiceImpl(calendarService,
                                              attendeeService,
                                              mock(AgendaEventConferenceService.class),
                                              mock(AgendaEventReminderService.class),
                                              mock(AgendaRemoteEventService.class),
                                              mock(AgendaEventDatePollService.class),
                                              mock(AgendaSearchConnector.class),
                                              eventStorage,
                                              identityManager,
                                              spaceService,
                                              mock(ListenerService.class),
                                              mock(MetadataService.class));
    eventService.setCalendarShareAccess(new CalendarShareAccess(mock(AgendaCalendarShareService.class)));
    calendarStorage = mock(AgendaCalendarStorage.class);
    when(calendarStorage.getCalendarById(CALENDAR)).thenAnswer(invocation -> calendar());
  }

  /**
   * A member lists the space's events, subscribed ones included, and reads
   * each, read-only; an outsider is refused both.
   *
   * @throws Exception never
   */
  @Test
  void aMemberReadsTheSpacesSubscribedEventsAndAnOutsiderNothing() throws Exception {
    EventFilter filter = new EventFilter();
    filter.setOwnerIds(List.of(SPACE));
    filter.setStart(ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    filter.setEnd(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC));

    eventService.getEvents(filter, ZoneId.of("UTC"), MEMBER);
    verify(eventStorage).getEventIds(any());
    Event read = eventService.getEventById(500, ZoneId.of("UTC"), MEMBER);
    assertFalse(read.getAcl().isCanEdit(), "read-only for a member");

    assertThrows(IllegalAccessException.class, () -> eventService.getEvents(filter, ZoneId.of("UTC"), OUTSIDER));
    assertThrows(IllegalAccessException.class, () -> eventService.getEventById(500, ZoneId.of("UTC"), OUTSIDER));
    assertFalse(eventService.canAccessEvent(event(), OUTSIDER));
  }

  /**
   * Nobody updates or creates an event of the space's subscribed calendar, not
   * even the manager who may edit the space's own calendar; the same calendar
   * unflagged is theirs to write.
   */
  @Test
  void nobodyWritesTheSpacesSubscribedEvents() {
    subscribed = false;
    assertTrue(eventService.canUpdateEvent(event(), MANAGER), "control: the manager edits the space's own events");
    assertTrue(eventService.canCreateEvent(calendar(), MEMBER), "control: a redactor creates in the space's own calendar");

    subscribed = true;
    assertFalse(eventService.canUpdateEvent(event(), MANAGER));
    assertFalse(eventService.canUpdateEvent(event(), MEMBER));
    assertFalse(eventService.canCreateEvent(calendar(), MANAGER));
    assertThrows(IllegalAccessException.class, () -> eventService.deleteEventById(500, MANAGER));
    verify(eventStorage, never()).deleteEventById(anyLong());
  }

  /**
   * Through the calendar API the space's subscribed calendar grants its manager
   * nothing — no edit, no event, no publishing — and cannot be deleted there.
   *
   * @throws Exception never
   */
  @Test
  void theSpacesSubscribedCalendarGrantsTheManagerNothing() throws Exception {
    AgendaCalendarServiceImpl service = calendarServiceImpl();

    CalendarPermission acl = service.getCalendarById(CALENDAR, "manager").getAcl();
    assertFalse(acl.isCanEdit());
    assertFalse(acl.isCanCreate());
    assertFalse(acl.isCanPublish());
    assertThrows(IllegalAccessException.class, () -> service.deleteCalendarById(CALENDAR, "manager"));
    assertThrows(IllegalAccessException.class, () -> service.getCalendarById(CALENDAR, "outsider"));

    subscribed = false;
    assertTrue(service.getCalendarById(CALENDAR, "manager").getAcl().isCanPublish(), "control: the space's own calendar");
    assertEquals(SPACE, service.getCalendarById(CALENDAR, "member").getOwnerId());
  }

  /**
   * The space's calendar, flagged as a subscription as the test says.
   *
   * @return the calendar
   */
  private Calendar calendar() {
    Calendar calendar = new Calendar();
    calendar.setId(CALENDAR);
    calendar.setOwnerId(SPACE);
    calendar.setName("Holidays");
    calendar.setSubscription(subscribed);
    return calendar;
  }

  /**
   * An event the feed imported, created by the manager who added it.
   *
   * @return the event
   */
  private static Event event() {
    Event event = new Event();
    event.setId(500);
    event.setCalendarId(CALENDAR);
    event.setCreatorId(MANAGER);
    event.setStart(ZonedDateTime.of(2026, 9, 20, 9, 0, 0, 0, ZoneOffset.UTC));
    event.setEnd(ZonedDateTime.of(2026, 9, 20, 10, 0, 0, 0, ZoneOffset.UTC));
    return event;
  }

  /**
   * Registers a user.
   *
   * @param id identity identifier
   * @param username the user
   */
  private void user(long id, String username) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    when(identityManager.getIdentity(String.valueOf(id))).thenReturn(identity);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(identity);
  }

  /**
   * The calendar service over the storage.
   *
   * @return the service
   */
  private AgendaCalendarServiceImpl calendarServiceImpl() {
    InitParams initParams = mock(InitParams.class);
    ValuesParam colors = new ValuesParam();
    colors.setValues(new ArrayList<>(List.of("#1f77b4")));
    when(initParams.getValuesParam("defaultColors")).thenReturn(colors);
    AgendaCalendarServiceImpl service = new AgendaCalendarServiceImpl(calendarStorage, identityManager, spaceService, initParams);
    service.setCalendarShareAccess(new CalendarShareAccess(mock(AgendaCalendarShareService.class)));
    return service;
  }

}
