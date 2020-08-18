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

import java.time.ZonedDateTime;
import java.util.List;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.commons.exception.ObjectNotFoundException;

public class AgendaEventServiceImpl implements AgendaEventService {

  public AgendaEventServiceImpl() {
    // TODO Auto-generated constructor stub
  }

  @Override
  public List<Event> getEvents(ZonedDateTime start, ZonedDateTime end, String username) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<Event> getEventsByOwner(long ownerId,
                                      ZonedDateTime start,
                                      ZonedDateTime end,
                                      String username) throws IllegalAccessException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Event getEventById(long eventId, String username) throws IllegalAccessException {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public Event getEventById(long eventId) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public void createEvent(Event event, String username) throws IllegalAccessException {
    // TODO Auto-generated method stub

  }

  @Override
  public void updateEvent(Event event, String username) {
    // TODO Auto-generated method stub

  }

  @Override
  public void deleteEventById(long eventId, String username) throws IllegalAccessException, ObjectNotFoundException {
    // TODO Auto-generated method stub

  }

}
