package org.exoplatform.agenda.storage;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.exoplatform.agenda.dao.*;
import org.exoplatform.agenda.entity.*;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.commons.exception.ObjectNotFoundException;

public class AgendaEventDatePollStorage {

  private EventDatePollDAO   datePollDAO;

  private EventDateOptionDAO dateOptionDAO;

  private EventDateVoteDAO   dateVoteDAO;

  public AgendaEventDatePollStorage(EventDateOptionDAO dateOptionDAO,
                                    EventDatePollDAO datePollDAO,
                                    EventDateVoteDAO dateVoteDAO) {
    this.datePollDAO = datePollDAO;
    this.dateOptionDAO = dateOptionDAO;
    this.dateVoteDAO = dateVoteDAO;
  }

  public List<EventDateOption> getEventDateOptions(long eventId) {
    List<EventDateOptionEntity> dateOptionEntities = dateOptionDAO.findDateOptionsByEventId(eventId);
    if (dateOptionEntities == null || dateOptionEntities.isEmpty()) {
      return Collections.emptyList();
    }
    EventDatePollEntity datePollEntity = datePollDAO.findDatePollByEventId(eventId);
    Long selectedDateOptionId = datePollEntity == null
        || datePollEntity.getSelectedDateOptionId() == null ? 0l : datePollEntity.getSelectedDateOptionId();

    List<EventDateOption> dateOptions = new ArrayList<>();
    for (EventDateOptionEntity eventDateOptionEntity : dateOptionEntities) {
      ZonedDateTime startDate = AgendaDateUtils.fromDate(eventDateOptionEntity.getStartDate());
      ZonedDateTime endDate = AgendaDateUtils.fromDate(eventDateOptionEntity.getEndDate());
      Long dateOptionId = eventDateOptionEntity.getId();
      List<EventDateVoteEntity> dateVoteEntities = dateVoteDAO.findVotersByDateOptionId(dateOptionId);
      List<Long> voters = dateVoteEntities.stream()
                                          .map(dateVoteEntity -> dateVoteEntity.getIdentityId())
                                          .collect(Collectors.toList());
      EventDateOption dateOption = new EventDateOption(dateOptionId,
                                                       eventDateOptionEntity.getEventId(),
                                                       startDate,
                                                       endDate,
                                                       eventDateOptionEntity.isAllDay(),
                                                       selectedDateOptionId == dateOptionId, // NOSONAR
                                                       voters);
      dateOptions.add(dateOption);
    }
    return dateOptions;
  }

  public EventDateOption createDateOption(EventDateOption dateOption) {
    EventDateOptionEntity dateOptionEntity = new EventDateOptionEntity();
    dateOptionEntity.setId(null);
    dateOptionEntity.setAllDay(dateOption.isAllDay());
    dateOptionEntity.setEventId(dateOption.getEventId());

    ZonedDateTime start = dateOption.getStart();
    ZonedDateTime end = dateOption.getEnd();

    if (dateOption.isAllDay()) {
      start = start.toLocalDate().atStartOfDay(ZoneOffset.UTC);
      end = end.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1);
    } else {
      start = start.withZoneSameInstant(ZoneOffset.UTC);
      end = end.withZoneSameInstant(ZoneOffset.UTC);
    }

    dateOptionEntity.setStartDate(AgendaDateUtils.toDate(start));
    dateOptionEntity.setEndDate(AgendaDateUtils.toDate(end));
    dateOptionEntity = dateOptionDAO.create(dateOptionEntity);

    return new EventDateOption(dateOptionEntity.getId(),
                               dateOptionEntity.getEventId(),
                               AgendaDateUtils.fromDate(dateOptionEntity.getStartDate()),
                               AgendaDateUtils.fromDate(dateOptionEntity.getEndDate()),
                               dateOptionEntity.isAllDay(),
                               false,
                               null);
  }

  public void selectDateOption(long dateOptionId) throws ObjectNotFoundException {
    EventDateOptionEntity dateOptionEntity = dateOptionDAO.find(dateOptionId);
    if (dateOptionEntity == null) {
      throw new ObjectNotFoundException("Date Option with id " + dateOptionId + " is not found");
    }

    Long eventId = dateOptionEntity.getEventId();
    EventDatePollEntity datePollEntity = datePollDAO.findDatePollByEventId(eventId);
    if (datePollEntity == null) {
      datePollEntity = new EventDatePollEntity();
      datePollEntity.setEventId(eventId);
      datePollEntity.setId(null);
      datePollEntity.setSelectedDateOptionId(dateOptionId);
      datePollDAO.create(datePollEntity);
    } else {
      datePollEntity.setSelectedDateOptionId(dateOptionId);
      datePollDAO.update(datePollEntity);
    }
  }

  public void deleteEventVotes(long eventId) {
    List<EventDateOptionEntity> dateOptionEntities = dateOptionDAO.findDateOptionsByEventId(eventId);
    if (dateOptionEntities != null && !dateOptionEntities.isEmpty()) {
      List<Long> eventDateOptionIds = dateOptionEntities.stream().map(EventDateOptionEntity::getId).collect(Collectors.toList());
      dateVoteDAO.deleteDateOptionsVotes(eventDateOptionIds);
    }
  }

  public void updateDateOption(EventDateOption dateOption) throws ObjectNotFoundException {
    long dateOptionId = dateOption.getId();

    EventDateOptionEntity dateOptionEntity = dateOptionDAO.find(dateOptionId);
    if (dateOptionEntity == null) {
      throw new ObjectNotFoundException("Date Option with id " + dateOptionId + " is not found");
    }
    dateOptionEntity.setAllDay(dateOption.isAllDay());
    dateOptionEntity.setEventId(dateOption.getEventId());

    ZonedDateTime start = dateOption.getStart();
    ZonedDateTime end = dateOption.getEnd();

    if (dateOption.isAllDay()) {
      start = start.toLocalDate().atStartOfDay(ZoneOffset.UTC);
      end = end.toLocalDate().atStartOfDay(ZoneOffset.UTC).plusDays(1).minusSeconds(1);
    } else {
      start = start.withZoneSameInstant(ZoneOffset.UTC);
      end = end.withZoneSameInstant(ZoneOffset.UTC);
    }

    dateOptionEntity.setStartDate(AgendaDateUtils.toDate(start));
    dateOptionEntity.setEndDate(AgendaDateUtils.toDate(end));
    dateOptionDAO.update(dateOptionEntity);
  }

  public void deleteDateOption(EventDateOption dateOption) {
    if (dateOption.isSelected()) {
      EventDatePollEntity datePollEntity = datePollDAO.findDatePollByEventId(dateOption.getEventId());
      if (datePollEntity != null && datePollEntity.getSelectedDateOptionId() == dateOption.getId()) {
        datePollEntity.setSelectedDateOptionId(null);
        datePollDAO.update(datePollEntity);
      }
    }

    EventDateOptionEntity dateOptionEntity = dateOptionDAO.find(dateOption.getId());
    if (dateOptionEntity != null) {
      dateOptionDAO.delete(dateOptionEntity);
    }
  }

  public EventDateOption getDateOption(long dateOptionId, boolean withVoters, boolean withSelection) {
    EventDateOptionEntity dateOptionEntity = dateOptionDAO.find(dateOptionId);
    if (dateOptionEntity == null) {
      return null;
    }
    EventDatePollEntity datePollEntity = withSelection ? datePollDAO.findDatePollByEventId(dateOptionEntity.getEventId()) : null;
    Long selectedDateOptionId = datePollEntity == null
        || datePollEntity.getSelectedDateOptionId() == null ? 0l : datePollEntity.getSelectedDateOptionId();

    ZonedDateTime startDate = AgendaDateUtils.fromDate(dateOptionEntity.getStartDate());
    ZonedDateTime endDate = AgendaDateUtils.fromDate(dateOptionEntity.getEndDate());
    List<EventDateVoteEntity> dateVoteEntities = withVoters ? dateVoteDAO.findVotersByDateOptionId(dateOptionId)
                                                            : Collections.emptyList();
    List<Long> voters = dateVoteEntities.stream()
                                        .map(dateVoteEntity -> dateVoteEntity.getIdentityId())
                                        .collect(Collectors.toList());
    return new EventDateOption(dateOptionId,
                               dateOptionEntity.getEventId(),
                               startDate,
                               endDate,
                               dateOptionEntity.isAllDay(),
                               selectedDateOptionId == dateOptionId, // NOSONAR
                               voters);
  }

  public void vote(long dateOptionId, long identityId) throws ObjectNotFoundException {
    EventDateOptionEntity dateOptionEntity = dateOptionDAO.find(dateOptionId);
    if (dateOptionEntity == null) {
      throw new ObjectNotFoundException("Date Option with id " + dateOptionId + " is not found");
    }

    EventDateVoteEntity dateVoteEntity = dateVoteDAO.findVoteByOptionAndIdentity(dateOptionId, identityId);
    if (dateVoteEntity != null) {
      // Idempotent operation, vote already exists
      return;
    }
    dateVoteEntity = new EventDateVoteEntity();
    dateVoteEntity.setDateOptionId(dateOptionId);
    dateVoteEntity.setIdentityId(identityId);
    dateVoteDAO.create(dateVoteEntity);
  }

  public void dismiss(long dateOptionId, long identityId) throws ObjectNotFoundException {
    EventDateOptionEntity dateOptionEntity = dateOptionDAO.find(dateOptionId);
    if (dateOptionEntity == null) {
      throw new ObjectNotFoundException("Date Option with id " + dateOptionId + " is not found");
    }

    EventDateVoteEntity dateVoteEntity = dateVoteDAO.findVoteByOptionAndIdentity(dateOptionId, identityId);
    if (dateVoteEntity == null) {
      // Idempotent operation, vote not exists
      return;
    }
    dateVoteDAO.delete(dateVoteEntity);
  }

}
