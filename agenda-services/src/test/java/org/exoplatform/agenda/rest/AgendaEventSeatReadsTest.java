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
package org.exoplatform.agenda.rest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.net.URI;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
import org.exoplatform.agenda.rest.model.EventAttendeeEntity;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.RestUtils;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.services.rest.ApplicationContext;
import org.exoplatform.services.rest.impl.ApplicationContextImpl;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;

/**
 * Pins the reads of a series' seats and attendees over REST, on the
 * container: a date shows the attendees valid there, a series read with no
 * date each attendee once, and the detail reads the seats of the date shown.
 */
public class AgendaEventSeatReadsTest extends BaseAgendaEventTest {

  private AgendaEventAttendeeStorage attendeeStorage;

  /**
   * The entity build reads the current user through the social
   * {@code EntityBuilder}, which requires an authenticated conversation state
   * and a bound JAX-RS application context, as every real REST call has.
   */
  @Before
  public void setUpRestContext() {
    attendeeStorage = container.getComponentInstanceOfType(AgendaEventAttendeeStorage.class);
    readAs(testuser1Identity);
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
   * A date shows the attendees valid there, though the series holds a single
   * row: a list of one row ranged away from the date shows nobody, read
   * alone or from the rows a list read keeps per series.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void aDateShowsNobodyWhenTheSeriesOnlyRowIsRangedAwayFromIt() throws Exception {
    Event series = series(LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC), 4, testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(), testuser2Identity, new EventAttendee(0, 0, 0, dates.get(2), null, EventAttendeeResponse.ACCEPTED, null));

    assertEquals(List.of(), identitiesOf(entityOf(series, dates.get(0), "attendees")));
    assertEquals(List.of(testuser2Identity.getId()), identitiesOf(entityOf(series, dates.get(2), "attendees")));
    assertEquals(List.of(testuser2Identity.getId()), identitiesOf(entityOf(series, dates.get(3), "attendees")));

    EventEntity listed = entityOf(series, dates.get(0), "conferences");
    Map<Long, EventAttendeeList> attendeesBySeries = new HashMap<>();
    attendeesBySeries.put(series.getId(), agendaEventAttendeeService.getEventAttendees(series.getId()));
    RestUtils.fillAttendees(identityManager, agendaEventAttendeeService, listed, dates.get(0), attendeesBySeries, 0);
    assertEquals(List.of(), identitiesOf(listed));
  }

  /**
   * A series read with no date shows each attendee once, with the row valid
   * at its next date, not their first row; the reader alone sees theirs.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aSeriesReadWithNoDateShowsEachAttendeeOnceAtItsNextDate() throws Exception {
    ZonedDateTime start = LocalDate.now().minusDays(10).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event series = series(start, 20, testuser2Identity, testuser3Identity);
    agendaEventAttendeeService.sendEventResponse(series.getId(), id(testuser2Identity), EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                         start.plusDays(5),
                                                         id(testuser2Identity),
                                                         EventAttendeeResponse.DECLINED);

    EventEntity all = entityOf(series, null, "attendees");
    assertEquals(List.of(testuser2Identity.getId(), testuser3Identity.getId()), identitiesOf(all).stream().sorted().toList());
    assertEquals(EventAttendeeResponse.DECLINED, responseOf(all, testuser2Identity));

    readAs(testuser2Identity);
    EventEntity own = entityOf(series, null, "response");
    assertEquals(List.of(testuser2Identity.getId()), identitiesOf(own));
    assertEquals(EventAttendeeResponse.DECLINED, responseOf(own, testuser2Identity));
  }

  /**
   * A series read with no date shows an attendee whose rows all start after
   * its next date once, answering nothing there, in the full list and to
   * themselves.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void aSeriesReadWithNoDateShowsAnAttendeeAnsweringFromALaterDateWithNoAnswer() throws Exception {
    Event series = series(LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC),
                          4,
                          testuser2Identity,
                          testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(), testuser3Identity, new EventAttendee(0, 0, 0, dates.get(2), null, EventAttendeeResponse.ACCEPTED, null));

    EventEntity all = entityOf(series, null, "attendees");
    assertEquals(List.of(testuser2Identity.getId(), testuser3Identity.getId()), identitiesOf(all).stream().sorted().toList());
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(all, testuser3Identity));

    readAs(testuser3Identity);
    EventEntity own = entityOf(series, null, "response");
    assertEquals(List.of(testuser3Identity.getId()), identitiesOf(own));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(own, testuser3Identity));
  }

  /**
   * The detail reads carry the seats of the date they show: a date with no
   * row of its own is counted on the series at that date, and a series read
   * with no date at its next date, with the reader's rank when they wait.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void theDetailReadsCountTheSeatsOfTheDateShown() throws Exception {
    Event series = series(LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC),
                          4,
                          testuser1Identity,
                          testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(), testuser1Identity, new EventAttendee(0, 0, 0, null, null, EventAttendeeResponse.WAITING, null));
    replaceRows(series.getId(),
                testuser2Identity,
                new EventAttendee(0, 0, 0, null, dates.get(1), EventAttendeeResponse.DECLINED, null),
                new EventAttendee(0, 0, 0, dates.get(1), null, EventAttendeeResponse.ACCEPTED, null));
    AgendaEventRest eventRest = eventRest();

    Response occurrence = eventRest.getEventOccurrence(series.getId(), AgendaDateUtils.toRFC3339Date(dates.get(1)), null, null);
    EventEntity atSecondDate = (EventEntity) occurrence.getEntity();
    assertEquals(1, atSecondDate.getAcceptedCount());
    assertEquals(1, atSecondDate.getWaitingCount());
    assertEquals(Integer.valueOf(1), atSecondDate.getWaitingListPosition());

    Response whole = eventRest.getEventById(series.getId(), null, null, false);
    EventEntity atNextDate = (EventEntity) whole.getEntity();
    assertEquals(0, atNextDate.getAcceptedCount());
    assertEquals(1, atNextDate.getWaitingCount());

    readAs(testuser2Identity);
    EventEntity readByAnother = (EventEntity) eventRest.getEventOccurrence(series.getId(),
                                                                           AgendaDateUtils.toRFC3339Date(dates.get(1)),
                                                                           null,
                                                                           null)
                                                       .getEntity();
    assertNull(readByAnother.getWaitingListPosition());
  }

  /**
   * The read of a series' first date counts the seats of its last date when
   * no other is left, for a series in UTC+10 from 08:00 to 09:00 local time,
   * whose last date ends on the UTC day before its own: the earlier dates'
   * Yes are not counted, before the date has a row of its own and after.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void theFirstDateReadCountsTheLastDateWhenNoOtherIsLeft() throws Exception {
    ZoneId brisbane = ZoneId.of("Australia/Brisbane");
    ZonedDateTime start = LocalDate.now(brisbane).minusDays(3).atTime(8, 0).atZone(brisbane);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(brisbane);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(3);
    event.getRecurrence().setInterval(2);
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity, testuser3Identity, testuser4Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    assertEquals("three dates, two days apart, the last one tomorrow in Brisbane", 3, dates.size());
    ZonedDateTime lastDate = dates.get(2);
    replaceRows(series.getId(),
                testuser2Identity,
                new EventAttendee(0, 0, 0, null, lastDate, EventAttendeeResponse.DECLINED, null),
                new EventAttendee(0, 0, 0, lastDate, null, EventAttendeeResponse.ACCEPTED, null));
    for (Identity identity : List.of(testuser3Identity, testuser4Identity)) {
      replaceRows(series.getId(),
                  identity,
                  new EventAttendee(0, 0, 0, null, lastDate, EventAttendeeResponse.ACCEPTED, null),
                  new EventAttendee(0, 0, 0, lastDate, null, EventAttendeeResponse.DECLINED, null));
    }
    AgendaEventRest eventRest = eventRest();

    Response beforeItsRow = eventRest.getEventById(series.getId(), null, null, true);
    assertEquals(200, beforeItsRow.getStatus());
    assertEquals(1, ((EventEntity) beforeItsRow.getEntity()).getAcceptedCount());

    agendaEventService.saveEventExceptionalOccurrence(series.getId(), lastDate);
    Response afterItsRow = eventRest.getEventById(series.getId(), null, null, true);
    assertEquals(200, afterItsRow.getStatus());
    assertEquals(1, ((EventEntity) afterItsRow.getEntity()).getAcceptedCount());
  }

  /**
   * A masked event shows its time and nothing else: no seat count reaches the
   * reader.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void aMaskedEventShowsNoSeats() throws Exception {
    Event series = series(LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC), 4, testuser2Identity);
    replaceRows(series.getId(), testuser2Identity, new EventAttendee(0, 0, 0, null, null, EventAttendeeResponse.ACCEPTED, null));
    EventEntity masked = entityOf(series, null, "conferences");
    masked.setMasked(true);

    RestUtils.fillSeatCounts(agendaEventAttendeeService, masked, id(testuser1Identity));

    assertEquals(0, masked.getAcceptedCount());
  }

  private AgendaEventRest eventRest() {
    return new AgendaEventRest(identityManager,
                               Mockito.mock(UserPortalConfigService.class),
                               agendaCalendarService,
                               agendaEventService,
                               agendaEventConferenceService,
                               agendaRemoteEventService,
                               agendaEventDatePollService,
                               agendaEventReminderService,
                               agendaEventAttendeeService,
                               Mockito.mock(PortalContainer.class));
  }

  /**
   * A daily series at the start's time with a count, in the first test user's
   * calendar, inviting the attendees given.
   */
  private Event series(ZonedDateTime start, int count, Identity... attendees) throws Exception {
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(count);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * The occurrence ids of a series' dates, as the occurrence list gives them.
   */
  private List<ZonedDateTime> occurrenceIds(Event series) {
    Event stored = agendaEventService.getEventById(series.getId());
    ZonedDateTime start = stored.getStart();
    return agendaEventService.getEventOccurrencesInPeriod(stored, start.minusDays(1), start.plusDays(30), stored.getTimeZoneId(), 0)
                             .stream()
                             .map(occurrence -> occurrence.getOccurrence().getId())
                             .collect(Collectors.toList());
  }

  /** What the organiser does from the participants drawer. */
  private void replaceRows(long eventId, Identity identity, EventAttendee... rows) {
    ZonedDateTime responseDate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
    agendaEventAttendeeService.getEventAttendees(eventId)
                              .getEventAttendees(id(identity))
                              .forEach(row -> attendeeStorage.removeEventAttendee(row.getId()));
    for (EventAttendee row : rows) {
      attendeeStorage.saveEventAttendee(new EventAttendee(0,
                                                          eventId,
                                                          id(identity),
                                                          row.getFromOccurrenceId(),
                                                          row.getUntilOccurrenceId(),
                                                          row.getResponse(),
                                                          responseDate),
                                        eventId);
    }
  }

  /**
   * The entity of a series, of one of its dates when one is given, as the
   * detail reads build it.
   */
  private EventEntity entityOf(Event series, ZonedDateTime date, String expand) throws Exception {
    Event event = date == null ? agendaEventService.getEventById(series.getId())
                               : agendaEventService.getEventOccurrence(series.getId(), date, ZoneOffset.UTC, id(testuser1Identity));
    return RestUtils.getEventEntity(identityManager,
                                    agendaCalendarService,
                                    agendaEventService,
                                    agendaRemoteEventService,
                                    agendaEventDatePollService,
                                    agendaEventReminderService,
                                    agendaEventConferenceService,
                                    agendaEventAttendeeService,
                                    event,
                                    date,
                                    ZoneOffset.UTC,
                                    List.of(expand));
  }

  private void readAs(Identity identity) {
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(identity.getRemoteId())));
  }

  private static List<String> identitiesOf(EventEntity eventEntity) {
    return eventEntity.getAttendees() == null ? List.of()
                                              : eventEntity.getAttendees()
                                                           .stream()
                                                           .map(attendee -> attendee.getIdentity().getId())
                                                           .toList();
  }

  private static EventAttendeeResponse responseOf(EventEntity eventEntity, Identity identity) {
    return eventEntity.getAttendees()
                      .stream()
                      .filter(attendee -> attendee.getIdentity().getId().equals(identity.getId()))
                      .map(EventAttendeeEntity::getResponse)
                      .findFirst()
                      .orElseThrow();
  }

  private static long id(Identity identity) {
    return Long.parseLong(identity.getId());
  }
}
