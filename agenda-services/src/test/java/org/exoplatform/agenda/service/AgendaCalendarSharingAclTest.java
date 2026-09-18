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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
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
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.model.CalendarEditorChange;
import org.exoplatform.agenda.constant.EventAccess;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.model.AgendaEventSearchFilter;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.EventOccurrence;
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
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
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

  /** The identifier the storage gives the exceptional occurrence it is asked to write. */
  private static final long        NEW_EVENT = 601;

  /** Alice's own personal calendar: what an editor may create in, and may not move the owner's events to. */
  private static final long        ALICE_CALENDAR = 78;

  /** Another calendar of the owner's, shared with nobody. */
  private static final long        OTHER_CALENDAR = 80;

  /** A space, and its calendar: never shareable. */
  private static final long        SPACE          = 9;

  /** The space's calendar. */
  private static final long        SPACE_CALENDAR = 79;

  private final Map<String, Identity> identities = new HashMap<>();

  private AgendaCalendarShareService shareService;

  private AgendaCalendarStorage      calendarStorage;

  private AgendaEventStorage         eventStorage;

  private AgendaEventAttendeeService attendeeService;

  private AgendaSearchConnector      searchConnector;

  private AgendaCalendarServiceImpl  calendarService;

  private AgendaEventServiceImpl     eventService;

  private ListenerService            listenerService;

  private EventVisibility            visibility = EventVisibility.PRIVATE;

  /** The level the owner's calendar is shared with Alice at (EXO-90378). */
  private CalendarShareLevel         aliceLevel = CalendarShareLevel.VIEW;

  /** Whether the fixture's event lets its attendees update it. */
  private boolean                    attendeeMayUpdate;

  /**
   * The level the one share of this fixture grants, null for no share: the
   * owner's calendar is shared with Alice and with nobody else.
   *
   * @param calendarId the calendar asked about
   * @param identityId the reader asked about
   * @return the level, or null
   */
  private CalendarShareLevel levelOf(long calendarId, long identityId) {
    return calendarId == CALENDAR && identityId == ALICE ? aliceLevel : null;
  }

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
    // One source of truth for the share, read by every primitive, so that a
    // test flipping the level flips what all of them answer (EXO-90378)
    when(shareService.isSharedWith(anyLong(), anyLong())).thenAnswer(invocation -> levelOf(invocation.getArgument(0),
                                                                                          invocation.getArgument(1)) != null);
    when(shareService.getShareLevel(anyLong(), anyLong())).thenAnswer(invocation -> levelOf(invocation.getArgument(0),
                                                                                           invocation.getArgument(1)));
    when(shareService.getShareLevels(anyLong())).thenAnswer(invocation -> levelOf(CALENDAR,
                                                                                 invocation.getArgument(0)) == null
                                                                                                                   ? Map.of()
                                                                                                                   : Map.of(CALENDAR,
                                                                                                                            aliceLevel));
    when(shareService.getSharedCalendarIds(anyLong())).thenAnswer(invocation -> levelOf(CALENDAR,
                                                                                       invocation.getArgument(0)) == null
                                                                                                                         ? List.of()
                                                                                                                         : List.of(CALENDAR));

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
    listenerService = mock(ListenerService.class);
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
                                              listenerService,
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
   * An editor writes in the calendar shared with them (EXO-90378): they may
   * create in it, and update and delete its events, exactly as its owner may.
   * A viewer may none of it, and a stranger even less.
   */
  @Test
  void anEditorCreatesUpdatesAndDeletesInTheSharedCalendar() {
    assertFalse(eventService.canCreateEvent(calendar(), ALICE), "a viewer creates nothing");
    assertFalse(eventService.canUpdateEvent(event(), ALICE), "and updates nothing");

    aliceLevel = CalendarShareLevel.EDIT;

    assertTrue(eventService.canCreateEvent(calendar(), ALICE), "an editor creates in the owner's calendar");
    assertTrue(eventService.canUpdateEvent(event(), ALICE), "and updates its events");
    assertTrue(eventService.canUpdateEvent(series(), ALICE), "series included");
    assertFalse(eventService.canCreateEvent(calendar(), CAROL), "a stranger creates nothing");
    assertFalse(eventService.canUpdateEvent(event(), CAROL), "and updates nothing");
    assertTrue(eventService.canUpdateEvent(event(), OWNER), "and the owner keeps every right they had");
  }

  /**
   * An edit share is a <b>reading</b> access too: the editor reads the
   * calendar's private events in full, where a viewer reads them as busy time
   * (EXO-90378, PO decision 2).
   *
   * @throws Exception when a read is refused
   */
  @Test
  void anEditorReadsThePrivateEventsInFull() throws Exception {
    assertEquals(EventAccess.SHARED, eventService.getEventAccess(event(), ALICE));
    assertTrue(eventService.getEventById(EVENT, ZoneOffset.UTC, ALICE).isMasked());

    aliceLevel = CalendarShareLevel.EDIT;

    assertEquals(EventAccess.SHARED_EDIT, eventService.getEventAccess(event(), ALICE));
    Event forAlice = eventService.getEventById(EVENT, ZoneOffset.UTC, ALICE);
    assertFalse(forAlice.isMasked(), "an editor is not shown busy time in the calendar they write");
    assertEquals("Dentist", forAlice.getSummary());
    assertEquals("Tooth 12", forAlice.getDescription());
    assertTrue(forAlice.getAcl().isCanEdit(), "and the event carries the right the UI drives edit, delete and drag from");
    assertEquals(EventAccess.NONE, eventService.getEventAccess(event(), CAROL), "a stranger still reads nothing");
  }

  /**
   * A listing and a search hit of the shared calendar are unmasked for an
   * editor and masked for a viewer: the level travels through the listing's
   * one cached read, not through a second question per event.
   */
  @Test
  void aListingAndASearchHitAreUnmaskedForAnEditor() throws Exception {
    when(eventStorage.getEventIds(any())).thenReturn(List.of(EVENT));
    when(searchConnector.search(any())).thenAnswer(invocation -> List.of(hit()));
    aliceLevel = CalendarShareLevel.EDIT;

    List<Event> listed = eventService.getEvents(filter(List.of(CALENDAR)), ZoneOffset.UTC, ALICE);
    assertEquals(1, listed.size());
    assertFalse(listed.get(0).isMasked());
    assertEquals("Dentist", listed.get(0).getSummary());
    assertTrue(listed.get(0).getAcl().isCanEdit());

    List<EventSearchResult> found = eventService.search(new AgendaEventSearchFilter(ALICE,
                                                                                   ZoneOffset.UTC,
                                                                                   "dent",
                                                                                   null,
                                                                                   null,
                                                                                   null,
                                                                                   0,
                                                                                   10));
    assertFalse(found.get(0).isMasked());
    assertEquals("Dentist", found.get(0).getSummary());
    assertFalse(found.get(0).getExcerpts().isEmpty(), "and the words that matched are not withheld either");
  }

  /**
   * The calendar row an editor reads: they may create in it, and nothing
   * more. canEdit stays false — renaming, recolouring and deleting the
   * calendar are the owner's — and so do canPublish and canShare, so an
   * editor can neither publish the calendar nor re-share it nor level anyone.
   *
   * @throws Exception when the read is refused
   */
  @Test
  void anEditorsCalendarRowGrantsCreationAndNothingElse() throws Exception {
    aliceLevel = CalendarShareLevel.EDIT;

    Calendar forAlice = calendarService.getCalendarById(CALENDAR, "alice");

    assertTrue(forAlice.isSharedWithMe());
    assertEquals(CalendarShareLevel.EDIT, forAlice.getShareLevel());
    assertTrue(forAlice.getAcl().isCanCreate());
    assertFalse(forAlice.getAcl().isCanEdit(), "the calendar itself stays the owner's");
    assertFalse(forAlice.getAcl().isCanPublish());
    assertFalse(forAlice.getAcl().isCanShare(), "an editor re-shares nothing and levels nobody");

    aliceLevel = CalendarShareLevel.VIEW;
    Calendar viewer = calendarService.getCalendarById(CALENDAR, "alice");
    assertEquals(CalendarShareLevel.VIEW, viewer.getShareLevel());
    assertFalse(viewer.getAcl().isCanCreate(), "a downgrade takes the creation right back");
  }

  /**
   * An editor may not move an event out of the calendar shared with them: the
   * event is the owner's. Refused on the patch path, into their own calendar
   * — which they may otherwise create in — and the owner is unaffected.
   */
  @Test
  void anEditorCannotMoveAnEventOutOfTheSharedCalendar() {
    when(calendarStorage.getCalendarById(ALICE_CALENDAR)).thenAnswer(invocation -> aliceCalendar());
    aliceLevel = CalendarShareLevel.EDIT;
    Map<String, List<String>> move = Map.of("calendarId", List.of(String.valueOf(ALICE_CALENDAR)));

    assertThrows(IllegalAccessException.class,
                 () -> eventService.updateEventFields(EVENT, move, false, false, ALICE),
                 "the event stays in the calendar its owner shared");
    org.mockito.Mockito.verify(eventStorage, org.mockito.Mockito.never()).updateEvent(any());

    assertTrue(eventService.canCreateEvent(aliceCalendar(), ALICE), "though she may create in her own calendar");
    assertThrows(IllegalAccessException.class,
                 () -> eventService.updateEventFields(EVENT, move, false, false, CAROL),
                 "a stranger is refused before the move is even looked at");
  }

  /**
   * An editor may change every other field of the owner's events, the
   * calendar among them as long as it does not change: the move check is a
   * check on the move, not a veto on the patch.
   *
   * @throws Exception when the patch is refused
   */
  @Test
  void anEditorPatchesTheOwnersEventInPlace() throws Exception {
    when(eventStorage.updateEvent(any())).thenAnswer(invocation -> invocation.getArgument(0));
    aliceLevel = CalendarShareLevel.EDIT;

    eventService.updateEventFields(EVENT,
                                   Map.of("summary", List.of("Moved by alice"), "calendarId", List.of(String.valueOf(CALENDAR))),
                                   false,
                                   false,
                                   ALICE);

    ArgumentCaptor<Event> written = ArgumentCaptor.forClass(Event.class);
    org.mockito.Mockito.verify(eventStorage).updateEvent(written.capture());
    assertEquals("Moved by alice", written.getValue().getSummary());
    assertEquals(CALENDAR, written.getValue().getCalendarId(), "and it is still the owner's calendar");
    assertEquals(ALICE, written.getValue().getModifierId(), "the change is recorded as hers");
  }

  /**
   * An editor deletes the owner's events; a viewer does not. The refusal is
   * the same predicate, so the pin is on the entry point the REST DELETE
   * reaches.
   */
  @Test
  void anEditorDeletesTheOwnersEventAndAViewerDoesNot() {
    assertThrows(IllegalAccessException.class, () -> eventService.deleteEventById(EVENT, ALICE), "a viewer deletes nothing");

    aliceLevel = CalendarShareLevel.EDIT;

    assertTrue(eventService.canUpdateEvent(event(), ALICE), "and an editor may, through the one predicate DELETE goes through");
    assertThrows(IllegalAccessException.class, () -> eventService.deleteEventById(EVENT, CAROL), "a stranger deletes nothing");
  }

  /**
   * An editor may create an exceptional occurrence of a series held in the
   * shared calendar — the case where EXO-90378 meets EXO-90381, which
   * requires the right to update the series before an occurrence of it may be
   * created. A viewer may not, and neither may a stranger.
   */
  @Test
  void anEditorMayCreateAnExceptionalOccurrenceOfTheOwnersSeries() {
    assertFalse(eventService.canUpdateEvent(series(), ALICE), "a viewer cannot update the series");

    aliceLevel = CalendarShareLevel.EDIT;

    assertTrue(eventService.canUpdateEvent(series(), ALICE),
               "an editor may update the series, which is what creating an occurrence of it requires (EXO-90381)");
    assertTrue(eventService.canCreateEvent(calendar(), ALICE), "and may create in the calendar it is filed in");
    assertFalse(eventService.canUpdateEvent(series(), CAROL));
  }

  /**
   * An editor edits one date of the owner's series for real, through the write
   * the web UI makes: an exceptional occurrence filed into the owner's
   * calendar, where the series is (EXO-90382). A viewer is still refused, and
   * so is a stranger — the update right on the series is the only thing that
   * branch asks for, so it is the only thing refusing them.
   * <p>
   * The editor's half is a non-regression pin, not a grant this change makes:
   * an edit share already grants creation in the shared calendar (EXO-90378),
   * so it passed before the relaxation too. The refusals are what bite.
   *
   * @throws Exception when a write is refused for a user who may make it
   */
  @Test
  void anEditorEditsOneDateOfTheOwnersSeriesAndAViewerDoesNot() throws Exception {
    AtomicReference<Event> stored = new AtomicReference<>();
    when(eventStorage.createEvent(any())).thenAnswer(invocation -> {
      Event created = invocation.<Event> getArgument(0).clone();
      created.setId(NEW_EVENT);
      stored.set(created);
      return created;
    });
    when(eventStorage.getEventById(NEW_EVENT)).thenAnswer(invocation -> stored.get());

    assertThrows(IllegalAccessException.class,
                 () -> eventService.createEvent(occurrenceOfSeries(), null, null, null, null, null, false, ALICE),
                 "a viewer edits no date of the owner's series");
    assertThrows(IllegalAccessException.class,
                 () -> eventService.createEvent(occurrenceOfSeries(), null, null, null, null, null, false, CAROL),
                 "and neither does a stranger");
    org.mockito.Mockito.verify(eventStorage, org.mockito.Mockito.never()).createEvent(any());

    aliceLevel = CalendarShareLevel.EDIT;

    assertNotNull(eventService.createEvent(occurrenceOfSeries(), null, null, null, null, null, false, ALICE));
    ArgumentCaptor<Event> written = ArgumentCaptor.forClass(Event.class);
    org.mockito.Mockito.verify(eventStorage).createEvent(written.capture());
    assertEquals(SERIES, written.getValue().getParentId(), "the occurrence amends the owner's series");
    assertEquals(CALENDAR, written.getValue().getCalendarId(), "and is filed where the series is");
  }

  /**
   * An editor sets their own reminders on the owner's events; a viewer is
   * still refused (EXO-90378, PO decision 13). A reminder is per receiver:
   * the editor's reaches nobody else.
   *
   * @throws Exception when a reminder is refused for a user who may set one
   */
  @Test
  void anEditorSetsTheirOwnRemindersAndAViewerDoesNot() throws Exception {
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

    aliceLevel = CalendarShareLevel.EDIT;

    reminderService.saveEventReminders(event(), List.of(), ALICE);
  }

  /**
   * The owner is told what an editor changed in their calendar (EXO-90378),
   * and told nothing when they changed it themselves. The decision is the
   * service's — it is the one place that knows by what right the writer wrote
   * — and the change carries the event's summary, captured at the write.
   *
   * @throws Exception when a patch is refused
   */
  @Test
  void theOwnerIsToldWhatAnEditorChangedAndNothingOfTheirOwnChanges() throws Exception {
    when(eventStorage.updateEvent(any())).thenAnswer(invocation -> invocation.getArgument(0));
    aliceLevel = CalendarShareLevel.EDIT;

    eventService.updateEventFields(EVENT, Map.of("summary", List.of("Moved by alice")), false, false, ALICE);

    ArgumentCaptor<CalendarEditorChange> change = ArgumentCaptor.forClass(CalendarEditorChange.class);
    org.mockito.Mockito.verify(listenerService).broadcast(eq(AgendaEventService.CALENDAR_EDITED_BY_SHAREE_EVENT),
                                                         change.capture(),
                                                         eq(OWNER));
    assertEquals(CALENDAR, change.getValue().getCalendarId());
    assertEquals(OWNER, change.getValue().getOwnerIdentityId());
    assertEquals(ALICE, change.getValue().getModifierIdentityId());
    assertEquals(CalendarEditorChange.Kind.CHANGED, change.getValue().getKind());
    assertEquals("Moved by alice", change.getValue().getEventSummary(), "the summary as it stands after the change");

    eventService.updateEventFields(EVENT, Map.of("summary", List.of("Moved by bob")), false, false, OWNER);

    org.mockito.Mockito.verify(listenerService, org.mockito.Mockito.times(1))
                       .broadcast(eq(AgendaEventService.CALENDAR_EDITED_BY_SHAREE_EVENT), any(), any());
  }

  /**
   * A change by someone whose right is an older one raises nothing: an
   * attendee the event lets update it writes by that right, not by the share,
   * and the owner is told about the one case they cannot otherwise see coming
   * (EXO-90378). This is the case the share check itself answers for — the
   * owner's own change is caught a line later, by the owner guard.
   *
   * @throws Exception when a patch is refused
   */
  @Test
  void aWriterHoldingAnOlderRightRaisesNoChangeNotification() throws Exception {
    when(eventStorage.updateEvent(any())).thenAnswer(invocation -> invocation.getArgument(0));
    // Alice attends and the event lets its attendees update it: an older right
    // than the share, and the one writeRightOf answers before it
    attendeeMayUpdate = true;
    when(attendeeService.isEventAttendee(EVENT, ALICE)).thenReturn(true);
    aliceLevel = CalendarShareLevel.EDIT;

    eventService.updateEventFields(EVENT, Map.of("summary", List.of("Moved by alice")), false, false, ALICE);

    org.mockito.Mockito.verify(listenerService, org.mockito.Mockito.never())
                       .broadcast(eq(AgendaEventService.CALENDAR_EDITED_BY_SHAREE_EVENT), any(), any());
  }

  /**
   * A viewer changes nothing, so there is nothing to tell the owner about: the
   * refusal comes first and no change is ever broadcast.
   */
  @Test
  void aViewerRaisesNoChangeNotification() throws Exception {
    aliceLevel = CalendarShareLevel.VIEW;

    assertThrows(IllegalAccessException.class,
                 () -> eventService.updateEventFields(EVENT, Map.of("summary", List.of("x")), false, false, ALICE));

    org.mockito.Mockito.verify(listenerService, org.mockito.Mockito.never())
                       .broadcast(eq(AgendaEventService.CALENDAR_EDITED_BY_SHAREE_EVENT), any(), any());
  }

  /**
   * The owner's calendar listing is per calendar, not per owner (EXO-90378):
   * a colleague the owner shared <b>one</b> calendar with is served that one
   * and not the owner's others, instead of being refused the whole listing
   * because of them.
   * <p>
   * This is the listing the event form asks for when it resolves an event's
   * destination. While it answered 401, the form resolved no calendar, and
   * saving an event of a shared calendar died in the browser before any
   * request was issued — the defect the PO hit on the rig.
   *
   * @throws Exception when the listing is refused
   */
  @Test
  void theOwnersListingServesTheSharedCalendarAndLeavesTheirOthersOut() throws Exception {
    // The owner has two calendars; only CALENDAR is shared with Alice
    Calendar other = calendar();
    other.setId(OTHER_CALENDAR);
    other.setName("Private");
    when(calendarStorage.getCalendarById(OTHER_CALENDAR)).thenAnswer(invocation -> {
      Calendar copy = calendar();
      copy.setId(OTHER_CALENDAR);
      copy.setName("Private");
      return copy;
    });
    when(calendarStorage.getCalendarIdsByOwnerIds(anyInt(), anyInt(), any(Long[].class))).thenReturn(List.of(CALENDAR,
                                                                                                            OTHER_CALENDAR));
    aliceLevel = CalendarShareLevel.EDIT;

    List<Calendar> forAlice = calendarService.getCalendarsByOwnerIds(List.of(OWNER), "alice");

    assertEquals(1, forAlice.size(), "the shared calendar, and not the owner's other one");
    assertEquals(CALENDAR, forAlice.get(0).getId());
    assertTrue(forAlice.get(0).isSharedWithMe());
    assertTrue(forAlice.get(0).getAcl().isCanCreate());

    List<Calendar> forOwner = calendarService.getCalendarsByOwnerIds(List.of(OWNER), "owner");
    assertEquals(2, forOwner.size(), "the owner still gets every calendar of theirs");
  }

  /**
   * A stranger is still refused the whole listing: leaving out what they may
   * not read must not turn a refusal into an empty answer, which would let
   * anyone ask after anyone's calendars.
   */
  @Test
  void aStrangerIsStillRefusedTheOwnersListing() {
    when(calendarStorage.getCalendarIdsByOwnerIds(anyInt(), anyInt(), any(Long[].class))).thenReturn(List.of(CALENDAR));

    assertThrows(IllegalAccessException.class, () -> calendarService.getCalendarsByOwnerIds(List.of(OWNER), "carol"));
  }

  /**
   * Without a share service to ask, nobody writes by a share either: the
   * absent bean answers no level, never the narrower one, so an editor is as
   * refused as a viewer.
   */
  @Test
  void anAbsentShareServiceAdmitsNoEditor() {
    aliceLevel = CalendarShareLevel.EDIT;
    eventService.setCalendarShareAccess(new CalendarShareAccess(null));
    calendarService.setCalendarShareAccess(new CalendarShareAccess(null));

    assertFalse(eventService.canCreateEvent(calendar(), ALICE));
    assertFalse(eventService.canUpdateEvent(event(), ALICE));
    assertEquals(EventAccess.NONE, eventService.getEventAccess(event(), ALICE));
    assertThrows(IllegalAccessException.class, () -> calendarService.getCalendarById(CALENDAR, "alice"));
  }

  /**
   * A share never reaches a space calendar (EXO-90357, unchanged here): the
   * edit branch of the write predicate asks for a calendar whose owner is a
   * <b>user</b>, so a record naming a space calendar grants nothing.
   */
  @Test
  void aShareGrantsNothingOnASpaceCalendar() {
    Identity space = new Identity(SpaceIdentityProvider.NAME, "team");
    space.setId(String.valueOf(SPACE));
    identities.put(String.valueOf(SPACE), space);
    when(calendarStorage.getCalendarById(SPACE_CALENDAR)).thenAnswer(invocation -> spaceCalendar());
    aliceLevel = CalendarShareLevel.EDIT;
    // A record naming the space's calendar, which the owner check must refuse
    // on its own: the whole point is that no share ever grants on a space,
    // whatever a row says
    when(shareService.getShareLevel(SPACE_CALENDAR, ALICE)).thenReturn(CalendarShareLevel.EDIT);
    when(shareService.isSharedWith(SPACE_CALENDAR, ALICE)).thenReturn(true);

    assertFalse(eventService.canCreateEvent(spaceCalendar(), ALICE), "a space calendar is not shareable, so not editable");
    Event spaceEvent = event();
    spaceEvent.setCalendarId(SPACE_CALENDAR);
    assertFalse(eventService.canUpdateEvent(spaceEvent, ALICE));
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
    event.setAllowAttendeeToUpdate(attendeeMayUpdate);
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
   * The payload the web UI sends when one date of the owner's series is
   * edited: no identifier, the series as parent, the series' own calendar, the
   * occurrence identifier, and no recurrence.
   *
   * @return the event to pass to {@code createEvent}
   */
  private static Event occurrenceOfSeries() {
    Event event = new Event();
    event.setParentId(SERIES);
    event.setCalendarId(CALENDAR);
    event.setSummary("Standup, moved");
    event.setStatus(EventStatus.CONFIRMED);
    event.setAvailability(EventAvailability.BUSY);
    event.setVisibility(EventVisibility.DEFAULT);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.setStart(ZonedDateTime.of(2026, 10, 8, 10, 0, 0, 0, ZoneOffset.UTC));
    event.setEnd(ZonedDateTime.of(2026, 10, 8, 11, 0, 0, 0, ZoneOffset.UTC));
    event.setOccurrence(new EventOccurrence(ZonedDateTime.of(2026, 10, 8, 9, 0, 0, 0, ZoneOffset.UTC)));
    return event;
  }

  /**
   * Alice's own personal calendar, the target of a move an editor may not
   * make.
   *
   * @return the calendar
   */
  private static Calendar aliceCalendar() {
    Calendar calendar = new Calendar();
    calendar.setId(ALICE_CALENDAR);
    calendar.setOwnerId(ALICE);
    calendar.setName("Alice");
    return calendar;
  }

  /**
   * A space's calendar, which no share ever reaches.
   *
   * @return the calendar
   */
  private static Calendar spaceCalendar() {
    Calendar calendar = new Calendar();
    calendar.setId(SPACE_CALENDAR);
    calendar.setOwnerId(SPACE);
    calendar.setName("Team");
    return calendar;
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
