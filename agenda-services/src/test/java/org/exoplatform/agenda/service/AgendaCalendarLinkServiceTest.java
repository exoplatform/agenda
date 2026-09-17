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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.UndeclaredThrowableException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.crypto.BadPaddingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.CalendarLink;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.storage.CalendarLinkStorage;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.security.codec.AbstractCodec;
import org.exoplatform.web.security.codec.CodecInitializer;

/**
 * Pins every rule of calendar links, on the service that holds them: who
 * manages a link, what its token is, who sees it again, and when it stops
 * answering.
 * <p>
 * The storage is an in-memory stand-in rather than a mock, so a reset really
 * replaces the digest a later fetch looks up; the ACL runs through the real
 * {@code Utils} checks over mocked identity and space services; the platform
 * codec is a reversible stand-in whose key can be "replaced".
 */
class AgendaCalendarLinkServiceTest {

  private static final long          OWNER          = 1;

  private static final long          OTHER_USER     = 2;

  private static final long          MANAGER        = 3;

  private static final long          SECOND_MANAGER = 4;

  private static final long          MEMBER         = 5;

  /** A platform administrator: a super-manager of every space, a manager of none. */
  private static final long          SUPER_MANAGER  = 6;

  /** Holds the manager role of the space without being one of its members. */
  private static final long          ROLE_ONLY      = 7;

  private static final long          SPACE          = 100;

  private static final long          PERSONAL_CAL   = 10;

  private static final long          SPACE_CAL      = 20;

  private static final long          OTHER_CAL      = 30;

  private static final Instant       NOW            = Instant.parse("2026-09-14T10:00:00Z");

  private final Map<String, Identity> identities    = new HashMap<>();

  private final Set<String>          managers       = new HashSet<>();

  private final Set<String>          members        = new HashSet<>();

  /** Who social's canManageSpace also admits, on top of the managers. */
  private final Set<String>          superManagers  = new HashSet<>();

  /** Who the platform lists as a manager of the space, which may lag behind the rights check. */
  private final Set<String>          listedManagers = new HashSet<>();

  private InMemoryLinkStorage        storage;

  private AgendaCalendarService      calendarService;

  private AgendaEventService         eventService;

  private StandInCodec               codec;

  private AgendaCalendarLinkServiceImpl service;

  private Calendar                   deletedCalendar;

  /**
   * Builds two calendars — a personal one owned by {@code owner}, a space one
   * managed by {@code manager} and {@code manager2} with {@code member} as a
   * plain member — and the service over them.
   *
   * @throws Exception never, the mocked codec and event read declare it
   */
  @BeforeEach
  void setUp() throws Exception {
    user(OWNER, "owner");
    user(OTHER_USER, "other");
    user(MANAGER, "manager");
    user(SECOND_MANAGER, "manager2");
    user(MEMBER, "member");
    user(SUPER_MANAGER, "admin");
    user(ROLE_ONLY, "roleonly");
    Identity spaceIdentity = new Identity(SpaceIdentityProvider.NAME, "team");
    spaceIdentity.setId(String.valueOf(SPACE));
    identities.put(String.valueOf(SPACE), spaceIdentity);
    managers.addAll(List.of("manager", "manager2"));
    members.addAll(List.of("manager", "manager2", "member"));
    superManagers.add("admin");
    listedManagers.addAll(managers);

    IdentityManager identityManager = mock(IdentityManager.class);
    when(identityManager.getIdentity(anyString())).thenAnswer(invocation -> identities.get(invocation.getArgument(0)));
    when(identityManager.getOrCreateUserIdentity(anyString())).thenAnswer(invocation -> identities.values()
                                                                                                   .stream()
                                                                                                   .filter(Identity::isUser)
                                                                                                   .filter(identity -> identity.getRemoteId()
                                                                                                                               .equals(invocation.getArgument(0)))
                                                                                                   .findFirst()
                                                                                                   .orElse(null));
    Space space = new Space();
    space.setPrettyName("team");
    SpaceService spaceService = mock(SpaceService.class);
    when(spaceService.getSpaceByPrettyName("team")).thenReturn(space);
    // canManageSpace as social computes it — (member and manager) or super-manager —
    // so that a check going back through it would let the administrator in.
    when(spaceService.canManageSpace(eq(space), anyString())).thenAnswer(invocation -> {
      String username = invocation.getArgument(1);
      return managers.contains(username) && members.contains(username) || superManagers.contains(username);
    });
    when(spaceService.isManager(eq(space), anyString())).thenAnswer(invocation -> managers.contains(invocation.getArgument(1)));
    when(spaceService.isMember(eq(space), anyString())).thenAnswer(invocation -> members.contains(invocation.getArgument(1)));
    when(spaceService.canViewSpace(eq(space), anyString())).thenAnswer(invocation -> members.contains(invocation.getArgument(1)));
    when(spaceService.getManagerSpaces(anyString())).thenAnswer(invocation -> spaces(listedManagers.contains(invocation.getArgument(0)) ? List.of(space)
                                                                                                                                   : List.of()));
    when(identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, "team")).thenReturn(spaceIdentity);

    calendarService = mock(AgendaCalendarService.class);
    when(calendarService.getCalendarById(anyLong())).thenAnswer(invocation -> {
      long id = invocation.getArgument(0);
      if (deletedCalendar != null && deletedCalendar.getId() == id) {
        return deletedCalendar;
      }
      return id == PERSONAL_CAL ? calendar(PERSONAL_CAL, OWNER)
                                : id == SPACE_CAL ? calendar(SPACE_CAL, SPACE) : id == OTHER_CAL ? calendar(OTHER_CAL, OTHER_USER) : null;
    });
    eventService = mock(AgendaEventService.class);
    when(eventService.getEvents(any(), any(), anyLong())).thenReturn(Collections.emptyList());

    codec = new StandInCodec();
    CodecInitializer codecInitializer = mock(CodecInitializer.class);
    when(codecInitializer.getCodec()).thenReturn(codec);

    storage = new InMemoryLinkStorage(Map.of(PERSONAL_CAL, OWNER, SPACE_CAL, SPACE, OTHER_CAL, OTHER_USER));
    service = new AgendaCalendarLinkServiceImpl(storage, calendarService, eventService, identityManager, spaceService, codecInitializer);
    service.setClock(Clock.fixed(NOW, ZoneOffset.UTC));
  }

  /**
   * A token is 32 random bytes in base64url, a new one each time, and it is
   * never stored in clear: once as its SHA-256 digest, once encrypted by the
   * platform codec.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void theTokenIsARandom256BitSecretStoredOnlyAsDigestAndEncryptedCopy() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");

    assertTrue(token.matches("^[A-Za-z0-9_-]{43}$"), "base64url without padding: " + token);
    assertEquals(32, Base64.getUrlDecoder().decode(token).length, "256 bits of randomness");
    CalendarLink stored = storage.rows.get(PERSONAL_CAL);
    assertNotEquals(token, stored.getTokenHash(), "the digest is not the token");
    assertEquals(AgendaCalendarLinkServiceImpl.hash(token), stored.getTokenHash(), "it is its SHA-256");
    assertNotEquals(token, stored.getTokenEncrypted(), "the stored copy is not the token in clear");
    assertEquals(token, codec.decode(stored.getTokenEncrypted()), "it is the token encrypted by the platform codec");
    assertNotEquals(token, service.saveCalendarLink(PERSONAL_CAL, "owner"), "every creation draws a new token");
  }

  /**
   * The owner of a personal calendar creates, reads and deletes its link; the
   * read gives the token back every time, and never the stored secrets.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void theOwnerManagesAndSeesTheLinkOfTheirPersonalCalendar() throws Exception {
    assertNull(service.getCalendarLink(PERSONAL_CAL, "owner"), "no link yet");

    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    CalendarLink link = service.getCalendarLink(PERSONAL_CAL, "owner");

    assertEquals(OWNER, link.getCreatorId());
    assertEquals(NOW.toEpochMilli(), link.getCreatedDate());
    assertTrue(link.isActive());
    assertEquals(token, link.getToken(), "the link is shown again");
    assertEquals(token, service.getCalendarLink(PERSONAL_CAL, "owner").getToken(), "and again");
    assertNull(link.getTokenHash(), "the digest never leaves the service");
    assertNull(link.getTokenEncrypted(), "nor the encrypted copy");
    assertTrue(service.getCalendarFeed(token).startsWith("BEGIN:VCALENDAR"));

    service.deleteCalendarLink(PERSONAL_CAL, "owner");
    assertNull(service.getCalendarLink(PERSONAL_CAL, "owner"));
    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(token), "a deleted link opens nothing");
  }

  /**
   * The calendar a link of this eXo publishes is found only while the link
   * answers, exactly as its feed is served (EXO-90278, which recognises a
   * subscription to a link of this eXo): a link whose creator's account was
   * disabled resolves to no calendar, and neither does a token that opens
   * nothing.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void theCalendarOfALinkIsFoundOnlyWhileTheLinkAnswers() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    assertEquals(PERSONAL_CAL, service.getFeedCalendarId(token));

    alter(OWNER, "disabled");

    assertThrows(ObjectNotFoundException.class, () -> service.getFeedCalendarId(token),
                 "a link whose creator was disabled resolves to no calendar");
    assertThrows(ObjectNotFoundException.class, () -> service.getFeedCalendarId("not-a-token"));
  }

  /**
   * Nobody but its owner manages or sees a personal calendar's link.
   *
   * @throws Exception when setting up the link fails
   */
  @Test
  void anotherUserCannotManageOrSeeAPersonalCalendarLink() throws Exception {
    service.saveCalendarLink(PERSONAL_CAL, "owner");
    String before = storage.rows.get(PERSONAL_CAL).getTokenHash();

    assertThrows(IllegalAccessException.class, () -> service.getCalendarLink(PERSONAL_CAL, "other"));
    assertThrows(IllegalAccessException.class, () -> service.saveCalendarLink(PERSONAL_CAL, "other"));
    assertThrows(IllegalAccessException.class, () -> service.deleteCalendarLink(PERSONAL_CAL, "other"));
    assertEquals(before, storage.rows.get(PERSONAL_CAL).getTokenHash(), "and the link is left as it was");
  }

  /**
   * PO rules 1 and 4: the link belongs to the space calendar. A second manager
   * sees the link another manager created — creator, date and the URL itself —
   * and resetting it replaces it: the first URL stops answering, the new one
   * answers, and the resetting manager becomes its creator.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void aSecondManagerSeesAndResetsTheLinkAnotherManagerCreated() throws Exception {
    String first = service.saveCalendarLink(SPACE_CAL, "manager");

    CalendarLink seen = service.getCalendarLink(SPACE_CAL, "manager2");
    assertEquals(MANAGER, seen.getCreatorId(), "the second manager sees who created the link");
    assertTrue(seen.isActive());
    assertEquals(first, seen.getToken(), "and the link itself");

    String second = service.saveCalendarLink(SPACE_CAL, "manager2");

    assertEquals(1, storage.rows.size(), "still one link for the calendar");
    assertEquals(SECOND_MANAGER, service.getCalendarLink(SPACE_CAL, "manager").getCreatorId());
    assertEquals(second, service.getCalendarLink(SPACE_CAL, "manager").getToken(), "the first manager now sees the new link");
    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(first), "the replaced URL stops answering");
    assertTrue(service.getCalendarFeed(second).startsWith("BEGIN:VCALENDAR"), "the new one answers");
  }

  /**
   * PO rule 3: a plain member of the space is refused reading (and so seeing
   * the URL), creating, resetting and deleting the link, and a refused reset or
   * delete changes nothing.
   *
   * @throws Exception when setting up the link fails
   */
  @Test
  void aMemberIsRefusedOnReadCreateResetAndDelete() throws Exception {
    assertThrows(IllegalAccessException.class, () -> service.saveCalendarLink(SPACE_CAL, "member"), "create");
    assertTrue(storage.rows.isEmpty(), "a refused creation stores nothing");

    String token = service.saveCalendarLink(SPACE_CAL, "manager");
    String digest = storage.rows.get(SPACE_CAL).getTokenHash();

    assertThrows(IllegalAccessException.class, () -> service.getCalendarLink(SPACE_CAL, "member"), "read");
    assertThrows(IllegalAccessException.class, () -> service.saveCalendarLink(SPACE_CAL, "member"), "reset");
    assertThrows(IllegalAccessException.class, () -> service.deleteCalendarLink(SPACE_CAL, "member"), "delete");
    assertEquals(digest, storage.rows.get(SPACE_CAL).getTokenHash(), "the link is untouched");
    assertTrue(service.getCalendarFeed(token).startsWith("BEGIN:VCALENDAR"), "and still answers");
  }

  /**
   * PO rule 2: a space calendar's link stops answering once its creator is no
   * longer a manager — demoted, or gone from the space — and the managers who
   * remain see it dead, without its URL.
   *
   * @param departure how the creator stopped being a manager
   * @throws Exception when setting up the link fails
   */
  @ParameterizedTest
  @ValueSource(strings = { "demoted", "left" })
  void aCreatorWhoIsNoLongerAManagerMakesTheFeedNotFound(String departure) throws Exception {
    String token = service.saveCalendarLink(SPACE_CAL, "manager");
    assertNotNull(service.getCalendarFeed(token), "the link answers while its creator manages the space");

    managers.remove("manager");
    if ("left".equals(departure)) {
      members.remove("manager");
    }

    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(token));
    CalendarLink dead = service.getCalendarLink(SPACE_CAL, "manager2");
    assertFalse(dead.isActive(), "the remaining manager sees it dead");
    assertNull(dead.getToken(), "and a dead link gives no URL");
  }

  /**
   * PO rule 2: a space calendar's link stops answering when its creator's
   * account is disabled or deleted, even while still listed as a manager.
   *
   * @param state what happened to the creator's account
   * @throws Exception when setting up the link fails
   */
  @ParameterizedTest
  @ValueSource(strings = { "disabled", "deleted", "purged" })
  void aDisabledOrDeletedCreatorMakesTheSpaceFeedNotFound(String state) throws Exception {
    String token = service.saveCalendarLink(SPACE_CAL, "manager");

    alter(MANAGER, state);

    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(token));
    CalendarLink dead = service.getCalendarLink(SPACE_CAL, "manager2");
    assertFalse(dead.isActive());
    assertNull(dead.getToken());
  }

  /**
   * PO rule 2: a personal calendar's link stops answering when its owner can no
   * longer read it — account disabled or deleted.
   *
   * @param state what happened to the owner's account
   * @throws Exception when setting up the link fails
   */
  @ParameterizedTest
  @ValueSource(strings = { "disabled", "deleted", "purged" })
  void aDisabledOrDeletedOwnerMakesThePersonalFeedNotFound(String state) throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");

    alter(OWNER, state);

    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(token));
  }

  /**
   * PO rule 4: once the codec key is replaced, the stored copy no longer
   * decrypts. The link cannot be displayed any more — no token — but it is still
   * active and the feed keeps answering through its digest.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void aLinkEncryptedUnderAReplacedKeyStillAnswersButCannotBeDisplayed() throws Exception {
    String token = service.saveCalendarLink(SPACE_CAL, "manager");

    codec.replaceKey();

    CalendarLink link = service.getCalendarLink(SPACE_CAL, "manager2");
    assertTrue(link.isActive(), "the link still answers");
    assertNull(link.getToken(), "but it cannot be shown");
    assertTrue(service.getCalendarFeed(token).startsWith("BEGIN:VCALENDAR"), "and the feed keeps working");
  }

  /**
   * A stored copy that decrypts into another token than the one the digest
   * names is not shown: the URL on screen is always the one that works.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void aCopyThatDecryptsIntoAnotherTokenIsNotDisplayed() throws Exception {
    service.saveCalendarLink(PERSONAL_CAL, "owner");
    CalendarLink stored = storage.rows.get(PERSONAL_CAL);
    stored.setTokenEncrypted(codec.encode("B".repeat(43)));

    assertNull(service.getCalendarLink(PERSONAL_CAL, "owner").getToken());
  }

  /**
   * An unknown token, a malformed one and no token at all are refused alike,
   * and each is still looked up, so a malformed token is not answered faster
   * than a real one.
   */
  @Test
  void unknownAndMalformedTokensAreNotFoundAfterALookup() {
    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed("A".repeat(43)));
    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed("not a token"));
    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(null));

    assertEquals(3, storage.lookupsByDigest, "every refusal went through the same lookup");
  }

  /**
   * A token whose digest matches a row but which is not a well-formed token is
   * still refused.
   */
  @Test
  void aMalformedTokenIsRefusedEvenWhenItsDigestIsStored() {
    storage.save(PERSONAL_CAL, OWNER, AgendaCalendarLinkServiceImpl.hash("short"), "copy", new Date());

    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed("short"));
  }

  /**
   * A link to a calendar that is gone opens nothing, and a link cannot be
   * managed on a calendar that does not exist.
   *
   * @throws Exception when setting up the link fails
   */
  @Test
  void aMissingCalendarOpensNothingAndCannotBeManaged() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    deletedCalendar = calendar(PERSONAL_CAL, OWNER);
    deletedCalendar.setDeleted(true);

    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(token));
    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarLink(PERSONAL_CAL, "owner"));
    assertThrows(ObjectNotFoundException.class, () -> service.saveCalendarLink(999, "owner"));
    assertThrows(IllegalArgumentException.class, () -> service.saveCalendarLink(0, "owner"));
  }

  /**
   * The feed reads the published window as the link's creator — so the calendar
   * ACL is applied to them again — and keeps only this calendar's events.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void theFeedReadsTheWindowAsTheCreatorAndKeepsOnlyThisCalendar() throws Exception {
    String token = service.saveCalendarLink(SPACE_CAL, "manager");
    ZonedDateTime now = ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC);
    when(eventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(event(1, SPACE_CAL, "Kept", now.plusDays(1)),
                                                                             event(2, 77, "Other calendar", now.plusDays(2))));

    String document = service.getCalendarFeed(token);

    ArgumentCaptor<EventFilter> filter = ArgumentCaptor.forClass(EventFilter.class);
    verify(eventService).getEvents(filter.capture(), eq(ZoneOffset.UTC), eq(MANAGER));
    assertEquals(List.of(SPACE), filter.getValue().getOwnerIds());
    assertEquals(now.minusDays(AgendaCalendarLinkServiceImpl.PAST_DAYS), filter.getValue().getStart());
    assertEquals(now.plusDays(AgendaCalendarLinkServiceImpl.FUTURE_DAYS), filter.getValue().getEnd());
    assertTrue(document.contains("SUMMARY:Kept"));
    assertFalse(document.contains("Other calendar"), "an event of another calendar of the same owner stays out");
  }

  /**
   * The same calendar renders the same bytes whenever it is fetched, so the
   * entity tag computed over it lets a client refreshing an unchanged calendar
   * get a 304.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void anUnchangedCalendarRendersTheSameDocumentAtAnotherTime() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    ZonedDateTime now = ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC);
    when(eventService.getEvents(any(), any(), anyLong())).thenAnswer(invocation -> List.of(event(1, PERSONAL_CAL, "Sync", now.plusDays(1))));

    String first = service.getCalendarFeed(token);
    service.setClock(Clock.fixed(NOW.plusSeconds(3 * 3600 + 17), ZoneOffset.UTC));
    String later = service.getCalendarFeed(token);

    assertEquals(first, later);
  }

  /**
   * A dense calendar is capped, earliest events kept.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void theFeedIsCappedEarliestFirst() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    ZonedDateTime now = ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC);
    List<Event> events = new ArrayList<>();
    for (int i = AgendaCalendarLinkServiceImpl.MAX_EVENTS + 5; i > 0; i--) {
      events.add(event(i, PERSONAL_CAL, "E" + i, now.plusHours(i)));
    }
    when(eventService.getEvents(any(), any(), anyLong())).thenReturn(events);

    String document = service.getCalendarFeed(token);

    assertEquals(AgendaCalendarLinkServiceImpl.MAX_EVENTS, document.split("BEGIN:VEVENT", -1).length - 1);
    assertTrue(document.contains("SUMMARY:E1\r\n"), "the earliest event is kept");
    assertFalse(document.contains("SUMMARY:E" + (AgendaCalendarLinkServiceImpl.MAX_EVENTS + 5) + "\r\n"),
                "the latest is dropped");
  }

  /**
   * EXO-90322: the published feed masks a PRIVATE event and nothing else. A
   * PUBLIC one and a DEFAULT one — DEFAULT being what every event stored before
   * the flag existed carries — keep their title, location and description.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void onlyAPrivateEventIsPublishedAsABusyBlock() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    ZonedDateTime now = ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC);
    Event secret = event(1, PERSONAL_CAL, "Salary review", now.plusHours(1));
    secret.setLocation("HR office");
    secret.setVisibility(EventVisibility.PRIVATE);
    Event stated = event(2, PERSONAL_CAL, "Team lunch", now.plusHours(2));
    stated.setVisibility(EventVisibility.PUBLIC);
    Event legacy = event(3, PERSONAL_CAL, "Weekly sync", now.plusHours(3));
    legacy.setVisibility(EventVisibility.DEFAULT);
    when(eventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(secret, stated, legacy));

    String document = service.getCalendarFeed(token);

    assertFalse(document.contains("Salary review"), "the private event's title does not leave eXo");
    assertFalse(document.contains("HR office"), "nor its location");
    assertTrue(document.contains("SUMMARY:Busy\r\n"), "it is published as a busy block");
    assertTrue(document.contains("SUMMARY:Team lunch\r\n"), "a public event keeps its title");
    assertTrue(document.contains("SUMMARY:Weekly sync\r\n"), "and so does one whose visibility was never set");
  }

  /**
   * EXO-90322: an event whose visibility was never written at all — a null the
   * service defaults away on every write, but which a stale cached row or a
   * connector could still hand the feed — is published in full, as it was
   * before the flag existed. Only PRIVATE masks.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void anEventWithNoVisibilityAtAllIsPublishedInFull() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    ZonedDateTime now = ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC);
    Event unset = event(1, PERSONAL_CAL, "Weekly sync", now.plusHours(1));
    assertNull(unset.getVisibility(), "the fixture really carries no visibility");
    when(eventService.getEvents(any(), any(), anyLong())).thenReturn(List.of(unset));

    assertTrue(service.getCalendarFeed(token).contains("SUMMARY:Weekly sync\r\n"));
  }

  /**
   * EXO-90322: the predicate the feed is wired to reads the event's own
   * visibility, and nothing else. This is the line that used to be a hardcoded
   * {@code false}, so it is pinned on its own as well as through the document.
   */
  @Test
  void isPrivateReadsTheEventVisibility() {
    Event event = new Event();

    assertFalse(AgendaCalendarLinkServiceImpl.isPrivate(event), "no visibility set");
    event.setVisibility(EventVisibility.DEFAULT);
    assertFalse(AgendaCalendarLinkServiceImpl.isPrivate(event), "DEFAULT does not mask");
    event.setVisibility(EventVisibility.PUBLIC);
    assertFalse(AgendaCalendarLinkServiceImpl.isPrivate(event), "PUBLIC does not mask");
    event.setVisibility(EventVisibility.PRIVATE);
    assertTrue(AgendaCalendarLinkServiceImpl.isPrivate(event), "PRIVATE masks");
    assertFalse(AgendaCalendarLinkServiceImpl.isPrivate(null), "and no event masks nothing");
  }

  /**
   * When the event service refuses the creator the calendar, the feed opens
   * nothing rather than failing.
   *
   * @throws Exception when setting up the link fails
   */
  @Test
  void aRefusedEventReadIsNotFound() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    when(eventService.getEvents(any(), any(), anyLong())).thenThrow(new IllegalAccessException("refused"));

    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(token));
  }

  /**
   * The platform's clean-up deletes a calendar's link without a permission
   * check, and ignores an identifier that names nothing.
   *
   * @throws Exception when setting up the link fails
   */
  @Test
  void theCleanUpDeletesWithoutACheck() throws Exception {
    service.saveCalendarLink(PERSONAL_CAL, "owner");

    service.deleteCalendarLinks(PERSONAL_CAL);
    service.deleteCalendarLinks(0);

    assertTrue(storage.rows.isEmpty());
    verify(calendarService, never()).getCalendarById(0);
  }

  /**
   * PO decision (b): a super-manager who is not a manager of the space — a
   * platform administrator — may not read, create, reset or delete a space
   * calendar's link, although social lets them manage the space.
   *
   * @throws Exception when setting up the link fails
   */
  @Test
  void aSuperManagerWhoIsNotAManagerIsRefusedEveryLinkOperation() throws Exception {
    assertThrows(IllegalAccessException.class, () -> service.saveCalendarLink(SPACE_CAL, "admin"), "create");
    assertTrue(storage.rows.isEmpty(), "nothing stored");

    service.saveCalendarLink(SPACE_CAL, "manager");
    String digest = storage.rows.get(SPACE_CAL).getTokenHash();

    assertThrows(IllegalAccessException.class, () -> service.getCalendarLink(SPACE_CAL, "admin"), "read, so no URL");
    assertThrows(IllegalAccessException.class, () -> service.saveCalendarLink(SPACE_CAL, "admin"), "reset");
    assertThrows(IllegalAccessException.class, () -> service.deleteCalendarLink(SPACE_CAL, "admin"), "delete");
    assertEquals(digest, storage.rows.get(SPACE_CAL).getTokenHash(), "the link is untouched");
  }

  /**
   * PO decision (b): holding the manager role is not enough — a real manager is
   * also a member of the space.
   */
  @Test
  void theManagerRoleWithoutMembershipIsRefused() {
    managers.add("roleonly");

    assertThrows(IllegalAccessException.class, () -> service.saveCalendarLink(SPACE_CAL, "roleonly"));
  }

  /**
   * PO decision (b): a super-manager's listing holds no space calendar link, even
   * when the platform's list of managed spaces names the space for them.
   *
   * @throws Exception when setting up the link fails
   */
  @Test
  void aSuperManagerListsNoSpaceLinkEvenWhenTheSpaceIsListedForThem() throws Exception {
    service.saveCalendarLink(SPACE_CAL, "manager");
    listedManagers.add("admin");

    assertTrue(service.getCalendarLinks("admin").isEmpty());
  }

  /**
   * PO decision (b): a space link whose creator is only a super-manager — one
   * created before the rule — opens nothing, and the space's real managers see it
   * stopped, without its URL.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void aLinkCreatedByASuperManagerDiesAtFetchTime() throws Exception {
    String token = "S".repeat(43);
    storage.save(SPACE_CAL, SUPER_MANAGER, AgendaCalendarLinkServiceImpl.hash(token), codec.encode(token), new Date());

    assertThrows(ObjectNotFoundException.class, () -> service.getCalendarFeed(token));
    CalendarLink seen = service.getCalendarLink(SPACE_CAL, "manager");
    assertFalse(seen.isActive());
    assertNull(seen.getToken());
    assertEquals(1, service.getCalendarLinks("manager").size(), "and it is listed, stopped, for them");
  }

  /**
   * The listing of an owner holds their personal calendar's link and nothing of
   * anybody else's, read in one storage query.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void anOwnerListsTheLinkOfTheirPersonalCalendarOnly() throws Exception {
    String token = service.saveCalendarLink(PERSONAL_CAL, "owner");
    service.saveCalendarLink(OTHER_CAL, "other");
    service.saveCalendarLink(SPACE_CAL, "manager");
    storage.ownerQueries = 0;

    List<CalendarLink> links = service.getCalendarLinks("owner");

    assertEquals(1, links.size());
    assertEquals(PERSONAL_CAL, links.get(0).getCalendarId());
    assertEquals(token, links.get(0).getToken(), "a working link comes with its token");
    assertNull(links.get(0).getTokenHash(), "and never with its stored secrets");
    assertNull(links.get(0).getTokenEncrypted());
    assertEquals(1, storage.ownerQueries, "one query for every calendar, never one per calendar");
  }

  /**
   * A manager lists the link of the space calendar another manager created;
   * a plain member of the same space lists nothing.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void aManagerListsTheSpaceLinkAndAMemberDoesNot() throws Exception {
    String token = service.saveCalendarLink(SPACE_CAL, "manager");

    List<CalendarLink> managerLinks = service.getCalendarLinks("manager2");
    assertEquals(1, managerLinks.size());
    assertEquals(SPACE_CAL, managerLinks.get(0).getCalendarId());
    assertEquals(MANAGER, managerLinks.get(0).getCreatorId());
    assertEquals(token, managerLinks.get(0).getToken());

    assertTrue(service.getCalendarLinks("member").isEmpty(), "a member manages no link and lists none");
  }

  /**
   * A stopped link is listed, inactive and without its token, so the page can
   * say why it stopped; a working link that can no longer be displayed is listed
   * active and without its token.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void stoppedAndUndisplayableLinksAreListedWithoutTheirToken() throws Exception {
    service.saveCalendarLink(SPACE_CAL, "manager");
    service.saveCalendarLink(PERSONAL_CAL, "owner");
    managers.remove("manager");
    listedManagers.remove("manager");

    CalendarLink stopped = service.getCalendarLinks("manager2").get(0);
    assertFalse(stopped.isActive(), "the creator is no longer a manager");
    assertEquals(MANAGER, stopped.getCreatorId(), "and the entry still names them");
    assertNull(stopped.getToken());

    codec.replaceKey();
    CalendarLink undisplayable = service.getCalendarLinks("owner").get(0);
    assertTrue(undisplayable.isActive());
    assertNull(undisplayable.getToken());
  }

  /**
   * The rights are checked again for every link found: a space the platform
   * still lists as managed, but whose management right the user lost, gives
   * nothing; and a link of a deleted calendar is left out.
   *
   * @throws Exception when the service refuses
   */
  @Test
  void everyListedLinkIsCheckedAgainstTheRightsOfTheMoment() throws Exception {
    service.saveCalendarLink(SPACE_CAL, "manager");
    service.saveCalendarLink(PERSONAL_CAL, "owner");
    managers.remove("manager2");

    assertTrue(service.getCalendarLinks("manager2").isEmpty(), "listed as manager, no longer allowed");

    deletedCalendar = calendar(PERSONAL_CAL, OWNER);
    deletedCalendar.setDeleted(true);
    assertTrue(service.getCalendarLinks("owner").isEmpty(), "a deleted calendar's link is not listed");
  }

  /**
   * A user with no usable identity is refused the listing.
   */
  @Test
  void aListingNeedsAUsableIdentity() {
    identities.get(String.valueOf(OWNER)).setEnable(false);

    assertThrows(IllegalAccessException.class, () -> service.getCalendarLinks("owner"));
  }

  /**
   * A list of spaces the way the platform pages them.
   *
   * @param spaces the spaces
   * @return the paged access
   */
  private ListAccess<Space> spaces(List<Space> spaces) {
    return new ListAccess<>() {
      @Override
      public Space[] load(int offset, int limit) {
        return spaces.stream().skip(offset).limit(limit).toArray(Space[]::new);
      }

      @Override
      public int getSize() {
        return spaces.size();
      }
    };
  }

  /**
   * Registers a user identity.
   *
   * @param id identity identifier
   * @param username user name
   */
  private void user(long id, String username) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    identities.put(String.valueOf(id), identity);
  }

  /**
   * Changes the state of a user's account.
   *
   * @param id identity identifier
   * @param state disabled, deleted, or purged (identity gone)
   */
  private void alter(long id, String state) {
    switch (state) {
    case "disabled" -> identities.get(String.valueOf(id)).setEnable(false);
    case "deleted" -> identities.get(String.valueOf(id)).setDeleted(true);
    default -> identities.remove(String.valueOf(id));
    }
  }

  /**
   * A calendar as the calendar service answers it.
   *
   * @param id calendar identifier
   * @param ownerId owner identity identifier
   * @return the calendar
   */
  private Calendar calendar(long id, long ownerId) {
    Calendar calendar = new Calendar();
    calendar.setId(id);
    calendar.setOwnerId(ownerId);
    calendar.setTitle("Calendar " + id);
    return calendar;
  }

  /**
   * An event of a calendar.
   *
   * @param id event identifier
   * @param calendarId calendar identifier
   * @param summary title
   * @param start start
   * @return the event
   */
  private Event event(long id, long calendarId, String summary, ZonedDateTime start) {
    Event event = new Event();
    event.setId(id);
    event.setCalendarId(calendarId);
    event.setSummary(summary);
    event.setStart(start);
    event.setEnd(start.plusMinutes(30));
    event.setCreated(ZonedDateTime.ofInstant(NOW, ZoneOffset.UTC).minusDays(2));
    return event;
  }

  /**
   * A reversible stand-in for the platform codec, whose key can be replaced:
   * a copy made under the old key then fails to decode the way the real AES
   * codec does, with a padding error wrapped in an undeclared exception.
   */
  static class StandInCodec extends AbstractCodec {

    private String key = "key-1";

    /**
     * Replaces the key.
     */
    void replaceKey() {
      key = "key-2";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String encode(String plainText) {
      return key + ":" + new StringBuilder(plainText).reverse();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String decode(String encodedInput) {
      if (!encodedInput.startsWith(key + ":")) {
        throw new UndeclaredThrowableException(new BadPaddingException("Given final block not properly padded"));
      }
      return new StringBuilder(encodedInput.substring(key.length() + 1)).reverse().toString();
    }
  }

  /**
   * Calendar link storage kept in memory: one row per calendar, looked up by
   * digest the way the unique index is.
   */
  static class InMemoryLinkStorage extends CalendarLinkStorage {

    final Map<Long, CalendarLink> rows = new HashMap<>();

    final Map<Long, Long>         owners;

    int                           lookupsByDigest;

    int                           ownerQueries;

    /**
     * Builds the storage with no repository behind it.
     *
     * @param owners the owner of each calendar, as the join reads it
     */
    InMemoryLinkStorage(Map<Long, Long> owners) {
      super(null);
      this.owners = owners;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CalendarLink> getByCalendarOwnerIds(List<Long> ownerIds, int limit) {
      ownerQueries++;
      return rows.values()
                 .stream()
                 .filter(link -> ownerIds.contains(owners.get(link.getCalendarId())))
                 .limit(limit)
                 .map(this::copy)
                 .toList();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarLink getByCalendarId(long calendarId) {
      return copy(rows.get(calendarId));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarLink getByTokenHash(String tokenHash) {
      lookupsByDigest++;
      return copy(rows.values().stream().filter(link -> link.getTokenHash().equals(tokenHash)).findFirst().orElse(null));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public CalendarLink save(long calendarId, long creatorId, String tokenHash, String tokenEncrypted, Date createdDate) {
      CalendarLink link = new CalendarLink(calendarId, creatorId, createdDate.getTime(), tokenHash, tokenEncrypted, null, false);
      rows.put(calendarId, link);
      return copy(link);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean deleteByCalendarId(long calendarId) {
      return rows.remove(calendarId) != null;
    }

    /**
     * Copies a row, as a read from the database would.
     *
     * @param link the row
     * @return a copy, or null
     */
    private CalendarLink copy(CalendarLink link) {
      return link == null ? null
                          : new CalendarLink(link.getCalendarId(),
                                             link.getCreatorId(),
                                             link.getCreatedDate(),
                                             link.getTokenHash(),
                                             link.getTokenEncrypted(),
                                             null,
                                             false);
    }
  }

}
