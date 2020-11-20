package org.exoplatform.agenda.job;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class WebReminderJob implements Job {
  private static final Log           LOG = ExoLogger.getLogger(WebReminderJob.class);

  private AgendaEventReminderService agendaEventReminderService;

  private AgendaEventService         eventService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    agendaEventReminderService = CommonsUtils.getService(AgendaEventReminderService.class);
    eventService = CommonsUtils.getService(AgendaEventService.class);
    LOG.info("/********************** job invoked ******************************/");
    ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
    ZonedDateTime start = ZonedDateTime.of(now.getYear(),
                                           now.getMonthValue(),
                                           now.getDayOfMonth(),
                                           now.getHour(),
                                           now.getMinute(),
                                           0,
                                           0,
                                           ZoneOffset.UTC);
    long milliToNano = 1000000l;
    ZonedDateTime end = start.plusMinutes(1).minusNanos(milliToNano);
    List<Long> ownerIds = new ArrayList<>();
    List<EventAttendeeResponse> attendeeResponses = new ArrayList<>();
    attendeeResponses.add(EventAttendeeResponse.TENTATIVE);
    attendeeResponses.add(EventAttendeeResponse.ACCEPTED);
    ZoneId userTimeZone = start.getZone();
    List<Event> events = new ArrayList<>();
    List<EventReminder> eventReminders = new ArrayList<>();
    EventFilter eventFilter = new EventFilter(0, ownerIds, attendeeResponses, start, end, 0);
    try {
      events = eventService.getEvents(eventFilter, null, userTimeZone);
    } catch (IllegalAccessException e) {
      LOG.error("/********************** No events ******************************/", e);
    }
    List<Event> simpleEvents =new ArrayList<>();
    if(events == null || events.isEmpty()) {
      return;
    } else {
      simpleEvents = filterEvents(events);
    }
    if (simpleEvents == null || simpleEvents.isEmpty()) {
      return ;
    } else {
      for (Event simpleEvent : simpleEvents) {
        eventReminders = agendaEventReminderService.getEventReminders(simpleEvent.getId(), simpleEvent.getCreatorId());
        LOG.info("/********************** Remainder events ******************************/");
      }
    }

    if (eventReminders == null || eventReminders.isEmpty()) {
      return;
    } else {
      ZonedDateTime currentTime = ZonedDateTime.now(ZoneOffset.UTC);
      ZonedDateTime current = ZonedDateTime.of(currentTime.getYear(),
              currentTime.getMonthValue(),
              currentTime.getDayOfMonth(),
              currentTime.getHour(),
              currentTime.getMinute(),
              0,
              0,
              ZoneOffset.UTC);
      for (EventReminder eventReminder : eventReminders) {
        if (isTimeToRemind(eventReminder.getDatetime(), current)) {
          LOG.info("/********************** notification ******************************/", events.toString());
        }
      }
    }
  }
  private List<Event> filterEvents(List<Event> events) {
    List<Event> eventsFiltered = new ArrayList<>();
    if (events == null && events.isEmpty()) {
      return null;
    } else {
      for (Event event : events) {
        if (event.getParentId() == 0) {
          eventsFiltered.add(event);
        } else {
          eventsFiltered.add(event);
        }
      }
    }
    return eventsFiltered;
  }
  private boolean isTimeToRemind(ZonedDateTime eventReminder, ZonedDateTime now) {
    long delta = 15000;
    long currentTime = eventReminder.toInstant().toEpochMilli();
    long remindTime = now.toInstant().toEpochMilli();
    long diff = currentTime - remindTime;
    return diff <= delta && currentTime >= remindTime;
  }
}
