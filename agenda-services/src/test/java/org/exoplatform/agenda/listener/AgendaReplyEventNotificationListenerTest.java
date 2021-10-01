// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import static org.junit.Assert.assertEquals;

import java.time.ZonedDateTime;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.commons.api.notification.service.WebNotificationService;

public class AgendaReplyEventNotificationListenerTest extends BaseAgendaEventTest {

  @Test
  public void testSendNotificationWhenReplyToEvent() throws Exception {

    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity);

    long eventParticipantId = Long.parseLong(testuser4Identity.getId());

    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int initialNotificationsSize = webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId());

    EventAttendee oldAttendeeResponse =
                                      new EventAttendee(0, event.getId(), eventParticipantId, EventAttendeeResponse.NEEDS_ACTION);
    EventAttendee newAttendeeResponse = new EventAttendee(0, event.getId(), eventParticipantId, EventAttendeeResponse.ACCEPTED);

    AgendaEventReplyListener agendaEventReplyListener = new AgendaEventReplyListener(container);
    agendaEventReplyListener.onEvent(new org.exoplatform.services.listener.Event<EventAttendee, EventAttendee>(null,
                                                                                                               oldAttendeeResponse,
                                                                                                               newAttendeeResponse));

    assertEquals(initialNotificationsSize + 1l, webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId()));
  }
}
