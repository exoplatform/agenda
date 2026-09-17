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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarSubscription;
import org.exoplatform.agenda.model.CalendarSubscriptionEvent;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.storage.CalendarSubscriptionStorage;
import org.exoplatform.agenda.util.CalendarAddressGuard;
import org.exoplatform.agenda.util.CalendarFeedException;
import org.exoplatform.agenda.util.CalendarFeedFetcher;
import org.exoplatform.agenda.util.CalendarFeedFetcher.FeedResponse;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.agenda.util.CalendarFeedParser;
import org.exoplatform.agenda.util.CalendarFeedParser.ImportedEvent;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.search.index.IndexingService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.security.codec.AbstractCodec;
import org.exoplatform.web.security.codec.CodecInitializer;

/**
 * Pins the rules of calendar subscriptions (EXO-90278): a link is read before
 * anything is stored and refused with a stable code, the URL is stored
 * encrypted, a link of this eXo is read in the process and refused when it
 * shows a calendar the user already sees, a refresh updates occurrences in
 * place, creates the new ones and removes the dropped ones, a failure keeps the
 * last good copy, a subscription is its owner's alone, and nothing imported
 * goes through a service that broadcasts.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AgendaCalendarSubscriptionServiceTest {

  private static final Instant                  NOW          = Instant.parse("2026-09-14T08:00:00Z");

  private static final String                   URL          = "https://feeds.example.org/holidays.ics";

  private static final long                     JOHN         = 1;

  private static final long                     MARY         = 2;

  private static final long                     SUBSCRIPTION = 11;

  private static final long                     CALENDAR     = 77;

  @Mock
  private CalendarSubscriptionStorage           storage;

  @Mock
  private AgendaCalendarService                 calendarService;

  @Mock
  private AgendaEventStorage                    eventStorage;

  @Mock
  private AgendaEventAttendeeStorage            attendeeStorage;

  @Mock
  private AgendaCalendarLinkService             linkService;

  @Mock
  private CalendarFeedFetcher                   fetcher;

  @Mock
  private IdentityManager                       identityManager;

  @Mock
  private SpaceService                          spaceService;

  @Mock
  private CodecInitializer                      codecInitializer;

  @Mock
  private AbstractCodec                         codec;

  @Mock
  private IndexingService                       indexingService;

  private final Map<Long, CalendarSubscription> rows         = new HashMap<>();

  private final List<CalendarSubscriptionEvent> eventRows    = new ArrayList<>();

  private final AtomicLong                      ids          = new AtomicLong(1000);

  private AgendaCalendarSubscriptionServiceImpl service;

  /**
   * Wires the service over mocks that behave like the storages they stand for.
   *
   * @throws Exception never
   */
  @BeforeEach
  void setUp() throws Exception {
    when(codecInitializer.getCodec()).thenReturn(codec);
    when(codec.encode(anyString())).thenAnswer(invocation -> "enc:" + invocation.getArgument(0));
    when(codec.decode(anyString())).thenAnswer(invocation -> {
      String value = invocation.getArgument(0);
      if (!value.startsWith("enc:")) {
        throw new IllegalStateException("another key");
      }
      return value.substring(4);
    });
    when(fetcher.getGuard()).thenReturn(new CalendarAddressGuard(false, Set.of(80, 443, 8080, 8443)));
    user(JOHN, "john", true);
    user(MARY, "mary", true);

    when(calendarService.createCalendar(any(Calendar.class), anyString())).thenAnswer(invocation -> {
      Calendar calendar = invocation.getArgument(0);
      calendar.setId(CALENDAR);
      return calendar;
    });
    when(calendarService.getCalendarById(CALENDAR)).thenAnswer(invocation -> subscribedCalendar());
    when(storage.create(any())).thenAnswer(invocation -> {
      CalendarSubscription subscription = invocation.getArgument(0);
      subscription.setId(SUBSCRIPTION);
      rows.put(SUBSCRIPTION, copy(subscription));
      return copy(subscription);
    });
    when(storage.getById(anyLong())).thenAnswer(invocation -> copy(rows.get((Long) invocation.getArgument(0))));
    when(storage.getEvents(anyLong())).thenAnswer(invocation -> new ArrayList<>(eventRows));
    when(storage.saveEvent(any())).thenAnswer(invocation -> {
      CalendarSubscriptionEvent row = invocation.getArgument(0);
      if (row.getId() == 0) {
        row.setId(ids.incrementAndGet());
      }
      eventRows.removeIf(existing -> existing.getId() == row.getId());
      eventRows.add(row);
      return row;
    });
    when(storage.claim(anyLong(), anyString(), any(), any())).thenReturn(true);
    when(storage.claimDue(anyLong(), anyString(), any(), any())).thenReturn(true);
    when(storage.recordSuccess(anyLong(), anyString(), anyString(), any(), any(), any(), any(), any(), anyBoolean(), any()))
                                                                                                                             .thenReturn(true);
    when(storage.recordFailure(anyLong(), anyString(), any(), anyString(), any())).thenReturn(true);
    when(storage.updateUrl(anyLong(), anyString(), anyString(), any())).thenReturn(true);
    when(eventStorage.createEvent(any())).thenAnswer(invocation -> {
      Event event = invocation.getArgument(0);
      event.setId(ids.incrementAndGet());
      return event;
    });
    when(eventStorage.updateEvent(any())).thenAnswer(invocation -> invocation.getArgument(0));

    service = new AgendaCalendarSubscriptionServiceImpl(storage,
                                                        calendarService,
                                                        eventStorage,
                                                        attendeeStorage,
                                                        linkService,
                                                        fetcher,
                                                        identityManager,
                                                        spaceService,
                                                        codecInitializer,
                                                        indexingService);
    service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /**
   * A refused or unreadable link is refused with its code before anything is
   * created or stored.
   *
   * @throws Exception never
   */
  @Test
  void aLinkIsReadBeforeAnythingIsStoredAndRefusedWithItsCode() throws Exception {
    for (String reason : List.of(CalendarFeedException.REFUSED_ADDRESS,
                                 CalendarFeedException.UNREACHABLE,
                                 CalendarFeedException.TIMEOUT,
                                 CalendarFeedException.TOO_LARGE,
                                 CalendarFeedException.UNRESOLVABLE)) {
      doThrow(new CalendarFeedException(reason)).when(fetcher).fetch(any(), any(), any());
      IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                                                      () -> service.createSubscription(URL, null, null, "john"));
      assertEquals("agenda.calendarSubscription." + reason, refused.getMessage());
    }
    doReturn(response("<html><body>Sign in</body></html>")).when(fetcher).fetch(any(), any(), any());
    assertEquals("agenda.calendarSubscription.notACalendar",
                 assertThrows(IllegalArgumentException.class, () -> service.createSubscription(URL, null, null, "john")).getMessage());
    assertEquals("agenda.calendarSubscription.schemeNotAllowed",
                 assertThrows(IllegalArgumentException.class,
                              () -> service.createSubscription("file:///etc/passwd", null, null, "john")).getMessage());

    verify(calendarService, never()).createCalendar(any(Calendar.class), anyString());
    verify(storage, never()).create(any());
  }

  /**
   * Checking a link answers the calendar's own name and stores nothing.
   *
   * @throws Exception never
   */
  @Test
  void checkingALinkAnswersItsNameAndStoresNothing() throws Exception {
    when(fetcher.fetch(any(), any(), any())).thenReturn(response(ics("X-WR-CALNAME:Holidays")));

    assertEquals("Holidays", service.checkUrl("webcal://feeds.example.org/holidays.ics", "john"));

    verify(fetcher).fetch(eq(URI.create(URL)), isNull(), isNull());
    verify(calendarService, never()).createCalendar(any(Calendar.class), anyString());
    verify(storage, never()).create(any());
  }

  /**
   * Subscribing creates a flagged calendar named after the feed, stores the URL
   * encrypted and keyed by a digest, imports every occurrence through the
   * storage, and records the outcome with the interval the feed advertises.
   *
   * @throws Exception never
   */
  @Test
  void subscribingStoresTheUrlEncryptedAndImportsTheOccurrences() throws Exception {
    when(fetcher.fetch(any(), any(), any())).thenReturn(response(ics("X-WR-CALNAME:Holidays",
                                                                     "REFRESH-INTERVAL;VALUE=DURATION:PT6H",
                                                                     vevent("a@test", "20261001T090000Z", "A", null),
                                                                     vevent("b@test", "20261002T090000Z", "B", "STATUS:TENTATIVE"))));

    CalendarSubscription subscription = service.createSubscription(" webcal://feeds.example.org/holidays.ics ", "", "", "john");

    ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
    verify(calendarService).createCalendar(calendar.capture(), eq("john"));
    assertEquals(JOHN, calendar.getValue().getOwnerId());
    assertEquals("Holidays", calendar.getValue().getName());
    assertTrue(calendar.getValue().isSubscription(), "the calendar is flagged as a subscription");

    ArgumentCaptor<CalendarSubscription> stored = ArgumentCaptor.forClass(CalendarSubscription.class);
    verify(storage).create(stored.capture());
    assertEquals("enc:" + URL, stored.getValue().getUrlEncrypted(), "the URL is stored encrypted, webcal read as https");
    assertEquals(64, stored.getValue().getUrlKey().length());
    assertFalse(stored.getValue().getUrlKey().contains("example"), "the key is a digest, not the URL");
    assertEquals(service.getNode(), stored.getValue().getClaimedBy(), "created claimed, so the job does not read it meanwhile");

    ArgumentCaptor<Event> events = ArgumentCaptor.forClass(Event.class);
    verify(eventStorage, times(2)).createEvent(events.capture());
    for (Event event : events.getAllValues()) {
      assertEquals(CALENDAR, event.getCalendarId());
      assertEquals(JOHN, event.getCreatorId());
      assertEquals(EventStatus.CONFIRMED, event.getStatus(), "never TENTATIVE: that is a date poll in agenda");
      assertFalse(event.isAllowAttendeeToUpdate());
      assertFalse(event.isAllowAttendeeToInvite());
      assertEquals(EventAvailability.FREE, event.getAvailability(), "a subscribed event never makes its owner busy");
      // This path writes through the storage, so nothing defaults the value for
      // it: stated here because a null would be a row the service never wrote
      // (EXO-90322)
      assertEquals(EventVisibility.DEFAULT,
                   event.getVisibility(),
                   "and is published like any other event of the subscriber's");
    }
    ArgumentCaptor<EventAttendee> attendees = ArgumentCaptor.forClass(EventAttendee.class);
    ArgumentCaptor<Long> attendedEvents = ArgumentCaptor.forClass(Long.class);
    verify(attendeeStorage, times(2)).saveEventAttendee(attendees.capture(), attendedEvents.capture());
    for (int i = 0; i < 2; i++) {
      assertEquals(JOHN, attendees.getAllValues().get(i).getIdentityId(), "the owner attends, so the default view lists the event");
      assertEquals(EventAttendeeResponse.ACCEPTED, attendees.getAllValues().get(i).getResponse(), "and it is never a pending invitation");
      assertEquals(events.getAllValues().get(i).getId(), attendedEvents.getAllValues().get(i));
    }
    assertEquals(2, eventRows.size());
    verify(indexingService, times(2)).reindex(anyString(), anyString());
    verify(storage).recordSuccess(eq(SUBSCRIPTION),
                                  eq(service.getNode()),
                                  eq(stored.getValue().getUrlKey()),
                                  isNull(),
                                  isNull(),
                                  anyString(),
                                  eq(360),
                                  eq(Date.from(NOW)),
                                  eq(false),
                                  eq(Date.from(NOW.plus(Duration.ofHours(6)))));

    assertEquals(URL, subscription.getUrl(), "its owner reads the URL in clear");
    assertEquals("Holidays", subscription.getName());
    assertNull(subscription.getUrlEncrypted(), "and never the stored secrets");
    assertNull(subscription.getUrlKey());
  }

  /**
   * A name and a colour given win over the feed's; a colour that is not
   * {@code #RRGGBB} and a name too long are refused.
   *
   * @throws Exception never
   */
  @Test
  void aGivenNameAndColourWinAndInvalidOnesAreRefused() throws Exception {
    when(fetcher.fetch(any(), any(), any())).thenReturn(response(ics("X-WR-CALNAME:Holidays")));

    service.createSubscription(URL, " Mine ", "#112233", "john");

    ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
    verify(calendarService).createCalendar(calendar.capture(), eq("john"));
    assertEquals("Mine", calendar.getValue().getName());
    assertEquals("#112233", calendar.getValue().getColor());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.INVALID_COLOR,
                 assertThrows(IllegalArgumentException.class, () -> service.createSubscription(URL, null, "red", "john")).getMessage());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.NAME_TOO_LONG,
                 assertThrows(IllegalArgumentException.class,
                              () -> service.createSubscription(URL, "n".repeat(201), null, "john")).getMessage());
  }

  /**
   * A user subscribes once to a link; losing an insert race removes the calendar
   * created for it.
   *
   * @throws Exception never
   */
  @Test
  void aUserSubscribesOnceToALink() throws Exception {
    when(storage.getByUrlKey(anyString())).thenReturn(new CalendarSubscription());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.ALREADY_SUBSCRIBED,
                 assertThrows(IllegalArgumentException.class, () -> service.createSubscription(URL, null, null, "john")).getMessage());
    verify(fetcher, never()).fetch(any(), any(), any());

    doReturn(null).when(storage).getByUrlKey(anyString());
    doReturn(response(ics())).when(fetcher).fetch(any(), any(), any());
    doReturn(null).when(storage).create(any());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.ALREADY_SUBSCRIBED,
                 assertThrows(IllegalArgumentException.class, () -> service.createSubscription(URL, null, null, "john")).getMessage());
    verify(calendarService).deleteCalendarById(CALENDAR);
  }

  /**
   * A link of this eXo publishing a calendar the user already sees is refused
   * without any read, saying which: their own calendar, or a space's they can
   * view.
   *
   * @throws Exception never
   */
  @Test
  void aLinkOfThisExoToACalendarTheUserSeesIsRefused() throws Exception {
    Calendar own = calendarOf(5, JOHN);
    when(calendarService.getCalendarById(5)).thenReturn(own);
    when(linkService.getFeedCalendarId("TOKEN")).thenReturn(5L);
    Identity spaceIdentity = new Identity(SpaceIdentityProvider.NAME, "team");
    spaceIdentity.setId("100");
    when(identityManager.getIdentity("100")).thenReturn(spaceIdentity);
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName("team")).thenReturn(space);
    when(spaceService.canViewSpace(space, "john")).thenReturn(true);
    when(calendarService.getCalendarById(6)).thenReturn(calendarOf(6, 100));
    when(linkService.getFeedCalendarId("SPACE")).thenReturn(6L);

    try (MockedStatic<CommonsUtils> commons = mockStatic(CommonsUtils.class)) {
      commons.when(CommonsUtils::getCurrentDomain).thenReturn("http://localhost:8080");
      assertEquals("agenda.calendarSubscription.ownCalendar",
                   assertThrows(IllegalArgumentException.class,
                                () -> service.createSubscription("http://localhost:8080/agenda/rest/ical/TOKEN.ics",
                                                                 null,
                                                                 null,
                                                                 "john")).getMessage());
      assertEquals("agenda.calendarSubscription.alreadyInAgenda",
                   assertThrows(IllegalArgumentException.class,
                                () -> service.createSubscription("http://localhost:8080/agenda/rest/ical/SPACE.ics",
                                                                 null,
                                                                 null,
                                                                 "john")).getMessage());
    }
    verify(fetcher, never()).fetch(any(), any(), any());
    verify(linkService, never()).getCalendarFeed(anyString());
  }

  /**
   * A colleague's link of this eXo is read in the process, never over the
   * network; a link of another port or path is an ordinary one; a dead link of
   * this eXo is reported as such.
   *
   * @throws Exception never
   */
  @Test
  void aColleaguesLinkOfThisExoIsReadInTheProcess() throws Exception {
    when(calendarService.getCalendarById(5)).thenReturn(calendarOf(5, MARY));
    when(linkService.getFeedCalendarId("TOKEN")).thenReturn(5L);
    when(linkService.getCalendarFeed("TOKEN")).thenReturn(ics("X-WR-CALNAME:Mary", vevent("m@test", "20261001T090000Z", "M", null)));
    when(linkService.getFeedCalendarId("DEAD")).thenThrow(new ObjectNotFoundException("nothing"));

    try (MockedStatic<CommonsUtils> commons = mockStatic(CommonsUtils.class)) {
      commons.when(CommonsUtils::getCurrentDomain).thenReturn("http://localhost:8080/");
      service.createSubscription("http://LOCALHOST:8080/agenda/rest/ical/TOKEN.ics", null, null, "john");
      ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
      verify(calendarService).createCalendar(calendar.capture(), eq("john"));
      assertEquals("Mary", calendar.getValue().getName(), "the calendar is named after the document read in the process");
      verify(fetcher, never()).fetch(any(), any(), any());
      verify(eventStorage).createEvent(any());

      assertNull(service.ownFeedToken(URI.create("http://localhost:9090/agenda/rest/ical/TOKEN.ics")), "another port");
      assertNull(service.ownFeedToken(URI.create("http://localhost:8080/other/rest/ical/TOKEN.ics")), "another path");
      assertNull(service.ownFeedToken(URI.create("http://evil.example.org:8080/agenda/rest/ical/TOKEN.ics")), "another host");
      assertNull(service.ownFeedToken(URI.create("http://localhost:8080/agenda/rest/ical/a/b.ics")), "no token");
      assertEquals("DEAD", service.ownFeedToken(URI.create("http://localhost:8080/agenda/rest/ical/DEAD.ics")));
      assertEquals("agenda.calendarSubscription.linkNotFound",
                   assertThrows(IllegalArgumentException.class,
                                () -> service.checkUrl("http://localhost:8080/agenda/rest/ical/DEAD.ics", "john")).getMessage());
    }
  }

  /**
   * A refresh rewrites a changed occurrence in its own event, creates a new one,
   * removes the one the feed dropped, and leaves an unchanged one alone.
   *
   * @throws Exception never
   */
  /**
   * The kernel persistence context and IDM request transaction are cycled after
   * every subscription of a batch -- success, no-op or caught failure alike --
   * so the scope stays one subscription wide instead of accumulating across the
   * whole batch (up to 20 sequential feed reads of up to 30 s each). The idiom
   * this reuses is {@code AgendaCalendarStorage.deleteCalendarById}'s own.
   * Mutation-verified: with the cycling removed, this call count drops to zero.
   *
   * @throws Exception never
   */
  @Test
  void theScopeIsCycledAfterEverySubscriptionOfTheBatch() throws Exception {
    subscriptionRow("enc:" + URL, "same-digest", 0);
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION, 99L));
    doReturn(new FeedResponse(true, null, "\"v1\"", null)).when(fetcher).fetch(any(), any(), any());

    try (MockedStatic<RequestLifeCycle> lifecycle = mockStatic(RequestLifeCycle.class)) {
      service.refreshDueSubscriptions(10);

      // SUBSCRIPTION goes through the full not-modified refresh; 99 has no row
      // (storage.getById answers null) and is skipped before refreshClaimed --
      // both still cycle the scope once each.
      lifecycle.verify(RequestLifeCycle::restartTransaction, times(2));
    }
  }

  @Test
  void aRefreshUpdatesInPlaceCreatesNewAndRemovesDropped() throws Exception {
    byte[] body = ics(vevent("a@test", "20261001T090000Z", "A changed", null),
                      vevent("b@test", "20261002T090000Z", "B", null),
                      vevent("d@test", "20261004T090000Z", "D", null)).getBytes(StandardCharsets.UTF_8);
    Map<String, ImportedEvent> imported = new HashMap<>();
    CalendarFeedParser.parse(body, NOW.minus(Duration.ofDays(30)), NOW.plus(Duration.ofDays(365)), 2000)
                      .events()
                      .forEach(event -> imported.put(event.key(), event));
    subscriptionRow("enc:" + URL, "old-digest", 0);
    eventRows.add(new CalendarSubscriptionEvent(1, SUBSCRIPTION, 101, key("a@test|"), "stale"));
    eventRows.add(new CalendarSubscriptionEvent(2, SUBSCRIPTION, 102, key("b@test|"), imported.get("b@test|").contentHash()));
    eventRows.add(new CalendarSubscriptionEvent(3, SUBSCRIPTION, 103, key("c@test|"), "gone"));
    when(eventStorage.getEventById(101)).thenReturn(storedEvent(101));
    when(eventStorage.getEventById(103)).thenReturn(storedEvent(103));
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));
    when(fetcher.fetch(any(), any(), any())).thenReturn(new FeedResponse(false, body, "\"v2\"", null));

    assertEquals(1, service.refreshDueSubscriptions(10));

    ArgumentCaptor<Event> updated = ArgumentCaptor.forClass(Event.class);
    verify(eventStorage).updateEvent(updated.capture());
    assertEquals(101, updated.getValue().getId(), "the changed occurrence keeps its event");
    assertEquals("A changed", updated.getValue().getSummary());
    ArgumentCaptor<Event> created = ArgumentCaptor.forClass(Event.class);
    verify(eventStorage).createEvent(created.capture());
    assertEquals("D", created.getValue().getSummary());
    verify(eventStorage).deleteEventById(103);
    verify(attendeeStorage, times(1)).saveEventAttendee(any(), eq(created.getValue().getId()));
    verify(eventStorage, never()).getEventById(102);
    verify(storage).deleteEvent(3);
    verify(indexingService).unindex(anyString(), eq("103"));
    verify(storage).recordSuccess(eq(SUBSCRIPTION), eq(service.getNode()), eq("url-key"), eq("\"v2\""), isNull(),
                                  eq(sha256(body)), isNull(), eq(Date.from(NOW)), eq(false),
                                  eq(Date.from(NOW.plus(AgendaCalendarSubscriptionServiceImpl.DEFAULT_REFRESH))));
  }

  /**
   * A malformed feed records its failure, keeps every imported occurrence, and is
   * retried later.
   *
   * @throws Exception never
   */
  @Test
  void aMalformedFeedKeepsTheLastGoodCopy() throws Exception {
    subscriptionRow("enc:" + URL, "old-digest", 0);
    eventRows.add(new CalendarSubscriptionEvent(1, SUBSCRIPTION, 101, key("a@test|"), "hash"));
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));
    when(fetcher.fetch(any(), any(), any())).thenReturn(response("BEGIN:VCALENDAR\r\nBEGIN:VEVENT\r\nDTSTART:2026"));

    service.refreshDueSubscriptions(10);

    verify(storage).recordFailure(eq(SUBSCRIPTION), eq(service.getNode()), eq(Date.from(NOW)),
                                  eq("agenda.calendarSubscription.malformedCalendar"),
                                  eq(Date.from(NOW.plus(AgendaCalendarSubscriptionServiceImpl.RETRY_AFTER_FAILURE))));
    verify(eventStorage, never()).createEvent(any());
    verify(eventStorage, never()).updateEvent(any());
    verify(eventStorage, never()).deleteEventById(anyLong());
    verify(storage, never()).deleteEvent(anyLong());
    verify(storage, never()).recordSuccess(anyLong(), anyString(), anyString(), any(), any(), any(), any(), any(), anyBoolean(), any());
  }

  /**
   * A feed answering that nothing changed, or the very body already imported,
   * rewrites nothing and records the success.
   *
   * @throws Exception never
   */
  @Test
  void anUnchangedFeedRewritesNothing() throws Exception {
    byte[] body = ics(vevent("a@test", "20261001T090000Z", "A", null)).getBytes(StandardCharsets.UTF_8);
    subscriptionRow("enc:" + URL, sha256(body), 0);
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));

    doReturn(new FeedResponse(true, null, "\"v1\"", null)).when(fetcher).fetch(any(), any(), any());
    service.refreshDueSubscriptions(10);
    doReturn(new FeedResponse(false, body, "\"v1\"", null)).when(fetcher).fetch(any(), any(), any());
    service.refreshDueSubscriptions(10);

    verify(fetcher, times(2)).fetch(eq(URI.create(URL)), eq("\"v1\""), isNull());
    verify(eventStorage, never()).createEvent(any());
    verify(eventStorage, never()).updateEvent(any());
    verify(storage, never()).getEvents(anyLong());
    verify(storage, times(2)).recordSuccess(eq(SUBSCRIPTION), anyString(), anyString(), any(), any(), eq(sha256(body)), any(),
                                            any(), anyBoolean(), any());
  }

  /**
   * A refresh asked a moment ago is refused, and so is one while another runs.
   *
   * @throws Exception never
   */
  @Test
  void aRefreshAskedTooSoonOrWhileRunningIsRefused() throws Exception {
    subscriptionRow("enc:" + URL, null, NOW.minus(Duration.ofMinutes(1)).toEpochMilli());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.REFRESH_TOO_SOON,
                 assertThrows(IllegalStateException.class, () -> service.refreshSubscription(SUBSCRIPTION, "john")).getMessage());
    verify(storage, never()).claim(anyLong(), anyString(), any(), any());

    subscriptionRow("enc:" + URL, null, NOW.minus(Duration.ofMinutes(6)).toEpochMilli());
    doReturn(false).when(storage).claim(anyLong(), anyString(), any(), any());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.REFRESH_IN_PROGRESS,
                 assertThrows(IllegalStateException.class, () -> service.refreshSubscription(SUBSCRIPTION, "john")).getMessage());
    verify(fetcher, never()).fetch(any(), any(), any());
  }

  /**
   * A refresh asked by the owner reads the link and records a failure on the
   * subscription it returns rather than throwing it.
   *
   * @throws Exception never
   */
  @Test
  void aRefreshNowRecordsItsFailure() throws Exception {
    subscriptionRow("enc:" + URL, null, 0);
    when(fetcher.fetch(any(), any(), any())).thenThrow(new CalendarFeedException(CalendarFeedException.UNREACHABLE));

    service.refreshSubscription(SUBSCRIPTION, "john");

    verify(storage).claim(eq(SUBSCRIPTION), eq(service.getNode()), eq(Date.from(NOW)), any());
    verify(storage).recordFailure(eq(SUBSCRIPTION), anyString(), any(), eq("agenda.calendarSubscription.unreachable"), any());
  }

  /**
   * A subscription is its owner's alone: another user reads, changes, refreshes
   * and removes nothing, and a missing one is not found.
   *
   * @throws Exception never
   */
  @Test
  void onlyTheOwnerReadsOrChangesASubscription() throws Exception {
    subscriptionRow("enc:" + URL, null, 0);

    assertThrows(IllegalAccessException.class, () -> service.getSubscription(SUBSCRIPTION, "mary"));
    assertThrows(IllegalAccessException.class, () -> service.updateSubscription(SUBSCRIPTION, URL, "x", null, "mary"));
    assertThrows(IllegalAccessException.class, () -> service.refreshSubscription(SUBSCRIPTION, "mary"));
    assertThrows(IllegalAccessException.class, () -> service.deleteSubscription(SUBSCRIPTION, "mary"));
    assertThrows(ObjectNotFoundException.class, () -> service.getSubscription(99, "john"));
    assertTrue(service.getSubscriptions("mary").isEmpty());

    verify(storage, never()).delete(anyLong());
    verify(storage, never()).claim(anyLong(), anyString(), any(), any());
    verify(calendarService, never()).updateCalendar(any(Calendar.class));
    verify(fetcher, never()).fetch(any(), any(), any());
  }

  /**
   * Unsubscribing removes the subscription, its calendar with its events, and
   * the events from the search index.
   *
   * @throws Exception never
   */
  @Test
  void unsubscribingRemovesTheCalendarAndItsEvents() throws Exception {
    subscriptionRow("enc:" + URL, null, 0);
    eventRows.add(new CalendarSubscriptionEvent(1, SUBSCRIPTION, 101, key("a@test|"), "h"));
    eventRows.add(new CalendarSubscriptionEvent(2, SUBSCRIPTION, 102, key("b@test|"), "h"));

    service.deleteSubscription(SUBSCRIPTION, "john");

    verify(storage).delete(SUBSCRIPTION);
    verify(calendarService).deleteCalendarById(CALENDAR);
    verify(indexingService).unindex(anyString(), eq("101"));
    verify(indexingService).unindex(anyString(), eq("102"));
  }

  /**
   * The calendar is deleted before the subscription row, not after: the mirror of
   * {@code createSubscription}'s own rollback order. Deleting the row first can
   * leave a calendar with no row pointing at it if the calendar deletion then
   * fails -- an orphan no listing shows and no user-facing path can remove.
   * Mutation-verified: with the two calls swapped back, this order assertion fails.
   *
   * @throws Exception never
   */
  @Test
  void unsubscribingDeletesTheCalendarBeforeTheSubscriptionRow() throws Exception {
    subscriptionRow("enc:" + URL, null, 0);

    service.deleteSubscription(SUBSCRIPTION, "john");

    InOrder order = inOrder(calendarService, storage);
    order.verify(calendarService).deleteCalendarById(CALENDAR);
    order.verify(storage).delete(SUBSCRIPTION);
  }

  /**
   * The counterfactual the ordering fix is for: a subscription-row delete that
   * fails still leaves no orphan, because the calendar was already deleted
   * first. Under the old order this failure would leave the row gone and the
   * calendar behind, unreachable.
   *
   * @throws Exception never
   */
  @Test
  void aFailedRowDeleteStillLeavesNoOrphanCalendar() throws Exception {
    subscriptionRow("enc:" + URL, null, 0);
    doThrow(new RuntimeException("db down")).when(storage).delete(SUBSCRIPTION);

    assertThrows(RuntimeException.class, () -> service.deleteSubscription(SUBSCRIPTION, "john"));

    verify(calendarService).deleteCalendarById(CALENDAR);
  }

  /**
   * {@code deleteCalendarSubscription} documents its contract as "a calendar
   * that was deleted by another path" -- with the calendar still there, it
   * refuses rather than stranding the calendar the same way the ordering fix
   * above closes. Its only caller today is the clean-up listener, which never
   * reaches this branch because it fires after the deletion; this pins the
   * contract for whichever caller reaches it next.
   */
  @Test
  void deleteCalendarSubscriptionRefusesWhileTheCalendarStillExists() {
    subscriptionRow("enc:" + URL, null, 0);
    when(storage.getByCalendarId(CALENDAR)).thenReturn(rows.get(SUBSCRIPTION));

    service.deleteCalendarSubscription(CALENDAR);

    verify(storage, never()).delete(anyLong());
  }

  /**
   * The documented case: the calendar is already gone, so the row is removed.
   */
  @Test
  void deleteCalendarSubscriptionRemovesTheRowOnceTheCalendarIsGone() {
    subscriptionRow("enc:" + URL, null, 0);
    when(storage.getByCalendarId(CALENDAR)).thenReturn(rows.get(SUBSCRIPTION));
    when(calendarService.getCalendarById(CALENDAR)).thenReturn(null);

    service.deleteCalendarSubscription(CALENDAR);

    verify(storage).delete(SUBSCRIPTION);
  }

  /**
   * The subscription of a disabled owner is not read, and one whose URL no
   * longer decrypts stops being read and says so.
   *
   * @throws Exception never
   */
  @Test
  void aDisabledOwnerOrAnUnreadableUrlStopsTheRefresh() throws Exception {
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));
    subscriptionRow("enc:" + URL, null, 0);
    user(JOHN, "john", false);
    service.refreshDueSubscriptions(10);
    verify(storage).recordFailure(eq(SUBSCRIPTION), anyString(), any(), eq(AgendaCalendarSubscriptionServiceImpl.USER_DISABLED), any());

    user(JOHN, "john", true);
    subscriptionRow("from-another-key", null, 0);
    service.refreshDueSubscriptions(10);
    verify(storage).recordFailure(eq(SUBSCRIPTION), anyString(), any(), eq(AgendaCalendarSubscriptionServiceImpl.URL_UNREADABLE), any());
    verify(fetcher, never()).fetch(any(), any(), any());

    CalendarSubscription read = service.getSubscription(SUBSCRIPTION, "john");
    assertNull(read.getUrl());
    assertEquals(AgendaCalendarSubscriptionServiceImpl.URL_UNREADABLE, read.getLastError());
  }

  /**
   * A due subscription another node claimed is left to that node.
   *
   * @throws Exception never
   */
  @Test
  void aSubscriptionClaimedByAnotherNodeIsNotRead() throws Exception {
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));
    doReturn(false).when(storage).claimDue(anyLong(), anyString(), any(), any());

    assertEquals(0, service.refreshDueSubscriptions(10));

    verify(storage, never()).getById(anyLong());
    verify(fetcher, never()).fetch(any(), any(), any());
  }

  /**
   * A new link is read before it replaces the old one, and its answer is
   * imported without a second read; a refused new link changes nothing.
   *
   * @throws Exception never
   */
  @Test
  void aNewLinkIsReadBeforeItReplacesTheOldOne() throws Exception {
    subscriptionRow("enc:" + URL, "digest", 0);
    String newUrl = "https://feeds.example.org/other.ics";
    when(fetcher.fetch(eq(URI.create(newUrl)), any(), any())).thenReturn(response(ics(vevent("n@test", "20261001T090000Z", "N", null))));

    service.updateSubscription(SUBSCRIPTION, newUrl, null, null, "john");

    verify(fetcher, times(1)).fetch(any(), any(), any());
    verify(storage).updateUrl(eq(SUBSCRIPTION), eq("enc:" + newUrl), anyString(), eq(Date.from(NOW)));
    verify(eventStorage).createEvent(any());

    doThrow(new CalendarFeedException(CalendarFeedException.REFUSED_ADDRESS)).when(fetcher)
                                                                              .fetch(eq(URI.create("https://feeds.example.org/refused.ics")), any(), any());
    assertEquals("agenda.calendarSubscription.refusedAddress",
                 assertThrows(IllegalArgumentException.class,
                              () -> service.updateSubscription(SUBSCRIPTION,
                                                               "https://feeds.example.org/refused.ics",
                                                               "renamed",
                                                               null,
                                                               "john")).getMessage());
    verify(storage, times(1)).updateUrl(anyLong(), anyString(), anyString(), any());
    verify(calendarService, never()).updateCalendar(any(Calendar.class));
  }

  /**
   * A new name or colour is written on the calendar.
   *
   * @throws Exception never
   */
  @Test
  void aNewNameOrColourIsWrittenOnTheCalendar() throws Exception {
    subscriptionRow("enc:" + URL, "digest", 0);

    service.updateSubscription(SUBSCRIPTION, null, "Renamed", "#445566", "john");

    ArgumentCaptor<Calendar> calendar = ArgumentCaptor.forClass(Calendar.class);
    verify(calendarService).updateCalendar(calendar.capture());
    assertEquals("Renamed", calendar.getValue().getName());
    assertEquals("#445566", calendar.getValue().getColor());
    verify(fetcher, never()).fetch(any(), any(), any());
  }

  /**
   * An outcome that no longer matches the row — its URL was changed during the
   * read — releases this node's claim instead of leaving it held until stale.
   *
   * @throws Exception never
   */
  @Test
  void aLostOutcomeReleasesTheClaim() throws Exception {
    subscriptionRow("enc:" + URL, null, 0);
    when(storage.getDueIds(any(), any(), anyInt())).thenReturn(List.of(SUBSCRIPTION));
    doReturn(false).when(storage).recordSuccess(anyLong(), anyString(), anyString(), any(), any(), any(), any(), any(), anyBoolean(), any());
    doReturn(new FeedResponse(true, null, null, null)).when(fetcher).fetch(any(), any(), any());

    service.refreshDueSubscriptions(10);
    verify(storage).release(SUBSCRIPTION, service.getNode());

    doReturn(false).when(storage).recordFailure(anyLong(), anyString(), any(), anyString(), any());
    doThrow(new CalendarFeedException(CalendarFeedException.TIMEOUT)).when(fetcher).fetch(any(), any(), any());
    service.refreshDueSubscriptions(10);
    verify(storage, times(2)).release(SUBSCRIPTION, service.getNode());
  }

  /**
   * A user reads at most two links at a time: a third read asked while two are
   * running is refused without reaching the network.
   *
   * @throws Exception never
   */
  @Test
  void aUserReadsAtMostTwoLinksAtATime() throws Exception {
    List<String> nested = new ArrayList<>();
    doAnswer(invocation -> {
      if (nested.isEmpty()) {
        nested.add("second");
        return response(ics("X-WR-CALNAME:" + service.checkUrl(URL, "john")));
      }
      if (nested.size() == 1) {
        nested.add("third");
        try {
          service.checkUrl(URL, "john");
          nested.add("third was read");
        } catch (IllegalStateException e) {
          nested.add(e.getMessage());
        }
      }
      return response(ics("X-WR-CALNAME:Inner"));
    }).when(fetcher).fetch(any(), any(), any());

    service.checkUrl(URL, "john");

    assertEquals(List.of("second", "third", AgendaCalendarSubscriptionServiceImpl.TOO_MANY_READS), nested);
    verify(fetcher, times(2)).fetch(any(), any(), any());
    doReturn(response(ics("X-WR-CALNAME:Again"))).when(fetcher).fetch(any(), any(), any());
    assertEquals("Again", service.checkUrl(URL, "john"), "the permits are given back once the reads end");
    assertEquals("Again", service.checkUrl(URL, "mary"), "and they are counted per user");
  }

  /**
   * A calendar created for a subscription that cannot be stored is removed, and
   * a first import that fails leaves a subscription carrying its failure rather
   * than an error answer.
   *
   * @throws Exception never
   */
  @Test
  void aSubscriptionThatCannotBeStoredLeavesNoCalendarAndAFailedImportKeepsTheSubscription() throws Exception {
    doReturn(response(ics(vevent("a@test", "20261001T090000Z", "A", null)))).when(fetcher).fetch(any(), any(), any());
    doThrow(new IllegalStateException("codec gone")).when(codec).encode(anyString());
    assertThrows(IllegalStateException.class, () -> service.createSubscription(URL, null, null, "john"));
    verify(calendarService).deleteCalendarById(CALENDAR);
    verify(storage, never()).create(any());

    doAnswer(invocation -> "enc:" + invocation.getArgument(0)).when(codec).encode(anyString());
    doThrow(new IllegalStateException("database gone")).when(eventStorage).createEvent(any());
    CalendarSubscription subscription = service.createSubscription(URL, null, null, "john");
    assertEquals(SUBSCRIPTION, subscription.getId());
    verify(storage).recordFailure(eq(SUBSCRIPTION), anyString(), any(), eq(AgendaCalendarSubscriptionServiceImpl.REFRESH_FAILED), any());
    verify(calendarService, times(1)).deleteCalendarById(CALENDAR);
  }

  /**
   * The interval a feed advertises is honoured within one hour and one day.
   */
  @Test
  void theAdvertisedRefreshIntervalIsBounded() {
    assertEquals(1440, AgendaCalendarSubscriptionServiceImpl.refreshMinutes(Duration.ofDays(7)));
    assertEquals(60, AgendaCalendarSubscriptionServiceImpl.refreshMinutes(Duration.ofMinutes(10)));
    assertEquals(360, AgendaCalendarSubscriptionServiceImpl.refreshMinutes(Duration.ofHours(6)));
    assertNull(AgendaCalendarSubscriptionServiceImpl.refreshMinutes(null));
  }

  /**
   * Nothing imported can send an invitation or a notification, compute a
   * reminder or reach a connector's listeners: the service holds no service that
   * broadcasts an event change, and writes through the event storage.
   */
  @Test
  void importedEventsReachNoBroadcastingService() {
    Set<Class<?>> dependencies = Set.of(AgendaCalendarSubscriptionServiceImpl.class.getConstructors()[0].getParameterTypes());
    for (Class<?> broadcasting : List.of(AgendaEventService.class,
                                         AgendaEventAttendeeService.class,
                                         AgendaEventReminderService.class,
                                         AgendaRemoteEventService.class,
                                         ListenerService.class)) {
      assertFalse(dependencies.contains(broadcasting), "must not depend on " + broadcasting.getSimpleName());
    }
    assertTrue(dependencies.contains(AgendaEventStorage.class));
    assertTrue(Arrays.stream(AgendaCalendarSubscriptionServiceImpl.class.getDeclaredFields())
                     .noneMatch(field -> field.getType().equals(AgendaEventService.class)));
  }

  /**
   * Registers a user identity.
   *
   * @param id identity identifier
   * @param username user name
   * @param enabled whether the account is enabled
   */
  private void user(long id, String username, boolean enabled) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    identity.setEnable(enabled);
    when(identityManager.getIdentity(String.valueOf(id))).thenReturn(identity);
    when(identityManager.getOrCreateUserIdentity(username)).thenReturn(identity);
  }

  /**
   * Stores John's subscription row.
   *
   * @param urlEncrypted the stored URL
   * @param contentHash the digest of the last import
   * @param lastAttemptDate when a refresh was last attempted
   */
  private void subscriptionRow(String urlEncrypted, String contentHash, long lastAttemptDate) {
    CalendarSubscription subscription = new CalendarSubscription();
    subscription.setId(SUBSCRIPTION);
    subscription.setCalendarId(CALENDAR);
    subscription.setUserIdentityId(JOHN);
    subscription.setUrlEncrypted(urlEncrypted);
    subscription.setUrlKey("url-key");
    subscription.setContentHash(contentHash);
    subscription.setEtag("\"v1\"");
    subscription.setLastAttemptDate(lastAttemptDate);
    subscription.setNextRefreshDate(NOW.toEpochMilli());
    rows.put(SUBSCRIPTION, subscription);
  }

  /**
   * A copy of a subscription, as each storage read gives a new one.
   *
   * @param source the subscription, may be null
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
    copy.setUrlEncrypted(source.getUrlEncrypted());
    copy.setUrlKey(source.getUrlKey());
    copy.setEtag(source.getEtag());
    copy.setLastModified(source.getLastModified());
    copy.setContentHash(source.getContentHash());
    copy.setRefreshMinutes(source.getRefreshMinutes());
    copy.setLastAttemptDate(source.getLastAttemptDate());
    copy.setLastSuccessDate(source.getLastSuccessDate());
    copy.setLastError(source.getLastError());
    copy.setNextRefreshDate(source.getNextRefreshDate());
    copy.setClaimedBy(source.getClaimedBy());
    copy.setCreatedDate(source.getCreatedDate());
    return copy;
  }

  /**
   * The calendar of the subscription.
   *
   * @return the calendar
   */
  private static Calendar subscribedCalendar() {
    Calendar calendar = calendarOf(CALENDAR, JOHN);
    calendar.setName("Holidays");
    calendar.setColor("#08a554");
    calendar.setSubscription(true);
    return calendar;
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
   * An event already imported into the subscription's calendar.
   *
   * @param id identifier
   * @return the event
   */
  private static Event storedEvent(long id) {
    Event event = new Event();
    event.setId(id);
    event.setCalendarId(CALENDAR);
    event.setCreatorId(JOHN);
    return event;
  }

  /**
   * The stored key of an occurrence of the test subscription.
   *
   * @param importedKey the parser's key
   * @return the digest the service stores
   */
  private static String key(String importedKey) {
    return sha256((SUBSCRIPTION + "|" + importedKey).getBytes(StandardCharsets.UTF_8));
  }

  /**
   * SHA-256 of bytes.
   *
   * @param bytes the bytes
   * @return lowercase hexadecimal digest
   */
  private static String sha256(byte[] bytes) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
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
    return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//test//EN\r\n" + String.join("\r\n", lines)
        + (lines.length == 0 ? "" : "\r\n") + "END:VCALENDAR\r\n";
  }

  /**
   * An event of one hour.
   *
   * @param uid its UID
   * @param start its UTC start, basic format
   * @param summary its title
   * @param extra an extra property line, or null
   * @return the lines of the event
   */
  private static String vevent(String uid, String start, String summary, String extra) {
    String end = start.substring(0, 9) + String.format("%02d", Integer.parseInt(start.substring(9, 11)) + 1) + start.substring(11);
    return String.join("\r\n",
                       "BEGIN:VEVENT",
                       "UID:" + uid,
                       "DTSTART:" + start,
                       "DTEND:" + end,
                       "SUMMARY:" + summary,
                       extra == null ? "X-NONE:1" : extra,
                       "END:VEVENT");
  }

}
