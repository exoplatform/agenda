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
package org.exoplatform.agenda.storage;

import static org.junit.Assert.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.util.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.exoplatform.agenda.dao.CalendarDAO;
import org.exoplatform.agenda.entity.CalendarEntity;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.storage.cached.CachedAgendaCalendarStorage;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.cache.CacheService;
import org.exoplatform.services.cache.concurrent.ConcurrentFIFOExoCache;
import org.exoplatform.services.listener.ListenerService;

public class AgendaCalendarStorageTest {

  private CachedAgendaCalendarStorage agendaCalendarStorage;

  private CalendarDAO                 calendarDAO;

  @Before
  public void setUp() throws Exception { // NOSONAR
    CacheService cacheService = mock(CacheService.class);
    calendarDAO = mock(CalendarDAO.class);
    AgendaEventStorage agendaEventStorage = mock(AgendaEventStorage.class);
    ListenerService listenerService = new ListenerService(new ExoContainerContext(null));
    listenerService = spy(listenerService);
    when(cacheService.getCacheInstance(CachedAgendaCalendarStorage.CALENDAR_CACHE_NAME)).thenReturn(new ConcurrentFIFOExoCache<>(CachedAgendaCalendarStorage.CALENDAR_CACHE_NAME,
                                                                                                                                 500));
    agendaCalendarStorage = new CachedAgendaCalendarStorage(cacheService, agendaEventStorage, calendarDAO, listenerService);
  }

  @Test
  public void testGetCalendarById() {
    CalendarEntity calendarEntity = new CalendarEntity();
    calendarEntity.setColor("color");
    calendarEntity.setDescription("description");
    calendarEntity.setCreatedDate(new Date());
    calendarEntity.setUpdatedDate(new Date());
    calendarEntity.setId(1l);
    calendarEntity.setOwnerId(2l);
    calendarEntity.setSystem(true);

    when(calendarDAO.find(eq(2l))).thenReturn(calendarEntity);

    Calendar notExistingCalendar = agendaCalendarStorage.getCalendarById(1l);
    assertNull(notExistingCalendar);
    verify(calendarDAO, times(1)).find(anyLong());

    Calendar calendar = agendaCalendarStorage.getCalendarById(2l);
    assertNotNull(calendar);
    verify(calendarDAO, times(2)).find(anyLong());
    agendaCalendarStorage.getCalendarById(2l);

    // Verify that cache is operant and the call isn't made
    verify(calendarDAO, times(2)).find(anyLong());
  }

  @Test
  public void testGetCalendarIdsByOwnerIds() {
    when(calendarDAO.getCalendarIdsByOwnerIds(eq(0), eq(10), anyVararg())).thenReturn(Collections.singletonList(2l));
    List<Long> result = agendaCalendarStorage.getCalendarIdsByOwnerIds(0, 10, 2l);
    assertNotNull(result);
    assertEquals(1, result.size());

    verify(calendarDAO, times(1)).getCalendarIdsByOwnerIds(0, 10, 2l);
    agendaCalendarStorage.getCalendarIdsByOwnerIds(0, 10, 2l);
    // No cache used for this call
    verify(calendarDAO, times(2)).getCalendarIdsByOwnerIds(0, 10, 2l);
  }

  @Test
  public void testCountCalendarsByOwners() {
    when(calendarDAO.countCalendarsByOwnerIds(anyVararg())).thenReturn(1);
    int count = agendaCalendarStorage.countCalendarsByOwners(2l);
    assertEquals(1, count);

    verify(calendarDAO, times(1)).countCalendarsByOwnerIds(2l);
    agendaCalendarStorage.countCalendarsByOwners(2l);
    // No cache used for this call
    verify(calendarDAO, times(2)).countCalendarsByOwnerIds(2l);
  }

  @Test
  public void testCreateCalendar() throws Exception { // NOSONAR
    Calendar calendar = new Calendar(0,
                                     2l,
                                     true,
                                     null,
                                     "description",
                                     null,
                                     null,
                                     "color",
                                     null);
    when(calendarDAO.create(anyObject())).thenAnswer(new Answer<CalendarEntity>() {
      @Override
      public CalendarEntity answer(InvocationOnMock invocation) throws Throwable {
        CalendarEntity calendarEntity = invocation.getArgumentAt(0, CalendarEntity.class);
        calendarEntity.setId(1l);
        calendarEntity.setCreatedDate(new Date());
        return calendarEntity;
      }
    });
    Calendar createdCalendar = agendaCalendarStorage.createCalendar(calendar);
    assertNotNull(createdCalendar);
    assertEquals(createdCalendar.getId(), 1l);
    assertNotNull(createdCalendar.getCreated());
    calendar.setId(createdCalendar.getId());
    calendar.setCreated(createdCalendar.getCreated());
    assertEquals(calendar, createdCalendar);
  }

  @Test
  public void testUpdateCalendar() throws Exception { // NOSONAR
    long calendarId = 1l;

    CalendarEntity calendarEntity = new CalendarEntity();
    calendarEntity.setId(calendarId);
    calendarEntity.setOwnerId(2l);
    calendarEntity.setDescription("description");
    calendarEntity.setCreatedDate(new Date());
    calendarEntity.setSystem(true);
    calendarEntity.setColor("color");

    when(calendarDAO.find(eq(calendarId))).thenReturn(calendarEntity);

    Calendar calendar = agendaCalendarStorage.getCalendarById(calendarId);
    assertNotNull(calendar);
    verify(calendarDAO, times(1)).find(anyLong());
    agendaCalendarStorage.getCalendarById(calendarId);
    // Check cache is operant
    verify(calendarDAO, times(1)).find(anyLong());

    agendaCalendarStorage.updateCalendar(calendar);
    verify(calendarDAO, times(1)).update(anyObject());

    agendaCalendarStorage.getCalendarById(calendarId);
    // Verify that cache is cleared
    verify(calendarDAO, times(2)).find(anyLong());
  }

  @Test
  public void testDeleteCalendarById() throws Exception { // NOSONAR
    long calendarId = 1l;

    CalendarEntity calendarEntity = new CalendarEntity();
    calendarEntity.setId(calendarId);
    calendarEntity.setOwnerId(2l);
    calendarEntity.setDescription("description");
    calendarEntity.setCreatedDate(new Date());
    calendarEntity.setSystem(true);
    calendarEntity.setColor("color");

    when(calendarDAO.find(eq(calendarId))).thenReturn(calendarEntity);

    Calendar calendar = agendaCalendarStorage.getCalendarById(calendarId);
    assertNotNull(calendar);
    verify(calendarDAO, times(1)).find(anyLong());
    agendaCalendarStorage.getCalendarById(calendarId);
    // Check cache is operant
    verify(calendarDAO, times(1)).find(anyLong());

    agendaCalendarStorage.deleteCalendarById(calendarId);
    verify(calendarDAO, times(1)).delete(anyObject());
    when(calendarDAO.find(eq(calendarId))).thenReturn(null);

    // Verify that cache is cleared
    agendaCalendarStorage.getCalendarById(calendarId);
    verify(calendarDAO, times(3)).find(anyLong());
    agendaCalendarStorage.getCalendarById(calendarId);
    verify(calendarDAO, times(4)).find(anyLong());
  }

}
