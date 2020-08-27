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

import static org.mockito.Mockito.mock;

import org.junit.Before;
import org.junit.Test;

import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

public class AgendaEventServiceTest {

  private IdentityManager        identityManager;

  private SpaceService           spaceService;

  private AgendaEventStorage     agendaEventStorage;

  private AgendaEventServiceImpl agendaEventServiceImpl;

  @Before
  public void setUp() {
    spaceService = mock(SpaceService.class);
    identityManager = mock(IdentityManager.class);
    agendaEventStorage = mock(AgendaEventStorage.class);
//    agendaEventServiceImpl = new AgendaEventServiceImpl(agendaEventStorage, identityManager, spaceService);
  }

  @Test
  public void testGetEvents() {
    // TODO
  }

}
