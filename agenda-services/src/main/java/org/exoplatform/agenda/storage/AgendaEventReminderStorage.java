package org.exoplatform.agenda.storage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.dao.EventDAO;
import org.exoplatform.agenda.dao.EventReminderDAO;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.entity.EventReminderEntity;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;

public class AgendaEventReminderStorage {

  private EventDAO         eventDAO;

  private EventReminderDAO eventReminderDAO;

  private ListenerService  listenerService;

  public AgendaEventReminderStorage(EventDAO eventDAO,
                                    EventReminderDAO eventReminderDAO,
                                    ListenerService listenerService) {
    this.eventDAO = eventDAO;
    this.eventReminderDAO = eventReminderDAO;
    this.listenerService = listenerService;
  }

  public void saveEventReminder(long eventId, EventReminder eventReminder) {
    EventReminderEntity eventReminderEntity = EntityMapper.toEntity(eventReminder);

    EventEntity eventEntity = eventDAO.find(eventId);
    eventReminderEntity.setEvent(eventEntity);

    if (eventReminder.getId() == 0) {
      eventReminderEntity.setId(null);
      eventReminderEntity = eventReminderDAO.create(eventReminderEntity);
      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.reminder.created",
                           eventReminderEntity.getEvent().getId(),
                           eventReminderEntity.getId());
    } else {
      eventReminderDAO.update(eventReminderEntity);
      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.reminder.updated",
                           eventReminderEntity.getEvent().getId(),
                           eventReminderEntity.getId());
    }
  }

  public void removeEventReminder(long eventReminderId) {
    EventReminderEntity eventReminderEntity = eventReminderDAO.find(eventReminderId);
    if (eventReminderEntity != null) {
      eventReminderDAO.delete(eventReminderEntity);
      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.reminder.deleted",
                           eventReminderEntity.getEvent().getId(),
                           eventReminderId);
    }
  }

  public List<EventReminder> getEventReminders(long eventId, long userId) {
    List<EventReminderEntity> eventReminderEntities = eventReminderDAO.getEventReminders(eventId, userId);
    if (eventReminderEntities == null) {
      return Collections.emptyList();
    }
    return eventReminderEntities.stream()
                                .map(eventReminderEntity -> EntityMapper.fromEntity(eventReminderEntity))
                                .collect(Collectors.toList());
  }

}
