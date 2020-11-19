package org.exoplatform.agenda.job;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.util.AgendaDateUtils;
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
    EventFilter eventFilter = new EventFilter(0, ownerIds, attendeeResponses, start, end, null, userTimeZone, 0);
    try {
      List<Event> events = eventService.getEvents(eventFilter);
      List<Event> simpleEvents = new ArrayList<>();
      for (Event event : events) {
        if (event.getParentId() == 0) {
          simpleEvents.add(event);
        }
      }
      List<EventReminder> eventReminders = new ArrayList<>();
      for (Event simpleEvent : simpleEvents) {
        eventReminders = agendaEventReminderService.getEventReminders(simpleEvent.getId(), simpleEvent.getCreatorId());
      }
      for (EventReminder eventReminder : eventReminders) {
        if (eventReminder.getDatetime() == now) {
          LOG.info("/********************** List events ******************************/", events.toString());
        }
      }
    } catch (IllegalAccessException e) {
      LOG.error("/********************** No events ******************************/", e);
    }
  }
}
