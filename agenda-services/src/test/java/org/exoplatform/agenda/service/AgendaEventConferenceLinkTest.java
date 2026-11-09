/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Test;

import org.exoplatform.agenda.dao.EventDAO;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.storage.AgendaEventConferenceStorage;
import org.exoplatform.agenda.util.ConferenceLinkSchemeTest;

/**
 * Pins that a web conference link running a script is refused by every save
 * of an event's conferences, before anything is written, while a link the
 * event already holds keeps flowing through the saves that pass the stored
 * list back.
 */
public class AgendaEventConferenceLinkTest extends BaseAgendaEventTest {

  private static final String SCRIPT_URL = "JavaScript:alert(1)";

  private static final String NEW_URL    = "https://meet.example.com/new";

  @Test
  public void testSaveEventConferencesRefusesScriptLinksBeforeAnyChange() throws Exception {
    long eventId = createEventWithConference().getId();

    for (String url : ConferenceLinkSchemeTest.SCRIPT_URLS) {
      List<EventConference> conferences = Collections.singletonList(conference(url));
      assertThrows(url, IllegalArgumentException.class,
                   () -> agendaEventConferenceService.saveEventConferences(eventId, conferences));
      assertStoredUrls(eventId, "conf_uri");
    }
  }

  @Test
  public void testSaveEventConferencesKeepsOtherLinks() throws Exception {
    long eventId = createEventWithConference().getId();

    List<EventConference> conferences = ConferenceLinkSchemeTest.OTHER_URLS.stream()
                                                                           .map(AgendaEventConferenceLinkTest::conference)
                                                                           .collect(Collectors.toList());
    agendaEventConferenceService.saveEventConferences(eventId, conferences);

    assertStoredUrls(eventId, ConferenceLinkSchemeTest.OTHER_URLS.toArray(new String[0]));
  }

  @Test
  public void testSaveEventConferencesKeepsAStoredScriptLinkPassedBackUnchanged() throws Exception {
    long eventId = createEventWithConference().getId();
    storeLegacyScriptLink(eventId);

    List<EventConference> conferences = new ArrayList<>(agendaEventConferenceService.getEventConferences(eventId));
    conferences.add(conference("https://meet.example.com/room"));
    agendaEventConferenceService.saveEventConferences(eventId, conferences);

    assertStoredUrls(eventId, "conf_uri", SCRIPT_URL, "https://meet.example.com/room");
  }

  @Test
  public void testCreateEventWithAScriptLinkIsRefusedAndWritesNothing() throws Exception {
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    Event event = newEventInstance(getDate(), getDate(), true);
    long eventCount = getEventDAO().count();

    assertThrows(IllegalArgumentException.class,
                 () -> agendaEventService.createEvent(event,
                                                      Collections.emptyList(),
                                                      Collections.singletonList(conference(SCRIPT_URL)),
                                                      Collections.emptyList(),
                                                      null,
                                                      null,
                                                      false,
                                                      userIdentityId));
    assertEquals(eventCount, getEventDAO().count().longValue());
  }

  @Test
  public void testUpdateEventWithAScriptLinkIsRefusedAndWritesNothing() throws Exception {
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    long eventId = createEventWithConference().getId();
    Event updatedEvent = agendaEventService.getEventById(eventId, ZoneOffset.UTC, userIdentityId).clone();
    updatedEvent.setSummary("updatedSummary");

    assertThrows(IllegalArgumentException.class,
                 () -> agendaEventService.updateEvent(updatedEvent,
                                                      Collections.emptyList(),
                                                      Collections.singletonList(conference(SCRIPT_URL)),
                                                      Collections.emptyList(),
                                                      null,
                                                      null,
                                                      false,
                                                      userIdentityId));
    assertEquals("eventSummary", agendaEventService.getEventById(eventId).getSummary());
    assertStoredUrls(eventId, "conf_uri");
  }

  @Test
  public void testUpdateEventKeepsAStoredScriptLinkPassedBackUnchanged() throws Exception {
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    long eventId = createEventWithConference().getId();
    storeLegacyScriptLink(eventId);
    Event updatedEvent = agendaEventService.getEventById(eventId, ZoneOffset.UTC, userIdentityId).clone();
    updatedEvent.setSummary("updatedSummary");

    agendaEventService.updateEvent(updatedEvent,
                                   Collections.emptyList(),
                                   agendaEventConferenceService.getEventConferences(eventId),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   userIdentityId);

    assertEquals("updatedSummary", agendaEventService.getEventById(eventId).getSummary());
    assertStoredUrls(eventId, "conf_uri", SCRIPT_URL);
  }

  @Test
  public void testExceptionalOccurrenceIsCreatedWithoutAStoredScriptLink() throws Exception {
    ZonedDateTime start = getDate();
    long seriesId = createEventWithConference().getId();
    storeLegacyScriptLink(seriesId);

    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(seriesId, start);

    assertNotNull(exceptionalOccurrence);
    assertStoredUrls(exceptionalOccurrence.getId(), "conf_uri");
  }

  @Test
  public void testCreateEventExceptionalOccurrenceWithAScriptLinkIsRefusedAndWritesNothing() throws Exception {
    ZonedDateTime start = getDate();
    long seriesId = createEventWithConference().getId();
    long eventCount = getEventDAO().count();

    assertThrows(IllegalArgumentException.class,
                 () -> agendaEventService.createEventExceptionalOccurrence(seriesId,
                                                                           Collections.emptyList(),
                                                                           Collections.singletonList(conference(SCRIPT_URL)),
                                                                           Collections.emptyList(),
                                                                           start));
    assertEquals(eventCount, getEventDAO().count().longValue());
  }

  /**
   * A date the platform copied from a series holding a refused link has the
   * series' other links only. It is still a plain copy of the series: when the
   * series' links change, the date is not kept as a date of its own, and a
   * new copy carries the series' new links.
   */
  @Test
  public void testDateCopiedWithoutAStoredScriptLinkFollowsTheSeries() throws Exception {
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    ZonedDateTime start = getDate();
    long seriesId = createEventWithConference().getId();
    storeLegacyScriptLink(seriesId);
    Event copiedDate = agendaEventService.saveEventExceptionalOccurrence(seriesId, start);
    ZonedDateTime occurrenceId = copiedDate.getOccurrence().getId();

    updateSeriesConferences(seriesId, userIdentityId, Collections.singletonList(conference(NEW_URL)));

    // Dropped as a plain copy of the series, then copied again from it
    assertNull(agendaEventStorage.getEventById(copiedDate.getId()));
    Event copiedAgain = agendaEventService.saveEventExceptionalOccurrence(seriesId, occurrenceId);
    assertStoredUrls(copiedAgain.getId(), NEW_URL);
  }

  /**
   * A date kept for a change of its own still takes the series' new links when
   * it held the series' links as the platform copied them.
   */
  @Test
  public void testDateWithAChangeOfItsOwnTakesTheSeriesNewLink() throws Exception {
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    ZonedDateTime start = getDate();
    long seriesId = createEventWithConference().getId();
    storeLegacyScriptLink(seriesId);
    Event date = agendaEventService.saveEventExceptionalOccurrence(seriesId, start);
    date.setSummary("dateSummary");
    agendaEventStorage.updateEvent(date);

    updateSeriesConferences(seriesId, userIdentityId, Collections.singletonList(conference(NEW_URL)));

    assertEquals("dateSummary", agendaEventService.getEventById(date.getId()).getSummary());
    assertStoredUrls(date.getId(), NEW_URL);
  }

  /**
   * A series that keeps its refused link next to a new one hands the date the
   * links it may hold, not the refused one.
   */
  @Test
  public void testSeriesKeepingItsScriptLinkHandsTheDateTheOtherLinks() throws Exception {
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    ZonedDateTime start = getDate();
    long seriesId = createEventWithConference().getId();
    storeLegacyScriptLink(seriesId);
    Event date = agendaEventService.saveEventExceptionalOccurrence(seriesId, start);
    date.setSummary("dateSummary");
    agendaEventStorage.updateEvent(date);

    List<EventConference> conferences = new ArrayList<>(agendaEventConferenceService.getEventConferences(seriesId));
    conferences.add(conference(NEW_URL));
    updateSeriesConferences(seriesId, userIdentityId, conferences);

    assertStoredUrls(seriesId, "conf_uri", SCRIPT_URL, NEW_URL);
    assertStoredUrls(date.getId(), "conf_uri", NEW_URL);
  }

  private void updateSeriesConferences(long seriesId,
                                       long userIdentityId,
                                       List<EventConference> conferences) throws Exception {
    // Only the links change: attendees and reminders are saved as they are
    Event series = agendaEventService.getEventById(seriesId, ZoneOffset.UTC, userIdentityId).clone();
    agendaEventService.updateEvent(series,
                                   agendaEventAttendeeService.getEventAttendees(seriesId).getEventAttendees(),
                                   conferences,
                                   agendaEventReminderService.getEventReminders(seriesId, userIdentityId),
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
  }

  private Event createEventWithConference() throws Exception {
    ZonedDateTime start = getDate();
    Event event = newEventInstance(start, start, true);
    return createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);
  }

  private void storeLegacyScriptLink(long eventId) {
    EventConference conference = conference(SCRIPT_URL);
    conference.setEventId(eventId);
    container.getComponentInstanceOfType(AgendaEventConferenceStorage.class).saveEventConference(conference);
  }

  private void assertStoredUrls(long eventId, String... expectedUrls) {
    List<String> storedUrls = agendaEventConferenceService.getEventConferences(eventId)
                                                          .stream()
                                                          .map(EventConference::getUrl)
                                                          .sorted()
                                                          .collect(Collectors.toList());
    List<String> urls = new ArrayList<>(List.of(expectedUrls));
    Collections.sort(urls);
    assertEquals(urls, storedUrls);
  }

  private EventDAO getEventDAO() {
    return container.getComponentInstanceOfType(EventDAO.class);
  }

  private static EventConference conference(String url) {
    return new EventConference(0, 0, "webrtc", url, null, null, null);
  }

}
