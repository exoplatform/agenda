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
package org.exoplatform.agenda.dao;

import java.util.Date;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.container.*;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.naming.InitialContextInitializer;

import junit.framework.TestCase;

public class EventDAOTest extends TestCase {

  private EventAvailability availability = EventAvailability.BUSY;

  private boolean           allDay       = true;

  private String            color        = "Color";

  private String            location     = "Location";

  private String            description  = "Description";

  private String            summary      = "Summary";

  private Date              occurrenceId = new Date();

  private EventStatus       status       = EventStatus.TENTATIVE;

  private long              creatorId    = 2;

  private Date              startDate    = new Date(System.currentTimeMillis() - 86400000l);

  private Date              endDate      = new Date(System.currentTimeMillis());

  private PortalContainer   container;

  private EventDAO          eventDAO;

  private CalendarDAO       calendarDAO;

  @Override
  protected void setUp() throws Exception {
    RootContainer rootContainer = RootContainer.getInstance();
    rootContainer.getComponentInstanceOfType(InitialContextInitializer.class);

    container = PortalContainer.getInstance();
    eventDAO = container.getComponentInstanceOfType(EventDAO.class);
    calendarDAO = container.getComponentInstanceOfType(CalendarDAO.class);

    ExoContainerContext.setCurrentContainer(container);
    begin();
  }

  @Override
  protected void tearDown() throws Exception {
    end();
  }

  private void end() {
    RequestLifeCycle.end();
  }

  public void testCreateEvent() {
    CalendarEntity calendarEntity = newCalendar();
    try {
      EventEntity eventEntity = newEvent(calendarEntity);
      assertNotNull(eventEntity);
      assertNotNull(eventEntity.getId());
      assertEquals(availability, eventEntity.getAvailability());
      assertEquals(allDay, eventEntity.isAllDay());
      assertEquals(color, eventEntity.getColor());
      assertEquals(location, eventEntity.getLocation());
      assertEquals(description, eventEntity.getDescription());
      assertEquals(summary, eventEntity.getSummary());
      assertEquals(occurrenceId, eventEntity.getOccurrenceId());
      assertEquals(status, eventEntity.getStatus());
      assertEquals(creatorId, eventEntity.getCreatorId());
      assertEquals(startDate, eventEntity.getStartDate());
      assertEquals(endDate, eventEntity.getEndDate());
    } finally {
      eventDAO.deleteCalendarEvents(calendarEntity.getId());
      RequestLifeCycle.restartTransaction();
      calendarDAO.delete(calendarEntity);
    }
  }

  public void testDeleteEvent() {
    CalendarEntity calendarEntity = newCalendar();
    try {
      EventEntity eventEntity = newEvent(calendarEntity);
      assertNotNull(eventEntity.getId());
      eventDAO.delete(eventEntity);
      eventEntity = eventDAO.find(eventEntity.getId());
      assertNull(eventEntity);
    } finally {
      eventDAO.deleteCalendarEvents(calendarEntity.getId());
      calendarDAO.delete(calendarEntity);
    }
  }

  /**
   * Deleting a personal calendar relies on this bulk move: every event of the
   * source calendar must end up in the target calendar, the returned
   * identifiers must be exactly the moved events, and events of other
   * calendars must not move.
   */
  public void testMoveCalendarEvents() {
    CalendarEntity sourceCalendar = newCalendar();
    CalendarEntity targetCalendar = newCalendar();
    try {
      EventEntity movedEvent1 = newEvent(sourceCalendar);
      EventEntity movedEvent2 = newEvent(sourceCalendar);
      EventEntity untouchedEvent = newEvent(targetCalendar);

      java.util.List<Long> movedEventIds = eventDAO.moveCalendarEvents(sourceCalendar.getId(), targetCalendar.getId());
      assertNotNull(movedEventIds);
      assertEquals(2, movedEventIds.size());
      assertTrue(movedEventIds.contains(movedEvent1.getId()));
      assertTrue(movedEventIds.contains(movedEvent2.getId()));

      EventEntity storedMovedEvent1 = eventDAO.find(movedEvent1.getId());
      EventEntity storedMovedEvent2 = eventDAO.find(movedEvent2.getId());
      EventEntity storedUntouchedEvent = eventDAO.find(untouchedEvent.getId());
      assertEquals(targetCalendar.getId(), storedMovedEvent1.getCalendar().getId());
      assertEquals(targetCalendar.getId(), storedMovedEvent2.getCalendar().getId());
      assertEquals(targetCalendar.getId(), storedUntouchedEvent.getCalendar().getId());

      // An empty source calendar moves nothing
      java.util.List<Long> emptyMove = eventDAO.moveCalendarEvents(sourceCalendar.getId(), targetCalendar.getId());
      assertNotNull(emptyMove);
      assertTrue(emptyMove.isEmpty());
    } finally {
      eventDAO.deleteCalendarEvents(sourceCalendar.getId());
      eventDAO.deleteCalendarEvents(targetCalendar.getId());
      RequestLifeCycle.restartTransaction();
      calendarDAO.delete(sourceCalendar);
      calendarDAO.delete(targetCalendar);
    }
  }

  private EventEntity newEvent(CalendarEntity calendarEntity) {
    return newEvent(calendarEntity,
                    availability,
                    allDay,
                    color,
                    location,
                    description,
                    summary,
                    occurrenceId,
                    status,
                    creatorId,
                    startDate,
                    endDate);
  }

  private EventEntity newEvent(CalendarEntity calendarEntity,
                               EventAvailability availability,
                               boolean allDay,
                               String color,
                               String location,
                               String description,
                               String summary,
                               Date occurrenceId,
                               EventStatus status,
                               long creatorId,
                               Date startDate,
                               Date endDate) {
    EventEntity eventEntity = new EventEntity();
    eventEntity.setAllDay(allDay);
    eventEntity.setAvailability(availability);
    eventEntity.setCalendar(calendarEntity);
    eventEntity.setColor(color);
    eventEntity.setDescription(description);
    eventEntity.setCreatorId(creatorId);
    eventEntity.setEndDate(endDate);
    eventEntity.setStartDate(startDate);
    eventEntity.setLocation(location);
    eventEntity.setOccurrenceId(occurrenceId);
    eventEntity.setStatus(status);
    eventEntity.setSummary(summary);

    return eventDAO.create(eventEntity);
  }

  private CalendarEntity newCalendar() {
    CalendarEntity calendarEntity = new CalendarEntity();

    String calendarColor = "Color";
    String calendarDescription = "Description";
    long ownerId = 2;
    boolean isSystem = true;

    calendarEntity.setColor(calendarColor);
    calendarEntity.setDescription(calendarDescription);
    calendarEntity.setOwnerId(ownerId);
    calendarEntity.setSystem(isSystem);
    calendarEntity = calendarDAO.create(calendarEntity);
    return calendarEntity;
  }

  private void begin() {
    RequestLifeCycle.begin(container);
  }
}
