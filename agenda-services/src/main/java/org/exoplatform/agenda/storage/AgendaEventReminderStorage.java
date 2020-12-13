package org.exoplatform.agenda.storage;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.exoplatform.agenda.dao.EventReminderDAO;
import org.exoplatform.agenda.entity.EventReminderEntity;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;

public class AgendaEventReminderStorage {

  private EventReminderDAO eventReminderDAO;

  private ListenerService  listenerService;

  public AgendaEventReminderStorage(EventReminderDAO eventReminderDAO,
                                    ListenerService listenerService) {
    this.eventReminderDAO = eventReminderDAO;
    this.listenerService = listenerService;
  }

  public void saveEventReminder(EventReminder eventReminder) {
    EventReminderEntity eventReminderEntity = EntityMapper.toEntity(eventReminder);

    if (eventReminder.getId() == 0) {
      eventReminderEntity.setId(null);
      eventReminderEntity = eventReminderDAO.create(eventReminderEntity);
      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.reminder.created",
                           eventReminderEntity.getEventId(),
                           eventReminderEntity.getId());
    } else {
      eventReminderDAO.update(eventReminderEntity);
      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.reminder.updated",
                           eventReminderEntity.getEventId(),
                           eventReminderEntity.getId());
    }
  }

  public void removeEventReminder(long eventReminderId) {
    EventReminderEntity eventReminderEntity = eventReminderDAO.find(eventReminderId);
    if (eventReminderEntity != null) {
      eventReminderDAO.delete(eventReminderEntity);
      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.reminder.deleted",
                           eventReminderEntity.getEventId(),
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

  public List<EventReminder> getEventReminders(long eventId) {
    List<EventReminderEntity> eventReminderEntities = eventReminderDAO.getEventReminders(eventId);
    if (eventReminderEntities == null) {
      return Collections.emptyList();
    }
    return eventReminderEntities.stream()
                                .map(eventReminderEntity -> EntityMapper.fromEntity(eventReminderEntity))
                                .collect(Collectors.toList());
  }

  public void removeEventReminders(long eventId, long identityId) {
    List<EventReminderEntity> eventReminderEntities = eventReminderDAO.getEventReminders(eventId, identityId);
    if (eventReminderEntities != null) {
      eventReminderDAO.deleteAll(eventReminderEntities);
    }
  }

  public void removeEventReminders(long eventId) {
    List<EventReminderEntity> eventReminderEntities = eventReminderDAO.getEventReminders(eventId);
    if (eventReminderEntities != null) {
      eventReminderDAO.deleteAll(eventReminderEntities);
    }
  }

  public List<EventReminder> getEventReminders(ZonedDateTime start, ZonedDateTime end) {
    Date startDate = new Date(start.withZoneSameInstant(ZoneOffset.UTC).toEpochSecond() * 1000);
    Date endDate = new Date(end.withZoneSameInstant(ZoneOffset.UTC).toEpochSecond() * 1000);

    List<EventReminderEntity> reminderEntities = eventReminderDAO.getEventReminders(startDate, endDate);
    return reminderEntities.stream()
                           .map(eventReminderEntity -> EntityMapper.fromEntity(eventReminderEntity))
                           .collect(Collectors.toList());
  }

}
