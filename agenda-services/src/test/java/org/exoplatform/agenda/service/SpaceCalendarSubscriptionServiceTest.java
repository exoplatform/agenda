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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.agenda.model.CalendarSubscriptionEvent;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.storage.CalendarSubscriptionStorage;
import org.exoplatform.agenda.util.CalendarAddressGuard;
import org.exoplatform.agenda.util.CalendarFeedFetcher;
import org.exoplatform.agenda.util.CalendarFeedFetcher.FeedResponse;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.social.core.activity.model.ExoSocialActivity;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.ActivityManager;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.security.codec.AbstractCodec;
import org.exoplatform.web.security.codec.CodecInitializer;

/**
 * Pins the rules of a space's calendar subscriptions (EXO-90373): only a real
 * manager of the space — a member holding the manager role — lists, checks,
 * adds, reads, edits, refreshes or removes them, whoever added them, and a
 * member, a redactor, a super-manager who is not a member manager and an
 * outsider are all refused before anything is read or stored; a missing or
 * deleted owner is not found; the calendar is the space's, in the space's
 * colour, its events attended by the space; a space never subscribes to its
 * own calendar but may to another space's; the creation is announced once in
 * the space's stream, and a failed announcement undoes nothing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SpaceCalendarSubscriptionServiceTest {

  private static final Instant      NOW          = Instant.parse("2026-09-17T08:00:00Z");

  private static final String       URL          = "https://feeds.example.org/holidays.ics";

  /** A real manager of the space, who adds the subscription. */
  private static final long         JOHN         = 1;

  /** A plain member. */
  private static final long         MARY         = 2;

  /** A redactor, who writes events in the space. */
  private static final long         PAUL         = 3;

  /** A super-manager: may manage the space, is not a member manager of it. */
  private static final long         ROOT         = 4;

  /** Not a member. */
  private static final long         DAVE         = 5;

  /** Another real manager, who did not add the subscription. */
  private static final long         ANNE         = 6;

  private static final long         SPACE        = 100;

  private static final long         OTHER_SPACE  = 200;

  private static final long         SUBSCRIPTION = 11;

  private static final long         CALENDAR     = 77;

  @Mock
  private CalendarSubscriptionStorage storage;

  @Mock
  private AgendaCalendarService     calendarService;

  @Mock
  private AgendaEventStorage        eventStorage;

  @Mock
  private AgendaEventAttendeeStorage attendeeStorage;

  @Mock
  private AgendaCalendarLinkService linkService;

  @Mock
  private CalendarFeedFetcher       fetcher;

  @Mock
  private IdentityManager           identityManager;

  @Mock
  private SpaceService              spaceService;

  @Mock
  private CodecInitializer          codecInitializer;

  @Mock
  private AbstractCodec             codec;

  @Mock
  private IndexingService           indexingService;

  @Mock
  private ActivityManager           activityManager;

  private final Map<Long, CalendarSubscription> rows = new HashMap<>();

  private final AtomicLong          ids          = new AtomicLong(1000);

  private Space                     space;

  private Identity                  spaceIdentity;

  private AgendaCalendarSubscriptionServiceImpl service;

  /**
   * Wires the service over a space where John and Anne are real managers, Mary
   * a member, Paul a redactor, Root a super-manager and Dave an outsider.
   *
   * @throws Exception never
   */
  @BeforeEach
  void setUp() throws Exception {
    when(codecInitializer.getCodec()).thenReturn(codec);
    when(codec.encode(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    when(codec.decode(anyString())).thenAnswer(invocation -> ((String) invocation.getArgument(0)).substring(4));
    when(fetcher.getGuard()).thenReturn(new CalendarAddressGuard(false, Set.of(80, 443, 8080, 8443)));
    when(fetcher.fetch(any(), any(), any())).thenReturn(response(ics("X-WR-CALNAME:Holidays",
                                                                     vevent("a@test", "20261001T090000Z", "A"),
                                                                     vevent("b@test", "20261002T090000Z", "B"))));

    user(JOHN, "john");
    user(MARY, "mary");
    user(PAUL, "paul");
    user(ROOT, "root");
    user(DAVE, "dave");
    user(ANNE, "anne");
    space = new Space();
    space.setPrettyName("team");
    spaceIdentity = spaceIdentity(SPACE, "team", space);
    spaceIdentity(OTHER_SPACE, "other", new Space());
    for (String member : List.of("john", "mary", "paul", "anne")) {
      when(spaceService.isMember(space, member)).thenReturn(true);
      when(spaceService.canViewSpace(space, member)).thenReturn(true);
    }
    when(spaceService.isManager(space, "john")).thenReturn(true);
    when(spaceService.isManager(space, "anne")).thenReturn(true);
    // what a redactor and a super-manager are granted elsewhere, and must not
    // be granted here
    when(spaceService.isRedactor(space, "paul")).thenReturn(true);
    when(spaceService.canRedactOnSpace(space, "paul")).thenReturn(true);
    when(spaceService.canManageSpace(space, "root")).thenReturn(true);
    when(spaceService.canManageSpace(space, "john")).thenReturn(true);
    when(spaceService.canManageSpace(space, "anne")).thenReturn(true);
    when(spaceService.canViewSpace(space, "root")).thenReturn(true);

    Calendar spaceCalendar = new Calendar();
    spaceCalendar.setId(5);
    spaceCalendar.setOwnerId(SPACE);
    spaceCalendar.setColor("#abcdef");
    when(calendarService.getCalendarsByOwnerIds(eq(List.of(SPACE)), anyString())).thenReturn(List.of(spaceCalendar));
    when(calendarService.createCalendar(any(Calendar.class), anyString())).thenAnswer(invocation -> {
      Calendar calendar = invocation.getArgument(0);
      calendar.setId(CALENDAR);
      return calendar;
    });
    when(calendarService.getCalendarById(CALENDAR)).thenAnswer(invocation -> {
      Calendar calendar = new Calendar();
      calendar.setId(CALENDAR);
      calendar.setOwnerId(SPACE);
      calendar.setName("Holidays");
      calendar.setColor("#abcdef");
      calendar.setSubscription(true);
      return calendar;
    });
    when(storage.create(any())).thenAnswer(invocation -> {
      CalendarSubscription subscription = invocation.getArgument(0);
      subscription.setId(SUBSCRIPTION);
      rows.put(SUBSCRIPTION, copy(subscription));
      return copy(subscription);
    });
    when(storage.getById(anyLong())).thenAnswer(invocation -> copy(rows.get((Long) invocation.getArgument(0))));
    when(storage.claim(anyLong(), anyString(), any(), any())).thenReturn(true);
    when(storage.claimDue(anyLong(), anyString(), any(), any())).thenReturn(true);
    when(storage.recordSuccess(anyLong(), anyString(), anyString(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                                                                                                                             .thenReturn(true);
    when(storage.recordFailure(anyLong(), anyString(), any(), anyString(), any())).thenReturn(true);
    // As the DAO does: forgetting what was read clears the content hash, which
    // is the record that a withdrawn link's copy has already been purged
    doAnswer(invocation -> {
      CalendarSubscription stored = rows.get((Long) invocation.getArgument(0));
      if (stored != null) {
        stored.setContentHash(null);
      }
      return null;
    }).when(storage).forgetContent(anyLong());
    when(eventStorage.createEvent(any())).thenAnswer(invocation -> {
      Event event = invocation.getArgument(0);
      event.setId(ids.incrementAndGet());
      return event;
    });

    service = new AgendaCalendarSubscriptionServiceImpl(storage,
                                                        calendarService,
                                                        eventStorage,
                                                        attendeeStorage,
                                                        linkService,
                                                        fetcher,
                                                        identityManager,
                                                        spaceService,
                                                        codecInitializer,
                                                        indexingService,
                                                        activityManager);
    service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
    service.setLabelResolver((username, key) -> key);
  }

  /**
   * Only a real manager lists, checks or adds a space's subscriptions: a member,
   * a redactor, a super-manager who is not a member manager, and an outsider are
   * refused before any link is read or anything is created.
   *
   * @throws Exception never
   */
  @Test
  void onlyARealManagerListsChecksOrAddsASpacesSubscriptions() throws Exception {
    for (String refused : List.of("mary", "paul", "root", "dave")) {
      assertFalse(service.canManageSubscriptions(SPACE, refused), refused);
      assertThrows(IllegalAccessException.class, () -> service.getSubscriptions(SPACE, refused), refused);
      assertThrows(IllegalAccessException.class, () -> service.checkUrl(URL, SPACE, refused), refused);
      assertThrows(IllegalAccessException.class, () -> service.createSubscription(URL, null, null, SPACE, refused), refused);
    }
    verify(fetcher, never()).fetch(any(), any(), any());
    verify(calendarService, never()).createCalendar(any(Calendar.class), anyString());
    verify(storage, never()).create(any());
    verify(storage, never()).getByOwner(eq(SPACE), anyInt());

    assertTrue(service.canManageSubscriptions(SPACE, "john"));
    assertTrue(service.canManageSubscriptions(SPACE, "anne"));
    assertEquals(List.of(), service.getSubscriptions(SPACE, "john"));
    assertEquals("Holidays", service.checkUrl(URL, SPACE, "john"));
  }

  /**
   * A user's personal subscriptions are theirs alone, even for a manager of a
   * space they belong to.
   */
  @Test
  void aUsersSubscriptionsAreManagedByNobodyElse() {
    assertFalse(service.canManageSubscriptions(MARY, "john"));
    assertThrows(IllegalAccessException.class, () -> service.getSubscriptions(MARY, "john"));
    assertThrows(IllegalAccessException.class, () -> service.createSubscription(URL, null, null, MARY, "john"));
    assertTrue(service.canManageSubscriptions(JOHN, "john"));
  }

  /**
   * An owner that does not exist, or a space deleted meanwhile, is not found.
   */
  @Test
  void aMissingOrDeletedOwnerIsNotFound() {
    assertThrows(ObjectNotFoundException.class, () -> service.getSubscriptions(999, "john"));
    assertThrows(ObjectNotFoundException.class, () -> service.createSubscription(URL, null, null, 0, "john"));
    spaceIdentity.setDeleted(true);
    assertThrows(ObjectNotFoundException.class, () -> service.getSubscriptions(SPACE, "john"));
    assertThrows(ObjectNotFoundException.class, () -> service.checkUrl(URL, SPACE, "john"));
    assertFalse(service.canManageSubscriptions(SPACE, "john"));
  }

  /**
   * Once added, a space's subscription is read, edited, refreshed and removed by
   * any real manager — not only the one who added it — and by no member,
   * redactor, super-manager or outsider; the manager who added it loses it with
   * the role.
   *
   * @throws Exception never
   */
  @Test
  void onlyARealManagerReadsRefreshesOrRemovesASpacesSubscription() throws Exception {
    spaceRow();

    for (String refused : List.of("mary", "paul", "root", "dave")) {
      assertThrows(IllegalAccessException.class, () -> service.getSubscription(SUBSCRIPTION, refused), refused);
      assertThrows(IllegalAccessException.class, () -> service.updateSubscription(SUBSCRIPTION, null, "x", null, refused), refused);
      assertThrows(IllegalAccessException.class, () -> service.refreshSubscription(SUBSCRIPTION, refused), refused);
      assertThrows(IllegalAccessException.class, () -> service.deleteSubscription(SUBSCRIPTION, refused), refused);
    }
    verify(storage, never()).claim(anyLong(), anyString(), any(), any());
    verify(storage, never()).delete(anyLong());
    verify(calendarService, never()).updateCalendar(any(Calendar.class));

    CalendarSubscription read = service.getSubscription(SUBSCRIPTION, "anne");
    assertEquals(URL, read.getUrl(), "a manager who did not add it reads it");
    assertEquals("john", read.getCreatorUsername());
    assertEquals("John Smith", read.getCreatorFullName());
    assertNotNull(service.refreshSubscription(SUBSCRIPTION, "anne"));

    when(spaceService.isManager(space, "john")).thenReturn(false);
    assertThrows(IllegalAccessException.class, () -> service.deleteSubscription(SUBSCRIPTION, "john"),
                 "the manager who added it loses it with the role");

    service.deleteSubscription(SUBSCRIPTION, "anne");
    verify(storage).delete(SUBSCRIPTION);
    verify(calendarService).deleteCalendarById(CALENDAR);
  }

  /**
   * A space's subscription fills a calendar of the space, in the space's colour
   * whatever colour is given, recorded as the space's and added by the manager;
   * its events are created by the manager and attended by the space, accepted,
   * and it is announced once in the space's stream by the manager.
   *
   * @throws Exception never
   */
  @Test
  void aSpacesSubscriptionFillsACalendarOfTheSpaceInItsColour() throws Exception {
    CalendarSubscription created = service.createSubscription(URL, "Team <holidays>", "#123456", SPACE, "john");

    ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
    verify(calendarService).createCalendar(calendar.capture(), eq("john"));
    assertEquals(SPACE, calendar.getValue().getOwnerId());
    assertEquals("#abcdef", calendar.getValue().getColor(), "the space's colour, not the one given");
    assertTrue(calendar.getValue().isSubscription());

    ArgumentCaptor<CalendarSubscription> stored = ArgumentCaptor.forClass(CalendarSubscription.class);
    verify(storage).create(stored.capture());
    assertEquals(SPACE, stored.getValue().getOwnerIdentityId());
    assertEquals(JOHN, stored.getValue().getUserIdentityId());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.urlKey(SPACE, URI.create(URL)), stored.getValue().getUrlKey());
    assertNotEquals(AgendaCalendarSubscriptionServiceImpl.urlKey(JOHN, URI.create(URL)),
                    stored.getValue().getUrlKey(),
                    "the space and its manager each subscribe once to a link");

    ArgumentCaptor<Event> events = ArgumentCaptor.forClass(Event.class);
    verify(eventStorage, times(2)).createEvent(events.capture());
    events.getAllValues().forEach(event -> assertEquals(JOHN, event.getCreatorId()));
    verify(attendeeStorage, never()).saveEventAttendee(any(), anyLong());

    ArgumentCaptor<ExoSocialActivity> activity = ArgumentCaptor.forClass(ExoSocialActivity.class);
    verify(activityManager).saveActivityNoReturn(eq(spaceIdentity), activity.capture());
    assertEquals(String.valueOf(JOHN), activity.getValue().getUserId());
    assertEquals("Added the calendar <b>Team &lt;holidays&gt;</b> to the agenda of this space.",
                 activity.getValue().getTitle(),
                 "named after the calendar, escaped");
    assertEquals("john", created.getCreatorUsername());
  }

  /**
   * A space's imported occurrences are attended by nobody (EXO-90373), while a
   * personal subscription's are attended by their owner.
   * <p>
   * An attendee row naming the space would say that the space's members attend
   * the meeting, and every attendee-keyed listing of the platform expands to
   * {user} + {user's spaces} — which is how caldav's seeding of "the meetings
   * they attend" (CaldavPendingInvitationService) listed each imported
   * occurrence for every connected member and wrote a copy of it into their
   * personal CalDAV account, to come back as a remote event of theirs. The
   * feed's events are the space's, not its members' commitments.
   *
   * @throws Exception never
   */
  @Test
  void aSpacesImportedOccurrencesAreAttendedByNobody() throws Exception {
    service.createSubscription(URL, null, null, SPACE, "john");
    verify(eventStorage, times(2)).createEvent(any());
    verify(attendeeStorage, never()).saveEventAttendee(any(), anyLong());

    rows.clear();
    service.createSubscription("https://feeds.example.org/mine.ics", null, null, JOHN, "john");

    ArgumentCaptor<EventAttendee> attendees = ArgumentCaptor.forClass(EventAttendee.class);
    verify(attendeeStorage, times(2)).saveEventAttendee(attendees.capture(), anyLong());
    for (EventAttendee attendee : attendees.getAllValues()) {
      assertEquals(JOHN, attendee.getIdentityId(), "control: a personal subscription is attended by its owner");
      assertEquals(EventAttendeeResponse.ACCEPTED, attendee.getResponse(), "and never as a pending invitation");
    }
  }

  /**
   * The activity is written in the manager's language, with the calendar name
   * escaped, and a failure to post it keeps the subscription; a personal
   * subscription posts nothing.
   *
   * @throws Exception never
   */
  @Test
  void theAnnouncementIsTranslatedEscapedAndNeverUndoesTheSubscription() throws Exception {
    service.setLabelResolver((username, key) -> "a ajouté l'agenda {0}");
    assertEquals("a ajouté l'agenda &lt;b&gt;&amp;", service.spaceActivityTitle("john", "<b>&"));
    service.setLabelResolver((username, key) -> {
      throw new IllegalStateException("no container");
    });
    assertEquals("Added the calendar <b>x</b> to the agenda of this space.", service.spaceActivityTitle("john", "x"));

    doThrow(new IllegalStateException("stream down")).when(activityManager).saveActivityNoReturn(any(Identity.class), any());
    assertNotNull(service.createSubscription(URL, null, null, SPACE, "john"));
    verify(storage, never()).delete(anyLong());

    rows.clear();
    service.createSubscription("https://feeds.example.org/other.ics", null, null, JOHN, "john");
    verify(activityManager, times(1)).saveActivityNoReturn(any(Identity.class), any());
  }

  /**
   * A space never subscribes to a link of one of its own calendars, but may to
   * another space's or a colleague's — even one its manager already sees.
   *
   * @throws Exception never
   */
  @Test
  void aSpaceNeverSubscribesToItsOwnCalendarButMayToAnotherSpaces() throws Exception {
    when(calendarService.getCalendarById(5)).thenReturn(calendarOf(5, SPACE));
    when(calendarService.getCalendarById(6)).thenReturn(calendarOf(6, OTHER_SPACE));
    when(calendarService.getCalendarById(7)).thenReturn(calendarOf(7, JOHN));
    when(linkService.getFeedCalendarId("OWN")).thenReturn(5L);
    when(linkService.getFeedCalendarId("OTHER")).thenReturn(6L);
    when(linkService.getFeedCalendarId("JOHN")).thenReturn(7L);
    when(linkService.getCalendarFeed(anyString())).thenReturn(ics("X-WR-CALNAME:Linked", vevent("l@test", "20261001T090000Z", "L")));
    when(spaceService.canViewSpace(any(Space.class), eq("john"))).thenReturn(true);

    try (MockedStatic<CommonsUtils> commons = mockStatic(CommonsUtils.class)) {
      commons.when(CommonsUtils::getCurrentDomain).thenReturn("http://localhost:8080");
      assertEquals("agenda.calendarSubscription.ownSpaceCalendar",
                   assertThrows(IllegalArgumentException.class,
                                () -> service.checkUrl("http://localhost:8080/agenda/rest/ical/OWN.ics", SPACE, "john")).getMessage());
      assertEquals("Linked", service.checkUrl("http://localhost:8080/agenda/rest/ical/OTHER.ics", SPACE, "john"));
      assertEquals("Linked", service.checkUrl("http://localhost:8080/agenda/rest/ical/JOHN.ics", SPACE, "john"));
      assertEquals("agenda.calendarSubscription.ownCalendar",
                   assertThrows(IllegalArgumentException.class,
                                () -> service.checkUrl("http://localhost:8080/agenda/rest/ical/JOHN.ics", "john")).getMessage(),
                   "control: the same link is still refused for John himself");
    }
  }

  /**
   * An edit keeps a space's calendar in the space's colour, and a space's
   * subscription is refreshed while the space exists, whatever became of the
   * manager who added it.
   *
   * @throws Exception never
   */
  @Test
  void aSpacesSubscriptionKeepsTheSpaceColourAndOutlivesItsCreator() throws Exception {
    spaceRow();

    service.updateSubscription(SUBSCRIPTION, null, "Renamed", "#123456", "anne");
    ArgumentCaptor<Calendar> updated = ArgumentCaptor.forClass(Calendar.class);
    verify(calendarService).updateCalendar(updated.capture());
    assertEquals("Renamed", updated.getValue().getName());
    assertEquals("#abcdef", updated.getValue().getColor());

    Identity john = identityManager.getIdentity(String.valueOf(JOHN));
    john.setEnable(false);
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));
    assertEquals(1, service.refreshDueSubscriptions(10));
    verify(fetcher).fetch(eq(URI.create(URL)), any(), any());
    verify(storage, never()).recordFailure(anyLong(), anyString(), any(), eq(AgendaCalendarSubscriptionServiceImpl.USER_DISABLED), any());
  }

  /**
   * A colour saved on the space's calendar is given to the calendars the space's
   * subscriptions fill, the ones already in that colour left alone; a user's
   * calendar, a subscribed calendar or a blank colour recolours nothing.
   *
   * @throws Exception never
   */
  @Test
  void theSpacesSubscribedCalendarsFollowTheSpacesColour() throws Exception {
    Calendar first = calendarOf(77, SPACE);
    first.setSubscription(true);
    first.setColor("#abcdef");
    Calendar second = calendarOf(78, SPACE);
    second.setSubscription(true);
    second.setColor("#FF0000");
    when(calendarService.getCalendarById(77)).thenReturn(first);
    when(calendarService.getCalendarById(78)).thenReturn(second);
    when(storage.getCalendarIdsByOwner(eq(SPACE), anyInt())).thenReturn(List.of(77L, 78L));
    when(storage.getCalendarIdsByOwner(eq(JOHN), anyInt())).thenReturn(List.of(77L, 78L));

    Calendar johnsCalendar = calendarOf(9, JOHN);
    johnsCalendar.setColor("#000000");
    service.followSpaceColor(johnsCalendar);
    Calendar subscribed = calendarOf(77, SPACE);
    subscribed.setSubscription(true);
    subscribed.setColor("#000000");
    service.followSpaceColor(subscribed);
    Calendar blank = calendarOf(5, SPACE);
    service.followSpaceColor(blank);
    verify(calendarService, never()).updateCalendar(any(Calendar.class));

    Calendar spaceCalendar = calendarOf(5, SPACE);
    spaceCalendar.setColor("#ff0000");
    service.followSpaceColor(spaceCalendar);

    ArgumentCaptor<Calendar> recoloured = ArgumentCaptor.forClass(Calendar.class);
    verify(calendarService, times(1)).updateCalendar(recoloured.capture());
    assertEquals(77, recoloured.getValue().getId(), "the one already in that colour, whatever its case, is left alone");
    assertEquals("#ff0000", recoloured.getValue().getColor());
    assertTrue(recoloured.getValue().isSubscription(), "and it stays a subscribed calendar");
  }

  /**
   * A refresh that meets a deleted space removes its subscription, calendar and
   * events without reading the link; a space not found is only retried.
   *
   * @throws Exception never
   */
  @Test
  void aRefreshThatMeetsADeletedSpaceRemovesTheSubscription() throws Exception {
    spaceRow();
    when(storage.getEvents(SUBSCRIPTION)).thenReturn(List.of(new CalendarSubscriptionEvent(1, SUBSCRIPTION, 501, "k", "h")));
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));

    when(identityManager.getIdentity(String.valueOf(SPACE))).thenReturn(null);
    service.refreshDueSubscriptions(10);
    verify(storage).recordFailure(eq(SUBSCRIPTION), anyString(), any(), eq(AgendaCalendarSubscriptionServiceImpl.USER_DISABLED), any());
    verify(storage, never()).delete(anyLong());

    spaceIdentity.setDeleted(true);
    when(identityManager.getIdentity(String.valueOf(SPACE))).thenReturn(spaceIdentity);
    service.refreshDueSubscriptions(10);

    verify(fetcher, never()).fetch(any(), any(), any());
    verify(storage).delete(SUBSCRIPTION);
    verify(calendarService).deleteCalendarById(CALENDAR);
    verify(indexingService).unindex(anyString(), eq("501"));
  }

  /**
   * A link of this eXo withdrawn for a week loses its imported events and keeps
   * its error, and what was read is forgotten so a link published again is
   * imported in full; withdrawn for less, or failing for another reason, the
   * last good copy stays.
   *
   * @throws Exception never
   */
  @Test
  void aLinkWithdrawnForAWeekLosesItsImportedEvents() throws Exception {
    String link = "http://localhost:8080/agenda/rest/ical/GONE.ics";
    CalendarSubscription row = spaceRow();
    row.setUrlEncrypted("enc:" + link);
    row.setCreatedDate(NOW.minus(java.time.Duration.ofDays(30)).toEpochMilli());
    row.setLastSuccessDate(NOW.minus(java.time.Duration.ofDays(6)).toEpochMilli());
    // A row that imported something carries the hash of what it read: that is
    // what recordSuccess writes, and what forgetContent clears once the copy
    // has been purged
    row.setContentHash("h");
    when(storage.getEvents(SUBSCRIPTION)).thenReturn(List.of(new CalendarSubscriptionEvent(1, SUBSCRIPTION, 501, "k", "h")));
    Event imported = new Event();
    imported.setId(501);
    imported.setCalendarId(CALENDAR);
    when(eventStorage.getEventById(501)).thenReturn(imported);
    when(linkService.getFeedCalendarId("GONE")).thenThrow(new ObjectNotFoundException("withdrawn"));
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));

    try (MockedStatic<CommonsUtils> commons = mockStatic(CommonsUtils.class)) {
      commons.when(CommonsUtils::getCurrentDomain).thenReturn("http://localhost:8080");
      service.refreshDueSubscriptions(10);
      verify(eventStorage, never()).deleteEventById(anyLong());
      verify(storage, never()).forgetContent(anyLong());

      row.setLastSuccessDate(NOW.minus(java.time.Duration.ofDays(7)).toEpochMilli());
      service.refreshDueSubscriptions(10);

      // A third cycle, an hour later, on a row whose copy is gone. Nothing but
      // a successful read writes the content hash again, so a failure never
      // moves the date the purge window is measured from: without the
      // content-hash test at the call site the predicate stays true and every
      // retry re-issues these writes, hourly, for the life of the instance.
      // forgetContent having cleared the hash is what stops it, and the
      // storage stub clears it exactly as the DAO's statement does.
      service.refreshDueSubscriptions(10);
    }

    verify(eventStorage).deleteEventById(501);
    verify(indexingService).unindex(anyString(), eq("501"));
    verify(storage).deleteEvent(1);
    verify(storage, times(1)).forgetContent(SUBSCRIPTION);
    verify(storage, times(3)).recordFailure(eq(SUBSCRIPTION), anyString(), any(), eq("agenda.calendarSubscription.linkNotFound"), any());
    verify(storage, never()).delete(anyLong());
  }

  /**
   * The calendars of several owners' subscriptions are read in one statement
   * (EXO-90373).
   * <p>
   * This is asked on the listing path, where the owners are the reader's own
   * identity and every space they belong to, and once per attendee by the
   * availability reader — so one query per owner would be one query per space
   * on every grid navigation, and a few hundred on a meeting suggestion.
   */
  @Test
  void theOwnersSubscribedCalendarsAreReadInOneStatement() {
    when(storage.getCalendarIdsByOwners(any(), anyInt())).thenReturn(List.of(77L, 78L));

    List<Long> calendarIds = service.getSubscriptionCalendarIds(java.util.Arrays.asList(SPACE, JOHN, SPACE, null));

    assertEquals(List.of(77L, 78L), calendarIds);
    verify(storage, times(1)).getCalendarIdsByOwners(eq(List.of(SPACE, JOHN)), anyInt());
    verify(storage, never()).getCalendarIdsByOwner(anyLong(), anyInt());
  }

  /**
   * An unreachable external link keeps its copy however long it fails: only a
   * withdrawn link of this eXo is purged.
   *
   * @throws Exception never
   */
  @Test
  void anUnreachableLinkKeepsItsCopyHoweverLong() throws Exception {
    CalendarSubscription row = spaceRow();
    row.setCreatedDate(NOW.minus(java.time.Duration.ofDays(60)).toEpochMilli());
    row.setLastSuccessDate(NOW.minus(java.time.Duration.ofDays(30)).toEpochMilli());
    when(storage.getEvents(SUBSCRIPTION)).thenReturn(List.of(new CalendarSubscriptionEvent(1, SUBSCRIPTION, 501, "k", "h")));
    doThrow(new org.exoplatform.agenda.util.CalendarFeedException(org.exoplatform.agenda.util.CalendarFeedException.UNREACHABLE))
                                                                                                                               .when(fetcher)
                                                                                                                               .fetch(any(), any(), any());
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));

    service.refreshDueSubscriptions(10);

    verify(eventStorage, never()).deleteEventById(anyLong());
    verify(storage, never()).forgetContent(anyLong());
  }

  /**
   * Stores the space's subscription, added by John.
   *
   * @return the stored row, which the storage copies on every read
   */
  private CalendarSubscription spaceRow() {
    CalendarSubscription subscription = new CalendarSubscription();
    subscription.setId(SUBSCRIPTION);
    subscription.setCalendarId(CALENDAR);
    subscription.setUserIdentityId(JOHN);
    subscription.setOwnerIdentityId(SPACE);
    subscription.setUrlEncrypted("enc:" + URL);
    subscription.setUrlKey(AgendaCalendarSubscriptionServiceImpl.urlKey(SPACE, URI.create(URL)));
    subscription.setNextRefreshDate(NOW.toEpochMilli());
    rows.put(SUBSCRIPTION, subscription);
    return subscription;
  }

  /**
   * A copy of a stored row, as a storage reads it again: the service clears the
   * secrets of what it returns, never of what is stored.
   *
   * @param source the row, may be null
   * @return the copy
   */
  private static CalendarSubscription copy(CalendarSubscription source) {
    if (source == null) {
      return null;
    }
    CalendarSubscription copy = new CalendarSubscription();
    copy.setId(source.getId());
    copy.setCalendarId(source.getCalendarId());
    copy.setUserIdentityId(source.getUserIdentityId());
    copy.setOwnerIdentityId(source.getOwnerIdentityId());
    copy.setUrlEncrypted(source.getUrlEncrypted());
    copy.setUrlKey(source.getUrlKey());
    copy.setNextRefreshDate(source.getNextRefreshDate());
    copy.setLastSuccessDate(source.getLastSuccessDate());
    copy.setCreatedDate(source.getCreatedDate());
    // The digest of what was last read: the refresh reads it to tell a copy it
    // has already purged from one it still has to
    copy.setContentHash(source.getContentHash());
    return copy;
  }

  /**
   * Registers an enabled user.
   *
   * @param id identity identifier
   * @param username the user
   */
  private void user(long id, String username) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    identity.setEnable(true);
    Profile profile = new Profile(identity);
    profile.setProperty(Profile.FULL_NAME, username.substring(0, 1).toUpperCase() + username.substring(1) + " Smith");
    identity.setProfile(profile);
    when(identityManager.getIdentity(String.valueOf(id))).thenReturn(identity);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(identity);
  }

  /**
   * Registers a space identity.
   *
   * @param id identity identifier
   * @param prettyName the space's pretty name
   * @param model the space
   * @return the identity
   */
  private Identity spaceIdentity(long id, String prettyName, Space model) {
    Identity identity = new Identity(SpaceIdentityProvider.NAME, prettyName);
    identity.setId(String.valueOf(id));
    when(identityManager.getIdentity(String.valueOf(id))).thenReturn(identity);
    when(spaceService.getSpaceByPrettyName(prettyName)).thenReturn(model);
    return identity;
  }

  /**
   * A calendar of an owner.
   *
   * @param id identifier
   * @param ownerId owner identity identifier
   * @return the calendar
   */
  private static Calendar calendarOf(long id, long ownerId) {
    Calendar calendar = new Calendar();
    calendar.setId(id);
    calendar.setOwnerId(ownerId);
    return calendar;
  }

  /**
   * A read answering a body.
   *
   * @param body the body
   * @return the response
   */
  private static FeedResponse response(String body) {
    return new FeedResponse(false, body.getBytes(StandardCharsets.UTF_8), null, null);
  }

  /**
   * A calendar document.
   *
   * @param lines the lines inside VCALENDAR
   * @return the document
   */
  private static String ics(String... lines) {
    return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\n" + String.join("\r\n", lines) + "\r\nEND:VCALENDAR\r\n";
  }

  /**
   * An event of one hour.
   *
   * @param uid its UID
   * @param start its UTC start, basic format
   * @param summary its title
   * @return the lines of the event
   */
  private static String vevent(String uid, String start, String summary) {
    String end = start.substring(0, 9) + String.format("%02d", Integer.parseInt(start.substring(9, 11)) + 1) + start.substring(11);
    return String.join("\r\n", "BEGIN:VEVENT", "UID:" + uid, "DTSTART:" + start, "DTEND:" + end, "SUMMARY:" + summary, "END:VEVENT");
  }

}
