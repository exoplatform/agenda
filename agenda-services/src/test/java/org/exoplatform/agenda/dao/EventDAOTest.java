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

import java.sql.Date;

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

  private String            occurrenceId = "OccurrenceId";

  private String            remoteId     = "RemoteId";

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
      assertEquals(remoteId, eventEntity.getRemoteId());
      assertEquals(status, eventEntity.getStatus());
      assertEquals(creatorId, eventEntity.getCreatorId());
      assertEquals(startDate, eventEntity.getStartDate());
      assertEquals(endDate, eventEntity.getEndDate());
    } finally {
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
      calendarDAO.delete(calendarEntity);
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
                    remoteId,
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
                               String occurrenceId,
                               String remoteId,
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
    eventEntity.setRemoteId(remoteId);
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
