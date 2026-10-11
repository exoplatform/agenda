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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TransactionRequiredException;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.model.EventResponseOutcome;
import org.exoplatform.agenda.service.RecordingStatementInspector.RecordedStatement;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.identity.model.Identity;

/**
 * Pins the seat rule on the pools of one date — an event of no series, and a
 * date of a series with its own row — on the container: a Yes takes a free
 * seat or keeps the one the attendee holds, and waits on a full date
 * otherwise; an answer that leaves the attendee fewer seats (a seat given
 * up, seated rows merged) promotes the waiting list in the same transaction
 * while seats are free; an answer stored as a Yes that leaves the pool full
 * while unmarked marks it; a reset promotes nobody; and the date's row an
 * answer or a save of one date needs is found under the lock of the series
 * before one is created.
 * <p>
 * The capacity is written with a targeted update, never by saving back an
 * event read from storage, which would re-save its recurrence's end in the
 * JVM's zone. The series start in sixty days, so that no date of them is
 * materialised by the reminder computing listener when they are created.
 * <p>
 * <b>What this does not verify.</b> The container's HSQLDB runs in MVCC,
 * where a {@code SELECT … FOR UPDATE} does not make a second one wait: two
 * Yes on the last seat answered at once are not serialised here. That is
 * the two-session check on MySQL and PostgreSQL the delivery records in its
 * pull request.
 */
public class AgendaSeatRuleTest extends BaseAgendaEventTest {

  private static final List<String>    WATCHED    = List.of(Utils.POST_EVENT_RESPONSE_SAVED,
                                                            Utils.POST_EVENT_RESPONSE_SENT,
                                                            Utils.POST_EVENT_ATTENDEE_PROMOTED,
                                                            Utils.POST_EVENT_CAPACITY_REACHED);

  private static final List<Broadcast> BROADCASTS = Collections.synchronizedList(new ArrayList<>());

  private static boolean               listenersRegistered;

  private EntityManagerService         entityManagerService;

  private AgendaEventAttendeeStorage   attendeeStorage;

  /**
   * Registers, once for the container, the listeners recording the answer,
   * promotion and capacity events.
   */
  @Before
  public void registerListeners() {
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);
    attendeeStorage = container.getComponentInstanceOfType(AgendaEventAttendeeStorage.class);
    if (!listenersRegistered) {
      for (String eventName : WATCHED) {
        listenerService.addListener(eventName, new Listener<Object, Object>() {
          @Override
          public void onEvent(org.exoplatform.services.listener.Event<Object, Object> event) {
            BROADCASTS.add(new Broadcast(eventName, event.getSource(), event.getData()));
          }
        });
      }
      listenersRegistered = true;
    }
    BROADCASTS.clear();
  }

  /**
   * Leaves no recording behind.
   */
  @After
  public void clearRecordings() {
    BROADCASTS.clear();
    RecordingStatementInspector.stop();
  }

  /**
   * On a full event a Yes is stored as a place on the waiting list, and the
   * answer reports what it stored.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesOnAFullEventWaits() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity);
    answer(event.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    EventResponseOutcome outcome = agendaEventAttendeeService.sendEventResponse(event.getId(),
                                                                                null,
                                                                                id(testuser3Identity),
                                                                                EventAttendeeResponse.ACCEPTED,
                                                                                true);

    assertEquals(EventAttendeeResponse.WAITING, outcome.response());
    assertNull("an event of no series has no date to open", outcome.firstWaitingOccurrenceId());
    assertEquals(EventAttendeeResponse.WAITING, responseOf(event.getId(), testuser3Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(event.getId(), testuser2Identity));
    assertEquals(EventAttendeeResponse.WAITING,
                 ((EventAttendee) only(Utils.POST_EVENT_RESPONSE_SAVED).data()).getResponse());
  }

  /**
   * A place on the waiting list asked while a seat is free is a Yes, and so
   * is any on an event with no limit.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aPlaceOnTheWaitingListAskedWithAFreeSeatIsAYes() throws Exception {
    Event limited = limitedEvent(2, testuser2Identity);
    Event unlimited = limitedEvent(0, testuser3Identity);

    answer(limited.getId(), testuser2Identity, EventAttendeeResponse.WAITING);
    answer(unlimited.getId(), testuser3Identity, EventAttendeeResponse.WAITING);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(limited.getId(), testuser2Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(unlimited.getId(), testuser3Identity));
  }

  /**
   * An attendee who holds a seat and answers Yes again keeps it, with the
   * date of their answer, which only a change of answer moves: a Yes given
   * again after a No is dated anew.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aSeatedAttendeeKeepsTheSeatAndTheDateOfTheAnswer() throws Exception {
    Event event = limitedEvent(1, testuser2Identity);
    answer(event.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    ZonedDateTime answeredAt = rowOf(event.getId(), testuser2Identity).getResponseDate();
    assertNotNull(answeredAt);

    answer(event.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventAttendee row = rowOf(event.getId(), testuser2Identity);
    assertEquals(EventAttendeeResponse.ACCEPTED, row.getResponse());
    assertEquals(answeredAt.toInstant(), row.getResponseDate().toInstant());

    answer(event.getId(), testuser2Identity, EventAttendeeResponse.DECLINED);
    Thread.sleep(5);
    answer(event.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue("a Yes given again after a No is dated anew",
               rowOf(event.getId(), testuser2Identity).getResponseDate().isAfter(answeredAt));
  }

  /**
   * A Maybe never takes a seat.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aMaybeNeverTakesASeat() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity);

    answer(event.getId(), testuser2Identity, EventAttendeeResponse.TENTATIVE);
    answer(event.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.TENTATIVE, responseOf(event.getId(), testuser2Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(event.getId(), testuser3Identity));
  }

  /**
   * A No from a seated attendee gives the seat to the attendee who has waited
   * longest, by the date of their answer, in the transaction of the No: the
   * promotion is reported as saved and as promoted, never as an answer the
   * promoted attendee sent, and is dated when it happens.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aNoFromASeatPromotesTheLongestWaitingInTheSameTransaction() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity, testuser4Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser4Identity, EventAttendeeResponse.ACCEPTED);
    // The second to answer has waited longer than the first: the order is the
    // answer date's, not the row's
    EventAttendee firstToWait = rowOf(eventId, testuser3Identity);
    EventAttendee secondToWait = rowOf(eventId, testuser4Identity);
    ZonedDateTime longestWaiting = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
    secondToWait.setResponseDate(longestWaiting);
    attendeeStorage.saveEventAttendee(secondToWait, eventId);
    firstToWait.setResponseDate(longestWaiting.plusHours(1));
    attendeeStorage.saveEventAttendee(firstToWait, eventId);
    BROADCASTS.clear();

    List<RecordedStatement> statements = record(() -> answer(eventId, testuser2Identity, EventAttendeeResponse.DECLINED));

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser4Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));
    assertTrue("the promotion is dated when it happens",
               rowOf(eventId, testuser4Identity).getResponseDate().isAfter(longestWaiting.plusHours(1)));
    Broadcast promoted = only(Utils.POST_EVENT_ATTENDEE_PROMOTED);
    assertEquals(id(testuser4Identity), ((EventAttendee) promoted.data()).getIdentityId());
    assertEquals(EventAttendeeResponse.WAITING, ((EventAttendee) promoted.source()).getResponse());
    assertEquals("only the No is an answer sent",
                 List.of(id(testuser2Identity)),
                 identitiesOf(Utils.POST_EVENT_RESPONSE_SENT));
    assertTrue("the promoted attendee's answer is reported as saved",
               identitiesOf(Utils.POST_EVENT_RESPONSE_SAVED).contains(id(testuser4Identity)));
    List<RecordedStatement> attendeeWrites = statements.stream()
                                                       .filter(AgendaSeatRuleTest::isAttendeeWrite)
                                                       .toList();
    assertTrue("the No and the promotion are both written: " + attendeeWrites, attendeeWrites.size() >= 2);
    assertTrue("an attendee row was written outside the No's transaction: " + attendeeWrites,
               attendeeWrites.stream().allMatch(RecordedStatement::inTransaction));
  }

  /**
   * An answer to every date of a pool of one date replaces every row of the
   * attendee, a ranged pair kept from when the event was a series included:
   * when the seat is held by a row other than the first one, a Yes again
   * keeps it, and a No gives it up and promotes the waiting list.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anAnswerFromAnAttendeeWhoseLaterRowHoldsTheSeatCountsThatSeat() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity, testuser4Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));
    // Left from an older shape of the data, the pool over its capacity
    EventAttendee overCapacity = rowOf(eventId, testuser4Identity);
    overCapacity.setResponse(EventAttendeeResponse.ACCEPTED);
    attendeeStorage.saveEventAttendee(overCapacity, eventId);
    splitAtDate(eventId, testuser2Identity, event.getStart(), EventAttendeeResponse.DECLINED, EventAttendeeResponse.ACCEPTED);
    assertEquals("the row read first is the declined one",
                 EventAttendeeResponse.DECLINED,
                 responseOf(eventId, testuser2Identity));

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals("a Yes again keeps the seat held by the later row",
                 EventAttendeeResponse.ACCEPTED,
                 responseOf(eventId, testuser2Identity));

    EventAttendee underCapacity = rowOf(eventId, testuser4Identity);
    underCapacity.setResponse(EventAttendeeResponse.DECLINED);
    attendeeStorage.saveEventAttendee(underCapacity, eventId);
    splitAtDate(eventId, testuser2Identity, event.getStart(), EventAttendeeResponse.DECLINED, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser2Identity, EventAttendeeResponse.DECLINED);
    assertEquals("a No gives up the seat held by the later row",
                 EventAttendeeResponse.ACCEPTED,
                 responseOf(eventId, testuser3Identity));
  }

  /**
   * Two seated rows of one attendee, kept from when the event was a series,
   * count as two seats: an answer to every date merges them into one, and
   * the seat it frees goes to the waiting list.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesMergingTwoSeatedRowsGivesTheFreedSeatToTheWaitingList() throws Exception {
    Event event = limitedEvent(2, testuser2Identity, testuser3Identity);
    long eventId = event.getId();
    splitAtDate(eventId, testuser2Identity, event.getStart(), EventAttendeeResponse.ACCEPTED, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals("two seated rows fill the pool", EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser2Identity));
    assertEquals(1, agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(id(testuser2Identity)).size());
    assertEquals("the freed seat goes to the waiting list",
                 EventAttendeeResponse.ACCEPTED,
                 responseOf(eventId, testuser3Identity));
  }

  /**
   * The date of an answer changes only with the answer: a row whose answer
   * has not changed since the date was recorded keeps its null date when the
   * same answer is given again.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anUnchangedAnswerKeepsANullAnswerDate() throws Exception {
    Event event = limitedEvent(2, testuser2Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    EventAttendee row = rowOf(eventId, testuser2Identity);
    row.setResponseDate(null);
    attendeeStorage.saveEventAttendee(row, eventId);
    assertNull(rowOf(eventId, testuser2Identity).getResponseDate());

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser2Identity));
    assertNull(rowOf(eventId, testuser2Identity).getResponseDate());
  }

  /**
   * Rows of an attendee that all hold the answer given again merge into a
   * row that keeps the earliest of their dates, a missing date counting as
   * the earliest: here the row read second, which has none.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anUnchangedAnswerOverSeveralRowsKeepsTheirNullAnswerDate() throws Exception {
    Event event = limitedEvent(2, testuser2Identity);
    long eventId = event.getId();
    splitAtDate(eventId, testuser2Identity, event.getStart(), EventAttendeeResponse.ACCEPTED, EventAttendeeResponse.ACCEPTED);
    EventAttendee readSecond = agendaEventAttendeeService.getEventAttendees(eventId)
                                                         .getEventAttendees(id(testuser2Identity))
                                                         .stream()
                                                         .filter(row -> row.getFromOccurrenceId() != null)
                                                         .findFirst()
                                                         .orElseThrow();
    readSecond.setResponseDate(null);
    attendeeStorage.saveEventAttendee(readSecond, eventId);

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(id(testuser2Identity)).size());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser2Identity));
    assertNull(rowOf(eventId, testuser2Identity).getResponseDate());
  }

  /**
   * Rows of an attendee that all wait, kept from when the event was a
   * series, keep the earliest of their places on the waiting list when the
   * attendee asks again for every date, not the place of the row read first:
   * the attendee stays ahead of those who asked after them.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesAgainKeepsTheEarliestPlaceOnTheWaitingListOfItsRows() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity, testuser4Identity);
    long eventId = event.getId();
    answer(eventId, testuser4Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    EventAttendee waiting = rowOf(eventId, testuser3Identity);
    waiting.setResponseDate(now.minusMinutes(90));
    attendeeStorage.saveEventAttendee(waiting, eventId);
    // Both rows wait, the one read first since after the other attendee
    splitAtDate(eventId, testuser2Identity, event.getStart(), EventAttendeeResponse.WAITING, EventAttendeeResponse.WAITING);
    EventAttendee readFirst = rowOf(eventId, testuser2Identity);
    readFirst.setResponseDate(now.minusHours(1));
    attendeeStorage.saveEventAttendee(readFirst, eventId);

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser2Identity));

    answer(eventId, testuser4Identity, EventAttendeeResponse.DECLINED);
    assertEquals("the earliest place of its rows is kept",
                 EventAttendeeResponse.ACCEPTED,
                 responseOf(eventId, testuser2Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));
  }

  /**
   * An answer to every date that changes the answer of some of the
   * attendee's rows, a Yes again after a No on some dates, gets a new date,
   * though another of those rows already held that answer.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesAfterANoOnSomeRowsGetsANewAnswerDate() throws Exception {
    Event event = limitedEvent(2, testuser2Identity);
    long eventId = event.getId();
    splitAtDate(eventId, testuser2Identity, event.getStart(), EventAttendeeResponse.ACCEPTED, EventAttendeeResponse.DECLINED);
    EventAttendee seated = rowOf(eventId, testuser2Identity);
    seated.setResponseDate(null);
    attendeeStorage.saveEventAttendee(seated, eventId);
    ZonedDateTime before = ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(1);

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventAttendee row = rowOf(eventId, testuser2Identity);
    assertEquals(EventAttendeeResponse.ACCEPTED, row.getResponse());
    assertNotNull("the answer changed on some dates", row.getResponseDate());
    assertTrue(row.getResponseDate().isAfter(before));
  }

  /**
   * An organiser who removes a seated attendee frees the seat: the first
   * waiting attendee is promoted.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void removingASeatedAttendeePromotesTheFirstWaiting() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    removeAttendee(eventId, id(testuser2Identity), id(testuser1Identity));

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
    assertEquals(id(testuser3Identity),
                 ((EventAttendee) only(Utils.POST_EVENT_ATTENDEE_PROMOTED).data()).getIdentityId());
  }

  /**
   * A save that removes a seated attendee and adds the saver with a Yes gives
   * the seat it frees to the first waiting attendee, who asked before the
   * saver: the saver waits behind them.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeatFreedByASaveGoesToTheWaitingListBeforeTheSaver() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));

    List<EventAttendee> savedList = new ArrayList<>(List.of(rowOf(eventId, testuser3Identity)));
    savedList.add(new EventAttendee(0, eventId, id(testuser1Identity), EventAttendeeResponse.ACCEPTED));
    agendaEventAttendeeService.saveEventAttendees(agendaEventService.getEventById(eventId),
                                                  savedList,
                                                  id(testuser1Identity),
                                                  false,
                                                  false,
                                                  new AgendaEventModification(eventId,
                                                                              event.getCalendarId(),
                                                                              id(testuser1Identity)));

    assertNull(responseOf(eventId, testuser2Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser1Identity));
  }

  /**
   * The answer filling the last seat marks the pool and reports it, once:
   * neither a place on the waiting list nor a seated attendee's Yes given
   * again reports it a second time.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theAnswerFillingTheLastSeatMarksThePoolOnce() throws Exception {
    Event event = limitedEvent(2, testuser2Identity, testuser3Identity, testuser4Identity);
    long eventId = event.getId();

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertNull(agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
    assertEquals(eventId, only(Utils.POST_EVENT_CAPACITY_REACHED).source());

    answer(eventId, testuser4Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
  }

  /**
   * The capacity an answer is decided on is the one of the row it locked,
   * not the one the event cache still serves.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theDecisionReadsTheCapacityOfTheLockedRow() throws Exception {
    Event event = limitedEvent(2, testuser2Identity, testuser3Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals("the cache is warm", 2, (int) agendaEventStorage.getEventById(eventId).getCapacity());
    inOtherTransaction(entityManager -> entityManager.createQuery("UPDATE AgendaEvent ev SET ev.capacity = 1 WHERE ev.id = :id")
                                                     .setParameter("id", eventId)
                                                     .executeUpdate());
    assertEquals("and stale", 2, (int) agendaEventStorage.getEventById(eventId).getCapacity());

    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));
  }

  /**
   * A date of a series with its own row is its own pool: its capacity counts
   * its own rows only, and the answer reports the date it left waiting. The
   * series' answers are left alone.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aDateWithItsOwnRowIsItsOwnPool() throws Exception {
    Event series = futureSeries(testuser2Identity, testuser3Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(), date);
    setCapacity(occurrence.getId(), 1);

    EventResponseOutcome first = answerDate(series.getId(), date, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    EventResponseOutcome second = answerDate(series.getId(), date, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, first.response());
    assertNull(first.firstWaitingOccurrenceId());
    assertEquals(EventAttendeeResponse.WAITING, second.response());
    assertEquals(occurrence.getOccurrence().getId().toInstant(), second.firstWaitingOccurrenceId().toInstant());
    assertEquals(EventAttendeeResponse.WAITING, responseOf(occurrence.getId(), testuser3Identity));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(series.getId(), testuser3Identity));
  }

  /**
   * Answering a date with no row of its own creates the row under the lock of
   * the series, once: a second answer on the date lands on the same row.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void answeringADateCreatesItsRowOnce() throws Exception {
    Event series = futureSeries(testuser2Identity, testuser3Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);

    answerDate(series.getId(), date, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(series.getId(), date, testuser3Identity, EventAttendeeResponse.DECLINED);

    List<Long> rows = agendaEventStorage.getExceptionalOccurenceIds(series.getId());
    assertEquals("the date has one row: " + rows, 1, rows.size());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(rows.get(0), testuser2Identity));
    assertEquals(EventAttendeeResponse.DECLINED, responseOf(rows.get(0), testuser3Identity));
  }

  /**
   * An all-day date is answered on one row, dated on its own day, west of UTC
   * and east of it, with the occurrence ids the occurrence list gives.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anAllDayDateIsAnsweredOnOneRowOfItsDayOnBothSidesOfUtc() throws Exception {
    for (ZoneId zone : List.of(ZoneId.of("America/New_York"), ZoneId.of("Asia/Tokyo"))) {
      Event series = allDaySeries(zone, testuser2Identity, testuser3Identity);
      ZonedDateTime date = occurrenceIds(series).get(1);
      LocalDate expectedDay = series.getStart().withZoneSameLocal(zone).toLocalDate().plusDays(1);

      answerDate(series.getId(), date, testuser2Identity, EventAttendeeResponse.ACCEPTED);
      answerDate(series.getId(), date, testuser3Identity, EventAttendeeResponse.ACCEPTED);

      List<Long> rows = agendaEventStorage.getExceptionalOccurenceIds(series.getId());
      assertEquals(zone + ": the date has one row: " + rows, 1, rows.size());
      ZonedDateTime rowDate = agendaEventStorage.getEventById(rows.get(0)).getOccurrence().getId();
      assertEquals(zone + ": the row is the date answered", expectedDay, rowDate.toLocalDate());
    }
  }

  /**
   * A row stored for an all-day date at the local midnight of a zone east of
   * UTC, a UTC day earlier than the date's id, is still the date's row: it is
   * found less than twelve hours away, and answered instead of doubled.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aDateRowStoredTwelveHoursFromItsUtcDayIsFound() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    Event series = allDaySeries(tokyo, testuser2Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);
    Event legacyRow = rowStoredAt(series, localMidnight(date, tokyo), testuser2Identity);

    answerDate(series.getId(), date, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(List.of(legacyRow.getId()), agendaEventStorage.getExceptionalOccurenceIds(series.getId()));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(legacyRow.getId(), testuser2Identity));
  }

  /**
   * An all-day row stored before TASK-39591, at the local midnight of the
   * series' zone, is the row of the date it was stored for, east of UTC up to
   * fourteen hours, where it sits on the UTC day before and the next date's
   * row on the date's own UTC day, and west of it. The answer and the read of
   * the date both find it.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aLegacyAllDayRowIsTheRowOfItsDateInTheSeriesZone() throws Exception {
    for (ZoneId zone : List.of(ZoneId.of("Asia/Tokyo"),
                               ZoneId.of("Pacific/Tongatapu"),
                               ZoneId.of("Pacific/Kiritimati"),
                               ZoneId.of("America/New_York"))) {
      Event series = allDaySeries(zone, testuser2Identity);
      List<ZonedDateTime> dates = occurrenceIds(series);
      Event nextDateRow = rowStoredAt(series, localMidnight(dates.get(2), zone), testuser2Identity);
      Event dateRow = rowStoredAt(series, localMidnight(dates.get(1), zone), testuser2Identity);

      answerDate(series.getId(), dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);

      assertEquals(zone + ": the date's row is answered",
                   EventAttendeeResponse.ACCEPTED,
                   responseOf(dateRow.getId(), testuser2Identity));
      assertEquals(zone + ": the next date's row is not",
                   EventAttendeeResponse.NEEDS_ACTION,
                   responseOf(nextDateRow.getId(), testuser2Identity));
      assertEquals(zone + ": the read of the date finds its row",
                   dateRow.getId(),
                   agendaEventService.getExceptionalOccurrenceEvent(series.getId(), dates.get(1)).getId());
    }
  }

  /**
   * West of UTC, in a zone whose clocks skip midnight, an all-day date starts
   * at its first local time from that day on, which its identifier carries as
   * UTC, past the UTC midnight naming the date. An answer to the day the clocks
   * skip midnight, or to the day after, answers a row of its own, which the
   * read of the date finds, and leaves the row of the day before as it was.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anAnswerToADateStartingPastMidnightAnswersThatDate() throws Exception {
    for (ZoneId zone : List.of(ZoneId.of("America/Santiago"), ZoneId.of("America/Havana"))) {
      LocalDate dayWithoutMidnight = nextChangeOfOffset(zone, true);
      Event series = allDaySeries(dayWithoutMidnight.minusDays(1), zone, testuser2Identity);
      List<ZonedDateTime> dates = occurrenceIds(series);
      Event eveRow = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));
      for (int i = 1; i < 3; i++) {
        ZonedDateTime date = dates.get(i);
        String label = zone + " " + date;
        assertNotEquals(label + ": the identifier is past a UTC midnight",
                        LocalTime.MIDNIGHT,
                        date.withZoneSameInstant(ZoneOffset.UTC).toLocalTime());

        answerDate(series.getId(), date, testuser2Identity, EventAttendeeResponse.ACCEPTED);

        Event dateRow = agendaEventService.getExceptionalOccurrenceEvent(series.getId(), date);
        assertEquals(label + ": the row is on the date",
                     dayWithoutMidnight.plusDays(i - 1L),
                     dateRow.getOccurrence().getId().withZoneSameInstant(ZoneOffset.UTC).toLocalDate());
        assertEquals(label + ": the date's row is answered",
                     EventAttendeeResponse.ACCEPTED,
                     responseOf(dateRow.getId(), testuser2Identity));
      }
      assertEquals(zone + ": the eve's row is not answered",
                   EventAttendeeResponse.NEEDS_ACTION,
                   responseOf(eveRow.getId(), testuser2Identity));
      assertEquals(zone + ": each date has a row of its own", 3, agendaEventStorage.getExceptionalOccurenceIds(series.getId()).size());
    }
  }

  /**
   * An answer to a date and the upcoming ones, given on the day the clocks
   * skip midnight with the identifier the occurrence list gives it, covers that
   * date, whether its row is created after the answer or existed before, and
   * not the eve.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anUpcomingAnswerFromADateStartingPastMidnightCoversThatDate() throws Exception {
    for (ZoneId zone : List.of(ZoneId.of("America/Santiago"), ZoneId.of("America/Havana"))) {
      LocalDate dayWithoutMidnight = nextChangeOfOffset(zone, true);

      Event rowAfter = allDaySeries(dayWithoutMidnight.minusDays(1), zone, testuser2Identity);
      List<ZonedDateTime> dates = occurrenceIds(rowAfter);
      agendaEventAttendeeService.sendUpcomingEventResponse(rowAfter.getId(), dates.get(1), id(testuser2Identity), EventAttendeeResponse.ACCEPTED);
      Event dateRow = agendaEventService.saveEventExceptionalOccurrence(rowAfter.getId(), dates.get(1));
      Event eveRow = agendaEventService.saveEventExceptionalOccurrence(rowAfter.getId(), dates.get(0));
      assertEquals(zone + ": the date's row created after the answer takes it",
                   EventAttendeeResponse.ACCEPTED,
                   responseOf(dateRow.getId(), testuser2Identity));
      assertEquals(zone + ": the eve's does not",
                   EventAttendeeResponse.NEEDS_ACTION,
                   responseOf(eveRow.getId(), testuser2Identity));

      Event rowBefore = allDaySeries(dayWithoutMidnight.minusDays(1), zone, testuser2Identity);
      dates = occurrenceIds(rowBefore);
      dateRow = agendaEventService.saveEventExceptionalOccurrence(rowBefore.getId(), dates.get(1));
      agendaEventAttendeeService.sendUpcomingEventResponse(rowBefore.getId(), dates.get(1), id(testuser2Identity), EventAttendeeResponse.ACCEPTED);
      assertEquals(zone + ": the date's row created before the answer takes it",
                   EventAttendeeResponse.ACCEPTED,
                   responseOf(dateRow.getId(), testuser2Identity));
    }
  }

  /**
   * A date that got two rows, as two first writes did before they were found
   * under the lock of the series, is answered and read on the first created.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aDateWithTwoRowsIsAnsweredOnTheFirstCreated() throws Exception {
    Event series = futureSeries(testuser2Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);
    Event firstRow = rowStoredAt(series, date, testuser2Identity);
    Event secondRow = rowStoredAt(series, date, testuser2Identity);

    answerDate(series.getId(), date, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(firstRow.getId(), testuser2Identity));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(secondRow.getId(), testuser2Identity));
    assertEquals(firstRow.getId(), agendaEventService.getExceptionalOccurrenceEvent(series.getId(), date).getId());
  }

  /**
   * The rows of a timed series keep the time of day they were created at when
   * the series moves, and the date they stand for is their day in the series'
   * zone: in UTC moved from 01:00 to 23:00, and in Tokyo from 08:30 to 09:30,
   * across midnight UTC, a date's row is not the next date's, nearer in time.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aMovedTimedSeriesAnswersTheRowOfTheDateInItsZone() throws Exception {
    Object[][] cases = { { ZoneOffset.UTC, LocalTime.of(23, 0), LocalTime.of(1, 0) },
        { ZoneId.of("Asia/Tokyo"), LocalTime.of(9, 30), LocalTime.of(8, 30) } };
    for (Object[] movedSeries : cases) {
      ZoneId zone = (ZoneId) movedSeries[0];
      LocalTime rowsTime = (LocalTime) movedSeries[2];
      ZonedDateTime start = LocalDate.now().plusDays(60).atTime((LocalTime) movedSeries[1]).atZone(zone);
      Event event = newEventInstance(start, start.plusMinutes(30), false);
      event.setTimeZoneId(zone);
      Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
      List<ZonedDateTime> dates = occurrenceIds(series);
      Event nextDateRow = rowStoredAt(series, atLocalTime(dates.get(2), zone, rowsTime), testuser2Identity);
      Event dateRow = rowStoredAt(series, atLocalTime(dates.get(1), zone, rowsTime), testuser2Identity);

      answerDate(series.getId(), dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);

      assertEquals(zone + ": the date's row is answered",
                   EventAttendeeResponse.ACCEPTED,
                   responseOf(dateRow.getId(), testuser2Identity));
      assertEquals(zone + ": the next date's row is not",
                   EventAttendeeResponse.NEEDS_ACTION,
                   responseOf(nextDateRow.getId(), testuser2Identity));
    }
  }

  /**
   * A series with several dates a day, an hourly rule or a daily one listing
   * two hours, two minutes or two seconds, gives a date with no row of its own
   * one when it is answered, rather than the row of another date of its day,
   * and finds it again.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesOfSeveralDatesADayGivesEachDateItsOwnRow() throws Exception {
    for (Event series : List.of(hourlySeries(testuser2Identity),
                                dailySeriesAt9(byRule -> byRule.setByHour(List.of("9", "14")), testuser2Identity),
                                dailySeriesAt9(byRule -> byRule.setByMinute(List.of("0", "30")), testuser2Identity),
                                dailySeriesAt9(byRule -> byRule.setBySecond(List.of("0", "30")), testuser2Identity))) {
      EventRecurrence recurrence = series.getRecurrence();
      String rule = recurrence.getFrequency() + " " + recurrence.getByHour() + " " + recurrence.getByMinute() + " " + recurrence.getBySecond();
      List<ZonedDateTime> dates = occurrenceIds(series);
      Event first = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));

      answerDate(series.getId(), dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
      Event second = agendaEventService.createEventExceptionalOccurrence(series.getId(), null, null, null, dates.get(1));

      List<Long> rows = agendaEventStorage.getExceptionalOccurenceIds(series.getId());
      assertEquals(rule + ": each date has its row: " + rows, 2, rows.size());
      assertNotEquals(rule, first.getId(), second.getId());
      assertEquals(rule, dates.get(1).toInstant(), agendaEventStorage.getEventById(second.getId()).getOccurrence().getId().toInstant());
      assertEquals(rule, EventAttendeeResponse.ACCEPTED, responseOf(second.getId(), testuser2Identity));
      assertEquals(rule, EventAttendeeResponse.NEEDS_ACTION, responseOf(first.getId(), testuser2Identity));
    }
  }

  /**
   * After an answer to one date of a day holding another date's row, the read
   * of that date, which the event dialog reloads, serves the row answered.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void theReadOfADateAfterItsAnswerServesTheRowAnswered() throws Exception {
    Event series = hourlySeries(testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));
    answerDate(series.getId(), dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    Event read = agendaEventService.getEventOccurrence(series.getId(), dates.get(1), ZoneOffset.UTC, id(testuser2Identity));

    assertEquals(dates.get(1).toInstant(), read.getOccurrence().getId().toInstant());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(read.getId(), testuser2Identity));
  }

  /**
   * An attendee who may modify a series amends a date with no row of its own
   * without the right to create events in its calendar, and not a date that
   * has one, also when that row was stored on the UTC day before.
   *
   * @throws Exception when a save fails unexpectedly
   */
  @Test
  public void aDateAlreadyAmendedAsksTheCreationRightEvenStoredOnTheUtcDayBefore() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZonedDateTime start = LocalDate.now().plusDays(60).atStartOfDay(tokyo);
    Event event = newEventInstance(start, start, true);
    event.setTimeZoneId(tokyo);
    event.setAllowAttendeeToUpdate(true);
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    rowStoredAt(series, localMidnight(dates.get(1), tokyo), testuser2Identity);

    assertThrows(IllegalAccessException.class, () -> amendAs(testuser2Identity, series, dates.get(1), "Amended again"));
    Event amended = amendAs(testuser2Identity, series, dates.get(2), "Amended once");

    assertEquals("Amended once", agendaEventStorage.getEventById(amended.getId()).getSummary());
  }

  /**
   * An identifier on the UTC day of a series' last date, but on the next day
   * in the series' zone, names a date the series does not have: an attendee
   * without the creation right may not amend it, and the last date amended
   * gets one row. At 10:00 in Paris, 23:30 UTC is already the next day.
   *
   * @throws Exception when a save fails unexpectedly
   */
  @Test
  public void anIdentifierOnTheNextDayOfTheSeriesZoneIsNotOneOfItsDates() throws Exception {
    ZoneId paris = ZoneId.of("Europe/Paris");
    Event series = seriesAllowingAttendeesToUpdate(paris, LocalTime.of(10, 0), testuser2Identity);
    ZonedDateTime date = occurrenceIds(series).get(2);
    ZonedDateTime nextDayInParis = date.withZoneSameInstant(ZoneOffset.UTC).withHour(23).withMinute(30);

    assertThrows(IllegalAccessException.class, () -> amendAs(testuser2Identity, series, nextDayInParis, "Next day"));
    amendAs(testuser2Identity, series, date, "The date");

    assertEquals(1, agendaEventStorage.getExceptionalOccurenceIds(series.getId()).size());
  }

  /**
   * An identifier on a date the series has in its zone, but which the
   * occurrence list does not match to that date, would add a row beside the
   * computed occurrence rather than take its place: an attendee without the
   * creation right may not amend it. At 10:00 in Kiritimati, 23:59 the same
   * day is fourteen hours later, on the next UTC day.
   *
   * @throws Exception when a save fails unexpectedly
   */
  @Test
  public void anIdentifierTheListDoesNotMatchToItsDateIsNotAnAmendment() throws Exception {
    ZoneId kiritimati = ZoneId.of("Pacific/Kiritimati");
    Event series = seriesAllowingAttendeesToUpdate(kiritimati, LocalTime.of(10, 0), testuser2Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);
    ZonedDateTime lateTheSameDay = atLocalTime(date, kiritimati, LocalTime.of(23, 59));

    assertThrows(IllegalAccessException.class, () -> amendAs(testuser2Identity, series, lateTheSameDay, "Late"));

    assertTrue(agendaEventStorage.getExceptionalOccurenceIds(series.getId()).isEmpty());
  }

  /**
   * An identifier the occurrence list matches to two dates, one by its UTC day
   * and the next by less than twelve hours, would take both out of the list as
   * the row of one: an attendee without the creation right may not amend it.
   * At 10:00 in Paris, a date at 23:30 UTC is the next date in Paris.
   *
   * @throws Exception when a save fails unexpectedly
   */
  @Test
  public void anIdentifierTheListMatchesToTwoDatesIsNotAnAmendment() throws Exception {
    Event series = seriesAllowingAttendeesToUpdate(ZoneId.of("Europe/Paris"), LocalTime.of(10, 0), testuser2Identity);
    ZonedDateTime firstDateAtNight = occurrenceIds(series).get(0).withZoneSameInstant(ZoneOffset.UTC).withHour(23).withMinute(30);

    assertThrows(IllegalAccessException.class, () -> amendAs(testuser2Identity, series, firstDateAtNight, "Two dates"));

    assertTrue(agendaEventStorage.getExceptionalOccurenceIds(series.getId()).isEmpty());
  }

  /**
   * The dates of a series with several dates a day are beyond the window the
   * date check expands: an attendee without the creation right may not amend
   * one, even one the window shows, whose day holds another date the window
   * leaves out.
   *
   * @throws Exception when a save fails unexpectedly
   */
  @Test
  public void aDateOfASeriesOfSeveralDatesADayAsksTheCreationRight() throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event event = newEventInstance(start, start.plusMinutes(30), false);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.setAllowAttendeeToUpdate(true);
    event.getRecurrence().setByHour(List.of("9", "14"));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    ZonedDateTime secondDayAtNine = occurrenceIds(series).get(2);

    assertThrows(IllegalAccessException.class, () -> amendAs(testuser2Identity, series, secondDayAtNine, "Nine"));

    assertTrue(agendaEventStorage.getExceptionalOccurenceIds(series.getId()).isEmpty());
  }

  /**
   * Across a spring-forward, two dates of a daily series share a UTC day, which
   * the occurrence list takes as one: in New York at 19:30, the eve of the
   * change is at 00:30 UTC and the day of the change at 23:30 UTC the same
   * day. Each is still one of its dates, and an attendee allowed to modify the
   * series amends each with the identifier the series computes for it.
   *
   * @throws Exception when a save fails
   */
  @Test
  public void theDatesSharingAUtcDayAcrossASpringForwardAreEachAmended() throws Exception {
    ZoneId newYork = ZoneId.of("America/New_York");
    LocalDate springForward = nextChangeOfOffset(newYork, true);
    Event series = amendableSeries(springForward.minusDays(1).atTime(19, 30).atZone(newYork), false);
    List<ZonedDateTime> dates = occurrenceIds(series);
    assertEquals("both dates are on one UTC day",
                 dates.get(0).withZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
                 dates.get(1).withZoneSameInstant(ZoneOffset.UTC).toLocalDate());

    amendAs(testuser2Identity, series, dates.get(0), "The eve");
    amendAs(testuser2Identity, series, dates.get(1), "The day");

    assertEquals(2, agendaEventStorage.getExceptionalOccurenceIds(series.getId()).size());
  }

  /**
   * Every date a daily series computes around a change of offset, forward and
   * back, west and east of UTC and south of the equator, at times of day that
   * put two dates on one UTC day or fall in the gap, and all day long, also in
   * zones whose clocks skip midnight, is one of its dates: an attendee allowed
   * to modify the series amends each with the identifier the series computes
   * for it, and each gets its own row.
   *
   * @throws Exception when a save fails
   */
  @Test
  public void everyDateComputedAroundAChangeOfOffsetIsAmended() throws Exception {
    List<LocalTime> times = List.of(LocalTime.of(0, 30), LocalTime.of(2, 30), LocalTime.of(10, 30), LocalTime.of(19, 30), LocalTime.of(23, 30));
    for (ZoneId zone : List.of(ZoneId.of("America/New_York"),
                               ZoneId.of("Europe/Paris"),
                               ZoneId.of("Australia/Sydney"),
                               ZoneId.of("America/Santiago"),
                               ZoneId.of("America/Havana"))) {
      for (boolean forward : List.of(true, false)) {
        LocalDate eve = nextChangeOfOffset(zone, forward).minusDays(1);
        for (LocalTime time : times) {
          amendEveryDate(amendableSeries(eve.atTime(time).atZone(zone), false), zone + " " + (forward ? "forward " : "back ") + time);
        }
        amendEveryDate(amendableSeries(eve.atStartOfDay(zone), true), zone + " " + (forward ? "forward" : "back") + " all day");
      }
    }
  }

  /**
   * "This occurrence only" saved on one hour of an hourly series creates the
   * row of that hour and leaves the row of another hour of the day as it was.
   *
   * @throws Exception when the save fails
   */
  @Test
  public void aSaveOfOneHourOfAnHourlySeriesLeavesTheRowOfAnother() throws Exception {
    Event series = hourlySeries(testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    Event first = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));

    Event saved = agendaEventService.createEvent(dateForm(series, dates.get(1), "Only this hour"),
                                                 List.of(new EventAttendee(0, id(testuser2Identity), null)),
                                                 null,
                                                 null,
                                                 null,
                                                 null,
                                                 false,
                                                 id(testuser1Identity));

    assertNotEquals(first.getId(), saved.getId());
    assertEquals(2, agendaEventStorage.getExceptionalOccurenceIds(series.getId()).size());
    assertEquals(series.getSummary(), agendaEventStorage.getEventById(first.getId()).getSummary());
    assertEquals("Only this hour", agendaEventStorage.getEventById(saved.getId()).getSummary());
  }

  /**
   * A row stored at the local midnight of an all-day date east of UTC sits on
   * the UTC day before: an attendee the date's row invites, and the series
   * does not, may answer it, the check made before the lock finding the row
   * the step answers.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aDateRowOnTheUtcDayBeforeAdmitsWhoItAloneInvites() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    Event series = allDaySeries(tokyo, testuser2Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);
    Event dateRow = rowStoredAt(series, localMidnight(date, tokyo), testuser3Identity);

    answerDate(series.getId(), date, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateRow.getId(), testuser3Identity));
  }

  /**
   * The row of the day before, less than a day earlier and so inside the
   * lookup's window, is not taken for the date answered: a series at 23:30
   * UTC puts its previous date an hour before the date's UTC day.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theRowOfTheDayBeforeIsNotTakenForTheDate() throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(23, 30).atZone(ZoneOffset.UTC);
    Event event = newEventInstance(start, start.plusMinutes(20), false);
    event.setTimeZoneId(ZoneOffset.UTC);
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    Event dayBefore = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));

    answerDate(series.getId(), dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    List<Long> rows = agendaEventStorage.getExceptionalOccurenceIds(series.getId());
    assertEquals("each date has its row: " + rows, 2, rows.size());
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(dayBefore.getId(), testuser2Identity));
    long dateRow = rows.stream().filter(rowId -> rowId != dayBefore.getId()).findFirst().orElseThrow();
    assertEquals(dates.get(1).toInstant(), agendaEventStorage.getEventById(dateRow).getOccurrence().getId().toInstant());
  }

  /**
   * An attendee removed from one date of a series may not answer that date,
   * and a user who may answer neither the series nor the date leaves no row
   * behind when refused.
   *
   * @throws Exception when a write fails unexpectedly
   */
  @Test
  public void anAnswerToADateIsRefusedToWhoMayNotAnswerIt() throws Exception {
    Event series = futureSeries(testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(1));
    removeAttendee(occurrence.getId(), id(testuser3Identity), id(testuser1Identity));

    assertThrows(IllegalAccessException.class,
                 () -> answerDate(series.getId(), dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED));
    assertThrows(IllegalAccessException.class,
                 () -> answerDate(series.getId(), dates.get(2), testuser4Identity, EventAttendeeResponse.ACCEPTED));

    assertEquals(List.of(occurrence.getId()), agendaEventStorage.getExceptionalOccurenceIds(series.getId()));
    assertNull(responseOf(occurrence.getId(), testuser3Identity));
  }

  /**
   * "This occurrence only" saved from a form opened before the date got its
   * row updates that row instead of creating a second one.
   *
   * @throws Exception when the save fails
   */
  @Test
  public void aSaveOfOneDateThatGotItsRowMeanwhileUpdatesIt() throws Exception {
    Event series = futureSeries(testuser2Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);
    Event materialised = agendaEventService.saveEventExceptionalOccurrence(series.getId(), date);

    Event saved = agendaEventService.createEvent(dateForm(series, date, "Only this date"),
                                                 List.of(new EventAttendee(0, id(testuser2Identity), null)),
                                                 null,
                                                 null,
                                                 null,
                                                 null,
                                                 false,
                                                 id(testuser1Identity));

    assertEquals(materialised.getId(), saved.getId());
    assertEquals(List.of(materialised.getId()), agendaEventStorage.getExceptionalOccurenceIds(series.getId()));
    assertEquals("Only this date", agendaEventStorage.getEventById(materialised.getId()).getSummary());
  }

  /**
   * Asked to create the row of a date that already has one, the creation
   * returns that row.
   *
   * @throws Exception when the creation fails
   */
  @Test
  public void aSecondCreationOfADateReturnsItsRow() throws Exception {
    Event series = futureSeries(testuser2Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);
    Event materialised = agendaEventService.saveEventExceptionalOccurrence(series.getId(), date);

    Event created = agendaEventService.createEventExceptionalOccurrence(series.getId(), null, null, null, date);

    assertEquals(materialised.getId(), created.getId());
    assertEquals(List.of(materialised.getId()), agendaEventStorage.getExceptionalOccurenceIds(series.getId()));
  }

  /**
   * A date whose copied answers fill its capacity starts as having reached
   * it, without reporting it: no answer filled it. A date with a seat left
   * starts as not having reached it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateCopiedFullStartsAsHavingReachedItsCapacitySilently() throws Exception {
    Event series = futureSeries(testuser2Identity, testuser3Identity);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = occurrenceIds(series);
    setCapacity(series.getId(), 2);
    BROADCASTS.clear();

    Event full = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(1));

    assertNotNull(agendaEventStorage.getEventById(full.getId()).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    setCapacity(series.getId(), 3);
    Event notFull = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(2));

    assertNull(agendaEventStorage.getEventById(notFull.getId()).getCapacityReachedDate());
  }

  /**
   * A date copies the series' rows valid at it only: the lone row of an
   * attendee who answered from a later date is not copied, which the attendee
   * list's shortcut for a list of one row would do.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateCopiesOnlyTheRowsValidAtIt() throws Exception {
    Event series = futureSeries(testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    leaveOneRowFrom(series, dates.get(2), testuser2Identity);

    Event first = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));

    assertTrue(agendaEventAttendeeService.getEventAttendees(first.getId()).isEmpty());
  }

  /**
   * "This occurrence only" saved with no attendee gives the date the series'
   * rows valid at it, as a materialised date does: the lone row of an
   * attendee who answered from a later date is copied onto that date, not
   * onto an earlier one.
   *
   * @throws Exception when a save fails
   */
  @Test
  public void aDateSavedWithNoAttendeeTakesOnlyTheRowsValidAtIt() throws Exception {
    Event series = futureSeries(testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    leaveOneRowFrom(series, dates.get(2), testuser2Identity);

    Event first = agendaEventService.createEvent(dateForm(series, dates.get(0), "Only the first date"),
                                                 Collections.emptyList(),
                                                 null,
                                                 null,
                                                 null,
                                                 null,
                                                 false,
                                                 id(testuser1Identity));
    Event third = agendaEventService.createEvent(dateForm(series, dates.get(2), "Only the third date"),
                                                 Collections.emptyList(),
                                                 null,
                                                 null,
                                                 null,
                                                 null,
                                                 false,
                                                 id(testuser1Identity));

    assertTrue(agendaEventAttendeeService.getEventAttendees(first.getId()).isEmpty());
    assertNotNull(rowOf(third.getId(), testuser2Identity));
  }

  /**
   * A date's row materialised from a series whose only attendee row starts on
   * a later date carries nothing of its own, no attendee being the series'
   * list at that date: a change of the series drops it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateRowBuiltWithoutTheRowOfALaterDateIsDroppedOnASeriesChange() throws Exception {
    Event series = futureSeries(testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    leaveOneRowFrom(series, dates.get(2), testuser2Identity);
    Event first = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));
    assertTrue(agendaEventAttendeeService.getEventAttendees(first.getId()).isEmpty());

    Event toSave = agendaEventService.getEventById(series.getId(), null, id(testuser1Identity));
    toSave.setSummary("Renamed series");
    agendaEventService.updateEvent(toSave,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   agendaEventConferenceService.getEventConferences(series.getId()),
                                   agendaEventReminderService.getEventReminders(series.getId()),
                                   null,
                                   null,
                                   false,
                                   id(testuser1Identity));

    assertNull(agendaEventStorage.getEventById(first.getId()));
  }

  /**
   * A reset starts every answer over, the waiting list included, except the
   * identities kept; it forgets the capacity reached, promotes nobody, and
   * reports no answer as sent.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aResetStartsEveryAnswerOverAndPromotesNobody() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity, testuser4Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser4Identity, EventAttendeeResponse.DECLINED);
    assertNotNull(agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
    BROADCASTS.clear();

    agendaEventAttendeeService.resetEventResponses(eventId, Set.of(id(testuser4Identity)));

    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(eventId, testuser2Identity));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(eventId, testuser3Identity));
    assertEquals(EventAttendeeResponse.DECLINED, responseOf(eventId, testuser4Identity));
    assertNull(agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED).isEmpty());
    assertTrue(broadcasts(Utils.POST_EVENT_RESPONSE_SENT).isEmpty());
    assertEquals(Set.of(id(testuser2Identity), id(testuser3Identity)),
                 Set.copyOf(identitiesOf(Utils.POST_EVENT_RESPONSE_SAVED)));
  }

  /**
   * A save of the whole event, in a request that read the event before the
   * answers marked its pool as having reached its capacity, leaves the mark
   * as they set it.
   *
   * @throws Exception when a save fails
   */
  @Test
  public void aSaveOfTheWholeEventKeepsTheCapacityReachedMarkSetSinceItsRead() throws Exception {
    Event event = limitedEvent(1, testuser2Identity);
    long eventId = event.getId();
    // Read from the database by this request: its row is in the persistence
    // context the save goes through
    agendaEventStorage.evictEvents(List.of(eventId));
    Event toSave = agendaEventService.getEventById(eventId, null, id(testuser1Identity));
    assertNull(toSave.getCapacityReachedDate());
    markInOtherTransaction(eventId);

    toSave.setSummary("Renamed event");
    Event returned = saveWholeEvent(toSave);
    assertNotNull("the event the save returns carries the stored mark", returned.getCapacityReachedDate());

    agendaEventStorage.evictEvents(List.of(eventId));
    Event saved = agendaEventStorage.getEventById(eventId);
    assertEquals("Renamed event", saved.getSummary());
    assertNotNull(saved.getCapacityReachedDate());
  }

  /**
   * The mark is written under the event lock a decide step holds: a write
   * outside a transaction is refused, rather than written on its own.
   *
   * @throws Exception when the event cannot be created
   */
  @Test
  public void aMarkWrittenOutsideATransactionIsRefused() throws Exception {
    long eventId = limitedEvent(1, testuser2Identity).getId();
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);

    assertThrows(TransactionRequiredException.class, () -> agendaEventStorage.saveCapacityReachedDate(eventId, now));

    agendaEventStorage.evictEvents(List.of(eventId));
    assertNull(agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
  }

  /**
   * A change of one field of the event, which saves a copy of the stored row,
   * in a request that read the event while its pool was marked as having
   * reached its capacity, leaves the mark as a reset cleared it since, and
   * the event read again in that request does not carry it.
   *
   * @throws Exception when the change fails
   */
  @Test
  public void aChangeOfOneFieldKeepsTheCapacityReachedMarkClearedSinceItsRead() throws Exception {
    Event event = limitedEvent(5, testuser2Identity);
    long eventId = event.getId();
    saveCapacityReachedDate(eventId, ZonedDateTime.now(ZoneOffset.UTC));
    // Read from the database by this request: its marked row is in the
    // persistence context the change goes through
    agendaEventStorage.evictEvents(List.of(eventId));
    assertNotNull(agendaEventService.getEventById(eventId, null, id(testuser1Identity)).getCapacityReachedDate());
    writeMarkInOtherTransaction(eventId, null);

    agendaEventService.updateEventFields(eventId, getFields("summary", "Renamed event"), false, false, id(testuser1Identity));

    Event read = agendaEventStorage.getEventById(eventId);
    assertEquals("Renamed event", read.getSummary());
    assertNull("the event read again in the request carries the stored mark", read.getCapacityReachedDate());
    agendaEventStorage.evictEvents(List.of(eventId));
    assertNull(agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
  }

  /**
   * A change of one field of the event, in a request that read the event
   * before the answers marked its pool as having reached its capacity, leaves
   * the mark, and the event read again in that request, as the listeners of
   * the change read it, carries it: no decide step follows to read the row
   * afresh.
   *
   * @throws Exception when the change fails
   */
  @Test
  public void aChangeOfOneFieldKeepsTheCapacityReachedMarkSetSinceItsRead() throws Exception {
    Event event = limitedEvent(1, testuser2Identity);
    long eventId = event.getId();
    // Read from the database by this request: its row is in the persistence
    // context the change goes through
    agendaEventStorage.evictEvents(List.of(eventId));
    assertNull(agendaEventService.getEventById(eventId, null, id(testuser1Identity)).getCapacityReachedDate());
    markInOtherTransaction(eventId);

    agendaEventService.updateEventFields(eventId, getFields("summary", "Renamed event"), false, false, id(testuser1Identity));

    Event read = agendaEventStorage.getEventById(eventId);
    assertEquals("Renamed event", read.getSummary());
    assertNotNull("the event read again in the request carries the stored mark", read.getCapacityReachedDate());
  }

  /**
   * A save of the whole event from the copy the event cache still serves,
   * read before the answers marked its pool as having reached its capacity,
   * keeps the mark and returns the event with it.
   *
   * @throws Exception when a save fails
   */
  @Test
  public void aSaveOfTheWholeEventFromItsCachedCopyReturnsTheStoredMark() throws Exception {
    Event event = limitedEvent(1, testuser2Identity);
    long eventId = event.getId();
    assertNull("the cache is warm", agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
    markInOtherTransaction(eventId);
    assertNull("and stale", agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
    // The save comes in another request: the cache still serves the copy read
    // before the mark, and the persistence context holds no row yet
    end();
    begin();

    Event toSave = agendaEventService.getEventById(eventId, null, id(testuser1Identity));
    toSave.setSummary("Renamed event");
    Event returned = saveWholeEvent(toSave);
    assertNotNull("the event the save returns carries the stored mark", returned.getCapacityReachedDate());

    agendaEventStorage.evictEvents(List.of(eventId));
    Event saved = agendaEventStorage.getEventById(eventId);
    assertEquals("Renamed event", saved.getSummary());
    assertNotNull(saved.getCapacityReachedDate());
  }

  /**
   * A reset that keeps the answer of the only attendee holding a seat still
   * makes the pool forget that it reached its capacity.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aResetKeepingTheSeatedAnswerForgetsTheCapacityReached() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventStorage.getEventById(eventId).getCapacityReachedDate());

    agendaEventAttendeeService.resetEventResponses(eventId, Set.of(id(testuser2Identity)));

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser2Identity));
    assertNull(agendaEventStorage.getEventById(eventId).getCapacityReachedDate());
  }

  /**
   * Moving the date of a confirmed event starts the answers over, the
   * waiting list included, and promotes nobody.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateChangeStartsTheWaitingListOver() throws Exception {
    Event event = limitedEvent(1, testuser2Identity, testuser3Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    Event toSave = agendaEventService.getEventById(eventId, null, id(testuser1Identity));
    toSave.setStart(toSave.getStart().plusHours(1));
    toSave.setEnd(toSave.getEnd().plusHours(1));
    agendaEventService.updateEvent(toSave,
                                   agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(),
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   id(testuser1Identity));

    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(eventId, testuser2Identity));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(eventId, testuser3Identity));
    assertTrue(broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED).isEmpty());
  }

  /**
   * Moving the dates of a series starts over the answers its dates' rows
   * hold, the waiting list included, also when nobody answered the series
   * itself, and promotes nobody: on a series with no limit, and on one whose
   * date is full.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateChangeOfASeriesStartsTheAnswersOfItsDatesOver() throws Exception {
    for (int capacity : List.of(0, 1)) {
      String label = "capacity " + capacity;
      Event series = futureSeries(testuser2Identity, testuser3Identity);
      setCapacity(series.getId(), capacity);
      ZonedDateTime date = occurrenceIds(series).get(1);
      answerDate(series.getId(), date, testuser2Identity, EventAttendeeResponse.ACCEPTED);
      answerDate(series.getId(), date, testuser3Identity, EventAttendeeResponse.ACCEPTED);
      long dateRowId = agendaEventService.getExceptionalOccurrenceEvent(series.getId(), date).getId();
      assertEquals(label,
                   capacity == 0 ? EventAttendeeResponse.ACCEPTED : EventAttendeeResponse.WAITING,
                   responseOf(dateRowId, testuser3Identity));
      assertEquals(label, EventAttendeeResponse.NEEDS_ACTION, responseOf(series.getId(), testuser2Identity));
      assertEquals(label, EventAttendeeResponse.NEEDS_ACTION, responseOf(series.getId(), testuser3Identity));
      BROADCASTS.clear();

      Event toSave = agendaEventService.getEventById(series.getId(), null, id(testuser1Identity));
      toSave.setStart(toSave.getStart().plusHours(1));
      toSave.setEnd(toSave.getEnd().plusHours(1));
      agendaEventService.updateEvent(toSave,
                                     agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                     agendaEventConferenceService.getEventConferences(series.getId()),
                                     agendaEventReminderService.getEventReminders(series.getId()),
                                     null,
                                     null,
                                     false,
                                     id(testuser1Identity));

      assertNotNull(label + ": the date's row is kept", agendaEventStorage.getEventById(dateRowId));
      assertEquals(label, EventAttendeeResponse.NEEDS_ACTION, responseOf(dateRowId, testuser2Identity));
      assertEquals(label, EventAttendeeResponse.NEEDS_ACTION, responseOf(dateRowId, testuser3Identity));
      assertTrue(label, broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED).isEmpty());
    }
  }

  /**
   * "This occurrence only" matches the date with the series as locked, not as
   * the event cache still serves it: a series moved to another zone meanwhile
   * names the date by its day in that zone, and the save updates the row it
   * holds for it.
   *
   * @throws Exception when a save fails
   */
  @Test
  public void aSaveOfOneDateMatchesItWithTheSeriesAsLocked() throws Exception {
    Event series = seriesAllowingAttendeesToUpdate(ZoneOffset.UTC, LocalTime.of(23, 30), testuser2Identity);
    ZonedDateTime date = occurrenceIds(series).get(1);
    Event dateRow = rowStoredAt(series, date, testuser2Identity);
    assertEquals("the cache is warm", ZoneOffset.UTC, agendaEventStorage.getEventById(series.getId()).getTimeZoneId());
    inOtherTransaction(entityManager -> entityManager.createQuery("UPDATE AgendaEvent ev SET ev.timeZoneId = :zone WHERE ev.id = :id")
                                                     .setParameter("zone", "Europe/Paris")
                                                     .setParameter("id", series.getId())
                                                     .executeUpdate());
    assertEquals("and stale", ZoneOffset.UTC, agendaEventStorage.getEventById(series.getId()).getTimeZoneId());

    Event saved = amendAs(testuser1Identity, series, date.plusHours(1), "Saved on the date");

    assertEquals(dateRow.getId(), saved.getId());
    assertEquals(1, agendaEventStorage.getExceptionalOccurenceIds(series.getId()).size());
  }

  /**
   * The saver who adds themself with a Yes to a full event waits like anyone
   * answering it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void theSaversOwnYesWaitsOnAFullEvent() throws Exception {
    Event event = limitedEvent(1, testuser2Identity);
    long eventId = event.getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    List<EventAttendee> attendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees());
    attendees.add(new EventAttendee(0, eventId, id(testuser1Identity), EventAttendeeResponse.ACCEPTED));
    Event toSave = agendaEventService.getEventById(eventId, null, id(testuser1Identity));
    toSave.setSummary("Renamed");
    agendaEventService.updateEvent(toSave, attendees, null, null, null, null, false, id(testuser1Identity));

    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser1Identity));
  }

  /**
   * Choosing the date of a poll starts the other answers over without
   * reporting them as sent: a reset is not an answer. The chooser's Yes is
   * one.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aPollDateChoiceResetsWithoutReportingAnswersAsSent() throws Exception {
    Event poll = datePoll();
    long pollId = poll.getId();
    answer(pollId, testuser2Identity, EventAttendeeResponse.TENTATIVE);
    EventDateOption option = agendaEventDatePollService.getEventDateOptions(pollId, ZoneOffset.UTC).get(0);
    BROADCASTS.clear();

    agendaEventService.selectEventDateOption(pollId, option.getId(), id(testuser1Identity));

    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(pollId, testuser2Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(pollId, testuser1Identity));
    assertEquals(List.of(id(testuser1Identity)), identitiesOf(Utils.POST_EVENT_RESPONSE_SENT));
  }

  private static boolean isAttendeeWrite(RecordedStatement statement) {
    String sql = statement.sql().toLowerCase();
    return sql.contains("exo_agenda_attendee") && (sql.startsWith("update") || sql.startsWith("insert") || sql.startsWith("delete"));
  }

  /**
   * An event of no series, starting in sixty days in the first test user's
   * calendar, who is not one of its attendees, with the capacity given.
   */
  private Event limitedEvent(int capacity, Identity... attendees) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    Event created = createEvent(event, id(testuser1Identity), attendees);
    setCapacity(created.getId(), capacity);
    return created;
  }

  /**
   * A daily series of three dates starting in sixty days, in the first test
   * user's calendar, who is not one of its attendees.
   */
  private Event futureSeries(Identity... attendees) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    return createEvent(newEventInstance(start, start.plusHours(1), false), id(testuser1Identity), attendees);
  }

  /**
   * A daily all-day series of three dates starting in sixty days, in a given
   * zone.
   */
  private Event allDaySeries(ZoneId zone, Identity... attendees) throws Exception {
    return allDaySeries(LocalDate.now().plusDays(60), zone, attendees);
  }

  /**
   * A daily all-day series of three dates starting on a date, in a given zone.
   */
  private Event allDaySeries(LocalDate firstDate, ZoneId zone, Identity... attendees) throws Exception {
    ZonedDateTime start = firstDate.atStartOfDay(zone);
    Event event = newEventInstance(start, start, true);
    event.setTimeZoneId(zone);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * An hourly series starting at 09:00 UTC in sixty days, in the first test
   * user's calendar: the recurrence editor does not offer it, a REST payload
   * creates it.
   */
  private Event hourlySeries(Identity... attendees) throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event event = newEventInstance(start, start.plusMinutes(30), false);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.HOURLY);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * A daily series at 09:00 UTC starting in sixty days, in the first test
   * user's calendar, with the BY rule given: two hours, minutes or seconds give
   * it two dates a day without an hourly frequency.
   */
  private Event dailySeriesAt9(Consumer<EventRecurrence> byRule, Identity... attendees) throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event event = newEventInstance(start, start.plusMinutes(30), false);
    event.setTimeZoneId(ZoneOffset.UTC);
    byRule.accept(event.getRecurrence());
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * A date's occurrence id moved to another time of the same day in a zone.
   */
  private static ZonedDateTime atLocalTime(ZonedDateTime occurrenceId, ZoneId zone, LocalTime time) {
    return occurrenceId.withZoneSameInstant(zone).with(time).withZoneSameInstant(ZoneOffset.UTC);
  }

  /**
   * A row of a series stored with the occurrence id given, as a row created
   * before an identifier's formula changed keeps its own, inviting the
   * attendees given.
   */
  private Event rowStoredAt(Event series, ZonedDateTime storedOccurrenceId, Identity... attendees) throws Exception {
    Event row = agendaEventStorage.getEventById(series.getId());
    row.setId(0);
    row.setParentId(series.getId());
    row.setRecurrence(null);
    row.setOccurrence(new EventOccurrence(storedOccurrenceId, true, false));
    Event storedRow = agendaEventStorage.createEvent(row);
    agendaEventAttendeeService.saveEventAttendees(storedRow,
                                                  Arrays.stream(attendees)
                                                        .map(attendee -> new EventAttendee(0,
                                                                                           storedRow.getId(),
                                                                                           id(attendee),
                                                                                           EventAttendeeResponse.NEEDS_ACTION))
                                                        .toList(),
                                                  0,
                                                  false,
                                                  false,
                                                  null);
    return storedRow;
  }

  /**
   * The local midnight, in a zone, of an all-day date's occurrence id, which
   * is the date written as a UTC midnight: the id a row of that date stored
   * before TASK-39591 carries.
   */
  private static ZonedDateTime localMidnight(ZonedDateTime allDayOccurrenceId, ZoneId zone) {
    return allDayOccurrenceId.toLocalDate().atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC);
  }

  /**
   * The form of "this occurrence only" on one date of a series, as the client
   * sends it: a copy of the series on that date, with its own summary.
   */
  private Event dateForm(Event series, ZonedDateTime date, String summary) {
    Event form = agendaEventService.getEventById(series.getId());
    ZonedDateTime start = date.withZoneSameInstant(form.getStart().getZone());
    form.setId(0);
    form.setParentId(series.getId());
    form.setRecurrence(null);
    form.setOccurrence(new EventOccurrence(date));
    form.setStart(start);
    form.setEnd(start.plusHours(1));
    form.setSummary(summary);
    return form;
  }

  /**
   * The local date of the next change of offset of a zone sixty days from now
   * at least, forward (a gap) or back (an overlap).
   */
  private static LocalDate nextChangeOfOffset(ZoneId zone, boolean forward) {
    ZoneOffsetTransition transition = zone.getRules().nextTransition(Instant.now().plus(Duration.ofDays(60)));
    while (transition.isGap() != forward) {
      transition = zone.getRules().nextTransition(transition.getInstant());
    }
    return transition.getDateTimeBefore().toLocalDate();
  }

  /**
   * A daily series of three dates from a start, in the first test user's
   * calendar, whose attendee, the second test user, may modify it.
   */
  private Event amendableSeries(ZonedDateTime start, boolean allDay) throws Exception {
    Event event = newEventInstance(start, allDay ? start : start.plusHours(1), allDay);
    event.setTimeZoneId(start.getZone());
    event.setAllowAttendeeToUpdate(true);
    return createEvent(event, id(testuser1Identity), testuser2Identity);
  }

  /**
   * A daily series of three dates starting in sixty days at a time of a zone,
   * in the first test user's calendar, whose attendees may modify it.
   */
  private Event seriesAllowingAttendeesToUpdate(ZoneId zone, LocalTime time, Identity... attendees) throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(time).atZone(zone);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(zone);
    event.setAllowAttendeeToUpdate(true);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * "This occurrence only" saved by the second test user on every date of a
   * series, with the identifier the occurrence list gives it: no date is
   * refused, and the rows stored are one per date, each on its date.
   */
  private void amendEveryDate(Event series, String label) throws Exception {
    List<ZonedDateTime> dates = occurrenceIds(series);
    for (ZonedDateTime date : dates) {
      try {
        amendAs(testuser2Identity, series, date, label);
      } catch (IllegalAccessException e) {
        throw new AssertionError(label + ": the date " + date + " is refused", e);
      }
    }
    List<LocalDate> rowDates = agendaEventStorage.getExceptionalOccurenceIds(series.getId())
                                                 .stream()
                                                 .map(rowId -> dateNamed(series,
                                                                         agendaEventStorage.getEventById(rowId)
                                                                                           .getOccurrence()
                                                                                           .getId()))
                                                 .sorted()
                                                 .toList();
    assertEquals(label, dates.stream().map(date -> dateNamed(series, date)).toList(), rowDates);
  }

  /**
   * The date an identifier the occurrence list gives, or a row stored from it,
   * names: on an all-day series its UTC date, the date relabelled as UTC; on a
   * timed one its date in the series' zone.
   */
  private static LocalDate dateNamed(Event series, ZonedDateTime occurrenceId) {
    ZoneId zone = series.isAllDay() ? ZoneOffset.UTC : series.getTimeZoneId();
    return occurrenceId.withZoneSameInstant(zone).toLocalDate();
  }

  /**
   * "This occurrence only" saved by a user on one date of a series, inviting
   * that user.
   */
  private Event amendAs(Identity identity, Event series, ZonedDateTime date, String summary) throws Exception {
    return agendaEventService.createEvent(dateForm(series, date, summary),
                                          List.of(new EventAttendee(0, id(identity), null)),
                                          null,
                                          null,
                                          null,
                                          null,
                                          false,
                                          id(identity));
  }

  /**
   * Leaves an attendee of a series one row, from a later date: the answer from
   * that date splits the attendee's row, and the row before it is removed.
   */
  private void leaveOneRowFrom(Event series, ZonedDateTime date, Identity identity) throws Exception {
    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(), date, id(identity), EventAttendeeResponse.ACCEPTED);
    for (EventAttendee row : agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees()) {
      if (row.getFromOccurrenceId() == null) {
        attendeeStorage.removeEventAttendee(row.getId());
      }
    }
    assertEquals("the attendee has one row, from a later date",
                 1,
                 agendaEventAttendeeService.getEventAttendees(series.getId()).size());
  }

  /**
   * A date poll of the space calendar with two dates, created by the first
   * test user, who invites the second one.
   */
  private Event datePoll() throws Exception {
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);
    event.setRecurrence(recurrence);
    ZonedDateTime start = getDate();
    List<EventDateOption> dateOptions = Arrays.asList(new EventDateOption(0, 0, start, start, false, false, null),
                                                      new EventDateOption(0,
                                                                          0,
                                                                          start.plusDays(1),
                                                                          start.plusDays(1),
                                                                          true,
                                                                          true,
                                                                          null));
    return agendaEventService.createEvent(event,
                                          Arrays.asList(new EventAttendee(0,
                                                                          id(testuser1Identity),
                                                                          EventAttendeeResponse.NEEDS_ACTION),
                                                        new EventAttendee(0,
                                                                          id(testuser2Identity),
                                                                          EventAttendeeResponse.NEEDS_ACTION)),
                                          Collections.emptyList(),
                                          Collections.emptyList(),
                                          dateOptions,
                                          null,
                                          true,
                                          id(testuser1Identity));
  }

  /**
   * The occurrence ids of a series' dates, as the occurrence list gives them.
   */
  private List<ZonedDateTime> occurrenceIds(Event series) {
    Event stored = agendaEventService.getEventById(series.getId());
    ZonedDateTime start = stored.getStart();
    return agendaEventService.getEventOccurrencesInPeriod(stored, start.minusDays(1), start.plusDays(5), stored.getTimeZoneId(), 0)
                             .stream()
                             .map(occurrence -> occurrence.getOccurrence().getId())
                             .collect(Collectors.toList());
  }

  /**
   * Writes the capacity of an event row with a targeted update, and drops the
   * row from the event cache.
   */
  private void setCapacity(long eventId, int capacity) {
    inOtherTransaction(entityManager -> entityManager.createQuery("UPDATE AgendaEvent ev SET ev.capacity = :capacity WHERE ev.id = :id")
                                                     .setParameter("capacity", capacity)
                                                     .setParameter("id", eventId)
                                                     .executeUpdate());
    agendaEventStorage.evictEvents(List.of(eventId));
  }

  private void answer(long eventId, Identity identity, EventAttendeeResponse response) throws Exception {
    agendaEventAttendeeService.sendEventResponse(eventId, id(identity), response);
  }

  private EventResponseOutcome answerDate(long seriesId,
                                          ZonedDateTime date,
                                          Identity identity,
                                          EventAttendeeResponse response) throws Exception {
    return agendaEventAttendeeService.sendEventResponse(seriesId, date, id(identity), response, true);
  }

  /** What the organiser does from the participants drawer. */
  private void removeAttendee(long eventId, long identityId, long modifierId) {
    Event event = agendaEventService.getEventById(eventId);
    List<EventAttendee> remainingAttendees = agendaEventAttendeeService.getEventAttendees(eventId)
                                                                       .getEventAttendees()
                                                                       .stream()
                                                                       .filter(attendee -> attendee.getIdentityId() != identityId)
                                                                       .collect(Collectors.toList());
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  remainingAttendees,
                                                  modifierId,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(eventId, event.getCalendarId(), modifierId));
  }

  /**
   * Replaces an attendee's rows on an event with the ranged pair a series
   * leaves: an answer up to a date, then another from it, the first one
   * stored first, both given two hours ago.
   */
  private void splitAtDate(long eventId,
                           Identity identity,
                           ZonedDateTime date,
                           EventAttendeeResponse untilDate,
                           EventAttendeeResponse fromDate) {
    ZonedDateTime responseDate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
    agendaEventAttendeeService.getEventAttendees(eventId)
                              .getEventAttendees(id(identity))
                              .forEach(row -> attendeeStorage.removeEventAttendee(row.getId()));
    attendeeStorage.saveEventAttendee(new EventAttendee(0, eventId, id(identity), null, date, untilDate, responseDate), eventId);
    attendeeStorage.saveEventAttendee(new EventAttendee(0, eventId, id(identity), date, null, fromDate, responseDate), eventId);
  }

  private EventAttendee rowOf(long eventId, Identity identity) {
    return agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendee(id(identity), null);
  }

  private EventAttendeeResponse responseOf(long eventId, Identity identity) {
    EventAttendee row = rowOf(eventId, identity);
    return row == null ? null : row.getResponse();
  }

  private static List<Broadcast> broadcasts(String name) {
    synchronized (BROADCASTS) {
      return BROADCASTS.stream().filter(broadcast -> broadcast.name().equals(name)).toList();
    }
  }

  private static Broadcast only(String name) {
    List<Broadcast> broadcasts = broadcasts(name);
    assertEquals(name + " is broadcast once: " + broadcasts, 1, broadcasts.size());
    return broadcasts.get(0);
  }

  private static List<Long> identitiesOf(String name) {
    return broadcasts(name).stream().map(broadcast -> ((EventAttendee) broadcast.data()).getIdentityId()).toList();
  }

  private List<RecordedStatement> record(Action action) throws Exception {
    RecordingStatementInspector.start();
    try {
      action.run();
      return RecordingStatementInspector.stop();
    } finally {
      RecordingStatementInspector.stop();
    }
  }

  private void markInOtherTransaction(long eventId) {
    writeMarkInOtherTransaction(eventId, new Date());
  }

  private void writeMarkInOtherTransaction(long eventId, Date capacityReachedDate) {
    inOtherTransaction(entityManager -> entityManager.createQuery("UPDATE AgendaEvent ev SET ev.capacityReachedDate = :date WHERE ev.id = :id")
                                                     .setParameter("date", capacityReachedDate)
                                                     .setParameter("id", eventId)
                                                     .executeUpdate());
  }

  private Event saveWholeEvent(Event event) throws Exception {
    long eventId = event.getId();
    return agendaEventService.updateEvent(event,
                                          agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(),
                                          agendaEventConferenceService.getEventConferences(eventId),
                                          agendaEventReminderService.getEventReminders(eventId),
                                          null,
                                          null,
                                          false,
                                          id(testuser1Identity));
  }

  private void inOtherTransaction(Consumer<EntityManager> write) {
    EntityManager other = entityManagerService.getEntityManagerFactory().createEntityManager();
    try {
      other.getTransaction().begin();
      write.accept(other);
      other.getTransaction().commit();
    } finally {
      other.close();
    }
  }

  private static long id(Identity identity) {
    return Long.parseLong(identity.getId());
  }

  /**
   * A broadcast recorded by the listeners.
   */
  private record Broadcast(String name, Object source, Object data) {
  }

  /**
   * An action that may throw.
   */
  @FunctionalInterface
  private interface Action {
    void run() throws Exception;
  }
}
