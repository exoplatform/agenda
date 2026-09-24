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
import static org.exoplatform.agenda.util.Utils.generateIcsFile;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.mail.Attachment;
import org.junit.Test;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;

public class AgendaEventServiceTest extends BaseAgendaEventTest {

  /** A link back to the event in eXo, of the shape NotificationUtils mints. */
  private static final String EVENT_LINK      = "http://localhost:8080/portal/dw/agenda?eventId=42";

  /** The video call, which is a different thing and has its own property. */
  private static final String CONFERENCE_LINK = "https://meet.example.com/room";


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
  public void testBusyEventsAreOnlyAcceptedOnes() throws Exception { // NOSONAR
    // Backs the MCP free/busy primitive (get_availability / suggest_meeting_time / conflicts): a user is busy only for
    // events they ACCEPTED. An invited-but-not-yet-accepted event must NOT make them busy; accepting flips it to busy.
    ZonedDateTime start = getDate().withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null); // single (non-recurring) event
    event = createEvent(event.clone(),
                        Long.parseLong(testuser1Identity.getId()),
                        testuser1Identity,
                        testuser2Identity);

    long user2 = Long.parseLong(testuser2Identity.getId());
    EventFilter acceptedFilter = new EventFilter(user2,
                                                 null,
                                                 Collections.singletonList(EventAttendeeResponse.ACCEPTED),
                                                 start.minusHours(1),
                                                 start.plusHours(2),
                                                 10);
    // testuser2 was only invited -> NEEDS_ACTION -> not busy
    List<Event> before = agendaEventService.getEvents(acceptedFilter.clone(), ZoneOffset.UTC, user2);
    assertTrue("an invited-but-not-accepted event must NOT count as busy", before.isEmpty());

    // testuser2 accepts -> now busy
    agendaEventAttendeeService.sendEventResponse(event.getId(), user2, EventAttendeeResponse.ACCEPTED);
    List<Event> after = agendaEventService.getEvents(acceptedFilter.clone(), ZoneOffset.UTC, user2);
    assertEquals("an accepted event must count as busy", 1, after.size());
  }

  @Test
  public void testCancelSingleOccurrenceKeepsSurroundingOccurrences() throws Exception { // NOSONAR
    // Regression for EXO-88461: cancelling ONE occurrence of a recurring series must remove only that date and keep
    // every occurrence before AND after it (the MCP cancel_agenda_event(occurrence_id) bug used to drop this-and-future).
    ZoneId timeZone = ZoneOffset.UTC;
    ZonedDateTime start = ZonedDateTime.of(2022, 3, 7, 10, 0, 0, 0, timeZone);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(timeZone);
    // Weekly, 6 occurrences
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     null,
                                                     6,
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

    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusWeeks(6).plusDays(1);
    List<Event> before = agendaEventService.getEventOccurrencesInPeriod(event, periodStart, periodEnd, timeZone, 0);
    assertEquals("expected 6 weekly occurrences before cancellation", 6, before.size());
    LocalDate secondDate = before.get(1).getStart().withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    LocalDate thirdDate = before.get(2).getStart().withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    LocalDate fourthDate = before.get(3).getStart().withZoneSameInstant(ZoneOffset.UTC).toLocalDate();

    // Cancel ONLY the 3rd occurrence exactly as the MCP tool does: materialize the exceptional occurrence, then
    // set its status to CANCELLED.
    Event exceptional = agendaEventService.saveEventExceptionalOccurrence(event.getId(),
                                                                          before.get(2).getOccurrence().getId());
    agendaEventService.updateEventFields(exceptional.getId(),
                                         Collections.singletonMap("status",
                                                                  Collections.singletonList(EventStatus.CANCELLED.name())),
                                         false,
                                         false,
                                         Long.parseLong(testuser1Identity.getId()));

    // The cancelled exceptional occurrence must be persisted as CANCELLED (so it is hidden from listings)
    assertEquals(EventStatus.CANCELLED, agendaEventService.getEventById(exceptional.getId()).getStatus());

    List<Event> after = agendaEventService.getEventOccurrencesInPeriod(event, periodStart, periodEnd, timeZone, 0);
    List<LocalDate> remainingDates = after.stream()
                                          .map(o -> o.getStart().withZoneSameInstant(ZoneOffset.UTC).toLocalDate())
                                          .toList();
    assertEquals("expected 5 occurrences after cancelling one", 5, after.size());
    assertFalse("cancelled 3rd occurrence must be gone", remainingDates.contains(thirdDate));
    // The occurrences surrounding the cancelled one (before AND after it) must still exist
    assertTrue("occurrence before the cancelled date must remain", remainingDates.contains(secondDate));
    assertTrue("occurrence after the cancelled date must remain", remainingDates.contains(fourthDate));
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
      event.setParameters(createdEvent.getParameters());
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

    // EXO-90322: the same walk for the visibility, because this is the one path
    // that can un-mask a private event — a blank value resets it to DEFAULT —
    // and because the modification set is what decides whether a connector
    // rewrites every attendee's synchronised copy
    fieldName = "visibility";
    fieldValue = EventVisibility.PRIVATE.name();
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.VISIBILITY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getVisibility().name());

    fieldValue = "";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    event = agendaEventService.getEventById(eventId);
    assertEquals("a blank value resets the visibility to DEFAULT, which publishes in full",
                 EventVisibility.DEFAULT,
                 event.getVisibility());

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

  /**
   * An update that does not state the visibility or the availability keeps the
   * stored ones, and an update that states them stores what it states. The
   * caller shape is caldav-integration's inbound sync, whose
   * IcsEventMapper.toEvent builds a fresh Event with no visibility before
   * calling updateEvent: a PRIVATE event must stay masked on the calendar-link
   * feed after it. An explicit reset stays updateEventFields' blank value,
   * pinned in testUpdateEventFields. The stated branch is the form's own save
   * path (a full PUT through updateEvent), so a guard that always kept the stored
   * value would make the visibility and availability selects no-ops.
   *
   * @throws Exception when the update fails
   */
  @Test
  public void testUpdateEventWithoutVisibilityKeepsTheStoredOne() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    Event createdEvent = newEventInstance(start, start, false);
    createdEvent = createEvent(createdEvent.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);
    long eventId = createdEvent.getId();
    agendaEventService.updateEventFields(eventId,
                                         getFields("visibility", EventVisibility.PRIVATE.name()),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    agendaEventService.updateEventFields(eventId,
                                         getFields("availability", EventAvailability.FREE.name()),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));

    Event event = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
    event.setVisibility(null);
    event.setAvailability(null);
    agendaEventService.updateEvent(event,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));

    Event stored = agendaEventService.getEventById(eventId);
    assertEquals("an update that states no visibility must not un-mask a private event",
                 EventVisibility.PRIVATE,
                 stored.getVisibility());
    assertEquals("an update that states no availability must not turn a free event busy",
                 EventAvailability.FREE,
                 stored.getAvailability());

    event = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
    event.setVisibility(EventVisibility.PUBLIC);
    event.setAvailability(EventAvailability.BUSY);
    agendaEventService.updateEvent(event,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));

    stored = agendaEventService.getEventById(eventId);
    assertEquals("a stated visibility is stored", EventVisibility.PUBLIC, stored.getVisibility());
    assertEquals("a stated availability is stored", EventAvailability.BUSY, stored.getAvailability());
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

    RequestLifeCycle.restartTransaction();
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
            .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date(end))
            .with(STORED_PARAMETER_EVENT_LOCATION, "eXo office, Earth");

    MessageInfo messageInfo = new MessageInfo();
    Attachment attachment = new Attachment();

    byte[] icsContent = generateIcsFile("42",
            "ownerId",
            "eventSummary",
            "eventDescription",
            AgendaDateUtils.toRFC3339Date(start),
            AgendaDateUtils.toRFC3339Date(end),
            "eventConference",
            "eventModifierId",
            "eventCreator",
            "location",
            "https://exo.example.com/portal/dw/agenda?eventId=42",
            EventAvailability.DEFAULT,
            Locale.getDefault(),
            dstTimeZone);
    attachment.setMimeType("text/calendar;charset=utf-8;method=PUBLISH");
    attachment.setInputStream(new ByteArrayInputStream(icsContent));
    messageInfo.addAttachment(attachment);
    assertNotNull(messageInfo.getAttachment());
    assertEquals(1, messageInfo.getAttachment().size());
    String text = new String(messageInfo.getAttachment().get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    List<String> lines = text.contains("\r\n") ? List.of(text.split("\r\n")) : List.of(text.split("\n"));
    String dtStart = lines.stream().filter(s -> s.contains("DTSTART;TZID")).findAny().get();
    String dtEnd = lines.stream().filter(s -> s.contains("DTEND;TZID")).findAny().get();
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

  /**
   * Reads the iCalendar document eXo would mail for an event, already unfolded
   * so a property can be matched whole.
   *
   * <p>
   * RFC 5545 &sect;3.1 lets a writer break any line after 75 octets and
   * continue it with a leading space, which the eXo writer does: without
   * unfolding, a DESCRIPTION long enough to be broken cannot be asserted on.
   *
   * @param eventDescription description to pass to the generator, HTML as the
   *          editor would store it
   * @param userLocale locale the labels are read in
   * @param eventModifierId identity id to write as ORGANIZER, blank for none
   * @return the unfolded document
   */
  private String generateIcs(String eventDescription, Locale userLocale, String eventModifierId) {
    return generateIcs(eventDescription, userLocale, eventModifierId, EVENT_LINK);
  }

  /**
   * The same, choosing what link back to eXo the document is given — null
   * standing for the guest case, where the caller withholds it.
   *
   * @param eventDescription description to pass to the generator, HTML as the
   *          editor would store it
   * @param userLocale locale the labels are read in
   * @param eventModifierId identity id to write as ORGANIZER, blank for none
   * @param eventUrl link back to the event in eXo, null for a guest
   * @return the unfolded document
   */
  private String generateIcs(String eventDescription, Locale userLocale, String eventModifierId, String eventUrl) {
    return generateIcs(eventDescription, userLocale, eventModifierId, eventUrl, EventAvailability.DEFAULT);
  }

  /**
   * The same, choosing what the event does to the recipient's time.
   *
   * @param eventDescription description to pass to the generator, HTML as the
   *          editor would store it
   * @param userLocale locale the labels are read in
   * @param eventModifierId identity id to write as ORGANIZER, blank for none
   * @param eventUrl link back to the event in eXo, null for a guest
   * @param availability what the organiser chose on the form, null for an
   *          event that carries none
   * @return the unfolded document
   */
  private String generateIcs(String eventDescription,
                             Locale userLocale,
                             String eventModifierId,
                             String eventUrl,
                             EventAvailability availability) {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    byte[] icsContent = generateIcsFile("42",
                                        spaceIdentity.getId(),
                                        "eventSummary",
                                        eventDescription,
                                        AgendaDateUtils.toRFC3339Date(start),
                                        AgendaDateUtils.toRFC3339Date(end),
                                        CONFERENCE_LINK,
                                        eventModifierId,
                                        "Root Root",
                                        "location",
                                        eventUrl,
                                        availability,
                                        userLocale,
                                        ZoneId.of("Europe/Paris"));
    String text = new String(icsContent, StandardCharsets.UTF_8);
    return text.replace("\r\n ", "").replace("\r\n\t", "").replace("\n ", "").replace("\n\t", "");
  }

  /**
   * Reads one property out of an unfolded iCalendar document.
   *
   * @param ics unfolded document
   * @param propertyName property name, without its parameters
   * @return the whole property line, or null when the document has none
   */
  private String icsProperty(String ics, String propertyName) {
    return Arrays.stream(ics.split("\\R"))
                 .filter(line -> line.equals(propertyName) || line.startsWith(propertyName + ":")
                     || line.startsWith(propertyName + ";"))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * EXO-90327: the document a recipient's calendar imports — the invitation
   * mail's <code>event.ics</code> and the <code>/ics</code> download, both
   * written by {@code Utils.generateIcsFile} — says what the organiser chose on
   * the form. A <code>FREE</code> event is transparent; <code>BUSY</code>,
   * <code>DEFAULT</code> and an event that carries none are opaque.
   * <p>
   * <strong>TRANSP has to be present, not merely correct.</strong> RFC 5545
   * §3.8.2.7 makes an absent <code>TRANSP</code> default to
   * <code>OPAQUE</code>, so the document that omits it books the slot — which
   * is what this generator did before EXO-90327, and what no test could see,
   * because a missing property and a busy one are the same answer. The pin
   * therefore asserts the line, not just the value.
   *
   */
  @Test
  public void testIcsCarriesTheAvailabilityAsTransp() {
    Map<EventAvailability, String> expected = new LinkedHashMap<>();
    expected.put(EventAvailability.FREE, "TRANSP:TRANSPARENT");
    expected.put(EventAvailability.BUSY, "TRANSP:OPAQUE");
    expected.put(EventAvailability.DEFAULT, "TRANSP:OPAQUE");
    expected.put(null, "TRANSP:OPAQUE");

    expected.forEach((availability, line) -> {
      String ics = generateIcs("eventDescription", Locale.ENGLISH, "", EVENT_LINK, availability);
      assertEquals("availability " + availability + " must be written as " + line, line, icsProperty(ics, "TRANSP"));
    });
  }

  /**
   * PRODID must carry its value alone: the writer emits the property name
   * itself, so a value that repeats it puts PRODID:PRODID: on the wire.
   */
  @Test
  public void testIcsFileNamesTheProductOnce() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");
    String prodId = icsProperty(ics, "PRODID");
    assertNotNull("the document must name the product that wrote it", prodId);
    assertFalse("PRODID must not repeat its own property name: " + prodId, prodId.startsWith("PRODID:PRODID:"));
    assertTrue("PRODID value must be an FPI: " + prodId, prodId.startsWith("PRODID:-//"));
  }

  /**
   * The body must declare the same method as the MIME part that carries it,
   * and that method is PUBLISH: answering is done through the links in the
   * mail, not through iMIP.
   */
  @Test
  public void testIcsFileDeclaresItsMethodInTheBody() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");
    assertEquals("METHOD:PUBLISH", icsProperty(ics, "METHOD"));
  }

  /**
   * ORGANIZER carries a CAL-ADDRESS, which RFC 5545 &sect;3.3.3 defines as a
   * URI: a bare mail address has no scheme and is dropped by a client that
   * validates the value.
   */
  @Test
  public void testIcsFileWritesOrganizerAsAMailtoUri() {
    Profile profile = testuser1Identity.getProfile();
    profile.setProperty(Profile.EMAIL, "testuser1@example.com");
    identityManager.updateProfile(profile);

    String ics = generateIcs(null, Locale.ENGLISH, testuser1Identity.getId());
    String organizer = icsProperty(ics, "ORGANIZER");
    assertNotNull("the invitation must say who sent it", organizer);
    assertTrue("ORGANIZER must be a mailto: URI: " + organizer, organizer.endsWith(":mailto:testuser1@example.com"));
  }

  /**
   * DESCRIPTION is plain text by definition; the HTML flavour belongs to
   * X-ALT-DESC, which must keep carrying it.
   */
  @Test
  public void testIcsFileDescriptionIsPlainTextAndAltDescIsHtml() {
    String ics = generateIcs("<p>Bring the <b>slides</b>.</p>", Locale.ENGLISH, "unknownModifier");

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertFalse("DESCRIPTION must not carry markup: " + description, description.contains("<"));
    assertFalse("DESCRIPTION must not carry markup: " + description, description.contains("&lt;"));
    assertTrue("DESCRIPTION must keep the text of the description: " + description,
               description.contains("Bring the slides."));

    String altDescription = icsProperty(ics, "X-ALT-DESC");
    assertNotNull("the HTML flavour must still be offered", altDescription);
    assertTrue("X-ALT-DESC must carry the HTML: " + altDescription, altDescription.contains("<html><body>"));
    assertTrue("X-ALT-DESC must be typed as HTML: " + altDescription, altDescription.contains("FMTTYPE=text/html"));
  }

  /**
   * The space attribution is what the invitation is for: it must survive
   * DESCRIPTION becoming plain text.
   */
  @Test
  public void testIcsFileStillAttributesTheInvitationToItsSpace() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");
    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertTrue("the sender must be named: " + description, description.contains("Root Root"));
    assertTrue("the space must be named: " + description, description.contains(space.getDisplayName()));
  }

  /**
   * An event belonging to no space attributes itself to its sender alone.
   *
   * <p>
   * The attribution used to be concatenated unconditionally, so an event on a
   * personal calendar — whose owner identity resolves to no space — read
   * "Invitation sent by Root Root in space null". Harmless-looking in a mail
   * nobody re-reads, and much less so now that the very same sentence is what
   * the CalDAV copy carries into the user's own calendar (EXO-89732): the
   * clause is dropped when there is no space to name.
   */
  @Test
  public void testIcsFileNamesNoSpaceWhenTheEventBelongsToNone() {
    byte[] icsContent = generateIcsFile("42",
                                              testuser1Identity.getId(),
                                              "eventSummary",
                                              null,
                                              AgendaDateUtils.toRFC3339Date(getDate()),
                                              AgendaDateUtils.toRFC3339Date(getDate().plusHours(1)),
                                              null,
                                              "unknownModifier",
                                              "Root Root",
                                              "location",
                                              EVENT_LINK,
                                              EventAvailability.DEFAULT,
                                              Locale.ENGLISH,
                                              ZoneId.of("Europe/Paris"));
    String ics = new String(icsContent, StandardCharsets.UTF_8).replace("\r\n ", "").replace("\n ", "");

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertTrue("the sender must still be named: " + description, description.contains("Root Root"));
    assertFalse("an absent space must not be written as the word null: " + description, description.contains("null"));
    assertFalse("and the clause introducing it must be dropped with it: " + description, description.contains("in space"));
  }

  /**
   * URL means "where this event lives" (RFC 5545 &sect;3.8.4.6), so it must
   * name the event in eXo — not the video call, which is a different thing and
   * has a property of its own.
   *
   * <p>
   * The mailed document used to set URL from the conference link, which both
   * said the wrong thing and left the event's own address out of the document
   * altogether (EXO-89751).
   */
  @Test
  public void testIcsFileUrlIsTheEventAndNotTheConference() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");

    String url = icsProperty(ics, "URL");
    assertNotNull("the mailed document must say where the event lives", url);
    assertEquals("URL must be the event in eXo", "URL:" + EVENT_LINK, url);
    assertFalse("URL must not be the conference link: " + url, url.contains(CONFERENCE_LINK));
  }

  /**
   * The link is also written into the description, beside the conference line
   * already there: many calendar clients never surface URL, and the
   * description is what a person reads.
   */
  @Test
  public void testIcsFileDescriptionCarriesTheEventLinkBesideTheConferenceOne() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertTrue("the description must carry the event link: " + description, description.contains(EVENT_LINK));
    // The label reads as its key here: no resource bundle is registered in
    // the test container, and EventIcsBuilder answers the key rather than
    // failing the push. What is pinned is that the line is introduced by the
    // agenda.eventLink label at all — the English text of it lives in
    // Agenda_en.properties.
    assertTrue("and it must be labelled: " + description, description.contains("agenda.eventLink " + EVENT_LINK));
    assertTrue("the conference line must still be there: " + description, description.contains(CONFERENCE_LINK));

    String altDescription = icsProperty(ics, "X-ALT-DESC");
    assertNotNull(altDescription);
    assertTrue("the HTML flavour must carry it too: " + altDescription, altDescription.contains(EVENT_LINK));
  }

  /**
   * A guest has no eXo account, so the link lands them on a login screen. The
   * mail is the one channel that knows who it is going to, and it withholds
   * the link there — from URL and from the description alike.
   */
  @Test
  public void testIcsFileWithholdsTheEventLinkFromAGuest() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier", null);

    assertNull("a guest's document must carry no URL: " + icsProperty(ics, "URL"), icsProperty(ics, "URL"));

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertFalse("nor the labelled line: " + description, description.contains("agenda.eventLink"));
    assertFalse("nor the link anywhere in it: " + description, description.contains("agenda?eventId="));
    assertTrue("the conference link is not withheld — a guest can join the call: " + description,
               description.contains(CONFERENCE_LINK));

    String altDescription = icsProperty(ics, "X-ALT-DESC");
    assertNotNull(altDescription);
    assertFalse("nor the HTML flavour of the line: " + altDescription, altDescription.contains("agenda?eventId="));
  }

  /**
   * A non-ASCII character must reach DESCRIPTION as itself.
   *
   * <p>
   * The HTML flavour runs its content through an entity encoder, so an
   * accented character becomes &amp;eacute; or &amp;#xe9;, whose semicolon the
   * iCalendar writer then escapes into \; — the character mangled twice over,
   * which is what a French recipient was shown. Plain text must be built from
   * the source values, not by unescaping the HTML one.
   */
  @Test
  public void testIcsFileDescriptionKeepsNonAsciiCharactersIntact() {
    String ics = generateIcs("<p>Réunion reportée à 20h &amp; suivante</p>", Locale.FRENCH, "unknownModifier");

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertTrue("the accented text must reach DESCRIPTION as itself: " + description,
               description.contains("Réunion reportée à 20h"));
    assertFalse("DESCRIPTION must carry no HTML entity: " + description, description.contains("&eacute"));
    assertFalse("DESCRIPTION must carry no HTML entity: " + description, description.contains("&#x"));
    assertFalse("DESCRIPTION must carry no escaped entity semicolon: " + description, description.contains("\\;"));
    assertTrue("an escaped ampersand must be decoded, not left as an entity: " + description,
               description.contains("& suivante"));
  }

  /**
   * Moving an event to another calendar through update requires the right to
   * create events in the <b>target</b> calendar (derived from the stored
   * calendar row): a user allowed to update an event must not be able to file
   * it into someone else's calendar, while moving between the user's own
   * calendars must work.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventMoveChecksTargetCalendarAcl() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    Event createdEvent = createEvent(eventInstance.clone(), user1IdentityId, testuser1Identity);
    assertEquals(calendar.getId(), createdEvent.getCalendarId());

    // 1. Moving the event to another user's personal calendar must be refused
    org.exoplatform.agenda.model.Calendar user2Calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(Long.parseLong(testuser2Identity.getId()));
    Event eventToMove = agendaEventService.getEventById(createdEvent.getId(), null, user1IdentityId).clone();
    eventToMove.setCalendarId(user2Calendar.getId());
    try {
      agendaEventService.updateEvent(eventToMove,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     false,
                                     user1IdentityId);
      fail("Shouldn't allow to move an event into another user's calendar");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved",
                 calendar.getId(),
                 agendaEventService.getEventById(createdEvent.getId()).getCalendarId());

    // 2. Moving the event between the user's own calendars must work
    org.exoplatform.agenda.model.Calendar secondCalendar =
                                                          new org.exoplatform.agenda.model.Calendar(0, user1IdentityId, false, null, null, null, null, null, null);
    secondCalendar.setName("Second calendar");
    secondCalendar = agendaCalendarService.createCalendar(secondCalendar, testuser1Identity.getRemoteId());
    try {
      eventToMove = agendaEventService.getEventById(createdEvent.getId(), null, user1IdentityId).clone();
      eventToMove.setCalendarId(secondCalendar.getId());
      agendaEventService.updateEvent(eventToMove,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     false,
                                     user1IdentityId);
      assertEquals("The event must have moved to the user's second calendar",
                   secondCalendar.getId(),
                   agendaEventService.getEventById(createdEvent.getId()).getCalendarId());
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
      agendaCalendarService.deleteCalendarById(user2Calendar.getId());
    }
  }

  /**
   * Moving an event to another calendar through a field patch (the
   * {@code calendarId} field of {@code PATCH /v1/agenda/events/{id}}) requires
   * the right to create events in the target calendar, as the full update does
   * (EXO-90381): the creator of an event may not file it into a space calendar
   * where they aren't a redactor, nor into another user's personal calendar. A
   * target calendar that doesn't exist is still reported as such, before any
   * permission is checked.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventFieldsMoveIntoCalendarWithoutCreateRightIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    Event createdEvent = createEvent(eventInstance.clone(), user1IdentityId, testuser1Identity);
    long eventId = createdEvent.getId();
    assertEquals(calendar.getId(), createdEvent.getCalendarId());

    // A space with a redactor: its other members can no longer add events
    spaceService.addRedactor(space, testuser3Identity.getRemoteId());
    assertTrue("testuser1 can update the event he created", agendaEventService.canUpdateEvent(createdEvent, user1IdentityId));
    assertFalse("testuser1 isn't a redactor of the space", agendaEventService.canCreateEvent(spaceCalendar, user1IdentityId));

    // 1. Into a space calendar where the user isn't a redactor
    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(spaceCalendar.getId())),
                                           false,
                                           false,
                                           user1IdentityId);
      fail("Shouldn't allow to move an event into a space calendar where the user can't create events");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved", calendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());

    // 1b. A patch that changes another field first stores nothing either
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("summary", Collections.singletonList("Patched before the refused move"));
    fields.put("calendarId", Collections.singletonList(String.valueOf(spaceCalendar.getId())));
    try {
      agendaEventService.updateEventFields(eventId, fields, false, false, user1IdentityId);
      fail("Shouldn't allow to move an event into a space calendar where the user can't create events");
    } catch (IllegalAccessException e) {
      // Expected
    }
    Event storedEvent = agendaEventService.getEventById(eventId);
    assertEquals("The event must not have moved", calendar.getId(), storedEvent.getCalendarId());
    assertEquals("A field patched before the refused move must not be stored",
                 createdEvent.getSummary(),
                 storedEvent.getSummary());

    // 2. Into another user's personal calendar
    org.exoplatform.agenda.model.Calendar user2Calendar =
                                                         agendaCalendarService.getOrCreateCalendarByOwnerId(Long.parseLong(testuser2Identity.getId()));
    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(user2Calendar.getId())),
                                           false,
                                           false,
                                           user1IdentityId);
      fail("Shouldn't allow to move an event into another user's calendar");
    } catch (IllegalAccessException e) {
      // Expected
    } finally {
      agendaCalendarService.deleteCalendarById(user2Calendar.getId());
    }
    assertEquals("The event must not have moved", calendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());

    // 3. A target calendar that doesn't exist is reported as not found
    assertThrows(IllegalArgumentException.class,
                 () -> agendaEventService.updateEventFields(eventId,
                                                            getFields("calendarId", String.valueOf(Integer.MAX_VALUE)),
                                                            false,
                                                            false,
                                                            user1IdentityId));
  }

  /**
   * The full update of an event stays refused when it moves the event into a
   * space calendar where the user isn't a redactor (EXO-90381), as it already
   * was for another user's personal calendar.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventMoveIntoSpaceCalendarWithoutCreateRightIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    Event createdEvent = createEvent(eventInstance.clone(), user1IdentityId, testuser1Identity);
    long eventId = createdEvent.getId();

    spaceService.addRedactor(space, testuser3Identity.getRemoteId());
    assertFalse("testuser1 isn't a redactor of the space", agendaEventService.canCreateEvent(spaceCalendar, user1IdentityId));

    Event eventToMove = agendaEventService.getEventById(eventId, null, user1IdentityId).clone();
    eventToMove.setCalendarId(spaceCalendar.getId());
    try {
      agendaEventService.updateEvent(eventToMove,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     false,
                                     user1IdentityId);
      fail("Shouldn't allow to move an event into a space calendar where the user can't create events");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved", calendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());
  }

  /**
   * "Attendees can modify the event" lets an attendee change a space event,
   * not take it out of the space into their own calendar (EXO-90149): the
   * move is refused and the event's permissions say so, while the attendee
   * keeps the right to edit it in place.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventMoveOutOfSpaceByAttendeeOnlyIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    eventInstance.setCalendarId(spaceCalendar.getId());
    eventInstance.setAllowAttendeeToUpdate(true);
    long eventId = createEvent(eventInstance, user1IdentityId, testuser2Identity).getId();
    org.exoplatform.agenda.model.Calendar user2Calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(user2IdentityId);

    Event eventToMove = agendaEventService.getEventById(eventId, null, user2IdentityId).clone();
    assertTrue("The attendee may edit the event", eventToMove.getAcl().isCanEdit());
    assertFalse("The attendee may not move the event", eventToMove.getAcl().isCanMove());
    eventToMove.setCalendarId(user2Calendar.getId());
    try {
      agendaEventService.updateEvent(eventToMove,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     false,
                                     user2IdentityId);
      fail("Shouldn't allow an attendee to move a space event into their own calendar");
    } catch (IllegalAccessException e) {
      // Expected
    }
    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(user2Calendar.getId())),
                                           false,
                                           false,
                                           user2IdentityId);
      fail("Shouldn't allow an attendee to move a space event into their own calendar through a field patch");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved", spaceCalendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());

    EventFilter filter = new EventFilter(user2IdentityId, null, null, start.minusHours(1), start.plusHours(2), 0);
    List<Event> listedEvents = agendaEventService.getEvents(filter, ZoneOffset.UTC, user2IdentityId);
    Event listedEvent = listedEvents.stream().filter(listed -> listed.getId() == eventId).findFirst().orElse(null);
    assertNotNull("The attendee lists the event", listedEvent);
    assertTrue("The attendee may edit the listed event", listedEvent.getAcl().isCanEdit());
    assertFalse("The attendee may not move the listed event", listedEvent.getAcl().isCanMove());
  }

  /**
   * Redacting in a space is not managing it (EXO-90149): an attendee who is an
   * explicit redactor of the space, but neither the event's creator nor a
   * manager of the space, may edit the space event and not move it out.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventMoveOutOfSpaceByRedactorAttendeeIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    eventInstance.setCalendarId(spaceCalendar.getId());
    eventInstance.setAllowAttendeeToUpdate(true);
    long eventId = createEvent(eventInstance, user1IdentityId, testuser2Identity).getId();
    spaceService.addRedactor(space, testuser2Identity.getRemoteId());
    assertTrue("testuser2 is a redactor of the space", agendaEventService.canCreateEvent(spaceCalendar, user2IdentityId));
    org.exoplatform.agenda.model.Calendar user2Calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(user2IdentityId);

    Event eventToMove = agendaEventService.getEventById(eventId, null, user2IdentityId);
    assertTrue("The redactor attendee may edit the event", eventToMove.getAcl().isCanEdit());
    assertFalse("The redactor attendee may not move the event", eventToMove.getAcl().isCanMove());
    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(user2Calendar.getId())),
                                           false,
                                           false,
                                           user2IdentityId);
      fail("Shouldn't allow a redactor who only attends the event to move it out of the space");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved", spaceCalendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());
  }

  /**
   * The same holds the other way (EXO-90149): an attendee allowed to modify
   * someone's personal event may not file it into a space, even one where
   * they may create events.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventMoveOutOfPersonalCalendarByAttendeeOnlyIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    eventInstance.setAllowAttendeeToUpdate(true);
    long eventId = createEvent(eventInstance, user1IdentityId, testuser2Identity).getId();
    assertTrue("testuser2 may create events in the space", agendaEventService.canCreateEvent(spaceCalendar, user2IdentityId));

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(spaceCalendar.getId())),
                                           false,
                                           false,
                                           user2IdentityId);
      fail("Shouldn't allow an attendee to file someone's personal event into a space");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved", calendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());
  }

  /**
   * An exceptional occurrence filed into another calendar takes its date out
   * of the series' calendar for everyone (EXO-90149): an attendee allowed to
   * modify a space series may change a date in place, not file it into their
   * own calendar.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceOutOfSpaceSeriesByAttendeeOnlyIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    seriesInstance.setCalendarId(spaceCalendar.getId());
    seriesInstance.setAllowAttendeeToUpdate(true);
    Event series = createEvent(seriesInstance, user1IdentityId, testuser2Identity);
    long seriesId = series.getId();
    org.exoplatform.agenda.model.Calendar user2Calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(user2IdentityId);

    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusDays(5);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                             periodStart,
                                                                             periodEnd,
                                                                             series.getTimeZoneId(),
                                                                             0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    try {
      createEvent(newOccurrenceInstance(seriesId, occurrences.get(1), user2Calendar.getId()), user2IdentityId);
      fail("Shouldn't allow an attendee to file an occurrence of a space series into their own calendar");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The series must keep all its dates",
                 occurrences.size(),
                 agendaEventService.getEventOccurrencesInPeriod(series, periodStart, periodEnd, series.getTimeZoneId(), 0)
                                   .size());
  }

  /**
   * A space event can go back to a personal calendar (EXO-90149) when the
   * user created it, and when an attendee also manages the space: the
   * attendee right is read first, and must not hide the manager one.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventMoveOutOfSpaceByCreatorOrManagerSucceeds() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    eventInstance.setCalendarId(spaceCalendar.getId());
    eventInstance.setAllowAttendeeToUpdate(true);

    // 1. The creator moves it into their own calendar
    long creatorEventId = createEvent(eventInstance, user1IdentityId, testuser2Identity).getId();
    Event creatorEvent = agendaEventService.getEventById(creatorEventId, null, user1IdentityId).clone();
    assertTrue("The creator may move the event", creatorEvent.getAcl().isCanMove());
    creatorEvent.setCalendarId(calendar.getId());
    agendaEventService.updateEvent(creatorEvent,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   user1IdentityId);
    assertEquals("The event must have moved to the creator's calendar",
                 calendar.getId(),
                 agendaEventService.getEventById(creatorEventId).getCalendarId());

    // 2. An attendee who manages the space moves it into their own calendar
    long managerEventId = createEvent(eventInstance, user1IdentityId, testuser2Identity).getId();
    spaceService.setManager(space, testuser2Identity.getRemoteId(), true);
    org.exoplatform.agenda.model.Calendar user2Calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(user2IdentityId);
    try {
      Event managerEvent = agendaEventService.getEventById(managerEventId, null, user2IdentityId).clone();
      assertTrue("A space manager attending the event may move it", managerEvent.getAcl().isCanMove());
      agendaEventService.updateEventFields(managerEventId,
                                           getFields("calendarId", String.valueOf(user2Calendar.getId())),
                                           false,
                                           false,
                                           user2IdentityId);
      assertEquals("The event must have moved to the manager's calendar",
                   user2Calendar.getId(),
                   agendaEventService.getEventById(managerEventId).getCalendarId());
    } finally {
      // The moved event must not outlive the test in testuser2's calendar
      agendaCalendarService.deleteCalendarById(user2Calendar.getId());
    }
  }

  /**
   * Legitimate calendar changes through a field patch keep working
   * (EXO-90381): the owner moves an event between their own calendars, a space
   * redactor moves it into the space calendar, and a patch that keeps the
   * event in its calendar needs no right to add events there.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventFieldsLegitimateCalendarChangesSucceed() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    Event createdEvent = createEvent(eventInstance.clone(), user1IdentityId, testuser1Identity);
    long eventId = createdEvent.getId();

    org.exoplatform.agenda.model.Calendar secondCalendar =
                                                          new org.exoplatform.agenda.model.Calendar(0, user1IdentityId, false, null, null, null, null, null, null);
    secondCalendar.setName("Second calendar");
    secondCalendar = agendaCalendarService.createCalendar(secondCalendar, testuser1Identity.getRemoteId());
    try {
      // 1. Between the owner's own calendars
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(secondCalendar.getId())),
                                           false,
                                           false,
                                           user1IdentityId);
      assertEquals("The event must have moved to the user's second calendar",
                   secondCalendar.getId(),
                   agendaEventService.getEventById(eventId).getCalendarId());

      // 2. A space redactor into the space calendar
      spaceService.addRedactor(space, testuser1Identity.getRemoteId());
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(spaceCalendar.getId())),
                                           false,
                                           false,
                                           user1IdentityId);
      assertEquals("The event must have moved to the space calendar",
                   spaceCalendar.getId(),
                   agendaEventService.getEventById(eventId).getCalendarId());

      // 3. Keeping the calendar: no longer a redactor, still the event's creator
      spaceService.addRedactor(space, testuser3Identity.getRemoteId());
      spaceService.removeRedactor(space, testuser1Identity.getRemoteId());
      assertFalse("testuser1 isn't a redactor of the space anymore",
                  agendaEventService.canCreateEvent(spaceCalendar, user1IdentityId));
      Map<String, List<String>> fields = getFields("calendarId", String.valueOf(spaceCalendar.getId()));
      fields.put("summary", Collections.singletonList("Kept in its calendar"));
      agendaEventService.updateEventFields(eventId, fields, false, false, user1IdentityId);
      Event storedEvent = agendaEventService.getEventById(eventId);
      assertEquals(spaceCalendar.getId(), storedEvent.getCalendarId());
      assertEquals("Kept in its calendar", storedEvent.getSummary());
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
    }
  }

  /**
   * Creating an exceptional occurrence of a recurring event through
   * {@code createEvent} (an event carrying a parent, as the web UI does when a
   * single computed occurrence is edited) requires the right to update that
   * series (EXO-90381): the right to add events to one's own calendar isn't
   * enough, or anyone could remove an occurrence of someone else's series from
   * its owner's views. A parent that doesn't exist is reported as not found,
   * and the creator of the series still creates its occurrences.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateEventOccurrenceRequiresUpdateRightOnSeries() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event series = createEvent(newEventInstance(start, start.plusHours(1), false), user1IdentityId, testuser1Identity);
    long seriesId = series.getId();
    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusDays(5);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, periodStart, periodEnd, series.getTimeZoneId(), 0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    Event occurrence = occurrences.get(1);

    org.exoplatform.agenda.model.Calendar user2Calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(user2IdentityId);
    try {
      assertFalse("testuser2 can't update testuser1's series", agendaEventService.canUpdateEvent(series, user2IdentityId));
      assertTrue("testuser2 can add events to his own calendar",
                 agendaEventService.canCreateEvent(user2Calendar, user2IdentityId));

      // 1. Another user, into his own calendar
      try {
        createEvent(newOccurrenceInstance(seriesId, occurrence, user2Calendar.getId()), user2IdentityId);
        fail("Shouldn't allow to create an occurrence of a series the user can't update");
      } catch (IllegalAccessException e) {
        // Expected
      }
      assertEquals("The occurrence must still be in the series",
                   occurrences.size(),
                   agendaEventService.getEventOccurrencesInPeriod(series, periodStart, periodEnd, series.getTimeZoneId(), 0).size());

      // 2. A parent that doesn't exist is reported as not found
      try {
        createEvent(newOccurrenceInstance(Integer.MAX_VALUE, occurrence, user2Calendar.getId()), user2IdentityId);
        fail("Shouldn't allow to create an occurrence of a series that doesn't exist");
      } catch (AgendaException e) {
        assertEquals(AgendaExceptionType.EVENT_NOT_FOUND, e.getAgendaExceptionType());
      }

      // 3. The creator of the series, in its calendar
      Event created = createEvent(newOccurrenceInstance(seriesId, occurrence, calendar.getId()), user1IdentityId);
      assertEquals(seriesId, created.getParentId());
      assertTrue("The creator's occurrence must be listed as an exceptional occurrence",
                 agendaEventService.getExceptionalOccurrenceEvents(seriesId, null, user1IdentityId)
                                   .stream()
                                   .anyMatch(exceptional -> exceptional.getId() == created.getId()));
    } finally {
      agendaCalendarService.deleteCalendarById(user2Calendar.getId());
    }
  }

  /**
   * An attendee the organiser allowed to update the event edits one date of the
   * series, which is filed in the organiser's own calendar (EXO-90382). They
   * may change the whole series, so they may change one date of it: the
   * exceptional occurrence stays in the series' calendar, and the right to add
   * events to that calendar — which an attendee of somebody else's personal
   * calendar never has — is not asked, so the edit is stored.
   * <p>
   * The call used to end in an {@link IllegalAccessException} all the same,
   * thrown <b>after</b> the row was written, by the read-back
   * {@code createEvent} performs before it stores the payload's attendees: at
   * that instant the new row carried no attendee of its own, so the reader was
   * refused access to an event they had just been allowed to create. That was
   * a second, distinct defect on the read path, reported with EXO-90382 and
   * fixed by EXO-90408, which reads the created row back without asking the
   * read ACL of it a second time. Per the instruction this pin carried until
   * then, the {@code assertThrows} has been replaced by an assertion on the
   * returned event; the two halves are still asserted separately — the
   * permission check lets the attendee through, and the call now answers with
   * the occurrence instead of refusing it.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceByAttendeeAllowedToUpdateSeries() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    seriesInstance.setAllowAttendeeToUpdate(true);
    Event series = createEvent(seriesInstance, user1IdentityId, testuser2Identity);
    long seriesId = series.getId();
    assertEquals(calendar.getId(), series.getCalendarId());

    assertTrue("testuser2 attends the series and may update it",
               agendaEventService.canUpdateEvent(series, user2IdentityId));
    assertFalse("testuser2 can't add events to testuser1's personal calendar",
                agendaEventService.canCreateEvent(calendar, user2IdentityId));
    assertTrue("The series has no exceptional occurrence yet",
               agendaEventService.getExceptionalOccurrenceEvents(seriesId, null, user1IdentityId).isEmpty());

    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusDays(5);
    List<Event> occurrences =
                            agendaEventService.getEventOccurrencesInPeriod(series,
                                                                          periodStart,
                                                                          periodEnd,
                                                                          series.getTimeZoneId(),
                                                                          0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    Event occurrence = occurrences.get(1);

    // EXO-90408: the occurrence is answered with, not refused
    Event created = createEvent(newOccurrenceInstance(seriesId, occurrence, calendar.getId()), user2IdentityId);
    assertNotNull("The attendee's occurrence must be returned, not refused", created);
    assertTrue("The returned occurrence must be the stored row", created.getId() > 0);
    assertEquals(seriesId, created.getParentId());
    assertEquals("The returned occurrence stays in the series' calendar", calendar.getId(), created.getCalendarId());

    // And the second read, the one AgendaEventRest.createEvent performs before
    // answering (RestUtils.getEventByIdAndUser -> the gated getEventById), must
    // not refuse either. It does not because the occurrence now carries the
    // series' attendees, so the creator reads it as an attendee of it — dropping
    // the read-back's gate alone would have moved the 401 one frame outwards
    Event reRead = agendaEventService.getEventById(created.getId(), series.getTimeZoneId(), user2IdentityId);
    assertNotNull("The REST layer's own re-read must not refuse the creator either", reRead);
    assertEquals(created.getId(), reRead.getId());

    // What this change buys: the permission check let the attendee through, so
    // their edit of that one date is stored in the organiser's calendar
    List<Event> exceptional = agendaEventService.getExceptionalOccurrenceEvents(seriesId, null, user1IdentityId);
    assertEquals("The attendee's edit of one date must be stored as an exceptional occurrence of the series",
                 1,
                 exceptional.size());
    assertEquals(seriesId, exceptional.get(0).getParentId());
    assertEquals("The occurrence stays in the series' calendar", calendar.getId(), exceptional.get(0).getCalendarId());
  }

  /**
   * The creator of a series in a space calendar edits one date of it after the
   * space got a redactor, which took their right to add events there
   * (EXO-90382). They still update the series, so they still edit one of its
   * dates: the exceptional occurrence stays in the space calendar and asks for
   * no creation right there.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceBySpaceEventCreatorNoLongerRedactor() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    seriesInstance.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(seriesInstance, user1IdentityId, testuser1Identity);
    long seriesId = series.getId();
    assertEquals(spaceCalendar.getId(), series.getCalendarId());

    // A space with a redactor: its other members can no longer add events
    spaceService.addRedactor(space, testuser3Identity.getRemoteId());
    assertFalse("testuser1 isn't a redactor of the space any more",
                agendaEventService.canCreateEvent(spaceCalendar, user1IdentityId));
    assertTrue("testuser1 created the series and still updates it",
               agendaEventService.canUpdateEvent(series, user1IdentityId));

    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusDays(5);
    List<Event> occurrences =
                            agendaEventService.getEventOccurrencesInPeriod(series,
                                                                          periodStart,
                                                                          periodEnd,
                                                                          series.getTimeZoneId(),
                                                                          0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);

    Event created =
                  createEvent(newOccurrenceInstance(seriesId, occurrences.get(1), spaceCalendar.getId()), user1IdentityId);
    assertEquals(seriesId, created.getParentId());
    assertEquals("The occurrence stays in the space calendar", spaceCalendar.getId(), created.getCalendarId());
    assertTrue("The creator's occurrence must be listed as an exceptional occurrence of the series",
               agendaEventService.getExceptionalOccurrenceEvents(seriesId, null, user1IdentityId)
                                 .stream()
                                 .anyMatch(exceptional -> exceptional.getId() == created.getId()));
  }

  /**
   * The relaxation of EXO-90382 grants nothing to somebody with no right over
   * the series: a stranger filing an exceptional occurrence into the series'
   * own calendar is refused, and the series keeps every one of its dates. The
   * calendar being the series' is what makes this pin bite — that is the branch
   * which no longer asks for a creation right, so the update right on the
   * series is the only thing left refusing it.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceInTheSeriesCalendarWithoutUpdateRightIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user4IdentityId = Long.parseLong(testuser4Identity.getId());

    Event series = createEvent(newEventInstance(start, start.plusHours(1), false), user1IdentityId, testuser1Identity);
    long seriesId = series.getId();

    assertFalse("testuser4 has no right over testuser1's series",
                agendaEventService.canUpdateEvent(series, user4IdentityId));

    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusDays(5);
    List<Event> occurrences =
                            agendaEventService.getEventOccurrencesInPeriod(series,
                                                                          periodStart,
                                                                          periodEnd,
                                                                          series.getTimeZoneId(),
                                                                          0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    Event occurrence = occurrences.get(1);

    try {
      createEvent(newOccurrenceInstance(seriesId, occurrence, calendar.getId()), user4IdentityId);
      fail("Shouldn't allow a stranger to create an occurrence of a series they can't update");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The occurrence must still be in the series",
                 occurrences.size(),
                 agendaEventService.getEventOccurrencesInPeriod(series,
                                                               periodStart,
                                                               periodEnd,
                                                               series.getTimeZoneId(),
                                                               0)
                                   .size());
  }

  /**
   * Filing an exceptional occurrence into a calendar other than the series' is
   * a creation there, and keeps the creation right EXO-90382 dropped for the
   * same-calendar case: the creator of the series, who may update it, is
   * refused the space calendar they aren't a redactor of, and allowed another
   * calendar of their own.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceInAnotherCalendarStillNeedsCreateRight() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event series = createEvent(newEventInstance(start, start.plusHours(1), false), user1IdentityId, testuser1Identity);
    long seriesId = series.getId();
    assertEquals(calendar.getId(), series.getCalendarId());

    spaceService.addRedactor(space, testuser3Identity.getRemoteId());
    assertTrue("testuser1 created the series and updates it", agendaEventService.canUpdateEvent(series, user1IdentityId));
    assertFalse("testuser1 isn't a redactor of the space", agendaEventService.canCreateEvent(spaceCalendar, user1IdentityId));

    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusDays(5);
    List<Event> occurrences =
                            agendaEventService.getEventOccurrencesInPeriod(series,
                                                                          periodStart,
                                                                          periodEnd,
                                                                          series.getTimeZoneId(),
                                                                          0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    Event occurrence = occurrences.get(1);

    // 1. Into a calendar the user can't add events to
    try {
      createEvent(newOccurrenceInstance(seriesId, occurrence, spaceCalendar.getId()), user1IdentityId);
      fail("Shouldn't allow to file an occurrence into a calendar where the user can't create events");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertTrue("Nothing must have been stored",
               agendaEventService.getExceptionalOccurrenceEvents(seriesId, null, user1IdentityId).isEmpty());

    // 2. Into another calendar of their own, where they may create
    org.exoplatform.agenda.model.Calendar secondCalendar = new org.exoplatform.agenda.model.Calendar(0,
                                                                                                    user1IdentityId,
                                                                                                    false,
                                                                                                    null,
                                                                                                    null,
                                                                                                    null,
                                                                                                    null,
                                                                                                    null,
                                                                                                    null);
    secondCalendar.setName("Second calendar");
    secondCalendar = agendaCalendarService.createCalendar(secondCalendar, testuser1Identity.getRemoteId());
    try {
      assertTrue("testuser1 may add events to his own second calendar",
                 agendaEventService.canCreateEvent(secondCalendar, user1IdentityId));
      Event created = createEvent(newOccurrenceInstance(seriesId, occurrence, secondCalendar.getId()), user1IdentityId);
      assertEquals(seriesId, created.getParentId());
      assertEquals(secondCalendar.getId(), created.getCalendarId());
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
    }
  }

  /**
   * Naming a parent is not the same as amending one date of it, and only the
   * second relaxes the creation right (EXO-90382, review round 1).
   * <p>
   * The attacker here is the strongest one the relaxed branch admits: an
   * attendee the organiser allowed to update <b>one plain, non-repeating</b>
   * event of their personal calendar. That grant gives {@code canUpdateEvent}
   * on that event — {@code writeRightOf}'s attendee branch asks for no calendar
   * access at all — while {@code canCreateEvent} on the organiser's calendar
   * stays false. They then post an event that is <b>not</b> an occurrence: no
   * occurrence identifier, its own recurrence, its own summary and dates,
   * naming that event as parent and that calendar as destination. Without the
   * {@code isExceptionalOccurrenceOf} guard the write is accepted and a whole
   * new series appears in the organiser's calendar, expanded over its own
   * dates.
   * <p>
   * Two assertions, and the second is the one that bites: the call is refused,
   * and nothing of the attacker's lands in the organiser's calendar. Asserting
   * the refusal alone would not do — the guard's job is to stop the write, and
   * only the organiser's calendar can say whether it did. (Until EXO-90408 the
   * refusal assertion was worse than weak: the read-back threw the very same
   * exception type <b>after</b> a successful write, so it passed on the
   * mutant.)
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateNonOccurrenceNamingAParentStillNeedsCreateRight() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user4IdentityId = Long.parseLong(testuser4Identity.getId());

    Event hostInstance = newEventInstance(start, start.plusHours(1), false);
    hostInstance.setRecurrence(null);
    hostInstance.setAllowAttendeeToUpdate(true);
    Event host = createEvent(hostInstance, user1IdentityId, testuser4Identity);
    assertEquals(calendar.getId(), host.getCalendarId());
    assertNull("The host event is deliberately not a series", agendaEventService.getEventById(host.getId()).getRecurrence());
    assertTrue("testuser4 may update the event they were invited to",
               agendaEventService.canUpdateEvent(agendaEventService.getEventById(host.getId()), user4IdentityId));
    assertFalse("testuser4 can't add events to testuser1's personal calendar",
                agendaEventService.canCreateEvent(calendar, user4IdentityId));

    Event payload = newEventInstance(start.plusDays(10), start.plusDays(10).plusHours(1), false);
    payload.setId(0);
    payload.setParentId(host.getId());
    payload.setCalendarId(calendar.getId());
    payload.setOccurrence(null);
    payload.setSummary("Injected by an attendee");
    assertNotNull("The payload carries a recurrence of its own", payload.getRecurrence());

    try {
      createEvent(payload, user4IdentityId);
      fail("Shouldn't let an attendee file an arbitrary event into a calendar they can't create in");
    } catch (IllegalAccessException e) {
      // Expected
    }

    List<Event> owned = agendaEventService.getEvents(new EventFilter(Collections.singletonList(user1IdentityId),
                                                                     start.minusDays(1),
                                                                     start.plusDays(30)),
                                                     ZoneOffset.UTC,
                                                     user1IdentityId);
    assertTrue("Nothing of the attendee's must land in the organiser's calendar",
               owned.stream().noneMatch(event -> "Injected by an attendee".equals(event.getSummary())));
  }

  /**
   * An exceptional occurrence created with no attendee in the payload takes the
   * ones its series carries for that date (EXO-90408).
   * <p>
   * This is the invariant {@code saveEventExceptionalOccurrence} already keeps
   * when it detaches a date of a series: an occurrence is the series on one
   * day, so the people invited to the series are invited to it. Without it the
   * row is an orphan — no attendee, hence absent from every attendee-keyed
   * listing, while the series' own expansion drops that date because an
   * exceptional occurrence exists for it.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceWithoutAttendeesTakesTheSeriesAttendees() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());
    long user3IdentityId = Long.parseLong(testuser3Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    seriesInstance.setAllowAttendeeToUpdate(true);
    Event series = createEvent(seriesInstance, user1IdentityId, testuser1Identity, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();

    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                            start.minusDays(1),
                                                                            start.plusDays(5),
                                                                            series.getTimeZoneId(),
                                                                            0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);

    // The payload names nobody: the web UI sends the occurrence it read, but a
    // client that sends only the dates is just as legitimate
    Event created = createEvent(newOccurrenceInstance(seriesId, occurrences.get(1), calendar.getId()), user2IdentityId);
    assertNotNull(created);

    List<Long> inherited = agendaEventAttendeeService.getEventAttendees(created.getId())
                                                     .getEventAttendees()
                                                     .stream()
                                                     .map(EventAttendee::getIdentityId)
                                                     .sorted()
                                                     .toList();
    assertEquals("The occurrence must carry the series' attendees",
                 Arrays.asList(user1IdentityId, user2IdentityId, user3IdentityId).stream().sorted().toList(),
                 inherited);

    // And the state those copies end in, which is decided one frame later by
    // AgendaReplyOnSaveListener on the creation event, not by the arguments
    // this seeding passes (review round 2): everyone is asked again, and the
    // one who made the edit is on it
    List<EventAttendee> rows = agendaEventAttendeeService.getEventAttendees(created.getId()).getEventAttendees();
    for (EventAttendee row : rows) {
      if (row.getIdentityId() == user2IdentityId) {
        assertEquals("The attendee who edited the date is on it", EventAttendeeResponse.ACCEPTED, row.getResponse());
      } else {
        assertEquals("and everybody else is asked again for that date",
                     EventAttendeeResponse.NEEDS_ACTION,
                     row.getResponse());
      }
    }
  }

  /**
   * Editing one date of a series does not take that date out of anybody's
   * calendar (EXO-90408).
   * <p>
   * {@code AgendaEvent.getExceptionalOccurenceIdsByPeriod} matches an
   * exceptional occurrence on its parent alone, so the moment one exists for a
   * date, {@code filterExceptionalEvents} removes that date from the series'
   * expansion — for every reader, the organiser included. What replaces it is
   * the exceptional row itself, and a personal agenda reads by attendee: a row
   * with no attendee replaces nothing and the date simply disappears.
   * <p>
   * The pin therefore asserts through the listing path, for all three readers:
   * the organiser who owns the calendar, the attendee who made the edit, and
   * the other invitee who did nothing. Each must find exactly one event on that
   * date, and it must be the exceptional row.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceKeepsTheDateInEveryAttendeesListing() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());
    long user3IdentityId = Long.parseLong(testuser3Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    seriesInstance.setAllowAttendeeToUpdate(true);
    Event series = createEvent(seriesInstance, user1IdentityId, testuser1Identity, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();

    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                            start.minusDays(1),
                                                                            start.plusDays(5),
                                                                            series.getTimeZoneId(),
                                                                            0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    Event occurrence = occurrences.get(1);

    // Everyone sees that date before the edit
    for (long readerId : new long[] { user1IdentityId, user2IdentityId, user3IdentityId }) {
      assertEquals("The date must be in the listing of " + readerId + " before the edit",
                   1,
                   listOnDayOf(occurrence, readerId).size());
    }

    Event created = createEvent(newOccurrenceInstance(seriesId, occurrence, calendar.getId()), user2IdentityId);
    assertNotNull(created);

    for (long readerId : new long[] { user1IdentityId, user2IdentityId, user3IdentityId }) {
      List<Event> onThatDay = listOnDayOf(occurrence, readerId);
      assertEquals("The date must still be in the listing of " + readerId + " after the edit", 1, onThatDay.size());
      assertEquals("And it must be the exceptional occurrence", created.getId(), onThatDay.get(0).getId());
    }
  }

  /**
   * An exceptional occurrence whose payload names attendees keeps exactly
   * those, and takes none from its series (EXO-90408).
   * <p>
   * The series is the fallback for a payload that names nobody; it never
   * overrides or completes one that does. Dropping somebody from one date of a
   * series is a legitimate edit, and the seeding must not undo it.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceWithAttendeesKeepsExactlyThose() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user3IdentityId = Long.parseLong(testuser3Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    Event series = createEvent(seriesInstance, user1IdentityId, testuser1Identity, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();

    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                            start.minusDays(1),
                                                                            start.plusDays(5),
                                                                            series.getTimeZoneId(),
                                                                            0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);

    // The organiser drops everyone but testuser3 from that one date
    Event created = createEvent(newOccurrenceInstance(seriesId, occurrences.get(1), calendar.getId()),
                                user1IdentityId,
                                testuser3Identity);
    assertNotNull(created);

    List<Long> attendeeIds = agendaEventAttendeeService.getEventAttendees(created.getId())
                                                       .getEventAttendees()
                                                       .stream()
                                                       .map(EventAttendee::getIdentityId)
                                                       .toList();
    assertEquals("Only the attendee the payload names may be on the occurrence",
                 Collections.singletonList(user3IdentityId),
                 attendeeIds);
  }

  /**
   * An event that is not an exceptional occurrence is left exactly as it was
   * (EXO-90408): neither an ordinary event created with no attendee, nor an
   * event that merely names a parent without amending one of its dates, takes
   * an attendee from anywhere.
   * <p>
   * An event with nobody on it is a legitimate event — a note to self in one's
   * own calendar — and the second case is the one the
   * {@code isExceptionalOccurrenceOf} guard of EXO-90382 already separates on
   * the permission side: naming a parent is not amending one date of it, on
   * this side either.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateNonOccurrenceTakesNoAttendeeFromAnywhere() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    // 1. An ordinary event, nobody invited
    Event alone = createEvent(newEventInstance(start, start.plusHours(1), false), user1IdentityId);
    assertTrue("An ordinary event created with no attendee must keep none",
               agendaEventAttendeeService.getEventAttendees(alone.getId()).getEventAttendees().isEmpty());

    // 2. An event naming a series as parent, but carrying no occurrence
    // identifier: it is not an amendment of one of its dates
    Event series = createEvent(newEventInstance(start, start.plusHours(1), false),
                               user1IdentityId,
                               testuser2Identity,
                               testuser3Identity);
    Event payload = newEventInstance(start.plusDays(10), start.plusDays(10).plusHours(1), false);
    payload.setId(0);
    payload.setParentId(series.getId());
    payload.setCalendarId(calendar.getId());
    payload.setOccurrence(null);
    Event child = createEvent(payload, user1IdentityId);
    assertTrue("Naming a parent must not take the parent's attendees",
               agendaEventAttendeeService.getEventAttendees(child.getId()).getEventAttendees().isEmpty());

    // 3. An event naming a NON-REPEATING event as parent, and carrying an
    // occurrence identifier: there is no such thing as one date of it, so the
    // same guard that refuses the relaxed permission branch refuses the
    // seeding. Review round 2: the seeding used to make this check for itself,
    // and more weakly — it asked for a parent and an identifier and never that
    // the parent be a series
    Event plainInstance = newEventInstance(start.plusDays(20), start.plusDays(20).plusHours(1), false);
    plainInstance.setRecurrence(null);
    Event plain = createEvent(plainInstance, user1IdentityId, testuser2Identity, testuser3Identity);
    assertNull("The parent of this case must not be a series", plain.getRecurrence());
    assertFalse("and it must have attendees to take, or the case proves nothing",
                agendaEventAttendeeService.getEventAttendees(plain.getId()).getEventAttendees().isEmpty());

    Event onAPlainParent = newEventInstance(start.plusDays(21), start.plusDays(21).plusHours(1), false);
    onAPlainParent.setId(0);
    onAPlainParent.setParentId(plain.getId());
    onAPlainParent.setCalendarId(calendar.getId());
    onAPlainParent.setOccurrence(new EventOccurrence(start.plusDays(21)));
    Event notAnOccurrence = createEvent(onAPlainParent, user1IdentityId);
    assertTrue("One date of a non-repeating event does not exist, so nothing is inherited",
               agendaEventAttendeeService.getEventAttendees(notAnOccurrence.getId()).getEventAttendees().isEmpty());
  }

  /**
   * Naming a date the series does not have is not amending one of its dates
   * either (review round 2 of EXO-90382 + EXO-90408).
   * <p>
   * The relaxed branch used to be entered on the payload's <i>shape</i> alone —
   * a parent with a recurrence, and any non-null occurrence identifier. Both
   * come from the client, and {@code createEvent} stores the payload's own
   * start, end and summary verbatim instead of deriving them from the
   * identifier as {@code createEventExceptionalOccurrence} does. So an
   * attendee the organiser allowed to update the series, who may not add
   * events to the organiser's calendar at all, could post an arbitrary event
   * at an arbitrary date into it by naming the series as parent and any
   * instant as the date it amends — as often as they liked, since nothing
   * rejects a second row for the same identifier either.
   * <p>
   * Two things made it worse than a stray row, and both are asserted here: an
   * identifier outside the series strips no date from the series in exchange
   * ({@code getExceptionalOccurenceIdsByPeriod} matches on a window the row is
   * not in), so the injection is purely additive; and EXO-90408's seeding puts
   * every invitee of the series on it, so it lands in their personal agendas
   * too.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceAtADateTheSeriesDoesNotHaveStillNeedsCreateRight() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());
    long user3IdentityId = Long.parseLong(testuser3Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    seriesInstance.setAllowAttendeeToUpdate(true);
    Event series = createEvent(seriesInstance, user1IdentityId, testuser1Identity, testuser2Identity, testuser3Identity);
    long seriesId = series.getId();

    assertTrue("testuser2 attends the series and may update it",
               agendaEventService.canUpdateEvent(series, user2IdentityId));
    assertFalse("testuser2 can't add events to testuser1's personal calendar",
                agendaEventService.canCreateEvent(calendar, user2IdentityId));

    // A date the series never has: it ends two days after it starts
    ZonedDateTime farAway = start.plusDays(400);
    Event payload = newEventInstance(farAway, farAway.plusHours(1), false);
    payload.setId(0);
    payload.setParentId(seriesId);
    payload.setCalendarId(calendar.getId());
    payload.setRecurrence(null);
    payload.setOccurrence(new EventOccurrence(farAway));
    payload.setSummary("INJECTED");

    boolean refused = false;
    try {
      createEvent(payload, user2IdentityId);
    } catch (IllegalAccessException e) {
      refused = true;
      assertTrue("and be refused for the same reason an ordinary event would be: " + e.getMessage(),
                 e.getMessage().contains("can't create an event in calendar"));
    }

    // The effect is asserted first and unconditionally: a refusal assertion
    // alone cannot tell a refusal from a refusal after a successful write, and
    // it is the write that matters here
    for (long readerId : new long[] { user1IdentityId, user2IdentityId, user3IdentityId }) {
      EventFilter filter = new EventFilter(readerId, null, null, farAway.minusHours(2), farAway.plusHours(3), 0);
      assertTrue("Nothing of the attendee's must land in the calendar of " + readerId,
                 agendaEventService.getEvents(filter, ZoneOffset.UTC, readerId).isEmpty());
    }
    assertTrue("and the series must have gained no exceptional occurrence",
               agendaEventService.getExceptionalOccurrenceEvents(seriesId, null, user1IdentityId).isEmpty());
    assertTrue("An event at a date the series does not have must ask for the creation right", refused);
  }

  /**
   * An identifier on a date the series has, at another time of day, is still
   * an amendment of that date (review round 3 of EXO-90382 + EXO-90408).
   * <p>
   * The date check matches on the UTC date, not on the instant, and this is
   * the half of it that a match on the instant alone would refuse. It is not a
   * looseness: it is the granularity the rest of the machinery already works
   * at — {@code getExceptionalOccurrenceEvent} queries the identifier's UTC
   * day, and {@code filterExceptionalEvents} matches on the UTC date before
   * anything else — which is why such a row still replaces the computed
   * occurrence rather than being added beside it, asserted here through the
   * organiser's own listing. {@code filterExceptionalEvents} names the case in
   * its own comment: an identifier computed by the previous algorithm.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateOccurrenceAtAnotherTimeOfTheSameDateIsStillThatDate() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    seriesInstance.setAllowAttendeeToUpdate(true);
    Event series = createEvent(seriesInstance, user1IdentityId, testuser1Identity, testuser2Identity);
    long seriesId = series.getId();

    assertFalse("testuser2 can't add events to testuser1's personal calendar",
                agendaEventService.canCreateEvent(calendar, user2IdentityId));

    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                            start.minusDays(1),
                                                                            start.plusDays(5),
                                                                            series.getTimeZoneId(),
                                                                            0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    Event occurrence = occurrences.get(1);

    ZonedDateTime exact = occurrence.getOccurrence().getId().withZoneSameInstant(ZoneOffset.UTC);
    ZonedDateTime sameDayOtherTime = exact.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusHours(11);
    assertNotEquals("The two identifiers must differ as instants, or the pin proves nothing", exact, sameDayOtherTime);
    assertEquals("and name the same UTC date", exact.toLocalDate(), sameDayOtherTime.toLocalDate());

    Event payload = newOccurrenceInstance(seriesId, occurrence, calendar.getId());
    payload.setOccurrence(new EventOccurrence(sameDayOtherTime));
    Event created = createEvent(payload, user2IdentityId);
    assertNotNull("An identifier on that date at another time must still amend that date", created);

    // And it replaces the computed date rather than being added beside it
    List<Event> onThatDay = listOnDayOf(occurrence, user1IdentityId);
    assertEquals("The organiser must still see exactly one event on that date", 1, onThatDay.size());
    assertEquals("and it must be the amendment", created.getId(), onThatDay.get(0).getId());
  }

  /**
   * Amending one date of a series is a thing there is one of (review round 2 of
   * EXO-90382 + EXO-90408).
   * <p>
   * Nothing on the {@code createEvent} path deduplicates an exceptional
   * occurrence — {@code saveEventExceptionalOccurrence} is the path that looks
   * for an existing row first — so without a guard the relaxed branch is
   * re-entered for the same date as often as the caller likes, each call adding
   * another row to a calendar they may not write to. The first amendment is
   * the grant; the second asks for the creation right like any other creation
   * there.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateSecondOccurrenceForTheSameDateStillNeedsCreateRight() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event seriesInstance = newEventInstance(start, start.plusHours(1), false);
    seriesInstance.setAllowAttendeeToUpdate(true);
    Event series = createEvent(seriesInstance, user1IdentityId, testuser1Identity, testuser2Identity);
    long seriesId = series.getId();

    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                            start.minusDays(1),
                                                                            start.plusDays(5),
                                                                            series.getTimeZoneId(),
                                                                            0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    Event occurrence = occurrences.get(1);

    // The first amendment is the grant this delivery makes
    assertNotNull(createEvent(newOccurrenceInstance(seriesId, occurrence, calendar.getId()), user2IdentityId));

    boolean refused = false;
    try {
      createEvent(newOccurrenceInstance(seriesId, occurrence, calendar.getId()), user2IdentityId);
    } catch (IllegalAccessException e) {
      refused = true;
      assertTrue("and be refused for the same reason an ordinary event would be: " + e.getMessage(),
                 e.getMessage().contains("can't create an event in calendar"));
    }

    assertEquals("The series must carry exactly one exceptional occurrence for that date",
                 1,
                 agendaEventService.getExceptionalOccurrenceEvents(seriesId, null, user1IdentityId).size());
    assertTrue("A second amendment of the same date must ask for the creation right", refused);
  }

  /**
   * Reads one reader's agenda over the day of a computed occurrence, the way a
   * personal agenda reads it: by attendee, over a window around that
   * occurrence.
   *
   * @param occurrence the computed occurrence whose day is read
   * @param readerIdentityId identity identifier of the reader
   * @return the events that reader sees on that day
   * @throws Exception when the listing call fails
   */
  private List<Event> listOnDayOf(Event occurrence, long readerIdentityId) throws Exception {
    EventFilter eventFilter = new EventFilter(readerIdentityId,
                                              null,
                                              null,
                                              occurrence.getStart().minusHours(2),
                                              occurrence.getEnd().plusHours(2),
                                              0);
    return agendaEventService.getEvents(eventFilter, ZoneOffset.UTC, readerIdentityId);
  }

  /**
   * Builds the payload the web UI sends when a single computed occurrence of a
   * series is edited: no identifier, the series as parent, the occurrence
   * identifier and dates, and no recurrence.
   *
   * @param parentId the identifier of the series
   * @param occurrence the computed occurrence being edited
   * @param calendarId the calendar the occurrence is filed into
   * @return the event to pass to {@code createEvent}
   */
  private Event newOccurrenceInstance(long parentId, Event occurrence, long calendarId) {
    Event event = occurrence.clone();
    event.setId(0);
    event.setParentId(parentId);
    event.setCalendarId(calendarId);
    event.setRecurrence(null);
    event.setOccurrence(new EventOccurrence(occurrence.getOccurrence().getId()));
    return event;
  }
}
