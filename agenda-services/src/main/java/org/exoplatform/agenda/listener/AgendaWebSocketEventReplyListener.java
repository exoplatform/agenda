package org.exoplatform.agenda.listener;

import java.util.HashSet;
import java.util.Set;

import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeePromotion;
import org.exoplatform.agenda.model.WebSocketMessage;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.AgendaWebSocketService;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.*;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;

/**
 * A listener that will be triggered synchronously after saving user reply to an
 * event to transmit modifications to the end user himself via WebSocket. This
 * will allow to update listed events when the response changed switch filters
 * used to display event in UI. It also tells an attendee promoted from the
 * waiting list, whose event data is the promotion, and the event's creator,
 * whose page then shows the seat given.
 */
@Asynchronous
public class AgendaWebSocketEventReplyListener extends Listener<EventAttendee, Object> {

  private PortalContainer        container;

  private AgendaEventService     agendaEventService;

  private AgendaWebSocketService agendaWebSocketService;

  private IdentityManager        identityManager;

  public AgendaWebSocketEventReplyListener(PortalContainer container,
                                           AgendaEventService agendaEventService,
                                           IdentityManager identityManager,
                                           AgendaWebSocketService agendaWebSocketService) {
    this.container = container;
    this.agendaEventService = agendaEventService;
    this.agendaWebSocketService = agendaWebSocketService;
    this.identityManager = identityManager;
  }

  @Override
  public void onEvent(Event<EventAttendee, Object> event) throws Exception {
    RequestLifeCycle.begin(container);
    try {
      EventAttendeePromotion promotion = event.getData() instanceof EventAttendeePromotion eventPromotion ? eventPromotion
                                                                                                          : null;
      EventAttendee newAttendee = promotion == null ? (EventAttendee) event.getData() : promotion.attendee();
      long eventId = newAttendee.getEventId();
      long userIdentityId = newAttendee.getIdentityId();

      org.exoplatform.agenda.model.Event agendaEvent = agendaEventService.getEventById(eventId);
      long calendarId = agendaEvent.getCalendarId();
      if (promotion != null && agendaEvent.getParentId() > 0 && (promotion.otherDates() > 0 || promotion.openEnded())) {
        // A promotion from a date the series stores that gave other dates too
        // names the series, which the page reloads whole: the date would
        // refresh that date only
        eventId = agendaEvent.getParentId();
      }

      WebSocketMessage socketMessage = new WebSocketMessage(event.getEventName());
      socketMessage.addParam("eventId", eventId);
      socketMessage.addParam("calendarId", calendarId);

      Set<String> recipients = new HashSet<>();
      recipients.add(identityManager.getIdentity(userIdentityId).getRemoteId());
      if (promotion != null) {
        // The organiser's page shows the participant list, which a seat freed
        // elsewhere has just changed (EXO-91879): it reads it again
        Identity creator = identityManager.getIdentity(agendaEvent.getCreatorId());
        if (creator != null) {
          recipients.add(creator.getRemoteId());
        }
      }
      agendaWebSocketService.sendMessage(socketMessage, recipients);
    } finally {
      RequestLifeCycle.end();
    }
  }

}
