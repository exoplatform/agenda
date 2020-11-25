package org.exoplatform.agenda.job;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventFilter;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventReminderService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.commons.utils.CommonsUtils;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;
import org.quartz.*;

import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;


public class ComputingReminderJob implements Job {
  private static final Log   LOG = ExoLogger.getLogger(ComputingReminderJob.class);

  private AgendaEventService eventService;

  private AgendaEventReminderService eventReminderService;
  
  private AgendaEventAttendeeService eventAttendeeService;

  @Override
  public void execute(JobExecutionContext context) throws JobExecutionException {
    eventService = CommonsUtils.getService(AgendaEventService.class);
    eventReminderService = CommonsUtils.getService(AgendaEventReminderService.class);
    eventAttendeeService = CommonsUtils.getService(AgendaEventAttendeeService.class);
    try {
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
      ZonedDateTime end = start.plusDays(1).minusNanos(milliToNano);
      ZoneId userTimeZone = start.getZone();
      List<Long> ownerIds = new ArrayList<>();
      List<EventAttendeeResponse> attendeeResponses = new ArrayList<>();
      List<Event> events = new ArrayList<>();
      List<EventAttendee> eventAttendees = new ArrayList<>();
      attendeeResponses.add(EventAttendeeResponse.TENTATIVE);
      attendeeResponses.add(EventAttendeeResponse.ACCEPTED);
      EventFilter eventFilter = new EventFilter(0, ownerIds, attendeeResponses, start, end, 0);
      events = eventService.getEvents(eventFilter, null, userTimeZone);
      for (Event event : events) {
        eventAttendees = eventAttendeeService.getEventAttendees(event.getId());
        boolean hasAttendees = eventAttendees != null && !eventAttendees.isEmpty();
        if (hasAttendees) {
          for (EventAttendee eventAttendee : eventAttendees) {
            eventReminderService.computeUpdatedTriggerDate(event, eventAttendee.getIdentityId());
          }
        }
      }
    } catch (IllegalAccessException e) {
      LOG.error("User '{}' attempts to access not authorized events of owner Id '{}'", e);
    } catch (AgendaException e) {
      LOG.error("Error retrieving list of events", e);
    }
  }
}
