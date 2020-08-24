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
package org.exoplatform.agenda.util;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.CalendarEntity;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.rest.entity.IdentityEntity;

public class EntityBuilder {

  private static final String IDENTITIES_REST_PATH = "/v1/social/identities"; // NOSONAR

  private static final String IDENTITIES_EXPAND    = "all";

  private EntityBuilder() {
  }

  public static final Calendar toCalendar(CalendarEntity calendarEntity) {
    return new Calendar(calendarEntity.getId(),
                        Long.parseLong(calendarEntity.getOwner().getId()),
                        calendarEntity.isSystem(),
                        calendarEntity.getTitle(),
                        calendarEntity.getDescription(),
                        calendarEntity.getCreated(),
                        calendarEntity.getUpdated(),
                        calendarEntity.getColor(),
                        calendarEntity.getAcl());
  }

  public static final CalendarEntity fromCalendar(Calendar calendar) {
    return new CalendarEntity(calendar.getId(),
                              getIdentityEntity(calendar.getOwnerId()),
                              calendar.isSystem(),
                              calendar.getTitle(),
                              calendar.getDescription(),
                              calendar.getCreated(),
                              calendar.getUpdated(),
                              calendar.getColor(),
                              calendar.getAcl());
  }

  public static final Event toEvent(EventEntity eventEntity) {
    return new Event(eventEntity.getId(),
                     eventEntity.getParent() == null ? 0l : eventEntity.getParent().getId(),
                     eventEntity.getRemoteId(),
                     eventEntity.getRemoteProviderId(),
                     eventEntity.getCalendar() == null ? 0l : eventEntity.getCalendar().getId(),
                     eventEntity.getCreator() == null ? 0l : Long.parseLong(eventEntity.getCreator().getId()),
                     eventEntity.getCreated(),
                     eventEntity.getUpdated(),
                     eventEntity.getSummary(),
                     eventEntity.getDescription(),
                     eventEntity.getLocation(),
                     eventEntity.getColor(),
                     eventEntity.getStart(),
                     eventEntity.getEnd(),
                     eventEntity.isAllDay(),
                     eventEntity.getAvailability(),
                     eventEntity.getStatus(),
                     eventEntity.getRecurrence(),
                     eventEntity.getOccurrence(),
                     eventEntity.getAcl(),
                     eventEntity.getAttendees(),
                     eventEntity.getConferences(),
                     eventEntity.getAttachments());
  }

  public static final EventEntity fromEvent(Event event) {
    return new EventEntity(event.getId(),
                           getEventEntity(event.getParentId()),
                           event.getRemoteId(),
                           event.getRemoteProviderId(),
                           getCalendarEntity(event.getCalendarId()),
                           getIdentityEntity(event.getCreatorId()),
                           event.getCreated(),
                           event.getUpdated(),
                           event.getSummary(),
                           event.getDescription(),
                           event.getLocation(),
                           event.getColor(),
                           event.getStart(),
                           event.getEnd(),
                           event.isAllDay(),
                           event.getAvailability(),
                           event.getStatus(),
                           event.getRecurrence(),
                           event.getOccurrence(),
                           event.getAcl(),
                           event.getAttendees(),
                           event.getConferences(),
                           event.getAttachments(),
                           null);
  }

  private static CalendarEntity getCalendarEntity(long calendarId) {
    if (calendarId <= 0) {
      return null;
    }
    AgendaCalendarService agendaCalendarService = ExoContainerContext.getService(AgendaCalendarService.class);
    Calendar calendar = agendaCalendarService.getCalendarById(calendarId);
    return fromCalendar(calendar);
  }

  private static EventEntity getEventEntity(long eventId) {
    if (eventId <= 0) {
      return null;
    }
    AgendaEventService agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    Event event = agendaEventService.getEventById(eventId);
    return fromEvent(event);
  }

  private static IdentityEntity getIdentityEntity(long ownerId) {
    Identity identity = getIdentity(ownerId);
    if (identity == null) {
      return null;
    }
    return org.exoplatform.social.rest.api.EntityBuilder.buildEntityIdentity(identity, IDENTITIES_REST_PATH, IDENTITIES_EXPAND);
  }

  private static final Identity getIdentity(long identityId) {
    IdentityManager identityManager = ExoContainerContext.getService(IdentityManager.class);
    return identityManager.getIdentity(String.valueOf(identityId));
  }

}
