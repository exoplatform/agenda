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
