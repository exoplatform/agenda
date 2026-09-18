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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarPermission;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.search.AgendaSearchConnector;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventReminderStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.commons.utils.ListAccess;
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
import org.exoplatform.web.security.codec.CodecInitializer;

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

  private AgendaCalendarSubscriptionService subscriptionService;

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
    final Identity spaceIdentity = new Identity(SpaceIdentityProvider.NAME, "team");
    spaceIdentity.setId(String.valueOf(SPACE));
    when(identityManager.getIdentity(String.valueOf(SPACE))).thenReturn(spaceIdentity);
    final Space space = new Space();
    space.setPrettyName("team");
    when(spaceService.getSpaceByPrettyName("team")).thenReturn(space);
    for (String member : List.of("manager", "member")) {
      when(spaceService.canViewSpace(space, member)).thenReturn(true);
      when(spaceService.isMember(space, member)).thenReturn(true);
      when(spaceService.canRedactOnSpace(space, member)).thenReturn(true);
    }
    when(spaceService.isManager(space, "manager")).thenReturn(true);
    // the member belongs to this one space: what an attendee-keyed listing
    // expands to, and what the subscribed calendars are then looked up for
    ListAccess<Space> memberSpaces = new ListAccess<>() {
      @Override
      public Space[] load(int offset, int limit) {
        return new Space[] {space};
      }

      @Override
      public int getSize() {
        return 1;
      }
    };
    when(spaceService.getMemberSpaces(anyString())).thenReturn(memberSpaces);
    when(identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, "team")).thenReturn(spaceIdentity);
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
    subscriptionService = mock(AgendaCalendarSubscriptionService.class);
    eventService.setCalendarSubscriptionAccess(new CalendarSubscriptionAccess(subscriptionService));
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
    EventFilter filter = spaceFilter();

    eventService.getEvents(filter, ZoneId.of("UTC"), MEMBER);
    ArgumentCaptor<EventFilter> asked = ArgumentCaptor.forClass(EventFilter.class);
    verify(eventStorage).getEventIds(asked.capture());
    assertEquals(List.of(SPACE), asked.getValue().getOwnerIds(), "the space's agenda asks by owner");
    assertEquals(0, asked.getValue().getAttendeeId(), "and never by attendee");
    Event read = eventService.getEventById(500, ZoneId.of("UTC"), MEMBER);
    assertFalse(read.getAcl().isCanEdit(), "read-only for a member");

    assertThrows(IllegalAccessException.class, () -> eventService.getEvents(filter, ZoneId.of("UTC"), OUTSIDER));
    assertThrows(IllegalAccessException.class, () -> eventService.getEventById(500, ZoneId.of("UTC"), OUTSIDER));
    assertFalse(eventService.canAccessEvent(event(), OUTSIDER));
  }

  /**
   * A member's own agenda reaches the space's subscribed events the way a
   * personal subscription's owner reaches theirs — by the calendar's owner,
   * never by an invitation nobody sent (EXO-90373). The personal agenda's
   * default view is attendee-scoped, and a subscribed calendar's events carry
   * no attendee row, so the calendars of the owners the reader asked for are
   * added to the listing: ticking the space brings its subscribed calendar in,
   * unticking it takes the owner — and those calendars — out.
   *
   * @throws Exception never
   */
  @Test
  void aMembersOwnAgendaReachesTheSpacesSubscribedEventsByOwnerNotByInvitation() throws Exception {
    when(subscriptionService.getSubscriptionCalendarIds(List.of(MEMBER, SPACE))).thenReturn(List.of(CALENDAR));
    EventFilter filter = spaceFilter();
    filter.setOwnerIds(List.of(MEMBER, SPACE));
    filter.setAttendeeId(MEMBER);

    eventService.getEvents(filter, ZoneId.of("UTC"), MEMBER);

    ArgumentCaptor<EventFilter> asked = ArgumentCaptor.forClass(EventFilter.class);
    verify(eventStorage).getEventIds(asked.capture());
    assertEquals(List.of(CALENDAR), asked.getValue().getCalendarIds(), "the space's subscribed calendar is read too");
    assertEquals(MEMBER, asked.getValue().getAttendeeId(), "beside the meetings the member attends");

    when(subscriptionService.getSubscriptionCalendarIds(List.of(MEMBER))).thenReturn(List.of());
    EventFilter withoutTheSpace = spaceFilter();
    withoutTheSpace.setOwnerIds(List.of(MEMBER));
    withoutTheSpace.setAttendeeId(MEMBER);

    eventService.getEvents(withoutTheSpace, ZoneId.of("UTC"), MEMBER);

    verify(eventStorage, times(2)).getEventIds(asked.capture());
    assertEquals(List.of(), asked.getValue().getCalendarIds(), "unticking the space takes its subscribed calendar out");
  }

  /**
   * An outsider naming the space themselves is refused before the subscribed
   * calendars are even looked up (EXO-90373).
   * <p>
   * This is the one request shape for which the lookup would be asked with an
   * owner the caller chose: attendee-scoped, so the new route fires, and
   * {@code ownerIds} carrying a space the caller has no business reading. The
   * check that saves it is the owner loop three lines above the lookup, and it
   * is unconditional — so this pin exists to fail the day someone hoists the
   * lookup above that loop, which is the refactor that would turn a display
   * scope into an access one.
   */
  @Test
  void anOutsiderNamingTheSpaceIsRefusedBeforeItsSubscribedCalendarsAreLookedUp() {
    EventFilter filter = spaceFilter();
    filter.setOwnerIds(List.of(SPACE));
    filter.setAttendeeId(OUTSIDER);

    assertThrows(IllegalAccessException.class, () -> eventService.getEvents(filter, ZoneId.of("UTC"), OUTSIDER));

    verify(subscriptionService, never()).getSubscriptionCalendarIds(any());
  }

  /**
   * A caller that asks to be spared the subscribed calendars gets neither the
   * calendars nor the query that reads them (EXO-90373).
   * <p>
   * Two callers do: the availability and conflict readers, for which an
   * imported event is never a busy block yet would spend their 500-event
   * budget before the FREE test drops it; and the timeline widget under its
   * "accepted events" filter, which names itself after a response nobody ever
   * gave on a feed's events. Both pin their own end of it; this pins the end
   * that honours them.
   *
   * @throws Exception never
   */
  @Test
  void aCallerMayAskForTheListingWithoutTheOwnersSubscribedCalendars() throws Exception {
    when(subscriptionService.getSubscriptionCalendarIds(List.of(MEMBER, SPACE))).thenReturn(List.of(CALENDAR));
    EventFilter filter = spaceFilter();
    filter.setOwnerIds(List.of(MEMBER, SPACE));
    filter.setAttendeeId(MEMBER);
    filter.setSubscribedCalendarsExcluded(true);

    eventService.getEvents(filter, ZoneId.of("UTC"), MEMBER);

    ArgumentCaptor<EventFilter> asked = ArgumentCaptor.forClass(EventFilter.class);
    verify(eventStorage).getEventIds(asked.capture());
    assertEquals(List.of(), asked.getValue().getCalendarIds(), "the subscribed calendars are left out");
    assertEquals(MEMBER, asked.getValue().getAttendeeId(), "the meetings the member attends are still read");
    verify(subscriptionService, never()).getSubscriptionCalendarIds(any());
  }

  /**
   * Nobody answers such an event and nobody sets a reminder on it, member or
   * manager: no invitation was ever sent, and the read-only contract holds for
   * the answer and the reminder as it holds for the edit (EXO-90373).
   *
   * @throws Exception never
   */
  @Test
  void nobodyAnswersOrRemindsOnTheSpacesSubscribedEvents() throws Exception {
    // a real, empty attendee list, so that the refusal a mutant falls back on is
    // the attendee check's and not a mock's NullPointerException
    AgendaEventAttendeeStorage attendeeStorage = mock(AgendaEventAttendeeStorage.class);
    when(attendeeStorage.getEventAttendees(anyLong())).thenReturn(EventAttendeeList.EMPTY_ATTENDEE_LIST);
    AgendaEventAttendeeServiceImpl attendees = new AgendaEventAttendeeServiceImpl(attendeeStorage,
                                                                                  eventStorage,
                                                                                  mock(ListenerService.class),
                                                                                  identityManager,
                                                                                  spaceService,
                                                                                  mock(CodecInitializer.class));
    attendees.setAgendaCalendarService(calendarService);
    InitParams reminderParams = mock(InitParams.class);
    AgendaEventReminderServiceImpl reminders = new AgendaEventReminderServiceImpl(mock(AgendaEventReminderStorage.class),
                                                                                  eventStorage,
                                                                                  mock(AgendaEventAttendeeStorage.class),
                                                                                  mock(AgendaUserSettingsService.class),
                                                                                  identityManager,
                                                                                  spaceService,
                                                                                  mock(ListenerService.class),
                                                                                  reminderParams);
    reminders.setAgendaCalendarService(calendarService);

    for (long user : new long[] {MEMBER, MANAGER}) {
      assertTrue(assertThrows(IllegalAccessException.class,
                              () -> attendees.sendEventResponse(500, user, EventAttendeeResponse.ACCEPTED),
                              "no answer to an event nobody was invited to").getMessage().contains("calendar subscription"),
                 "and refused as an event of a subscription, before any attendee question");
      assertTrue(assertThrows(IllegalAccessException.class,
                              () -> reminders.saveEventReminders(event(), List.of(new EventReminder()), user),
                              "and no reminder either").getMessage().contains("calendar subscription"),
                 "for the same reason");
    }

    // The occurrence path of the same writer. It is unreachable today - the
    // importer writes every occurrence of a feed as its own singleton and
    // never sets a recurrence, so the recurrence test above it refuses first -
    // and it is guarded anyway: the read-only contract must not rest on that
    // property of the importer. Reached here with a recurrent event to prove
    // the guard runs, not to claim a caller reaches it.
    Event recurrent = event();
    recurrent.setRecurrence(new EventRecurrence());
    when(eventStorage.getEventById(600)).thenReturn(recurrent);
    recurrent.setId(600);

    assertTrue(assertThrows(IllegalAccessException.class,
                            () -> reminders.saveUpcomingEventReminders(600,
                                                                       ZonedDateTime.of(2026, 9, 20, 9, 0, 0, 0, ZoneOffset.UTC),
                                                                       List.of(new EventReminder()),
                                                                       MEMBER),
                            "nor on the remaining occurrences of one").getMessage().contains("calendar subscription"),
               "refused as an event of a subscription there too");
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
    assertThrows(IllegalStateException.class, () -> service.deleteCalendarById(CALENDAR, "manager"));
    assertThrows(IllegalAccessException.class, () -> service.getCalendarById(CALENDAR, "outsider"));

    subscribed = false;
    assertTrue(service.getCalendarById(CALENDAR, "manager").getAcl().isCanPublish(), "control: the space's own calendar");
    assertEquals(SPACE, service.getCalendarById(CALENDAR, "member").getOwnerId());
  }

  /**
   * A listing of the space's events over the displayed week.
   *
   * @return the filter
   */
  private static EventFilter spaceFilter() {
    EventFilter filter = new EventFilter();
    filter.setOwnerIds(List.of(SPACE));
    filter.setStart(ZonedDateTime.of(2026, 9, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    filter.setEnd(ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    return filter;
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
