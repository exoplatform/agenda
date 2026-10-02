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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.After;
import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.AgendaUserSettings;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.HeldMailInvitation;
import org.exoplatform.agenda.model.LandedMailInvitation;
import org.exoplatform.agenda.model.MailInvitation;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.metadata.MetadataService;

/**
 * Lands invitations through agenda's real services and the real metadata
 * lookup, on the test container's HSQLDB (EXO-90866): the event properties a
 * later message finds the event by are written by {@code createEvent} and read
 * back by social's property query, so these tests run that query on a real
 * engine.
 */
public class AgendaInvitationLandingServiceTest extends BaseAgendaEventTest {

  private static final String            ORGANIZER = "boss@partner.example";

  private static final String            MAILBOX   = "testuser1@mail.example";

  private AgendaInvitationLandingService landingService;

  private final List<Long>               owners    = new ArrayList<>();

  /**
   * Builds the service over the container's services, as the Spring context
   * does through the bridge.
   */
  private AgendaInvitationLandingService landingService() {
    if (landingService == null) {
      landingService = new AgendaInvitationLandingService(identityManager,
                                                          agendaCalendarService,
                                                          agendaEventService,
                                                          agendaEventAttendeeService,
                                                          agendaEventReminderService,
                                                          agendaEventConferenceService,
                                                          agendaRemoteEventService,
                                                          agendaUserSettingsService,
                                                          container.getComponentInstanceOfType(MetadataService.class));
    }
    return landingService;
  }

  /**
   * Removes the personal calendars the landings created, and their events.
   *
   * @throws ObjectNotFoundException when a calendar vanished meanwhile
   */
  @After
  public void removePersonalCalendars() throws ObjectNotFoundException {
    for (long owner : owners) {
      Calendar personal = agendaCalendarService.getOrCreateCalendarByOwnerId(owner);
      agendaCalendarService.deleteCalendarById(personal.getId());
    }
    owners.clear();
  }

  /**
   * An accepted invitation is created in the user's personal calendar, the user
   * its attendee with their answer, under the invitation's own text and times,
   * with agenda's link; the UID, SEQUENCE and organiser are kept with it.
   */
  @Test
  public void testAcceptCreatesTheEventInThePersonalCalendar() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, ics("REQUEST", uid, 0, ORGANIZER, "Quarterly review", ""));

    assertNotNull(landed);
    assertFalse(landed.removed());
    assertFalse(landed.alreadyHeld());
    assertEquals("http://localhost:8080/portal/classic/agenda?eventId=" + landed.eventId(), landed.link());
    long user = identityOf("testuser2");
    Event event = agendaEventService.getEventById(landed.eventId());
    assertEquals("Quarterly review", event.getSummary());
    assertEquals(EventStatus.CONFIRMED, event.getStatus());
    assertEquals(user, event.getCreatorId());
    assertEquals(agendaCalendarService.getOrCreateCalendarByOwnerId(user).getId(), event.getCalendarId());
    assertEquals(ZonedDateTime.of(2030, 10, 15, 9, 0, 0, 0, ZoneOffset.UTC).toInstant(), event.getStart().toInstant());
    assertEquals(EventAttendeeResponse.ACCEPTED, agendaEventAttendeeService.getEventResponse(event.getId(), null, user));
    List<EventAttendee> attendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    assertEquals(1, attendees.size());
    assertEquals(user, attendees.get(0).getIdentityId());
  }

  /**
   * A tentative answer stays tentative. Agenda accepts on its creator's
   * behalf when an event is created; the landing sets the answer after.
   */
  @Test
  public void testMaybeIsKeptTentative() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.TENTATIVE, ics("REQUEST", uid, 0, ORGANIZER, "Sync", ""));

    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(landed.eventId(), null, identityOf("testuser2")));
  }

  /**
   * A decline of an event the user never added creates nothing.
   */
  @Test
  public void testDeclineCreatesNothing() throws Exception {
    String uid = uid();
    assertNull(land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.DECLINED, ics("REQUEST", uid, 0, ORGANIZER, "Sync", "")));
    // Nothing was created: an acceptance now creates the one event, not a second
    LandedMailInvitation accepted = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED,
                                         ics("REQUEST", uid, 0, ORGANIZER, "Sync", ""));
    assertEquals(1, eventsOf("testuser2", uid));
    assertNotNull(accepted);
  }

  /**
   * The same invitation landed twice is the same event, not a second one; an
   * answer given then is set on it, and a decline on an event held declines
   * it, the event kept.
   */
  @Test
  public void testTheSameInvitationLandsOnceAndTakesTheLaterAnswer() throws Exception {
    String uid = uid();
    String object = ics("REQUEST", uid, 0, ORGANIZER, "Sync", "");
    LandedMailInvitation first = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, object);
    LandedMailInvitation second = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.DECLINED, object);

    assertEquals(first.eventId(), second.eventId());
    assertNotNull(agendaEventService.getEventById(first.eventId()));
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(first.eventId(), null, identityOf("testuser2")));
  }

  /**
   * The organiser's strictly newer revision rewrites the event and the answer
   * the user gave stands; a revision no newer changes nothing.
   */
  @Test
  public void testANewerRevisionUpdatesAndKeepsTheAnswer() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 1, EventAttendeeResponse.TENTATIVE, ics("REQUEST", uid, 1, ORGANIZER, "Sync", ""));

    land("testuser2", "REQUEST", uid, 1, null, ics("REQUEST", uid, 1, ORGANIZER, "Same sequence", ""));
    assertEquals("Sync", agendaEventService.getEventById(landed.eventId()).getSummary());

    LandedMailInvitation updated = land("testuser2", "REQUEST", uid, 2, null, ics("REQUEST", uid, 2, ORGANIZER, "Moved sync", "")
        .replace("DTSTART:20301015T090000Z", "DTSTART:20301016T140000Z")
        .replace("DTEND:20301015T100000Z", "DTEND:20301016T150000Z"));

    assertEquals(landed.eventId(), updated.eventId());
    Event event = agendaEventService.getEventById(landed.eventId());
    assertEquals("Moved sync", event.getSummary());
    assertEquals(ZonedDateTime.of(2030, 10, 16, 14, 0, 0, 0, ZoneOffset.UTC).toInstant(), event.getStart().toInstant());
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(landed.eventId(), null, identityOf("testuser2")));
  }

  /**
   * Only the event's organiser rewrites or removes it: a newer revision or a
   * cancellation from somebody who learnt the UID is refused, the event left
   * as it was.
   */
  @Test
  public void testAnotherOrganiserNeitherUpdatesNorCancels() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, ics("REQUEST", uid, 0, ORGANIZER, "Sync", ""));

    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser2", "REQUEST", uid, 5, null, ics("REQUEST", uid, 5, "intruder@else.example", "Hijacked", "")));
    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser2", "CANCEL", uid, 5, null, ics("CANCEL", uid, 5, "intruder@else.example", "Sync", "")));

    Event event = agendaEventService.getEventById(landed.eventId());
    assertNotNull(event);
    assertEquals("Sync", event.getSummary());
  }

  /**
   * An event whose kept organiser is the user's own address is theirs, and no
   * mail touches it.
   */
  @Test
  public void testAnEventTheUserOrganisesIsNotTouched() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "PUBLISH", uid, 0, null, ics("PUBLISH", uid, 0, MAILBOX, "Mine", ""));

    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser2", "CANCEL", uid, 1, null, ics("CANCEL", uid, 1, MAILBOX, "Mine", "")));
    assertNotNull(agendaEventService.getEventById(landed.eventId()));
  }

  /**
   * The organiser's cancellation removes the event the user holds, an older
   * one does not, and one of an event never added does nothing.
   */
  @Test
  public void testTheOrganisersCancellationRemovesTheEvent() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 3, EventAttendeeResponse.ACCEPTED, ics("REQUEST", uid, 3, ORGANIZER, "Sync", ""));

    assertThrows(IllegalArgumentException.class, () -> land("testuser2", "CANCEL", uid, 2, null, ics("CANCEL", uid, 2, ORGANIZER, "Sync", "")));
    assertNotNull(agendaEventService.getEventById(landed.eventId()));

    LandedMailInvitation removed = land("testuser2", "CANCEL", uid, 3, null, ics("CANCEL", uid, 3, ORGANIZER, "Sync", ""));
    assertTrue(removed.removed());
    assertNull(removed.link());
    assertNull(agendaEventService.getEventById(landed.eventId()));

    assertNull(land("testuser2", "CANCEL", uid, 4, null, ics("CANCEL", uid, 4, ORGANIZER, "Sync", "")));
  }

  /**
   * Two users holding the same meeting hold two events, and each landing reads
   * only its own user's.
   */
  @Test
  public void testEachUserHoldsTheirOwnEvent() throws Exception {
    String uid = uid();
    String object = ics("REQUEST", uid, 0, ORGANIZER, "Sync", "");
    LandedMailInvitation second = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, object);
    LandedMailInvitation third = land("testuser3", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, object);

    assertNotEquals(second.eventId(), third.eventId());
    assertEquals(identityOf("testuser3"), agendaEventService.getEventById(third.eventId()).getCreatorId());
  }

  /**
   * The lookup key is an event property anybody able to edit an event can
   * write: an event of another user's carrying this user's key is not adopted,
   * and nothing of it is touched.
   */
  @Test
  public void testAForgedPropertyOnAnotherUsersEventIsNotAdopted() throws Exception {
    String uid = uid();
    long victim = identityOf("testuser2");
    long forger = identityOf("testuser3");
    owners.add(forger);
    Event forged = new Event();
    forged.setCalendarId(agendaCalendarService.getOrCreateCalendarByOwnerId(forger).getId());
    forged.setSummary("Forged");
    forged.setStart(ZonedDateTime.of(2030, 10, 15, 9, 0, 0, 0, ZoneOffset.UTC));
    forged.setEnd(ZonedDateTime.of(2030, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC));
    forged.setTimeZoneId(ZoneOffset.UTC);
    Map<String, String> properties = new HashMap<>();
    properties.put(AgendaInvitationLandingService.UID_PROPERTY, AgendaInvitationLandingService.uidKey(victim, uid));
    properties.put(AgendaInvitationLandingService.ORGANIZER_PROPERTY, ORGANIZER);
    forged.setParameters(properties);
    Event created = agendaEventService.createEvent(forged, List.of(), List.of(), List.of(), null, null, false, forger);

    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 9, EventAttendeeResponse.ACCEPTED, ics("REQUEST", uid, 9, ORGANIZER, "Sync", ""));

    assertNotEquals(created.getId(), landed.eventId());
    assertEquals("Forged", agendaEventService.getEventById(created.getId()).getSummary());
  }

  /**
   * A message agenda does not land: a REPLY or a COUNTER, one occurrence
   * alone, an invitation naming no organiser, an object about another event.
   */
  @Test
  public void testWhatIsNotLandedIsRefused() throws Exception {
    String uid = uid();
    owners.add(identityOf("testuser2"));
    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser2", "REPLY", uid, 0, EventAttendeeResponse.ACCEPTED, ics("REPLY", uid, 0, ORGANIZER, "Sync", "")));
    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser2", "COUNTER", uid, 0, null, ics("COUNTER", uid, 0, ORGANIZER, "Sync", "")));
    assertThrows(IllegalArgumentException.class,
                 () -> landingService().land(new MailInvitation("testuser2", MAILBOX, "REQUEST", uid, "20301015T090000Z", 0,
                                                                EventAttendeeResponse.ACCEPTED,
                                                                ics("REQUEST", uid, 0, ORGANIZER, "Sync", ""))));
    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, ics("REQUEST", uid, 0, null, "Sync", "")));
    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser2", "REQUEST", "other-" + uid, 0, EventAttendeeResponse.ACCEPTED,
                            ics("REQUEST", uid, 0, ORGANIZER, "Sync", "")));
    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, ics("CANCEL", uid, 0, ORGANIZER, "Sync", "")));
  }

  /**
   * One of this deployment's own meetings is never landed: added, it is
   * answered as already held, with its page, when the message describes it;
   * an answer carried by such a message, or a description of another event,
   * is refused. Named by its UID or by its link alike.
   */
  @Test
  public void testAnExoMeetingIsNeverLanded() throws Exception {
    long user = identityOf("testuser1");
    Event meeting = newEventInstance(ZonedDateTime.of(2030, 10, 15, 9, 0, 0, 0, ZoneOffset.UTC),
                                     ZonedDateTime.of(2030, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC),
                                     false);
    meeting.setSummary("Board meeting");
    meeting.setRecurrence(null);
    meeting = agendaEventService.createEvent(meeting, List.of(new EventAttendee(0, user, null)), List.of(), List.of(), null, null, false, user);
    String ownUid = "agenda-event-" + meeting.getId() + "@localhost";

    LandedMailInvitation held = land("testuser1", "REQUEST", ownUid, 0, null, ics("REQUEST", ownUid, 0, ORGANIZER, "Board meeting", ""));
    assertTrue(held.alreadyHeld());
    assertEquals(meeting.getId(), held.eventId());

    String otherUid = uid();
    String byLink = ics("REQUEST", otherUid, 0, ORGANIZER, "Board meeting",
                        "URL:http://localhost:8080/portal/dw/agenda?eventId=" + meeting.getId() + "\r\n");
    assertTrue(land("testuser1", "REQUEST", otherUid, 0, null, byLink).alreadyHeld());

    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser1", "REQUEST", ownUid, 0, EventAttendeeResponse.ACCEPTED,
                            ics("REQUEST", ownUid, 0, ORGANIZER, "Board meeting", "")));
    assertThrows(IllegalArgumentException.class,
                 () -> land("testuser1", "REQUEST", ownUid, 0, null, ics("REQUEST", ownUid, 0, ORGANIZER, "Something else", "")));
  }

  /**
   * A series lands with its rule, and an excluded date as a cancelled
   * exceptional occurrence.
   */
  @Test
  public void testASeriesLandsWithItsExclusions() throws Exception {
    String uid = uid();
    String object = ics("REQUEST", uid, 0, ORGANIZER, "Weekly", "RRULE:FREQ=WEEKLY;COUNT=5\r\nEXDATE:20301022T090000Z\r\n");
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, object);

    Event series = agendaEventService.getEventById(landed.eventId());
    assertNotNull(series.getRecurrence());
    assertEquals(5, series.getRecurrence().getCount());
    Event excluded = agendaEventService.getExceptionalOccurrenceEvent(landed.eventId(),
                                                                      ZonedDateTime.of(2030, 10, 22, 9, 0, 0, 0, ZoneOffset.UTC));
    assertNotNull(excluded);
    assertEquals(EventStatus.CANCELLED, excluded.getStatus());
  }

  /**
   * An override moving one occurrence of the series is placed as a confirmed
   * exceptional occurrence carrying the override's own time and title.
   */
  @Test
  public void testAnOverrideMovesItsOccurrence() throws Exception {
    String uid = uid();
    String object = ics("REQUEST", uid, 0, ORGANIZER, "Weekly", "RRULE:FREQ=WEEKLY;COUNT=5\r\n");
    object = object.replace("END:VCALENDAR\r\n",
                            "BEGIN:VEVENT\r\nUID:" + uid + "\r\nSEQUENCE:0\r\nRECURRENCE-ID:20301022T090000Z\r\n"
                                + "DTSTAMP:20300101T000000Z\r\nDTSTART:20301022T130000Z\r\nDTEND:20301022T140000Z\r\n"
                                + "SUMMARY:Weekly, moved\r\nORGANIZER:mailto:" + ORGANIZER + "\r\nEND:VEVENT\r\nEND:VCALENDAR\r\n");
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, object);

    Event moved = agendaEventService.getExceptionalOccurrenceEvent(landed.eventId(),
                                                                   ZonedDateTime.of(2030, 10, 22, 9, 0, 0, 0, ZoneOffset.UTC));
    assertNotNull(moved);
    assertEquals(EventStatus.CONFIRMED, moved.getStatus());
    assertEquals("Weekly, moved", moved.getSummary());
    assertEquals(ZonedDateTime.of(2030, 10, 22, 13, 0, 0, 0, ZoneOffset.UTC).toInstant(), moved.getStart().toInstant());
  }

  /**
   * A newer revision of a series rewrites it: agenda drops the exceptions it
   * held, and the revision's own are placed again; the user's answer stands.
   */
  @Test
  public void testANewerRevisionOfASeriesReplacesItsExclusions() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.TENTATIVE,
                                       ics("REQUEST", uid, 0, ORGANIZER, "Weekly",
                                           "RRULE:FREQ=WEEKLY;COUNT=5\r\nEXDATE:20301022T090000Z\r\n"));
    land("testuser2", "REQUEST", uid, 1, null,
         ics("REQUEST", uid, 1, ORGANIZER, "Weekly", "RRULE:FREQ=WEEKLY;COUNT=5\r\nEXDATE:20301029T090000Z\r\n"));

    assertNull(agendaEventService.getExceptionalOccurrenceEvent(landed.eventId(),
                                                                ZonedDateTime.of(2030, 10, 22, 9, 0, 0, 0, ZoneOffset.UTC)));
    Event excluded = agendaEventService.getExceptionalOccurrenceEvent(landed.eventId(),
                                                                      ZonedDateTime.of(2030, 10, 29, 9, 0, 0, 0, ZoneOffset.UTC));
    assertNotNull(excluded);
    assertEquals(EventStatus.CANCELLED, excluded.getStatus());
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(landed.eventId(), null, identityOf("testuser2")));
  }

  /**
   * A user with a remote calendar account connected to agenda, on an enabled
   * provider, is not agenda's: the connector shows the copy their mail server
   * filed, and an eXo copy beside it would be the meeting twice. The same
   * account on a provider an administrator switched off shows nothing, and the
   * invitation lands, as it does once the account is disconnected.
   */
  @Test
  public void testAUserWithAConnectedRemoteCalendarIsNotAgendas() throws Exception {
    long user = identityOf("testuser4");
    String provider = remoteProvider.getName();
    AgendaUserSettings settings = agendaUserSettingsService.getAgendaUserSettings(user);
    settings.addOrUpdateConnectedConnector(provider, "testuser4@remote.example");
    agendaUserSettingsService.saveAgendaUserSettings(user, settings);
    try {
      String uid = uid();
      String object = ics("REQUEST", uid, 0, ORGANIZER, "Sync", "");
      assertFalse(landingService().holdsCalendarFor("testuser4"));
      assertNull(land("testuser4", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, object));
      assertEquals(0, eventsOf("testuser4", uid));

      agendaRemoteEventService.saveRemoteProviderStatus(provider, false, false);
      assertTrue(landingService().holdsCalendarFor("testuser4"));
      agendaRemoteEventService.saveRemoteProviderStatus(provider, true, false);

      settings = agendaUserSettingsService.getAgendaUserSettings(user);
      settings.removeConnectedConnector(provider);
      agendaUserSettingsService.saveAgendaUserSettings(user, settings);
      assertTrue(landingService().holdsCalendarFor("testuser4"));
      assertNotNull(land("testuser4", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, object));
      assertEquals(1, eventsOf("testuser4", uid));
      restartTransaction();
      assertNotNull(landingService().held("testuser4", MAILBOX, uid, null, ORGANIZER));

      // Connected again: the event agenda holds is not told of, as nothing would be landed.
      settings = agendaUserSettingsService.getAgendaUserSettings(user);
      settings.addOrUpdateConnectedConnector(provider, "testuser4@remote.example");
      agendaUserSettingsService.saveAgendaUserSettings(user, settings);
      restartTransaction();
      assertNull(landingService().held("testuser4", MAILBOX, uid, null, ORGANIZER));
    } finally {
      agendaRemoteEventService.saveRemoteProviderStatus(provider, true, false);
      settings = agendaUserSettingsService.getAgendaUserSettings(user);
      settings.removeConnectedConnector(provider);
      agendaUserSettingsService.saveAgendaUserSettings(user, settings);
    }
  }

  /**
   * The mail reader asks whether the user's calendar holds an invitation when
   * it is opened (EXO-90873): the event landed is told with its link, the answer
   * agenda holds for the user — the one given in agenda since included — and the
   * SEQUENCE landed; nothing is written by asking.
   */
  @Test
  public void testHeldTellsTheEventItsAnswerAndItsSequence() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 3, EventAttendeeResponse.TENTATIVE, ics("REQUEST", uid, 3, ORGANIZER, "Sync", ""));

    restartTransaction();
    HeldMailInvitation held = landingService().held("testuser2", MAILBOX, uid, null, ORGANIZER);
    assertEquals(new HeldMailInvitation(landed.eventId(), landed.link(), EventAttendeeResponse.TENTATIVE, 3), held);

    long user = identityOf("testuser2");
    agendaEventAttendeeService.sendEventResponse(landed.eventId(), user, EventAttendeeResponse.DECLINED, false);
    restartTransaction();
    assertEquals(EventAttendeeResponse.DECLINED, landingService().held("testuser2", MAILBOX, " " + uid + " ", null, ORGANIZER).response());
    assertEquals(1, eventsOf("testuser2", uid));
    assertEquals(EventAttendeeResponse.DECLINED, agendaEventAttendeeService.getEventResponse(landed.eventId(), null, user));
  }

  /**
   * An event added from the mail without an answer holds agenda's ACCEPTED for
   * its creator, which nobody said: it is told with no answer, and a Maybe or
   * a Decline given in agenda since is told as it is.
   */
  @Test
  public void testHeldAfterAnAdditionWithoutAnswerSaysNoAnswer() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 1, null, ics("REQUEST", uid, 1, ORGANIZER, "Sync", ""));
    long user = identityOf("testuser2");
    assertEquals(EventAttendeeResponse.ACCEPTED, agendaEventAttendeeService.getEventResponse(landed.eventId(), null, user));

    restartTransaction();
    HeldMailInvitation held = landingService().held("testuser2", MAILBOX, uid, null, ORGANIZER);
    assertNotNull(held);
    assertNull("nobody answered", held.response());
    assertEquals(1, held.sequence());

    agendaEventAttendeeService.sendEventResponse(landed.eventId(), user, EventAttendeeResponse.TENTATIVE, false);
    restartTransaction();
    assertEquals(EventAttendeeResponse.TENTATIVE, landingService().held("testuser2", MAILBOX, uid, null, ORGANIZER).response());
  }

  /**
   * Nothing is held where a landing would not act: another UID, another user's
   * event, one occurrence of the meeting, another organiser's message, an event
   * the user organises, an event cancelled since, an event removed, a user
   * agenda cannot serve — and a forged key on another user's event is not
   * adopted.
   */
  @Test
  public void testHeldIsNullWhereALandingWouldNotAct() throws Exception {
    String uid = uid();
    LandedMailInvitation landed = land("testuser2", "REQUEST", uid, 0, EventAttendeeResponse.ACCEPTED, ics("REQUEST", uid, 0, ORGANIZER, "Sync", ""));
    restartTransaction();
    assertNotNull(landingService().held("testuser2", MAILBOX, uid, null, ORGANIZER));

    assertNull(landingService().held("testuser2", MAILBOX, uid(), null, ORGANIZER));
    assertNull(landingService().held("testuser3", MAILBOX, uid, null, ORGANIZER));
    assertNull(landingService().held("testuser2", MAILBOX, uid, "20301015T090000Z", ORGANIZER));
    assertNull(landingService().held("testuser2", MAILBOX, " ", null, ORGANIZER));
    assertNull(landingService().held("nobody-" + UUID.randomUUID(), MAILBOX, uid, null, ORGANIZER));
    assertNull("another organiser", landingService().held("testuser2", MAILBOX, uid, null, "intruder@else.example"));
    assertNull("no organiser", landingService().held("testuser2", MAILBOX, uid, null, null));
    assertNotNull("the organiser's address in any case",
                  landingService().held("testuser2", MAILBOX, uid, null, ORGANIZER.toUpperCase()));

    // An event the user organises is not told of from a mail; a published event
    // naming no organiser is.
    String mine = uid();
    land("testuser2", "PUBLISH", mine, 0, null, ics("PUBLISH", mine, 0, MAILBOX, "Mine", ""));
    restartTransaction();
    assertNull("the user's own", landingService().held("testuser2", MAILBOX, mine, null, MAILBOX));
    String published = uid();
    land("testuser2", "PUBLISH", published, 0, null, ics("PUBLISH", published, 0, null, "Published", ""));
    restartTransaction();
    assertNotNull("published", landingService().held("testuser2", MAILBOX, published, null, null));
    assertNull("published, claimed by an organiser", landingService().held("testuser2", MAILBOX, published, null, ORGANIZER));

    Event event = agendaEventService.getEventById(landed.eventId());
    event.setStatus(EventStatus.CANCELLED);
    event.setVisibility(null);
    long user = identityOf("testuser2");
    agendaEventService.updateEvent(event,
                                   agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees(),
                                   List.of(),
                                   List.of(),
                                   null,
                                   null,
                                   false,
                                   user);
    restartTransaction();
    assertNull("cancelled since", landingService().held("testuser2", MAILBOX, uid, null, ORGANIZER));

    agendaEventService.deleteEventById(landed.eventId(), user);
    restartTransaction();
    assertNull("removed", landingService().held("testuser2", MAILBOX, uid, null, ORGANIZER));

    // Another user's event carrying this user's key.
    String forgedUid = uid();
    long forger = identityOf("testuser3");
    owners.add(forger);
    Event forged = new Event();
    forged.setCalendarId(agendaCalendarService.getOrCreateCalendarByOwnerId(forger).getId());
    forged.setSummary("Forged");
    forged.setStart(ZonedDateTime.of(2030, 10, 15, 9, 0, 0, 0, ZoneOffset.UTC));
    forged.setEnd(ZonedDateTime.of(2030, 10, 15, 10, 0, 0, 0, ZoneOffset.UTC));
    forged.setTimeZoneId(ZoneOffset.UTC);
    Map<String, String> properties = new HashMap<>();
    properties.put(AgendaInvitationLandingService.UID_PROPERTY, AgendaInvitationLandingService.uidKey(user, forgedUid));
    forged.setParameters(properties);
    agendaEventService.createEvent(forged, List.of(), List.of(), List.of(), null, null, false, forger);
    restartTransaction();
    assertNull("forged", landingService().held("testuser2", MAILBOX, forgedUid, null, ORGANIZER));
  }

  /**
   * A user who does not exist has no calendar here.
   */
  @Test
  public void testAnUnknownUserHasNoCalendar() {
    assertFalse(landingService().holdsCalendarFor("nobody-" + UUID.randomUUID()));
    assertTrue(landingService().holdsCalendarFor("testuser2"));
  }

  /**
   * Lands an invitation for a user with their mailbox address.
   */
  private LandedMailInvitation land(String username,
                                    String method,
                                    String uid,
                                    int sequence,
                                    EventAttendeeResponse response,
                                    String object) {
    return land(username, method, uid, sequence, response, object, false);
  }

  /**
   * Lands an invitation for a user, the method read from the object when
   * asked.
   */
  private LandedMailInvitation land(String username,
                                    String method,
                                    String uid,
                                    int sequence,
                                    EventAttendeeResponse response,
                                    String object,
                                    boolean methodFromObject) {
    long owner = identityOf(username);
    // testuser1's calendar is the base test's own, removed by its tear-down
    if (!owners.contains(owner) && owner != calendar.getOwnerId()) {
      owners.add(owner);
    }
    restartTransaction();
    return landingService().land(new MailInvitation(username,
                                                    "testuser1".equals(username) ? "other@mail.example" : MAILBOX,
                                                    methodFromObject ? null : method,
                                                    uid,
                                                    null,
                                                    sequence,
                                                    response,
                                                    object));
  }

  /**
   * How many events of the user's personal calendar carry this invitation's
   * title, the UID being unique per test.
   */
  private int eventsOf(String username, String uid) throws Exception {
    long owner = identityOf(username);
    org.exoplatform.agenda.model.EventFilter filter = new org.exoplatform.agenda.model.EventFilter();
    filter.setOwnerIds(List.of(owner));
    filter.setStart(ZonedDateTime.of(2030, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    filter.setEnd(ZonedDateTime.of(2030, 11, 1, 0, 0, 0, 0, ZoneOffset.UTC));
    int count = 0;
    for (Event event : agendaEventService.getEvents(filter, ZoneOffset.UTC, owner)) {
      if ("Sync".equals(event.getSummary())) {
        count++;
      }
    }
    return count;
  }

  /**
   * The identity of a test user.
   */
  private long identityOf(String username) {
    return Long.parseLong(identityManager.getOrCreateIdentity("organization", username).getId());
  }

  /**
   * A fresh UID.
   */
  private static String uid() {
    return UUID.randomUUID() + "@partner.example";
  }

  /**
   * An invitation object.
   */
  private static String ics(String method, String uid, int sequence, String organizer, String summary, String extra) {
    return "BEGIN:VCALENDAR\r\n"
        + "VERSION:2.0\r\n"
        + "PRODID:-//Partner//Test//EN\r\n"
        + "METHOD:" + method + "\r\n"
        + "BEGIN:VEVENT\r\n"
        + "UID:" + uid + "\r\n"
        + "SEQUENCE:" + sequence + "\r\n"
        + "DTSTAMP:20300101T000000Z\r\n"
        + "DTSTART:20301015T090000Z\r\n"
        + "DTEND:20301015T100000Z\r\n"
        + "SUMMARY:" + summary + "\r\n"
        + (organizer == null ? "" : "ORGANIZER;CN=Boss:mailto:" + organizer + "\r\n")
        + "ATTENDEE;PARTSTAT=NEEDS-ACTION:mailto:" + MAILBOX + "\r\n"
        + extra
        + "END:VEVENT\r\n"
        + "END:VCALENDAR\r\n";
  }
}
