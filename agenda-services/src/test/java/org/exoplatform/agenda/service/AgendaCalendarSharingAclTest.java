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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.agenda.constant.EventAccess;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.model.AgendaEventSearchFilter;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.model.EventSearchResult;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventRecurrenceType;
import org.exoplatform.agenda.search.AgendaSearchConnector;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventReminderStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.metadata.MetadataService;

/**
 * Pins what a calendar share grants, on the services that decide it
 * (EXO-90357): a sharee reads the calendar and its events — by identifier,
 * as an occurrence, in a listing, in a search — a private event as busy time
 * only; a stranger is refused everywhere, a calendar named in a listing
 * included; and a sharee gets no reminder. Each pin runs the real ACL over
 * mocked identity and share services.
 */
class AgendaCalendarSharingAclTest {

  private static final long        OWNER    = 1;

  private static final long        ALICE    = 2;

  private static final long        CAROL    = 3;

  private static final long        CALENDAR = 77;

  private static final long        EVENT    = 500;

  private static final long        SERIES   = 600;

  private final Map<String, Identity> identities = new HashMap<>();

  private AgendaCalendarShareService shareService;

  private AgendaCalendarStorage      calendarStorage;

  private AgendaEventStorage         eventStorage;

  private AgendaEventAttendeeService attendeeService;

  private AgendaSearchConnector      searchConnector;

  private AgendaCalendarServiceImpl  calendarService;

  private AgendaEventServiceImpl     eventService;

  private EventVisibility            visibility = EventVisibility.PRIVATE;

  /**
   * Wires the calendar and event services over the owner's calendar, shared
   * with Alice and with nobody else.
   */
  @BeforeEach
  void setUp() {
    identities.clear();
    user(OWNER, "owner");
    user(ALICE, "alice");
    user(CAROL, "carol");
    IdentityManager identityManager = mock(IdentityManager.class);
    when(identityManager.getIdentity(anyString())).thenAnswer(invocation -> identities.get(invocation.<String> getArgument(0)));
    when(identityManager.getOrCreateUserIdentity(anyString())).thenAnswer(invocation -> identities.values()
                                                                                                   .stream()
                                                                                                   .filter(identity -> identity.getRemoteId()
                                                                                                                               .equals(invocation.getArgument(0)))
                                                                                                   .findFirst()
                                                                                                   .orElse(null));
    shareService = mock(AgendaCalendarShareService.class);
    when(shareService.isSharedWith(CALENDAR, ALICE)).thenReturn(true);
    when(shareService.getSharedCalendarIds(anyLong())).thenReturn(List.of());
    when(shareService.getSharedCalendarIds(ALICE)).thenReturn(List.of(CALENDAR));

    calendarStorage = mock(AgendaCalendarStorage.class);
    when(calendarStorage.getCalendarById(CALENDAR)).thenAnswer(invocation -> calendar());
    InitParams initParams = mock(InitParams.class);
    ValuesParam colors = new ValuesParam();
    colors.setValues(new ArrayList<>(List.of("#1f77b4")));
    when(initParams.getValuesParam("defaultColors")).thenReturn(colors);
    calendarService = new AgendaCalendarServiceImpl(calendarStorage, identityManager, mock(SpaceService.class), initParams);
    calendarService.setCalendarShareAccess(new CalendarShareAccess(shareService));

    eventStorage = mock(AgendaEventStorage.class);
    when(eventStorage.getEventById(EVENT)).thenAnswer(invocation -> event());
    when(eventStorage.getEventById(SERIES)).thenAnswer(invocation -> series());
    attendeeService = mock(AgendaEventAttendeeService.class);
    searchConnector = mock(AgendaSearchConnector.class);
    SpaceService spaceService = mock(SpaceService.class);
    @SuppressWarnings("unchecked")
    ListAccess<Space> noSpaces = mock(ListAccess.class);
    when(spaceService.getMemberSpaces(anyString())).thenReturn(noSpaces);
    eventService = new AgendaEventServiceImpl(calendarService,
                                              attendeeService,
                                              mock(AgendaEventConferenceService.class),
                                              mock(AgendaEventReminderService.class),
                                              mock(AgendaRemoteEventService.class),
                                              mock(AgendaEventDatePollService.class),
                                              searchConnector,
                                              eventStorage,
                                              identityManager,
                                              spaceService,
                                              mock(ListenerService.class),
                                              mock(MetadataService.class));
    eventService.setCalendarShareAccess(new CalendarShareAccess(shareService));
  }

  /**
   * The sharee reads the calendar with nothing granted on it; the owner may
   * share it; a stranger is refused.
   *
   * @throws Exception when a read is refused
   */
  @Test
  void aShareeReadsTheCalendarAndNothingMore() throws Exception {
    Calendar forAlice = calendarService.getCalendarById(CALENDAR, "alice");

    assertTrue(forAlice.isSharedWithMe());
    assertFalse(forAlice.getAcl().isCanCreate());
    assertFalse(forAlice.getAcl().isCanEdit());
    assertFalse(forAlice.getAcl().isCanPublish());
    assertFalse(forAlice.getAcl().isCanShare());

    Calendar forOwner = calendarService.getCalendarById(CALENDAR, "owner");
    assertFalse(forOwner.isSharedWithMe());
    assertTrue(forOwner.getAcl().isCanShare());
    assertTrue(forOwner.getAcl().isCanEdit());

    assertThrows(IllegalAccessException.class, () -> calendarService.getCalendarById(CALENDAR, "carol"));
  }

  /**
   * Without a share service to ask — the Spring context absent or not up —
   * nobody is admitted by a share: absent means not shared.
   */
  @Test
  void anAbsentShareServiceAdmitsNobody() {
    calendarService.setCalendarShareAccess(new CalendarShareAccess(null));
    eventService.setCalendarShareAccess(new CalendarShareAccess(null));

    assertThrows(IllegalAccessException.class, () -> calendarService.getCalendarById(CALENDAR, "alice"));
    assertEquals(EventAccess.NONE, eventService.getEventAccess(event(), ALICE));
  }

  /**
   * A subscribed calendar is never shareable, even by its owner.
   *
   * @throws Exception when the read is refused
   */
  @Test
  void aSubscribedCalendarIsNotShareable() throws Exception {
    when(calendarStorage.getCalendarById(CALENDAR)).thenAnswer(invocation -> {
      Calendar calendar = calendar();
      calendar.setSubscription(true);
      return calendar;
    });

    assertFalse(calendarService.getCalendarById(CALENDAR, "owner").getAcl().isCanShare());
  }

  /**
   * The access of each reader: the owner in full, an invitee in full even
   * through a shared calendar, the sharee through the share only, a stranger
   * not at all.
   */
  @Test
  void theAccessOfEachReader() {
    assertEquals(EventAccess.FULL, eventService.getEventAccess(event(), OWNER));
    assertEquals(EventAccess.SHARED, eventService.getEventAccess(event(), ALICE));
    assertEquals(EventAccess.NONE, eventService.getEventAccess(event(), CAROL));
    assertTrue(eventService.canAccessEvent(event(), ALICE));
    assertFalse(eventService.canAccessEvent(event(), CAROL));

    when(attendeeService.isEventAttendee(EVENT, ALICE)).thenReturn(true);
    assertEquals(EventAccess.FULL, eventService.getEventAccess(event(), ALICE), "an invitee is never a sharee");
  }

  /**
   * By identifier: the sharee reads a private event as busy time, a public
   * one in full; the owner reads everything in full; a stranger is refused.
   *
   * @throws Exception when a read is refused
   */
  @Test
  void byIdentifierAPrivateEventIsMaskedForTheShareeOnly() throws Exception {
    Event forAlice = eventService.getEventById(EVENT, ZoneOffset.UTC, ALICE);
    assertTrue(forAlice.isMasked());
    assertNull(forAlice.getSummary());
    assertNull(forAlice.getDescription());
    assertNull(forAlice.getLocation());
    assertNotNull(forAlice.getStart(), "the time is what a masked event keeps");
    assertFalse(forAlice.getAcl().isCanEdit());

    Event forOwner = eventService.getEventById(EVENT, ZoneOffset.UTC, OWNER);
    assertFalse(forOwner.isMasked());
    assertEquals("Dentist", forOwner.getSummary());

    visibility = EventVisibility.DEFAULT;
    assertEquals("Dentist", eventService.getEventById(EVENT, ZoneOffset.UTC, ALICE).getSummary(), "only PRIVATE masks");
    visibility = EventVisibility.PUBLIC;
    assertFalse(eventService.getEventById(EVENT, ZoneOffset.UTC, ALICE).isMasked());

    assertThrows(IllegalAccessException.class, () -> eventService.getEventById(EVENT, ZoneOffset.UTC, CAROL));
  }

  /**
   * An occurrence of a series: the sharee reads it masked, the owner in full,
   * a stranger is refused — even an occurrence with no exceptional row of its
   * own, which used to be handed out to anyone naming the series.
   *
   * @throws Exception when a read is refused
   */
  @Test
  void anOccurrenceIsMaskedForTheShareeAndRefusedToAStranger() throws Exception {
    ZonedDateTime occurrenceId = ZonedDateTime.of(2026, 10, 8, 9, 0, 0, 0, ZoneOffset.UTC);

    Event forAlice = eventService.getEventOccurrence(SERIES, occurrenceId, ZoneOffset.UTC, ALICE);
    assertNotNull(forAlice);
    assertTrue(forAlice.isMasked());
    assertNull(forAlice.getSummary());

    Event forOwner = eventService.getEventOccurrence(SERIES, occurrenceId, ZoneOffset.UTC, OWNER);
    assertEquals("Standup", forOwner.getSummary());

    assertThrows(IllegalAccessException.class, () -> eventService.getEventOccurrence(SERIES, occurrenceId, ZoneOffset.UTC, CAROL));
  }

  /**
   * A listing naming the shared calendar: the sharee gets it, with its
   * private events masked; a stranger naming it is refused before anything is
   * read; the owner names it as their own.
   *
   * @throws Exception when a read is refused
   */
  @Test
  void aListingNamingTheSharedCalendarIsCheckedAgainstTheReader() throws Exception {
    when(eventStorage.getEventIds(any())).thenReturn(List.of(EVENT));

    List<Event> forAlice = eventService.getEvents(filter(List.of(CALENDAR)), ZoneOffset.UTC, ALICE);
    assertEquals(1, forAlice.size());
    assertTrue(forAlice.get(0).isMasked());
    assertNull(forAlice.get(0).getSummary());
    ArgumentCaptor<EventFilter> sent = ArgumentCaptor.forClass(EventFilter.class);
    org.mockito.Mockito.verify(eventStorage).getEventIds(sent.capture());
    assertEquals(List.of(CALENDAR), sent.getValue().getCalendarIds(), "the calendar reaches the query");

    List<Event> forOwner = eventService.getEvents(filter(List.of(CALENDAR)), ZoneOffset.UTC, OWNER);
    assertEquals("Dentist", forOwner.get(0).getSummary());

    assertThrows(IllegalAccessException.class, () -> eventService.getEvents(filter(List.of(CALENDAR)), ZoneOffset.UTC, CAROL),
                 "a third user naming someone else's shared calendar is refused");
    assertThrows(IllegalAccessException.class, () -> eventService.getEvents(filter(List.of(99L)), ZoneOffset.UTC, ALICE),
                 "and so is a calendar that does not exist");
  }

  /**
   * A listing naming no calendar and restricting no owner reads every
   * calendar shared with the reader; one restricting an owner reads none.
   *
   * @throws Exception when a read is refused
   */
  @Test
  void aListingNamingNoCalendarReadsTheSharedOnesByDefault() throws Exception {
    when(eventStorage.getEventIds(any())).thenReturn(List.of());
    ArgumentCaptor<EventFilter> sent = ArgumentCaptor.forClass(EventFilter.class);

    EventFilter open = filter(null);
    open.setAttendeeId(ALICE);
    eventService.getEvents(open, ZoneOffset.UTC, ALICE);
    org.mockito.Mockito.verify(eventStorage).getEventIds(sent.capture());
    assertEquals(List.of(CALENDAR), sent.getValue().getCalendarIds());

    EventFilter restricted = filter(null);
    restricted.setOwnerIds(List.of(ALICE));
    eventService.getEvents(restricted, ZoneOffset.UTC, ALICE);
    org.mockito.Mockito.verify(eventStorage, org.mockito.Mockito.times(2)).getEventIds(sent.capture());
    assertTrue(sent.getValue().getCalendarIds().isEmpty(), "an owner restriction reads no shared calendar by default");
  }

  /**
   * A search hit of the shared calendar is masked for the sharee when the
   * stored event is private — its excerpts too — and left as it is for the
   * owner; the shared calendars reach the connector from the service, and
   * not when the search is restricted to spaces.
   */
  @Test
  void aSearchHitOfASharedCalendarIsMaskedForTheSharee() {
    when(searchConnector.search(any())).thenAnswer(invocation -> List.of(hit()));

    List<EventSearchResult> forAlice = eventService.search(new AgendaEventSearchFilter(ALICE, ZoneOffset.UTC, "dent", null, null, null, 0, 10));
    assertTrue(forAlice.get(0).isMasked());
    assertNull(forAlice.get(0).getSummary());
    assertTrue(forAlice.get(0).getExcerpts().isEmpty());
    ArgumentCaptor<AgendaEventSearchFilter> sent = ArgumentCaptor.forClass(AgendaEventSearchFilter.class);
    org.mockito.Mockito.verify(searchConnector).search(sent.capture());
    assertEquals(List.of(CALENDAR), sent.getValue().getSharedCalendarIds());

    List<EventSearchResult> forOwner = eventService.search(new AgendaEventSearchFilter(OWNER, ZoneOffset.UTC, "dent", null, null, null, 0, 10));
    assertEquals("Dentist", forOwner.get(0).getSummary());

    eventService.search(new AgendaEventSearchFilter(ALICE, ZoneOffset.UTC, "dent", List.of(5L), null, null, 0, 10));
    org.mockito.Mockito.verify(searchConnector, org.mockito.Mockito.times(3)).search(sent.capture());
    assertTrue(sent.getValue().getSharedCalendarIds().isEmpty(), "a space search reads no shared calendar");
  }

  /**
   * A sharee gets no reminder on an event they read through the share alone;
   * the owner does.
   *
   * @throws Exception when the reminder is refused for the owner
   */
  @Test
  void aShareeGetsNoReminder() throws Exception {
    AgendaEventReminderServiceImpl reminderService = new AgendaEventReminderServiceImpl(mock(AgendaEventReminderStorage.class),
                                                                                       eventStorage,
                                                                                       mock(AgendaEventAttendeeStorage.class),
                                                                                       mock(AgendaUserSettingsService.class),
                                                                                       mock(IdentityManager.class),
                                                                                       mock(SpaceService.class),
                                                                                       mock(ListenerService.class),
                                                                                       mock(InitParams.class));
    reminderService.setAgendaEventService(eventService);

    assertThrows(IllegalAccessException.class, () -> reminderService.saveEventReminders(event(), List.of(), ALICE));
    assertThrows(IllegalAccessException.class, () -> reminderService.saveUpcomingEventReminders(SERIES,
                                                                                                ZonedDateTime.now(ZoneOffset.UTC),
                                                                                                List.of(),
                                                                                                ALICE));
    reminderService.saveEventReminders(event(), List.of(), OWNER);
  }

  /**
   * A filter over the event's day.
   *
   * @param calendarIds the calendars named, null for none
   * @return the filter
   */
  private static EventFilter filter(List<Long> calendarIds) {
    EventFilter filter = new EventFilter(List.of(OWNER),
                                         ZonedDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                                         ZonedDateTime.of(2026, 10, 2, 0, 0, 0, 0, ZoneOffset.UTC));
    filter.setOwnerIds(null);
    filter.setCalendarIds(calendarIds);
    return filter;
  }

  /**
   * Registers a user identity.
   *
   * @param id identity identifier
   * @param username username
   */
  private void user(long id, String username) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    identity.setEnable(true);
    identities.put(String.valueOf(id), identity);
  }

  /**
   * The owner's calendar, as the cache hands a copy of it.
   *
   * @return the calendar
   */
  private static Calendar calendar() {
    Calendar calendar = new Calendar();
    calendar.setId(CALENDAR);
    calendar.setOwnerId(OWNER);
    calendar.setName("Personal");
    return calendar;
  }

  /**
   * A private event of the calendar, as the cache hands a copy of it.
   *
   * @return the event
   */
  private Event event() {
    Event event = new Event();
    event.setId(EVENT);
    event.setCalendarId(CALENDAR);
    event.setCreatorId(OWNER);
    event.setSummary("Dentist");
    event.setDescription("Tooth 12");
    event.setLocation("Downtown");
    event.setVisibility(visibility);
    event.setAvailability(EventAvailability.BUSY);
    event.setStatus(EventStatus.CONFIRMED);
    event.setStart(ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC));
    event.setEnd(ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC));
    event.setTimeZoneId(ZoneOffset.UTC);
    return event;
  }

  /**
   * A private weekly series of the calendar.
   *
   * @return the series
   */
  private Event series() {
    Event event = event();
    event.setId(SERIES);
    event.setSummary("Standup");
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setType(EventRecurrenceType.WEEKLY);
    recurrence.setFrequency(EventRecurrenceFrequency.WEEKLY);
    recurrence.setInterval(1);
    recurrence.setByDay(List.of("TH"));
    event.setRecurrence(recurrence);
    return event;
  }

  /**
   * A search hit on the private event, as the index answers it.
   *
   * @return the hit
   */
  private static EventSearchResult hit() {
    EventSearchResult hit = new EventSearchResult();
    hit.setId(EVENT);
    hit.setCalendarId(CALENDAR);
    hit.setSummary("Dentist");
    hit.setDescription("Tooth 12");
    hit.setExcerpts(List.of("Tooth <b>12</b>"));
    hit.setStart(ZonedDateTime.of(2026, 10, 1, 9, 0, 0, 0, ZoneOffset.UTC));
    hit.setEnd(ZonedDateTime.of(2026, 10, 1, 10, 0, 0, 0, ZoneOffset.UTC));
    return hit;
  }

}
