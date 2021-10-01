// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.dao;

import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.container.*;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.naming.InitialContextInitializer;

import junit.framework.TestCase;

public class CalendarDAOTest extends TestCase {

  private PortalContainer container;

  private CalendarDAO     calendarDAO;

  @Override
  protected void setUp() throws Exception {
    RootContainer rootContainer = RootContainer.getInstance();
    rootContainer.getComponentInstanceOfType(InitialContextInitializer.class);

    container = PortalContainer.getInstance();
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

  public void testCreateCalendar() {
    CalendarEntity calendarEntity = new CalendarEntity();

    String color = "Color";
    String description = "Description";
    long ownerId = 2;
    boolean isSystem = true;

    calendarEntity.setColor(color);
    calendarEntity.setDescription(description);
    calendarEntity.setOwnerId(ownerId);
    calendarEntity.setSystem(isSystem);

    try {
      calendarEntity = calendarDAO.create(calendarEntity);
      assertNotNull(calendarEntity.getId());
      assertEquals(color, calendarEntity.getColor());
      assertEquals(description, calendarEntity.getDescription());
      assertNotNull(calendarEntity.getOwnerId());
      assertEquals(ownerId, calendarEntity.getOwnerId().longValue());
      assertEquals(isSystem, calendarEntity.isSystem());
    } finally {
      calendarDAO.delete(calendarEntity);
    }
  }

  public void testDeleteCalendar() {
    CalendarEntity calendarEntity = new CalendarEntity();

    String color = "Color";
    String description = "Description";
    long ownerId = 2;
    boolean isSystem = true;

    calendarEntity.setColor(color);
    calendarEntity.setDescription(description);
    calendarEntity.setOwnerId(ownerId);
    calendarEntity.setSystem(isSystem);

    calendarEntity = calendarDAO.create(calendarEntity);

    assertNotNull(calendarEntity.getId());

    calendarDAO.delete(calendarEntity);

    calendarEntity = calendarDAO.find(calendarEntity.getId());
    assertNull(calendarEntity);
  }

  private void begin() {
    RequestLifeCycle.begin(container);
  }
}
