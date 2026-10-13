package org.exoplatform.agenda.listener;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;

/**
 * A listener that will be triggered when a new response is sent to an event.
 * This will update user reminders switch his/her default reminders. If the user
 * changed the response on an event from 'Yes' or 'May be' to 'No', then all
 * user reminders on the event has to be deleted automatically. When the user
 * changes his reponse from 'No' or 'Needs action' (default) to 'Yes' or 'May
 * be', then the default user reminders has to be added automatically. A place
 * on the waiting list holds no reminder, unless the user holds a place on
 * another date ahead of the same series. This
 * listener is triggered synchronously to ensure that the Event gets all its
 * updates (including user reminders) before returning the Event entity on REST
 * endpoint in order to display the updated response + added/removed reminders.
 */
public class AgendaEventReminderListener extends Listener<EventAttendee, EventAttendee> {

  private AgendaEventReminderService agendaEventReminderService;

  private AgendaUserSettingsService  agendaUserSettingsService;

  private AgendaEventService         agendaEventService;

  private AgendaEventAttendeeService agendaEventAttendeeService;

  @Override
  public void onEvent(Event<EventAttendee, EventAttendee> event) throws Exception {
    EventAttendee oldAttendee = event.getSource();
    EventAttendee newAttendee = event.getData();
    long eventId = newAttendee.getEventId();
    long identityId = newAttendee.getIdentityId();

    if (isWaiting(oldAttendee) || isWaiting(newAttendee)) {
      // The answer reported is the one at a single date, while the reminders
      // cover the whole event: an answer to a series can wait at that date and
      // hold a seat at later ones. The user keeps reminders while they hold a
      // place on a date ahead, a waiting one excepted.
      boolean holdsAPlace = holdsAPlaceAhead(eventId, identityId);
      boolean hasReminders = !getAgendaEventReminderService().getEventReminders(eventId, identityId).isEmpty();
      if (holdsAPlace && !hasReminders) {
        addUserReminders(eventId, identityId);
      } else if (!holdsAPlace && hasReminders) {
        getAgendaEventReminderService().removeUserReminders(eventId, identityId);
      }
      return;
    }

    boolean oldResponseNoReminders = oldAttendee == null || holdsNoReminders(oldAttendee.getResponse());
    boolean newResponseNoReminders = holdsNoReminders(newAttendee.getResponse());

    if (oldResponseNoReminders != newResponseNoReminders) {
      if (newResponseNoReminders) {
        getAgendaEventReminderService().removeUserReminders(eventId, identityId);
      } else {
        addUserReminders(eventId, identityId);
      }
    }
  }

  private void addUserReminders(long eventId, long identityId) throws IllegalAccessException, AgendaException {
    org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
    AgendaUserSettings agendaUserSettings = getAgendaUserSettingsService().getAgendaUserSettings(identityId);
    List<EventReminderParameter> reminderParameters = agendaUserSettings.getReminders();
    if (reminderParameters != null && !reminderParameters.isEmpty()) {
      List<EventReminder> reminders =
                                    reminderParameters.stream()
                                                      .map(reminderParameter -> new EventReminder(identityId,
                                                                                                  reminderParameter.getBefore(),
                                                                                                  reminderParameter.getBeforePeriodType()))
                                                      .collect(Collectors.toList());
      getAgendaEventReminderService().saveEventReminders(agendaEvent, reminders, identityId);
    }
  }

  /**
   * @param eventId the event, or the series, answered
   * @param identityId the user who answered
   * @return whether the user holds a place that is not on the waiting list on
   *         a date ahead: at the next date of a series or after it; on an
   *         event of no series, on its row
   * @throws ObjectNotFoundException when the event no longer exists
   */
  private boolean holdsAPlaceAhead(long eventId, long identityId) throws ObjectNotFoundException {
    EventAttendee atNextDate = getAgendaEventAttendeeService().getSeriesAttendee(eventId, identityId);
    if (atNextDate == null) {
      return false;
    }
    if (!holdsNoReminders(atNextDate.getResponse())) {
      return true;
    }
    List<EventAttendee> rows = getAgendaEventAttendeeService().getEventAttendees(eventId).getEventAttendees(identityId);
    // The rows after the one valid at the next date start where it ends: one
    // occurrence id compared with another, never with the clock, which would
    // read an all-day id as an instant. With no row valid at that date, read
    // as a copy answering nothing, the user's rows all start after it.
    boolean validAtNextDate = rows.stream()
                                  .anyMatch(row -> row.getId() == atNextDate.getId()
                                      && row.getResponse() == atNextDate.getResponse());
    ZonedDateTime aheadFrom = validAtNextDate ? atNextDate.getUntilOccurrenceId() : null;
    if (validAtNextDate && aheadFrom == null) {
      return false;
    }
    return rows.stream()
               .anyMatch(row -> row.getFromOccurrenceId() != null
                   && (aheadFrom == null || !row.getFromOccurrenceId().isBefore(aheadFrom))
                   && !holdsNoReminders(row.getResponse()));
  }

  private static boolean isWaiting(EventAttendee attendee) {
    return attendee != null && EventAttendeeResponse.WAITING.equals(attendee.getResponse());
  }

  /**
   * @param response an attendee's answer
   * @return whether it holds no reminder: a decline, no answer yet, or a
   *         place on the waiting list, which holds no seat until a promotion
   *         gives one and the reminders with it
   */
  private static boolean holdsNoReminders(EventAttendeeResponse response) {
    return EventAttendeeResponse.DECLINED.equals(response) || EventAttendeeResponse.NEEDS_ACTION.equals(response)
        || EventAttendeeResponse.WAITING.equals(response);
  }

  public AgendaUserSettingsService getAgendaUserSettingsService() {
    if (agendaUserSettingsService == null) {
      agendaUserSettingsService = ExoContainerContext.getService(AgendaUserSettingsService.class);
    }
    return agendaUserSettingsService;
  }

  public AgendaEventReminderService getAgendaEventReminderService() {
    if (agendaEventReminderService == null) {
      agendaEventReminderService = ExoContainerContext.getService(AgendaEventReminderService.class);
    }
    return agendaEventReminderService;
  }

  public AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = ExoContainerContext.getService(AgendaEventService.class);
    }
    return agendaEventService;
  }

  public AgendaEventAttendeeService getAgendaEventAttendeeService() {
    if (agendaEventAttendeeService == null) {
      agendaEventAttendeeService = ExoContainerContext.getService(AgendaEventAttendeeService.class);
    }
    return agendaEventAttendeeService;
  }
}
