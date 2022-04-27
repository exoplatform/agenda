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

import static org.exoplatform.agenda.util.NotificationUtils.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;

import org.junit.Test;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;

public class AgendaEventServiceTest extends BaseAgendaEventTest {

  @Test
  public void testCreateEvent() throws Exception { // NOSONAR
    try {
      Event event = new Event();
      event.setId(0);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     0l);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.createEvent(null,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(2);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(0);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now().plusDays(1));
      event.setEnd(ZonedDateTime.now());
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      long userIdentityId = 2000l;
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     userIdentityId);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(12);
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser4Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      spaceService.addRedactor(space, testuser1Identity.getRemoteId());

      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
    } catch (AgendaException e) {
      fail(e.getMessage());
    }

    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    LocalDate untilDate = event.getRecurrence().getUntil();
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser2Identity,
                                     testuser3Identity);

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);
    AgendaEventModification eventModification = eventCreationReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ADDED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 1,
                 eventModification.getModificationTypes().size());

    assertEquals(event.getSummary(), createdEvent.getSummary());
    assertEquals(event.getDescription(), createdEvent.getDescription());
    assertEquals(event.getCalendarId(), createdEvent.getCalendarId());
    assertEquals(event.getColor(), createdEvent.getColor());
    assertEquals(event.getStart().toLocalDate(), createdEvent.getStart().toLocalDate());
    assertEquals(event.getEnd().toLocalDate(), createdEvent.getEnd().toLocalDate());
    assertEquals(event.getLocation(), createdEvent.getLocation());
    assertEquals(Long.parseLong(testuser1Identity.getId()), createdEvent.getCreatorId());
    assertEquals(event.getAvailability(), createdEvent.getAvailability());
    assertEquals(event.getOccurrence(), createdEvent.getOccurrence());
    assertNotNull(createdEvent.getAcl());
    assertTrue(createdEvent.getAcl().isCanEdit());
    assertFalse(createdEvent.getAcl().isAttendee());

    assertNotNull(createdEvent.getCreated());
    assertNull(createdEvent.getUpdated());
    assertEquals(event.getModifierId(), createdEvent.getModifierId());

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertNotNull(createdEventRecurrence.getUntil());
    assertEquals(untilDate,
                 createdEventRecurrence.getUntil());
    assertEquals(createdEvent.getRecurrence().getOverallEnd().toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());

    assertNotNull(createdEventRecurrence.getOverallStart());
    assertEquals(start.toLocalDate(),
                 createdEventRecurrence.getOverallStart().toLocalDate());

    assertNotNull(createdEventRecurrence.getOverallEnd());
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getOverallEnd().toLocalDate());
  }

  @Test
  public void testCreateEvent_InSpace_AsMember() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setCalendarId(spaceCalendar.getId());
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser2Identity,
                                     testuser3Identity);

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);
    AgendaEventModification eventModification = eventCreationReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ADDED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 1,
                 eventModification.getModificationTypes().size());

    try {
      event = newEventInstance(start, start, allDay);
      event.setCalendarId(spaceCalendar.getId());
      createEvent(event.clone(), Long.parseLong(testuser5Identity.getId()), testuser2Identity, testuser3Identity);
      fail("testuser5 is not member of space and shouldn't be able to create an event");
    } catch (IllegalAccessException e) {
      // Expected
    }
  }

  @Test
  public void testCreateEvent_InSpace_AsManager() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;
    spaceService.addRedactor(space, testuser1Identity.getRemoteId());
    spaceService.setManager(space, testuser2Identity.getRemoteId(), true);
    Event event = newEventInstance(start, start, allDay);
    event.setCalendarId(spaceCalendar.getId());
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser2Identity.getId()),
                                     testuser3Identity);

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);
    AgendaEventModification eventModification = eventCreationReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ADDED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 1,
                 eventModification.getModificationTypes().size());
  }

  @Test
  public void testGetEventById() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    try {
      agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser3Identity.getId()));
      fail("Should fail when a non attendee attempts to access event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser3Identity.getId()));
      fail("Should fail when a non attendee attempts to access event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event createdEvent = agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    assertEquals(event.getSummary(), createdEvent.getSummary());
    assertEquals(event.getDescription(), createdEvent.getDescription());
    assertEquals(event.getCalendarId(), createdEvent.getCalendarId());
    assertEquals(event.getColor(), createdEvent.getColor());
    assertEquals(event.getLocation(), createdEvent.getLocation());
    assertEquals(event.getCreatorId(), createdEvent.getCreatorId());
    assertEquals(event.getAvailability(), createdEvent.getAvailability());
    assertEquals(event.getOccurrence(), createdEvent.getOccurrence());
    assertEquals(event.getStart().withZoneSameLocal(ZoneOffset.UTC), createdEvent.getStart().withZoneSameLocal(ZoneOffset.UTC));
    assertEquals(event.getEnd().withZoneSameLocal(ZoneOffset.UTC), createdEvent.getEnd().withZoneSameLocal(ZoneOffset.UTC));
    assertNotNull(createdEvent.getAcl());
    assertFalse(createdEvent.getAcl().isCanEdit());
    assertTrue(createdEvent.getAcl().isAttendee());

    assertNotNull(createdEvent.getCreated());
    assertNull(createdEvent.getUpdated());
    assertEquals(0, createdEvent.getModifierId());

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(createdEvent.getRecurrence().getOverallEnd().toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());

    assertNotNull(createdEventRecurrence.getOverallStart());
    assertEquals(start.toLocalDate(),
                 createdEventRecurrence.getOverallStart().toLocalDate());

    assertNotNull(createdEventRecurrence.getOverallEnd());
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getOverallEnd().toLocalDate());
  }

  @Test
  public void testGetEventById_Recurrent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    ZonedDateTime end = getDate().withNano(0).plusHours(2);

    boolean allDay = false;

    Event event = newEventInstance(start, end, allDay);
    Event createdEvent = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);
    createdEvent = agendaEventService.getEventById(createdEvent.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    assertEquals(event.getSummary(), createdEvent.getSummary());
    assertEquals(event.getDescription(), createdEvent.getDescription());
    assertEquals(event.getCalendarId(), createdEvent.getCalendarId());
    assertEquals(event.getColor(), createdEvent.getColor());
    assertEquals(event.getLocation(), createdEvent.getLocation());
    assertEquals(Long.parseLong(testuser1Identity.getId()), createdEvent.getCreatorId());
    assertEquals(event.getAvailability(), createdEvent.getAvailability());
    assertEquals(event.getOccurrence(), createdEvent.getOccurrence());
    assertEquals(event.getStart().withZoneSameInstant(ZoneOffset.UTC),
                 createdEvent.getStart().withZoneSameInstant(ZoneOffset.UTC));
    assertEquals(event.getEnd().withZoneSameInstant(ZoneOffset.UTC), createdEvent.getEnd().withZoneSameInstant(ZoneOffset.UTC));

    assertNotNull(createdEvent.getCreated());
    assertNull(createdEvent.getUpdated());
    assertEquals(0, createdEvent.getModifierId());

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertNotNull(createdEventRecurrence.getUntil());

    assertEquals(end.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(createdEventRecurrence.getUntil(),
                 createdEvent.getRecurrence().getOverallEnd().withZoneSameInstant(ZoneOffset.UTC).toLocalDate());

    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());

    assertNotNull(createdEventRecurrence.getOverallStart());
    assertEquals(start.toLocalDate(),
                 createdEventRecurrence.getOverallStart().toLocalDate());

    assertNotNull(createdEventRecurrence.getOverallEnd());
    assertEquals(end.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getOverallEnd().toLocalDate());
    assertEquals(end.plusDays(2).getHour(),
                 createdEventRecurrence.getOverallEnd().getHour());
    assertEquals(end.plusDays(2).getMinute(),
                 createdEventRecurrence.getOverallEnd().getMinute());
    assertEquals(end.plusDays(2).getSecond(),
                 createdEventRecurrence.getOverallEnd().getSecond());
  }

  @Test
  public void testGetEventById_RecurrenceAttributes() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.getRecurrence().setType(EventRecurrenceType.YEARLY);
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.YEARLY);
    event.getRecurrence().setBySecond(Collections.singletonList("1"));
    event.getRecurrence().setByMinute(Collections.singletonList("1"));
    event.getRecurrence().setByHour(Collections.singletonList("1"));
    event.getRecurrence().setByDay(Collections.singletonList("TU"));
    event.getRecurrence().setByMonthDay(Collections.singletonList("2"));
    event.getRecurrence().setByMonth(Collections.singletonList("3"));
    event.getRecurrence().setByWeekNo(Collections.singletonList("30"));
    event.getRecurrence().setByYearDay(Collections.singletonList("165"));
    event.getRecurrence().setBySetPos(Collections.singletonList("-1"));
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    Event createdEvent = agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
  }

  @Test
  public void testGetEventById_Recurrent_AllDayEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    try {
      agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser3Identity.getId()));
      fail("Should fail when a non attendee attempts to access event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event createdEvent = agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    assertEquals(event.getSummary(), createdEvent.getSummary());
    assertEquals(event.getDescription(), createdEvent.getDescription());
    assertEquals(event.getCalendarId(), createdEvent.getCalendarId());
    assertEquals(event.getColor(), createdEvent.getColor());
    assertEquals(event.getLocation(), createdEvent.getLocation());
    assertEquals(event.getCreatorId(), createdEvent.getCreatorId());
    assertEquals(event.getAvailability(), createdEvent.getAvailability());
    assertEquals(event.getOccurrence(), createdEvent.getOccurrence());
    assertEquals(event.getStart().withZoneSameLocal(ZoneOffset.UTC), createdEvent.getStart().withZoneSameLocal(ZoneOffset.UTC));
    assertEquals(event.getEnd().withZoneSameLocal(ZoneOffset.UTC), createdEvent.getEnd().withZoneSameLocal(ZoneOffset.UTC));
    assertEquals(event.getStart().toLocalDate(), createdEvent.getEnd().toLocalDate());

    assertNotNull(createdEvent.getCreated());
    assertNull(createdEvent.getUpdated());
    assertEquals(0, createdEvent.getModifierId());

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(createdEvent.getRecurrence().getOverallEnd().toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());

    assertNotNull(createdEventRecurrence.getOverallStart());
    assertEquals(start.toLocalDate(),
                 createdEventRecurrence.getOverallStart().toLocalDate());

    assertNotNull(createdEventRecurrence.getOverallEnd());
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getOverallEnd().toLocalDate());
  }

  @Test
  public void testCreateEventExceptionalOccurrence() throws Exception { // NOSONAR
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

    try {
      agendaEventService.createEventExceptionalOccurrence(5500l,
                                                          ATTENDEES,
                                                          CONFERENCES,
                                                          REMINDERS,
                                                          start.plusDays(2));
    } catch (AgendaException e) {
      // Expected, not existing id
    }

    try {
      agendaEventService.createEventExceptionalOccurrence(event.getId(),
                                                          ATTENDEES,
                                                          CONFERENCES,
                                                          REMINDERS,
                                                          start.plusDays(3));
    } catch (IllegalStateException e) {
      // Expected, not existing occurrence
    }

    try {
      agendaEventService.createEventExceptionalOccurrence(event.getId(),
                                                          ATTENDEES,
                                                          CONFERENCES,
                                                          REMINDERS,
                                                          start.minusDays(1));
    } catch (IllegalStateException e) {
      // Expected, not existing occurrence
    }

    ZonedDateTime occurrenceId = start.plusDays(1);
    Event exceptionalOccurrence = agendaEventService.createEventExceptionalOccurrence(event.getId(),
                                                                                      ATTENDEES,
                                                                                      CONFERENCES,
                                                                                      REMINDERS,
                                                                                      occurrenceId);

    assertNotNull(exceptionalOccurrence);
    assertNotNull(exceptionalOccurrence.getOccurrence());
    assertEquals(occurrenceId.withZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
                 exceptionalOccurrence.getOccurrence().getId().withZoneSameInstant(ZoneOffset.UTC).toLocalDate());

    long eventId = exceptionalOccurrence.getId();
    assertTrue(eventId > 0);
    assertEquals(event.getId(), exceptionalOccurrence.getParentId());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertTrue(eventAttendees != null && !eventAttendees.isEmpty());

    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    assertTrue(eventConferences != null && !eventConferences.isEmpty());

    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertTrue(eventReminders != null && !eventReminders.isEmpty());
  }

  @Test
  public void testSaveEventExceptionalOccurrenceWithDST() throws Exception { // NOSONAR
    ZoneId dstTimeZone = ZoneId.of("Europe/Paris");
    ZonedDateTime start = ZonedDateTime.of(2021, 9, 15, 10, 0, 0, 0, dstTimeZone);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(dstTimeZone);
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     null,
                                                     0,
                                                     EventRecurrenceType.WEEKLY,
                                                     EventRecurrenceFrequency.WEEKLY,
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

    event = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    ZonedDateTime periodStart = ZonedDateTime.of(2021, 12, 3, 0, 0, 0, 0, dstTimeZone);
    List<Event> eventOccurrencesInPeriod = agendaEventService.getEventOccurrencesInPeriod(event, periodStart, periodStart.plusWeeks(1), dstTimeZone, 0);
    assertNotNull(eventOccurrencesInPeriod);
    assertEquals(1, eventOccurrencesInPeriod.size());
    Event occurrence = eventOccurrencesInPeriod.get(0);
    assertNotNull(occurrence);
    assertNotNull(occurrence.getOccurrence());

    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(event.getId(), occurrence.getOccurrence().getId());
    ZonedDateTime exceptionalEventStart = exceptionalOccurrence.getStart().withZoneSameInstant(dstTimeZone);

    assertNotNull(exceptionalOccurrence);
    assertNotNull(exceptionalOccurrence.getOccurrence());
    assertEquals(LocalDate.of(2021, 12, 8),
                 exceptionalEventStart.toLocalDate());
    assertEquals(start.getHour(),
                 exceptionalEventStart.getHour());
    assertEquals(start.getMinute(),
                 exceptionalEventStart.getMinute());
  }

  @Test
  public void testGetParentRecurrentEvents() throws Exception { // NOSONAR
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
    List<Event> parentRecurrentEvents = agendaEventService.getParentRecurrentEvents(start,
                                                                                    start.plusDays(2),
                                                                                    ZoneId.systemDefault());
    assertNotNull(parentRecurrentEvents);
    assertEquals(1, parentRecurrentEvents.size());

    Event exceptionalOccurrence = agendaEventService.createEventExceptionalOccurrence(event.getId(),
                                                                                      ATTENDEES,
                                                                                      CONFERENCES,
                                                                                      REMINDERS,
                                                                                      start.plusDays(1));

    assertNotNull(exceptionalOccurrence);
    parentRecurrentEvents = agendaEventService.getParentRecurrentEvents(start,
                                                                        start.plusDays(2),
                                                                        ZoneId.systemDefault());
    assertNotNull(parentRecurrentEvents);
    assertEquals(1, parentRecurrentEvents.size());
  }

  @Test
  public void testGetOccurrenceFromRecurrentEventByCount() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);

    int count = 3;
    int interval = 1;
    LocalDate untilDate = start.plusDays(6).toLocalDate();
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     untilDate,
                                                     count,
                                                     EventRecurrenceType.DAILY,
                                                     EventRecurrenceFrequency.DAILY,
                                                     interval,
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

    ZonedDateTime occurrenceDate = event.getStart().withZoneSameInstant(event.getTimeZoneId());
    Event eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                                  occurrenceDate,
                                                                  event.getTimeZoneId(),
                                                                  Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(interval).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(2).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(count).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(2).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);
    assertNotNull(eventOccurrence.getOccurrence());
    assertNotNull(eventOccurrence.getOccurrence().getId());
    assertTrue(eventOccurrence.getId() == 0);
    assertEquals(occurrenceDate.toLocalDate(), eventOccurrence.getOccurrence().getId().toLocalDate());

    assertNotNull(eventOccurrence.getAcl());
    assertTrue(eventOccurrence.getAcl().isCanEdit());
    assertTrue(eventOccurrence.getAcl().isAttendee());
  }

  @Test
  public void testGetOccurrenceFromRecurrentEventByInterval() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);

    int count = 0;
    int interval = 3;
    LocalDate untilDate = start.plusDays(interval * 2l).toLocalDate();
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     untilDate,
                                                     count,
                                                     EventRecurrenceType.DAILY,
                                                     EventRecurrenceFrequency.DAILY,
                                                     interval,
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

    ZonedDateTime occurrenceDate = event.getStart().withZoneSameInstant(event.getTimeZoneId());
    Event eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                                  occurrenceDate,
                                                                  event.getTimeZoneId(),
                                                                  Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(interval).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(interval + 1l).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(interval * 2l).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);
    assertNotNull(eventOccurrence.getOccurrence());
    assertNotNull(eventOccurrence.getOccurrence().getId());
    assertTrue(eventOccurrence.getId() == 0);
    assertEquals(occurrenceDate.toLocalDate(), eventOccurrence.getOccurrence().getId().toLocalDate());

    assertNotNull(eventOccurrence.getAcl());
    assertTrue(eventOccurrence.getAcl().isCanEdit());
    assertTrue(eventOccurrence.getAcl().isAttendee());
  }

  @Test
  public void testGetExceptionalOccurrenceEvent() throws Exception { // NOSONAR
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

    List<Event> exceptionalOccurrenceEvents =
                                            agendaEventService.getExceptionalOccurrenceEvents(2000l,
                                                                                              null,
                                                                                              Long.parseLong(testuser1Identity.getId()));
    assertNotNull(exceptionalOccurrenceEvents);
    assertTrue(exceptionalOccurrenceEvents.isEmpty());

    try {
      agendaEventService.getExceptionalOccurrenceEvents(eventId,
                                                        null,
                                                        Long.parseLong(testuser3Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event exceptionalOccurrence = agendaEventService.createEventExceptionalOccurrence(eventId,
                                                                                      ATTENDEES,
                                                                                      CONFERENCES,
                                                                                      REMINDERS,
                                                                                      start.plusDays(1));

    assertNotNull(exceptionalOccurrence);
    assertTrue(exceptionalOccurrence.getId() > 0);
    assertNotNull(exceptionalOccurrence.getOccurrence());
    assertNotNull(exceptionalOccurrence.getOccurrence().getId());

    exceptionalOccurrenceEvents = agendaEventService.getExceptionalOccurrenceEvents(eventId,
                                                                                    null,
                                                                                    Long.parseLong(testuser1Identity.getId()));

    assertTrue(exceptionalOccurrenceEvents.stream()
                                          .anyMatch(exceptionalOccurrenceEvent -> exceptionalOccurrenceEvent.getId() == exceptionalOccurrence.getId()));

    Event eventOccurrence = agendaEventService.getEventOccurrence(exceptionalOccurrence.getParentId(),
                                                                  exceptionalOccurrence.getOccurrence().getId(),
                                                                  ZoneId.systemDefault(),
                                                                  Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);
    assertEquals(eventOccurrence.getId(), exceptionalOccurrence.getId());

    assertNotNull(eventOccurrence.getAcl());
    assertTrue(eventOccurrence.getAcl().isCanEdit());
    assertTrue(eventOccurrence.getAcl().isAttendee());
  }

  @Test
  public void testUpdateEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = false;

    Event createdEvent = newEventInstance(start, start, allDay);
    createdEvent = createEvent(createdEvent.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = createdEvent.getId();
    Event storedEvent = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(storedEvent);
    assertTrue(storedEvent.getId() > 0);
    assertNotNull(storedEvent.getRecurrence());

    try {
      Event event = new Event();
      event.setId(eventId);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     0l);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEvent(null,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(0);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      event.setParentId(eventId);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now().plusDays(1));
      event.setEnd(ZonedDateTime.now());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     3000l);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      event.setRecurrence(new EventRecurrence());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     3000l);
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(1200);
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser4Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(15000);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      event = agendaEventService.createEvent(event,
                                             Collections.singletonList(new EventAttendee(0,
                                                                                         Long.parseLong(testuser2Identity.getId()),
                                                                                         EventAttendeeResponse.ACCEPTED)),
                                             Collections.emptyList(),
                                             Collections.emptyList(),
                                             null,
                                             null,
                                             true,
                                             Long.parseLong(testuser2Identity.getId()));
      agendaEventService.updateEvent(event,
                                     Collections.singletonList(new EventAttendee(0,
                                                                                 Long.parseLong(testuser2Identity.getId()),
                                                                                 EventAttendeeResponse.ACCEPTED)),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      spaceService.addRedactor(space, testuser1Identity.getRemoteId());

      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      event = agendaEventService.createEvent(event,
                                             Collections.singletonList(new EventAttendee(0,
                                                                                         Long.parseLong(testuser2Identity.getId()),
                                                                                         EventAttendeeResponse.ACCEPTED)),
                                             Collections.emptyList(),
                                             Collections.emptyList(),
                                             null,
                                             null,
                                             true,
                                             Long.parseLong(testuser2Identity.getId()));
      agendaEventService.updateEvent(event,
                                     Collections.singletonList(new EventAttendee(0,
                                                                                 Long.parseLong(testuser2Identity.getId()),
                                                                                 EventAttendeeResponse.ACCEPTED)),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.updateEvent(event,
                                     Collections.singletonList(new EventAttendee(0,
                                                                                 storedEvent.getCreatorId(),
                                                                                 EventAttendeeResponse.ACCEPTED)),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    createdEvent = newEventInstance(start, start, allDay);
    createdEvent.setRecurrence(null);
    createdEvent = createEvent(createdEvent.clone(),
                               Long.parseLong(testuser1Identity.getId()),
                               testuser1Identity,
                               testuser2Identity);

    eventId = createdEvent.getId();

    List<EventReminder> reminders = agendaEventReminderService.getEventReminders(eventId,
                                                                                 Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());

    EventReminder eventReminder = reminders.get(0);
    assertNotNull(eventReminder);

    storedEvent = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser2Identity.getId()));

    storedEvent.setRecurrence(null);

    agendaEventService.updateEvent(storedEvent,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));

    AgendaEventModification eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.REMINDER_DELETED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.CONFERENCE_DELETED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ATTENDEE_DELETED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 4,
                 eventModification.getModificationTypes().size());

    Event updatedEvent = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(updatedEvent);
    assertNull(updatedEvent.getRecurrence());
    assertNull(updatedEvent.getOccurrence());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertTrue(eventAttendees == null || eventAttendees.isEmpty());
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    assertTrue(eventConferences == null || eventConferences.isEmpty());

    updatedEvent.setAllowAttendeeToUpdate(true);
    updatedEvent.setAllowAttendeeToInvite(false);

    EventAttendee eventAttendeeTestUser2 = new EventAttendee(0,
                                                             updatedEvent.getId(),
                                                             Long.parseLong(testuser2Identity.getId()),
                                                             null);
    EventAttendee eventAttendeeTestUser1 = new EventAttendee(0,
                                                             updatedEvent.getId(),
                                                             Long.parseLong(testuser1Identity.getId()),
                                                             null);
    updatedEvent = agendaEventService.updateEvent(updatedEvent,
                                                  Arrays.asList(eventAttendeeTestUser1, eventAttendeeTestUser2),
                                                  null,
                                                  reminders,
                                                  null,
                                                  null,
                                                  false,
                                                  Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.REMINDER_ADDED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ALLOW_MODIFY_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ATTENDEE_ADDED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 4,
                 eventModification.getModificationTypes().size());

    try {
      updatedEvent = agendaEventService.updateEvent(updatedEvent,
                                                    Arrays.asList(eventAttendeeTestUser1, eventAttendeeTestUser2),
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    false,
                                                    Long.parseLong(testuser3Identity.getId()));
      fail("shouldn't allow other attendee to update event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    assertTrue(updatedEvent.isAllowAttendeeToUpdate());
    assertTrue("allowAttendeeToInvite should be true automatically when allowAttendeeToUpdate is set to true",
               updatedEvent.isAllowAttendeeToInvite());

    reminders = agendaEventReminderService.getEventReminders(eventId, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());
    EventReminder sameEventReminder = reminders.get(0);
    assertNotNull(sameEventReminder);
    assertEquals(eventReminder.getDatetime(), sameEventReminder.getDatetime());

    updatedEvent.setAllowAttendeeToUpdate(false);
    updatedEvent.setStart(updatedEvent.getStart().plusDays(1));
    updatedEvent.setEnd(updatedEvent.getEnd().plusDays(1));
    updatedEvent = agendaEventService.updateEvent(updatedEvent,
                                                  Arrays.asList(eventAttendeeTestUser1, eventAttendeeTestUser2),
                                                  null,
                                                  null,
                                                  null,
                                                  null,
                                                  false,
                                                  Long.parseLong(testuser2Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ALLOW_MODIFY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 4,
                 eventModification.getModificationTypes().size());

    assertTrue("Attendees shouldn't be able to modify allowAttendeeToInvite and allowAttendeeToUpdate",
               updatedEvent.isAllowAttendeeToUpdate());
    assertTrue(updatedEvent.isAllowAttendeeToInvite());

    reminders = agendaEventReminderService.getEventReminders(eventId, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());
    sameEventReminder = reminders.get(0);
    assertNotNull(sameEventReminder);
    assertEquals(sameEventReminder.getDatetime(), eventReminder.getDatetime().plusDays(1));
  }

  @Test
  public void testUpdateEventFields() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event createdEvent = newEventInstance(start, start, allDay);
    createdEvent = createEvent(createdEvent.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = createdEvent.getId();
    Event storedEvent = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(storedEvent);
    assertTrue(storedEvent.getId() > 0);
    assertNotNull(storedEvent.getRecurrence());

    try {
      Event event = new Event();
      event.setId(eventId);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     0l);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    Map<String, List<String>> fields = getFields("summary", "fieldValue");
    try {
      agendaEventService.updateEventFields(0,
                                           fields,
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           fields,
                                           true,
                                           true,
                                           0l);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           fields,
                                           true,
                                           true,
                                           2000l);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           fields,
                                           true,
                                           true,
                                           Long.parseLong(testuser5Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", "-1"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", "500000"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("timeZoneId", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("timeZoneId", "Not existant"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (Exception e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("start", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("end", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("start", "2080-10-10"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("end", "2010-10-10"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("recurrence", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("id", "2553"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("parentId", "2553"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("created", "2020-10-10"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("occurrence", "2020-10-10"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("acl", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    createdEvent = newEventInstance(start, start, allDay);
    createdEvent = createEvent(createdEvent.clone(),
                               Long.parseLong(testuser1Identity.getId()),
                               testuser1Identity,
                               testuser2Identity,
                               testuser3Identity);
    eventId = createdEvent.getId();

    List<EventReminder> reminders = agendaEventReminderService.getEventReminders(eventId,
                                                                                 Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());

    EventReminder eventReminder = reminders.get(0);
    assertNotNull(eventReminder);

    String fieldName = "calendarId";
    String fieldValue = String.valueOf(spaceCalendar.getId());
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    AgendaEventModification eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.OWNER_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    Event event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getCalendarId()));

    fieldName = "summary";
    fieldValue = "summaryValue";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.SUMMARY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getSummary()));

    fieldName = "description";
    fieldValue = "descriptionValue";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.DESCRIPTION_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getDescription()));

    fieldName = "location";
    fieldValue = "locationValue";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.LOCATION_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getLocation()));

    fieldName = "color";
    fieldValue = "colorValue";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.COLOR_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getColor()));

    fieldName = "timeZoneId";
    fieldValue = "Europe/Paris";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.TIMEZONE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getTimeZoneId().getId());

    reminders = agendaEventReminderService.getEventReminders(eventId, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());
    EventReminder sameEventReminder = reminders.get(0);
    assertNotNull(sameEventReminder);
    assertEquals(sameEventReminder.getDatetime(), eventReminder.getDatetime());

    fieldName = "start";
    fieldValue = AgendaDateUtils.toRFC3339Date(start.minusDays(1), ZoneId.systemDefault(), allDay);
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, AgendaDateUtils.toRFC3339Date(event.getStart(), ZoneId.systemDefault(), allDay));

    reminders = agendaEventReminderService.getEventReminders(eventId, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());

    EventReminder updatedEventReminder = reminders.get(0);
    assertNotNull(updatedEventReminder);
    assertNotEquals(updatedEventReminder.getDatetime(), eventReminder.getDatetime());

    fieldName = "end";
    fieldValue = AgendaDateUtils.toRFC3339Date(start.plusDays(2), ZoneId.systemDefault(), allDay);
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, AgendaDateUtils.toRFC3339Date(event.getEnd(), ZoneId.systemDefault(), allDay));

    fieldName = "allDay";
    fieldValue = String.valueOf(!allDay);
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 3,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.isAllDay()));

    fieldName = "availability";
    fieldValue = EventAvailability.BUSY.name();
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.AVAILABILITY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getAvailability().name());

    fieldName = "status";
    fieldValue = EventStatus.TENTATIVE.name();
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.STATUS_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 3,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getStatus().name());

    fieldName = "status";
    fieldValue = EventStatus.CONFIRMED.name();
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.STATUS_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 3,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getStatus().name());

    fieldName = "allowAttendeeToUpdate";
    fieldValue = String.valueOf(!event.isAllowAttendeeToUpdate());
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ALLOW_MODIFY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.isAllowAttendeeToUpdate()));

    fieldName = "allowAttendeeToInvite";
    fieldValue = String.valueOf(!event.isAllowAttendeeToInvite());
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ALLOW_INVITE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.isAllowAttendeeToInvite()));
  }

  @Test
  public void testUpdateEvent_InSpace_AsMember() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setCalendarId(spaceCalendar.getId());
    event.setAllowAttendeeToUpdate(true);
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser2Identity,
                                     testuser3Identity);

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    String newDescription = "Desc2";
    createdEvent.setDescription(newDescription);
    agendaEventService.updateEvent(createdEvent,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser2Identity.getId()));

    Event updatedEvent = agendaEventService.getEventById(createdEvent.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(updatedEvent);
    assertEquals(newDescription, updatedEvent.getDescription());

    spaceService.removeMember(space, testuser2Identity.getRemoteId());
    try {
      agendaEventService.updateEvent(updatedEvent,
                                     null,
                                     null,
                                     null,
                                     null,
                                     null,
                                     false,
                                     Long.parseLong(testuser2Identity.getId()));
      fail("testuser2 shouldn't be able to update a previously created event by him, while he's not member of space anymore");
    } catch (IllegalAccessException e) {
      // Expected
    }
  }

  @Test
  public void testDeleteEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = event.getId();
    try {
      agendaEventService.deleteEventById(eventId, Long.parseLong(testuser2Identity.getId()));
      fail("Event with id " + eventId + " shouldn't be deletable by an attendee");
    } catch (IllegalAccessException e) {
      // Expected to have this exception, just check if event really always
      // exists
      event = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
      assertNotNull(event);
    }

    agendaEventService.deleteEventById(eventId, Long.parseLong(testuser1Identity.getId()));
    AgendaEventModification eventModification = eventDeletionReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.DELETED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 1,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
    assertNull(event);

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertTrue(eventAttendees == null || eventAttendees.isEmpty());
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    assertTrue(eventConferences == null || eventConferences.isEmpty());
  }

  @Test
  public void testGetEvents_Recurrent_WithExceptionalEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    ZonedDateTime end = getDate().plusHours(2).withNano(0);

    boolean allDay = false;

    Event event = newEventInstance(start, end, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);
    Event createdEvent = agendaEventService.getEventById(event.getId(),
                                                         ZoneId.systemDefault(),
                                                         Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);
    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              null,
                                              null,
                                              getDate().plusHours(1),
                                              getDate().plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(1, events.size());
    assertEquals(0, events.get(0).getId());

    Event exceptionalEvent = events.get(0).clone();
    exceptionalEvent = createEvent(exceptionalEvent, Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1),
                                  getDate().plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(1, events.size());
    assertTrue(events.get(0).getId() > 0);
    assertEquals(exceptionalEvent.getId(), events.get(0).getId());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1).plusDays(1),
                                  getDate().plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(1, events.size());
    assertEquals(0, events.get(0).getId());

    exceptionalEvent.setEnd(exceptionalEvent.getEnd().plusDays(1));
    exceptionalEvent.setStart(exceptionalEvent.getStart().plusDays(1));
    agendaEventService.updateEvent(exceptionalEvent,
                                   ATTENDEES,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1),
                                  getDate().plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1).plusDays(1),
                                  getDate().plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(2, events.size());
    assertTrue(events.stream().noneMatch(occurrenceEvent -> occurrenceEvent.getOccurrence() == null));
    assertTrue(events.stream().anyMatch(occurrenceEvent -> occurrenceEvent.getOccurrence().isExceptional()));
    assertTrue(events.stream().anyMatch(occurrenceEvent -> !occurrenceEvent.getOccurrence().isExceptional()));
  }

  @Test
  public void testGetEvents_SpaceMembers() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), spaceIdentity);
    Event createdEvent = agendaEventService.getEventById(event.getId(),
                                                         null,
                                                         Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    try {
      agendaEventService.getEventById(event.getId(),
                                      null,
                                      Long.parseLong(testuser4Identity.getId()));
      fail("Should throw an exception when a non member user attempts to access a space event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              null,
                                              null,
                                              getDate().plusHours(1),
                                              getDate().plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser4Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1),
                                  getDate().plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser4Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());
  }

  @Test
  public void testGetEvents() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    Event event = newEventInstance(start, end, false);
    Event event1 = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);
    event1 = agendaEventService.getEventById(event1.getId(),
                                             null,
                                             Long.parseLong(testuser1Identity.getId()));
    assertNotNull(event1);

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              null,
                                              null,
                                              start,
                                              end,
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    Event occurrenceEvent = events.get(0);
    assertEquals(0, occurrenceEvent.getId());
    assertEquals(event1.getId(), occurrenceEvent.getParentId());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    assertEquals(0, occurrenceEvent.getId());
    assertEquals(event1.getId(), occurrenceEvent.getParentId());

    event = newEventInstance(start, start, true);
    createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(2, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(4, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  event1.getRecurrence()
                                        .getUntil()
                                        .plusDays(2)
                                        .atStartOfDay(ZoneId.systemDefault()),
                                  event1.getRecurrence()
                                        .getUntil()
                                        .plusDays(4)
                                        .atStartOfDay(ZoneId.systemDefault())
                                        .minusSeconds(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(6, events.size());

    spaceService.deleteSpace(space);

    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(4, events.size());
  }

  @Test
  public void testGetEventsByOwner() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    long testuser1Id = Long.parseLong(testuser1Identity.getId());
    try {
      EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                                Collections.singletonList(testuser1Id),
                                                null,
                                                date.plusHours(1),
                                                date.plusMinutes(90),
                                                0);
      agendaEventService.getEvents(eventFilter,
                                   ZoneId.systemDefault(),
                                   Long.parseLong(testuser2Identity.getId()));
      fail("User 'testuser2' shouldn't be able to access calendar of user 'testuser1'");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event event = newEventInstance(start, end, false);
    Event event1 = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);
    event1 = agendaEventService.getEventById(event1.getId(),
                                             null,
                                             Long.parseLong(testuser1Identity.getId()));

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser1Identity.getId()),
                                              Collections.singletonList(testuser1Id),
                                              null,
                                              date.plusHours(1),
                                              date.plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    Event occurrenceEvent = events.get(0);
    assertEquals(0, occurrenceEvent.getId());
    assertEquals(event1.getId(), occurrenceEvent.getParentId());

    eventFilter = new EventFilter(Long.parseLong(testuser1Identity.getId()),
                                  Collections.singletonList(testuser1Id),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser1Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    assertEquals(0, occurrenceEvent.getId());
    assertEquals(event1.getId(), occurrenceEvent.getParentId());

    event = newEventInstance(start, start, true);
    createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    eventFilter = new EventFilter(Long.parseLong(testuser1Identity.getId()),
                                  Collections.singletonList(testuser1Id),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser1Identity.getId()));
    assertNotNull(events);
    assertEquals(2, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser1Identity.getId()),
                                  Collections.singletonList(testuser1Id),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser1Identity.getId()));
    assertNotNull(events);
    assertEquals(4, events.size());
  }

  @Test
  public void testGetEventsByOwnersAndAttendee() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    long testuser1Id = Long.parseLong(testuser1Identity.getId());
    try {
      EventFilter eventFilter = new EventFilter(testuser1Id,
                                                Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                                null,
                                                date.plusHours(1),
                                                date.plusMinutes(90),
                                                0);
      agendaEventService.getEvents(eventFilter,
                                   ZoneId.systemDefault(),
                                   Long.parseLong(testuser2Identity.getId()));
      fail("User 'testuser2' shouldn't be able to access calendar of user 'testuser1'");
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      EventFilter eventFilter = new EventFilter(Long.parseLong(testuser5Identity.getId()),
                                                Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                                null,
                                                date.plusHours(1),
                                                date.plusMinutes(90),
                                                0);
      agendaEventService.getEvents(eventFilter,
                                   ZoneId.systemDefault(),
                                   Long.parseLong(testuser5Identity.getId()));
      fail("User 'testuser2' shouldn't be able to access calendar of user 'testuser1'");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);
    event = agendaEventService.getEventById(event.getId(),
                                            null,
                                            Long.parseLong(testuser1Identity.getId()));

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                              null,
                                              date.plusHours(1),
                                              date.plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(1, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  Collections.singletonList(Long.parseLong(testuser2Identity.getId())),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()),
                                  Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    eventAttendees.add(new EventAttendee(0, event.getId(), Long.parseLong(spaceIdentity.getId()), null));
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  testuser1Id,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              testuser1Id,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));

    eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()),
                                  Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());
  }

  @Test
  public void testGetEventsByLimit() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();
    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    Event event = newEventInstance(start, end, false);
    event.setRecurrence(new EventRecurrence(0,
                                            null,
                                            0,
                                            EventRecurrenceType.DAILY,
                                            EventRecurrenceFrequency.DAILY,
                                            2,
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
    event.setCalendarId(spaceCalendar.getId());
    createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                              null,
                                              date.plusHours(1),
                                              null,
                                              10);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(10, events.size());
  }

  @Test
  public void testGetEventsByAttendee() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    long testuser1Id = Long.parseLong(testuser1Identity.getId());
    try {
      EventFilter eventFilter = new EventFilter(testuser1Id,
                                                null,
                                                null,
                                                date.plusHours(1),
                                                date.plusMinutes(90),
                                                0);
      agendaEventService.getEvents(eventFilter,
                                   ZoneId.systemDefault(),
                                   Long.parseLong(testuser2Identity.getId()));
      fail("User 'testuser2' shouldn't be able to access calendar of user 'testuser1'");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);
    event = agendaEventService.getEventById(event.getId(),
                                            null,
                                            Long.parseLong(testuser1Identity.getId()));

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              null,
                                              null,
                                              date.plusHours(1),
                                              date.plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    eventAttendees.add(new EventAttendee(0, event.getId(), Long.parseLong(spaceIdentity.getId()), null));
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  testuser1Id,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              testuser1Id,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));

    eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser4Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser4Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    eventAttendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    eventAttendees.add(new EventAttendee(0, event.getId(), Long.parseLong(testuser4Identity.getId()), null));
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  testuser1Id,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              testuser1Id,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));

    eventFilter = new EventFilter(Long.parseLong(testuser4Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser4Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());
  }

  @Test
  public void testGetEventsByResponseTypes() throws Exception {
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    Event event = newEventInstance(start, end, false);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);

    List<EventAttendeeResponse> responseTypes = new ArrayList<>();
    List<Event> events = new ArrayList<>();
    agendaEventAttendeeService.sendEventResponse(event.getId(),
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);
    responseTypes.add(EventAttendeeResponse.TENTATIVE);
    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()), null, responseTypes, start, end, 0);
    try {
      events = agendaEventService.getEvents(eventFilter,
                                            ZoneId.systemDefault(),
                                            Long.parseLong(testuser3Identity.getId()));
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertNotNull(events);
    assertEquals(1, events.size());

    event = newEventInstance(start, end, false);
    Event event1 = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);
    agendaEventAttendeeService.sendEventResponse(event1.getId(),
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);

    List<EventAttendeeResponse> responseTypes1 = new ArrayList<>();
    responseTypes1.add(EventAttendeeResponse.TENTATIVE);
    EventFilter eventFilter1 = new EventFilter(Long.parseLong(testuser3Identity.getId()), null, responseTypes1, start, end, 0);
    events = agendaEventService.getEvents(eventFilter1,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));

    assertNotNull(events);
    assertEquals(2, events.size());

    event = newEventInstance(start, end, false);
    Event event2 = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);
    agendaEventAttendeeService.sendEventResponse(event2.getId(),
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    List<EventAttendeeResponse> responseTypes2 = new ArrayList<>();
    responseTypes2.add(EventAttendeeResponse.TENTATIVE);
    responseTypes2.add(EventAttendeeResponse.ACCEPTED);
    EventFilter eventFilter2 = new EventFilter(Long.parseLong(testuser3Identity.getId()), null, responseTypes2, start, end, 0);
    events = agendaEventService.getEvents(eventFilter2,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(3, events.size());
  }

  @Test
  public void testCountPendingEvents() throws Exception {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    long countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(0, countPendingEvents);

    ZonedDateTime occurrenceId = start.plusDays(1);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId.plusDays(1));
    Event exceptionalOccurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceId);
    assertNotNull(exceptionalOccurrenceEvent);
    assertNotNull(exceptionalOccurrenceEvent.getOccurrence());

    countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(0, countPendingEvents);

    String exceptionalEventEndDateRFC3339 = AgendaDateUtils.toRFC3339Date(exceptionalOccurrenceEvent.getEnd().plusHours(1));
    Map<String, List<String>> dateFields = getFields("end", exceptionalEventEndDateRFC3339);
    agendaEventService.updateEventFields(exceptionalOccurrenceEvent.getId(), dateFields, false, false, userIdentityId);

    countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(0, countPendingEvents);

    exceptionalOccurrenceEvent = agendaEventService.getEventById(exceptionalOccurrenceEvent.getId());
    List<EventAttendee> eventAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId)
                                                                                   .getEventAttendees());
    eventAttendees.add(new EventAttendee(0,
                                         0,
                                         Long.parseLong(testuser5Identity.getId()),
                                         null));

    agendaEventService.updateEvent(exceptionalOccurrenceEvent,
                                   eventAttendees,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(1, countPendingEvents);
  }

  @Test
  public void testCountPastPendingEvents() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().minusHours(1);
    ZonedDateTime end = ZonedDateTime.now().minusMinutes(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    event.setRecurrence(null);
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    long countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(0, countPendingEvents);
  }

  @Test
  public void testGetPendingEvents() throws Exception {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    List<Event> pendingEvents = agendaEventService.getPendingEvents(null, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    ZonedDateTime occurrenceId = start.plusDays(1);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId.plusDays(1));
    Event exceptionalOccurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceId);
    assertNotNull(exceptionalOccurrenceEvent);
    assertNotNull(exceptionalOccurrenceEvent.getOccurrence());

    pendingEvents = agendaEventService.getPendingEvents(null, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    String exceptionalEventEndDateRFC3339 = AgendaDateUtils.toRFC3339Date(exceptionalOccurrenceEvent.getEnd().plusHours(1));
    Map<String, List<String>> dateFields = getFields("end", exceptionalEventEndDateRFC3339);
    agendaEventService.updateEventFields(exceptionalOccurrenceEvent.getId(), dateFields, false, false, userIdentityId);

    pendingEvents = agendaEventService.getPendingEvents(null, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    exceptionalOccurrenceEvent = agendaEventService.getEventById(exceptionalOccurrenceEvent.getId());
    List<EventAttendee> eventAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId)
                                                                                   .getEventAttendees());
    eventAttendees.add(new EventAttendee(0,
                                         0,
                                         Long.parseLong(testuser5Identity.getId()),
                                         null));

    agendaEventService.updateEvent(exceptionalOccurrenceEvent,
                                   eventAttendees,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    pendingEvents = agendaEventService.getPendingEvents(null, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
  }

  @Test
  public void testCountPendingEventsByOwnerIds() throws Exception {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    List<Long> ownerIds = Collections.singletonList(Long.parseLong(spaceIdentity.getId()));
    long countPendingEvents = agendaEventService.countPendingEvents(ownerIds, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);
    try {
      agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser4Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }
    try {
      agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser5Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    ZonedDateTime occurrenceId = start.plusDays(1);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId.plusDays(1));
    Event exceptionalOccurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceId);
    assertNotNull(exceptionalOccurrenceEvent);
    assertNotNull(exceptionalOccurrenceEvent.getOccurrence());

    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);

    String exceptionalEventEndDateRFC3339 = AgendaDateUtils.toRFC3339Date(exceptionalOccurrenceEvent.getEnd().plusHours(1));
    Map<String, List<String>> dateFields = getFields("end", exceptionalEventEndDateRFC3339);
    agendaEventService.updateEventFields(exceptionalOccurrenceEvent.getId(), dateFields, false, false, userIdentityId);

    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()));
    assertEquals(1, countPendingEvents);

    exceptionalOccurrenceEvent = agendaEventService.getEventById(exceptionalOccurrenceEvent.getId());
    List<EventAttendee> eventAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId)
                                                                                   .getEventAttendees());
    eventAttendees.add(new EventAttendee(0,
                                         0,
                                         Long.parseLong(testuser5Identity.getId()),
                                         null));

    agendaEventService.updateEvent(exceptionalOccurrenceEvent,
                                   eventAttendees,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()));
    assertEquals(1, countPendingEvents);
  }

  @Test
  public void testGetPendingEventsByOwnerIds() throws Exception {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    List<Long> ownerIds = Collections.singletonList(Long.parseLong(spaceIdentity.getId()));
    List<Event> pendingEvents = agendaEventService.getPendingEvents(ownerIds, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    try {
      agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }
    try {
      agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    ZonedDateTime occurrenceId = start.plusDays(1);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId.plusDays(1));
    Event exceptionalOccurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceId);
    assertNotNull(exceptionalOccurrenceEvent);
    assertNotNull(exceptionalOccurrenceEvent.getOccurrence());

    pendingEvents = agendaEventService.getPendingEvents(ownerIds, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    String exceptionalEventEndDateRFC3339 = AgendaDateUtils.toRFC3339Date(exceptionalOccurrenceEvent.getEnd().plusHours(1));
    Map<String, List<String>> dateFields = getFields("end", exceptionalEventEndDateRFC3339);
    agendaEventService.updateEventFields(exceptionalOccurrenceEvent.getId(), dateFields, false, false, userIdentityId);

    pendingEvents = agendaEventService.getPendingEvents(ownerIds, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());

    exceptionalOccurrenceEvent = agendaEventService.getEventById(exceptionalOccurrenceEvent.getId());
    List<EventAttendee> eventAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId)
                                                                                   .getEventAttendees());
    eventAttendees.add(new EventAttendee(0,
                                         0,
                                         Long.parseLong(testuser5Identity.getId()),
                                         null));

    agendaEventService.updateEvent(exceptionalOccurrenceEvent,
                                   eventAttendees,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    pendingEvents = agendaEventService.getPendingEvents(ownerIds, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
  }

  @Test
  public void testAddIcsFile() throws IOException {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    ZoneId dstTimeZone = ZoneId.of("Europe/Paris");

    NotificationInfo notification = new NotificationInfo();
    notification.with(STORED_PARAMETER_EVENT_TITLE, "eventSummary")
            .with(STORED_PARAMETER_EVENT_CREATOR, "Root Root")
            .with(STORED_PARAMETER_EVENT_START_DATE, AgendaDateUtils.toRFC3339Date(start))
            .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date(end));

    MessageInfo messageInfo = new MessageInfo();
    addIcsFile(notification, messageInfo, dstTimeZone);
    assertNotNull(messageInfo.getAttachment());
    assertEquals(1, messageInfo.getAttachment().size());
    String text = new String(messageInfo.getAttachment().get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    List<String> lines = text.contains("\r\n") ? List.of(text.split("\r\n")) : List.of(text.split("\n"));
    String dtStart = lines.stream().filter(s -> s.contains("DTSTART")).findAny().get();
    String dtEnd = lines.stream().filter(s -> s.contains("DTEND")).findAny().get();
    assertNotNull(dtStart);
    assertNotNull(dtEnd);
    String icsStartDate = dtStart.substring(dtStart.indexOf(":") + 1);
    String icsEndDate = dtEnd.substring(dtEnd.indexOf(":") + 1);

    String startDateRFC3339 = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_START_DATE);
    ZonedDateTime startDate = ZonedDateTime.parse(startDateRFC3339).withZoneSameInstant(dstTimeZone);
    String startDateFormatted = AgendaDateUtils.formatDateTimeWithSeconds(startDate);

    String endDateRFC3339 = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_END_DATE);
    ZonedDateTime endDate = ZonedDateTime.parse(endDateRFC3339).withZoneSameInstant(dstTimeZone);
    String endDateFormatted = AgendaDateUtils.formatDateTimeWithSeconds(endDate);

    assertEquals(icsStartDate, startDateFormatted);
    assertEquals(icsEndDate, endDateFormatted);
  }
}
