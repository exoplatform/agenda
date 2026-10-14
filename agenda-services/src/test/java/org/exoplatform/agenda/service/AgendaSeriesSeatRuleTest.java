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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jakarta.persistence.EntityManager;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.model.EventResponseOutcome;
import org.exoplatform.agenda.model.EventSeatCounts;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.identity.model.Identity;

/**
 * Pins the seat rule on the pool of a series, on the container: an answer
 * covering a range of dates is decided stretch by stretch at the first
 * counted date of each, a stretch whose dates have their own row or are over
 * keeping the seat the attendee held over it, else taking its counted
 * neighbour's decision, and stored as one row per run of dates
 * with the same outcome; a seat given up promotes the waiting rows on the
 * dates where a seat is free; the series is marked once when an answer
 * fills it; the answer is reported once; and the answer to the series as a
 * whole is read at its next date.
 * <p>
 * The series have a count, never an end date, whose read-back depends on the
 * JVM's zone, and start in sixty days, so that no date of them is
 * materialised by the reminder computing listener, unless a test says
 * otherwise. The capacity is written with a targeted update.
 * <p>
 * <b>What this does not verify.</b> The container's HSQLDB runs in MVCC,
 * where a {@code SELECT … FOR UPDATE} does not make a second one wait: two
 * answers on the last seat given at once are not serialised here.
 */
public class AgendaSeriesSeatRuleTest extends BaseAgendaEventTest {

  private static final List<String>    WATCHED    = List.of(Utils.POST_EVENT_RESPONSE_SAVED,
                                                            Utils.POST_EVENT_RESPONSE_SENT,
                                                            Utils.POST_EVENT_ATTENDEE_PROMOTED,
                                                            Utils.POST_EVENT_CAPACITY_REACHED);

  private static final List<Broadcast> BROADCASTS = Collections.synchronizedList(new ArrayList<>());

  private static final ZoneId          BRISBANE   = ZoneId.of("Australia/Brisbane");

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
  }

  /**
   * On a series of one seat taken from its third date, a Yes to every date is
   * seated on the dates before it and waits from it: two rows, the outcome
   * reading the next date's answer and the first date left waiting, and one
   * answer reported, not one per row.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesToEveryDateIsSeatedBeforeAFullDateAndWaitsFromIt() throws Exception {
    Event series = limitedSeries(1, 6, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerFrom(series.getId(), dates.get(2), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    EventResponseOutcome outcome = agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                                                null,
                                                                                id(testuser3Identity),
                                                                                EventAttendeeResponse.ACCEPTED,
                                                                                true);

    assertEquals(new EventResponseOutcome(EventAttendeeResponse.ACCEPTED, dates.get(2)), outcome);
    assertRows(series.getId(),
               testuser3Identity,
               row(null, dates.get(2), EventAttendeeResponse.ACCEPTED),
               row(dates.get(2), null, EventAttendeeResponse.WAITING));
    EventAttendee saved = (EventAttendee) only(Utils.POST_EVENT_RESPONSE_SAVED).data();
    EventAttendee sent = (EventAttendee) only(Utils.POST_EVENT_RESPONSE_SENT).data();
    assertEquals(id(testuser3Identity), saved.getIdentityId());
    assertEquals(EventAttendeeResponse.ACCEPTED, saved.getResponse());
    assertEquals(saved, sent);
  }

  /**
   * An answer to every date is reported, and returned, as the row valid at
   * the next date by start: a date moved before the first one of the series
   * is that date, though a later row covers it.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anAnswerToEveryDateIsReportedAtTheNextDateByStart() throws Exception {
    Event series = limitedSeries(1, 5, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerFrom(series.getId(), dates.get(3), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    Event moved = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(3));
    moved.setStart(moved.getStart().minusDays(4));
    moved.setEnd(moved.getEnd().minusDays(4));
    // As a move through the service marks it
    moved.getOccurrence().setDatesModified(true);
    agendaEventStorage.updateEvent(moved);
    BROADCASTS.clear();

    EventResponseOutcome outcome = agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                                                null,
                                                                                id(testuser3Identity),
                                                                                EventAttendeeResponse.ACCEPTED,
                                                                                true);

    assertRows(series.getId(),
               testuser3Identity,
               row(null, dates.get(3), EventAttendeeResponse.ACCEPTED),
               row(dates.get(3), null, EventAttendeeResponse.WAITING));
    assertEquals(EventAttendeeResponse.WAITING, outcome.response());
    EventAttendee sent = (EventAttendee) only(Utils.POST_EVENT_RESPONSE_SENT).data();
    assertEquals(id(testuser3Identity), sent.getIdentityId());
    assertEquals(dates.get(3), sent.getFromOccurrenceId());
  }

  /**
   * A Yes from a date on is seated up to the full date and waits from it; the
   * overload that does not report the answer as sent returns the outcome at
   * the date answered from.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesFromADateIsDecidedDateByDateWithoutBeingReportedAsSent() throws Exception {
    Event series = limitedSeries(1, 6, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerFrom(series.getId(), dates.get(3), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    EventResponseOutcome outcome = agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                                                        dates.get(1),
                                                                                        id(testuser3Identity),
                                                                                        EventAttendeeResponse.ACCEPTED,
                                                                                        false);

    assertEquals(new EventResponseOutcome(EventAttendeeResponse.ACCEPTED, dates.get(3)), outcome);
    assertRows(series.getId(),
               testuser3Identity,
               row(null, dates.get(1), EventAttendeeResponse.NEEDS_ACTION),
               row(dates.get(1), dates.get(3), EventAttendeeResponse.ACCEPTED),
               row(dates.get(3), null, EventAttendeeResponse.WAITING));
    assertEquals(dates.get(1), ((EventAttendee) only(Utils.POST_EVENT_RESPONSE_SAVED).data()).getFromOccurrenceId());
    assertTrue(broadcasts(Utils.POST_EVENT_RESPONSE_SENT).isEmpty());
  }

  /**
   * An attendee whose only row starts at a later date answers every date: the
   * row covers the dates before it too, with no capacity limit as with one.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anAnswerToEveryDateCoversTheDatesBeforeALoneRow() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    leaveOneRowFrom(series, dates.get(2), testuser2Identity);

    answer(series.getId(), testuser2Identity, EventAttendeeResponse.DECLINED);

    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(series.getId(), dates.get(0), id(testuser2Identity)));
    assertRows(series.getId(), testuser2Identity, row(null, null, EventAttendeeResponse.DECLINED));
  }

  /**
   * A seat given up from a date promotes the waiting row on the dates where a
   * seat is free only: the part promoted is a Yes of now, reported as
   * promoted once and never as sent; the rest keeps waiting with its row and
   * its answer date.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aSeatGivenUpFromADatePromotesTheWaitingRowFromThatDateOnly() throws Exception {
    Event series = limitedSeries(1, 5, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    EventAttendee waiting = rowsOf(series.getId(), testuser3Identity).get(0);
    assertEquals(EventAttendeeResponse.WAITING, waiting.getResponse());
    ZonedDateTime waitingDate = backdate(series.getId(), waiting, 2);
    ZonedDateTime beforePromotion = ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(1);
    BROADCASTS.clear();

    answerFrom(series.getId(), dates.get(2), testuser2Identity, EventAttendeeResponse.DECLINED);

    List<EventAttendee> rows = rowsOf(series.getId(), testuser3Identity);
    assertRows(series.getId(),
               testuser3Identity,
               row(null, dates.get(2), EventAttendeeResponse.WAITING),
               row(dates.get(2), null, EventAttendeeResponse.ACCEPTED));
    assertEquals("the rest keeps its row", waiting.getId(), rows.get(0).getId());
    assertEquals("the rest keeps its answer date", waitingDate, rows.get(0).getResponseDate());
    assertTrue("the part promoted is a Yes of now", rows.get(1).getResponseDate().isAfter(beforePromotion));
    Broadcast promoted = only(Utils.POST_EVENT_ATTENDEE_PROMOTED);
    assertEquals(dates.get(2), ((EventAttendee) promoted.data()).getFromOccurrenceId());
    assertEquals("the promotion is told as a Yes",
                 EventAttendeeResponse.ACCEPTED,
                 ((EventAttendee) promoted.data()).getResponse());
    assertEquals(List.of(id(testuser2Identity)), identitiesOf(Utils.POST_EVENT_RESPONSE_SENT));
  }

  /**
   * An attendee the organiser removes frees their seat on every date: the
   * waiting row is promoted in the same save.
   *
   * @throws Exception when a save fails
   */
  @Test
  public void removingASeatedAttendeeOfASeriesPromotesTheWaitingRow() throws Exception {
    Event series = limitedSeries(1, 3, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    removeAttendee(series.getId(), id(testuser2Identity), id(testuser1Identity));

    assertRows(series.getId(), testuser3Identity, row(null, null, EventAttendeeResponse.ACCEPTED));
    EventAttendee promoted = (EventAttendee) only(Utils.POST_EVENT_ATTENDEE_PROMOTED).data();
    assertEquals(id(testuser3Identity), promoted.getIdentityId());
    assertEquals("the promotion is reported from the first date promoted", dates.get(0), promoted.getFromOccurrenceId());
  }

  /**
   * A promotion on a series is reported from the first date the attendee got
   * a seat for, not from the start of the row promoted: the series' first
   * date, which has a row of its own, is not one of them.
   *
   * @throws Exception when a save fails
   */
  @Test
  public void aPromotionOnASeriesIsReportedFromItsFirstCountedDate() throws Exception {
    Event series = limitedSeries(1, 3, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));
    BROADCASTS.clear();

    removeAttendee(series.getId(), id(testuser2Identity), id(testuser1Identity));

    List<EventAttendee> promotedOnSeries = broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED).stream()
                                                                                         .map(broadcast -> (EventAttendee) broadcast.data())
                                                                                         .filter(promoted -> promoted.getEventId() == series.getId())
                                                                                         .toList();
    assertEquals(1, promotedOnSeries.size());
    assertRows(series.getId(), testuser3Identity, row(null, null, EventAttendeeResponse.ACCEPTED));
    assertEquals(dates.get(1), promotedOnSeries.get(0).getFromOccurrenceId());
  }

  /**
   * A seat given up on a series goes to one waiting row, the one waiting
   * longest by its answer date, though stored after the other: the next
   * waiting row counts the seat the first one took.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aSeatGivenUpOnASeriesGoesToTheLongestWaitingRowOnly() throws Exception {
    Event series = limitedSeries(1, 3, testuser2Identity, testuser4Identity, testuser3Identity);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(series.getId(), testuser4Identity, EventAttendeeResponse.ACCEPTED);
    answer(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    backdate(series.getId(), rowsOf(series.getId(), testuser3Identity).get(0), 2);
    BROADCASTS.clear();

    answer(series.getId(), testuser2Identity, EventAttendeeResponse.DECLINED);

    assertRows(series.getId(), testuser3Identity, row(null, null, EventAttendeeResponse.ACCEPTED));
    assertRows(series.getId(), testuser4Identity, row(null, null, EventAttendeeResponse.WAITING));
    assertEquals(id(testuser3Identity), ((EventAttendee) only(Utils.POST_EVENT_ATTENDEE_PROMOTED).data()).getIdentityId());
  }

  /**
   * A waiting row of a series whose every date has its own row holds no
   * counted date: a seat given up leaves it waiting on the series, each
   * date's own row promoting alone.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aWaitingRowWithNoCountedDateIsLeftWaiting() throws Exception {
    Event series = limitedSeries(1, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(), testuser2Identity, row(null, null, EventAttendeeResponse.ACCEPTED));
    replaceRows(series.getId(),
                testuser3Identity,
                row(null, dates.get(2), EventAttendeeResponse.WAITING),
                row(dates.get(2), null, EventAttendeeResponse.DECLINED));
    Event firstDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));
    Event secondDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(1));
    BROADCASTS.clear();

    answer(series.getId(), testuser2Identity, EventAttendeeResponse.DECLINED);

    assertRows(series.getId(),
               testuser3Identity,
               row(null, dates.get(2), EventAttendeeResponse.WAITING),
               row(dates.get(2), null, EventAttendeeResponse.DECLINED));
    assertTrue("no promotion on the series",
               broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED).stream()
                                                             .noneMatch(broadcast -> ((EventAttendee) broadcast.data()).getEventId() == series.getId()));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOn(firstDate.getId(), testuser3Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOn(secondDate.getId(), testuser3Identity));
  }

  /**
   * A stretch made only of a date that has its own row decides nothing: it
   * takes the decision of the counted stretch before it, else of the one
   * after it, and the first date left waiting is a counted one. The answer
   * returned is the one stored at the date answered from, by its own row.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aStretchOfADateWithItsOwnRowTakesItsNeighboursDecision() throws Exception {
    Event series = limitedSeries(1, 5, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerFrom(series.getId(), dates.get(2), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(1));

    EventResponseOutcome outcome = agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                                                        dates.get(1),
                                                                                        id(testuser3Identity),
                                                                                        EventAttendeeResponse.ACCEPTED,
                                                                                        true);

    assertRows(series.getId(),
               testuser3Identity,
               row(null, dates.get(1), EventAttendeeResponse.NEEDS_ACTION),
               row(dates.get(1), null, EventAttendeeResponse.WAITING));
    assertEquals(dates.get(2), outcome.firstWaitingOccurrenceId());
    assertEquals(EventAttendeeResponse.ACCEPTED, outcome.response());
  }

  /**
   * The dates over decide nothing: a stretch made only of them takes the
   * decision of the stretch after it, though a seat there is taken.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aStretchOfDatesOverTakesTheDecisionAfterIt() throws Exception {
    ZonedDateTime start = LocalDate.now().minusDays(10).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event series = limitedSeries(start, 1, 20, testuser2Identity, testuser3Identity);
    ZonedDateTime pastDate = start.plusDays(5);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerFrom(series.getId(), pastDate, testuser2Identity, EventAttendeeResponse.DECLINED);

    answer(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertRows(series.getId(), testuser3Identity, row(null, null, EventAttendeeResponse.ACCEPTED));
  }

  /**
   * An answer from a date given again unchanged keeps its place on the
   * waiting list; a Yes after a No gets a new one.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aPieceKeepsItsAnswerDateOnlyWhileItsAnswerIsUnchanged() throws Exception {
    Event series = limitedSeries(1, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerFrom(series.getId(), dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    ZonedDateTime waitingDate = backdate(series.getId(), rowsOf(series.getId(), testuser3Identity).get(1), 2);

    answerFrom(series.getId(), dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    EventAttendee again = rowsOf(series.getId(), testuser3Identity).get(1);
    assertEquals(EventAttendeeResponse.WAITING, again.getResponse());
    assertEquals("an unchanged answer keeps its date", waitingDate, again.getResponseDate());

    answerFrom(series.getId(), dates.get(1), testuser3Identity, EventAttendeeResponse.DECLINED);
    ZonedDateTime declinedDate = backdate(series.getId(), rowsOf(series.getId(), testuser3Identity).get(1), 1);
    answerFrom(series.getId(), dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    EventAttendee rejoined = rowsOf(series.getId(), testuser3Identity).get(1);
    assertEquals(EventAttendeeResponse.WAITING, rejoined.getResponse());
    assertTrue("a Yes after a No gets a new date", rejoined.getResponseDate().isAfter(declinedDate));
  }

  /**
   * The answer filling a counted date marks the series once, reporting that
   * date; a waiting answer, and a Yes given again on a marked series, report
   * nothing.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theAnswerFillingTheSeriesMarksItOnce() throws Exception {
    Event series = limitedSeries(1, 3, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);

    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    Broadcast reached = only(Utils.POST_EVENT_CAPACITY_REACHED);
    assertEquals(series.getId(), reached.source());
    assertEquals(dates.get(0), reached.data());
    assertNotNull(agendaEventStorage.getEventById(series.getId()).getCapacityReachedDate());
    BROADCASTS.clear();

    answer(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * On dates booked past the limit, a Yes again from an attendee who holds a
   * seat there keeps it: a Yes takes a free seat or keeps the one held.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesAgainKeepsTheSeatHeldOnDatesBookedPastTheLimit() throws Exception {
    Event series = limitedSeries(0, 3, testuser2Identity, testuser3Identity);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    setCapacity(series.getId(), 1);

    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertRows(series.getId(), testuser2Identity, row(null, null, EventAttendeeResponse.ACCEPTED));
  }

  /**
   * A seat is held on the dates of the row holding it only: a Yes to every
   * date from an attendee seated up to a date waits from that date, where
   * another attendee holds the only seat.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aSeatHeldUpToADateIsKeptOnThoseDatesOnly() throws Exception {
    Event series = limitedSeries(1, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(),
                testuser2Identity,
                row(null, dates.get(2), EventAttendeeResponse.ACCEPTED),
                row(dates.get(2), null, EventAttendeeResponse.DECLINED));
    replaceRows(series.getId(),
                testuser3Identity,
                row(null, dates.get(2), EventAttendeeResponse.DECLINED),
                row(dates.get(2), null, EventAttendeeResponse.ACCEPTED));

    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertRows(series.getId(),
               testuser2Identity,
               row(null, dates.get(2), EventAttendeeResponse.ACCEPTED),
               row(dates.get(2), null, EventAttendeeResponse.WAITING));
  }

  /**
   * A Yes again keeps the seat held over a stretch that decides nothing, its
   * dates having their own rows, though the stretch after it is full: the
   * attendee stays seated there and waits from the full date, and the answer
   * is returned, reported and read at the next date, still seated.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesAgainKeepsTheSeatHeldOverDatesThatDecideNothing() throws Exception {
    Event series = limitedSeries(1, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(),
                testuser2Identity,
                row(null, dates.get(2), EventAttendeeResponse.ACCEPTED),
                row(dates.get(2), null, EventAttendeeResponse.DECLINED));
    replaceRows(series.getId(),
                testuser3Identity,
                row(null, dates.get(2), EventAttendeeResponse.DECLINED),
                row(dates.get(2), null, EventAttendeeResponse.ACCEPTED));
    Event firstDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(0));
    Event secondDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(1));
    BROADCASTS.clear();

    EventResponseOutcome outcome = agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                                                null,
                                                                                id(testuser2Identity),
                                                                                EventAttendeeResponse.ACCEPTED,
                                                                                true);

    assertRows(series.getId(),
               testuser2Identity,
               row(null, dates.get(2), EventAttendeeResponse.ACCEPTED),
               row(dates.get(2), null, EventAttendeeResponse.WAITING));
    assertEquals(EventAttendeeResponse.ACCEPTED, outcome.response());
    assertEquals(dates.get(2), outcome.firstWaitingOccurrenceId());
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 ((EventAttendee) only(Utils.POST_EVENT_RESPONSE_SENT).data()).getResponse());
    assertEquals(EventAttendeeResponse.ACCEPTED, agendaEventAttendeeService.getSeriesResponse(series.getId(), id(testuser2Identity)));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOn(firstDate.getId(), testuser2Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOn(secondDate.getId(), testuser2Identity));
  }

  /**
   * An answer from a date reaches the row of that date stored before the
   * date's instant, on the same day of the series' zone but on the UTC day
   * before (01:00 for a date at noon, ten hours east of UTC), and leaves the
   * row of the date before.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anAnswerFromADateReachesTheRowOfThatDateStoredEarlierThatDay() throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(12, 0).atZone(BRISBANE);
    Event series = limitedSeries(start, 0, 4, testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    Event dateBefore = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(1));
    ZonedDateTime storedOccurrenceId = atLocalTime(dates.get(2), BRISBANE, LocalTime.of(1, 0));
    assertEquals("the row is stored on the UTC day before its date",
                 dates.get(2).toLocalDate().minusDays(1),
                 storedOccurrenceId.withZoneSameInstant(ZoneOffset.UTC).toLocalDate());
    Event dateRow = rowStoredAt(series, storedOccurrenceId, testuser2Identity);

    answerFrom(series.getId(), dates.get(2), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOn(dateRow.getId(), testuser2Identity));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOn(dateBefore.getId(), testuser2Identity));
  }

  /**
   * On a series with several dates a day, an answer from a date reaches the
   * rows of that date and of the later ones, and leaves the row of the date
   * before it on the same day.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anAnswerFromAnHourOfAnHourlySeriesLeavesTheHourBefore() throws Exception {
    Event series = hourlySeries(4, testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    Event hourBefore = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(1));
    Event hourFrom = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(2));
    Event hourAfter = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(3));

    answerFrom(series.getId(), dates.get(2), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOn(hourBefore.getId(), testuser2Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOn(hourFrom.getId(), testuser2Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOn(hourAfter.getId(), testuser2Identity));
  }

  /**
   * The answer to a series as a whole is read at its next date, whatever the
   * order of the rows; an attendee with no row reads no answer.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void theSeriesAnswerIsReadAtItsNextDate() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(),
                testuser2Identity,
                row(dates.get(2), null, EventAttendeeResponse.ACCEPTED),
                row(null, dates.get(2), EventAttendeeResponse.DECLINED));
    answer(series.getId(), testuser3Identity, EventAttendeeResponse.TENTATIVE);

    assertEquals(EventAttendeeResponse.DECLINED, agendaEventAttendeeService.getSeriesResponse(series.getId(), id(testuser2Identity)));
    assertEquals(EventAttendeeResponse.TENTATIVE, agendaEventAttendeeService.getSeriesResponse(series.getId(), id(testuser3Identity)));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION,
                 agendaEventAttendeeService.getSeriesResponse(series.getId(), id(testuser4Identity)));
    assertThrows(ObjectNotFoundException.class, () -> agendaEventAttendeeService.getSeriesResponse(-1, id(testuser2Identity)));
  }

  /**
   * The answer to a series whose dates are all over is read at its last date,
   * never on its first row.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void theAnswerToAnEndedSeriesIsReadAtItsLastDate() throws Exception {
    ZonedDateTime start = LocalDate.now().minusDays(30).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event series = limitedSeries(start, 0, 3, testuser2Identity);
    ZonedDateTime lastDate = start.plusDays(2);
    replaceRows(series.getId(),
                testuser2Identity,
                row(null, lastDate, EventAttendeeResponse.DECLINED),
                row(lastDate, null, EventAttendeeResponse.ACCEPTED));

    assertEquals(EventAttendeeResponse.ACCEPTED, agendaEventAttendeeService.getSeriesResponse(series.getId(), id(testuser2Identity)));
  }

  /**
   * A daily series with a count in UTC+10, 08:00 to 09:00 local time, every
   * date cancelled, the user having answered Yes from the last date only:
   * the answer read is Yes. The last date ends, in UTC, on the day before its
   * own date, which a window closed with the UTC date of the series' end
   * leaves out.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void aCancelledSeriesWhoseLastDateEndsOnTheUtcDayBeforeIsReadAtIt() throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(8, 0).atZone(BRISBANE);
    Event series = limitedSeries(start, 0, 3, testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    assertEquals(3, dates.size());
    answerFrom(series.getId(), dates.get(2), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    for (ZonedDateTime date : dates) {
      Event dateRow = agendaEventService.saveEventExceptionalOccurrence(series.getId(), date);
      agendaEventService.updateEventFields(dateRow.getId(),
                                           Map.of("status", List.of(EventStatus.CANCELLED.name())),
                                           false,
                                           false,
                                           id(testuser1Identity));
    }

    assertEquals(EventAttendeeResponse.ACCEPTED, agendaEventAttendeeService.getSeriesResponse(series.getId(), id(testuser2Identity)));
  }

  /**
   * The seats of a date with no row of its own are counted on the series'
   * rows valid at that date, with the rank of a waiting reader; a reader not
   * waiting there has none.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void theSeatsOfADateWithNoRowOfItsOwnAreCountedOnTheSeriesAtThatDate() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity, testuser3Identity, testuser4Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(), testuser2Identity, row(null, null, EventAttendeeResponse.ACCEPTED));
    replaceRows(series.getId(),
                testuser3Identity,
                row(null, dates.get(2), EventAttendeeResponse.DECLINED),
                row(dates.get(2), null, EventAttendeeResponse.WAITING));
    replaceRows(series.getId(),
                testuser4Identity,
                row(null, dates.get(2), EventAttendeeResponse.NEEDS_ACTION),
                row(dates.get(2), null, EventAttendeeResponse.ACCEPTED));

    assertEquals(new EventSeatCounts(1, 0, null),
                 agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(0), id(testuser3Identity)));
    assertEquals(new EventSeatCounts(2, 1, 1),
                 agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(2), id(testuser3Identity)));
    assertEquals(new EventSeatCounts(2, 1, null),
                 agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(2), id(testuser2Identity)));
  }

  /**
   * A date with its own row is a pool of its own: its seats are counted on its
   * row, read by the series' date or by the row itself, while the series'
   * rows count the dates with none.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theSeatsOfADateWithItsOwnRowAreCountedOnItsRow() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                 dates.get(1),
                                                 id(testuser3Identity),
                                                 EventAttendeeResponse.ACCEPTED,
                                                 true);
    Event dateRow = agendaEventStorage.findExceptionalOccurrence(agendaEventStorage.getEventById(series.getId()), dates.get(1));
    assertNotNull(dateRow);

    assertEquals(new EventSeatCounts(2, 0, null), agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(1), 0));
    assertEquals(new EventSeatCounts(2, 0, null), agendaEventAttendeeService.getSeatCounts(dateRow.getId(), null, 0));
    assertEquals(new EventSeatCounts(1, 0, null), agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(2), 0));
  }

  /**
   * A date is counted on the rows valid there, though the series holds one
   * row: a list of one row is never read unfiltered.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void aSingleRowRangedAwayFromADateCountsNothingThere() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(), testuser2Identity, row(dates.get(2), null, EventAttendeeResponse.ACCEPTED));

    assertEquals(new EventSeatCounts(0, 0, null), agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(0), 0));
    assertEquals(new EventSeatCounts(1, 0, null), agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(2), 0));
  }

  /**
   * A series read with no date is counted at its next date, not over every
   * row it holds.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void aSeriesReadWithNoDateIsCountedAtItsNextDate() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    replaceRows(series.getId(),
                testuser2Identity,
                row(null, dates.get(1), EventAttendeeResponse.DECLINED),
                row(dates.get(1), null, EventAttendeeResponse.ACCEPTED));
    replaceRows(series.getId(), testuser3Identity, row(null, null, EventAttendeeResponse.WAITING));

    assertEquals(new EventSeatCounts(0, 1, 1), agendaEventAttendeeService.getSeatCounts(series.getId(), null, id(testuser3Identity)));
  }

  /**
   * A series read with no date whose next date has a row of its own is
   * counted on that row, as the date read with its id is, not on the series'
   * rows at that date.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aSeriesReadWithNoDateWhoseNextDateHasItsOwnRowIsCountedOnThatRow() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                 dates.get(0),
                                                 id(testuser3Identity),
                                                 EventAttendeeResponse.ACCEPTED,
                                                 true);

    assertEquals(new EventSeatCounts(2, 0, null), agendaEventAttendeeService.getSeatCounts(series.getId(), null, 0));
    assertEquals(new EventSeatCounts(2, 0, null), agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(0), 0));
    assertEquals(new EventSeatCounts(1, 0, null), agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(1), 0));
  }

  /**
   * The rank on a waiting list is the promotion order: the earliest answer
   * first, the row stored first among answers given at the same instant.
   *
   * @throws Exception when a read fails
   */
  @Test
  public void theRankOnAWaitingListIsThePromotionOrder() throws Exception {
    Event series = limitedSeries(0, 3, testuser2Identity, testuser3Identity, testuser4Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    ZonedDateTime sameInstant = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0);
    for (Identity identity : List.of(testuser2Identity, testuser3Identity)) {
      replaceRows(series.getId(), identity, row(null, null, EventAttendeeResponse.WAITING));
      EventAttendee waiting = rowsOf(series.getId(), identity).get(0);
      waiting.setResponseDate(sameInstant);
      attendeeStorage.saveEventAttendee(waiting, series.getId());
    }
    replaceRows(series.getId(), testuser4Identity, row(null, null, EventAttendeeResponse.WAITING));
    backdate(series.getId(), rowsOf(series.getId(), testuser4Identity).get(0), 3);

    assertEquals(Integer.valueOf(1),
                 agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(0), id(testuser4Identity))
                                           .waitingListPosition());
    assertEquals(Integer.valueOf(2),
                 agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(0), id(testuser2Identity))
                                           .waitingListPosition());
    assertEquals(Integer.valueOf(3),
                 agendaEventAttendeeService.getSeatCounts(series.getId(), dates.get(0), id(testuser3Identity))
                                           .waitingListPosition());
  }

  /**
   * An answer to a series read with no date is the row valid at its next
   * date, not the user's first row; the attendees of the series read with no
   * date are each one row, the one valid there.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aSeriesAnswerReadWithNoDateIsTheRowAtItsNextDate() throws Exception {
    ZonedDateTime start = LocalDate.now().minusDays(10).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event series = limitedSeries(start, 0, 20, testuser2Identity, testuser3Identity);
    ZonedDateTime pastDate = start.plusDays(5);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerFrom(series.getId(), pastDate, testuser2Identity, EventAttendeeResponse.DECLINED);
    assertEquals("the user's first row is the Yes before the past date",
                 EventAttendeeResponse.ACCEPTED,
                 rowsOf(series.getId(), testuser2Identity).get(0).getResponse());

    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(series.getId(), null, id(testuser2Identity)));
    EventAttendee read = agendaEventAttendeeService.getSeriesAttendee(series.getId(), id(testuser2Identity));
    assertEquals(EventAttendeeResponse.DECLINED, read.getResponse());
    List<EventAttendee> attendees = agendaEventAttendeeService.getSeriesAttendees(series.getId());
    assertEquals(List.of(id(testuser2Identity), id(testuser3Identity)),
                 attendees.stream().map(EventAttendee::getIdentityId).sorted().toList());
    assertEquals(read.getId(),
                 attendees.stream().filter(attendee -> attendee.getIdentityId() == id(testuser2Identity)).findFirst().orElseThrow().getId());
  }

  /**
   * An attendee whose rows all start after the series' next date is read once,
   * answering nothing there, and their stored row keeps its answer; a user
   * with no row is not read.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void anAttendeeWhoseRowsStartAfterTheNextDateIsReadWithNoAnswer() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    leaveOneRowFrom(series, dates.get(2), testuser3Identity);
    EventAttendee storedRow = rowsOf(series.getId(), testuser3Identity).get(0);

    EventAttendee read = agendaEventAttendeeService.getSeriesAttendee(series.getId(), id(testuser3Identity));
    assertEquals(storedRow.getId(), read.getId());
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, read.getResponse());
    assertNull(read.getResponseDate());
    assertEquals(EventAttendeeResponse.NEEDS_ACTION,
                 agendaEventAttendeeService.getSeriesResponse(series.getId(), id(testuser3Identity)));
    List<EventAttendee> attendees = agendaEventAttendeeService.getSeriesAttendees(series.getId());
    assertEquals(List.of(id(testuser2Identity), id(testuser3Identity)),
                 attendees.stream().map(EventAttendee::getIdentityId).sorted().toList());
    assertEquals(EventAttendeeResponse.NEEDS_ACTION,
                 attendees.stream()
                          .filter(attendee -> attendee.getIdentityId() == id(testuser3Identity))
                          .findFirst()
                          .orElseThrow()
                          .getResponse());
    assertRows(series.getId(), testuser3Identity, row(dates.get(2), null, EventAttendeeResponse.ACCEPTED));
    assertNull(agendaEventAttendeeService.getSeriesAttendee(series.getId(), id(testuser4Identity)));
  }

  /**
   * A series saved back with the attendees its read with no date lists, as
   * the dialogs that delete or change its upcoming dates do, keeps the rows of
   * an attendee whose rows all start after its next date.
   *
   * @throws Exception when the save fails
   */
  @Test
  public void aSeriesSavedBackFromItsAttendeeListKeepsAnAttendeeAnsweringFromALaterDate() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    leaveOneRowFrom(series, dates.get(2), testuser3Identity);
    // The dialogs send each attendee's identity back, nothing of their rows
    List<EventAttendee> savedAttendees = agendaEventAttendeeService.getSeriesAttendees(series.getId())
                                                                   .stream()
                                                                   .map(attendee -> new EventAttendee(0,
                                                                                                      series.getId(),
                                                                                                      attendee.getIdentityId(),
                                                                                                      EventAttendeeResponse.NEEDS_ACTION))
                                                                   .collect(Collectors.toList());
    Event stored = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, id(testuser1Identity));

    agendaEventService.updateEvent(stored.clone(),
                                   savedAttendees,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   id(testuser1Identity));

    assertRows(series.getId(), testuser3Identity, row(dates.get(2), null, EventAttendeeResponse.ACCEPTED));
  }

  /**
   * The attendees of a date read narrowed by answer are filtered by their
   * range, though one row matches: an attendee answering Yes from a later
   * date is not among the Yes of an earlier one, as the reply notification
   * reads them.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aDateReadNarrowedByAnswerFiltersTheOneRowItMatches() throws Exception {
    Event series = limitedSeries(0, 4, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(series.getId(), testuser2Identity, EventAttendeeResponse.TENTATIVE);
    answer(series.getId(), testuser3Identity, EventAttendeeResponse.DECLINED);
    answerFrom(series.getId(), dates.get(2), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals("one row answers Yes", 1, agendaEventAttendeeService.getEventAttendees(series.getId(), EventAttendeeResponse.ACCEPTED).size());

    assertEquals(List.of(),
                 agendaEventAttendeeService.getEventAttendees(series.getId(), dates.get(0), EventAttendeeResponse.ACCEPTED));
    assertEquals(List.of(id(testuser3Identity)),
                 agendaEventAttendeeService.getEventAttendees(series.getId(), dates.get(2), EventAttendeeResponse.ACCEPTED)
                                           .stream()
                                           .map(EventAttendee::getIdentityId)
                                           .toList());
  }

  /**
   * A No to every date of a series reaches the row of a date over as given and
   * gives up no seat there: nobody waiting on that date is promoted.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aNoToEveryDateGivesUpNoSeatOnADateOver() throws Exception {
    ZonedDateTime start = LocalDate.now().minusDays(10).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event series = limitedSeries(start, 1, 20, testuser2Identity, testuser3Identity);
    long rowId = rowOfADateOverWithItsSeatHeld(series, start.plusDays(2));

    answer(series.getId(), testuser2Identity, EventAttendeeResponse.DECLINED);

    assertEquals(EventAttendeeResponse.DECLINED, responseOn(rowId, testuser2Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOn(rowId, testuser3Identity));
    assertTrue(broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED).isEmpty());
  }

  /**
   * A No to a series from a date reaches the row of a later date over as given
   * and gives up no seat there.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aNoFromADateGivesUpNoSeatOnALaterDateOver() throws Exception {
    ZonedDateTime start = LocalDate.now().minusDays(10).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event series = limitedSeries(start, 1, 20, testuser2Identity, testuser3Identity);
    long rowId = rowOfADateOverWithItsSeatHeld(series, start.plusDays(2));

    answerFrom(series.getId(), start.plusDays(1), testuser2Identity, EventAttendeeResponse.DECLINED);

    assertEquals(EventAttendeeResponse.DECLINED, responseOn(rowId, testuser2Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOn(rowId, testuser3Identity));
    assertTrue(broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED).isEmpty());
  }

  /**
   * A Yes to a series from a date reaches the row of a full later date still
   * to come as a place on its waiting list: a seat is decided there.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesFromADateWaitsOnAFullLaterDateStillToCome() throws Exception {
    Event series = limitedSeries(1, 5, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = occurrenceIds(series);
    long rowId = agendaEventService.saveEventExceptionalOccurrence(series.getId(), dates.get(2)).getId();
    setCapacity(rowId, 1);
    replaceRows(rowId, testuser3Identity, new EventAttendee(0, rowId, id(testuser3Identity), EventAttendeeResponse.ACCEPTED));
    replaceRows(rowId, testuser2Identity, new EventAttendee(0, rowId, id(testuser2Identity), EventAttendeeResponse.NEEDS_ACTION));

    answerFrom(series.getId(), dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.WAITING, responseOn(rowId, testuser2Identity));
  }

  /**
   * A Yes to every date of a series reaches the row of a full date over as a
   * Yes: it waits there for no seat, reports no date left waiting, and does
   * not mark the date full.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aYesToEveryDateWaitsOnNoDateOver() throws Exception {
    ZonedDateTime start = LocalDate.now().minusDays(10).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event series = limitedSeries(start, 1, 20, testuser2Identity, testuser3Identity);
    long rowId = agendaEventService.saveEventExceptionalOccurrence(series.getId(), start.plusDays(2)).getId();
    setCapacity(rowId, 1);
    replaceRows(rowId, testuser3Identity, new EventAttendee(0, rowId, id(testuser3Identity), EventAttendeeResponse.ACCEPTED));
    replaceRows(rowId, testuser2Identity, new EventAttendee(0, rowId, id(testuser2Identity), EventAttendeeResponse.NEEDS_ACTION));

    EventResponseOutcome outcome = agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                                                null,
                                                                                id(testuser2Identity),
                                                                                EventAttendeeResponse.ACCEPTED,
                                                                                true);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOn(rowId, testuser2Identity));
    assertNull(outcome.firstWaitingOccurrenceId());
    assertNull(agendaEventStorage.getEventById(rowId).getCapacityReachedDate());
  }

  /**
   * Materialises the row of a date of a series that is over, with a limit of
   * one seat: testuser2 holds it with a Yes, testuser3 waits, as answers given
   * while the date was still to come left them.
   *
   * @return the id of the row
   */
  private long rowOfADateOverWithItsSeatHeld(Event series, ZonedDateTime date) throws Exception {
    long rowId = agendaEventService.saveEventExceptionalOccurrence(series.getId(), date).getId();
    setCapacity(rowId, 1);
    replaceRows(rowId, testuser2Identity, new EventAttendee(0, rowId, id(testuser2Identity), EventAttendeeResponse.ACCEPTED));
    replaceRows(rowId, testuser3Identity, new EventAttendee(0, rowId, id(testuser3Identity), EventAttendeeResponse.WAITING));
    return rowId;
  }

  /**
   * A series in the first test user's calendar, who is not one of its
   * attendees, daily at the start's time with a count, with the capacity
   * given.
   */
  private Event limitedSeries(ZonedDateTime start, int capacity, int count, Identity... attendees) throws Exception {
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(start.getZone());
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(count);
    Event created = createEvent(event, id(testuser1Identity), attendees);
    if (capacity > 0) {
      setCapacity(created.getId(), capacity);
    }
    return created;
  }

  /**
   * A daily series at 09:00 UTC starting in sixty days.
   */
  private Event limitedSeries(int capacity, int count, Identity... attendees) throws Exception {
    return limitedSeries(LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC), capacity, count, attendees);
  }

  /**
   * An hourly series of half-hour dates at 09:00 UTC starting in sixty days,
   * with a count, in the first test user's calendar.
   */
  private Event hourlySeries(int count, Identity... attendees) throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event event = newEventInstance(start, start.plusMinutes(30), false);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.HOURLY);
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
   * Leaves an attendee of a series one row, from a later date: the answer from
   * that date splits the attendee's row, and the row before it is removed.
   */
  private void leaveOneRowFrom(Event series, ZonedDateTime date, Identity identity) throws Exception {
    answerFrom(series.getId(), date, identity, EventAttendeeResponse.ACCEPTED);
    for (EventAttendee row : rowsOf(series.getId(), identity)) {
      if (row.getFromOccurrenceId() == null) {
        attendeeStorage.removeEventAttendee(row.getId());
      }
    }
    assertEquals("the attendee has one row, from a later date", 1, rowsOf(series.getId(), identity).size());
  }

  /**
   * Replaces an attendee's rows on an event with the ones given, stored in
   * that order, answered two hours ago.
   */
  private void replaceRows(long eventId, Identity identity, EventAttendee... rows) {
    ZonedDateTime responseDate = ZonedDateTime.now(ZoneOffset.UTC).minusHours(2);
    rowsOf(eventId, identity).forEach(row -> attendeeStorage.removeEventAttendee(row.getId()));
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
   * Moves the answer date of a row some hours back.
   *
   * @return the answer date as stored
   */
  private ZonedDateTime backdate(long eventId, EventAttendee row, int hours) {
    row.setResponseDate(ZonedDateTime.now(ZoneOffset.UTC).minusHours(hours));
    attendeeStorage.saveEventAttendee(row, eventId);
    return agendaEventAttendeeService.getEventAttendees(eventId)
                                     .getEventAttendees()
                                     .stream()
                                     .filter(stored -> stored.getId() == row.getId())
                                     .findFirst()
                                     .orElseThrow()
                                     .getResponseDate();
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

  private void answerFrom(long seriesId, ZonedDateTime date, Identity identity, EventAttendeeResponse response) throws Exception {
    agendaEventAttendeeService.sendUpcomingEventResponse(seriesId, date, id(identity), response);
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
   * @return an attendee's rows on an event, the one with no start first, then
   *         by start
   */
  private List<EventAttendee> rowsOf(long eventId, Identity identity) {
    return agendaEventAttendeeService.getEventAttendees(eventId)
                                     .getEventAttendees(id(identity))
                                     .stream()
                                     .sorted(Comparator.comparing(EventAttendee::getFromOccurrenceId,
                                                                  Comparator.nullsFirst(Comparator.naturalOrder())))
                                     .toList();
  }

  private EventAttendeeResponse responseOn(long eventId, Identity identity) {
    return agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendee(id(identity), null).getResponse();
  }

  /**
   * Asserts the range and answer of an attendee's rows on an event, in the
   * order of {@link #rowsOf(long, Identity)}.
   */
  private void assertRows(long eventId, Identity identity, EventAttendee... expected) {
    List<String> actual = rowsOf(eventId, identity).stream().map(AgendaSeriesSeatRuleTest::describe).toList();
    assertEquals(Arrays.stream(expected).map(AgendaSeriesSeatRuleTest::describe).toList(), actual);
  }

  private static String describe(EventAttendee row) {
    return instant(row.getFromOccurrenceId()) + ".." + instant(row.getUntilOccurrenceId()) + " " + row.getResponse();
  }

  private static Object instant(ZonedDateTime date) {
    return date == null ? null : date.toInstant();
  }

  private static EventAttendee row(ZonedDateTime from, ZonedDateTime until, EventAttendeeResponse response) {
    return new EventAttendee(0, 0, 0, from, until, response, null);
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
}
