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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarPermission;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.search.AgendaSearchConnector;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.storage.CalendarLinkStorage;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.social.metadata.MetadataService;
import org.exoplatform.web.security.codec.CodecInitializer;

/**
 * Pins that a subscribed calendar is read-only everywhere a user could act on it
 * (EXO-90278), and pins each refusal against the same calendar unflagged: its
 * events are neither updated, moved into, deleted nor created — not by their
 * creator, not by an attendee allowed to edit — the calendar is neither edited
 * nor deleted through the calendar API, a client cannot unflag it, and it cannot
 * be published.
 */
class CalendarSubscriptionAclTest {

  private static final long      JOHN     = 1;

  private static final long      CALENDAR = 77;

  private IdentityManager        identityManager;

  private AgendaCalendarService  calendarService;

  private AgendaEventAttendeeService attendeeService;

  private AgendaEventStorage     eventStorage;

  private AgendaEventServiceImpl eventService;

  private AgendaCalendarStorage  calendarStorage;

  private boolean                subscribed;

  /**
   * Wires the event and calendar services over John's calendar, flagged or not
   * as the test says.
   */
  @BeforeEach
  void setUp() {
    identityManager = mock(IdentityManager.class);
    Identity john = new Identity(OrganizationIdentityProvider.NAME, "john");
    john.setId(String.valueOf(JOHN));
    when(identityManager.getIdentity(String.valueOf(JOHN))).thenReturn(john);
    when(identityManager.getOrCreateUserIdentity("john")).thenReturn(john);
    calendarService = mock(AgendaCalendarService.class);
    when(calendarService.getCalendarById(CALENDAR)).thenAnswer(invocation -> calendar());
    attendeeService = mock(AgendaEventAttendeeService.class);
    eventStorage = mock(AgendaEventStorage.class);
    when(eventStorage.getEventById(anyLong())).thenAnswer(invocation -> event());
    eventService = new AgendaEventServiceImpl(calendarService,
                                              attendeeService,
                                              mock(AgendaEventConferenceService.class),
                                              mock(AgendaEventReminderService.class),
                                              mock(AgendaRemoteEventService.class),
                                              mock(AgendaEventDatePollService.class),
                                              mock(AgendaSearchConnector.class),
                                              eventStorage,
                                              identityManager,
                                              mock(SpaceService.class),
                                              mock(ListenerService.class),
                                              mock(MetadataService.class));
    calendarStorage = mock(AgendaCalendarStorage.class);
    when(calendarStorage.getCalendarById(CALENDAR)).thenAnswer(invocation -> calendar());
  }

  /**
   * John's calendar, flagged as the test says.
   *
   * @return the calendar
   */
  private Calendar calendar() {
    Calendar calendar = new Calendar();
    calendar.setId(CALENDAR);
    calendar.setOwnerId(JOHN);
    calendar.setName("Holidays");
    calendar.setSubscription(subscribed);
    return calendar;
  }

  /**
   * An event John created in the calendar, which attendees may edit.
   *
   * @return the event
   */
  private static Event event() {
    Event event = new Event();
    event.setId(500);
    event.setCalendarId(CALENDAR);
    event.setCreatorId(JOHN);
    event.setAllowAttendeeToUpdate(true);
    event.setStart(ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC));
    event.setEnd(ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC));
    return event;
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
    return new AgendaCalendarServiceImpl(calendarStorage, identityManager, mock(SpaceService.class), initParams);
  }

  /**
   * Neither the creator nor an attendee allowed to edit updates an event of a
   * subscribed calendar, and nobody creates one there; the same event of an
   * ordinary calendar is theirs to update.
   */
  @Test
  void anEventOfASubscribedCalendarIsUpdatedOrCreatedByNobody() {
    when(attendeeService.isEventAttendee(anyLong(), anyLong())).thenReturn(true);

    subscribed = false;
    assertTrue(eventService.canUpdateEvent(event(), JOHN), "control: the creator updates an ordinary event");
    assertTrue(eventService.canUpdateEvent(event(), 2), "control: an attendee allowed to edit does too");
    assertTrue(eventService.canCreateEvent(calendar(), JOHN), "control: the owner creates in an ordinary calendar");

    subscribed = true;
    assertFalse(eventService.canUpdateEvent(event(), JOHN), "not its creator");
    assertFalse(eventService.canUpdateEvent(event(), 2), "not an attendee allowed to edit");
    assertFalse(eventService.canCreateEvent(calendar(), JOHN), "and nobody creates in it");
  }

  /**
   * Deleting or creating an event of a subscribed calendar is refused at the
   * service, and nothing reaches the storage.
   */
  @Test
  void deletingOrCreatingAnEventOfASubscribedCalendarIsRefused() {
    subscribed = true;

    assertThrows(IllegalAccessException.class, () -> eventService.deleteEventById(500, JOHN));
    verify(eventStorage, never()).deleteEventById(anyLong());

    Event event = event();
    event.setId(0);
    assertThrows(IllegalAccessException.class,
                 () -> eventService.createEvent(event, null, null, null, null, null, false, JOHN));
    verify(eventStorage, never()).createEvent(any());
  }

  /**
   * A subscribed calendar grants nothing, is neither edited nor deleted through
   * the calendar API — a client sending it unflagged changes nothing — while its
   * subscription may still rename it internally, keeping the flag.
   *
   * @throws Exception never
   */
  @Test
  void aSubscribedCalendarIsNotEditedOrDeletedThroughTheCalendarApi() throws Exception {
    AgendaCalendarServiceImpl service = calendarServiceImpl();

    subscribed = false;
    CalendarPermission ordinary = service.getCalendarById(CALENDAR, "john").getAcl();
    assertTrue(ordinary.isCanEdit() && ordinary.isCanCreate() && ordinary.isCanPublish(), "control: an ordinary calendar");

    subscribed = true;
    CalendarPermission acl = service.getCalendarById(CALENDAR, "john").getAcl();
    assertFalse(acl.isCanEdit());
    assertFalse(acl.isCanCreate());
    assertFalse(acl.isCanPublish());
    assertFalse(acl.isCanInviteeEdit());

    Calendar sent = calendar();
    sent.setSubscription(false);
    sent.setName("Renamed");
    assertThrows(IllegalAccessException.class, () -> service.updateCalendar(sent, "john"));
    assertThrows(IllegalAccessException.class, () -> service.deleteCalendarById(CALENDAR, "john"));
    verify(calendarStorage, never()).updateCalendar(any());
    verify(calendarStorage, never()).deleteCalendarById(anyLong());

    Calendar internal = calendar();
    internal.setSubscription(false);
    service.updateCalendar(internal);
    ArgumentCaptor<Calendar> written = ArgumentCaptor.forClass(Calendar.class);
    verify(calendarStorage).updateCalendar(written.capture());
    assertTrue(written.getValue().isSubscription(), "the stored flag wins over the one sent");
  }

  /**
   * A subscribed calendar cannot be published; the same calendar unflagged can.
   *
   * @throws Exception never
   */
  @Test
  void aSubscribedCalendarCannotBePublished() throws Exception {
    CalendarLinkStorage linkStorage = mock(CalendarLinkStorage.class);
    CodecInitializer codecInitializer = mock(CodecInitializer.class, invocation -> {
      throw new IllegalStateException("no codec in this test");
    });
    AgendaCalendarLinkServiceImpl linkService = new AgendaCalendarLinkServiceImpl(linkStorage,
                                                                                  calendarService,
                                                                                  mock(AgendaEventService.class),
                                                                                  identityManager,
                                                                                  mock(SpaceService.class),
                                                                                  codecInitializer);

    subscribed = true;
    assertThrows(IllegalAccessException.class, () -> linkService.saveCalendarLink(CALENDAR, "john"));
    assertThrows(IllegalAccessException.class, () -> linkService.getCalendarLink(CALENDAR, "john"));
    verify(linkStorage, never()).save(anyLong(), anyLong(), anyString(), anyString(), any());

    subscribed = false;
    assertThrows(IllegalStateException.class, () -> linkService.saveCalendarLink(CALENDAR, "john"),
                 "control: an ordinary calendar gets past the permission check, to the codec");
  }

}
