package org.exoplatform.agenda.service;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.storage.AgendaEventDatePollStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

public class AgendaEventDatePollServiceImpl implements AgendaEventDatePollService {

  private IdentityManager            identityManager;

  private ListenerService            listenerService;

  private AgendaEventAttendeeService eventAttendeeService;

  private AgendaEventDatePollStorage datePollStorage;

  private AgendaEventStorage         eventStorage;

  public AgendaEventDatePollServiceImpl(AgendaEventDatePollStorage datePollStorage,
                                        AgendaEventStorage eventStorage,
                                        AgendaEventAttendeeService eventAttendeeService,
                                        IdentityManager identityManager,
                                        ListenerService listenerService) {
    this.eventAttendeeService = eventAttendeeService;
    this.datePollStorage = datePollStorage;
    this.eventStorage = eventStorage;
    this.identityManager = identityManager;
    this.listenerService = listenerService;
  }

  @Override
  public List<EventDateOption> createEventPoll(long eventId, List<EventDateOption> dateOptions, long userIdentityId) {
    if (dateOptions == null || dateOptions.isEmpty()) {
      return Collections.emptyList();
    }
    List<EventDateOption> createdDateOptions = new ArrayList<>();
    for (EventDateOption eventDateOption : dateOptions) {
      eventDateOption.setId(0);
      eventDateOption.setEventId(eventId);
      eventDateOption.setSelected(false);
      EventDateOption createdDateOption = datePollStorage.createDateOption(eventDateOption);
      createdDateOptions.add(createdDateOption);
    }

    Utils.broadcastEvent(listenerService, Utils.POST_CREATE_AGENDA_EVENT_POLL, eventId, userIdentityId);

    return createdDateOptions;
  }

  @Override
  public List<EventDateOption> updateEventDateOptions(long eventId, List<EventDateOption> dateOptions) {
    if (dateOptions == null) {
      dateOptions = Collections.emptyList();
    }
    List<EventDateOption> existingDateOptions = getEventDateOptions(eventId, ZoneOffset.UTC);

    List<EventDateOption> dateOptionsToCreate = getDateOptionsToCreate(dateOptions);
    List<EventDateOption> dateOptionsToUpdate = getDateOptionsToUpdate(dateOptions, existingDateOptions);
    List<EventDateOption> dateOptionsToDelete = getDateOptionsToDelete(dateOptions, existingDateOptions);
    for (EventDateOption eventDateOption : dateOptionsToCreate) {
      eventDateOption.setEventId(eventId);
      EventDateOption createdDateOption = datePollStorage.createDateOption(eventDateOption);
      eventDateOption.setId(createdDateOption.getId());
    }
    for (EventDateOption eventDateOption : dateOptionsToUpdate) {
      eventDateOption.setEventId(eventId);
      try {
        datePollStorage.updateDateOption(eventDateOption);
      } catch (ObjectNotFoundException e) {
        // Attempt to create a new one
        datePollStorage.createDateOption(eventDateOption);
      }
    }
    for (EventDateOption eventDateOption : dateOptionsToDelete) {
      datePollStorage.deleteDateOption(eventDateOption);
    }

    Utils.broadcastEvent(listenerService, Utils.POST_UPDATE_AGENDA_EVENT_POLL, eventId, dateOptions);

    return dateOptions;
  }

  @Override
  public List<EventDateOption> getEventDateOptions(long eventId, ZoneId userTimeZone) {
    List<EventDateOption> eventDateOptions = datePollStorage.getEventDateOptions(eventId);
    eventDateOptions.forEach(eventDateOption -> transformDatesTimeZone(eventDateOption, userTimeZone));
    return eventDateOptions;
  }

  @Override
  public EventDateOption getEventDateOption(long dateOptionId, ZoneId userTimeZone) {
    EventDateOption dateOption = datePollStorage.getDateOption(dateOptionId, true, true);
    if (dateOption != null) {
      transformDatesTimeZone(dateOption, userTimeZone);
    }
    return dateOption;
  }

  @Override
  public void saveEventVotes(long eventId, List<Long> dateOptionVotes, long identityId) throws ObjectNotFoundException,
                                                                                        IllegalAccessException {
    Event event = eventStorage.getEventById(eventId);
    if (event == null) {
      throw new ObjectNotFoundException("Event with id " + eventId + " wasn't found");
    }

    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      throw new ObjectNotFoundException("Identity with id " + identityId + " wasn't found");
    }

    if (!eventAttendeeService.isEventAttendee(eventId, identityId)) {
      throw new IllegalAccessException("User with identity id " + identityId + " isn't attendee of event with id " + eventId);
    }

    if (dateOptionVotes == null) {
      dateOptionVotes = Collections.emptyList();
    }

    List<EventDateOption> eventDateOptions = datePollStorage.getEventDateOptions(eventId);
    for (EventDateOption eventDateOption : eventDateOptions) {
      long dateOptionId = eventDateOption.getId();
      if (dateOptionVotes.contains(dateOptionId)) {
        datePollStorage.vote(dateOptionId, identityId);
      } else {
        datePollStorage.dismiss(dateOptionId, identityId);
      }
    }

    Utils.broadcastEvent(listenerService, Utils.POST_VOTES_AGENDA_EVENT_POLL, eventId, identityId);
  }

  @Override
  public void voteDateOption(long dateOptionId, long identityId) throws ObjectNotFoundException,
                                                                 IllegalAccessException {
    EventDateOption dateOption = datePollStorage.getDateOption(dateOptionId, false, false);
    if (dateOption == null) {
      throw new ObjectNotFoundException("EventDateOption with id " + dateOptionId + " wasn't found");
    }

    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      throw new ObjectNotFoundException("Identity with id " + identityId + " wasn't found");
    }

    long eventId = dateOption.getEventId();
    if (!eventAttendeeService.isEventAttendee(eventId, identityId)) {
      throw new IllegalAccessException("User with identity id " + identityId + " isn't attendee of event with id " + eventId);
    }

    datePollStorage.vote(dateOptionId, identityId);

    Utils.broadcastEvent(listenerService, Utils.POST_VOTE_AGENDA_EVENT_POLL, dateOptionId, identityId);
  }

  @Override
  public void dismissDateOption(long dateOptionId, long identityId) throws ObjectNotFoundException {
    EventDateOption dateOption = datePollStorage.getDateOption(dateOptionId, false, false);
    if (dateOption == null) {
      throw new ObjectNotFoundException("EventDateOption with id " + dateOptionId + " wasn't found");
    }

    Identity userIdentity = identityManager.getIdentity(String.valueOf(identityId));
    if (userIdentity == null) {
      throw new ObjectNotFoundException("Identity with id " + identityId + " wasn't found");
    }

    datePollStorage.dismiss(dateOptionId, identityId);

    Utils.broadcastEvent(listenerService, Utils.POST_DISMISS_AGENDA_EVENT_POLL, dateOptionId, identityId);
  }

  @Override
  public void selectEventDateOption(long dateOptionId) throws ObjectNotFoundException {
    datePollStorage.selectDateOption(dateOptionId);
  }

  private List<EventDateOption> getDateOptionsToCreate(List<EventDateOption> dateOptions) {
    return dateOptions.stream()
                      .filter(dateOption -> dateOption.getId() == 0)
                      .collect(Collectors.toList());
  }

  private List<EventDateOption> getDateOptionsToUpdate(List<EventDateOption> dateOptions,
                                                       List<EventDateOption> existingDateOptions) {
    return dateOptions.stream()
                      .filter(dateOption -> {
                        if (dateOption.getId() <= 0) {
                          return false;
                        }
                        EventDateOption existingDateOption =
                                                           existingDateOptions.stream()
                                                                              .filter(tmp -> tmp.getId() == dateOption.getId())
                                                                              .findAny()
                                                                              .orElse(null);
                        return this.sameDateOption(existingDateOption, dateOption);
                      })
                      .collect(Collectors.toList());
  }

  private List<EventDateOption> getDateOptionsToDelete(List<EventDateOption> dateOptions,
                                                       List<EventDateOption> existingDateOptions) {
    return existingDateOptions.stream()
                              .filter(existingDateOption -> {
                                EventDateOption dateOption =
                                                           dateOptions.stream()
                                                                      .filter(tmp -> tmp.getId() == existingDateOption.getId())
                                                                      .findAny()
                                                                      .orElse(null);
                                return dateOption == null;
                              })
                              .collect(Collectors.toList());
  }

  private void transformDatesTimeZone(EventDateOption dateOption, ZoneId userTimeZone) {
    ZonedDateTime start = dateOption.getStart();
    ZonedDateTime end = dateOption.getEnd();

    ZoneId timeZone = userTimeZone == null ? ZoneOffset.UTC : userTimeZone;

    if (dateOption.isAllDay()) {
      start = start.withZoneSameLocal(ZoneOffset.UTC)
                   .toLocalDate()
                   .atStartOfDay(timeZone);
      end = end.withZoneSameLocal(ZoneOffset.UTC)
               .toLocalDate()
               .atStartOfDay(timeZone)
               .plusDays(1)
               .minusSeconds(1);
    } else {
      start = start.withZoneSameInstant(timeZone);
      end = end.withZoneSameInstant(timeZone);
    }
    dateOption.setStart(start);
    dateOption.setEnd(end);
  }

  private boolean sameDateOption(EventDateOption dateOption1, EventDateOption dateOption2) {
    return dateOption1.isAllDay() == dateOption2.isAllDay()
        && dateOption1.getStart()
                      .withZoneSameInstant(ZoneOffset.UTC)
                      .isEqual(dateOption2
                                          .getStart()
                                          .withZoneSameInstant(ZoneOffset.UTC))
        && dateOption1.getEnd()
                      .withZoneSameInstant(ZoneOffset.UTC)
                      .isEqual(dateOption2
                                          .getEnd()
                                          .withZoneSameInstant(ZoneOffset.UTC));
  }
}
