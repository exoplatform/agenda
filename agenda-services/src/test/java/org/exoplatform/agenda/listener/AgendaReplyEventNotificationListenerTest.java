package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.Mockito.*;

public class AgendaReplyEventNotificationListenerTest extends BaseAgendaEventTest {
  AgendaEventReplyListener agendaEventReplyListener = Mockito.mock(AgendaEventReplyListener.class);

  @Test
  public void testSendNotificationWhenReplyToEvent() throws Exception {

    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity);

    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_EVENT_RESPONSE_SENT, new Listener<EventAttendee, EventAttendee>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<EventAttendee, EventAttendee> event) throws Exception {
        if (executeListener.get()) {
          agendaEventReplyListener.onEvent(event);
        }
      }
    });
    executeListener.set(true);
    try {
      agendaEventAttendeeService.sendEventResponse(event.getId(),
                                                   Long.parseLong(testuser4Identity.getId()),
                                                   EventAttendeeResponse.ACCEPTED);
      Mockito.doCallRealMethod()
             .when(agendaEventReplyListener)
             .sendReplyResponseNotification(event, Long.parseLong(testuser4Identity.getId()), EventAttendeeResponse.ACCEPTED);
      // send invitation when user change his response
      verify(agendaEventReplyListener, times(1)).sendReplyResponseNotification(event,
                                                                               Long.parseLong(testuser4Identity.getId()),
                                                                               EventAttendeeResponse.ACCEPTED);

    } finally {
      executeListener.set(false);
    }

    executeListener.set(true);
    try {
      agendaEventAttendeeService.sendEventResponse(event.getId(),
                                                   Long.parseLong(testuser4Identity.getId()),
                                                   EventAttendeeResponse.NEEDS_ACTION);
      Mockito.doCallRealMethod()
             .when(agendaEventReplyListener)
             .sendReplyResponseNotification(event, Long.parseLong(testuser4Identity.getId()), EventAttendeeResponse.NEEDS_ACTION);
      // don't send invitation when user send the same response twice
      verify(agendaEventReplyListener, never()).sendReplyResponseNotification(event,
                                                                              Long.parseLong(testuser4Identity.getId()),
                                                                              EventAttendeeResponse.NEEDS_ACTION);
    } finally {
      executeListener.set(false);
    }
  }
}
