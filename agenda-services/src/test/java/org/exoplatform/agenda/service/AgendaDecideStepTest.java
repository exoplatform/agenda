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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.service.RecordingStatementInspector.RecordedStatement;
import org.exoplatform.agenda.storage.cached.CachedAgendaEventStorage;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.util.DecideContext;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.persistence.impl.EntityManagerService;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.identity.model.Identity;

/**
 * Pins the resolve, decide and publish parts of the decide steps on the
 * container: the lock taken as the first read of the step's one transaction,
 * the refusal of a call made inside a transaction the step did not begin, the
 * rollback of a step failing after a write, the resolve run again once when a
 * date moved or a row is gone, and the side effects published once the
 * transaction has committed. Which row a step locks is pinned by
 * {@code DecideStepsTest}.
 * <p>
 * <b>What this does not verify.</b> The container's HSQLDB runs in MVCC
 * ({@code hsqldb.tx=mvcc}), where a {@code SELECT … FOR UPDATE} does not make
 * a second one wait: two threads materialising one date can end on two rows
 * here, and two answers are not serialised. That the lock serialises the
 * decide steps, that a date materialised by two sessions at once ends on one
 * row, and that the reads after the lock see the latest committed rows under
 * InnoDB's REPEATABLE READ and PostgreSQL's READ COMMITTED, is left to the
 * two-session check on MySQL and PostgreSQL that the delivery records in its
 * pull request.
 */
public class AgendaDecideStepTest extends BaseAgendaEventTest {

  private static final AtomicReference<Consumer<AgendaEventModification>> UPDATE_PROBE = new AtomicReference<>();

  private static boolean                                                  probeRegistered;

  private EntityManagerService                                            entityManagerService;

  /**
   * Registers, once for the container, a listener of event updates that runs
   * the probe a test sets.
   */
  @Before
  public void registerProbe() {
    entityManagerService = container.getComponentInstanceOfType(EntityManagerService.class);
    if (!probeRegistered) {
      listenerService.addListener(Utils.POST_UPDATE_AGENDA_EVENT_EVENT, new Listener<AgendaEventModification, Object>() {
        @Override
        public void onEvent(org.exoplatform.services.listener.Event<AgendaEventModification, Object> event) {
          Consumer<AgendaEventModification> probe = UPDATE_PROBE.get();
          if (probe != null) {
            probe.accept(event.getSource());
          }
        }
      });
      probeRegistered = true;
    }
  }

  /**
   * Leaves no probe and no recording behind.
   */
  @After
  public void clearProbe() {
    UPDATE_PROBE.set(null);
    RecordingStatementInspector.stop();
  }

  /**
   * An answer locks the event row as the first statement of its transaction,
   * the event being served from a warm cache.
   *
   * @throws Exception when the answer fails
   */
  @Test
  public void anAnswerLocksTheEventAsTheFirstReadOfItsTransaction() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    agendaEventAttendeeService.sendEventResponse(series.getId(), id(testuser2Identity), EventAttendeeResponse.ACCEPTED);
    agendaEventStorage.getEventById(series.getId());

    List<RecordedStatement> statements =
                                       record(() -> agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                                                                 id(testuser2Identity),
                                                                                                 EventAttendeeResponse.DECLINED));

    assertTheLockIsTheFirstReadOfTheTransaction(statements);
    assertEquals(EventAttendeeResponse.DECLINED, responseOf(series.getId(), testuser2Identity));
  }

  /**
   * An answer from a date on locks the series as the first statement of its
   * transaction.
   *
   * @throws Exception when the answer fails
   */
  @Test
  public void anUpcomingAnswerLocksTheSeriesAsTheFirstReadOfItsTransaction() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    agendaEventStorage.getEventById(series.getId());

    List<RecordedStatement> statements =
                                       record(() -> agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                                                                         series.getStart()
                                                                                                               .plusDays(1),
                                                                                                         id(testuser2Identity),
                                                                                                         EventAttendeeResponse.ACCEPTED));

    assertTheLockIsTheFirstReadOfTheTransaction(statements);
  }

  /**
   * An answer to one date of a series, whose row it finds or creates, locks
   * the series as the first statement of its transaction, the series being
   * served from a warm cache.
   *
   * @throws Exception when the answer fails
   */
  @Test
  public void anAnswerToOneDateLocksTheSeriesAsTheFirstReadOfItsTransaction() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    agendaEventStorage.getEventById(series.getId());

    List<RecordedStatement> statements =
                                       record(() -> agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                                                                 series.getStart()
                                                                                                       .plusDays(1),
                                                                                                 id(testuser2Identity),
                                                                                                 EventAttendeeResponse.ACCEPTED,
                                                                                                 true));

    assertTheLockIsTheFirstReadOfTheTransaction(statements);
  }

  /**
   * An attendee list saved on its own locks the event as the first statement
   * of its transaction.
   *
   * @throws Exception when the save fails
   */
  @Test
  public void anAttendeeListLocksTheEventAsTheFirstReadOfItsTransaction() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    List<EventAttendee> attendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(series.getId())
                                                                              .getEventAttendees());
    attendees.add(new EventAttendee(0, series.getId(), id(testuser3Identity), EventAttendeeResponse.NEEDS_ACTION));

    List<RecordedStatement> statements = record(() -> agendaEventAttendeeService.saveEventAttendees(series,
                                                                                                    attendees,
                                                                                                    id(testuser1Identity),
                                                                                                    false,
                                                                                                    false,
                                                                                                    null));

    assertTheLockIsTheFirstReadOfTheTransaction(statements);
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(series.getId(), testuser3Identity));
  }

  /**
   * A date materialised from its series locks the series as the first
   * statement of its transaction, and so does a date created with lists of
   * its own.
   *
   * @throws Exception when the materialisation fails
   */
  @Test
  public void aMaterialisationLocksTheSeriesAsTheFirstReadOfItsTransaction() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    agendaEventStorage.getEventById(series.getId());

    List<RecordedStatement> statements =
                                       record(() -> agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                                                      series.getStart()));
    assertTheLockIsTheFirstReadOfTheTransaction(statements);

    statements = record(() -> agendaEventService.createEventExceptionalOccurrence(series.getId(),
                                                                                  Collections.emptyList(),
                                                                                  Collections.emptyList(),
                                                                                  Collections.emptyList(),
                                                                                  series.getStart().plusDays(1)));
    assertTheLockIsTheFirstReadOfTheTransaction(statements);
    assertEquals(2, agendaEventStorage.getExceptionalOccurenceIds(series.getId()).size());
  }

  /**
   * A second answer from the same date of a series replaces the first: the
   * row the first one wrote from that date is deleted, and its replacement,
   * which has the same key, inserted in the same transaction.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aSecondUpcomingAnswerFromTheSameDateReplacesTheFirst() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    ZonedDateTime date = series.getStart().plusDays(1);

    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                         date,
                                                         id(testuser2Identity),
                                                         EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendUpcomingEventResponse(series.getId(),
                                                         date,
                                                         id(testuser2Identity),
                                                         EventAttendeeResponse.DECLINED);

    List<EventAttendeeResponse> fromTheDate = agendaEventAttendeeService.getEventAttendees(series.getId())
                                                                        .getEventAttendees()
                                                                        .stream()
                                                                        .filter(attendee -> attendee.getIdentityId() == id(testuser2Identity)
                                                                            && attendee.getFromOccurrenceId() != null)
                                                                        .map(EventAttendee::getResponse)
                                                                        .toList();
    assertEquals(List.of(EventAttendeeResponse.DECLINED), fromTheDate);
  }

  /**
   * A move of dates locks both series before any other read of its
   * transaction, and reads the dates of a series in one query.
   *
   * @throws Exception when the move fails
   */
  @Test
  public void aMoveLocksBothSeriesFirstAndReadsTheDatesInOneQuery() throws Exception {
    Event first = futureSeries(testuser1Identity, testuser2Identity);
    Event second = futureSeries(testuser1Identity, testuser2Identity);
    for (int day = 0; day < 3; day++) {
      agendaEventService.saveEventExceptionalOccurrence(first.getId(), first.getStart().plusDays(day));
    }

    List<RecordedStatement> statements = record(() -> agendaEventService.moveExceptionalOccurrences(first.getId(),
                                                                                                    second.getId(),
                                                                                                    first.getStart()
                                                                                                         .plusDays(1),
                                                                                                    id(testuser1Identity)));

    List<RecordedStatement> inTransaction = statements.stream().filter(RecordedStatement::inTransaction).toList();
    assertTrue("both series are not locked before any other read of the transaction: " + statements,
               inTransaction.size() > 2 && inTransaction.get(0).isLockingReadOfAnEvent()
                   && inTransaction.get(1).isLockingReadOfAnEvent());
    assertEquals("both series are not locked: " + statements,
                 2,
                 statements.stream().filter(RecordedStatement::isLockingReadOfAnEvent).count());
    assertTrue("the dates of a series were not read in one query: " + statements,
               statements.stream().anyMatch(AgendaDecideStepTest::isDatesOfASeriesRead));
  }

  /**
   * The choice of a poll's date locks the poll as the first statement of its
   * transaction; its update is broadcast once the transaction has committed,
   * outside any decide step, and a listener reading the event back reads the
   * saved state, which the cache then holds.
   *
   * @throws Exception when the choice fails
   */
  @Test
  public void aPollDateChoiceIsBroadcastOnceCommitted() throws Exception {
    Event poll = datePoll();
    EventDateOption dateOption = agendaEventDatePollService.getEventDateOptions(poll.getId(), java.time.ZoneOffset.UTC).get(0);
    agendaEventStorage.getEventById(poll.getId());
    List<String> seenByTheListener = new ArrayList<>();
    UPDATE_PROBE.set(modification -> {
      if (modification.getEventId() == poll.getId()) {
        EntityManager entityManager = entityManagerService.getEntityManager();
        seenByTheListener.add("transaction=" + (entityManager != null && entityManager.getTransaction().isActive())
            + " context=" + (DecideContext.current() != null) + " status="
            + agendaEventService.getEventById(poll.getId()).getStatus());
      }
    });

    List<RecordedStatement> statements = record(() -> agendaEventService.selectEventDateOption(poll.getId(),
                                                                                               dateOption.getId(),
                                                                                               id(testuser1Identity)));

    assertTheLockIsTheFirstReadOfTheTransaction(statements);
    assertEquals(List.of("transaction=false context=false status=CONFIRMED"), seenByTheListener);
    assertEquals(EventStatus.CONFIRMED, agendaEventStorage.getEventById(poll.getId()).getStatus());
  }

  /**
   * A decide step called inside a transaction it did not begin is refused,
   * before anything is written: its lock would not be the first read of that
   * transaction. The transaction is begun by hand, the test classes not being
   * woven.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aStepCalledInsideATransactionItDidNotBeginIsRefused() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    EntityTransaction transaction = entityManagerService.getEntityManager().getTransaction();
    transaction.begin();
    try {
      assertThrows(IllegalStateException.class,
                   () -> agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                                      id(testuser2Identity),
                                                                      EventAttendeeResponse.ACCEPTED));
      assertNull(DecideContext.current());
    } finally {
      transaction.rollback();
    }
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(series.getId(), testuser2Identity));
  }

  /**
   * A decide step whose transaction a failure it caught marked for rollback
   * fails, and publishes none of its side effects: the transaction is rolled
   * back, and its writes with it.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aStepWhoseTransactionIsMarkedForRollbackFails() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    DecideSteps decideSteps = new DecideSteps(agendaEventStorage);
    List<String> published = new ArrayList<>();

    IllegalStateException failure = assertThrows(IllegalStateException.class,
                                                 () -> decideSteps.decide(DecideSteps.targetOf(series), () -> {
                                                   DecideContext.queue(() -> published.add("side effect"));
                                                   entityManagerService.getEntityManager()
                                                                       .getTransaction()
                                                                       .setRollbackOnly();
                                                   return null;
                                                 }));

    assertTrue(failure.getMessage(), failure.getMessage().contains("marked its transaction for rollback"));
    assertTrue("a side effect of a rolled back step was published", published.isEmpty());
    assertNull(DecideContext.current());
  }

  /**
   * A step that fails after a write leaves nothing written, and no context on
   * the thread. A reminder list holding null makes the date's creation fail
   * right after the date's row is inserted.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aStepFailingAfterAWriteLeavesNothingWritten() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);

    assertThrows(NullPointerException.class,
                 () -> agendaEventService.createEventExceptionalOccurrence(series.getId(),
                                                                           null,
                                                                           null,
                                                                           Collections.singletonList(null),
                                                                           series.getStart().plusDays(1)));

    assertNull(DecideContext.current());
    assertTrue("the date's row was committed by a step that failed",
               agendaEventStorage.getExceptionalOccurenceIds(series.getId()).isEmpty());
  }

  /**
   * A date moved to another series between the resolve, which read it from
   * the cache, and the lock is resolved again once: the answer locks the
   * series the date now belongs to, and is written.
   *
   * @throws Exception when the answer fails
   */
  @Test
  public void aDateMovedToAnotherSeriesIsResolvedAgain() throws Exception {
    Event first = futureSeries(testuser1Identity, testuser2Identity);
    Event second = futureSeries(testuser1Identity, testuser2Identity);
    Event date = agendaEventService.saveEventExceptionalOccurrence(first.getId(), first.getStart().plusDays(1));
    assertEquals(first.getId(), agendaEventStorage.getEventById(date.getId()).getParentId());
    inOtherTransaction(entityManager -> entityManager.createNativeQuery("UPDATE EXO_AGENDA_EVENT SET PARENT_EVENT_ID = ?1"
        + " WHERE EVENT_ID = ?2").setParameter(1, second.getId()).setParameter(2, date.getId()).executeUpdate());

    List<RecordedStatement> statements = record(() -> agendaEventAttendeeService.sendEventResponse(date.getId(),
                                                                                                   id(testuser2Identity),
                                                                                                   EventAttendeeResponse.DECLINED));

    assertEquals("the answer was not resolved again once: " + statements,
                 2,
                 statements.stream().filter(RecordedStatement::isLockingReadOfAnEvent).count());
    assertEquals(EventAttendeeResponse.DECLINED, responseOf(date.getId(), testuser2Identity));
  }

  /**
   * An event the cache still serves, but whose row no longer exists, is
   * reported as not found after one locking read, with nothing written: the
   * failed step evicts the stale entry it resolved from, so the resolve that
   * follows reads the row as gone instead of locking it again.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void anEventWhoseRowIsGoneIsReportedAsNotFound() throws Exception {
    Event event = newEventInstance(ZonedDateTime.now().plusDays(60).withNano(0), ZonedDateTime.now().plusDays(60).withNano(0).plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    event.setRecurrence(null);
    event.setOpen(true);
    Event openEvent = createEvent(event, id(testuser1Identity), testuser1Identity);
    assertTrue(Boolean.TRUE.equals(openEvent.getOpen()));
    Event ghost = openEvent.clone();
    ghost.setId(openEvent.getId() + 1_000_000);
    container.getComponentInstanceOfType(CacheService.class)
             .getCacheInstance(CachedAgendaEventStorage.EVENT_CACHE_NAME)
             .put(ghost.getId(), ghost);
    assertNotNull(agendaEventStorage.getEventById(ghost.getId()));

    List<RecordedStatement> statements = new ArrayList<>();
    assertThrows(ObjectNotFoundException.class, () -> {
      RecordingStatementInspector.start();
      try {
        agendaEventAttendeeService.sendEventResponse(ghost.getId(), id(testuser2Identity), EventAttendeeResponse.ACCEPTED);
      } finally {
        statements.addAll(RecordingStatementInspector.stop());
      }
    });

    assertEquals("the failed step did not evict the stale entry: the gone row was locked again: " + statements,
                 1,
                 statements.stream().filter(RecordedStatement::isLockingReadOfAnEvent).count());
    assertNull(agendaEventStorage.getEventById(ghost.getId()));
    assertNull(DecideContext.current());
  }

  /**
   * A move under a series the cache still serves, but whose row no longer
   * exists, is reported as not found, with no date moved.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aMoveUnderASeriesWhoseRowIsGoneIsReportedAsNotFound() throws Exception {
    Event first = futureSeries(testuser1Identity, testuser2Identity);
    Event second = futureSeries(testuser1Identity, testuser2Identity);
    Event date = agendaEventService.saveEventExceptionalOccurrence(first.getId(), first.getStart().plusDays(1));
    Event ghost = second.clone();
    ghost.setId(second.getId() + 1_000_000);
    container.getComponentInstanceOfType(CacheService.class)
             .getCacheInstance(CachedAgendaEventStorage.EVENT_CACHE_NAME)
             .put(ghost.getId(), ghost);

    assertThrows(AgendaException.class,
                 () -> agendaEventService.moveExceptionalOccurrences(first.getId(),
                                                                     ghost.getId(),
                                                                     first.getStart(),
                                                                     id(testuser1Identity)));

    assertEquals(first.getId(), agendaEventStorage.getEventById(date.getId()).getParentId());
  }

  /**
   * A move is checked again on the series as read under the lock: a series
   * that became a date meanwhile, while the cache still served it as a
   * series, is refused before any date moves.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aMoveIsCheckedAgainUnderTheLock() throws Exception {
    Event first = futureSeries(testuser1Identity, testuser2Identity);
    Event second = futureSeries(testuser1Identity, testuser2Identity);
    Event third = futureSeries(testuser1Identity, testuser2Identity);
    Event date = agendaEventService.saveEventExceptionalOccurrence(first.getId(), first.getStart().plusDays(1));
    agendaEventStorage.getEventById(second.getId());
    inOtherTransaction(entityManager -> entityManager.createNativeQuery("UPDATE EXO_AGENDA_EVENT SET PARENT_EVENT_ID = ?1"
        + " WHERE EVENT_ID = ?2").setParameter(1, third.getId()).setParameter(2, second.getId()).executeUpdate());

    assertThrows(IllegalArgumentException.class,
                 () -> agendaEventService.moveExceptionalOccurrences(first.getId(),
                                                                     second.getId(),
                                                                     first.getStart(),
                                                                     id(testuser1Identity)));

    assertEquals(first.getId(), agendaEventStorage.getEventById(date.getId()).getParentId());
  }

  /**
   * The choice of a poll's date is checked again on the poll as read under
   * the lock: a poll whose date was chosen meanwhile, while the cache still
   * served it as a poll, is refused.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aPollDateChoiceIsCheckedAgainUnderTheLock() throws Exception {
    Event poll = datePoll();
    EventDateOption dateOption = agendaEventDatePollService.getEventDateOptions(poll.getId(), java.time.ZoneOffset.UTC).get(0);
    agendaEventStorage.getEventById(poll.getId());
    inOtherTransaction(entityManager -> entityManager.createQuery("UPDATE AgendaEvent ev SET ev.status = :status"
        + " WHERE ev.id = :eventId").setParameter("status", EventStatus.CONFIRMED).setParameter("eventId", poll.getId()).executeUpdate());

    assertThrows(IllegalStateException.class,
                 () -> agendaEventService.selectEventDateOption(poll.getId(), dateOption.getId(), id(testuser1Identity)));

    assertFalse(agendaEventDatePollService.getEventDateOptions(poll.getId(), java.time.ZoneOffset.UTC)
                                          .stream()
                                          .anyMatch(EventDateOption::isSelected));
  }

  /**
   * An invitation sent while a decide step runs is queued, and dispatched only
   * once the step's transaction has ended.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void anInvitationSentDuringADecideStepIsQueued() throws Exception {
    Event series = futureSeries(testuser1Identity, testuser2Identity);
    List<EventAttendee> attendees = agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees();
    AgendaEventModification modification = new AgendaEventModification(series.getId(),
                                                                        series.getCalendarId(),
                                                                        id(testuser1Identity),
                                                                        Collections.singleton(AgendaEventModificationType.ADDED));

    DecideContext context = DecideContext.open();
    try {
      agendaEventAttendeeService.sendInvitations(series, attendees, modification);
      assertEquals(1, context.getEffectCount());
    } finally {
      context.close();
    }
  }

  private static void assertTheLockIsTheFirstReadOfTheTransaction(List<RecordedStatement> statements) {
    RecordedStatement first = statements.stream()
                                        .filter(RecordedStatement::inTransaction)
                                        .findFirst()
                                        .orElseThrow(() -> new AssertionError("no statement ran in a transaction: "
                                            + statements));
    assertTrue("the first statement of the transaction is not a locking read of an event row: " + statements,
               first.isLockingReadOfAnEvent());
  }

  private static boolean isDatesOfASeriesRead(RecordedStatement statement) {
    String sql = statement.sql().toLowerCase();
    return statement.inTransaction() && sql.contains("exo_agenda_recurrence") && sql.contains("occurrence_id is not null");
  }

  /**
   * A daily series of three dates starting in sixty days, in the first test
   * user's calendar: no date of it is materialised by the reminder computing
   * listener when it is created.
   *
   * @param attendees its attendees
   * @return the created series
   * @throws Exception when the creation fails
   */
  private Event futureSeries(Identity... attendees) throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    return createEvent(newEventInstance(start, start.plusHours(1), false), id(testuser1Identity), attendees);
  }

  /**
   * A date poll of the space calendar with two dates, created by the first
   * test user, who invites the second one.
   *
   * @return the created poll
   * @throws Exception when the creation fails
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

  private EventAttendeeResponse responseOf(long eventId, Identity identity) {
    return agendaEventAttendeeService.getEventAttendees(eventId)
                                     .getEventAttendees()
                                     .stream()
                                     .filter(attendee -> attendee.getIdentityId() == id(identity))
                                     .map(EventAttendee::getResponse)
                                     .findFirst()
                                     .orElse(null);
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
   * An action that may throw.
   */
  @FunctionalInterface
  private interface Action {
    void run() throws Exception;
  }
}
