// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.*;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.services.listener.Event;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.log.ExoLogger;
import org.exoplatform.services.log.Log;

/**
 * A listener that will be triggered synchronously after saving (create OR
 * update) an event to change automatically response of users
 */
public class AgendaReplyOnSaveListener extends Listener<AgendaEventModification, Object> {

  private static final Log           LOG = ExoLogger.getExoLogger(AgendaReplyOnSaveListener.class);

  private final PortalContainer      container;

  private AgendaEventService         agendaEventService;

  private AgendaEventDatePollService agendaEventDatePollService;

  private AgendaEventAttendeeService agendaEventAttendeeService;

  public AgendaReplyOnSaveListener(PortalContainer container) {
    this.container = container;
  }

  @Override
  public void onEvent(Event<AgendaEventModification, Object> event) throws Exception {
    AgendaEventModification eventModification = event.getSource();
    long eventId = eventModification.getEventId();
    long modifierId = eventModification.getModifierId();
    try {
      org.exoplatform.agenda.model.Event agendaEvent = getAgendaEventService().getEventById(eventId);
      List<EventAttendee> eventAttendees = getAgendaEventAttendeeService().getEventAttendees(eventId).getEventAttendees();
      switch (agendaEvent.getStatus()) {
        case CONFIRMED:
          // Check if dates modification is made
          if (eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED)
              || eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED)
              || eventModification.hasModification(AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT)
              || eventModification.hasModification(AgendaEventModificationType.ADDED)) {
            // Automatically change creator response to accepted
            // and for others to NEEDS_ACTION
            Set<Long> identityIds = new HashSet<>();
            boolean updateModifierResponse = eventModification.hasModification(AgendaEventModificationType.ADDED);
            boolean isModifierAttendee = false;
            for (EventAttendee eventAttendee : eventAttendees) {
              if (modifierId == eventAttendee.getIdentityId()) {
                isModifierAttendee = true;
                if (eventAttendee.getResponse() != EventAttendeeResponse.ACCEPTED) {
                  updateModifierResponse = true;
                }
              } else if (modifierId != eventAttendee.getIdentityId()
                  && eventAttendee.getResponse() != EventAttendeeResponse.NEEDS_ACTION) {
                identityIds.add(eventAttendee.getIdentityId());
              }
            }
            for (Long identityId : identityIds) {
              getAgendaEventAttendeeService().sendEventResponse(eventId,
                                                                identityId,
                                                                EventAttendeeResponse.NEEDS_ACTION,
                                                                false);
            }
            if (isModifierAttendee && updateModifierResponse) {
              getAgendaEventAttendeeService().sendEventResponse(eventId,
                                                                modifierId,
                                                                EventAttendeeResponse.ACCEPTED,
                                                                false);
            }
          }
          break;
        case TENTATIVE:
          // Check if dates modification is made
          if (eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED)
              || eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED)
              || eventModification.hasModification(AgendaEventModificationType.DATE_OPTION_CREATED)
              || eventModification.hasModification(AgendaEventModificationType.DATE_OPTION_DELETED)
              || eventModification.hasModification(AgendaEventModificationType.DATE_OPTION_UPDATED)
              || eventModification.hasModification(AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL)
              || eventModification.hasModification(AgendaEventModificationType.ADDED)) {

            if (!eventModification.hasModification(AgendaEventModificationType.ADDED)) {
              getAgendaEventDatePollService().resetEventVotes(eventId);
            }

            if (getAgendaEventAttendeeService().isEventAttendee(eventId, modifierId)) {
              // Automatically change creator response to accepted for all
              // proposed Date Polls and for other attendees to NEEDS_ACTION
              List<EventDateOption> eventDateOptions =
                                                     getAgendaEventDatePollService().getEventDateOptions(eventId, ZoneOffset.UTC);
              List<Long> acceptedDatePollIds = eventDateOptions.stream().map(EventDateOption::getId).collect(Collectors.toList());
              getAgendaEventDatePollService().saveEventVotes(eventId, acceptedDatePollIds, modifierId);
            }
            if (!eventModification.hasModification(AgendaEventModificationType.ADDED)) {
              Set<Long> identityIds = new HashSet<>();
              for (EventAttendee eventAttendee : eventAttendees) {
                if (modifierId != eventAttendee.getIdentityId()
                    && eventAttendee.getResponse() != EventAttendeeResponse.NEEDS_ACTION) {
                  identityIds.add(eventAttendee.getIdentityId());
                }
              }
              for (Long identityId : identityIds) {
                getAgendaEventAttendeeService().sendEventResponse(eventId,
                                                                  identityId,
                                                                  EventAttendeeResponse.NEEDS_ACTION,
                                                                  false);
              }
            }
          }
          break;
        default:
          break;
      }
    } catch (Exception e) {
      LOG.warn("An error occurred while changing event '{}' response of user '{}'", eventId, modifierId, e);
    }
  }

  private AgendaEventService getAgendaEventService() {
    if (agendaEventService == null) {
      agendaEventService = container.getComponentInstanceOfType(AgendaEventService.class);
    }
    return agendaEventService;
  }

  private AgendaEventDatePollService getAgendaEventDatePollService() {
    if (agendaEventDatePollService == null) {
      agendaEventDatePollService = container.getComponentInstanceOfType(AgendaEventDatePollService.class);
    }
    return agendaEventDatePollService;
  }

  private AgendaEventAttendeeService getAgendaEventAttendeeService() {
    if (agendaEventAttendeeService == null) {
      agendaEventAttendeeService = container.getComponentInstanceOfType(AgendaEventAttendeeService.class);
    }
    return agendaEventAttendeeService;
  }
}
