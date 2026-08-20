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

  /**
   * The default calendar of an owner must resolve by the {@code isSystem}
   * flag and never by row order: an older user-created row must not shadow
   * the system calendar.
   */
  public void testGetSystemCalendarIdByOwnerId() {
    long ownerId = 5623;

    CalendarEntity userCalendar = new CalendarEntity();
    userCalendar.setColor("Color");
    userCalendar.setOwnerId(ownerId);
    userCalendar.setSystem(false);
    userCalendar = calendarDAO.create(userCalendar);

    try {
      // Owner has calendars, but none is the system one yet
      assertNull(calendarDAO.getSystemCalendarIdByOwnerId(ownerId));

      CalendarEntity systemCalendar = new CalendarEntity();
      systemCalendar.setColor("Color");
      systemCalendar.setOwnerId(ownerId);
      systemCalendar.setSystem(true);
      systemCalendar = calendarDAO.create(systemCalendar);

      try {
        // The system calendar is newer (higher id) than the user one: the
        // system one must still win
        assertTrue(systemCalendar.getId() > userCalendar.getId());
        Long systemCalendarId = calendarDAO.getSystemCalendarIdByOwnerId(ownerId);
        assertNotNull(systemCalendarId);
        assertEquals(systemCalendar.getId(), systemCalendarId);
      } finally {
        calendarDAO.delete(systemCalendar);
      }
    } finally {
      calendarDAO.delete(userCalendar);
    }
  }

  /**
   * Every created calendar must carry a stable synchronization identifier,
   * generated when the caller didn't provide one and kept when it did.
   */
  public void testCreateGeneratesSyncUid() {
    CalendarEntity calendarEntity = new CalendarEntity();
    calendarEntity.setColor("Color");
    calendarEntity.setOwnerId(2L);
    calendarEntity.setSystem(false);

    calendarEntity = calendarDAO.create(calendarEntity);
    try {
      assertNotNull("A SYNC_UID must be generated when none is provided", calendarEntity.getSyncUid());
      assertEquals(36, calendarEntity.getSyncUid().length());
    } finally {
      calendarDAO.delete(calendarEntity);
    }

    CalendarEntity presetCalendarEntity = new CalendarEntity();
    presetCalendarEntity.setColor("Color");
    presetCalendarEntity.setOwnerId(2L);
    presetCalendarEntity.setSystem(false);
    presetCalendarEntity.setSyncUid("preset-sync-uid");
    presetCalendarEntity = calendarDAO.create(presetCalendarEntity);
    try {
      assertEquals("A provided SYNC_UID must be kept", "preset-sync-uid", presetCalendarEntity.getSyncUid());
    } finally {
      calendarDAO.delete(presetCalendarEntity);
    }
  }

  /**
   * The user-defined name must be persisted and read back as-is, null staying
   * null (meaning "derive the title from the owner identity").
   */
  public void testCalendarNamePersistence() {
    CalendarEntity calendarEntity = new CalendarEntity();
    calendarEntity.setColor("Color");
    calendarEntity.setOwnerId(2L);
    calendarEntity.setSystem(false);
    calendarEntity.setName("Personal projects");

    calendarEntity = calendarDAO.create(calendarEntity);
    try {
      CalendarEntity storedEntity = calendarDAO.find(calendarEntity.getId());
      assertEquals("Personal projects", storedEntity.getName());
    } finally {
      calendarDAO.delete(calendarEntity);
    }
  }

  private void begin() {
    RequestLifeCycle.begin(container);
  }
}
