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

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.exception.CapacityBelowAcceptedException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeePromotion;
import org.exoplatform.agenda.model.EventOccurrence;
import org.exoplatform.agenda.model.EventResponseOutcome;
import org.exoplatform.agenda.model.RemoteEvent;
import org.exoplatform.agenda.service.RecordingStatementInspector.RecordedStatement;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.social.core.identity.model.Identity;

/**
 * Pins the writes of a capacity limit (eXIP 7.3.0.21, US01): where an event
 * may hold one, the floor the Yes answers set, what a raised or removed limit
 * and a closing give the waiting list, and the closing's confirmation, on an
 * event of no series and on a series and its dates individually modified.
 * <p>
 * Every event is in the space's calendar, created by the first test user, a
 * manager of no row but its creator, and starts in sixty days: no date of a
 * series is materialised by the reminder computing listener when it is
 * created.
 */
public class AgendaEventCapacityWriteTest extends BaseAgendaEventTest {

  private static final String          CAPACITY           = "capacity";

  private static final List<String>    WATCHED            = List.of(Utils.POST_EVENT_ATTENDEE_PROMOTED,
                                                                    Utils.POST_EVENT_CAPACITY_REACHED,
                                                                    Utils.POST_UPDATE_AGENDA_EVENT_EVENT);

  private static final List<Broadcast> BROADCASTS         = Collections.synchronizedList(new ArrayList<>());

  private static boolean               listenersRegistered;

  /**
   * Registers, once for the container, the listeners recording the
   * promotions, the capacity reached events and the updates.
   */
  @Before
  public void registerListeners() {
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
   * A limit is stored on an open event of a space only: asked on a locked
   * event, on an event of a personal calendar or on a date poll, it is no
   * limit, as the open flag is.
   *
   * @throws Exception when a creation fails
   */
  @Test
  public void aLimitIsStoredOnAnOpenEventOfASpaceOnly() throws Exception {
    assertEquals(3, capacityOf(openEvent(3).getId()));

    Event locked = spaceEvent(3);
    locked.setOpen(false);
    assertEquals(0, capacityOf(createEvent(locked, id(testuser1Identity)).getId()));

    Event personal = spaceEvent(3);
    personal.setCalendarId(calendar.getId());
    assertEquals(0, capacityOf(createEvent(personal, id(testuser1Identity)).getId()));

    Event datePoll = spaceEvent(3);
    datePoll.setStatus(EventStatus.TENTATIVE);
    assertEquals(0, capacityOf(createEvent(datePoll, id(testuser1Identity)).getId()));
  }

  /**
   * A negative limit is refused with its code on every write path, and leaves
   * the stored limit as it was.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aNegativeLimitIsRefusedOnEveryWritePath() throws Exception {
    Event negative = spaceEvent(-1);
    assertCode("agenda.capacity.invalid", assertThrows(IllegalArgumentException.class,
                                                       () -> createEvent(negative, id(testuser1Identity))));

    long eventId = openEvent(2).getId();
    assertCode("agenda.capacity.invalid", assertThrows(IllegalArgumentException.class, () -> patch(eventId, CAPACITY, "-1")));
    assertCode("agenda.capacity.invalid", assertThrows(IllegalArgumentException.class, () -> patch(eventId, CAPACITY, "two")));
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(-1);
    assertCode("agenda.capacity.invalid", assertThrows(IllegalArgumentException.class, () -> save(toSave, false)));
    assertEquals(2, capacityOf(eventId));
  }

  /**
   * A limit is lowered no further than the Yes answers it holds, by a patch
   * or a full save, which then writes nothing at all; at the floor it is
   * taken.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aLimitBelowTheYesAnswersIsRefusedBeforeAnyWrite() throws Exception {
    long eventId = openEvent(3, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertBlocks(null, () -> patch(eventId, CAPACITY, "1"));
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(1);
    toSave.setSummary("refused");
    assertBlocks(null, () -> save(toSave, false));
    Event stored = agendaEventService.getEventById(eventId);
    assertEquals(3, capacityOf(eventId));
    assertEquals("eventSummary", stored.getSummary());

    patch(eventId, CAPACITY, "2");

    assertEquals(2, capacityOf(eventId));
  }

  /**
   * One full save that lowers the limit and removes an attendee holding a
   * seat stores both, the floor counting the removal, and publishes its update
   * once.
   *
   * @throws Exception when the save fails
   */
  @Test
  public void oneSaveThatLowersTheLimitAndRemovesAnAttendeeStoresBothAndPublishesOnce() throws Exception {
    long eventId = openEvent(3, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(1);
    List<EventAttendee> withoutTestuser3 = agendaEventAttendeeService.getEventAttendees(eventId)
                                                                     .getEventAttendees()
                                                                     .stream()
                                                                     .filter(attendee -> attendee.getIdentityId() != id(testuser3Identity))
                                                                     .collect(Collectors.toList());
    BROADCASTS.clear();

    agendaEventService.updateEvent(toSave,
                                   withoutTestuser3,
                                   agendaEventConferenceService.getEventConferences(eventId),
                                   agendaEventReminderService.getEventReminders(eventId),
                                   null,
                                   null,
                                   false,
                                   false,
                                   id(testuser1Identity));

    assertEquals(1, capacityOf(eventId));
    assertNull(rowOf(eventId, testuser3Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser2Identity));
    assertEquals(1, broadcasts(Utils.POST_UPDATE_AGENDA_EVENT_EVENT).size());
  }

  /**
   * A raised limit gives its seats to the waiting list in the order it
   * asked, and no limit gives one to everyone left waiting.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aRaisedLimitConfirmsTheWaitingListInTheOrderItAsked() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity, testuser4Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser4Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser4Identity));
    BROADCASTS.clear();

    patch(eventId, CAPACITY, "2");

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser4Identity));
    assertEquals(List.of(id(testuser3Identity)), promotedIdentities());

    patch(eventId, CAPACITY, "0");

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser4Identity));
  }

  /**
   * A full save raising the limit gives the seat to the waiting list before
   * the saver's own Yes it adds, which waits.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aFullSaveRaisingTheLimitConfirmsTheWaitingListBeforeTheSaversOwnYes() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(2);
    List<EventAttendee> attendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees());
    attendees.add(new EventAttendee(0, eventId, id(testuser1Identity), EventAttendeeResponse.ACCEPTED));

    agendaEventService.updateEvent(toSave,
                                   attendees,
                                   agendaEventConferenceService.getEventConferences(eventId),
                                   agendaEventReminderService.getEventReminders(eventId),
                                   null,
                                   null,
                                   false,
                                   false,
                                   id(testuser1Identity));

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser1Identity));
  }

  /**
   * A limit written never reports itself reached: its mark is written from
   * the Yes answers held, cleared by a limit raised above them, set silently
   * by a limit lowered to them, so that a Yes given again by a seated
   * attendee reports nothing.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aLimitWrittenNeverReportsItselfReached() throws Exception {
    long eventId = openEvent(1, testuser2Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());
    BROADCASTS.clear();

    patch(eventId, CAPACITY, "2");

    assertNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());

    patch(eventId, CAPACITY, "1");

    assertNotNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A limit raised to the seats its waiting list then takes leaves the event
   * full and marked silently: a Yes given again by a seated attendee reports
   * nothing; raised above them, the mark is cleared and the answer filling
   * the new limit reports it (D7).
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aLimitRaisedToTheSeatsItsWaitingListTakesIsMarkedSilently() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));
    BROADCASTS.clear();

    patch(eventId, CAPACITY, "2");

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
    assertNotNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    patch(eventId, CAPACITY, "3");

    assertNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());

    answer(eventId, testuser1Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(eventId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
  }

  /**
   * A series' limit raised to the seats its waiting list takes at every
   * date is marked silently: a Yes given again reports nothing; raised above
   * them, the mark is cleared and the answer filling the new limit reports
   * the series at its first date.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesLimitRaisedToTheSeatsItsWaitingListTakesIsMarkedSilently() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(seriesId, testuser3Identity));
    BROADCASTS.clear();

    patchAll(seriesId, CAPACITY, "2");

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(seriesId, testuser3Identity));
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    patchAll(seriesId, CAPACITY, "3");

    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answer(seriesId, testuser1Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
    assertEquals(dates.get(0), broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).data());
  }

  /**
   * A series' limit is marked when a date it carries is full under it, not
   * when the rows answering Yes over different dates add up to it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesLimitIsMarkedWhenADateItCarriesIsFull() throws Exception {
    Event series = openSeries(3, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    setAnswer(seriesId, testuser2Identity, null, dates.get(1), EventAttendeeResponse.ACCEPTED);
    setAnswer(seriesId, testuser3Identity, dates.get(1), null, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    patchAll(seriesId, CAPACITY, "2");

    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    patchAll(seriesId, CAPACITY, "1");

    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A series' limit lowered to the Yes answers a date it stores holds, that
   * date holding the series' limit, marks the series silently as well as the
   * date: the date reports through the series' mark (D21), so an answer then
   * filling a date of the series reports nothing, as it would had the
   * platform stored no row for the date.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesLimitLoweredToTheYesHeldOnADateItStoresIsMarkedSilently() throws Exception {
    Event series = openSeries(3, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(1)).getId();
    BROADCASTS.clear();

    patchAll(seriesId, CAPACITY, "2");

    assertEquals(2, capacityOf(dateId));
    assertNotNull(agendaEventService.getEventById(dateId).getCapacityReachedDate());
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A series' limit raised to the seats the waiting list of a date it stores
   * takes marks the series silently as well as the date: full under the new
   * limit once promoted, the date reports through the series' mark (D21), so
   * an answer then filling a date of the series reports nothing. The date's
   * answers are stored as answers given before the limit existed, so that
   * neither the date nor the series is marked before the limit is written.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesLimitRaisedToTheSeatsAStoredDatesWaitingListTakesIsMarkedSilently() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(1)).getId();
    setAnswer(dateId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(dateId, testuser3Identity, null, null, EventAttendeeResponse.WAITING);
    assertNull(agendaEventService.getEventById(dateId).getCapacityReachedDate());
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    BROADCASTS.clear();

    patchAll(seriesId, CAPACITY, "2");

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser3Identity));
    assertNotNull(agendaEventService.getEventById(dateId).getCapacityReachedDate());
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A series' limit lowered by a full save to the Yes answers a date it
   * stores holds marks the series silently as well as the date, the mark
   * written once the save has reached the dates the series stores.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesLimitLoweredByAFullSaveToTheYesHeldOnADateItStoresIsMarkedSilently() throws Exception {
    Event series = openSeries(3, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(1)).getId();
    BROADCASTS.clear();
    Event toSave = agendaEventService.getEventById(seriesId);
    toSave.setCapacity(2);

    save(toSave, false);

    assertEquals(2, capacityOf(dateId));
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A stored date holding the series' limit, through which the series was
   * reported, given a limit of its own, leaves the dates the series reports
   * (D21) and the series' mark standing: the series' limit stands, so the
   * answer then filling one of its dates reports nothing, as after any seat
   * freed and taken again.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aStoredDateGivenALimitOfItsOwnKeepsTheSeriesMark() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(1)).getId();
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    BROADCASTS.clear();

    patch(dateId, CAPACITY, "3");

    assertNull(agendaEventService.getEventById(dateId).getCapacityReachedDate());
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A stored date joining the dates the series reports with a seat free, its
   * limit of its own taken back, leaves the series' mark alone: a mark
   * standing after a seat was freed on a date of the series keeps the series
   * reported once while its limit stands, so the Yes taking the seat again
   * reports nothing.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aStoredDateJoiningTheDatesTheSeriesReportsWithASeatFreeLeavesTheSeriesMarkAlone() throws Exception {
    Event series = openSeries(2, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(1)).getId();
    patch(dateId, CAPACITY, "5");
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    answer(seriesId, testuser3Identity, EventAttendeeResponse.DECLINED);
    BROADCASTS.clear();

    patch(dateId, CAPACITY, "2");

    assertNull(agendaEventService.getEventById(dateId).getCapacityReachedDate());
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A stored date joining the dates the series reports with a seat free
   * while the series is unmarked leaves it unmarked: the Yes then filling
   * the date reports the series at that date (D7).
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aStoredDateJoiningTheDatesTheSeriesReportsWithASeatFreeLeavesTheSeriesUnmarked() throws Exception {
    Event series = openSeries(2, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(1)).getId();
    patch(dateId, CAPACITY, "3");
    BROADCASTS.clear();

    patch(dateId, CAPACITY, "2");

    assertNull(agendaEventService.getEventById(dateId).getCapacityReachedDate());
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
    assertEquals(dates.get(1), broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).data());
  }

  /**
   * A date over, given the series' limit while full, joins the dates the
   * series reports but decides no seat any more: the series stays unmarked,
   * and the answer then filling a date to come reports it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateOverJoiningTheDatesTheSeriesReportsFullLeavesTheSeriesUnmarked() throws Exception {
    Event series = pastSeries(2, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long dateOverId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(0)).getId();
    Event dateOver = agendaEventService.getEventById(dateOverId);
    dateOver.setCapacity(3);
    agendaEventStorage.updateEvent(dateOver);
    setAnswer(dateOverId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(dateOverId, testuser3Identity, null, null, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    patch(dateOverId, CAPACITY, "2");

    assertEquals(2, capacityOf(dateOverId));
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
  }

  /**
   * A cancelled date full under the series' limit is not one the series'
   * mark counts when the series' limit is written: the series stays
   * unmarked, the cancelled date is marked on its own, silently, and the
   * answers then filling a date the series carries report it once, the Yes
   * given again on the cancelled date reporting nothing.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aCancelledDateFullIsNotCountedBySeriesMarkWrite() throws Exception {
    Event series = openSeries(3, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long cancelledId = cancelDate(seriesId, dates.get(1));
    setAnswer(cancelledId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(cancelledId, testuser3Identity, null, null, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    patchAll(seriesId, CAPACITY, "2");

    assertEquals(2, capacityOf(cancelledId));
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertNotNull(agendaEventService.getEventById(cancelledId).getCapacityReachedDate());
    assertEquals(0, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
  }

  /**
   * A date over full under the series' limit is not one the series' mark
   * counts when the series' limit is written: the series stays unmarked, and
   * the answers then filling a date to come report it once.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateOverFullIsNotCountedBySeriesMarkWrite() throws Exception {
    Event series = pastSeries(3, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long dateOverId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(0)).getId();
    Event dateOver = agendaEventService.getEventById(dateOverId);
    dateOver.setCapacity(2);
    agendaEventStorage.updateEvent(dateOver);
    setAnswer(dateOverId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(dateOverId, testuser3Identity, null, null, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    patchAll(seriesId, CAPACITY, "2");

    assertEquals(2, capacityOf(dateOverId));
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
  }

  /**
   * A stored date whose limit of its own is set to the series' limit while
   * full under it joins the dates the series reports (D21): the series' mark
   * is written again from them, set silently, so that an answer then filling
   * a date of the series reports nothing.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aStoredDateSetBackToTheSeriesLimitWhileFullMarksTheSeriesSilently() throws Exception {
    Event series = openSeries(2, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(1)).getId();
    patch(dateId, CAPACITY, "3");
    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    BROADCASTS.clear();

    patch(dateId, CAPACITY, "2");

    assertNotNull(agendaEventService.getEventById(dateId).getCapacityReachedDate());
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A cancelled date holding the series' limit, confirmed again while full
   * under it, joins the dates the series reports (D21) as a date set back to
   * the series' limit does: the series is marked silently, so that an answer
   * then filling a date of the series reports nothing.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aCancelledDateConfirmedAgainWhileFullMarksTheSeriesSilently() throws Exception {
    Event series = openSeries(2, testuser2Identity, testuser3Identity, testuser4Identity, testuser5Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long cancelledId = cancelDate(seriesId, dates.get(1));
    setAnswer(cancelledId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(cancelledId, testuser3Identity, null, null, EventAttendeeResponse.ACCEPTED);
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    BROADCASTS.clear();

    patch(cancelledId, "status", EventStatus.CONFIRMED.name());

    assertEquals(2, capacityOf(cancelledId));
    assertEquals(EventStatus.CONFIRMED, agendaEventService.getEventById(cancelledId).getStatus());
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(seriesId, testuser4Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser5Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A full save setting a stored date's limit of its own back to the series'
   * limit while full marks the series silently, as a patch does.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aFullSaveSettingAStoredDateBackToTheSeriesLimitWhileFullMarksTheSeriesSilently() throws Exception {
    Event series = openSeries(2, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(1)).getId();
    patch(dateId, CAPACITY, "3");
    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    BROADCASTS.clear();
    Event toSave = agendaEventService.getEventById(dateId);
    toSave.setCapacity(2);

    save(toSave, false);

    assertEquals(2, capacityOf(dateId));
    assertNotNull(agendaEventService.getEventById(dateId).getCapacityReachedDate());
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A stored date whose limit of its own changes to another of its own stays
   * out of the dates the series reports: the series' mark is left alone, so
   * that a seat freed and taken again on a date of the series reports
   * nothing while the series' limit stands.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aStoredDatesOwnLimitChangedLeavesTheSeriesMarkAlone() throws Exception {
    Event series = openSeries(2, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(1)).getId();
    patch(dateId, CAPACITY, "5");
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    answer(seriesId, testuser3Identity, EventAttendeeResponse.DECLINED);
    BROADCASTS.clear();

    patch(dateId, CAPACITY, "6");

    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A stored date holding the series' limit, through which the series was
   * reported, cancelled, leaves the dates the series reports (D21) as a limit
   * of its own does, and the series' mark standing.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aStoredDateCancelledKeepsTheSeriesMark() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    BROADCASTS.clear();

    cancelDate(seriesId, dates.get(1));

    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * An answer a full save stores with a raised limit, the editor adding
   * themselves with a Yes, reports the new limit it fills (D7): the mark of
   * the old limit does not hide it.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void anAnswerSavedWithARaisedLimitReportsTheLimitItFills() throws Exception {
    long eventId = openEvent(2, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());
    BROADCASTS.clear();
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(3);
    List<EventAttendee> attendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees());
    attendees.add(new EventAttendee(0, eventId, id(testuser1Identity), EventAttendeeResponse.ACCEPTED));

    agendaEventService.updateEvent(toSave,
                                   attendees,
                                   agendaEventConferenceService.getEventConferences(eventId),
                                   agendaEventReminderService.getEventReminders(eventId),
                                   null,
                                   null,
                                   false,
                                   false,
                                   id(testuser1Identity));

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser1Identity));
    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(eventId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
    assertNotNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());
  }

  /**
   * A limit written by a full save writes the mark from the answers, as a
   * patch does, once the save has promoted the waiting list: raised to the
   * seats it takes, the event stays marked and a seated attendee's Yes again
   * reports nothing; raised above them, the mark is cleared.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aLimitWrittenByAFullSaveWritesTheReachedMarkFromTheAnswers() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());
    BROADCASTS.clear();
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(2);

    save(toSave, false);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
    assertNotNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());

    toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(3);
    save(toSave, false);

    assertNull(agendaEventService.getEventById(eventId).getCapacityReachedDate());
  }

  /**
   * Closing an event whose waiting list it would confirm is refused until the
   * user confirms it, writing nothing; confirmed, the event is closed, its
   * limit removed and its waiting list confirmed (D15).
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void closingAnEventWithAWaitingListNeedsTheUsersConfirmation() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    assertCode("agenda.capacity.closeNeedsConfirmation",
               assertThrows(IllegalArgumentException.class, () -> patch(eventId, "open", "false")));
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(eventId).getOpen());
    assertEquals(1, capacityOf(eventId));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));

    patchConfirmed(eventId, "open", "false");

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertEquals(0, capacityOf(eventId));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
    assertEquals(List.of(id(testuser3Identity)), promotedIdentities());
  }

  /**
   * A full save that closes the event asks for the same confirmation, and
   * confirms the waiting list once given.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void closingAnEventThroughAFullSaveNeedsTheUsersConfirmation() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setOpen(false);

    assertCode("agenda.capacity.closeNeedsConfirmation",
               assertThrows(IllegalArgumentException.class, () -> save(toSave.clone(), false)));
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(eventId).getOpen());

    save(toSave.clone(), true);

    assertEquals(0, capacityOf(eventId));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
  }

  /**
   * Closing an event nobody waits for confirms nobody and asks nothing: its
   * limit is removed silently.
   *
   * @throws Exception when the patch fails
   */
  @Test
  public void closingAnEventNobodyWaitsForAsksNoConfirmation() throws Exception {
    long eventId = openEvent(2, testuser2Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    patch(eventId, "open", "false");

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertEquals(0, capacityOf(eventId));
  }

  /**
   * A move to a personal calendar closes the event: it asks for the same
   * confirmation as the padlock.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aMoveToAPersonalCalendarIsAClosing() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    String personalCalendarId = String.valueOf(calendar.getId());

    assertCode("agenda.capacity.closeNeedsConfirmation",
               assertThrows(IllegalArgumentException.class, () -> patch(eventId, "calendarId", personalCalendarId)));
    assertEquals(spaceCalendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());

    patchConfirmed(eventId, "calendarId", personalCalendarId);

    assertEquals(0, capacityOf(eventId));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
  }

  /**
   * Allowing the participants to modify the event closes it through a full
   * save: it asks for the same confirmation.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aParticipantPermissionGrantedThroughAFullSaveIsAClosing() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setAllowAttendeeToUpdate(true);

    assertCode("agenda.capacity.closeNeedsConfirmation",
               assertThrows(IllegalArgumentException.class, () -> save(toSave.clone(), false)));

    save(toSave.clone(), true);

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
  }

  /**
   * A switch to a date poll is not a closing: it removes the limit, asks for
   * nothing and confirms nobody (F1).
   *
   * @throws Exception when the patch fails
   */
  @Test
  public void aSwitchToADatePollRemovesTheLimitAndConfirmsNobody() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    BROADCASTS.clear();

    patch(eventId, "status", EventStatus.TENTATIVE.name());

    assertEquals(0, capacityOf(eventId));
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertNotEquals(EventAttendeeResponse.ACCEPTED, responseOf(eventId, testuser3Identity));
    assertTrue(promotedIdentities().isEmpty());
  }

  /**
   * A limit asked by a patch on an event that may hold none is refused with
   * its code.
   *
   * @throws Exception when a creation fails
   */
  @Test
  public void aLimitAskedOnAnEventThatMayHoldNoneIsRefused() throws Exception {
    Event locked = spaceEvent(0);
    locked.setOpen(false);
    long lockedId = createEvent(locked, id(testuser1Identity)).getId();
    Event personal = spaceEvent(0);
    personal.setCalendarId(calendar.getId());
    long personalId = createEvent(personal, id(testuser1Identity)).getId();

    assertCode("agenda.capacity.notAllowed", assertThrows(IllegalArgumentException.class, () -> patch(lockedId, CAPACITY, "2")));
    assertCode("agenda.capacity.notAllowed",
               assertThrows(IllegalArgumentException.class, () -> patch(personalId, CAPACITY, "2")));
  }

  /**
   * A changed limit is reported among the event's modifications, and only
   * then.
   *
   * @throws Exception when a patch fails
   */
  @Test
  public void aChangedLimitIsReported() throws Exception {
    long eventId = openEvent(1).getId();

    patch(eventId, CAPACITY, "4");

    assertTrue(eventUpdateReference.get().hasModification(AgendaEventModificationType.CAPACITY_UPDATED));

    patch(eventId, "summary", "renamed");

    assertFalse(eventUpdateReference.get().hasModification(AgendaEventModificationType.CAPACITY_UPDATED));
  }

  /**
   * A write of an event holding a limit locks it as the first statement of
   * its transaction.
   *
   * @throws Exception when the patch fails
   */
  @Test
  public void aWriteOfAnEventWithALimitLocksItFirst() throws Exception {
    long eventId = openEvent(2, testuser2Identity).getId();

    List<RecordedStatement> statements = record(() -> patch(eventId, "summary", "locked"));

    RecordedStatement first = statements.stream()
                                        .filter(RecordedStatement::inTransaction)
                                        .findFirst()
                                        .orElseThrow(() -> new AssertionError("no statement ran in a transaction: " + statements));
    assertTrue("the first statement of the transaction is not a locking read of an event row: " + statements,
               first.isLockingReadOfAnEvent());
  }

  /**
   * A full save of an event with a limit locks it as the first statement of
   * its transaction, as a patch does.
   *
   * @throws Exception when the save fails
   */
  @Test
  public void aFullSaveOfAnEventWithALimitLocksItFirst() throws Exception {
    long eventId = openEvent(2, testuser2Identity).getId();
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(3);

    List<RecordedStatement> statements = record(() -> save(toSave, false));

    RecordedStatement first = statements.stream()
                                        .filter(RecordedStatement::inTransaction)
                                        .findFirst()
                                        .orElseThrow(() -> new AssertionError("no statement ran in a transaction: " + statements));
    assertTrue("the first statement of the transaction is not a locking read of an event row: " + statements,
               first.isLockingReadOfAnEvent());
  }

  /**
   * A full save of an event with a limit is one operation: a write failing
   * after the event row is stored leaves the row as it was.
   *
   * @throws Exception when an event can't be created
   */
  @Test
  public void aFullSaveFailingMidwayWritesNothing() throws Exception {
    long eventId = openEvent(3, testuser2Identity).getId();
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setCapacity(2);
    toSave.setSummary("half written");
    RemoteEvent unknownProvider = new RemoteEvent(0, eventId, id(testuser1Identity), "remoteId", 0, "unknownProvider");

    assertThrows(Exception.class,
                 () -> agendaEventService.updateEvent(toSave,
                                                      agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(),
                                                      agendaEventConferenceService.getEventConferences(eventId),
                                                      agendaEventReminderService.getEventReminders(eventId),
                                                      null,
                                                      unknownProvider,
                                                      false,
                                                      false,
                                                      id(testuser1Identity)));

    restartTransaction();
    Event stored = agendaEventService.getEventById(eventId);
    assertEquals("eventSummary", stored.getSummary());
    assertEquals(3, capacityOf(eventId));
  }

  /**
   * A series' limit is lowered no further than the most Yes answers given at
   * one of its dates still to come (D16).
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aSeriesLimitIsNoLowerThanTheMostYesAnswersAtOneOfItsDates() throws Exception {
    Event series = openSeries(4, testuser2Identity, testuser3Identity, testuser4Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendUpcomingEventResponse(seriesId,
                                                         dates.get(1),
                                                         id(testuser4Identity),
                                                         EventAttendeeResponse.ACCEPTED,
                                                         true);

    patchAll(seriesId, CAPACITY, "3");

    assertEquals(3, capacityOf(seriesId));
    assertBlocks(dates.get(1).toLocalDate(), () -> patchAll(seriesId, CAPACITY, "2"));
    assertEquals(3, capacityOf(seriesId));
  }

  /**
   * A series' floor counts the Yes answers date by date, not its Yes rows: an
   * attendee saying Yes to the first date only and another from the second
   * one hold one seat at each date.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aSeriesLimitCountsTheYesAnswersDateByDate() throws Exception {
    Event series = openSeries(2, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    ZonedDateTime secondDate = occurrenceIds(series).get(1);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendUpcomingEventResponse(seriesId,
                                                         secondDate,
                                                         id(testuser2Identity),
                                                         EventAttendeeResponse.DECLINED,
                                                         true);
    agendaEventAttendeeService.sendUpcomingEventResponse(seriesId,
                                                         secondDate,
                                                         id(testuser3Identity),
                                                         EventAttendeeResponse.ACCEPTED,
                                                         true);

    patchAll(seriesId, CAPACITY, "1");

    assertEquals(1, capacityOf(seriesId));
  }

  /**
   * A change of a series' limit reaches its dates individually modified whose
   * limit follows the series', each held to its own Yes answers; a date whose
   * limit is its own keeps it, and its floor.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aSeriesLimitReachesTheDatesWhoseLimitFollowsIt() throws Exception {
    Event series = openSeries(3, testuser2Identity, testuser3Identity, testuser4Identity);
    long seriesId = series.getId();
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    for (Identity identity : List.of(testuser2Identity, testuser3Identity, testuser4Identity)) {
      answerDate(seriesId, firstDate, identity, EventAttendeeResponse.ACCEPTED);
    }
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, firstDate).getId();
    assertEquals(3, capacityOf(dateId));

    assertBlocks(firstDate.toLocalDate(), () -> patchAll(seriesId, CAPACITY, "2"));

    patch(seriesId, CAPACITY, "2");

    assertEquals(2, capacityOf(seriesId));
    assertEquals(3, capacityOf(dateId));

    patchAll(seriesId, CAPACITY, "1");

    assertEquals(1, capacityOf(seriesId));
    assertEquals(3, capacityOf(dateId));
  }

  /**
   * A date of a series created with a limit of its own below the Yes answers
   * the series holds there takes the series' attendees with their answers,
   * which the creation's broadcast starts over, the creator's aside
   * (AgendaReplyOnSaveListener): once created, the date holds no Yes its
   * limit is below and nobody waits there.
   *
   * @throws Exception when the creation fails
   */
  @Test
  public void aDateCreatedWithALimitOfItsOwnStartsTheAnswersItTakesOver() throws Exception {
    Event series = openSeries(3, testuser2Identity, testuser3Identity, testuser4Identity);
    long seriesId = series.getId();
    for (Identity identity : List.of(testuser2Identity, testuser3Identity, testuser4Identity)) {
      answer(seriesId, identity, EventAttendeeResponse.ACCEPTED);
    }
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    Event date = agendaEventService.getEventOccurrence(seriesId, firstDate, ZoneOffset.UTC, id(testuser1Identity));
    date.setId(0);
    date.setCapacity(1);

    Event created = agendaEventService.createEvent(date,
                                                   Collections.emptyList(),
                                                   null,
                                                   null,
                                                   null,
                                                   null,
                                                   false,
                                                   id(testuser1Identity));

    assertEquals(1, capacityOf(created.getId()));
    for (Identity identity : List.of(testuser2Identity, testuser3Identity, testuser4Identity)) {
      assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(created.getId(), identity));
    }
  }

  /**
   * A date of a series that is over keeps its limit and its answers: a lower
   * limit of the series is not held to its Yes answers, and neither a removed
   * one nor a closing, which removes its own, confirms anybody waiting there.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateOverKeepsItsLimitAndItsAnswers() throws Exception {
    Event series = pastSeries(3, testuser2Identity, testuser3Identity, testuser4Identity, testuser5Identity);
    long seriesId = series.getId();
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    agendaEventService.saveEventExceptionalOccurrence(seriesId, firstDate);
    long firstDateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, firstDate).getId();
    for (Identity identity : List.of(testuser2Identity, testuser3Identity, testuser4Identity)) {
      setAnswer(firstDateId, identity, null, null, EventAttendeeResponse.ACCEPTED);
    }
    setAnswer(firstDateId, testuser5Identity, null, null, EventAttendeeResponse.WAITING);

    patchAll(seriesId, CAPACITY, "2");

    assertEquals(2, capacityOf(seriesId));
    assertEquals(3, capacityOf(firstDateId));

    patchAll(seriesId, CAPACITY, "0");

    assertEquals(3, capacityOf(firstDateId));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(firstDateId, testuser5Identity));

    patchAll(seriesId, "open", "false");

    assertEquals(0, capacityOf(firstDateId));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(firstDateId, testuser5Identity));
  }

  /**
   * A date over that a closing left waiting asks no confirmation when its
   * series, reopened with no limit, is closed again: with no limit to remove,
   * that closing confirms nobody there either.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void closingAgainASeriesWhoseDateOverStillWaitsAsksNoConfirmation() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    long firstDateId = firstDateOverWithItsSeatHeld(series);
    patchAll(seriesId, "open", "false");
    patchAll(seriesId, "open", "true");
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(firstDateId).getOpen());
    assertEquals(0, capacityOf(firstDateId));

    patchAll(seriesId, "open", "false");

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(firstDateId).getOpen());
    assertEquals(EventAttendeeResponse.WAITING, responseOf(firstDateId, testuser3Identity));
  }

  /**
   * Removing from a series someone who holds a Yes on a date of it that is
   * over gives that seat to nobody: the date keeps its answers.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void removingFromASeriesAYesOnADateOverGivesItsSeatToNobody() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long firstDateId = firstDateOverWithItsSeatHeld(series);

    saveWithout(agendaEventService.getEventById(series.getId()), testuser2Identity, false);

    assertNull(rowOf(firstDateId, testuser2Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(firstDateId, testuser3Identity));
    assertTrue(promotedIdentities().isEmpty());
  }

  /**
   * A full save closing a series while removing someone who holds a Yes on a
   * date of it that is over asks no confirmation and confirms nobody there.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void closingASeriesWhileRemovingAYesOnADateOverConfirmsNobodyThere() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long firstDateId = firstDateOverWithItsSeatHeld(series);
    Event toSave = agendaEventService.getEventById(series.getId());
    toSave.setOpen(false);

    saveWithout(toSave, testuser2Identity, false);

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(firstDateId).getOpen());
    assertEquals(EventAttendeeResponse.WAITING, responseOf(firstDateId, testuser3Identity));
    assertTrue(promotedIdentities().isEmpty());
  }

  /**
   * A date over gives nobody the seat a removal frees there, with no limit
   * left on it nor on its series either.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateOverWithNoLimitLeftGivesNobodyTheSeatARemovalFrees() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    long firstDateId = firstDateOverWithItsSeatHeld(series);
    patchAll(seriesId, "open", "false");
    patchAll(seriesId, "open", "true");
    assertEquals(0, capacityOf(seriesId));
    assertEquals(0, capacityOf(firstDateId));

    saveWithout(agendaEventService.getEventById(seriesId), testuser2Identity, false);

    assertEquals(EventAttendeeResponse.WAITING, responseOf(firstDateId, testuser3Identity));
    assertTrue(promotedIdentities().isEmpty());
  }

  /**
   * The move of dates under another series keeps a moved date's own limit
   * when the limit of the series it joins is below the Yes answers the date
   * holds, its dialog chaining the move between two other writes; a moved
   * date whose Yes answers fit follows the new limit.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateMovedUnderALowerLimitThanItsYesAnswersKeepsItsOwn() throws Exception {
    Event first = openSeries(2, testuser2Identity, testuser3Identity);
    long firstId = first.getId();
    List<ZonedDateTime> dates = occurrenceIds(first);
    answerDate(firstId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(firstId, dates.get(2), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(firstId, dates.get(2), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long fittingDateId = agendaEventService.getExceptionalOccurrenceEvent(firstId, dates.get(1)).getId();
    long fullDateId = agendaEventService.getExceptionalOccurrenceEvent(firstId, dates.get(2)).getId();
    Event continuation = agendaEventService.getEventById(firstId).clone();
    continuation.setId(0);
    continuation.setStart(dates.get(1));
    continuation.setEnd(dates.get(1).plusHours(1));
    continuation.setCapacity(1);
    Event second = createEvent(continuation, id(testuser1Identity), testuser2Identity, testuser3Identity);

    agendaEventService.moveExceptionalOccurrences(firstId, second.getId(), dates.get(1), id(testuser1Identity));

    assertEquals(second.getId(), agendaEventService.getEventById(fullDateId).getParentId());
    assertEquals(2, capacityOf(fullDateId));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(fullDateId, testuser2Identity));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(fullDateId, testuser3Identity));
    assertEquals(1, capacityOf(fittingDateId));
  }

  /**
   * A full save lowering a series' limit while removing someone who holds a
   * Yes on a date individually modified gives that date the lower limit: the
   * attendee removed holds no seat once the save is done.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aLimitLoweredWhileRemovingAYesReachesADateOfTheSeries() throws Exception {
    Event series = openSeries(2, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    ZonedDateTime date = occurrenceIds(series).get(0);
    answerDate(seriesId, date, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(seriesId, date, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, date).getId();
    Event toSave = agendaEventService.getEventById(seriesId);
    toSave.setCapacity(1);

    saveWithout(toSave, testuser3Identity, false);

    assertEquals(1, capacityOf(dateId));
    assertNull(rowOf(dateId, testuser3Identity));
  }

  /**
   * The date of an all-day series is over at the end of its day in the
   * series' zone, west of UTC too: today's date still holds its floor.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void anAllDayDateIsNotOverBeforeTheEndOfItsDayWestOfUtc() throws Exception {
    ZoneId newYork = ZoneId.of("America/New_York");
    ZonedDateTime start = LocalDate.now(newYork).atStartOfDay(newYork);
    Event event = newEventInstance(start, start, true);
    event.setTimeZoneId(newYork);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(3);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(3);
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity, testuser3Identity, testuser4Identity);
    long seriesId = series.getId();
    ZonedDateTime today = occurrenceIds(series).get(0);
    agendaEventService.saveEventExceptionalOccurrence(seriesId, today);
    long todayId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, today).getId();
    for (Identity identity : List.of(testuser2Identity, testuser3Identity, testuser4Identity)) {
      setAnswer(todayId, identity, null, null, EventAttendeeResponse.ACCEPTED);
    }

    // Named by its day in New York, the day of its UTC midnight id
    assertBlocks(start.toLocalDate(), () -> patchAll(seriesId, CAPACITY, "2"));
  }

  /**
   * A date whose row was made before its series moved to a later time of day
   * is over at the series' new time, not at the time its id keeps: today's
   * date, at a time still to come, holds its floor.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aDateIsOverAtTheTimeOfDayOfItsSeries() throws Exception {
    // A zone where it is noon now: 09:00 is over today there, 15:00 is not
    ZonedDateTime nowUtc = ZonedDateTime.now(ZoneOffset.UTC);
    ZoneId zone = ZoneOffset.ofHours(12 - nowUtc.getHour());
    LocalDate today = nowUtc.withZoneSameInstant(zone).toLocalDate();
    ZonedDateTime start = today.atTime(9, 0).atZone(zone);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(zone);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(3);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(3);
    Event series = createEvent(event, id(testuser1Identity), testuser2Identity, testuser3Identity, testuser4Identity);
    long seriesId = series.getId();
    ZonedDateTime todayId = occurrenceIds(series).get(0);
    agendaEventService.saveEventExceptionalOccurrence(seriesId, todayId);
    long todayRowId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, todayId).getId();
    List<Identity> accepting = List.of(testuser2Identity, testuser3Identity, testuser4Identity);
    accepting.forEach(identity -> setAnswer(todayRowId, identity, null, null, EventAttendeeResponse.ACCEPTED));
    Event moved = agendaEventService.getEventById(seriesId);
    moved.setStart(today.atTime(15, 0).atZone(zone));
    moved.setEnd(today.atTime(16, 0).atZone(zone));
    save(moved, false);
    assertEquals("today's row is kept", todayRowId, agendaEventService.getExceptionalOccurrenceEvent(seriesId, todayId).getId());
    accepting.forEach(identity -> setAnswer(todayRowId, identity, null, null, EventAttendeeResponse.ACCEPTED));

    assertBlocks(today, () -> patchAll(seriesId, CAPACITY, "2"));
  }

  /**
   * A date moved from a day over to a day still to come is over at its own
   * dates: it holds its floor.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aDateMovedFromADayOverToADayToComeHoldsItsFloor() throws Exception {
    Event series = pastSeries(3, testuser2Identity, testuser3Identity, testuser4Identity);
    long seriesId = series.getId();
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    agendaEventService.saveEventExceptionalOccurrence(seriesId, firstDate);
    long rowId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, firstDate).getId();
    Event row = agendaEventService.getEventById(rowId);
    ZonedDateTime comingStart = LocalDate.now(ZoneOffset.UTC).plusDays(20).atTime(9, 0).atZone(ZoneOffset.UTC);
    row.setStart(comingStart);
    row.setEnd(comingStart.plusHours(1));
    save(row, false);
    assertTrue(agendaEventService.getEventById(rowId).getOccurrence().isDatesModified());
    for (Identity identity : List.of(testuser2Identity, testuser3Identity, testuser4Identity)) {
      setAnswer(rowId, identity, null, null, EventAttendeeResponse.ACCEPTED);
    }

    // Named by the day it was moved to, not the day over its id keeps
    assertBlocks(comingStart.toLocalDate(), () -> patchAll(seriesId, CAPACITY, "2"));
  }

  /**
   * A limit of a series below the Yes answers of a date individually modified
   * and of a later date of its rule is refused naming the first of the two
   * (D16).
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aSeriesLimitRefusedNamesAnEarlierModifiedDateFirst() throws Exception {
    Event series = openSeries(3, testuser2Identity, testuser3Identity, testuser4Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    for (Identity identity : List.of(testuser2Identity, testuser3Identity, testuser4Identity)) {
      answerDate(seriesId, dates.get(0), identity, EventAttendeeResponse.ACCEPTED);
      agendaEventAttendeeService.sendUpcomingEventResponse(seriesId, dates.get(2), id(identity), EventAttendeeResponse.ACCEPTED, true);
    }

    assertBlocks(dates.get(0).toLocalDate(), () -> patchAll(seriesId, CAPACITY, "2"));
  }

  /**
   * A limit of a series below the Yes answers of a date of its rule and of a
   * later date individually modified is refused naming the first of the two
   * (D16).
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aSeriesLimitRefusedNamesAnEarlierDateOfItsRuleFirst() throws Exception {
    Event series = openSeries(3, testuser2Identity, testuser3Identity, testuser4Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    for (Identity identity : List.of(testuser2Identity, testuser3Identity, testuser4Identity)) {
      answerDate(seriesId, dates.get(2), identity, EventAttendeeResponse.ACCEPTED);
      agendaEventAttendeeService.sendUpcomingEventResponse(seriesId, dates.get(1), id(identity), EventAttendeeResponse.ACCEPTED, true);
    }

    assertBlocks(dates.get(1).toLocalDate(), () -> patchAll(seriesId, CAPACITY, "2"));
  }

  /**
   * A date of a series that blocks its limit is named by its day in the
   * series' zone: east of UTC, a date at 08:00 is on the day after its UTC
   * one.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void aSeriesLimitRefusedNamesItsDateByItsDayInTheSeriesZone() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZonedDateTime start = LocalDate.now(tokyo).plusDays(60).atTime(8, 0).atZone(tokyo);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(tokyo);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(3);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(2);
    long seriesId = createEvent(event, id(testuser1Identity), testuser2Identity, testuser3Identity).getId();
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertBlocks(start.toLocalDate(), () -> patchAll(seriesId, CAPACITY, "1"));
  }

  /**
   * A date of an all-day series that blocks its limit is named by its own day,
   * west of UTC too, not by the day before its UTC midnight id falls on there.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void anAllDaySeriesLimitRefusedNamesItsDateByItsDayWestOfUtc() throws Exception {
    ZoneId newYork = ZoneId.of("America/New_York");
    ZonedDateTime start = LocalDate.now(newYork).plusDays(60).atStartOfDay(newYork);
    Event event = newEventInstance(start, start, true);
    event.setTimeZoneId(newYork);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(3);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(2);
    long seriesId = createEvent(event, id(testuser1Identity), testuser2Identity, testuser3Identity).getId();
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertBlocks(start.toLocalDate(), () -> patchAll(seriesId, CAPACITY, "1"));
  }

  /**
   * A save that closes a series and gives it more dates counts every answer
   * waiting on it: the closing would confirm them at the dates the new rule
   * gives, though every date the stored rule gives is over.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void closingASeriesWhileGivingItMoreDatesCountsEveryWaitingAnswer() throws Exception {
    Event series = openSeries(LocalDate.now(ZoneOffset.UTC).minusDays(4), 3, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    setAnswer(seriesId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(seriesId, testuser3Identity, null, null, EventAttendeeResponse.WAITING);
    Event toSave = agendaEventService.getEventById(seriesId);
    toSave.setOpen(false);
    toSave.getRecurrence().setCount(10);

    assertCode("agenda.capacity.closeNeedsConfirmation",
               assertThrows(IllegalArgumentException.class, () -> save(toSave, false)));
  }

  /**
   * A save that closes a series and makes it an event of no series counts
   * every answer waiting on it: the closing would confirm them on the event,
   * though every date of the stored rule is over.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void closingASeriesWhileRemovingItsRuleCountsEveryWaitingAnswer() throws Exception {
    Event series = openSeries(LocalDate.now(ZoneOffset.UTC).minusDays(4), 3, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    setAnswer(seriesId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(seriesId, testuser3Identity, null, null, EventAttendeeResponse.WAITING);
    Event toSave = agendaEventService.getEventById(seriesId);
    toSave.setOpen(false);
    toSave.setRecurrence(null);

    assertCode("agenda.capacity.closeNeedsConfirmation",
               assertThrows(IllegalArgumentException.class, () -> save(toSave, false)));
  }

  /**
   * Closing a series asks no confirmation for an answer waiting on dates that
   * are all over: the closing would confirm nobody.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void closingASeriesAsksNoConfirmationForAnAnswerWaitingOnDatesOver() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    setAnswer(seriesId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(seriesId, testuser3Identity, null, dates.get(2), EventAttendeeResponse.WAITING);

    patch(seriesId, "open", "false");

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(seriesId).getOpen());
  }

  /**
   * A full save of a series that closes it counts the waiting lists of its
   * dates individually modified, as a patch of every date does.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void closingASeriesThroughAFullSaveCountsTheWaitingListsOfItsDates() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    answerDate(seriesId, firstDate, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(seriesId, firstDate, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, firstDate).getId();
    Event toSave = agendaEventService.getEventById(seriesId);
    toSave.setOpen(false);

    assertCode("agenda.capacity.closeNeedsConfirmation",
               assertThrows(IllegalArgumentException.class, () -> save(toSave.clone(), false)));
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(dateId).getOpen());

    save(toSave.clone(), true);

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(dateId).getOpen());
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser3Identity));
  }

  /**
   * Closing an event while removing the attendees waiting for it asks no
   * confirmation: the closing confirms nobody.
   *
   * @throws Exception when the save fails
   */
  @Test
  public void closingAnEventWhileRemovingItsWaitingListAsksNoConfirmation() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    Event toSave = agendaEventService.getEventById(eventId);
    toSave.setOpen(false);
    List<EventAttendee> withoutTestuser3 = agendaEventAttendeeService.getEventAttendees(eventId)
                                                                     .getEventAttendees()
                                                                     .stream()
                                                                     .filter(attendee -> attendee.getIdentityId() != id(testuser3Identity))
                                                                     .collect(Collectors.toList());

    agendaEventService.updateEvent(toSave,
                                   withoutTestuser3,
                                   agendaEventConferenceService.getEventConferences(eventId),
                                   agendaEventReminderService.getEventReminders(eventId),
                                   null,
                                   null,
                                   false,
                                   false,
                                   id(testuser1Identity));

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertNull(rowOf(eventId, testuser3Identity));
  }

  /**
   * A raised series' limit gives the seats it adds on a date individually
   * modified to that date's waiting list.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aRaisedSeriesLimitConfirmsTheWaitingListsOfItsDates() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    answerDate(seriesId, firstDate, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(seriesId, firstDate, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, firstDate).getId();
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
    BROADCASTS.clear();

    patchAll(seriesId, CAPACITY, "2");

    assertEquals(2, capacityOf(dateId));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser3Identity));
    assertEquals(List.of(id(testuser3Identity)), promotedIdentities());
  }

  /**
   * A cancelled date keeps its seats but is not one the series carries:
   * filled, it reports on its own, the notification then dropping it, and
   * leaves the series' mark to the dates the series carries.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aCancelledDateFilledReportsOnItsOwn() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long cancelledId = cancelDate(seriesId, dates.get(1));
    assertEquals(1, capacityOf(cancelledId));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    List<Broadcast> reached = broadcasts(Utils.POST_EVENT_CAPACITY_REACHED);
    assertEquals(2, reached.size());
    assertEquals(seriesId, reached.get(0).source());
    assertEquals(dates.get(0), reached.get(0).data());
    assertEquals(cancelledId, reached.get(1).source());
    assertEquals(dates.get(1), reached.get(1).data());
    assertNotNull(agendaEventService.getEventById(cancelledId).getCapacityReachedDate());
  }

  /**
   * A date holding the series' limit, reported through the series and then
   * moved on its own, starts its answers over, and the series' report over
   * with them (D19): the answer filling it again reports the series again.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aMovedDateReportedThroughTheSeriesStartsTheSeriesReportOver() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(1)).getId();
    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
    BROADCASTS.clear();

    Event date = agendaEventService.getEventById(dateId);
    date.setStart(date.getStart().plusHours(2));
    date.setEnd(date.getEnd().plusHours(2));
    save(date, false);

    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(dateId, testuser2Identity));
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertEquals(0, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());

    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser3Identity));
    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
  }

  /**
   * A date whose limit is its own, moved on its own, starts its own report
   * over and leaves the series' mark standing: it is not a date the series
   * reports.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aMovedDateWithALimitOfItsOwnLeavesTheSeriesMark() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(1)).getId();
    patch(dateId, CAPACITY, "2");
    answerDate(seriesId, dates.get(0), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    BROADCASTS.clear();

    Event date = agendaEventService.getEventById(dateId);
    date.setStart(date.getStart().plusHours(2));
    date.setEnd(date.getEnd().plusHours(2));
    save(date, false);

    assertEquals(EventAttendeeResponse.NEEDS_ACTION, responseOf(dateId, testuser3Identity));
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertEquals(0, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
  }

  /**
   * A date holding the series' limit that nobody answered, moved on its own
   * while the series is marked, starts the series' report over as well: the
   * series' mark is what its answers starting over clear (D19), and the
   * answer then filling the date reports the series.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aMovedDateNobodyAnsweredStartsTheSeriesReportOver() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(0), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(1)).getId();
    assertNotEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser2Identity));
    assertNotEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser3Identity));
    BROADCASTS.clear();

    Event date = agendaEventService.getEventById(dateId);
    date.setStart(date.getStart().plusHours(2));
    date.setEnd(date.getEnd().plusHours(2));
    save(date, false);

    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertEquals(0, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());

    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
  }

  /**
   * A Yes given on a stored date already over that holds the series' limit
   * (the API accepts it, the UI does not offer it) decides a seat there and
   * reports the date on its own, as a date the series does not report (D21):
   * the series' mark stays clear, and the answer then filling the first date
   * the series carries reports the series.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aYesOnADateOverHoldingTheSeriesLimitReportsTheDateOnItsOwn() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    BROADCASTS.clear();

    answerDate(seriesId, dates.get(0), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    long dateOverId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(0)).getId();
    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(dateOverId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
    assertNotNull(agendaEventService.getEventById(dateOverId).getCapacityReachedDate());
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(2, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(1).source());
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
  }

  /**
   * A stored date already over that holds the series' limit is not one the
   * series reports (D21): its answers starting over, as they do when it is
   * moved on its own, leave the series' mark standing, and the answer then
   * filling a date the series carries reports nothing.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateOverMovedOnItsOwnLeavesTheSeriesMark() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(4), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    long dateOverId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(0)).getId();
    BROADCASTS.clear();

    Event dateOver = agendaEventService.getEventById(dateOverId);
    dateOver.setStart(dateOver.getStart().plusHours(2));
    dateOver.setEnd(dateOver.getEnd().plusHours(2));
    save(dateOver, false);

    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());

    answerDate(seriesId, dates.get(5), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertTrue(broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).isEmpty());
  }

  /**
   * A date holding the series' limit saved on its own for the first time, a
   * row created for it with a title of its own, starts its answers over, the
   * creation's broadcast resetting the row it takes over
   * (AgendaReplyOnSaveListener), and the series' report with them (D19): the
   * answer then filling the date reports the series.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateSavedOnItsOwnForTheFirstTimeStartsTheSeriesReportOver() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 3, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answerDate(seriesId, dates.get(0), testuser2Identity, EventAttendeeResponse.ACCEPTED);
    assertNotNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertNull(agendaEventService.getExceptionalOccurrenceEvent(seriesId, dates.get(1)));
    BROADCASTS.clear();

    Event date = agendaEventService.getEventOccurrence(seriesId, dates.get(1), ZoneOffset.UTC, id(testuser1Identity));
    date.setId(0);
    date.setSummary("A title of its own");
    long dateId = agendaEventService.createEvent(date,
                                                 Collections.emptyList(),
                                                 null,
                                                 null,
                                                 null,
                                                 null,
                                                 false,
                                                 id(testuser1Identity))
                                    .getId();

    assertEquals(1, capacityOf(dateId));
    assertNull(agendaEventService.getEventById(seriesId).getCapacityReachedDate());
    assertEquals(0, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());

    answerDate(seriesId, dates.get(1), testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser3Identity));
    assertEquals(1, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).size());
    assertEquals(seriesId, broadcasts(Utils.POST_EVENT_CAPACITY_REACHED).get(0).source());
  }

  /**
   * A raised series' limit that reaches the waiting list of its first date,
   * individually modified and cancelled, reports the promotion from the
   * series' first date that is not cancelled.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aRaisedSeriesLimitReportsNoPromotionFromACancelledDate() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = cancelDate(seriesId, dates.get(0));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
    BROADCASTS.clear();

    patchAll(seriesId, CAPACITY, "2");

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals(seriesId, promotion.attendee().getEventId());
    assertEquals(dates.get(1), promotion.attendee().getFromOccurrenceId());
    assertEquals(1, promotion.otherDates());
  }

  /**
   * A No to every date that gives a waiting attendee a seat on the series
   * and on its first date, individually modified and cancelled, reports the
   * promotion from the series' first date that is not cancelled: a cancelled
   * date is no seat to tell, and a promotion told from it reaches nobody.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aPromotionIsNotReportedFromACancelledFirstDate() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = cancelDate(seriesId, dates.get(0));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals(id(testuser3Identity), promotion.attendee().getIdentityId());
    assertEquals("reported from the series", seriesId, promotion.attendee().getEventId());
    assertEquals(dates.get(1), promotion.attendee().getFromOccurrenceId());
    assertEquals("the last date, the cancelled one not counted", 1, promotion.otherDates());
    assertFalse(promotion.openEnded());
  }

  /**
   * A No to every date that gives a waiting attendee a seat on the series
   * and on a date in between, individually modified and cancelled, does not
   * count that date among the other dates promoted.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aCancelledDateIsNotCountedAmongTheOtherDatesPromoted() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = cancelDate(seriesId, dates.get(1));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals(seriesId, promotion.attendee().getEventId());
    assertEquals(dates.get(0), promotion.attendee().getFromOccurrenceId());
    assertEquals("the last date, the cancelled one not counted", 1, promotion.otherDates());
  }

  /**
   * A No to every date of a series cancelled alone, its date individually
   * modified still confirmed, that gives a waiting attendee a seat on both,
   * reports the promotion from that date only: the dates the cancelled series
   * carries are no seat to tell.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aPromotionIsNotReportedFromACancelledSeries() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(1)).getId();
    patch(seriesId, "status", EventStatus.CANCELLED.name());
    assertEquals(EventStatus.CONFIRMED, agendaEventService.getEventById(dateId).getStatus());
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals(id(testuser3Identity), promotion.attendee().getIdentityId());
    assertEquals("reported from the date still confirmed", dateId, promotion.attendee().getEventId());
    assertEquals("the dates of the cancelled series not counted", 0, promotion.otherDates());
  }

  /**
   * A seat given up on a cancelled event of no series reports no promotion:
   * the event is no seat to tell, nor to show.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void aPromotionOnACancelledEventIsNotReported() throws Exception {
    long eventId = openEvent(1, testuser2Identity, testuser3Identity).getId();
    answer(eventId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(eventId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    patch(eventId, "status", EventStatus.CANCELLED.name());
    assertEquals(EventAttendeeResponse.WAITING, responseOf(eventId, testuser3Identity));
    BROADCASTS.clear();

    answer(eventId, testuser2Identity, EventAttendeeResponse.DECLINED);

    assertEquals(List.of(), promotedIdentities());
  }

  /**
   * A promotion over a series and its first date, moved after its fourth, is
   * reported from the series' second date, which starts first: a date moved
   * is ordered at its own start, not at the day its id keeps.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aPromotionIsReportedFromTheDateThatStartsFirstNotFromADateMovedLater() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long rowId = moveDate(seriesId, dates.get(0), dates.get(3).plusHours(5));
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(rowId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals("reported from the series", seriesId, promotion.attendee().getEventId());
    assertEquals(dates.get(1), promotion.attendee().getFromOccurrenceId());
    assertEquals("the three later dates of the series and the date moved", 4, promotion.otherDates());
  }

  /**
   * A promotion over a series and its last date, moved before its first, is
   * reported from the date moved, which starts first.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aPromotionIsReportedFromADateMovedBeforeTheOthers() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long rowId = moveDate(seriesId, dates.get(4), dates.get(0).minusHours(5));
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(rowId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals("reported from the date moved", rowId, promotion.attendee().getEventId());
    assertEquals("the four dates of the series", 4, promotion.otherDates());
  }

  /**
   * On a series at 20:00 in Phoenix, a zone seven hours behind UTC all year,
   * a date made all day on the day of the series' second date starts after
   * the first one, whose id falls on that day in UTC three hours after its
   * midnight: the promotion is reported from the series' first date.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateMadeAllDayIsOrderedAtTheStartOfItsDayInTheSeriesZone() throws Exception {
    ZoneId phoenix = ZoneId.of("America/Phoenix");
    ZonedDateTime start = LocalDate.now(phoenix).plusDays(60).atTime(20, 0).atZone(phoenix);
    Event series = openSeries(start, false, 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    LocalDate secondDay = start.toLocalDate().plusDays(1);
    long rowId = moveDate(seriesId, dates.get(4), secondDay.atStartOfDay(phoenix), true);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(rowId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals("reported from the series", seriesId, promotion.attendee().getEventId());
    assertEquals(dates.get(0), promotion.attendee().getFromOccurrenceId());
    assertEquals(4, promotion.otherDates());
  }

  /**
   * On an all-day series in Tokyo, its first date made a timed date at 05:00
   * on the day of its second date starts after that day's midnight in Tokyo,
   * though before that date's id, the midnight UTC of its day: the promotion
   * is reported from the series' second date.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void anAllDayDateIsOrderedAtTheStartOfItsDayInTheSeriesZone() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZonedDateTime start = LocalDate.now(tokyo).plusDays(60).atStartOfDay(tokyo);
    Event series = openSeries(start, true, 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    LocalDate secondDay = start.toLocalDate().plusDays(1);
    long rowId = moveDate(seriesId, dates.get(0), secondDay.atTime(5, 0).atZone(tokyo), false);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(rowId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals("reported from the series", seriesId, promotion.attendee().getEventId());
    assertEquals(dates.get(1), promotion.attendee().getFromOccurrenceId());
    assertEquals(4, promotion.otherDates());
  }

  /**
   * On an all-day series in Tokyo, the row of its second date stored before
   * TASK-39591, at the local midnight of that day, which is the UTC day
   * before, is ordered at its day in Tokyo: the first date, made a timed
   * date at 10:00 on its own day, starts before it, and the promotion is
   * reported from that date.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aLegacyAllDayRowIsOrderedAtItsDateInTheSeriesZone() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZonedDateTime start = LocalDate.now(tokyo).plusDays(60).atStartOfDay(tokyo);
    Event series = openSeries(start, true, 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long legacyRowId = legacyRow(series, dates.get(1).toLocalDate().atStartOfDay(tokyo), testuser2Identity, testuser3Identity);
    long movedRowId = moveDate(seriesId, dates.get(0), start.toLocalDate().atTime(10, 0).atZone(tokyo), false);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(legacyRowId, testuser3Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(movedRowId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals("reported from the first date, made timed", movedRowId, promotion.attendee().getEventId());
    assertEquals("the legacy row and the three dates of the series", 4, promotion.otherDates());
  }

  /**
   * The row of a date not moved, given a zone of its own, keeps the id its
   * series wrote in the series' zone: a row stored before TASK-39591 at its
   * Tokyo midnight, its zone then set to Paris, is still ordered at its day
   * in Tokyo, after the first date made a timed date on its own day.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aLegacyAllDayRowInAZoneOfItsOwnIsOrderedAtItsDateInTheSeriesZone() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZonedDateTime start = LocalDate.now(tokyo).plusDays(60).atStartOfDay(tokyo);
    Event series = openSeries(start, true, 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long legacyRowId = legacyRow(series, dates.get(1).toLocalDate().atStartOfDay(tokyo), testuser2Identity, testuser3Identity);
    patch(legacyRowId, "timeZoneId", "Europe/Paris");
    assertFalse(agendaEventService.getEventById(legacyRowId).getOccurrence().isDatesModified());
    long movedRowId = moveDate(seriesId, dates.get(0), start.toLocalDate().atTime(10, 0).atZone(tokyo), false);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.WAITING, responseOf(legacyRowId, testuser3Identity));
    BROADCASTS.clear();

    answer(seriesId, testuser2Identity, EventAttendeeResponse.DECLINED);

    EventAttendeePromotion promotion = onlyPromotion();
    assertEquals("reported from the first date, made timed", movedRowId, promotion.attendee().getEventId());
    assertEquals(4, promotion.otherDates());
  }

  /**
   * The first date an answer to a series leaves waiting, which the message
   * telling the attendee so opens, is the one that starts first: the series'
   * second date, not its first one moved after the fourth, though that one's
   * id names the earliest day.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateLeftWaitingIsTheOneThatStartsFirstNotADateMovedLater() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long movedRowId = moveDate(seriesId, dates.get(0), dates.get(3).plusHours(5));
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome =
                                 agendaEventAttendeeService.sendEventResponse(seriesId, null, id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);

    assertEquals(EventAttendeeResponse.WAITING, responseOf(movedRowId, testuser3Identity));
    assertEquals(dates.get(1), outcome.firstWaitingOccurrenceId());
  }

  /**
   * The first date an answer to a series leaves waiting is its last date,
   * moved before the first one: a moved date is at its own start.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateLeftWaitingIsADateMovedBeforeTheOthers() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    moveDate(seriesId, dates.get(4), dates.get(0).minusHours(5));
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome =
                                 agendaEventAttendeeService.sendEventResponse(seriesId, null, id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);

    assertEquals(dates.get(4), outcome.firstWaitingOccurrenceId());
  }

  /**
   * Among the dates stored on rows of their own an answer leaves waiting, the
   * first one is the one that starts first: the second date, not the first
   * one moved after the fourth, nor the series' third date.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateLeftWaitingAmongStoredDatesIsTheOneThatStartsFirst() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    moveDate(seriesId, dates.get(0), dates.get(3).plusHours(5));
    agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(1));
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome =
                                 agendaEventAttendeeService.sendEventResponse(seriesId, null, id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);

    assertEquals(dates.get(1), outcome.firstWaitingOccurrenceId());
  }

  /**
   * On an all-day series in Tokyo, its first date made a timed date at 05:00
   * on the day of its second date starts after that day's midnight in Tokyo,
   * though before that date's id, the midnight UTC of its day: the first
   * date an answer leaves waiting is the second one.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateLeftWaitingOnAnAllDaySeriesIsOrderedAtTheStartOfItsDay() throws Exception {
    ZoneId tokyo = ZoneId.of("Asia/Tokyo");
    ZonedDateTime start = LocalDate.now(tokyo).plusDays(60).atStartOfDay(tokyo);
    Event series = openSeries(start, true, 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    moveDate(seriesId, dates.get(0), start.toLocalDate().plusDays(1).atTime(5, 0).atZone(tokyo), false);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome =
                                 agendaEventAttendeeService.sendEventResponse(seriesId, null, id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);

    assertEquals(dates.get(1), outcome.firstWaitingOccurrenceId());
  }

  /**
   * On an all-day series in New York, its first date made a timed date at
   * 22:00 on its own day starts before the second date, though after that
   * date's id, the midnight UTC of its day, which is still the evening before
   * in New York: the first date an answer leaves waiting is the moved one.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateLeftWaitingOnAnAllDaySeriesIsOrderedAtTheStartOfItsDayWestOfUtc() throws Exception {
    ZoneId newYork = ZoneId.of("America/New_York");
    ZonedDateTime start = LocalDate.now(newYork).plusDays(60).atStartOfDay(newYork);
    Event series = openSeries(start, true, 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    moveDate(seriesId, dates.get(0), start.toLocalDate().atTime(22, 0).atZone(newYork), false);
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome =
                                 agendaEventAttendeeService.sendEventResponse(seriesId, null, id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);

    assertEquals(dates.get(0), outcome.firstWaitingOccurrenceId());
  }

  /**
   * An answer to every date of a series that gets a seat on each date the
   * series carries, a date stored on its own row being full, leaves that date
   * waiting alone: it is the first date left waiting, and the answer stored
   * on the series is a Yes.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateLeftWaitingIsAStoredDateWhenEveryDateOfTheSeriesHasASeat() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(1)).getId();
    answerDate(seriesId, dates.get(1), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome =
                                 agendaEventAttendeeService.sendEventResponse(seriesId, null, id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);

    assertEquals(EventAttendeeResponse.ACCEPTED, outcome.response());
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
    assertEquals(dates.get(1), outcome.firstWaitingOccurrenceId());
  }

  /**
   * An answer to a date and the following ones leaves waiting, first, the
   * date that starts first: the series' second date, not its first one moved
   * after the fourth, which the answer starts from.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateAnUpcomingAnswerLeavesWaitingIsTheOneThatStartsFirst() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    moveDate(seriesId, dates.get(0), dates.get(3).plusHours(5));
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome = agendaEventAttendeeService.sendUpcomingEventResponse(seriesId,
                                                                                        dates.get(0),
                                                                                        id(testuser3Identity),
                                                                                        EventAttendeeResponse.ACCEPTED,
                                                                                        true);

    assertEquals(dates.get(1), outcome.firstWaitingOccurrenceId());
  }

  /**
   * An answer to a date and the following ones that gets a seat on each date
   * the series carries from there, a later date stored on its own row being
   * full, leaves that date waiting alone: it is the first date left waiting.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateAnUpcomingAnswerLeavesWaitingIsAStoredDateWhenEveryDateOfTheSeriesHasASeat() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, dates.get(2)).getId();
    answerDate(seriesId, dates.get(2), testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome = agendaEventAttendeeService.sendUpcomingEventResponse(seriesId,
                                                                                        dates.get(1),
                                                                                        id(testuser3Identity),
                                                                                        EventAttendeeResponse.ACCEPTED,
                                                                                        true);

    assertEquals(EventAttendeeResponse.ACCEPTED, outcome.response());
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
    assertEquals(dates.get(2), outcome.firstWaitingOccurrenceId());
  }

  /**
   * An answer to a date and the following ones leaves waiting, first, the
   * series' last date, moved before the date answered from: among the dates
   * answered, it is the one that starts first.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateAnUpcomingAnswerLeavesWaitingIsADateMovedBeforeTheOthers() throws Exception {
    Event series = openSeries(LocalDate.now().plusDays(60), 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    long movedRowId = moveDate(seriesId, dates.get(4), dates.get(1).minusHours(5));
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome = agendaEventAttendeeService.sendUpcomingEventResponse(seriesId,
                                                                                        dates.get(1),
                                                                                        id(testuser3Identity),
                                                                                        EventAttendeeResponse.ACCEPTED,
                                                                                        true);

    assertEquals(EventAttendeeResponse.WAITING, responseOf(movedRowId, testuser3Identity));
    assertEquals(dates.get(4), outcome.firstWaitingOccurrenceId());
  }

  /**
   * On a series of several dates a day, the first date an answer leaves
   * waiting is the one that starts first on that day: the 10:00 date, not
   * the 09:00 one moved to 11:30 the same day.
   *
   * @throws Exception when an answer fails
   */
  @Test
  public void theFirstDateLeftWaitingIsTheOneThatStartsFirstOnTheSameDay() throws Exception {
    ZonedDateTime start = LocalDate.now().plusDays(60).atTime(9, 0).atZone(ZoneOffset.UTC);
    Event series = openHourlySeries(start, 5, 1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    List<ZonedDateTime> dates = occurrenceIds(series);
    assertEquals(start.plusHours(1), dates.get(1));
    moveDate(seriesId, dates.get(0), start.plusHours(2).plusMinutes(30));
    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);

    EventResponseOutcome outcome =
                                 agendaEventAttendeeService.sendEventResponse(seriesId, null, id(testuser3Identity), EventAttendeeResponse.ACCEPTED, true);

    assertEquals(dates.get(1), outcome.firstWaitingOccurrenceId());
  }

  /**
   * Closing a series counts the waiting lists of the dates individually
   * modified it closes: refused until confirmed, then each of them confirmed.
   *
   * @throws Exception when a write fails otherwise
   */
  @Test
  public void closingASeriesCountsTheWaitingListsOfItsDates() throws Exception {
    Event series = openSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    answerDate(seriesId, firstDate, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answerDate(seriesId, firstDate, testuser3Identity, EventAttendeeResponse.ACCEPTED);
    long dateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, firstDate).getId();

    assertCode("agenda.capacity.closeNeedsConfirmation",
               assertThrows(IllegalArgumentException.class, () -> patchAll(seriesId, "open", "false")));
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(dateId).getOpen());

    agendaEventService.updateEventFields(seriesId, getFields("open", "false"), true, false, true, id(testuser1Identity));

    assertEquals(Boolean.FALSE, agendaEventService.getEventById(dateId).getOpen());
    assertEquals(0, capacityOf(dateId));
    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser3Identity));
  }

  /**
   * A date of a series moved by its first save alone, the form's "this
   * occurrence only" on a date without a row of its own, is a moved date
   * from that save on: read at its own dates, it decides its seats there once
   * the date its rule gave it is over, instead of keeping every Yes copied.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateMovedByItsFirstSaveAloneDecidesItsSeatsAtItsOwnDates() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    Event date = agendaEventService.getEventOccurrence(seriesId, occurrenceIds(series).get(0), ZoneOffset.UTC, id(testuser1Identity));
    date.setId(0);
    date.setStart(date.getStart().plusDays(8));
    date.setEnd(date.getEnd().plusDays(8));
    long dateId = createAlone(date);
    assertTrue(agendaEventService.getEventById(dateId).getOccurrence().isDatesModified());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser2Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
  }

  /**
   * A date of a series saved alone for the first time at the dates its series
   * gives it carries no mark of moved dates, so a change of the series still
   * carries it along.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateSavedAloneAtItsSeriesDatesIsNotMarkedMoved() throws Exception {
    Event series = openSeries(1, testuser2Identity);
    Event date = agendaEventService.getEventOccurrence(series.getId(), occurrenceIds(series).get(1), ZoneOffset.UTC, id(testuser1Identity));
    date.setId(0);
    date.setSummary("The second date, named");
    long dateId = createAlone(date);

    assertFalse(agendaEventService.getEventById(dateId).getOccurrence().isDatesModified());
  }

  /**
   * The dates a series gives a date are read from its rule in the series'
   * zone, not from the UTC day of the date's identifier: on a series whose
   * local start falls on another UTC day, a date saved alone for the first
   * time at the dates its series gives it carries no mark of moved dates
   * either.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateSavedAloneAtItsSeriesDatesOnAnotherUtcDayIsNotMarkedMoved() throws Exception {
    LocalDate day = LocalDate.now().plusDays(60);
    for (ZonedDateTime start : List.of(day.atTime(8, 30).atZone(ZoneId.of("Asia/Tokyo")),
                                       day.atTime(20, 30).atZone(ZoneId.of("America/New_York")))) {
      Event series = openSeries(start, false, 3, 1, testuser2Identity);
      Event date = agendaEventService.getEventOccurrence(series.getId(), occurrenceIds(series).get(1), ZoneOffset.UTC, id(testuser1Identity));
      date.setId(0);
      date.setSummary("The second date, named");
      long dateId = createAlone(date);

      assertFalse(start.getZone().getId(), agendaEventService.getEventById(dateId).getOccurrence().isDatesModified());
    }
  }

  /**
   * A date of a daily series posted on a day its rule does not give (a form
   * opened before the series' days changed, or the API) is read, unmarked, at
   * the series' time of day on that day: it is marked when the dates posted
   * differ from that, so that it decides its seats at its own dates.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateMovedByItsFirstSaveAloneOnADayTheRuleDoesNotGiveIsMarkedMoved() throws Exception {
    Event series = pastSeries(1, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    Event date = agendaEventService.getEventById(seriesId).clone();
    date.setId(0);
    date.setParentId(seriesId);
    date.setRecurrence(null);
    date.setOccurrence(new EventOccurrence(firstDate.minusDays(5)));
    date.setStart(firstDate.plusDays(8));
    date.setEnd(firstDate.plusDays(8).plusHours(1));
    long dateId = createAlone(date);
    assertTrue(agendaEventService.getEventById(dateId).getOccurrence().isDatesModified());

    answer(seriesId, testuser2Identity, EventAttendeeResponse.ACCEPTED);
    answer(seriesId, testuser3Identity, EventAttendeeResponse.ACCEPTED);

    assertEquals(EventAttendeeResponse.ACCEPTED, responseOf(dateId, testuser2Identity));
    assertEquals(EventAttendeeResponse.WAITING, responseOf(dateId, testuser3Identity));
  }

  /**
   * A date posted under an identifier its series' rule gives no date for, at
   * the dates an unmarked row is read at, is left unmarked: the series' time
   * of day on the identifier's day on a daily series (a day the rule does not
   * give); its identifier for the series' length on a series with several
   * dates a day (any instant that is none of its dates), where a date posted
   * elsewhere is marked.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDatePostedWhereAnUnmarkedRowIsReadUnderAnIdentifierTheRuleDoesNotGiveIsNotMarkedMoved() throws Exception {
    Event daily = pastSeries(1, testuser2Identity);
    ZonedDateTime dayTheRuleDoesNotGive = occurrenceIds(daily).get(0).minusDays(5);
    Event atSeriesTime = dateOf(daily, dayTheRuleDoesNotGive, dayTheRuleDoesNotGive);
    assertFalse(agendaEventService.getEventById(createAlone(atSeriesTime)).getOccurrence().isDatesModified());

    Event hourly = openHourlySeries(LocalDate.now().plusDays(60).atTime(8, 0).atZone(ZoneOffset.UTC), 3, 1, testuser2Identity);
    // The dates are listed once: the listing drops a day's computed dates once
    // one of them has a row (isSameOccurrenceDate reads the UTC day)
    ZonedDateTime firstHourlyDate = occurrenceIds(hourly).get(0);
    ZonedDateTime betweenTwoDates = firstHourlyDate.plusMinutes(30);
    Event atItsIdentifier = dateOf(hourly, betweenTwoDates, betweenTwoDates);
    assertFalse(agendaEventService.getEventById(createAlone(atItsIdentifier)).getOccurrence().isDatesModified());
    Event elsewhere = dateOf(hourly, betweenTwoDates.plusHours(7), firstHourlyDate);
    assertTrue(agendaEventService.getEventById(createAlone(elsewhere)).getOccurrence().isDatesModified());
  }

  /**
   * A date of a series built as the form posts one, with the identifier given,
   * starting at the start given for the series' length.
   */
  private Event dateOf(Event series, ZonedDateTime occurrenceId, ZonedDateTime start) throws Exception {
    Event stored = agendaEventService.getEventById(series.getId());
    Event date = stored.clone();
    date.setId(0);
    date.setParentId(series.getId());
    date.setRecurrence(null);
    date.setOccurrence(new EventOccurrence(occurrenceId));
    date.setStart(start);
    date.setEnd(start.plus(java.time.Duration.between(stored.getStart(), stored.getEnd())));
    return date;
  }

  /**
   * A date of an all-day series saved alone for the first time is marked
   * moved only when its days change: posted as the form posts an all-day date
   * (its first and last days at midnight UTC) with a title only, it is left
   * unmarked, in New York and in Tokyo, over one day and over two; moved ten
   * days, it is marked; posted on a day the rule does not give, all day
   * there, it is left unmarked.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateOfAnAllDaySeriesSavedAloneIsMarkedMovedOnlyWhenItsDaysChange() throws Exception {
    for (ZoneId zone : Arrays.asList(ZoneId.of("America/New_York"), ZoneId.of("Asia/Tokyo"))) {
      for (int days = 1; days <= 2; days++) {
        String where = zone.getId() + " over " + days;
        Event series = openAllDaySeries(LocalDate.now().plusDays(60), zone, days, 1, testuser2Identity);
        List<ZonedDateTime> dates = occurrenceIds(series);
        LocalDate itsDay = Utils.getSeriesDate(series, dates.get(0));
        long atItsDays = createAlone(allDayDateOf(series, dates.get(0), itsDay, itsDay.plusDays(days - 1L)));
        assertFalse(where, agendaEventService.getEventById(atItsDays).getOccurrence().isDatesModified());
        LocalDate tenDaysLater = Utils.getSeriesDate(series, dates.get(1)).plusDays(10);
        long moved = createAlone(allDayDateOf(series, dates.get(1), tenDaysLater, tenDaysLater.plusDays(days - 1L)));
        assertTrue(where, agendaEventService.getEventById(moved).getOccurrence().isDatesModified());
        ZonedDateTime dayTheRuleDoesNotGive = dates.get(0).minusDays(5);
        LocalDate thatDay = Utils.getSeriesDate(series, dayTheRuleDoesNotGive);
        long allDayThere = createAlone(allDayDateOf(series, dayTheRuleDoesNotGive, thatDay, thatDay.plusDays(days - 1L)));
        assertFalse(where, agendaEventService.getEventById(allDayThere).getOccurrence().isDatesModified());
      }
    }
  }

  /**
   * An open all-day series of the zone given, from the day given over the
   * days given, with a capacity, as the form creates one: its days at midnight
   * UTC.
   */
  private Event openAllDaySeries(LocalDate firstDay, ZoneId zone, int days, int capacity, Identity... attendees) throws Exception {
    ZonedDateTime start = firstDay.atStartOfDay(ZoneOffset.UTC);
    Event event = newEventInstance(start, start.plusDays(days - 1L), true);
    event.setTimeZoneId(zone);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(4);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(capacity);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * A date of an all-day series built as the REST layer builds a posted
   * all-day date: its first and last days at midnight UTC, with a title of
   * its own.
   */
  private Event allDayDateOf(Event series, ZonedDateTime occurrenceId, LocalDate firstDay, LocalDate lastDay) {
    Event stored = agendaEventService.getEventById(series.getId());
    Event date = stored.clone();
    date.setId(0);
    date.setParentId(series.getId());
    date.setRecurrence(null);
    date.setOccurrence(new EventOccurrence(occurrenceId));
    date.setStart(firstDay.atStartOfDay(ZoneOffset.UTC));
    date.setEnd(lastDay.atStartOfDay(ZoneOffset.UTC));
    date.setSummary("named");
    return date;
  }

  /**
   * A date moved by its first save alone keeps its dates when its series
   * moves: the propagation reads the mark the save set.
   *
   * @throws Exception when a write fails
   */
  @Test
  public void aDateMovedByItsFirstSaveAloneKeepsItsDatesWhenTheSeriesMoves() throws Exception {
    Event series = openSeries(1, testuser2Identity);
    Event date = agendaEventService.getEventOccurrence(series.getId(), occurrenceIds(series).get(1), ZoneOffset.UTC, id(testuser1Identity));
    date.setId(0);
    ZonedDateTime movedStart = date.getStart().plusHours(3);
    date.setStart(movedStart);
    date.setEnd(movedStart.plusHours(1));
    long dateId = createAlone(date);

    Event movedSeries = agendaEventService.getEventById(series.getId());
    movedSeries.setStart(movedSeries.getStart().plusHours(1));
    movedSeries.setEnd(movedSeries.getEnd().plusHours(1));
    save(movedSeries, false);

    assertEquals(movedStart.toInstant(), agendaEventService.getEventById(dateId).getStart().toInstant());
  }

  /** Creates a date of a series on its own row, as the form's "this occurrence only" does. */
  private long createAlone(Event date) throws Exception {
    return agendaEventService.createEvent(date, Collections.emptyList(), null, null, null, null, false, id(testuser1Identity)).getId();
  }

  /**
   * An event of no series in the space's calendar, starting in sixty days,
   * open, with the limit given: not created yet.
   */
  private Event spaceEvent(int capacity) {
    ZonedDateTime start = ZonedDateTime.now().plusDays(60).withNano(0).withSecond(0).withMinute(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    event.setRecurrence(null);
    event.setOpen(true);
    event.setCapacity(capacity);
    return event;
  }

  private Event openEvent(int capacity, Identity... attendees) throws Exception {
    return createEvent(spaceEvent(capacity), id(testuser1Identity), attendees);
  }

  /**
   * A daily series of three dates at 09:00 UTC starting in sixty days, in the
   * space's calendar, open, with the limit given.
   */
  private Event openSeries(int capacity, Identity... attendees) throws Exception {
    return openSeries(LocalDate.now().plusDays(60), 3, capacity, attendees);
  }

  /**
   * A daily series of ten dates at 09:00 UTC whose first date was three days
   * ago, in the space's calendar, open, with the limit given: its first three
   * dates are over.
   */
  private Event pastSeries(int capacity, Identity... attendees) throws Exception {
    return openSeries(LocalDate.now(ZoneOffset.UTC).minusDays(3), 10, capacity, attendees);
  }

  private Event openSeries(LocalDate firstDate, int count, int capacity, Identity... attendees) throws Exception {
    return openSeries(firstDate.atTime(9, 0).atZone(ZoneOffset.UTC), false, count, capacity, attendees);
  }

  /**
   * An hourly series of dates an hour long from the start given, in its
   * zone, in the space's calendar, open, with the limit given.
   */
  private Event openHourlySeries(ZonedDateTime start, int count, int capacity, Identity... attendees) throws Exception {
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(start.getZone());
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.HOURLY);
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(count);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(capacity);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * A daily series from the start given, in its zone, an hour long or all
   * day, in the space's calendar, open, with the limit given.
   */
  private Event openSeries(ZonedDateTime start,
                           boolean allDay,
                           int count,
                           int capacity,
                           Identity... attendees) throws Exception {
    Event event = newEventInstance(start, allDay ? start : start.plusHours(1), allDay);
    event.setTimeZoneId(start.getZone());
    event.getRecurrence().setUntil(null);
    event.getRecurrence().setCount(count);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(capacity);
    return createEvent(event, id(testuser1Identity), attendees);
  }

  /**
   * Replaces an attendee's rows on an event with one row answering the
   * response given over the range given, two hours ago, through the storage:
   * as answers given while the dates were still to come left them.
   */
  private void setAnswer(long eventId,
                         Identity identity,
                         ZonedDateTime from,
                         ZonedDateTime until,
                         EventAttendeeResponse response) {
    AgendaEventAttendeeStorage attendeeStorage = container.getComponentInstanceOfType(AgendaEventAttendeeStorage.class);
    agendaEventAttendeeService.getEventAttendees(eventId)
                              .getEventAttendees(id(identity))
                              .forEach(row -> attendeeStorage.removeEventAttendee(row.getId()));
    attendeeStorage.saveEventAttendee(new EventAttendee(0,
                                                        eventId,
                                                        id(identity),
                                                        from,
                                                        until,
                                                        response,
                                                        ZonedDateTime.now(ZoneOffset.UTC).minusHours(2)),
                                      eventId);
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

  private int capacityOf(long eventId) {
    Integer capacity = agendaEventService.getEventById(eventId).getCapacity();
    return capacity == null ? 0 : capacity;
  }

  private void patch(long eventId, String field, String value) throws Exception {
    agendaEventService.updateEventFields(eventId, getFields(field, value), false, false, false, id(testuser1Identity));
  }

  private void patchConfirmed(long eventId, String field, String value) throws Exception {
    agendaEventService.updateEventFields(eventId, getFields(field, value), false, false, true, id(testuser1Identity));
  }

  private void patchAll(long seriesId, String field, String value) throws Exception {
    agendaEventService.updateEventFields(seriesId, getFields(field, value), true, false, false, id(testuser1Identity));
  }

  /**
   * Stores a date of a series on its own row and cancels it, as the MCP tool
   * cancels one date.
   *
   * @return the identifier of the date's row
   */
  private long cancelDate(long seriesId, ZonedDateTime date) throws Exception {
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, date).getId();
    patch(dateId, "status", EventStatus.CANCELLED.name());
    return dateId;
  }

  /**
   * Stores a date of a series on a row of its own through the storage, with
   * the occurrence id given as stored, as the rows made before TASK-39591
   * were, the attendees given needing action on it.
   *
   * @return the identifier of the date's row
   */
  private long legacyRow(Event series, ZonedDateTime storedOccurrenceId, Identity... attendees) throws Exception {
    Event row = agendaEventStorage.getEventById(series.getId());
    row.setId(0);
    row.setParentId(series.getId());
    row.setRecurrence(null);
    row.setOccurrence(new EventOccurrence(storedOccurrenceId.withZoneSameInstant(ZoneOffset.UTC), true, false));
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
    return storedRow.getId();
  }

  /**
   * Stores a date of a series on its own row and moves it to the start given,
   * for an hour.
   *
   * @return the identifier of the date's row
   */
  private long moveDate(long seriesId, ZonedDateTime date, ZonedDateTime start) throws Exception {
    return moveDate(seriesId, date, start, false);
  }

  /**
   * Stores a date of a series on its own row and moves it to the start given,
   * for an hour, or all day from the start of its day.
   *
   * @return the identifier of the date's row
   */
  private long moveDate(long seriesId, ZonedDateTime date, ZonedDateTime start, boolean allDay) throws Exception {
    long dateId = agendaEventService.saveEventExceptionalOccurrence(seriesId, date).getId();
    Event row = agendaEventService.getEventById(dateId);
    row.setAllDay(allDay);
    row.setStart(start);
    row.setEnd(allDay ? start : start.plusHours(1));
    save(row, false);
    assertTrue(agendaEventService.getEventById(dateId).getOccurrence().isDatesModified());
    return dateId;
  }

  /** A full save of the event by its creator, one of its attendees removed. */
  private Event saveWithout(Event event, Identity removed, boolean confirmClose) throws Exception {
    long eventId = event.getId();
    return agendaEventService.updateEvent(event,
                                          agendaEventAttendeeService.getEventAttendees(eventId)
                                                                    .getEventAttendees()
                                                                    .stream()
                                                                    .filter(attendee -> attendee.getIdentityId() != id(removed))
                                                                    .collect(Collectors.toList()),
                                          agendaEventConferenceService.getEventConferences(eventId),
                                          agendaEventReminderService.getEventReminders(eventId),
                                          null,
                                          null,
                                          false,
                                          confirmClose,
                                          id(testuser1Identity));
  }

  /**
   * Materialises the first date of a series that is over, testuser2 holding
   * its one seat with a Yes and testuser3 waiting, as answers given while the
   * date was still to come left them.
   *
   * @return the id of the date's row
   */
  private long firstDateOverWithItsSeatHeld(Event series) throws Exception {
    long seriesId = series.getId();
    ZonedDateTime firstDate = occurrenceIds(series).get(0);
    agendaEventService.saveEventExceptionalOccurrence(seriesId, firstDate);
    long firstDateId = agendaEventService.getExceptionalOccurrenceEvent(seriesId, firstDate).getId();
    setAnswer(firstDateId, testuser2Identity, null, null, EventAttendeeResponse.ACCEPTED);
    setAnswer(firstDateId, testuser3Identity, null, null, EventAttendeeResponse.WAITING);
    return firstDateId;
  }

  /** A full save of the event by its creator, its attendees kept. */
  private Event save(Event event, boolean confirmClose) throws Exception {
    long eventId = event.getId();
    return agendaEventService.updateEvent(event,
                                          agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees(),
                                          agendaEventConferenceService.getEventConferences(eventId),
                                          agendaEventReminderService.getEventReminders(eventId),
                                          null,
                                          null,
                                          false,
                                          confirmClose,
                                          id(testuser1Identity));
  }

  private void answer(long eventId, Identity identity, EventAttendeeResponse response) throws Exception {
    agendaEventAttendeeService.sendEventResponse(eventId, id(identity), response);
  }

  private void answerDate(long seriesId, ZonedDateTime date, Identity identity, EventAttendeeResponse response) throws Exception {
    agendaEventAttendeeService.sendEventResponse(seriesId, date, id(identity), response, true);
  }

  private EventAttendee rowOf(long eventId, Identity identity) {
    return agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendee(id(identity), null);
  }

  private EventAttendeeResponse responseOf(long eventId, Identity identity) {
    EventAttendee row = rowOf(eventId, identity);
    return row == null ? null : row.getResponse();
  }

  private static void assertCode(String code, IllegalArgumentException exception) {
    assertEquals(code, exception.getMessage());
  }

  /**
   * Asserts the write refuses a limit below the Yes answers, naming the date
   * given, or no date.
   */
  private static void assertBlocks(LocalDate date, ThrowingRunnable write) {
    CapacityBelowAcceptedException refusal = assertThrows(CapacityBelowAcceptedException.class, write);
    assertEquals("agenda.capacity.belowAccepted", refusal.getMessage());
    assertEquals(date, refusal.getDate());
  }

  private static List<Broadcast> broadcasts(String name) {
    synchronized (BROADCASTS) {
      return BROADCASTS.stream().filter(broadcast -> broadcast.name().equals(name)).toList();
    }
  }

  private static EventAttendeePromotion onlyPromotion() {
    List<Broadcast> promotions = broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED);
    assertEquals("one promotion reported", 1, promotions.size());
    return (EventAttendeePromotion) promotions.get(0).data();
  }

  private static List<Long> promotedIdentities() {
    return broadcasts(Utils.POST_EVENT_ATTENDEE_PROMOTED).stream()
                                                         .map(broadcast -> ((EventAttendeePromotion) broadcast.data()).attendee().getIdentityId())
                                                         .toList();
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
