package org.exoplatform.agenda.service;

import static org.junit.Assert.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;

import org.junit.Test;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.exception.ObjectNotFoundException;

public class AgendaEventDatePollServiceTest extends BaseAgendaEventTest {

  @Test
  public void testCreateEventWithSingleDateOption() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    try {
      ZonedDateTime start = getDate();
      ZonedDateTime end = start.minusSeconds(1);
      EventDateOption dateOption = new EventDateOption(0,
                                                       0,
                                                       start,
                                                       end,
                                                       false,
                                                       false,
                                                       null);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Arrays.asList(dateOption),
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail("start date should be before end date");
    } catch (AgendaException e) {
      // Expected
    }

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption = new EventDateOption(0,
                                                     0,
                                                     start,
                                                     end,
                                                     false,
                                                     false,
                                                     null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));
    assertNotNull(createdEvent);
    assertEquals(start.withZoneSameInstant(ZoneOffset.UTC), createdEvent.getStart());
    assertEquals(end.withZoneSameInstant(ZoneOffset.UTC), createdEvent.getEnd());
    assertEquals(EventStatus.CONFIRMED, createdEvent.getStatus());

    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(createdEvent.getId(), ZoneOffset.UTC);
    assertTrue(dateOptions == null || dateOptions.isEmpty());
  }

  @Test
  public void testCreateEventWithMultipleDateOptions() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    long creatorIdentityId = Long.parseLong(testuser1Identity.getId());
    try {
      ZonedDateTime start = getDate();
      ZonedDateTime end = start.minusSeconds(1);
      EventDateOption dateOption1 = new EventDateOption(0,
                                                        0,
                                                        start,
                                                        end,
                                                        false,
                                                        false,
                                                        null);
      EventDateOption dateOption2 = new EventDateOption(0,
                                                        0,
                                                        start,
                                                        end,
                                                        true,
                                                        false,
                                                        null);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Arrays.asList(dateOption1, dateOption2),
                                     null,
                                     true,
                                     creatorIdentityId);
      fail("start date should be before end date");
    } catch (AgendaException e) {
      // Expected
    }

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0,
                                                      0,
                                                      start,
                                                      end,
                                                      false,
                                                      false,
                                                      null);
    EventDateOption dateOption2 = new EventDateOption(0,
                                                      0,
                                                      start.plusDays(1),
                                                      end.plusDays(1),
                                                      true,
                                                      true,
                                                      null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Arrays.asList(new EventAttendee(0,
                                                                                        creatorIdentityId,
                                                                                        EventAttendeeResponse.ACCEPTED)),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        creatorIdentityId);
    assertNotNull(createdEvent);
    assertEquals(dateOption1.getStart().withZoneSameInstant(ZoneOffset.UTC), createdEvent.getStart());
    assertEquals(dateOption2.getEnd().withZoneSameInstant(ZoneOffset.UTC), createdEvent.getEnd());
    assertEquals(EventStatus.TENTATIVE, createdEvent.getStatus());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(createdEvent.getId());
    assertNotNull(eventAttendees);
    assertEquals(1, eventAttendees.size());
    assertEquals(creatorIdentityId, eventAttendees.get(0).getIdentityId());
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, eventAttendees.get(0).getResponse());

    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(createdEvent.getId(), ZoneOffset.UTC);
    assertFalse(dateOptions == null || dateOptions.isEmpty());
    assertEquals(2, dateOptions.size()); // NOSONAR

    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));

    EventDateOption createdDateOption1 = dateOptions.get(0);
    EventDateOption createdDateOption2 = dateOptions.get(1);

    assertEquals(dateOption1.getStart().withZoneSameInstant(ZoneOffset.UTC), createdDateOption1.getStart());
    assertEquals(dateOption1.getEnd().withZoneSameInstant(ZoneOffset.UTC), createdDateOption1.getEnd());

    assertEquals(dateOption2.getStart()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC),
                 createdDateOption2.getStart());
    assertEquals(dateOption2.getEnd()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC)
                            .plusDays(1)
                            .minusSeconds(1),
                 createdDateOption2.getEnd());
  }

  @Test
  public void testUpdateEventWithSingleDateOption() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption = new EventDateOption(0,
                                                     0,
                                                     start,
                                                     end,
                                                     false,
                                                     false,
                                                     null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    try {
      start = getDate();
      end = start.minusSeconds(1);
      dateOption = new EventDateOption(0,
                                       0,
                                       start,
                                       end,
                                       false,
                                       false,
                                       null);
      agendaEventService.updateEvent(createdEvent,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Arrays.asList(dateOption),
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail("start date should be before end date");
    } catch (AgendaException e) {
      // Expected
    }

    start = getDate().plusDays(1);
    end = start;
    dateOption = new EventDateOption(0,
                                     0,
                                     start,
                                     end,
                                     true,
                                     false,
                                     null);
    Event updatedEvent = agendaEventService.updateEvent(createdEvent,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(createdEvent.getId(), ZoneOffset.UTC);
    assertTrue(dateOptions == null || dateOptions.isEmpty());
    assertEquals(EventStatus.CONFIRMED, event.getStatus());

    assertTrue(updatedEvent.isAllDay());
    assertEquals(dateOption.getStart()
                           .withZoneSameInstant(ZoneOffset.UTC)
                           .toLocalDate()
                           .atStartOfDay(ZoneOffset.UTC),
                 updatedEvent.getStart());
    assertEquals(dateOption.getEnd()
                           .withZoneSameInstant(ZoneOffset.UTC)
                           .toLocalDate()
                           .atStartOfDay(ZoneOffset.UTC)
                           .plusDays(1)
                           .minusSeconds(1),
                 updatedEvent.getEnd());
  }

  @Test
  public void testUpdateEventWithMultipleDateOptions() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0,
                                                      0,
                                                      start,
                                                      end,
                                                      false,
                                                      false,
                                                      null);
    EventDateOption dateOption2 = new EventDateOption(0,
                                                      0,
                                                      start.plusDays(1),
                                                      end.plusDays(1),
                                                      true,
                                                      true,
                                                      null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    try {
      ZonedDateTime startTmp = getDate();
      ZonedDateTime endTmp = start.minusSeconds(1);
      EventDateOption dateOptionTmp1 = new EventDateOption(0,
                                                           0,
                                                           startTmp,
                                                           endTmp,
                                                           false,
                                                           false,
                                                           null);
      EventDateOption dateOptionTmp2 = new EventDateOption(0,
                                                           0,
                                                           startTmp,
                                                           endTmp,
                                                           true,
                                                           false,
                                                           null);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Arrays.asList(dateOptionTmp1, dateOptionTmp2),
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail("start date should be before end date");
    } catch (AgendaException e) {
      // Expected
    }

    dateOption1.setAllDay(true);
    dateOption1.setStart(dateOption1.getStart().plusDays(1));
    dateOption1.setEnd(dateOption1.getEnd().plusDays(1));

    Event updatedEvent = agendaEventService.updateEvent(createdEvent,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    assertNotNull(updatedEvent);
    assertEquals(dateOption2.getStart().withZoneSameInstant(ZoneOffset.UTC), updatedEvent.getStart());
    assertEquals(dateOption1.getEnd().withZoneSameInstant(ZoneOffset.UTC), updatedEvent.getEnd());
    assertEquals(EventStatus.TENTATIVE, updatedEvent.getStatus());

    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(updatedEvent.getId(), ZoneOffset.UTC);
    assertFalse(dateOptions == null || dateOptions.isEmpty());
    assertEquals(2, dateOptions.size()); // NOSONAR

    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));

    EventDateOption createdDateOption1 = dateOptions.get(1);
    EventDateOption createdDateOption2 = dateOptions.get(0);

    assertEquals(dateOption1.getStart()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC),
                 createdDateOption1.getStart());
    assertEquals(dateOption1.getEnd()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC)
                            .plusDays(1)
                            .minusSeconds(1),
                 createdDateOption1.getEnd());

    assertEquals(dateOption2.getStart()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC),
                 createdDateOption2.getStart());
    assertEquals(dateOption2.getEnd()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC)
                            .plusDays(1)
                            .minusSeconds(1),
                 createdDateOption2.getEnd());

    assertEquals(dateOption2.getStart()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC),
                 createdDateOption2.getStart());
    assertEquals(dateOption2.getEnd()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC)
                            .plusDays(1)
                            .minusSeconds(1),
                 createdDateOption2.getEnd());
  }

  @Test
  public void testUpdateEventFromMultipleToSingleDateOption() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0,
                                                      0,
                                                      start,
                                                      end,
                                                      false,
                                                      false,
                                                      null);
    EventDateOption dateOption2 = new EventDateOption(0,
                                                      0,
                                                      start.plusDays(1),
                                                      end.plusDays(1),
                                                      true,
                                                      true,
                                                      null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    assertNotNull(createdEvent);
    assertEquals(EventStatus.TENTATIVE, createdEvent.getStatus());

    dateOption1.setAllDay(true);
    dateOption1.setStart(dateOption1.getStart().plusDays(1));
    dateOption1.setEnd(dateOption1.getEnd().plusDays(1));

    Event updatedEvent = agendaEventService.updateEvent(createdEvent,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    assertNotNull(updatedEvent);
    assertTrue(updatedEvent.isAllDay());
    assertEquals(dateOption1.getStart()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC),
                 updatedEvent.getStart());
    assertEquals(dateOption1.getEnd()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC)
                            .plusDays(1)
                            .minusSeconds(1),
                 updatedEvent.getEnd());
    assertEquals(EventStatus.CONFIRMED, updatedEvent.getStatus());
  }

  @Test
  public void testUpdateEventFromSingleToMultipleDateOption() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0,
                                                      0,
                                                      start,
                                                      end,
                                                      false,
                                                      false,
                                                      null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    assertNotNull(createdEvent);
    assertEquals(EventStatus.CONFIRMED, createdEvent.getStatus());

    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(createdEvent.getId(), ZoneOffset.UTC);
    assertEquals(0, dateOptions.size());

    EventDateOption dateOption2 = new EventDateOption(0,
                                                      0,
                                                      start.plusDays(1),
                                                      end.plusDays(1),
                                                      true,
                                                      true,
                                                      null);

    Event updatedEvent = agendaEventService.updateEvent(createdEvent,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));
    assertNotNull(updatedEvent);
    assertEquals(dateOption1.getStart().withZoneSameInstant(ZoneOffset.UTC), updatedEvent.getStart());
    assertEquals(dateOption2.getEnd().withZoneSameInstant(ZoneOffset.UTC), updatedEvent.getEnd());
    assertEquals(EventStatus.TENTATIVE, updatedEvent.getStatus());

    dateOptions = agendaEventDatePollService.getEventDateOptions(updatedEvent.getId(), ZoneOffset.UTC);
    assertFalse(dateOptions == null || dateOptions.isEmpty());
    assertEquals(2, dateOptions.size()); // NOSONAR

    // Test update again: should be idempotent
    agendaEventService.updateEvent(updatedEvent,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   dateOptions,
                                   null,
                                   true,
                                   Long.parseLong(testuser1Identity.getId()));
    dateOptions = agendaEventDatePollService.getEventDateOptions(updatedEvent.getId(), ZoneOffset.UTC);

    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));

    EventDateOption createdDateOption1 = dateOptions.get(0);
    EventDateOption createdDateOption2 = dateOptions.get(1);

    assertEquals(dateOption1.getStart()
                            .withZoneSameInstant(ZoneOffset.UTC),
                 createdDateOption1.getStart());
    assertEquals(dateOption1.getEnd()
                            .withZoneSameInstant(ZoneOffset.UTC),
                 createdDateOption1.getEnd());

    assertEquals(dateOption2.getStart()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC),
                 createdDateOption2.getStart());
    assertEquals(dateOption2.getEnd()
                            .withZoneSameInstant(ZoneOffset.UTC)
                            .toLocalDate()
                            .atStartOfDay(ZoneOffset.UTC)
                            .plusDays(1)
                            .minusSeconds(1),
                 createdDateOption2.getEnd());
  }

  @Test
  public void testSelectEventDateOption() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0,
                                                      0,
                                                      start,
                                                      end,
                                                      false,
                                                      false,
                                                      null);
    EventDateOption dateOption2 = new EventDateOption(0,
                                                      0,
                                                      start.plusDays(1),
                                                      end.plusDays(1),
                                                      true,
                                                      true,
                                                      null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Arrays.asList(new EventAttendee(0,
                                                                                        Long.parseLong(testuser1Identity.getId()),
                                                                                        EventAttendeeResponse.NEEDS_ACTION)),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    long eventId = createdEvent.getId();
    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertFalse(dateOptions == null || dateOptions.isEmpty());
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    EventDateOption createdDateOption1 = dateOptions.get(0);
    EventDateOption createdDateOption2 = dateOptions.get(1);
    assertFalse(createdDateOption1.isSelected());
    assertFalse(createdDateOption2.isSelected());

    agendaEventService.selectEventDateOption(eventId,
                                             createdDateOption1.getId(),
                                             Long.parseLong(testuser1Identity.getId()));

    Event updatedEvent = agendaEventService.getEventById(eventId);
    assertNotNull(updatedEvent);
    assertEquals(createdDateOption1.getStart().withZoneSameInstant(ZoneOffset.UTC), updatedEvent.getStart());
    assertEquals(createdDateOption1.getEnd().withZoneSameInstant(ZoneOffset.UTC), updatedEvent.getEnd());
    assertEquals(EventStatus.CONFIRMED, updatedEvent.getStatus());

    dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertFalse(dateOptions == null || dateOptions.isEmpty());
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    createdDateOption1 = dateOptions.get(0);
    createdDateOption2 = dateOptions.get(1);
    assertTrue(createdDateOption1.isSelected());
    assertFalse(createdDateOption2.isSelected());
  }

  @Test
  public void testVoteEventDateOption() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0,
                                                      0,
                                                      start,
                                                      end,
                                                      false,
                                                      false,
                                                      null);
    EventDateOption dateOption2 = new EventDateOption(0,
                                                      0,
                                                      start.plusDays(1),
                                                      end.plusDays(1),
                                                      true,
                                                      true,
                                                      null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    long eventId = createdEvent.getId();

    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    EventDateOption createdDateOption1 = dateOptions.get(0);

    try {
      agendaEventDatePollService.voteDateOption(5000l, Long.parseLong(testuser1Identity.getId()));
      fail("Date Option shouldn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventDatePollService.voteDateOption(createdDateOption1.getId(), Long.parseLong(testuser2Identity.getId()));
      fail("User is not attendee, thus shouldn't be able to vote on it");
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      agendaEventDatePollService.voteDateOption(createdDateOption1.getId(), Long.parseLong(testuser2Identity.getId()));
      fail("User is not attendee, thus shouldn't be able to vote on it");
    } catch (IllegalAccessException e) {
      // Expected
    }

    agendaEventService.updateEvent(createdEvent,
                                   Arrays.asList(new EventAttendee(0,
                                                                   Long.parseLong(spaceIdentity.getId()),
                                                                   EventAttendeeResponse.NEEDS_ACTION)),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   dateOptions,
                                   null,
                                   true,
                                   Long.parseLong(testuser1Identity.getId()));

    agendaEventDatePollService.voteDateOption(createdDateOption1.getId(), Long.parseLong(testuser2Identity.getId()));
    agendaEventDatePollService.voteDateOption(createdDateOption1.getId(), Long.parseLong(testuser2Identity.getId()));

    dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    createdDateOption1 = dateOptions.get(0);
    EventDateOption createdDateOption2 = dateOptions.get(1);

    assertNotNull(createdDateOption1.getVoters());
    assertEquals(1, createdDateOption1.getVoters().size());
    assertTrue(createdDateOption2.getVoters() == null || createdDateOption2.getVoters().isEmpty());

    agendaEventDatePollService.voteDateOption(createdDateOption2.getId(), Long.parseLong(testuser2Identity.getId()));
    agendaEventDatePollService.voteDateOption(createdDateOption2.getId(), Long.parseLong(testuser2Identity.getId()));

    dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    createdDateOption1 = dateOptions.get(0);
    createdDateOption2 = dateOptions.get(1);
    assertNotNull(createdDateOption1.getVoters());
    assertEquals(1, createdDateOption1.getVoters().size());
    assertNotNull(createdDateOption2.getVoters());
    assertEquals(1, createdDateOption2.getVoters().size());
  }

  @Test
  public void testDismissEventDateOption() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0,
                                                      0,
                                                      start,
                                                      end,
                                                      false,
                                                      false,
                                                      null);
    EventDateOption dateOption2 = new EventDateOption(0,
                                                      0,
                                                      start.plusDays(1),
                                                      end.plusDays(1),
                                                      true,
                                                      true,
                                                      null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    long eventId = createdEvent.getId();

    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    EventDateOption createdDateOption1 = dateOptions.get(0);

    try {
      agendaEventDatePollService.dismissDateOption(5000l, Long.parseLong(testuser1Identity.getId()));
      fail("Date Option shouldn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventDatePollService.dismissDateOption(createdDateOption1.getId(), Long.parseLong(testuser2Identity.getId()));
    } catch (Exception e) {
      fail("User should be able to dismiss his vote even if he's not an attendee anymore");
    }

    agendaEventService.updateEvent(createdEvent,
                                   Arrays.asList(new EventAttendee(0,
                                                                   Long.parseLong(spaceIdentity.getId()),
                                                                   EventAttendeeResponse.NEEDS_ACTION)),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   dateOptions,
                                   null,
                                   true,
                                   Long.parseLong(testuser1Identity.getId()));

    agendaEventDatePollService.voteDateOption(createdDateOption1.getId(), Long.parseLong(testuser2Identity.getId()));

    dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    createdDateOption1 = dateOptions.get(0);
    EventDateOption createdDateOption2 = dateOptions.get(1);

    assertNotNull(createdDateOption1.getVoters());
    assertEquals(1, createdDateOption1.getVoters().size());
    assertTrue(createdDateOption2.getVoters() == null || createdDateOption2.getVoters().isEmpty());

    agendaEventDatePollService.dismissDateOption(createdDateOption1.getId(), Long.parseLong(testuser2Identity.getId()));
    agendaEventDatePollService.voteDateOption(createdDateOption2.getId(), Long.parseLong(testuser2Identity.getId()));

    dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    createdDateOption1 = dateOptions.get(0);
    createdDateOption2 = dateOptions.get(1);
    assertNotNull(createdDateOption1.getVoters());
    assertEquals(0, createdDateOption1.getVoters().size());
    assertNotNull(createdDateOption2.getVoters());
    assertEquals(1, createdDateOption2.getVoters().size());

    agendaEventDatePollService.dismissDateOption(createdDateOption2.getId(), Long.parseLong(testuser2Identity.getId()));

    dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertEquals(2, dateOptions.size()); // NOSONAR
    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    createdDateOption1 = dateOptions.get(0);
    createdDateOption2 = dateOptions.get(1);
    assertNotNull(createdDateOption1.getVoters());
    assertEquals(0, createdDateOption1.getVoters().size());
    assertNotNull(createdDateOption2.getVoters());
    assertEquals(0, createdDateOption2.getVoters().size());
  }

  @Test
  public void testSaveEventVotes() throws Exception { // NOSONAR
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0, 0, start, end, false, false, null);
    EventDateOption dateOption2 = new EventDateOption(0, 0, start.plusDays(1), end.plusDays(1), true, true, null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Arrays.asList(new EventAttendee(0,
                                                                                        Long.parseLong(spaceIdentity.getId()),
                                                                                        EventAttendeeResponse.NEEDS_ACTION)),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    long eventId = createdEvent.getId();

    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertNotNull(dateOptions);
    assertEquals(2, dateOptions.size());

    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));
    try {
      agendaEventDatePollService.saveEventVotes(2000l, Collections.emptyList(), Long.parseLong(testuser1Identity.getId()));
      fail("Event with id shouldn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventDatePollService.saveEventVotes(eventId, Collections.emptyList(), Long.parseLong(testuser5Identity.getId()));
      fail("User is not attendee, thus shouldn't be able to vote on it");
    } catch (IllegalAccessException e) {
      // Expected
    }

    agendaEventDatePollService.saveEventVotes(eventId,
                                              Collections.singletonList(dateOptions.get(0).getId()),
                                              Long.parseLong(testuser2Identity.getId()));

    dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertNotNull(dateOptions);
    assertEquals(2, dateOptions.size()); // NOSONAR

    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));

    assertNotNull(dateOptions.get(0).getVoters());
    assertEquals(1, dateOptions.get(0).getVoters().size());

    assertNotNull(dateOptions.get(1).getVoters());
    assertEquals(0, dateOptions.get(1).getVoters().size());

    agendaEventDatePollService.saveEventVotes(eventId,
                                              Collections.singletonList(dateOptions.get(1).getId()),
                                              Long.parseLong(testuser2Identity.getId()));

    dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);
    assertNotNull(dateOptions);
    assertEquals(2, dateOptions.size()); // NOSONAR

    dateOptions.sort((option1, option2) -> option1.getStart().compareTo(option2.getStart()));

    assertNotNull(dateOptions.get(0).getVoters());
    assertEquals(0, dateOptions.get(0).getVoters().size());

    assertNotNull(dateOptions.get(1).getVoters());
    assertEquals(1, dateOptions.get(1).getVoters().size());
  }

}
