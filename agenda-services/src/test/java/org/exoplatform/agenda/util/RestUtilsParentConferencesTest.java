/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.util;

import static org.junit.Assert.*;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.services.rest.ApplicationContext;
import org.exoplatform.services.rest.impl.ApplicationContextImpl;
import org.exoplatform.services.security.ConversationState;

/**
 * Pins what the {@code parentAll} expand puts on the parent of a recurrent
 * event's occurrence.
 *
 * <p>
 * The parent is not a display detail here: an answer given for the whole
 * series acts on it, and it is the object the calendar connectors copy to the
 * user's connected Google or Microsoft 365 account
 * ({@code AgendaEventDialog.vue} emits {@code this.event.parent},
 * {@code RemoteEventConnector.js} keeps it, and each connector's
 * {@code buildConnectorEvent} reads {@code event.conferences} off it). Filled
 * with no conferences, it reached them stripped of the event's visio link and
 * the copy carried no way back to the meeting — EXO-90015.
 * </p>
 *
 * <p>
 * The case that matters is the <b>computed</b> occurrence — id 0, no row of
 * its own ({@code Utils.getOccurrences} clones the parent and zeroes the id),
 * which is what a user answering a series is looking at unless that one
 * occurrence was edited apart. It is precisely the case the ACL branch beside
 * this fill skips, which is why the fill sits outside that guard.
 * </p>
 */
public class RestUtilsParentConferencesTest extends BaseAgendaEventTest {

  /**
   * The entity rebuild reads the current user through the social
   * {@code EntityBuilder}, which requires an authenticated conversation state
   * and a bound JAX-RS application context — it caches an ETag on the latter
   * while building an identity. Every real REST call has both.
   */
  @Before
  public void setUpRestContext() {
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(testuser1Identity.getRemoteId())));
    ApplicationContext applicationContext = Mockito.mock(ApplicationContext.class);
    Mockito.when(applicationContext.getProperties()).thenReturn(new HashMap<>());
    Mockito.when(applicationContext.getBaseUri()).thenReturn(URI.create("http://localhost:8080/portal/rest"));
    ApplicationContextImpl.setCurrent(applicationContext);
  }

  /**
   * Clears the contexts installed for the test.
   */
  @After
  public void tearDownRestContext() {
    ApplicationContextImpl.setCurrent(null);
    ConversationState.setCurrent(null);
  }

  /**
   * The parent of a computed occurrence must carry the event's conferences
   * when {@code parentAll} is asked for, so that the series-level answer the
   * connectors push carries the visio link.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testParentOfComputedOccurrenceCarriesConferences() throws Exception { // NOSONAR
    Event event = createRecurrentEventWithConference();
    Event occurrence = firstOccurrenceOf(event);

    assertEquals("The occurrence this test is about must be a computed one, with no row of its own",
                 0,
                 occurrence.getId());

    EventEntity eventEntity = buildEntity(event, occurrence, Arrays.asList("all", "parentAll"));

    assertNotNull(eventEntity.getParent());
    List<org.exoplatform.agenda.model.EventConference> parentConferences = eventEntity.getParent().getConferences();
    assertNotNull("The parent an answer for the whole series pushes must carry the conferences", parentConferences);
    assertEquals(1, parentConferences.size());
    assertEquals("conf_uri", parentConferences.get(0).getUrl());
  }

  /**
   * The occurrence itself already carried the conferences, and must keep
   * doing so — the fix adds to the parent, it moves nothing.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testOccurrenceKeepsItsOwnConferences() throws Exception { // NOSONAR
    Event event = createRecurrentEventWithConference();
    Event occurrence = firstOccurrenceOf(event);

    EventEntity eventEntity = buildEntity(event, occurrence, Arrays.asList("all", "parentAll"));

    assertNotNull(eventEntity.getConferences());
    assertEquals(1, eventEntity.getConferences().size());
    assertEquals("conf_uri", eventEntity.getConferences().get(0).getUrl());
  }

  /**
   * Nothing is filled on the parent when {@code parentAll} was not asked for
   * — the fill is scoped to the expand that requests it.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testParentCarriesNothingWithoutParentAll() throws Exception { // NOSONAR
    Event event = createRecurrentEventWithConference();
    Event occurrence = firstOccurrenceOf(event);

    EventEntity eventEntity = buildEntity(event, occurrence, Arrays.asList("all"));

    assertNotNull(eventEntity.getParent());
    assertNull("Without parentAll the parent must stay as bare as it was",
               eventEntity.getParent().getConferences());
  }

  private Event createRecurrentEventWithConference() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0).plusDays(1);
    Event event = newEventInstance(start, start.plusHours(1), false);
    assertNotNull("newEventInstance must give a recurrent event for this test to mean anything",
                  event.getRecurrence());
    return createEvent(event.clone(),
                       Long.parseLong(testuser1Identity.getId()),
                       testuser1Identity,
                       testuser2Identity);
  }

  private Event firstOccurrenceOf(Event event) throws IllegalAccessException {
    Event occurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                             event.getStart().withZoneSameInstant(event.getTimeZoneId()),
                                                             event.getTimeZoneId(),
                                                             Long.parseLong(testuser1Identity.getId()));
    assertNotNull(occurrence);
    return occurrence;
  }

  private EventEntity buildEntity(Event event, Event occurrence, List<String> expandProperties) {
    return RestUtils.getEventEntity(identityManager,
                                    agendaCalendarService,
                                    agendaEventService,
                                    agendaRemoteEventService,
                                    agendaEventDatePollService,
                                    agendaEventReminderService,
                                    agendaEventConferenceService,
                                    agendaEventAttendeeService,
                                    occurrence,
                                    occurrence.getOccurrence().getId(),
                                    event.getTimeZoneId(),
                                    expandProperties);
  }
}
