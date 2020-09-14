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

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.commons.exception.ObjectNotFoundException;

public class AgendaEventAttendeeServiceTest extends BaseAgendaEventTest {

  @Test
  public void testGetEventAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser5Identity);

    long eventId = event.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId);
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
  public void testGetEventResponse() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event.setStatus(EventStatus.CONFIRMED);
    event = createEvent(event.clone(), creatorUserName, testuser1Identity, testuser5Identity);
    long eventId = event.getId();

    try {
      agendaEventAttendeeService.getEventResponse(eventId, Long.parseLong(testuser4Identity.getId()));
      fail("should throw an exception, user is not attendee of the event");
    } catch (IllegalAccessException e) {
      // Expected, user is not attendee of the event
    }

    try {
      agendaEventAttendeeService.getEventResponse(5000l, Long.parseLong(testuser1Identity.getId()));
      fail("should throw an exception, event id doesn't exists");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals("Creator should accept event just after creating the event", EventAttendeeResponse.ACCEPTED, eventResponse);

    eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
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
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event.setStatus(EventStatus.CONFIRMED);
    event = createEvent(event.clone(), creatorUserName, testuser1Identity, testuser5Identity);
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

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser1Identity.getId()),
                                                 EventAttendeeResponse.DECLINED);
    EventAttendeeResponse eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.DECLINED, eventResponse);

    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser5Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);
    eventResponse = agendaEventAttendeeService.getEventResponse(eventId,
                                                                Long.parseLong(testuser5Identity.getId()));
    assertNotNull(eventResponse);
    assertEquals(EventAttendeeResponse.TENTATIVE, eventResponse);
  }

  @Test
  public void testSaveEventAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser5Identity);

    long eventId = event.getId();
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId);
    assertNotNull(eventAttendees);
    assertEquals(1, eventAttendees.size());
    EventAttendee eventAttendee = eventAttendees.get(0);

    eventAttendee = eventAttendee.clone();
    eventAttendee.setId(0);
    eventAttendees.add(eventAttendee);

    long userIdentityId = Long.parseLong(testuser5Identity.getId());
    agendaEventAttendeeService.saveEventAttendees(event, eventAttendees, userIdentityId, true, true);
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId);
    assertNotNull(eventAttendees);
    assertEquals("Same user was added twice, only one attendee object should remain in store", 1, eventAttendees.size());

    eventAttendee = eventAttendee.clone();
    eventAttendee.setId(0);
    eventAttendee.setIdentityId(Long.parseLong(testuser4Identity.getId()));
    eventAttendees.add(eventAttendee);

    agendaEventAttendeeService.saveEventAttendees(event, eventAttendees, userIdentityId, true, true);
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId);
    assertNotNull(eventAttendees);
    assertEquals(2, eventAttendees.size());

    agendaEventAttendeeService.saveEventAttendees(event, Collections.emptyList(), userIdentityId, true, true);
    eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId);
    assertNotNull(eventAttendees);
    assertEquals(0, eventAttendees.size());
  }

}
