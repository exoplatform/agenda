package org.exoplatform.agenda.storage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.dao.EventAttendeeDAO;
import org.exoplatform.agenda.dao.EventDAO;
import org.exoplatform.agenda.entity.EventAttendeeEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;

public class AgendaEventAttendeeStorage {

  private EventDAO         eventDAO;

  private EventAttendeeDAO eventAttendeeDAO;

  private ListenerService  listenerService;

  public AgendaEventAttendeeStorage(EventDAO eventDAO,
                                    EventAttendeeDAO eventAttendeeDAO,
                                    ListenerService listenerService) {
    this.eventDAO = eventDAO;
    this.eventAttendeeDAO = eventAttendeeDAO;
    this.listenerService = listenerService;
  }

  public void saveEventAttendee(EventAttendee eventAttendee, long eventId) {
    EventAttendeeEntity eventAttendeeEntity = EntityMapper.toEntity(eventAttendee);
    EventEntity eventEntity = eventDAO.find(eventId);
    eventAttendeeEntity.setEvent(eventEntity);
    long eventAttendeeId = eventAttendee.getId();
    if (eventAttendeeId > 0) {
      eventAttendeeDAO.update(eventAttendeeEntity);

      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.attendee.updated",
                           eventAttendeeEntity.getEvent().getId(),
                           eventAttendeeId);
    } else {
      eventAttendeeEntity.setId(null);
      eventAttendeeEntity = eventAttendeeDAO.create(eventAttendeeEntity);
      eventAttendeeId = eventAttendeeEntity.getId();

      eventAttendee.setId(eventAttendeeId);

      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.attendee.created",
                           eventAttendeeEntity.getEvent().getId(),
                           eventAttendeeId);
    }
  }

  public List<EventAttendee> getEventAttendees(long eventId) {
    List<EventAttendeeEntity> eventAttendeeEntities = eventAttendeeDAO.getEventAttendees(eventId);
    if (eventAttendeeEntities == null) {
      return Collections.emptyList();
    }
    return eventAttendeeEntities.stream()
                                .map(eventAttendeeEntity -> EntityMapper.fromEntity(eventAttendeeEntity, eventId))
                                .collect(Collectors.toList());
  }

  public void removeEventAttendee(long eventAttendeeId) {
    EventAttendeeEntity eventAttendeeEntity = eventAttendeeDAO.find(eventAttendeeId);
    if (eventAttendeeEntity != null) {
      eventAttendeeDAO.delete(eventAttendeeEntity);

      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.attendee.deleted",
                           eventAttendeeEntity.getEvent().getId(),
                           eventAttendeeId);
    }
  }

  public EventAttendee getEventAttendee(long eventId, long identityId) {
    EventAttendeeEntity attendeeEntity = eventAttendeeDAO.getEventAttendee(eventId, identityId);
    if (attendeeEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(attendeeEntity, eventId);
  }

}
