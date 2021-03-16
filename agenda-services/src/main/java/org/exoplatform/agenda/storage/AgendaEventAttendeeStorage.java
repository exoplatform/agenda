package org.exoplatform.agenda.storage;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.collections.CollectionUtils;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.dao.EventAttendeeDAO;
import org.exoplatform.agenda.dao.EventDAO;
import org.exoplatform.agenda.entity.EventAttendeeEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeeList;
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

  public EventAttendeeList getEventAttendees(long eventId) {
    List<EventAttendeeEntity> eventAttendeeEntities = eventAttendeeDAO.getEventAttendees(eventId);
    return fromAttendeeEntities(eventId, eventAttendeeEntities);
  }

  public EventAttendeeList getEventAttendees(long eventId, EventAttendeeResponse... responses) {
    List<EventAttendeeEntity> eventAttendeeEntities = eventAttendeeDAO.getEventAttendeesByResponses(eventId, responses);
    return fromAttendeeEntities(eventId, eventAttendeeEntities);
  }

  public EventAttendeeList getEventAttendees(long eventId, long identityId) {
    List<EventAttendeeEntity> eventAttendeeEntities = eventAttendeeDAO.getEventAttendees(eventId, identityId);
    return fromAttendeeEntities(eventId, eventAttendeeEntities);
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

  private EventAttendeeList fromAttendeeEntities(long eventId, List<EventAttendeeEntity> eventAttendeeEntities) {
    if (CollectionUtils.isEmpty(eventAttendeeEntities)) {
      return EventAttendeeList.EMPTY_ATTENDEE_LIST;
    } else {
      List<EventAttendee> attendees = eventAttendeeEntities.stream()
                                                           .map(eventAttendeeEntity -> EntityMapper.fromEntity(eventAttendeeEntity,
                                                                                                               eventId))
                                                           .collect(Collectors.toList());
      return new EventAttendeeList(attendees);
    }
  }

}
