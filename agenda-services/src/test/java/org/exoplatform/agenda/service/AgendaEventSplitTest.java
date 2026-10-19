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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;

import java.lang.reflect.Field;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.Consumer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventRecurrenceType;
import org.exoplatform.agenda.constant.ReminderPeriodType;
import org.exoplatform.agenda.exception.CapacityBelowAcceptedException;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeePromotion;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.model.EventSplit;
import org.exoplatform.agenda.plugin.AgendaGuestUserIdentityProvider;
import org.exoplatform.agenda.service.RecordingStatementInspector.RecordedStatement;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;

/**
 * Pins the split of a recurring event at one of its dates, a "this and
 * upcoming" change (eXIP 7.3.0.21, option B): one transaction that creates
 * the continuation, carries the answers and the waiting list over when no
 * date moves, starts them over when one does, moves the dates customised
 * from the split on and shortens the series.
 */
public class AgendaEventSplitTest extends BaseAgendaEventTest {

  private static final List<AgendaEventModification> CREATIONS  = Collections.synchronizedList(new ArrayList<>());

  private static final List<EventAttendee>           PROMOTIONS = Collections.synchronizedList(new ArrayList<>());

  private static final List<Object>                  REACHED    = Collections.synchronizedList(new ArrayList<>());

  private static boolean                             listenersRegistered;

  /**
   * Registers, once for the container, the listeners recording the creations
   * and the promotions.
   */
  @Before
  public void registerListeners() {
    if (!listenersRegistered) {
      listenerService.addListener(Utils.POST_CREATE_AGENDA_EVENT_EVENT, new Listener<AgendaEventModification, Object>() {
        @Override
        public void onEvent(org.exoplatform.services.listener.Event<AgendaEventModification, Object> event) {
          CREATIONS.add(event.getSource());
        }
      });
      listenerService.addListener(Utils.POST_EVENT_ATTENDEE_PROMOTED, new Listener<EventAttendee, EventAttendeePromotion>() {
        @Override
        public void onEvent(org.exoplatform.services.listener.Event<EventAttendee, EventAttendeePromotion> event) {
          PROMOTIONS.add(event.getData().attendee());
        }
      });
      listenerService.addListener(Utils.POST_EVENT_CAPACITY_REACHED, new Listener<Object, Object>() {
        @Override
        public void onEvent(org.exoplatform.services.listener.Event<Object, Object> event) {
          REACHED.add(event.getSource());
        }
      });
      listenersRegistered = true;
    }
    CREATIONS.clear();
    PROMOTIONS.clear();
    REACHED.clear();
  }

  /**
   * Leaves no recording behind.
   */
  @After
  public void clearRecordings() {
    CREATIONS.clear();
    PROMOTIONS.clear();
    REACHED.clear();
    RecordingStatementInspector.stop();
  }

  /**
   * A space member nobody invited answers Yes from the fifth date on: a split
   * at the third keeps that answer on the continuation, from the fifth date,
   * and the series keeps no row of it. Also when the change lists the member
   * among the participants it adds.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void anAnswerFromALaterDateSurvivesASplitOnTheContinuationOnly() throws Exception {
    for (boolean added : new boolean[] { false, true }) {
      Event series = series(6, 0);
      List<ZonedDateTime> dates = dates(series);
      answerFrom(series.getId(), dates.get(4), testuser2Identity, EventAttendeeResponse.ACCEPTED);

      Event continuation = split(series,
                                 dates.get(2),
                                 testuser1Identity,
                                 edits -> {
                                 },
                                 added ? List.of(testuser2Identity) : List.of(),
                                 List.of(),
                                 false,
                                 false);

      List<EventAttendee> rows = rows(continuation.getId(), testuser2Identity);
      assertEquals("added: " + added, 1, rows.size());
      assertEquals(EventAttendeeResponse.ACCEPTED, rows.get(0).getResponse());
      assertTrue(rows.get(0).getFromOccurrenceId().isEqual(dates.get(4)));
      assertNull(responseAt(continuation.getId(), testuser2Identity, dates.get(2)));
      assertTrue("the series kept a row starting after its end", rows(series.getId(), testuser2Identity).isEmpty());
    }
  }

  /**
   * A change that adds a user picked in the suggester and an email guest
   * puts both on the continuation with no answer.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitAddsAPickedUserAndAnEmailGuestWithNoAnswer() throws Exception {
    Event series = series(4, 0, testuser2Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    Identity guest = identityManager.getOrCreateIdentity(AgendaGuestUserIdentityProvider.NAME, "guest-split@example.com");
    assertNotNull(guest);

    Event continuation = split(series,
                               dates(series).get(1),
                               testuser1Identity,
                               edits -> {
                               },
                               List.of(testuser4Identity, guest),
                               List.of(),
                               false,
                               false);

    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseAt(continuation.getId(), testuser4Identity, null));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseAt(continuation.getId(), guest, null));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));
  }

  /**
   * A split whose last write, the shortening of the series, fails leaves
   * nothing written: no continuation, the series and the customised date it
   * held as they were, in the database and in the cache.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aSplitWhoseShorteningFailsLeavesEverythingAsItWas() throws Exception {
    Event series = series(5, 0, testuser2Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = dates(series);
    agendaEventAttendeeService.sendEventResponse(seriesId, dates.get(3), id(testuser2Identity), EventAttendeeResponse.DECLINED, true);
    long customisedId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(3)).getId();
    // Read once through the cache, which must not keep what the split wrote
    String rule = agendaEventService.getEventById(seriesId).getRecurrence().getRrule();
    assertEquals(seriesId, agendaEventService.getEventById(customisedId).getParentId());
    int seriesRows = agendaEventAttendeeService.getEventAttendees(seriesId).getEventAttendees().size();

    AgendaEventStorage failingStorage = Mockito.spy(agendaEventStorage);
    List<Long> created = new ArrayList<>();
    Mockito.doAnswer(invocation -> {
      Event createdEvent = (Event) invocation.callRealMethod();
      created.add(createdEvent.getId());
      return createdEvent;
    }).when(failingStorage).createEvent(any());
    Mockito.doThrow(new IllegalStateException("the shortening failed"))
           .when(failingStorage)
           .updateEvent(argThat(event -> event != null && event.getId() == seriesId));
    Field storageField = AgendaEventServiceImpl.class.getDeclaredField("agendaEventStorage");
    storageField.setAccessible(true);
    storageField.set(agendaEventService, failingStorage);
    try {
      assertThrows(IllegalStateException.class, () -> split(series, dates.get(2)));
    } finally {
      storageField.set(agendaEventService, agendaEventStorage);
    }

    assertEquals("the continuation was not created", 1, created.size());
    assertNull("the continuation outlived the failure", agendaEventService.getEventById(created.get(0)));
    assertEquals(rule, agendaEventService.getEventById(seriesId).getRecurrence().getRrule());
    assertEquals(seriesId, agendaEventService.getEventById(customisedId).getParentId());
    assertEquals(EventAttendeeResponse.DECLINED, responseAt(customisedId, testuser2Identity, null));
    assertEquals(seriesRows, agendaEventAttendeeService.getEventAttendees(seriesId).getEventAttendees().size());
  }

  /**
   * A split by an editor who is not the series' creator, a manager of the
   * space, keeps the series' creator on the continuation.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitByAnotherEditorKeepsTheSeriesCreator() throws Exception {
    Event series = series(4, 0, testuser2Identity);

    Event continuation = split(series,
                               dates(series).get(1),
                               rootIdentity(),
                               edits -> {
                               },
                               List.of(),
                               List.of(),
                               false,
                               false);

    assertEquals(id(testuser1Identity), agendaEventService.getEventById(continuation.getId()).getCreatorId());
  }

  /**
   * An attendee allowed to modify the series, who may not create an event in
   * its calendar, is refused the split, and nothing is written.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void anAttendeeWhoMayNotCreateEventsInTheCalendarIsRefusedASplit() throws Exception {
    Event event = seriesEvent(4, 0);
    event.setOpen(false);
    event.setAllowAttendeeToUpdate(true);
    Event series = createEvent(event, id(testuser1Identity), testuser4Identity);
    String rule = agendaEventService.getEventById(series.getId()).getRecurrence().getRrule();

    assertThrows(IllegalAccessException.class,
                 () -> split(series,
                             dates(series).get(1),
                             testuser4Identity,
                             edits -> {
                             },
                             List.of(),
                             List.of(),
                             false,
                             false));

    assertEquals(rule, agendaEventService.getEventById(series.getId()).getRecurrence().getRrule());
    assertEquals(1, rows(series.getId(), testuser4Identity).size());
    assertNull(rows(series.getId(), testuser4Identity).get(0).getUntilOccurrenceId());
  }

  /**
   * A member of the space who may create an event in the series' calendar,
   * but may not update the series (neither its creator, nor an attendee
   * allowed to modify it, nor a manager of the space), is refused the split,
   * and nothing is written.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aMemberWhoMayCreateEventsButNotUpdateTheSeriesIsRefusedASplit() throws Exception {
    Event series = series(4, 0, testuser3Identity);
    Event stored = agendaEventService.getEventById(series.getId());
    String rule = stored.getRecurrence().getRrule();
    assertTrue(agendaEventService.canCreateEvent(spaceCalendar, id(testuser2Identity)));
    assertFalse(agendaEventService.canUpdateEvent(stored, id(testuser2Identity)));

    assertThrows(IllegalAccessException.class,
                 () -> split(series,
                             dates(series).get(1),
                             testuser2Identity,
                             edits -> {
                             },
                             List.of(),
                             List.of(),
                             false,
                             false));

    assertEquals(rule, agendaEventService.getEventById(series.getId()).getRecurrence().getRrule());
    assertEquals(1, rows(series.getId(), testuser3Identity).size());
    assertNull(rows(series.getId(), testuser3Identity).get(0).getUntilOccurrenceId());
  }

  /**
   * A change naming another calendar leaves the continuation in the series'.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitKeepsTheSeriesCalendarWhateverTheChangeNames() throws Exception {
    Event series = series(4, 0);

    Event continuation = split(series,
                               dates(series).get(1),
                               testuser1Identity,
                               edits -> edits.setCalendarId(calendar.getId()),
                               List.of(),
                               List.of(),
                               false,
                               false);

    assertEquals(spaceCalendar.getId(), agendaEventService.getEventById(continuation.getId()).getCalendarId());
  }

  /**
   * An editor who is not the creator opens, through a split, a series that
   * lets its attendees invite: the continuation is open, the permission
   * lowered as the padlock asks, never kept.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void anEditorWhoIsNotTheCreatorOpensTheContinuationWithoutTheInvitePermission() throws Exception {
    Event event = seriesEvent(4, 0);
    event.setOpen(false);
    event.setAllowAttendeeToInvite(true);
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    assertTrue(agendaEventService.getEventById(series.getId()).isAllowAttendeeToInvite());

    Event continuation = split(series, dates(series).get(1), rootIdentity(), edits -> {
      edits.setOpen(true);
      edits.setAllowAttendeeToInvite(false);
    }, List.of(), List.of(), false, false);

    Event stored = agendaEventService.getEventById(continuation.getId());
    assertEquals(Boolean.TRUE, stored.getOpen());
    assertFalse(stored.isAllowAttendeeToInvite());
  }

  /**
   * A series with a count, at 08:00 in a zone east of UTC, keeps its number
   * of dates across the two series, and is ended by a rule that states its
   * last day.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesWithACountKeepsItsNumberOfDatesAcrossTheSplit() throws Exception {
    ZoneId brisbane = ZoneId.of("Australia/Brisbane");
    LocalDate firstDay = LocalDate.now(brisbane).plusDays(60);
    Event event = seriesEvent(5, 0);
    event.setTimeZoneId(brisbane);
    event.setStart(firstDay.atTime(8, 0).atZone(brisbane));
    event.setEnd(firstDay.atTime(9, 0).atZone(brisbane));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    List<ZonedDateTime> dates = dates(series);
    assertEquals(5, dates.size());

    Event continuation = split(series, dates.get(2));

    assertEquals(2, dates(series).size());
    assertEquals(3, dates(continuation).size());
    assertEquals(3, agendaEventService.getEventById(continuation.getId()).getRecurrence().getCount());
    String rule = agendaEventService.getEventById(series.getId()).getRecurrence().getRrule();
    assertFalse(rule, rule.contains("COUNT="));
    assertTrue(rule, rule.contains("UNTIL=" + untilOf(firstDay.plusDays(1), brisbane)));
  }

  /**
   * A split that moves no date keeps the waiting list as it was: the place,
   * and the date it was asked at, which orders it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitThatMovesNoDateKeepsTheWaitingListInItsOrder() throws Exception {
    Event series = series(4, 1, testuser2Identity, testuser3Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerAll(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    EventAttendee waiting = rows(series.getId(), testuser3Identity).get(0);
    assertEquals(EventAttendeeResponse.WAITING, waiting.getResponse());

    Event continuation = split(series, dates(series).get(1));

    List<EventAttendee> rows = rows(continuation.getId(), testuser3Identity);
    assertEquals(1, rows.size());
    assertEquals(EventAttendeeResponse.WAITING, rows.get(0).getResponse());
    assertTrue(rows.get(0).getResponseDate().isEqual(waiting.getResponseDate()));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));
    assertTrue(PROMOTIONS.isEmpty());
  }

  /**
   * The continuation takes the seats a higher limit gives: the waiting list
   * is promoted, from the split on.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void theWaitingListTakesTheSeatsAHigherLimitGivesOnTheContinuation() throws Exception {
    Event series = series(4, 1, testuser2Identity, testuser3Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerAll(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);

    Event continuation = split(series, dates.get(1), testuser1Identity, edits -> edits.setCapacity(2), List.of(), List.of(), false, false);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser3Identity, dates.get(1)));
    assertEquals(EventAttendeeResponse.WAITING, responseAt(series.getId(), testuser3Identity, dates.get(0)));
    assertEquals(List.of(id(testuser3Identity)), PROMOTIONS.stream().map(EventAttendee::getIdentityId).toList());
  }

  /**
   * A change that removes a participant holding a seat gives the seat to the
   * waiting list, before it is decided.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitRemovingAYesGivesItsSeatToTheWaitingList() throws Exception {
    Event series = series(4, 1, testuser2Identity, testuser3Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerAll(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    Event continuation = split(series,
                               dates(series).get(1),
                               testuser1Identity,
                               edits -> {
                               },
                               List.of(),
                               List.of(testuser2Identity),
                               false,
                               false);

    assertTrue(rows(continuation.getId(), testuser2Identity).isEmpty());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser3Identity, null));
  }

  /**
   * A limit below the Yes answers the continuation keeps is refused, naming
   * the first date it is below, and nothing is written.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aLimitBelowTheYesAnswersKeptIsRefusedWithItsFirstDate() throws Exception {
    Event series = series(4, 2, testuser2Identity, testuser3Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerAll(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);
    String rule = agendaEventService.getEventById(series.getId()).getRecurrence().getRrule();

    CapacityBelowAcceptedException refusal = assertThrows(CapacityBelowAcceptedException.class,
                                                          () -> split(series,
                                                                      dates.get(1),
                                                                      testuser1Identity,
                                                                      edits -> edits.setCapacity(1),
                                                                      List.of(),
                                                                      List.of(),
                                                                      false,
                                                                      false));

    assertEquals("agenda.capacity.belowAccepted", refusal.getMessage());
    assertEquals(dates.get(1).toLocalDate(), refusal.getDate());
    assertEquals(rule, agendaEventService.getEventById(series.getId()).getRecurrence().getRrule());
  }

  /**
   * A split that closes the event while people wait for a seat is refused
   * unless confirmed; confirmed, the continuation is closed with no limit,
   * and the people waiting take a seat.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitThatClosesTheEventWhilePeopleWaitNeedsAConfirmation() throws Exception {
    Event series = series(4, 1, testuser2Identity, testuser3Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerAll(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);
    Consumer<Event> close = edits -> edits.setOpen(false);

    IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                    () -> split(series,
                                                                dates.get(1),
                                                                testuser1Identity,
                                                                close,
                                                                List.of(),
                                                                List.of(),
                                                                false,
                                                                false));
    assertEquals("agenda.capacity.closeNeedsConfirmation", refusal.getMessage());

    Event continuation = split(series, dates.get(1), testuser1Identity, close, List.of(), List.of(), true, false);

    Event stored = agendaEventService.getEventById(continuation.getId());
    assertEquals(Boolean.FALSE, stored.getOpen());
    assertEquals(0, stored.getCapacity() == null ? 0 : stored.getCapacity().intValue());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser3Identity, null));
  }

  /**
   * A split that closes the event while nobody waits needs no confirmation.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitThatClosesTheEventWhileNobodyWaitsNeedsNoConfirmation() throws Exception {
    Event series = series(4, 2, testuser2Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    Event continuation = split(series,
                               dates(series).get(1),
                               testuser1Identity,
                               edits -> edits.setOpen(false),
                               List.of(),
                               List.of(),
                               false,
                               false);

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(continuation.getId()).getOpen());
  }

  /**
   * A split that moves the dates starts every answer but the user's over,
   * the waiting list included; the user's Yes takes a seat.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitThatMovesADateStartsEveryAnswerButTheUsersOver() throws Exception {
    Event series = series(4, 2, testuser1Identity, testuser2Identity, testuser3Identity);
    answerAll(series.getId(), testuser1Identity, EventAttendeeResponse.ACCEPTED);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerAll(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseAt(series.getId(), testuser3Identity, null));
    ZonedDateTime splitFrom = dates(series).get(1);

    Event continuation = split(series, splitFrom, testuser1Identity, edits -> {
      edits.setStart(splitFrom.plusHours(1));
      edits.setEnd(splitFrom.plusHours(2));
    }, List.of(), List.of(), false, false);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser1Identity, null));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseAt(continuation.getId(), testuser2Identity, null));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseAt(continuation.getId(), testuser3Identity, null));
  }

  /**
   * A recurrence pattern given with its lists in another order moves no date:
   * the answers are carried over.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aRecurrencePatternListedInAnotherOrderMovesNoDate() throws Exception {
    Event event = seriesEvent(6, 0);
    event.getRecurrence().setType(EventRecurrenceType.WEEKLY);
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
    event.getRecurrence().setByDay(List.of("MO", "TU", "WE", "TH", "FR", "SA", "SU"));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    Event continuation = split(series, dates(series).get(2), testuser1Identity, edits -> {
      EventRecurrence recurrence = edits.getRecurrence().clone();
      recurrence.setByDay(List.of("SU", "SA", "FR", "TH", "WE", "TU", "MO"));
      edits.setRecurrence(recurrence);
    }, List.of(), List.of(), false, false);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));
  }

  /**
   * The rows of the series end at the split date: one covering it ends
   * there, one starting after it goes.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void theSeriesRowsEndAtTheSplitDate() throws Exception {
    Event series = series(6, 0, testuser2Identity);
    List<ZonedDateTime> dates = dates(series);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerFrom(series.getId(), dates.get(4), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    split(series, dates.get(2));

    List<EventAttendee> rows = rows(series.getId(), testuser2Identity);
    assertEquals(1, rows.size());
    assertTrue(rows.get(0).getUntilOccurrenceId().isEqual(dates.get(2)));
    assertTrue(rows(series.getId(), testuser3Identity).isEmpty());
  }

  /**
   * Only the part of an attendee's rows from the split date on is carried
   * over: a row ending before it stays with the series.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void onlyThePartOfTheRowsFromTheSplitOnIsCarriedOver() throws Exception {
    Event series = series(6, 0, testuser2Identity);
    List<ZonedDateTime> dates = dates(series);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerFrom(series.getId(), dates.get(1), testuser2Identity, EventAttendeeResponse.DECLINED);
    assertEquals(2, rows(series.getId(), testuser2Identity).size());

    Event continuation = split(series, dates.get(3));

    List<EventAttendee> rows = rows(continuation.getId(), testuser2Identity);
    assertEquals(1, rows.size());
    assertEquals(EventAttendeeResponse.DECLINED, rows.get(0).getResponse());
    assertNull(rows.get(0).getFromOccurrenceId());
  }

  /**
   * The attendees carried over keep their reminders on the continuation;
   * when a date moves, only the user keeps theirs.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void theRemindersOfTheAttendeesCarriedOverAreCopied() throws Exception {
    for (boolean movesADate : new boolean[] { false, true }) {
      Event series = series(4, 0, testuser2Identity);
      answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
      agendaEventReminderService.saveEventReminders(agendaEventService.getEventById(series.getId()),
                                                    List.of(new EventReminder(0, 0, 0, 2, ReminderPeriodType.HOUR, null)),
                                                    id(testuser2Identity));
      ZonedDateTime splitFrom = dates(series).get(1);

      Event continuation = split(series, splitFrom, testuser1Identity, edits -> {
        if (movesADate) {
          edits.setStart(splitFrom.plusHours(1));
          edits.setEnd(splitFrom.plusHours(2));
        }
      }, List.of(), List.of(), false, false);

      List<EventReminder> reminders = agendaEventReminderService.getEventReminders(continuation.getId(), id(testuser2Identity));
      assertEquals("moves a date: " + movesADate, movesADate ? 0 : 1, reminders.size());
      assertFalse(agendaEventReminderService.getEventReminders(continuation.getId(), id(testuser1Identity)).isEmpty());
    }
  }

  /**
   * The continuation is published as created by a split, and the answers it
   * carries over survive the listeners of the creation.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void theContinuationIsPublishedAsASplitAndKeepsItsAnswers() throws Exception {
    Event series = series(4, 0, testuser2Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    CREATIONS.clear();

    Event continuation = split(series, dates(series).get(1));

    assertEquals(1, CREATIONS.size());
    assertEquals(continuation.getId(), CREATIONS.get(0).getEventId());
    assertTrue(CREATIONS.get(0).hasModification(AgendaEventModificationType.ADDED));
    assertTrue(CREATIONS.get(0).hasModification(AgendaEventModificationType.SPLIT));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));
  }

  /**
   * A split that moves no date tells every participant once that the event
   * changed, asking nobody to answer again; one that moves a date invites
   * them to the new dates.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitTellsTheParticipantsTheEventChangedOrInvitesThemWhenADateMoves() throws Exception {
    Field attendeeServiceField = AgendaEventServiceImpl.class.getDeclaredField("attendeeService");
    attendeeServiceField.setAccessible(true);
    AgendaEventAttendeeServiceImpl spiedAttendeeService = Mockito.spy((AgendaEventAttendeeServiceImpl) agendaEventAttendeeService);
    attendeeServiceField.set(agendaEventService, spiedAttendeeService);
    try {
      Event series = series(6, 0, testuser2Identity);
      List<ZonedDateTime> dates = dates(series);
      answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
      answerFrom(series.getId(), dates.get(4), testuser2Identity, EventAttendeeResponse.TENTATIVE);

      split(series, dates.get(2), testuser1Identity, edits -> {
      }, List.of(testuser4Identity), List.of(), false, true);

      Mockito.verify(spiedAttendeeService)
             .sendInvitations(any(),
                              argThat(attendees -> attendees.stream().filter(row -> row.getIdentityId() == id(testuser2Identity)).count() == 1
                                  && attendees.stream().anyMatch(row -> row.getIdentityId() == id(testuser4Identity))),
                              argThat(modification -> modification.hasModification(AgendaEventModificationType.UPDATED)
                                  && modification.hasModification(AgendaEventModificationType.SPLIT)
                                  && !modification.hasModification(AgendaEventModificationType.ADDED)));
      Mockito.clearInvocations(spiedAttendeeService);

      Event moved = series(4, 0, testuser2Identity);
      ZonedDateTime splitFrom = dates(moved).get(1);
      split(moved, splitFrom, testuser1Identity, edits -> {
        edits.setStart(splitFrom.plusHours(1));
        edits.setEnd(splitFrom.plusHours(2));
      }, List.of(), List.of(), false, true);

      Mockito.verify(spiedAttendeeService)
             .sendInvitations(any(),
                              any(),
                              argThat(modification -> modification.hasModification(AgendaEventModificationType.ADDED)
                                  && modification.hasModification(AgendaEventModificationType.SPLIT)));
      Mockito.verify(spiedAttendeeService, Mockito.never())
             .sendInvitations(any(), any(), argThat(modification -> modification.hasModification(AgendaEventModificationType.UPDATED)));
    } finally {
      attendeeServiceField.set(agendaEventService, agendaEventAttendeeService);
    }
  }

  /**
   * A split from the first date of the series, which a change of the whole
   * series covers, or from a day that is none of its dates, is refused.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aSplitFromTheFirstDateOrFromNoDateOfTheSeriesIsRefused() throws Exception {
    Event series = series(4, 0);
    List<ZonedDateTime> dates = dates(series);

    for (ZonedDateTime splitFrom : List.of(dates.get(0), dates.get(3).plusDays(1), dates.get(0).minusDays(1))) {
      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> split(series, splitFrom));
      assertEquals("agenda.seriesSplit.notAllowed", refusal.getMessage());
    }
  }

  /**
   * An all-day series west of UTC is split on its own day: the dates do not
   * move, the answers are carried over, and the series ends on the day
   * before, in its zone.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void anAllDaySeriesWestOfUtcIsSplitOnItsOwnDay() throws Exception {
    ZoneId newYork = ZoneId.of("America/New_York");
    LocalDate firstDay = LocalDate.now(newYork).plusDays(60);
    Event event = seriesEvent(5, 0);
    event.setAllDay(true);
    event.setTimeZoneId(newYork);
    event.setStart(firstDay.atStartOfDay(newYork));
    event.setEnd(firstDay.atTime(23, 59, 59).atZone(newYork));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);
    assertEquals(firstDay.plusDays(2), dates.get(2).toLocalDate());

    Event continuation = split(series, dates.get(2));

    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));
    assertEquals(firstDay.plusDays(2), dates(continuation).get(0).toLocalDate());
    String rule = agendaEventService.getEventById(series.getId()).getRecurrence().getRrule();
    assertTrue(rule, rule.contains("UNTIL=" + untilOf(firstDay.plusDays(1), newYork)));
  }

  /**
   * A split locks the series as the first read of its transaction.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitLocksTheSeriesAsTheFirstReadOfItsTransaction() throws Exception {
    Event series = series(4, 0, testuser2Identity);
    ZonedDateTime splitFrom = dates(series).get(1);

    List<RecordedStatement> statements = record(() -> split(series, splitFrom));

    List<RecordedStatement> inTransaction = statements.stream().filter(RecordedStatement::inTransaction).toList();
    assertTrue("the series is not locked before any other read of the transaction: " + statements,
               !inTransaction.isEmpty() && inTransaction.get(0).isLockingReadOfAnEvent());
  }

  /**
   * A date customised after the split date moves under the continuation with
   * its own answers.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aCustomisedDateMovesUnderTheContinuationWithItsOwnAnswers() throws Exception {
    Event series = series(5, 0, testuser2Identity);
    List<ZonedDateTime> dates = dates(series);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendEventResponse(series.getId(),
                                                 dates.get(3),
                                                 id(testuser2Identity),
                                                 EventAttendeeResponse.DECLINED,
                                                 true);
    long customisedId = agendaEventService.getExceptionalOccurrenceEvent(series.getId(), dates.get(3)).getId();

    Event continuation = split(series, dates.get(1));

    assertEquals(continuation.getId(), agendaEventService.getEventById(customisedId).getParentId());
    assertEquals(EventAttendeeResponse.DECLINED, responseAt(customisedId, testuser2Identity, null));
    assertNotEquals(EventAttendeeResponse.DECLINED, responseAt(continuation.getId(), testuser2Identity, dates.get(2)));
  }

  /**
   * The continuation keeps the series' reached mark when it keeps its dates
   * and its limit, so that a date already full is not reported reached
   * again; a new limit, or new dates, start with none.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void theContinuationKeepsTheReachedMarkUnderTheSameLimitAndDatesOnly() throws Exception {
    ZonedDateTime reached = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1).withNano(0);
    List<Consumer<Event>> changes = List.of(edits -> {
    }, edits -> edits.setCapacity(2), edits -> {
      edits.setStart(edits.getStart().plusHours(1));
      edits.setEnd(edits.getEnd().plusHours(1));
    });
    for (int i = 0; i < changes.size(); i++) {
      Event series = series(4, 1, testuser2Identity);
      saveCapacityReachedDate(series.getId(), reached);

      Event continuation = split(series, dates(series).get(1), testuser1Identity, changes.get(i), List.of(), List.of(), false, false);

      ZonedDateTime mark = agendaEventService.getEventById(continuation.getId()).getCapacityReachedDate();
      if (i == 0) {
        assertNotNull(mark);
        assertTrue(mark.isEqual(reached));
      } else {
        assertNull("change " + i, mark);
      }
    }
  }

  /**
   * The continuation under a new limit over the same dates is marked from
   * the answers carried over, once its waiting list took the seats the
   * limit gives, reporting nothing: a limit raised to those seats leaves it
   * full and marked, so that a Yes given again reports nothing (D7).
   *
   * @throws Exception when a write fails
   */
  @Test
  public void theContinuationUnderANewLimitIsMarkedFromTheAnswersCarriedOver() throws Exception {
    Event series = series(4, 1, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = dates(series);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerAll(series.getId(), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseAt(series.getId(), testuser3Identity, dates.get(1)));
    REACHED.clear();

    Event continuation = split(series, dates.get(1), testuser1Identity, edits -> edits.setCapacity(2), List.of(), List.of(), false, false);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser3Identity, dates.get(1)));
    assertNotNull(agendaEventService.getEventById(continuation.getId()).getCapacityReachedDate());
    assertTrue(REACHED.isEmpty());

    answerAll(continuation.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(REACHED.isEmpty());
  }

  /**
   * A date customised before the split date stays with the series: people
   * waiting on it do not make a split that closes the continuation ask for a
   * confirmation.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateCustomisedBeforeTheSplitDoesNotCountInTheClosingGuard() throws Exception {
    Event series = series(4, 1, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = dates(series);
    agendaEventAttendeeService.sendEventResponse(series.getId(), dates.get(0), id(testuser2Identity), EventAttendeeResponse.ACCEPTED, true);
    agendaEventAttendeeService.sendEventResponse(series.getId(), dates.get(0), id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);
    long firstDateId = agendaEventService.getExceptionalOccurrenceEvent(series.getId(), dates.get(0)).getId();
    assertEquals(EventAttendeeResponse.WAITING, responseAt(firstDateId, testuser3Identity, null));

    Event continuation = split(series,
                               dates.get(2),
                               testuser1Identity,
                               edits -> edits.setOpen(false),
                               List.of(),
                               List.of(),
                               false,
                               false);

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(continuation.getId()).getOpen());
    assertEquals(EventAttendeeResponse.WAITING, responseAt(firstDateId, testuser3Identity, null));
  }

  /**
   * A change that leaves the zone to the series, its dates written in another
   * zone, that names the series' zone another way, or that names the zone the
   * series was read in, moves no date: the answers are carried over. One that
   * names another zone, whose daylight saving shifts other dates, starts them
   * over.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitKeepsTheAnswersUnlessItNamesAnotherZone() throws Exception {
    ZoneId newYork = ZoneId.of("America/New_York");
    Consumer<Event> inNewYork = edits -> {
      edits.setTimeZoneId(newYork);
      edits.setStart(edits.getStart().withZoneSameInstant(newYork));
      edits.setEnd(edits.getEnd().withZoneSameInstant(newYork));
    };
    List<Consumer<Event>> changes = List.of(edits -> {
      inNewYork.accept(edits);
      edits.setTimeZoneId(null);
    }, edits -> edits.setTimeZoneId(ZoneId.of("UTC")), inNewYork, inNewYork);
    List<ZoneId> readZones = Arrays.asList(null, null, newYork, null);
    for (int i = 0; i < changes.size(); i++) {
      Event series = series(4, 0, testuser2Identity);
      answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);

      Event continuation = split(series,
                                 dates(series).get(1),
                                 testuser1Identity,
                                 changes.get(i),
                                 List.of(),
                                 List.of(),
                                 false,
                                 false,
                                 readZones.get(i));

      assertEquals("change " + i,
                   i < 3 ? EventAttendeeResponse.ACCEPTED : EventAttendeeResponse.NEEDS_ACTION,
                   responseAt(continuation.getId(), testuser2Identity, null));
      if (i == 0 || i == 2) {
        assertEquals("change " + i, ZoneOffset.UTC, agendaEventService.getEventById(continuation.getId()).getTimeZoneId());
      }
    }
  }

  /**
   * A rule written in the zone the series was read in names its weekday as
   * the first date falls there: a series every Monday at 17:30 in Paris,
   * read in Tokyo, comes back every Tuesday. Its weekday is moved back onto
   * the series' own, so that a change of its count moves no date and keeps
   * the answers.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aWeeklyRuleWrittenInTheZoneTheSeriesWasReadInNamesTheSeriesWeekday() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    Event series = weeklySeriesAt1730InParis(List.of("MO"));
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);

    Event continuation = split(series, dates.get(2), testuser1Identity, edits -> {
      inZone(edits, tokyo);
      edits.getRecurrence().setByDay(new ArrayList<>(List.of("TU")));
      edits.getRecurrence().setCount(3);
    }, List.of(), List.of(), false, false, tokyo);

    Event stored = agendaEventService.getEventById(continuation.getId());
    assertEquals(ZoneId.of("Europe/Paris"), stored.getTimeZoneId());
    assertEquals(List.of("MO"), stored.getRecurrence().getByDay());
    assertEquals(dates.subList(2, 5).stream().map(ZonedDateTime::toInstant).toList(),
                 dates(continuation).stream().map(ZonedDateTime::toInstant).toList());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));
  }

  /**
   * A rule on several weekdays, sent as the series stores it, as the custom
   * repetition drawer does, names them in the series' zone already: read in
   * Tokyo, a series every Monday and Tuesday at 17:30 in Paris keeps them,
   * whichever of them the first date falls on in Tokyo, and a change of its
   * count moves no date.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aRuleOnSeveralWeekdaysAsStoredIsKeptWhicheverZoneItWasReadIn() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    Event series = weeklySeriesAt1730InParis(List.of("MO", "TU"));
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);

    Event continuation = split(series, dates.get(2), testuser1Identity, edits -> {
      inZone(edits, tokyo);
      edits.getRecurrence().setCount(3);
    }, List.of(), List.of(), false, false, tokyo);

    assertEquals(List.of("MO", "TU"), agendaEventService.getEventById(continuation.getId()).getRecurrence().getByDay());
    assertEquals(dates.subList(2, 5).stream().map(ZonedDateTime::toInstant).toList(),
                 dates(continuation).stream().map(ZonedDateTime::toInstant).toList());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));

    // The custom drawer shows the stored weekdays: one added there is named
    // in the series' zone too
    Event other = weeklySeriesAt1730InParis(List.of("MO", "TU"));
    Event customised = split(other, dates(other).get(2), testuser1Identity, edits -> {
      inZone(edits, tokyo);
      edits.getRecurrence().setType(EventRecurrenceType.CUSTOM);
      edits.getRecurrence().setByDay(new ArrayList<>(List.of("MO", "TU", "WE")));
    }, List.of(), List.of(), false, false, tokyo);

    assertEquals(List.of("MO", "TU", "WE"), agendaEventService.getEventById(customised.getId()).getRecurrence().getByDay());
  }

  /**
   * A list of weekdays names the event's own, whichever zone it was chosen
   * in: "working week days" chosen in Tokyo for a series at 17:30 in Paris
   * is Monday to Friday in Paris, as its label says to whoever reads it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aWeekdaysPresetChosenInAnotherZoneNamesTheEventsWeekdays() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    Event series = weeklySeriesAt1730InParis(List.of("MO"));
    List<ZonedDateTime> dates = dates(series);

    Event continuation = split(series, dates.get(2), testuser1Identity, edits -> {
      inZone(edits, tokyo);
      edits.getRecurrence().setType(EventRecurrenceType.WEEK_DAYS);
      edits.getRecurrence().setByDay(new ArrayList<>(List.of("MO", "TU", "WE", "TH", "FR")));
      edits.getRecurrence().setCount(7);
    }, List.of(), List.of(), false, false, tokyo);

    assertEquals(List.of("MO", "TU", "WE", "TH", "FR"),
                 agendaEventService.getEventById(continuation.getId()).getRecurrence().getByDay());
    assertEquals(7, dates(continuation).size());
    assertTrue(dates(continuation).stream()
                                  .map(date -> date.withZoneSameInstant(ZoneId.of("Europe/Paris")).getDayOfWeek())
                                  .allMatch(day -> day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY));
  }

  /**
   * A monthly rule written in the zone the series was read in names its day
   * as the first date falls there: a series on the 10th at 17:30 in Paris,
   * read in Tokyo, comes back on the 11th, and is moved back onto the 10th. A day
   * that is not the first date's in that zone is the user's, and is kept.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aMonthlyRuleWrittenInTheZoneTheSeriesWasReadInNamesTheSeriesDay() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZoneId paris = ZoneId.of("Europe/Paris");
    LocalDate tenth = LocalDate.now(paris).plusMonths(2).withDayOfMonth(10);
    for (String writtenDay : List.of("11", "20", "11 with no zone")) {
      Event event = seriesEvent(4, 0);
      event.setTimeZoneId(paris);
      event.setStart(tenth.atTime(17, 30).atZone(paris));
      event.setEnd(event.getStart().plusMinutes(30));
      event.getRecurrence().setFrequency(EventRecurrenceFrequency.MONTHLY);
      event.getRecurrence().setByMonthDay(new ArrayList<>(List.of("10")));
      Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
      ZonedDateTime secondDate = tenth.plusMonths(1).atTime(17, 30).atZone(paris);

      Event continuation = split(series, secondDate, testuser1Identity, edits -> {
        inZone(edits, tokyo);
        if (writtenDay.endsWith("no zone")) {
          edits.setTimeZoneId(null);
        }
        edits.getRecurrence().setByMonthDay(new ArrayList<>(List.of(writtenDay.substring(0, 2))));
      }, List.of(), List.of(), false, false, tokyo);

      assertEquals(writtenDay,
                   List.of(writtenDay.startsWith("11") ? "10" : writtenDay),
                   agendaEventService.getEventById(continuation.getId()).getRecurrence().getByMonthDay());
    }
  }

  /**
   * A series ending on a day, in a zone behind the server's, is continued up
   * to that day when the change keeps its end, whether it leaves the
   * recurrence to the series or gives it back as read.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aContinuationKeepingTheSeriesEndEndsOnItsLastDayBehindTheServersZone() throws Exception {
    ZoneId newYork = ZoneId.of("America/New_York");
    LocalDate firstDay = LocalDate.now(newYork).plusDays(60);
    LocalDate lastDay = firstDay.plusDays(5);
    List<Consumer<Event>> changes = List.of(edits -> edits.setRecurrence(null), edits -> {
    });
    for (int i = 0; i < changes.size(); i++) {
      Event event = seriesEvent(0, 0);
      event.setTimeZoneId(newYork);
      event.setStart(firstDay.atTime(9, 0).atZone(newYork));
      event.setEnd(firstDay.atTime(10, 0).atZone(newYork));
      event.getRecurrence().setUntil(lastDay);
      Event series = createEvent(event, id(testuser1Identity), testuser2Identity);

      Event continuation = split(series, dates(series).get(2), testuser1Identity, changes.get(i), List.of(), List.of(), false, false);

      String rule = agendaEventService.getEventById(continuation.getId()).getRecurrence().getRrule();
      assertTrue("change " + i + ": " + rule, rule.contains("UNTIL=" + untilOf(lastDay, newYork)));
    }
  }

  /**
   * A date customised from the split date on, where people wait, is closed by
   * its merge into a continuation that closes the event, or that grants a
   * permission an open event cannot carry: the split asks for a confirmation,
   * and, confirmed, the people waiting on that date take a seat.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSplitClosingAMovedDateWherePeopleWaitNeedsAConfirmation() throws Exception {
    List<Consumer<Event>> closings = List.of(edits -> edits.setOpen(false), edits -> edits.setAllowAttendeeToUpdate(true));
    for (int i = 0; i < closings.size(); i++) {
      Consumer<Event> closing = closings.get(i);
      Event series = series(4, 1, testuser2Identity, testuser3Identity);
      List<ZonedDateTime> dates = dates(series);
      agendaEventAttendeeService.sendEventResponse(series.getId(), dates.get(3), id(testuser2Identity), EventAttendeeResponse.ACCEPTED, true);
      agendaEventAttendeeService.sendEventResponse(series.getId(), dates.get(3), id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);
      long movedId = agendaEventService.getExceptionalOccurrenceEvent(series.getId(), dates.get(3)).getId();
      assertEquals(EventAttendeeResponse.WAITING, responseAt(movedId, testuser3Identity, null));

      IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                                                      () -> split(series,
                                                                  dates.get(1),
                                                                  testuser1Identity,
                                                                  closing,
                                                                  List.of(),
                                                                  List.of(),
                                                                  false,
                                                                  false));
      assertEquals("closing " + i, "agenda.capacity.closeNeedsConfirmation", refusal.getMessage());

      Event continuation = split(series, dates.get(1), testuser1Identity, closing, List.of(), List.of(), true, false);

      assertEquals(continuation.getId(), agendaEventService.getEventById(movedId).getParentId());
      assertEquals("closing " + i, EventAttendeeResponse.ACCEPTED, responseAt(movedId, testuser3Identity, null));
    }
  }

  /**
   * A limit a date customised from the split date on gets from its merge,
   * below the Yes answers of that date, is refused with that date, and
   * nothing is written.
   *
   * @throws Exception when the fixture fails
   */
  @Test
  public void aLimitBelowTheYesAnswersOfAMovedDateIsRefusedWithThatDate() throws Exception {
    Event series = series(4, 2, testuser2Identity, testuser3Identity);
    List<ZonedDateTime> dates = dates(series);
    agendaEventAttendeeService.sendEventResponse(series.getId(), dates.get(3), id(testuser2Identity), EventAttendeeResponse.ACCEPTED, true);
    agendaEventAttendeeService.sendEventResponse(series.getId(), dates.get(3), id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);
    String rule = agendaEventService.getEventById(series.getId()).getRecurrence().getRrule();

    CapacityBelowAcceptedException refusal = assertThrows(CapacityBelowAcceptedException.class,
                                                          () -> split(series,
                                                                      dates.get(1),
                                                                      testuser1Identity,
                                                                      edits -> edits.setCapacity(1),
                                                                      List.of(),
                                                                      List.of(),
                                                                      false,
                                                                      false));

    assertEquals(dates.get(3).toLocalDate(), refusal.getDate());
    assertEquals(rule, agendaEventService.getEventById(series.getId()).getRecurrence().getRrule());
  }

  /**
   * An all-day series east or west of UTC is split on the day its date id
   * names, whether the id is the UTC midnight it is written as, or the local
   * midnight of the series' zone converted to UTC as the REST parser delivers
   * it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void anAllDaySeriesIsSplitOnTheDayItsDateIdNamesEastAndWestOfUtc() throws Exception {
    for (ZoneId zone : List.of(ZoneId.of("Australia/Brisbane"), ZoneId.of("America/New_York"))) {
      LocalDate firstDay = LocalDate.now(zone).plusDays(60);
      LocalDate splitDay = firstDay.plusDays(2);
      for (boolean localMidnight : List.of(false, true)) {
        Event event = seriesEvent(5, 0);
        event.setAllDay(true);
        event.setTimeZoneId(zone);
        event.setStart(firstDay.atStartOfDay(zone));
        event.setEnd(firstDay.atTime(23, 59, 59).atZone(zone));
        Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
        ZonedDateTime dateId = dates(series).get(2);
        Event stored = agendaEventService.getEventById(series.getId());
        Duration duration = Duration.between(stored.getStart(), stored.getEnd());
        ZonedDateTime splitFrom = localMidnight ? splitDay.atStartOfDay(zone).withZoneSameInstant(ZoneOffset.UTC) : dateId;

        Event continuation = split(series, splitFrom, testuser1Identity, edits -> {
          edits.setStart(dateId);
          edits.setEnd(dateId.plus(duration));
        }, List.of(), List.of(), false, false);

        String message = zone + (localMidnight ? ", local midnight" : ", UTC midnight");
        assertEquals(message, splitDay, dates(continuation).get(0).toLocalDate());
        String rule = agendaEventService.getEventById(series.getId()).getRecurrence().getRrule();
        assertTrue(message + ": " + rule, rule.contains("UNTIL=" + untilOf(splitDay.minusDays(1), zone)));
      }
    }
  }

  /**
   * A change that gives neither dates nor a rule, as the participants drawer
   * sends it, continues the series' own dates from the split date and keeps
   * the answers.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aChangeGivingNoDatesNorRuleContinuesTheSeriesDatesAndKeepsTheAnswers() throws Exception {
    Event series = series(4, 0, testuser2Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);

    Event continuation = split(series, dates.get(1), testuser1Identity, edits -> {
      edits.setStart(null);
      edits.setEnd(null);
      edits.setRecurrence(null);
      edits.setTimeZoneId(null);
    }, List.of(), List.of(), false, false);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));
    assertEquals(dates.subList(1, 4).stream().map(ZonedDateTime::toInstant).toList(),
                 dates(continuation).stream().map(ZonedDateTime::toInstant).toList());
  }

  /**
   * A change that moves the first date to another weekday and leaves the
   * rule to the series moves a weekly rule naming one weekday onto the new
   * one: the dates follow the moved date, and the answers start over.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aChangeMovingTheFirstDateToAnotherWeekdayMovesTheSeriesWeeklyRule() throws Exception {
    Event event = seriesEvent(4, 0);
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
    event.getRecurrence().setByDay(new ArrayList<>(List.of(event.getStart().getDayOfWeek().name().substring(0, 2))));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);
    ZonedDateTime moved = dates.get(1).plusDays(1);

    Event continuation = split(series, dates.get(1), testuser1Identity, edits -> {
      edits.setRecurrence(null);
      edits.setStart(moved);
      edits.setEnd(moved.plusHours(1));
    }, List.of(), List.of(), false, false);

    String weekday = moved.getDayOfWeek().name().substring(0, 2);
    assertEquals(List.of(weekday), agendaEventService.getEventById(continuation.getId()).getRecurrence().getByDay());
    List<ZonedDateTime> continuationDates = dates(continuation);
    assertEquals(3, continuationDates.size());
    assertTrue(continuationDates.toString(),
               continuationDates.stream().allMatch(date -> date.getDayOfWeek() == moved.getDayOfWeek()));
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseAt(continuation.getId(), testuser2Identity, null));
  }

  /**
   * A change that moves the first date of a series repeating every week on
   * several weekdays to a weekday the series does not name, and leaves the
   * rule to the series, moves the weekday of the split date and keeps the
   * others: the continuation holds the date saved.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aChangeMovingTheFirstDateOfASeriesOnSeveralWeekdaysMovesItsWeekdayOnly() throws Exception {
    Event event = seriesEvent(4, 0);
    DayOfWeek first = event.getStart().getDayOfWeek();
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
    event.getRecurrence()
         .setByDay(new ArrayList<>(List.of(first.name().substring(0, 2), first.plus(2).name().substring(0, 2))));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    ZonedDateTime splitFrom = dates(series).get(2);
    ZonedDateTime moved = splitFrom.plusDays(1);

    Event continuation = split(series, splitFrom, testuser1Identity, edits -> {
      edits.setRecurrence(null);
      edits.setStart(moved);
      edits.setEnd(moved.plusHours(1));
    }, List.of(), List.of(), false, false);

    assertEquals(List.of(first.plus(1).name().substring(0, 2), first.plus(2).name().substring(0, 2)),
                 agendaEventService.getEventById(continuation.getId()).getRecurrence().getByDay());
    assertEquals(List.of(moved.toInstant(), moved.plusDays(1).toInstant()),
                 dates(continuation).stream().map(ZonedDateTime::toInstant).toList());
  }

  /**
   * A change that moves the first date of a series repeating every other
   * week into the week before, and leaves the rule to the series, moves the
   * weekday of the split date and repeats from the week of the moved date:
   * the continuation holds the date saved, and its other weekdays come a
   * week off the series'.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesRepeatingEveryOtherWeekWhoseFirstDateMovesIntoTheWeekBeforeRepeatsFromThatWeek() throws Exception {
    ZoneId paris = ZoneId.of("Europe/Paris");
    LocalDate monday = LocalDate.now(paris).plusDays(60).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    Event event = seriesEvent(8, 0);
    event.setTimeZoneId(paris);
    event.setStart(monday.atTime(9, 0).atZone(paris));
    event.setEnd(event.getStart().plusHours(1));
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
    event.getRecurrence().setInterval(2);
    event.getRecurrence().setByDay(new ArrayList<>(List.of("MO", "TH")));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    ZonedDateTime splitFrom = dates(series, 120).get(2);
    LocalDate sunday = monday.plusDays(13);
    ZonedDateTime moved = sunday.atTime(9, 0).atZone(paris);

    Event continuation = split(series, splitFrom, testuser1Identity, edits -> {
      edits.setRecurrence(null);
      edits.setStart(moved);
      edits.setEnd(moved.plusHours(1));
    }, List.of(), List.of(), false, false);

    assertEquals(monday.plusDays(14), splitFrom.withZoneSameInstant(paris).toLocalDate());
    assertEquals(List.of("SU", "TH"), agendaEventService.getEventById(continuation.getId()).getRecurrence().getByDay());
    assertEquals(List.of(sunday,
                         monday.plusDays(24),
                         monday.plusDays(27),
                         monday.plusDays(38),
                         monday.plusDays(41),
                         monday.plusDays(52)),
                 dates(continuation, 120).stream().map(date -> date.withZoneSameInstant(paris).toLocalDate()).toList());
  }

  /**
   * A change that moves the first date of a series held on several weekdays
   * onto another weekday the series names leaves the series on the weekdays
   * it names but the one of the split date, and the continuation holds the
   * date saved.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aChangeMovingTheFirstDateOntoAnotherWeekdayOfTheSeriesMergesThem() throws Exception {
    Event event = seriesEvent(4, 0);
    DayOfWeek first = event.getStart().getDayOfWeek();
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
    event.getRecurrence()
         .setByDay(new ArrayList<>(List.of(first.name().substring(0, 2), first.plus(2).name().substring(0, 2))));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    ZonedDateTime splitFrom = dates(series).get(2);
    ZonedDateTime moved = splitFrom.plusDays(2);

    Event continuation = split(series, splitFrom, testuser1Identity, edits -> {
      edits.setRecurrence(null);
      edits.setStart(moved);
      edits.setEnd(moved.plusHours(1));
    }, List.of(), List.of(), false, false);

    assertEquals(List.of(first.plus(2).name().substring(0, 2)),
                 agendaEventService.getEventById(continuation.getId()).getRecurrence().getByDay());
    assertEquals(List.of(moved.toInstant(), moved.plusDays(7).toInstant()),
                 dates(continuation).stream().map(ZonedDateTime::toInstant).toList());
  }

  /**
   * A change that gives neither dates nor a rule, as the participants drawer
   * sends it, on an all-day weekly series read in a zone that dates the
   * series' days otherwise, east and west of UTC, keeps the series' weekday,
   * continues its days and keeps the answers.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void anAllDayWeeklySeriesChangedFromTheDrawerKeepsItsWeekdayEastAndWestOfUtc() throws Exception {
    for (ZoneId zone : List.of(ZoneId.of("America/New_York"), ZoneId.of("Australia/Brisbane"))) {
      LocalDate firstDay = LocalDate.now(zone).plusDays(60);
      Event event = seriesEvent(5, 0);
      event.setAllDay(true);
      event.setTimeZoneId(zone);
      event.setStart(firstDay.atStartOfDay(zone));
      event.setEnd(firstDay.atTime(23, 59, 59).atZone(zone));
      event.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
      event.getRecurrence().setByDay(new ArrayList<>(List.of(firstDay.getDayOfWeek().name().substring(0, 2))));
      Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
      answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
      List<ZonedDateTime> dates = dates(series);

      Event continuation = split(series, dates.get(2), testuser1Identity, edits -> {
        edits.setStart(null);
        edits.setEnd(null);
        edits.setRecurrence(null);
        edits.setTimeZoneId(null);
      }, List.of(), List.of(), false, false, ZoneId.of("Pacific/Kiritimati"));

      String message = zone.getId();
      assertEquals(message,
                   List.of(firstDay.getDayOfWeek().name().substring(0, 2)),
                   agendaEventService.getEventById(continuation.getId()).getRecurrence().getByDay());
      assertEquals(message,
                   List.of(firstDay.plusWeeks(2), firstDay.plusWeeks(3), firstDay.plusWeeks(4)),
                   dates(continuation).stream().map(ZonedDateTime::toLocalDate).toList());
      assertEquals(message, EventAttendeeResponse.ACCEPTED, responseAt(continuation.getId(), testuser2Identity, null));
    }
  }

  /**
   * A change that moves the first date of a yearly series into another month
   * and leaves the rule to the series moves the day and the month the rule
   * names: the continuation holds the date saved.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aChangeMovingTheFirstDateOfAYearlySeriesIntoAnotherMonthMovesItsDayAndMonth() throws Exception {
    ZoneId paris = ZoneId.of("Europe/Paris");
    int year = LocalDate.now(paris).getYear() + 1;
    Event event = seriesEvent(3, 0);
    event.setTimeZoneId(paris);
    event.setStart(LocalDate.of(year, 3, 10).atTime(10, 0).atZone(paris));
    event.setEnd(event.getStart().plusHours(1));
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.YEARLY);
    event.getRecurrence().setByMonthDay(new ArrayList<>(List.of("10")));
    event.getRecurrence().setByMonth(new ArrayList<>(List.of("3")));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    ZonedDateTime splitFrom = dates(series, 800).get(1);
    ZonedDateTime moved = LocalDate.of(year + 1, 4, 12).atTime(10, 0).atZone(paris);

    Event continuation = split(series, splitFrom, testuser1Identity, edits -> {
      edits.setRecurrence(null);
      edits.setStart(moved);
      edits.setEnd(moved.plusHours(1));
    }, List.of(), List.of(), false, false);

    EventRecurrence rule = agendaEventService.getEventById(continuation.getId()).getRecurrence();
    assertEquals(List.of("12"), rule.getByMonthDay());
    assertEquals(List.of("4"), rule.getByMonth());
    assertEquals(List.of(moved.toInstant(), moved.plusYears(1).toInstant()),
                 dates(continuation, 800).stream().map(ZonedDateTime::toInstant).toList());
  }

  /**
   * A change that moves the first date of a monthly series held on several
   * days of the month to another day, and leaves the rule to the series,
   * moves the day of the split date and keeps the others: the continuation
   * holds the date saved.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aChangeMovingTheFirstDateOfAMonthlySeriesOnSeveralDaysMovesItsDayOnly() throws Exception {
    ZoneId paris = ZoneId.of("Europe/Paris");
    LocalDate firstDay = LocalDate.now(paris).plusDays(60).plusMonths(1).withDayOfMonth(10);
    Event event = seriesEvent(6, 0);
    event.setTimeZoneId(paris);
    event.setStart(firstDay.atTime(10, 0).atZone(paris));
    event.setEnd(event.getStart().plusHours(1));
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.MONTHLY);
    event.getRecurrence().setByMonthDay(new ArrayList<>(List.of("10", "25")));
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity);
    ZonedDateTime splitFrom = dates(series, 120).get(2);
    LocalDate movedDay = firstDay.plusMonths(1).withDayOfMonth(12);
    ZonedDateTime moved = movedDay.atTime(10, 0).atZone(paris);

    Event continuation = split(series, splitFrom, testuser1Identity, edits -> {
      edits.setRecurrence(null);
      edits.setStart(moved);
      edits.setEnd(moved.plusHours(1));
    }, List.of(), List.of(), false, false);

    assertEquals(List.of("12", "25"), agendaEventService.getEventById(continuation.getId()).getRecurrence().getByMonthDay());
    assertEquals(List.of(movedDay,
                         movedDay.withDayOfMonth(25),
                         movedDay.plusMonths(1),
                         movedDay.plusMonths(1).withDayOfMonth(25)),
                 dates(continuation, 120).stream().map(date -> date.withZoneSameInstant(paris).toLocalDate()).toList());
  }

  /**
   * A place on the waiting list carried over dates that all have rows of
   * their own is left waiting when the continuation gives seats: those dates
   * decide their seats on their rows, which are moved under the continuation
   * before its waiting list takes the seats.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aWaitingPlaceOnlyOverDatesWithRowsOfTheirOwnIsLeftWaiting() throws Exception {
    Event series = series(4, 1, testuser2Identity, testuser3Identity);
    answerAll(series.getId(), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    List<ZonedDateTime> dates = dates(series);
    answerFrom(series.getId(), dates.get(2), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseAt(series.getId(), testuser3Identity, dates.get(2)));
    for (ZonedDateTime date : dates.subList(2, 4)) {
      agendaEventAttendeeService.sendEventResponse(series.getId(), date, id(testuser3Identity), EventAttendeeResponse.DECLINED, true);
    }

    Event continuation = split(series,
                               dates.get(2),
                               testuser1Identity,
                               edits -> edits.setCapacity(2),
                               List.of(),
                               List.of(),
                               false,
                               false);

    assertEquals(EventAttendeeResponse.WAITING, responseAt(continuation.getId(), testuser3Identity, null));
    long movedId = agendaEventService.getExceptionalOccurrenceEvent(continuation.getId(), dates.get(2)).getId();
    assertEquals(EventAttendeeResponse.DECLINED, responseAt(movedId, testuser3Identity, null));
  }

  /**
   * A weekly series on the weekdays given at 17:30 in Paris, starting on a
   * Monday in sixty days, with six dates: created.
   */
  private Event weeklySeriesAt1730InParis(List<String> byDay) throws Exception {
    ZoneId paris = ZoneId.of("Europe/Paris");
    LocalDate monday = LocalDate.now(paris).plusDays(60).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    Event event = seriesEvent(6, 0);
    event.setTimeZoneId(paris);
    event.setStart(monday.atTime(17, 30).atZone(paris));
    event.setEnd(event.getStart().plusMinutes(30));
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
    event.getRecurrence().setByDay(new ArrayList<>(byDay));
    return createEvent(event, id(testuser1Identity), testuser2Identity);
  }

  /** The change written in the zone given, as a reader there writes it. */
  private static void inZone(Event edits, ZoneId zone) {
    edits.setTimeZoneId(zone);
    edits.setStart(edits.getStart().withZoneSameInstant(zone));
    edits.setEnd(edits.getEnd().withZoneSameInstant(zone));
  }

  /**
   * A daily series at 09:00 UTC starting in sixty days, in the space's
   * calendar, open, with the count and the limit given: not created yet.
   */
  private Event seriesEvent(int count, int capacity) {
    ZonedDateTime start = LocalDate.now(ZoneOffset.UTC).plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(count);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(capacity);
    return event;
  }

  private Event series(int count, int capacity, Identity... attendees) throws Exception {
    return createEvent(seriesEvent(count, capacity), id(testuser1Identity), attendees);
  }

  /**
   * The occurrence ids of a series' dates.
   */
  private List<ZonedDateTime> dates(Event series) {
    return dates(series, 60);
  }

  /**
   * The occurrence ids of a series' dates within the days given from its
   * first.
   */
  private List<ZonedDateTime> dates(Event series, int days) {
    Event stored = agendaEventService.getEventById(series.getId());
    ZonedDateTime start = stored.getStart();
    return agendaEventService.getEventOccurrencesInPeriod(stored, start.minusDays(1), start.plusDays(days), stored.getTimeZoneId(), 0)
                             .stream()
                             .map(occurrence -> occurrence.getOccurrence().getId())
                             .toList();
  }

  /** A split by the creator that edits nothing. */
  private Event split(Event series, ZonedDateTime splitFrom) throws Exception {
    return split(series, splitFrom, testuser1Identity, edits -> {
    }, List.of(), List.of(), false, false);
  }

  /**
   * A split as the dialog asks it: the series as stored, starting on the
   * split date at its times, with the edits given.
   */
  private Event split(Event series,
                      ZonedDateTime splitFrom,
                      Identity user,
                      Consumer<Event> edit,
                      List<Identity> added,
                      List<Identity> removed,
                      boolean confirmClose,
                      boolean sendInvitation) throws Exception {
    return split(series, splitFrom, user, edit, added, removed, confirmClose, sendInvitation, null);
  }

  /**
   * A split as the dialog asks it, written in the zone the series was read
   * in.
   */
  private Event split(Event series,
                      ZonedDateTime splitFrom,
                      Identity user,
                      Consumer<Event> edit,
                      List<Identity> added,
                      List<Identity> removed,
                      boolean confirmClose,
                      boolean sendInvitation,
                      ZoneId readZone) throws Exception {
    Event edits = agendaEventService.getEventById(series.getId()).clone();
    Duration duration = Duration.between(edits.getStart(), edits.getEnd());
    edits.setStart(splitFrom);
    edits.setEnd(splitFrom.plus(duration));
    edit.accept(edits);
    return agendaEventService.splitEvent(series.getId(),
                                         new EventSplit(edits,
                                                        null,
                                                        null,
                                                        added.stream().map(AgendaEventSplitTest::id).toList(),
                                                        new HashSet<>(removed.stream().map(AgendaEventSplitTest::id).toList()),
                                                        readZone),
                                         splitFrom,
                                         sendInvitation,
                                         confirmClose,
                                         id(user));
  }

  private void answerAll(long seriesId, Identity identity, EventAttendeeResponse response) throws Exception {
    agendaEventAttendeeService.sendEventResponse(seriesId, id(identity), response);
  }

  private void answerFrom(long seriesId, ZonedDateTime date, Identity identity, EventAttendeeResponse response) throws Exception {
    agendaEventAttendeeService.sendUpcomingEventResponse(seriesId, date, id(identity), response);
  }

  private List<EventAttendee> rows(long eventId, Identity identity) {
    return agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(id(identity));
  }

  private EventAttendeeResponse responseAt(long eventId, Identity identity, ZonedDateTime date) {
    EventAttendee row = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendee(id(identity), date);
    return row == null ? null : row.getResponse();
  }

  private Identity rootIdentity() {
    return identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "root");
  }

  /**
   * @return the UNTIL a rule ending on the day given, in the zone given,
   *         states: the last second of that day, in UTC
   */
  private static String untilOf(LocalDate lastDay, ZoneId zone) {
    return lastDay.atStartOfDay(zone)
                  .plusDays(1)
                  .minusSeconds(1)
                  .withZoneSameInstant(ZoneOffset.UTC)
                  .format(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'"));
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
