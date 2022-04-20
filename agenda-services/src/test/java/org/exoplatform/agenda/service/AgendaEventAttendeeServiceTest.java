/*
 * Copyright (C) 2020 eXo Platform SAS.
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
package org.exoplatform.agenda.service;

import static org.junit.Assert.*;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.exception.ObjectNotFoundException;

public class AgendaEventAttendeeServiceTest extends BaseAgendaEventTest {

  @Test
  public void testGetEventAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser5Identity);

    long eventId = event.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(1, eventAttendees.size());

    EventAttendee eventAttendeeToStore = ATTENDEES.get(0);

    EventAttendee eventAttendee = eventAttendees.get(0);
    assertNotNull(eventAttendee);
    assertTrue(eventAttendee.getId() > 0);
    assertEquals(eventAttendeeToStore.getIdentityId(), eventAttendee.getIdentityId());
    assertEquals(EventAttendeeResponse.NEEDS_ACTION, eventAttendee.getResponse());
  }

  @Test
  public void testGetEventAttendeesByResponses() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event.clone(),
                        creatorId,
                        testuser1Identity,
                        testuser5Identity,
                        spaceIdentity);

    long eventId = event.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId, EventAttendeeResponse.ACCEPTED)
                                                                   .getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(1, eventAttendees.size());

    EventAttendee eventAttendee = eventAttendees.get(0);
    assertNotNull(eventAttendee);
    assertTrue(eventAttendee.getId() > 0);
    assertEquals(creatorId, eventAttendee.getIdentityId());
    assertEquals(EventAttendeeResponse.ACCEPTED, eventAttendee.getResponse());

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);

    eventAttendees =
                   agendaEventAttendeeService.getEventAttendees(eventId, EventAttendeeResponse.ACCEPTED).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(2, eventAttendees.size());

    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId,
                                                                  EventAttendeeResponse.ACCEPTED,
                                                                  EventAttendeeResponse.TENTATIVE)
                                               .getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(3, eventAttendees.size());
  }

  @Test
  public void testGetEventResponse() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setStatus(EventStatus.CONFIRMED);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser5Identity);
    long eventId = event.getId();

    try {
      agendaEventAttendeeService.getEventResponse(eventId, null, Long.parseLong(testuser4Identity.getId()));
      fail("should throw an exception, user is not attendee of the event");
    } catch (IllegalAccessException e) {
      // Expected, user is not attendee of the event
    }

    try {
      agendaEventAttendeeService.getEventResponse(5000l, null, Long.parseLong(testuser1Identity.getId()));
      fail("should throw an exception, event id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                                      null,
                                                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals("Creator should accept event just after creating the event", EventAttendeeResponse.ACCEPTED, eventResponse);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                null,
                                                                Long.parseLong(testuser5Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals("Invitee default response should be empty just after creating the event",
                 EventAttendeeResponse.NEEDS_ACTION,
                 eventResponse);
  }

  @Test
  public void testSendEventResponse() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setStatus(EventStatus.CONFIRMED);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser5Identity);
    long eventId = event.getId();

    try {
      agendaEventAttendeeService.sendEventResponse(eventId, Long.parseLong(testuser1Identity.getId()), null);
      fail("should throw an exception, response shouldn't be null");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendEventResponse(5000l,
                                                   Long.parseLong(testuser1Identity.getId()),
                                                   EventAttendeeResponse.ACCEPTED);
      fail("should throw an exception, event with id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendEventResponse(0,
                                                   Long.parseLong(testuser1Identity.getId()),
                                                   EventAttendeeResponse.ACCEPTED);
      fail("should throw an exception, event with id doesn't exists");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser1Identity.getId()),
                                                 EventAttendeeResponse.DECLINED);
    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                                      null,
                                                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.DECLINED, eventResponse);

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);
    eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                null,
                                                                Long.parseLong(testuser5Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.TENTATIVE, eventResponse);
  }

  @Test
  public void testSaveRecurrentEventAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     start.plusDays(2).toLocalDate(),
                                                     0,
                                                     EventRecurrenceType.DAILY,
                                                     EventRecurrenceFrequency.DAILY,
                                                     1,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null);
    event.setRecurrence(recurrence);

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    long eventId = event.getId();
    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(eventId, start.plusDays(1));

    assertNotNull(exceptionalOccurrence);
    long exceptionalOccurrenceId = exceptionalOccurrence.getId();
    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, eventResponse);

    EventAttendeeResponse exceptionalOccurrenceResponse = agendaEventAttendeeService.getEventResponse(exceptionalOccurrenceId,
                                                                                                      null,
                                                                                                      userIdentityId);
    assertNotNull(exceptionalOccurrenceResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, exceptionalOccurrenceResponse);

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.DECLINED, eventResponse);
    exceptionalOccurrenceResponse = agendaEventAttendeeService.getEventResponse(exceptionalOccurrenceId,
                                                                                null,
                                                                                userIdentityId);
    assertNotNull(exceptionalOccurrenceResponse);
    assertEquals(EventAttendeeResponse.DECLINED, exceptionalOccurrenceResponse);

    agendaEventAttendeeService.sendEventResponse(exceptionalOccurrenceId, userIdentityId, EventAttendeeResponse.TENTATIVE);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.DECLINED, eventResponse);
    exceptionalOccurrenceResponse = agendaEventAttendeeService.getEventResponse(exceptionalOccurrenceId,
                                                                                null,
                                                                                userIdentityId);
    assertNotNull(exceptionalOccurrenceResponse);
    assertEquals(EventAttendeeResponse.TENTATIVE, exceptionalOccurrenceResponse);

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.ACCEPTED);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId, null, userIdentityId);
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, eventResponse);
    exceptionalOccurrenceResponse = agendaEventAttendeeService.getEventResponse(exceptionalOccurrenceId,
                                                                                null,
                                                                                userIdentityId);
    assertNotNull(exceptionalOccurrenceResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, exceptionalOccurrenceResponse);
  }

  @Test
  public void testSaveEventAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser5Identity);

    long eventId = event.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(1, eventAttendees.size());
    EventAttendee eventAttendee = eventAttendees.get(0);

    eventAttendee = eventAttendee.clone();
    eventAttendee.setId(0);
    eventAttendees.add(eventAttendee);

    long userIdentityId = Long.parseLong(testuser5Identity.getId());
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  userIdentityId,
                                                  true,
                                                  true,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              userIdentityId,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals("Same user was added twice, only one attendee object should remain in store", 1, eventAttendees.size());

    eventAttendee = eventAttendee.clone();
    eventAttendee.setId(0);
    eventAttendee.setIdentityId(Long.parseLong(testuser4Identity.getId()));
    eventAttendees.add(eventAttendee);

    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  userIdentityId,
                                                  true,
                                                  true,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              userIdentityId,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(2, eventAttendees.size());

    agendaEventAttendeeService.saveEventAttendees(event,
                                                  Collections.emptyList(),
                                                  userIdentityId,
                                                  true,
                                                  true,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              userIdentityId,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertNotNull(eventAttendees);
    assertEquals(0, eventAttendees.size());
  }

  @Test
  public void testSendUpcomingEventResponse() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setStatus(EventStatus.CONFIRMED);
    event.setRecurrence(new EventRecurrence(0,
                                            null,
                                            0,
                                            EventRecurrenceType.DAILY,
                                            EventRecurrenceFrequency.DAILY,
                                            1,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null,
                                            null));

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser5Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                                      null,
                                                                                      Long.parseLong(testuser5Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.ACCEPTED, eventResponse);

    List<Event> eventOccurrences = agendaEventService.getEventOccurrencesInPeriod(event,
                                                                                  start,
                                                                                  start.plusDays(10),
                                                                                  ZoneOffset.UTC,
                                                                                  0);
    assertNotNull(eventOccurrences);
    assertEquals(11, eventOccurrences.size());

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           Long.parseLong(testuser1Identity.getId()),
                                                           null);
      fail("should throw an exception, response shouldn't be null");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(0,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           Long.parseLong(testuser1Identity.getId()),
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, event with id doesn't exists");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           0,
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, occurrence id doesn't exists");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           2222l,
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, identity id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(5000l,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           Long.parseLong(testuser1Identity.getId()),
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, event with id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    try {
      agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                           eventOccurrences.get(3).getOccurrence().getId(),
                                                           Long.parseLong(testuser3Identity.getId()),
                                                           EventAttendeeResponse.TENTATIVE);
      fail("should throw an exception, user testuser3 isn't an attendee");
    } catch (IllegalAccessException e) {
      // Expected
    }

    agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                         eventOccurrences.get(3).getOccurrence().getId(),
                                                         Long.parseLong(testuser5Identity.getId()),
                                                         EventAttendeeResponse.TENTATIVE);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(0).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(1).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(2).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(3).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(4).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(9).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));

    agendaEventAttendeeService.sendUpcomingEventResponse(eventId,
                                                         eventOccurrences.get(5).getOccurrence().getId(),
                                                         Long.parseLong(testuser5Identity.getId()),
                                                         EventAttendeeResponse.DECLINED);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(0).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(1).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(2).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(3).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.TENTATIVE,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(4).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(5).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
    assertEquals(EventAttendeeResponse.DECLINED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(9).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId,
                                                             eventOccurrences.get(9).getOccurrence().getId(),
                                                             Long.parseLong(testuser5Identity.getId())));
  }

}
