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

import java.io.*;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.commons.file.model.FileItem;
import org.exoplatform.commons.file.services.FileService;
import org.exoplatform.commons.file.services.FileStorageException;
import org.exoplatform.download.DownloadResource;
import org.exoplatform.download.DownloadService;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.exoplatform.upload.UploadResource;
import org.exoplatform.upload.UploadService;

public class AgendaEventStorage {

  public static final String AGENDA_FILE_SERVICE_NS = "agenda";

  private static final Log   LOG                    = ExoLogger.getLogger(AgendaEventStorage.class);

  private FileService        fileService;

  private UploadService      uploadService;

  private DownloadService    downloadService;

  private CalendarDAO        calendarDAO;

  private RemoteProviderDAO  remoteProviderDAO;

  private EventDAO           eventDAO;

  private EventReminderDAO   eventReminderDAO;

  private EventConferenceDAO eventConferenceDAO;

  private EventAttachmentDAO eventAttachmentDAO;

  private EventAttendeeDAO   eventAttendeeDAO;

  private EventRecurrenceDAO eventRecurrenceDAO;

  private ListenerService    listenerService;

  public AgendaEventStorage(FileService fileService,
                            UploadService uploadService,
                            DownloadService downloadService,
                            RemoteProviderDAO remoteProviderDAO,
                            CalendarDAO calendarDAO,
                            EventDAO eventDAO,
                            EventReminderDAO eventReminderDAO,
                            EventConferenceDAO eventConferenceDAO,
                            EventAttachmentDAO eventAttachmentDAO,
                            EventAttendeeDAO eventAttendeeDAO,
                            EventRecurrenceDAO eventRecurrenceDAO,
                            ListenerService listenerService) {
    this.fileService = fileService;
    this.uploadService = uploadService;
    this.downloadService = downloadService;
    this.calendarDAO = calendarDAO;
    this.remoteProviderDAO = remoteProviderDAO;
    this.eventDAO = eventDAO;
    this.eventReminderDAO = eventReminderDAO;
    this.eventConferenceDAO = eventConferenceDAO;
    this.eventAttachmentDAO = eventAttachmentDAO;
    this.eventAttendeeDAO = eventAttendeeDAO;
    this.eventRecurrenceDAO = eventRecurrenceDAO;
    this.listenerService = listenerService;
  }

  public List<Long> getEventIds(ZonedDateTime start, ZonedDateTime end, Long... ownerIds) {
    if (start == null) {
      throw new IllegalArgumentException("Start date is mandatory");
    }
    if (end == null) {
      throw new IllegalArgumentException("End date is mandatory");
    }

    Date startDate = new Date(start.toEpochSecond() * 1000);
    Date endDate = new Date(end.toEpochSecond() * 1000);
    return eventDAO.getEventIdsByPeriod(startDate, endDate, ownerIds);
  }

  public Event getEventById(long eventId) {
    EventEntity eventEntity = eventDAO.find(eventId);
    if (eventEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(eventEntity);
  }

  public Event createEvent(Event event,
                           List<EventAttachment> attachments,
                           List<EventConference> conferences,
                           List<EventAttendee> attendees,
                           List<EventReminder> reminders) throws AgendaException {
    EventEntity eventEntity = createEvent(event);
    createEventRecurrence(event, eventEntity);

    event = EntityMapper.fromEntity(eventEntity);
    long creatorId = eventEntity.getCreatorId();

    saveEventAttachments(event.getId(), attachments, creatorId, true);

    saveEventConferences(event.getId(), conferences, true);

    saveEventAttendees(event, attendees, creatorId, true);

    saveEventReminders(event, reminders, creatorId, true);

    broadcastEvent("exo.agenda.calendar.created", event, creatorId);
    return event;
  }

  public void updateEvent(Event event,
                          List<EventAttachment> attachments,
                          List<EventConference> conferences,
                          List<EventAttendee> attendees,
                          List<EventReminder> reminders) throws AgendaException {
    long modifierId = event.getModifierId();

    EventEntity eventEntity = updateEvent(event);

    eventEntity = eventDAO.find(eventEntity.getId());
    event = EntityMapper.fromEntity(eventEntity);

    saveEventAttachments(event.getId(), attachments, modifierId, false);

    saveEventConferences(event.getId(), conferences, false);

    saveEventAttendees(event, attendees, modifierId, false);

    saveEventReminders(event, reminders, modifierId, false);

    broadcastEvent("exo.agenda.calendar.updated", event, modifierId);
  }

  public void deleteEventById(long eventId) {
    EventEntity eventEntity = eventDAO.deleteEvent(eventId);
    if (eventEntity != null) {
      broadcastEvent("exo.agenda.calendar.deleted", EntityMapper.fromEntity(eventEntity), 0);
    }
  }

  public void saveEventReminders(Event event,
                                 List<EventReminder> reminders,
                                 long userId,
                                 boolean newEvent) throws AgendaException {
    List<EventReminder> savedReminders = newEvent ? Collections.emptyList() : getEventReminders(event.getId(), userId);
    List<EventReminder> newReminders = reminders == null ? Collections.emptyList() : reminders;
    List<EventReminder> remindersToDelete =
                                          savedReminders.stream()
                                                        .filter(reminder -> newReminders.stream()
                                                                                        .noneMatch(newReminder -> newReminder.getId() == reminder.getId()))
                                                        .collect(Collectors.toList());

    // Delete Reminders
    for (EventReminder eventReminder : remindersToDelete) {
      removeEventReminder(eventReminder.getId());
    }

    // Create new Reminders
    for (EventReminder eventReminder : newReminders) {
      if (newEvent && eventReminder.getId() != 0) {
        LOG.warn("Event Reminders must not have an id for new events");
        eventReminder.setId(0);
      }
      eventReminder.setReceiverId(userId);
      saveEventReminder(event, eventReminder);
    }
  }

  public void saveEventReminder(Event event, EventReminder eventReminder) throws AgendaException {
    ZonedDateTime eventStartDate = event.getStart();
    if (eventReminder.getBefore() <= 0 || eventReminder.getBeforePeriodType() == null) {
      throw new AgendaException(AgendaExceptionType.REMINDER_DATE_CANT_COMPUTE);
    }
    ZonedDateTime reminderDate = null;
    if (eventReminder.getBefore() == 0) {
      switch (eventReminder.getBeforePeriodType()) {
      case MINUTE:
        reminderDate = eventStartDate.minusMinutes(eventReminder.getBefore());
        break;
      case HOUR:
        reminderDate = eventStartDate.minusHours(eventReminder.getBefore());
        break;
      case DAY:
        reminderDate = eventStartDate.minusDays(eventReminder.getBefore());
        break;
      case WEEK:
        reminderDate = eventStartDate.minusWeeks(eventReminder.getBefore());
        break;
      }
    } else {
      reminderDate = eventStartDate;
    }
    eventReminder.setDatetime(reminderDate);

    EventReminderEntity eventReminderEntity = EntityMapper.toEntity(eventReminder);

    EventEntity eventEntity = eventDAO.find(event.getId());
    eventReminderEntity.setEvent(eventEntity);

    if (eventReminder.getId() == 0) {
      eventReminderEntity.setId(null);
      eventReminderDAO.create(eventReminderEntity);
    } else {
      eventReminderDAO.update(eventReminderEntity);
    }
  }

  public void removeEventReminder(long eventReminderId) {
    EventReminderEntity eventReminderEntity = eventReminderDAO.find(eventReminderId);
    if (eventReminderEntity != null) {
      eventReminderDAO.delete(eventReminderEntity);
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

  public void saveEventAttendees(Event event, List<EventAttendee> attendees, long creatorUserId, boolean newEvent) {
    long eventId = event.getId();

    List<EventAttendee> savedAttendees = newEvent ? Collections.emptyList() : getEventAttendees(event.getId());
    List<EventAttendee> newAttendees = attendees == null ? Collections.emptyList() : attendees;
    List<EventAttendee> attendeesToDelete =
                                          savedAttendees.stream()
                                                        .filter(attendee -> newAttendees.stream()
                                                                                        .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                        .collect(Collectors.toList());

    // Delete attendees
    for (EventAttendee eventAttendee : attendeesToDelete) {
      removeEventAttendee(eventAttendee.getId());
    }

    List<EventAttendee> attendeesToCreate =
                                          newAttendees.stream()
                                                      .filter(attendee -> savedAttendees.stream()
                                                                                        .noneMatch(newAttendee -> newAttendee.getIdentityId() == attendee.getIdentityId()))
                                                      .collect(Collectors.toList());

    // Create new attendees
    for (EventAttendee eventAttendee : attendeesToCreate) {
      long identityId = eventAttendee.getIdentityId();

      EventAttendeeResponse response = null;
      if (identityId == creatorUserId) {
        if (event.getStatus() == EventStatus.CONFIRMED) {
          response = EventAttendeeResponse.ACCEPTED;
        } else if (event.getStatus() == EventStatus.TENTATIVE) {
          response = EventAttendeeResponse.TENTATIVE;
        } else if (event.getStatus() == EventStatus.CANCELED) {
          response = EventAttendeeResponse.DECLINED;
        }
      } else {
        response = EventAttendeeResponse.NEEDS_ACTION;
      }
      eventAttendee.setResponse(response);
      saveEventAttendee(eventAttendee, eventId);
    }
  }

  public void saveEventAttendee(EventAttendee eventAttendee, long eventId) {
    EventAttendeeEntity eventAttendeeEntity = EntityMapper.toEntity(eventAttendee);
    EventEntity eventEntity = eventDAO.find(eventId);
    eventAttendeeEntity.setEvent(eventEntity);
    if (eventAttendee.getId() > 0) {
      eventAttendeeDAO.update(eventAttendeeEntity);
    } else {
      eventAttendeeEntity.setId(null);
      eventAttendeeEntity = eventAttendeeDAO.create(eventAttendeeEntity);
      eventAttendee.setId(eventAttendeeEntity.getId());
    }
  }

  public List<EventAttendee> getEventAttendees(long eventId) {
    List<EventAttendeeEntity> eventAttendeeEntities = eventAttendeeDAO.getEventAttendees(eventId);
    if (eventAttendeeEntities == null) {
      return Collections.emptyList();
    }
    return eventAttendeeEntities.stream()
                                .map(eventAttendeeEntity -> EntityMapper.fromEntity(eventAttendeeEntity))
                                .collect(Collectors.toList());
  }

  public void removeEventAttendee(long eventAttendeeId) {
    EventAttendeeEntity eventAttendeeEntity = eventAttendeeDAO.find(eventAttendeeId);
    if (eventAttendeeEntity != null) {
      eventAttendeeDAO.delete(eventAttendeeEntity);
    }
  }

  public void saveEventAttachment(long eventId, long fileId) {
    EventAttachmentEntity eventAttachmentEntity = new EventAttachmentEntity();
    EventEntity eventEntity = eventDAO.find(eventId);
    if (eventEntity == null) {
      throw new IllegalStateException("Can't find event with id : " + eventId);
    }
    eventAttachmentEntity.setEvent(eventEntity);
    eventAttachmentEntity.setFileId(fileId);
    eventAttachmentDAO.create(eventAttachmentEntity);
  }

  public void removeEventAttachment(long attachmentId) {
    EventAttachmentEntity eventAttachmentEntity = eventAttachmentDAO.find(attachmentId);
    if (eventAttachmentEntity != null) {
      eventAttachmentDAO.delete(eventAttachmentEntity);
    }
  }

  public List<EventAttachment> getEventAttachments(long eventId) {
    List<EventAttachmentEntity> eventAttachments = eventAttachmentDAO.getEventAttachments(eventId);
    if (eventAttachments == null) {
      return Collections.emptyList();
    }
    return eventAttachments.stream().map(EntityMapper::fromEntity).collect(Collectors.toList());
  }

  public EventAttachment getEventAttachmentById(long attachmentId) {
    EventAttachmentEntity eventAttachmentEntity = eventAttachmentDAO.find(attachmentId);
    if (eventAttachmentEntity == null) {
      return null;
    }
    return EntityMapper.fromEntity(eventAttachmentEntity);
  }

  public String getEventAttachmentDownloadLink(long attachmentId) {
    EventAttachmentEntity eventAttachmentEntity = eventAttachmentDAO.find(attachmentId);
    if (eventAttachmentEntity == null) {
      return null;
    }
    try {
      FileItem file = fileService.getFile(eventAttachmentEntity.getFileId());
      InputStreamDownloadResource resource = new InputStreamDownloadResource(file);
      String downloadResourceId = downloadService.addDownloadResource(resource);
      return downloadService.getDownloadLink(downloadResourceId);
    } catch (IOException | FileStorageException e) {
      throw new IllegalStateException("Error while retrieving file of attachment with id " + attachmentId);
    }
  }

  public EventConference saveEventConference(EventConference eventConference) {
    long eventId = eventConference.getEventId();
    EventEntity eventEntity = eventDAO.find(eventId);
    if (eventEntity == null) {
      throw new IllegalStateException("Can't find event with id " + eventId);
    }
    EventConferenceEntity eventConferenceEntity = EntityMapper.toEntity(eventConference);
    eventConferenceEntity.setEvent(eventEntity);

    if (eventConference.getId() <= 0) {
      eventConferenceEntity.setId(null);
      eventConferenceEntity = eventConferenceDAO.create(eventConferenceEntity);
    } else {
      eventConferenceEntity = eventConferenceDAO.update(eventConferenceEntity);
    }
    return EntityMapper.fromEntity(eventConferenceEntity);
  }

  public void removeEventConference(long conferenceId) {
    EventConferenceEntity eventConferenceEntity = eventConferenceDAO.find(conferenceId);
    if (eventConferenceEntity == null) {
      throw new IllegalStateException("Event conference not found with id " + conferenceId);
    }
    eventConferenceDAO.delete(eventConferenceEntity);
  }

  public List<EventConference> getEventConferences(long eventId) {
    List<EventConferenceEntity> eventConferenceEntities = eventConferenceDAO.getEventConferences(eventId);
    if (eventConferenceEntities == null) {
      return Collections.emptyList();
    }
    return eventConferenceEntities.stream().map(EntityMapper::fromEntity).collect(Collectors.toList());
  }

  public void saveEventAttachments(long eventId, List<EventAttachment> attachments, long creatorIdentityId, boolean newEvent) {
    List<EventAttachment> savedAttachments = newEvent ? Collections.emptyList() : getEventAttachments(eventId);
    List<EventAttachment> newAttachments = attachments == null ? Collections.emptyList() : attachments;
    List<EventAttachment> attachmentsToDelete =
                                              savedAttachments.stream()
                                                              .filter(attachment -> newAttachments.stream()
                                                                                                  .noneMatch(newAttachment -> newAttachment.getFileId() == attachment.getFileId()))
                                                              .collect(Collectors.toList());

    // Delete attachments
    for (EventAttachment eventAttachment : attachmentsToDelete) {
      removeEventAttachment(eventAttachment.getId());
    }

    List<EventAttachment> attachmentsToCreate =
                                              newAttachments == null ? Collections.emptyList()
                                                                     : newAttachments.stream()
                                                                                     .filter(newAttachment -> savedAttachments.stream()
                                                                                                                              .noneMatch(attachment -> newAttachment.getFileId() == attachment.getFileId()))
                                                                                     .collect(Collectors.toList());
    // Create new attachments
    for (EventAttachment eventAttachment : attachmentsToCreate) {
      if (eventAttachment instanceof EventAttachmentUpload) {
        String uploadId = ((EventAttachmentUpload) eventAttachment).getUploadId();
        UploadResource uploadResource = uploadService.getUploadResource(uploadId);
        if (uploadResource == null) {
          LOG.warn("Can't find an uploaded attachement to event with id : {}", uploadId);
        } else {
          String fileName = uploadResource.getFileName();
          String mimeType = uploadResource.getMimeType();
          String storeLocation = uploadResource.getStoreLocation();
          File file = new File(storeLocation);
          if (file.exists()) {
            try (FileInputStream inputStream = new FileInputStream(file)) {
              FileItem fileItem = new FileItem(null,
                                               fileName,
                                               mimeType,
                                               AGENDA_FILE_SERVICE_NS,
                                               inputStream.available(),
                                               new Date(),
                                               String.valueOf(creatorIdentityId),
                                               false,
                                               inputStream);
              fileItem = fileService.writeFile(fileItem);
              long fileId = fileItem.getFileInfo().getId();
              saveEventAttachment(eventId, fileId);
            } catch (Exception e) {
              LOG.warn("An error happened while writing file '{}' read from location '{}'", fileName, storeLocation, e);
            }
          } else {
            LOG.warn("Can't find file '{}' read from location '{}'", fileName, storeLocation);
          }
        }
      } else if (eventAttachment.getFileId() > 0) {
        saveEventAttachment(eventId, eventAttachment.getFileId());
      } else {
        LOG.warn("Uploaded file attachment doesn't have a fileId neither an uploadId, it will be ignored. Attachment = {} ",
                 eventAttachment);
      }
    }
  }

  public void saveEventConferences(long eventId, List<EventConference> conferences, boolean newEvent) {
    List<EventConference> savedConferences = newEvent ? Collections.emptyList() : getEventConferences(eventId);
    List<EventConference> newConferences = conferences == null ? Collections.emptyList() : conferences;
    List<EventConference> conferencesToDelete =
                                              savedConferences.stream()
                                                              .filter(conference -> newConferences.stream()
                                                                                                  .noneMatch(newConference -> newConference.getId() == conference.getId()))
                                                              .collect(Collectors.toList());

    // Delete conferences
    for (EventConference eventConference : conferencesToDelete) {
      removeEventConference(eventConference.getId());
    }

    // Create new conferences
    for (EventConference eventConference : newConferences) {
      eventConference.setEventId(eventId);
      if (newEvent && eventConference.getId() != 0) {
        LOG.warn("Event conferences must not have an id for new events");
        eventConference.setId(0);
      }
      saveEventConference(eventConference);
    }
  }

  public List<RemoteProvider> getRemoteProviders() {
    List<RemoteProviderEntity> remoteProviders = remoteProviderDAO.findAll();
    return remoteProviders == null ? Collections.emptyList()
                                   : remoteProviders.stream()
                                                    .map(remoteProviderEntity -> EntityMapper.fromEntity(remoteProviderEntity))
                                                    .collect(Collectors.toList());
  }

  public RemoteProvider saveRemoteProvider(RemoteProvider remoteProvider) {
    RemoteProviderEntity remoteProviderEntity = EntityMapper.toEntity(remoteProvider);
    if (remoteProviderEntity.getId() == null) {
      remoteProviderEntity = remoteProviderDAO.create(remoteProviderEntity);
    } else {
      remoteProviderEntity = remoteProviderDAO.update(remoteProviderEntity);
    }
    return EntityMapper.fromEntity(remoteProviderEntity);
  }

  /**
   * @param parentRecurrentEvent a parent recurrent {@link Event}
   * @param start start DateTime of period to search on
   * @param end end DateTime of period to search on
   * @return {@link List} of {@link ZonedDateTime} corresponding to exceptional
   *         occurences events Identifiers of a parent recurrent event for a
   *         selected period of time
   */
  public List<Long> getExceptionalOccurenceEventIds(long parentRecurrentEvent,
                                                    ZonedDateTime start,
                                                    ZonedDateTime end) {
    return eventDAO.getExceptionalOccurenceEventIds(parentRecurrentEvent,
                                                    AgendaDateUtils.toDate(start),
                                                    AgendaDateUtils.toDate(end));
  }

  private EventEntity createEvent(Event event) {
    EventEntity eventEntity = EntityMapper.toEntity(event);
    eventEntity.setId(null);

    if (event.getParentId() > 0) {
      EventEntity parentEvent = eventDAO.find(event.getParentId());
      eventEntity.setParent(parentEvent);
    }

    updateEventCalendar(event, eventEntity);

    if (event.getRemoteProviderId() > 0) {
      RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.find(event.getRemoteProviderId());
      eventEntity.setRemoteProvider(remoteProviderEntity);
    }

    return eventDAO.create(eventEntity);
  }

  private EventEntity updateEvent(Event event) {
    EventEntity eventEntity = EntityMapper.toEntity(event);

    updateEventParent(event, eventEntity);
    updateEventCalendar(event, eventEntity);
    updateEventRemoteProvider(event, eventEntity);
    updateEventRecurrence(event, eventEntity);

    return eventDAO.update(eventEntity);
  }

  private void updateEventCalendar(Event event, EventEntity eventEntity) {
    CalendarEntity calendarEntity = calendarDAO.find(event.getCalendarId());
    eventEntity.setCalendar(calendarEntity);
  }

  private void updateEventParent(Event event, EventEntity eventEntity) {
    if (event.getParentId() > 0) {
      EventEntity parentEvent = eventDAO.find(event.getParentId());
      if (parentEvent == null) {
        throw new IllegalStateException("Can't find parent event with id " + event.getParentId());
      }
      eventEntity.setParent(parentEvent);
    }
  }

  private void updateEventRemoteProvider(Event event, EventEntity eventEntity) {
    if (event.getRemoteProviderId() > 0) {
      RemoteProviderEntity remoteProviderEntity = remoteProviderDAO.find(event.getRemoteProviderId());
      if (remoteProviderEntity == null) {
        throw new IllegalStateException("Can't find remote calendar provider with id " + event.getRemoteProviderId());
      }
      eventEntity.setRemoteProvider(remoteProviderEntity);
    }
  }

  private void updateEventRecurrence(Event event, EventEntity eventEntity) {
    EventRecurrence recurrence = event.getRecurrence();
    EventEntity storedEventEntity = eventDAO.find(eventEntity.getId());
    if (storedEventEntity == null) {
      throw new IllegalStateException("Can't find event with id " + eventEntity.getId());
    }

    if (recurrence != null) {
      EventRecurrenceEntity eventRecurrenceEntity = EntityMapper.toEntity(event, recurrence);
      eventRecurrenceEntity.setEvent(eventEntity);
      if (storedEventEntity.getRecurrence() != null) {
        eventRecurrenceEntity.setId(storedEventEntity.getRecurrence().getId());
        eventRecurrenceDAO.update(eventRecurrenceEntity);
      } else {
        eventRecurrenceEntity.setId(null);
        eventRecurrenceDAO.update(eventRecurrenceEntity);
      }
      eventEntity.setRecurrence(eventRecurrenceEntity);
    } else if (storedEventEntity.getRecurrence() != null) {
      eventRecurrenceDAO.delete(storedEventEntity.getRecurrence());
    }
  }

  private void createEventRecurrence(Event event, EventEntity eventEntity) {
    if (event.getRecurrence() != null) {
      EventRecurrenceEntity eventRecurrenceEntity = EntityMapper.toEntity(event, event.getRecurrence());
      eventRecurrenceEntity.setId(null);
      eventRecurrenceEntity.setEvent(eventEntity);
      eventEntity.setRecurrence(eventRecurrenceEntity);
      eventRecurrenceDAO.create(eventRecurrenceEntity);
    }
  }

  private void broadcastEvent(String eventName, Event event, long userId) {
    try {
      listenerService.broadcast(eventName, userId, event);
    } catch (Exception e) {
      LOG.warn("Error broadcasting event '" + eventName + "' on agenda event with id '" + event.getId() + "'", e);
    }
  }

  public class InputStreamDownloadResource extends DownloadResource {
    private InputStream inputStream;

    public InputStreamDownloadResource(FileItem fileItem) throws IOException {
      super(fileItem.getFileInfo().getMimetype());
      setDownloadName(fileItem.getFileInfo().getName());
      this.inputStream = fileItem.getAsStream();
    }

    public InputStream getInputStream() {
      return inputStream;
    }
  }

}
