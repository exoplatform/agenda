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
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;
import java.util.*;

import org.junit.Before;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.storage.AgendaCalendarStorage;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.commons.utils.ListAccess;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValuesParam;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaCalendarServiceTest {

  private IdentityManager           identityManager;

  private SpaceService              spaceService;

  private AgendaCalendarStorage     agendaCalendarStorage;

  private AgendaCalendarServiceImpl agendaCalendarService;

  @Before
  public void setUp() {
    spaceService = mock(SpaceService.class);
    identityManager = mock(IdentityManager.class);
    agendaCalendarStorage = mock(AgendaCalendarStorage.class);
    InitParams initParams = new InitParams();
    ValuesParam value = new ValuesParam();
    value.setName("defaultColors");
    value.setValues(Collections.singletonList("#111111"));
    initParams.addParam(value);
    agendaCalendarService = new AgendaCalendarServiceImpl(agendaCalendarStorage, identityManager, spaceService, initParams);
  }

  @Test
  public void testGetCalendarById() {
    long calendarId = 1;
    long calendarOwnerId = 2;

    String username = "testuser";
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));
    Profile calendarOwnerProfile = new Profile();
    calendarOwnerProfile.setProperty(Profile.FULL_NAME, username);
    calendarOwnerIdentity.setProfile(calendarOwnerProfile);

    Calendar calendar = new Calendar(calendarId,
                                     calendarOwnerId,
                                     true,
                                     "title",
                                     "description",
                                     AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()),
                                     AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()),
                                     "color",
                                     null);
    when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(calendar);
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(username))).thenReturn(calendarOwnerIdentity);

    // 0. Arguments validation tests
    try {
      agendaCalendarService.getCalendarById(0);
      fail("Should throw an exception when username is null");
    } catch (Exception e1) {
      // Expected
    }

    // 1. Test retrieve calendar by its id
    Calendar retrievedCalendar = agendaCalendarService.getCalendarById(calendarId);
    assertNotNull(retrievedCalendar);
    assertEquals(calendar, retrievedCalendar);
    assertEquals(username, calendar.getTitle());

    // 2. Test retrieve not existing calendar
    long notExistingCalendarId = calendarId + 2;
    retrievedCalendar = agendaCalendarService.getCalendarById(notExistingCalendarId);
    assertNull("Should return null when trying to retrieve inexistant calendar", retrievedCalendar);
  }

  @Test
  public void testGetOrCreateCalendarByOwnerId() throws Exception { // NOSONAR
    long calendarOwnerId = 2;

    String username = "testuser";
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(agendaCalendarStorage.countCalendarsByOwners(any())).thenReturn(0);

    agendaCalendarService.getOrCreateCalendarByOwnerId(calendarOwnerId);

    verify(agendaCalendarStorage, times(1)).createCalendar(any());
  }

  @Test
  public void testGetOrCreateCalendarByOwnerId_NoCreate() throws Exception { // NOSONAR
    long calendarId = 1;
    long calendarOwnerId = 2;

    String username = "testuser";
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(agendaCalendarStorage.countCalendarsByOwners(any())).thenReturn(1);

    Calendar calendar = new Calendar(calendarId,
                                     calendarOwnerId,
                                     true,
                                     "title",
                                     "description",
                                     AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()),
                                     AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()),
                                     "color",
                                     null);
    when(agendaCalendarStorage.getCalendarIdsByOwnerIds(anyInt(),
                                                        anyInt(),
                                                        any())).thenReturn(Collections.singletonList(calendar.getId()));
    when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(calendar);

    Calendar retrievedCalendar = agendaCalendarService.getOrCreateCalendarByOwnerId(calendarOwnerId);

    assertEquals(calendar, retrievedCalendar);
    verify(agendaCalendarStorage, times(0)).createCalendar(any());
  }

  @Test
  public void testGetCalendarByIdAndUsername() throws Exception { // NOSONAR
    long calendarId = 1;
    long calendarOwnerId = 2;

    String username = "testuser";
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));
    Profile calendarOwnerProfile = new Profile();
    calendarOwnerProfile.setProperty(Profile.FULL_NAME, username);
    calendarOwnerIdentity.setProfile(calendarOwnerProfile);

    Calendar calendar = new Calendar(calendarId,
                                     calendarOwnerId,
                                     true,
                                     "title",
                                     "description",
                                     AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()),
                                     AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()),
                                     "color",
                                     null);
    when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(calendar);
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(username))).thenReturn(calendarOwnerIdentity);

    // 0. Arguments validation tests
    try {
      agendaCalendarService.getCalendarById(0, username);
      fail("Should throw an exception when calendar id is not positive");
    } catch (Exception e1) {
      // Expected
    }

    try {
      agendaCalendarService.getCalendarById(calendarId, null);
      fail("Should throw an exception when username is null");
    } catch (Exception e1) {
      // Expected
    }

    // 1. When user accessing his own calendar, no error should be thrown
    Calendar retrievedCalendar = agendaCalendarService.getCalendarById(calendarId, username);
    assertNotNull("User should be able to access his own calendar", retrievedCalendar);

    // check ACL
    assertNotNull("ACL of calendar should have been computed", retrievedCalendar.getAcl());
    assertTrue("User should be able to modify calendar", retrievedCalendar.getAcl().isCanEdit());

    // Check retrieved attributes
    retrievedCalendar.setAcl(null);
    assertEquals("Retrieved calendar should be the same as the retrieved from storage", calendar, retrievedCalendar);

    // 2. Test retrieve not existing calendar
    long notExistingCalendarId = calendarId + 2;
    retrievedCalendar = agendaCalendarService.getCalendarById(notExistingCalendarId, username);
    assertNull("Should return null when trying to retrieve inexistant calendar", retrievedCalendar);

    // 3. When not found user is accessing another user calendar, an error
    // should be thrown
    try {
      agendaCalendarService.getCalendarById(calendarId, "inexistantuser");
      fail("should throw an error when trying to retrieve calendar with not found user");
    } catch (Exception e) {
      // Expected
    }

    // 4. When a member user is accessing space calendar, no error
    // should be thrown and the ACL.canEdit should equal to false
    calendarOwnerIdentity.setProviderId(SpaceIdentityProvider.NAME);
    String spacePrettyName = "spacetest";
    calendarOwnerIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);

    // 4.1 when user is not member
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(false);
    try {
      agendaCalendarService.getCalendarById(calendarId, username);
      fail("User shouldn't acces calendar of a space where he don't belong");
    } catch (IllegalAccessException e) {
      // Expected
    }

    // 4.2 when user is member only of the space
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(true);
    retrievedCalendar = agendaCalendarService.getCalendarById(calendarId, username);
    assertNotNull("User should be able to access his own calendar", retrievedCalendar);

    // check ACL
    assertNotNull("ACL of calendar should have been computed", retrievedCalendar.getAcl());
    assertFalse("User shouldn't be able to modify calendar", retrievedCalendar.getAcl().isCanEdit());

    // Check retrieved attributes
    retrievedCalendar.setAcl(null);
    assertEquals("Retrieved calendar should be the same as the retrieved from storage", calendar, retrievedCalendar);

    // 4.3 when user is member and manager of the space
    when(spaceService.isManager(eq(space), eq(username))).thenReturn(true);
    retrievedCalendar = agendaCalendarService.getCalendarById(calendarId, username);
    assertNotNull("User should be able to access his own calendar", retrievedCalendar);

    // check ACL
    assertNotNull("ACL of calendar should have been computed", retrievedCalendar.getAcl());
    assertTrue("User should be able to modify calendar", retrievedCalendar.getAcl().isCanEdit());

    // Check retrieved attributes
    retrievedCalendar.setAcl(null);
    assertEquals("Retrieved calendar should be the same as the retrieved from storage", calendar, retrievedCalendar);

    // 4.4 when user is super spaces manager but not member neither manager of
    // the space itself
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(false);
    when(spaceService.isManager(eq(space), eq(username))).thenReturn(false);
    when(spaceService.isSuperManager(eq(username))).thenReturn(true);
    retrievedCalendar = agendaCalendarService.getCalendarById(calendarId, username);
    assertNotNull("User should be able to access his own calendar", retrievedCalendar);

    // check ACL
    assertNotNull("ACL of calendar should have been computed", retrievedCalendar.getAcl());
    assertTrue("User should be able to modify calendar", retrievedCalendar.getAcl().isCanEdit());

    // Check retrieved attributes
    retrievedCalendar.setAcl(null);
    assertEquals("Retrieved calendar should be the same as the retrieved from storage", calendar, retrievedCalendar);
  }

  @Test
  public void testCreateCalendar() {
    long calendarId = 1;

    long calendarOwnerId = 2;
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, "test");
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));

    Calendar calendar = new Calendar(0,
                                     calendarOwnerId,
                                     true,
                                     "title",
                                     "description",
                                     null,
                                     null,
                                     "color",
                                     null);

    when(agendaCalendarStorage.createCalendar(eq(calendar))).thenAnswer(new Answer<Calendar>() {
      @Override
      public Calendar answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        Calendar calendar = (Calendar) args[0];
        calendar.setId(calendarId);
        calendar.setCreated(AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()));
        calendar.setUpdated(null);
        return calendar;
      }
    });

    // 0. Arguments validation
    try {
      agendaCalendarService.createCalendar(null);
      fail("Shouldn't allow to create null calendar");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      calendar.setId(1);
      agendaCalendarService.createCalendar(calendar);
      fail("Shouldn't allow to create calendar with a not zero id");
    } catch (IllegalArgumentException e) {
      // Expected
      calendar.setId(0);
    }
    try {
      calendar.setOwnerId(-1);
      agendaCalendarService.createCalendar(calendar);
      fail("Shouldn't allow to create calendar with a negative owner id");
    } catch (IllegalArgumentException e) {
      // Expected
      calendar.setOwnerId(calendarOwnerId);
    }

    // 1. Shouldn't allow to create calendar with not valid calendar owner
    try {
      agendaCalendarService.createCalendar(calendar);
      fail("Shouldn't allow to create calendar with a not valid calendar owner");
    } catch (IllegalStateException e) {
      // Expected
      calendar.setOwnerId(calendarOwnerId);
    }

    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    try {
      calendarOwnerIdentity.setProviderId("NotManagerProviderByCalendarAPI");
      agendaCalendarService.createCalendar(calendar);
      fail("Shouldn't allow to create calendar with not managed providerId for calendar owner");
    } catch (IllegalStateException e) {
      // Expected
      calendarOwnerIdentity.setProviderId(OrganizationIdentityProvider.NAME);
    }
    // 2. Should be able to create calendar and put a new id
    Calendar createdCalendar = agendaCalendarService.createCalendar(calendar);
    assertNotNull(createdCalendar);
    assertEquals(calendarId, createdCalendar.getId());
    assertNotNull(createdCalendar.getCreated());

    createdCalendar.setCreated(calendar.getCreated());
    createdCalendar.setId(calendar.getId());
    assertEquals(calendar, createdCalendar);
  }

  @Test
  public void testCreateCalendarWithUsername() throws Exception { // NOSONAR
    long calendarId = 1;

    long calendarOwnerId = 2;
    String username = "test";
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));

    Calendar calendar = new Calendar(0,
                                     calendarOwnerId,
                                     true,
                                     "title",
                                     "description",
                                     null,
                                     null,
                                     "color",
                                     null);

    when(agendaCalendarStorage.createCalendar(eq(calendar))).thenAnswer(new Answer<Calendar>() {
      @Override
      public Calendar answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        Calendar calendar = (Calendar) args[0];
        calendar.setId(calendarId);
        calendar.setCreated(AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()));
        calendar.setUpdated(null);
        return calendar;
      }
    });

    // 0. Arguments validation
    try {
      agendaCalendarService.createCalendar(null, username);
      fail("Shouldn't allow to create null calendar");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaCalendarService.createCalendar(calendar, null);
      fail("Shouldn't allow to create null calendar");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      calendar.setId(1);
      agendaCalendarService.createCalendar(calendar, username);
      fail("Shouldn't allow to create calendar with a not zero id");
    } catch (IllegalArgumentException e) {
      // Expected
      calendar.setId(0);
    }
    try {
      calendar.setOwnerId(-1);
      agendaCalendarService.createCalendar(calendar, username);
      fail("Shouldn't allow to create calendar with a negative owner id");
    } catch (IllegalStateException e) {
      // Expected
      calendar.setOwnerId(calendarOwnerId);
    }

    // 1. Shouldn't allow to create calendar with a not valid calendar owner
    try {
      agendaCalendarService.createCalendar(calendar, username);
      fail("Shouldn't allow to create calendar with a not valid calendar owner");
    } catch (IllegalStateException e) {
      // Expected
      calendar.setOwnerId(calendarOwnerId);
    }

    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(username))).thenReturn(calendarOwnerIdentity);

    try {
      calendarOwnerIdentity.setProviderId("NotManagerProviderByCalendarAPI");
      agendaCalendarService.createCalendar(calendar, username);
      fail("Shouldn't allow to create calendar with not managed providerId for calendar owner");
    } catch (IllegalStateException e) {
      // Expected
      calendarOwnerIdentity.setProviderId(OrganizationIdentityProvider.NAME);
    }

    when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(calendar);

    // 2. Should be able to create calendar and put a new id
    Calendar createdCalendar = agendaCalendarService.createCalendar(calendar, username);
    assertNotNull(createdCalendar);
    assertEquals(calendarId, createdCalendar.getId());
    assertNotNull(createdCalendar.getCreated());

    createdCalendar.setCreated(null);
    createdCalendar.setId(0);
    assertEquals(calendar, createdCalendar);

    // 3. Shouldn't be able to create calendar of space if user isn't manager or
    // super manager
    calendarOwnerIdentity.setProviderId(SpaceIdentityProvider.NAME);
    String spacePrettyName = "spacetest";
    calendarOwnerIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);

    // 3.1 when user is not member
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(false);
    try {
      agendaCalendarService.createCalendar(calendar, username);
      fail("User shouldn't be able to create space calendar");
    } catch (IllegalAccessException e) {
      // Expected
    }
    // 3.2 When user is member only
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(true);
    try {
      agendaCalendarService.createCalendar(calendar, username);
      fail("User shouldn't be able to create space calendar even when member");
    } catch (IllegalAccessException e) {
      // Expected
    }
    // 3.3 When user is manager of the space
    when(spaceService.isManager(eq(space), eq(username))).thenReturn(true);
    createdCalendar = agendaCalendarService.createCalendar(calendar, username);
    assertNotNull(createdCalendar);
    assertEquals(calendarId, createdCalendar.getId());
    assertNotNull(createdCalendar.getCreated());

    createdCalendar.setCreated(null);
    createdCalendar.setId(0);
    assertEquals(calendar, createdCalendar);
    // 3.3 When user is super manager
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(false);
    when(spaceService.isManager(eq(space), eq(username))).thenReturn(false);
    when(spaceService.isSuperManager(eq(username))).thenReturn(true);
    createdCalendar = agendaCalendarService.createCalendar(calendar, username);
    assertNotNull(createdCalendar);
    assertEquals(calendarId, createdCalendar.getId());
    assertNotNull(createdCalendar.getCreated());
  }

  @Test
  public void testUpdateCalendar() throws Exception { // NOSONAR
    long calendarId = 1;

    long calendarOwnerId = 2;
    String username = "test";
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));

    Calendar calendar = new Calendar(calendarId,
                                     calendarOwnerId,
                                     true,
                                     "title",
                                     "description",
                                     null,
                                     null,
                                     "color",
                                     null);

    when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(calendar);
    doAnswer(new Answer<Object>() {
      public Object answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        Calendar calendar = (Calendar) args[0];
        calendar.setId(calendarId);
        calendar.setUpdated(AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()));
        return null;
      }
    })
      .when(agendaCalendarStorage)
      .updateCalendar(eq(calendar));

    // 0. Arguments validation
    try {
      agendaCalendarService.updateCalendar(null);
      fail("Shouldn't allow to update null calendar");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      calendar.setId(0);
      agendaCalendarService.updateCalendar(calendar);
      fail("Shouldn't allow to update calendar with invalid id");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    // 1. trying to update non existing calendar
    try {
      long notExistingCalendarId = calendarId + 1;
      calendar.setId(notExistingCalendarId);
      agendaCalendarService.updateCalendar(calendar);
      fail("Shouldn't allow to update calendar with a not found calendar");
    } catch (ObjectNotFoundException e) {
      // Expected
      calendar.setId(calendarId);
    }
    verify(agendaCalendarStorage, times(0)).updateCalendar(any());

    // 2. Update existing calendar
    agendaCalendarService.updateCalendar(calendar);
    verify(agendaCalendarStorage, times(1)).updateCalendar(any());
  }

  @Test
  public void testUpdateCalendarWithUsername() throws Exception { // NOSONAR
    long calendarId = 1;

    long calendarOwnerId = 2;
    String username = "test";
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));

    Calendar calendar = new Calendar(calendarId,
                                     calendarOwnerId,
                                     true,
                                     "title",
                                     "description",
                                     null,
                                     null,
                                     "color",
                                     null);

    when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(calendar);

    doAnswer(new Answer<Object>() {
      public Object answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        Calendar calendar = (Calendar) args[0];
        calendar.setId(calendarId);
        calendar.setUpdated(AgendaDateUtils.toRFC3339Date(ZonedDateTime.now()));
        return null;
      }
    })
      .when(agendaCalendarStorage)
      .updateCalendar(eq(calendar));

    // 0. Arguments validation
    try {
      agendaCalendarService.updateCalendar(calendar, null);
      fail("Shouldn't allow to update null calendar");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaCalendarService.updateCalendar(null, username);
      fail("Shouldn't allow to update null username");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      calendar.setId(0);
      agendaCalendarService.updateCalendar(calendar, username);
      fail("Shouldn't allow to update calendar with invalid id");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    // 1. Updating non existent calendar
    try {
      long notExistingCalendarId = calendarId + 1;
      calendar.setId(notExistingCalendarId);
      agendaCalendarService.updateCalendar(calendar, username);
      fail("Shouldn't allow to update calendar with a not found calendar");
    } catch (ObjectNotFoundException e) {
      // Expected
      calendar.setId(calendarId);
    }
    verify(agendaCalendarStorage, times(0)).updateCalendar(any());

    // 2. Updating calendar owner
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);

    try {
      calendarOwnerIdentity.setProviderId("NotManagerProviderByCalendarAPI");
      agendaCalendarService.updateCalendar(calendar, username);
      fail("Shouldn't allow to update calendar with not managed providerId for calendar owner");
    } catch (IllegalStateException e) {
      // Expected
      calendarOwnerIdentity.setProviderId(OrganizationIdentityProvider.NAME);
    }

    // 3. Should be able to update calendar
    agendaCalendarService.updateCalendar(calendar, username);
    assertEquals(calendarId, calendar.getId());
    assertNotNull(calendar.getUpdated());

    // 4. Shouldn't be able to update calendar of space if user isn't manager or
    // super manager
    calendarOwnerIdentity.setProviderId(SpaceIdentityProvider.NAME);
    String spacePrettyName = "spacetest";
    calendarOwnerIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);

    // 4.1 when user is not member
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(false);
    try {
      agendaCalendarService.updateCalendar(calendar, username);
      fail("User shouldn't be able to update space calendar");
    } catch (IllegalAccessException e) {
      // Expected
    }
    // 4.2 When user is member only
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(true);
    try {
      agendaCalendarService.updateCalendar(calendar, username);
      fail("User shouldn't be able to update space calendar even when member");
    } catch (IllegalAccessException e) {
      // Expected
    }
    // 4.3 When user is manager of the space
    when(spaceService.isManager(eq(space), eq(username))).thenReturn(true);
    calendar.setUpdated(null);
    agendaCalendarService.updateCalendar(calendar, username);
    assertNotNull(calendar.getUpdated());

    // 4.3 When user is super manager
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(false);
    when(spaceService.isManager(eq(space), eq(username))).thenReturn(false);
    when(spaceService.isSuperManager(eq(username))).thenReturn(true);
    calendar.setUpdated(null);
    agendaCalendarService.updateCalendar(calendar, username);
    assertNotNull(calendar.getUpdated());
  }

  @Test
  public void testDeleteCalendarById() throws Exception { // NOSONAR
    // 0. Arguments validation
    try {
      agendaCalendarService.deleteCalendarById(0);
      fail("Shouldn't be able to delete calendar with invalid id");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    // 1. Shouldn't be able to delete inexistant calendar
    try {
      agendaCalendarService.deleteCalendarById(1);
      fail("Shouldn't be able to delete a non existing calendar");
    } catch (ObjectNotFoundException e) {
      // Expected
    }

    verify(agendaCalendarStorage, times(0)).deleteCalendarById(anyLong());

    // 2. Delete calendar
    long calendarId = 1;
    Calendar calendar = new Calendar(calendarId,
                                     2,
                                     true,
                                     "title",
                                     "description",
                                     null,
                                     null,
                                     "color",
                                     null);

    when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(calendar);
    agendaCalendarService.deleteCalendarById(calendarId);
    verify(agendaCalendarStorage, times(1)).deleteCalendarById(anyLong());
  }

  @Test
  public void testDeleteCalendarByIdAndUsername() throws Exception { // NOSONAR
    long calendarId = 1;
    long calendarOwnerId = 2;
    String username = "test";
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));

    Calendar calendar = new Calendar(calendarId,
                                     calendarOwnerId,
                                     true,
                                     "title",
                                     "description",
                                     null,
                                     null,
                                     "color",
                                     null);

    when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(calendar);

    // 0. Arguments validation
    try {
      agendaCalendarService.deleteCalendarById(calendarId, null);
      fail("Shouldn't allow to delete null username");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaCalendarService.deleteCalendarById(0, username);
      fail("Shouldn't allow to delete invalid calendar id");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    // 1. Deleting non existent calendar
    try {
      long inexistantCalendarId = calendarId + 2;
      agendaCalendarService.deleteCalendarById(inexistantCalendarId, username);
      fail("Shouldn't allow to delete calendar with not existing calendar");
    } catch (ObjectNotFoundException e) {
      // Expected
    }
    verify(agendaCalendarStorage, times(0)).deleteCalendarById(anyLong());

    // 2. Delete calendar by not owner
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);

    try {
      calendarOwnerIdentity.setRemoteId("anotherUser");
      agendaCalendarService.deleteCalendarById(calendarId, username);
      fail("Shouldn't allow to delete calendar with different user from calendar owner");
    } catch (IllegalStateException e) {
      // Expected
      calendarOwnerIdentity.setRemoteId(username);
    }

    // 3. Delete system calendar not allowed even by owner
    try {
      calendar.setSystem(true);
      agendaCalendarService.deleteCalendarById(calendarId, username);
      fail("Shouldn't allow to delete system calendar even by owner");
    } catch (IllegalStateException e) {
      // Expected
      calendar.setSystem(false);
    }

    // 4. Should be able to delete calendar
    agendaCalendarService.deleteCalendarById(calendarId, username);
    verify(agendaCalendarStorage, times(1)).deleteCalendarById(eq(calendarId));

    // 5. Shouldn't be able to delete calendar of space if user isn't manager or
    // super manager
    calendarOwnerIdentity.setProviderId(SpaceIdentityProvider.NAME);
    String spacePrettyName = "spacetest";
    calendarOwnerIdentity.setRemoteId(spacePrettyName);
    Space space = new Space();
    when(spaceService.getSpaceByPrettyName(eq(spacePrettyName))).thenReturn(space);

    // 5.1 when user is not member
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(false);
    try {
      agendaCalendarService.deleteCalendarById(calendarId, username);
      fail("User shouldn't be able to delete space calendar");
    } catch (IllegalAccessException e) {
      // Expected
    }

    // 5.2 When user is member only
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(true);
    try {
      agendaCalendarService.deleteCalendarById(calendarId, username);
      fail("User shouldn't be able to delete space calendar even when member");
    } catch (IllegalAccessException e) {
      // Expected
    }

    // 5.3 When user is manager of the space
    when(spaceService.isManager(eq(space), eq(username))).thenReturn(true);
    agendaCalendarService.deleteCalendarById(calendarId, username);
    verify(agendaCalendarStorage, times(2)).deleteCalendarById(eq(calendarId));

    // 5.4 When user is super manager
    when(spaceService.isMember(eq(space), eq(username))).thenReturn(false);
    when(spaceService.isManager(eq(space), eq(username))).thenReturn(false);
    when(spaceService.isSuperManager(eq(username))).thenReturn(true);
    agendaCalendarService.deleteCalendarById(calendarId, username);
    verify(agendaCalendarStorage, times(3)).deleteCalendarById(eq(calendarId));
  }

  @Test
  public void testGetCalendars() throws Exception { // NOSONAR
    // 0. Arguments validation
    try {
      agendaCalendarService.getCalendars(0, 10, null);
      fail("shouldn't allow to retrieve calendars list with null username");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    // 1. Test with inexistant user
    try {
      agendaCalendarService.getCalendars(0, 10, "fakeUser");
      fail("Shouldn't allow to retrieve calendars list with inexistant username");
    } catch (IllegalStateException e) {
      // Expected
    }

    // 2. Retrieve calendars with pagination
    String username = "testuser";
    long calendarOwnerId = 2;
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(username))).thenReturn(calendarOwnerIdentity);
    when(spaceService.getMemberSpaces(eq(username))).thenAnswer(new Answer<ListAccess<Space>>() {
      @Override
      public ListAccess<Space> answer(InvocationOnMock invocation) throws Throwable {
        @SuppressWarnings("unchecked")
        ListAccess<Space> userSpaces = mock(ListAccess.class);
        final int spacesCount = 45;
        when(userSpaces.getSize()).thenReturn(spacesCount);
        when(userSpaces.load(anyInt(), anyInt())).thenAnswer(new Answer<Space[]>() {
          @Override
          public Space[] answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            int offset = Integer.parseInt(args[0].toString());
            int size = Integer.parseInt(args[1].toString());
            Space[] spaces = new Space[size];
            for (int i = 0; i < size; i++) {
              spaces[i] = new Space();
              int index = i + offset;
              String prettyName = "testspace" + index;
              int spaceIdentityIndex = index + 1000;
              spaces[i].setId(String.valueOf(spaceIdentityIndex));
              spaces[i].setPrettyName(prettyName);
              Identity spaceIdentity = new Identity(String.valueOf(spaceIdentityIndex));
              spaceIdentity.setProviderId(SpaceIdentityProvider.NAME);
              spaceIdentity.setRemoteId(prettyName);
              when(identityManager.getOrCreateIdentity(eq(SpaceIdentityProvider.NAME),
                                                       eq(prettyName))).thenReturn(spaceIdentity);
              when(identityManager.getIdentity(spaceIdentity.getId())).thenReturn(spaceIdentity);
              when(spaceService.getSpaceByPrettyName(eq(prettyName))).thenReturn(spaces[i]);
              when(spaceService.isMember(eq(spaces[i]), eq(username))).thenReturn(true);
            }
            return spaces;
          }
        });
        return userSpaces;
      }
    });

    when(agendaCalendarStorage.getCalendarIdsByOwnerIds(anyInt(), anyInt(), anyVararg())).thenAnswer(new Answer<List<Long>>() {
      @Override
      public List<Long> answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        int offset = Integer.parseInt(args[0].toString());
        int size = Integer.parseInt(args[1].toString());
        List<Long> ownerIds = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
          ownerIds.add(invocation.getArgumentAt(i, Long.class));
        }
        int from = offset;
        int to = from + size;
        to = to > 46 ? 46 : to;
        List<Long> calendarIds = new ArrayList<>(ownerIds.subList(from, to));
        for (Long calendarId : calendarIds) {
          when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(new Calendar(calendarId,
                                                                                              calendarId,
                                                                                              false,
                                                                                              "title",
                                                                                              "description",
                                                                                              "",
                                                                                              null,
                                                                                              "color",
                                                                                              null));

        }
        return calendarIds;
      }
    });

    List<Calendar> calendars = agendaCalendarService.getCalendars(0, 10, username);
    assertNotNull(calendars);
    assertEquals(10, calendars.size());
    for (Calendar calendar : calendars) {
      assertNotNull(calendar);
    }

    calendars = agendaCalendarService.getCalendars(0, 50, username);
    assertNotNull(calendars);
    assertEquals(46, calendars.size());
    for (Calendar calendar : calendars) {
      assertNotNull(calendar);
    }
  }

  @Test
  public void testCountCalendars() throws Exception { // NOSONAR
    // 0. Arguments validation
    try {
      agendaCalendarService.countCalendars(null);
      fail("shouldn't allow to count calendars for null user");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    // 1. Test with inexistant user
    try {
      agendaCalendarService.countCalendars("fakeUser");
      fail("Shouldn't allow to count calendars for inexistant username");
    } catch (IllegalStateException e) {
      // Expected
    }

    // 2. Count calendars for a user
    String username = "testuser";
    long calendarOwnerId = 2;
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(username))).thenReturn(calendarOwnerIdentity);
    when(spaceService.getMemberSpaces(eq(username))).thenAnswer(new Answer<ListAccess<Space>>() {
      @Override
      public ListAccess<Space> answer(InvocationOnMock invocation) throws Throwable {
        @SuppressWarnings("unchecked")
        ListAccess<Space> userSpaces = mock(ListAccess.class);
        final int spacesCount = 45;
        when(userSpaces.getSize()).thenReturn(spacesCount);
        when(userSpaces.load(anyInt(), anyInt())).thenAnswer(new Answer<Space[]>() {
          @Override
          public Space[] answer(InvocationOnMock invocation) throws Throwable {
            Object[] args = invocation.getArguments();
            int offset = Integer.parseInt(args[0].toString());
            int size = Integer.parseInt(args[1].toString());
            Space[] spaces = new Space[size];
            for (int i = 0; i < size; i++) {
              spaces[i] = new Space();
              int index = i + offset;
              String prettyName = "testspace" + index;
              int spaceIdentityIndex = index + 1000;
              spaces[i].setId(String.valueOf(spaceIdentityIndex));
              spaces[i].setPrettyName(prettyName);
              Identity spaceIdentity = new Identity(String.valueOf(spaceIdentityIndex));
              spaceIdentity.setProviderId(SpaceIdentityProvider.NAME);
              spaceIdentity.setRemoteId(prettyName);
              when(identityManager.getOrCreateIdentity(eq(SpaceIdentityProvider.NAME),
                                                       eq(prettyName))).thenReturn(spaceIdentity);
            }
            return spaces;
          }
        });
        return userSpaces;
      }
    });

    when(agendaCalendarStorage.countCalendarsByOwners(anyVararg())).thenReturn(45);
    long calendarsCount = agendaCalendarService.countCalendars(username);
    assertEquals(45, calendarsCount);
  }

  @Test
  public void testGetCalendarsByOwnerId() throws Exception { // NOSONAR
    String username = "testuser";

    // 0. Arguments validation
    try {
      agendaCalendarService.getCalendarsByOwnerId(0, 0, 10, username);
      fail("shouldn't allow to retrieve calendars list with invalid owner id");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaCalendarService.getCalendarsByOwnerId(1, 0, 10, null);
      fail("shouldn't allow to retrieve calendars list with null username");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    // 1. Test with inexistant user
    try {
      agendaCalendarService.getCalendarsByOwnerId(1, 0, 10, "fakeUser");
      fail("Shouldn't allow to retrieve calendars list with inexistant username");
    } catch (IllegalStateException e) {
      // Expected
    }

    // 2. Try to retrieve calendars of another user
    long calendarOwnerId = 2;
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(username))).thenReturn(calendarOwnerIdentity);

    long anotherCalendarOwnerId = 3;
    String anotherUser = "username2";
    Identity anotherCalendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, anotherUser);
    anotherCalendarOwnerIdentity.setId(String.valueOf(anotherCalendarOwnerId));
    when(identityManager.getIdentity(eq(String.valueOf(anotherCalendarOwnerId)))).thenReturn(anotherCalendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(anotherUser))).thenReturn(anotherCalendarOwnerIdentity);

    try {
      agendaCalendarService.getCalendarsByOwnerId(anotherCalendarOwnerId, 0, 10, username);
      fail("Shouldn't allow to retrieve calendars list of another user");
    } catch (IllegalAccessException e) {
      // Expected
    }

    // 3. Retrieve calendars with pagination
    when(agendaCalendarStorage.getCalendarIdsByOwnerIds(anyInt(), anyInt(), anyVararg())).thenAnswer(new Answer<List<Long>>() {
      @Override
      public List<Long> answer(InvocationOnMock invocation) throws Throwable {
        Object[] args = invocation.getArguments();
        int offset = Integer.parseInt(args[0].toString());
        int size = Integer.parseInt(args[1].toString());
        List<Long> ownerIds = new ArrayList<>();
        for (int i = 2; i < args.length; i++) {
          ownerIds.add(invocation.getArgumentAt(i, Long.class));
        }
        List<Long> calendarIds = new ArrayList<>();
        int length = offset + size;
        length = length > 45 ? 45 : length;
        for (long i = offset; i < length; i++) {
          calendarIds.add(i + 1);
        }
        for (Long calendarId : calendarIds) {
          when(agendaCalendarStorage.getCalendarById(eq(calendarId))).thenReturn(new Calendar(calendarId,
                                                                                              calendarOwnerId,
                                                                                              false,
                                                                                              "title",
                                                                                              "description",
                                                                                              "",
                                                                                              null,
                                                                                              "color",
                                                                                              null));
        }
        return calendarIds;
      }
    });

    List<Calendar> calendars = agendaCalendarService.getCalendarsByOwnerId(calendarOwnerId, 0, 10, username);
    assertNotNull(calendars);
    assertEquals(10, calendars.size());

    calendars = agendaCalendarService.getCalendarsByOwnerId(calendarOwnerId, 0, 50, username);
    assertNotNull(calendars);
    assertEquals(45, calendars.size());
  }

  @Test
  public void testCountCalendarsByOwnerId() throws Exception { // NOSONAR
    // 0. Arguments validation
    try {
      agendaCalendarService.countCalendarsByOwnerId(1, null);
      fail("shouldn't allow to count calendars for null user");
    } catch (IllegalArgumentException e) {
      // Expected
    }
    try {
      agendaCalendarService.countCalendarsByOwnerId(0, "user");
      fail("shouldn't allow to count calendars for null user");
    } catch (IllegalArgumentException e) {
      // Expected
    }

    // 1. Test with inexistant user
    try {
      agendaCalendarService.countCalendarsByOwnerId(1, "fakeUser");
      fail("Shouldn't allow to count calendars for inexistant username");
    } catch (IllegalStateException e) {
      // Expected
    }

    // 2. Try to count calendars of another user
    String username = "testuser";
    long calendarOwnerId = 2;
    Identity calendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, username);
    calendarOwnerIdentity.setId(String.valueOf(calendarOwnerId));
    when(identityManager.getIdentity(eq(String.valueOf(calendarOwnerId)))).thenReturn(calendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(username))).thenReturn(calendarOwnerIdentity);

    long anotherCalendarOwnerId = 3;
    String anotherUser = "username2";
    Identity anotherCalendarOwnerIdentity = new Identity(OrganizationIdentityProvider.NAME, anotherUser);
    anotherCalendarOwnerIdentity.setId(String.valueOf(anotherCalendarOwnerId));
    when(identityManager.getIdentity(eq(String.valueOf(anotherCalendarOwnerId)))).thenReturn(anotherCalendarOwnerIdentity);
    when(identityManager.getOrCreateIdentity(eq(OrganizationIdentityProvider.NAME),
                                             eq(anotherUser))).thenReturn(anotherCalendarOwnerIdentity);

    try {
      agendaCalendarService.countCalendarsByOwnerId(anotherCalendarOwnerId, username);
      fail("Shouldn't allow to count calendars of another user");
    } catch (IllegalAccessException e) {
      // Expected
    }

    // 3. Count calendars for a user
    when(agendaCalendarStorage.countCalendarsByOwners(eq(calendarOwnerId))).thenReturn(45);
    long calendarsCount = agendaCalendarService.countCalendarsByOwnerId(calendarOwnerId, username);
    assertEquals(45, calendarsCount);
  }

}
