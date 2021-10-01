// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

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
