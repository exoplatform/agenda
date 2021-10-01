// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import static org.junit.Assert.assertTrue;

import java.time.ZonedDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;

public class AgendaEventAttendeeGamificationIntegrationListenerTest extends BaseAgendaEventTest {

  @Test
  public void testAddGamificationPointsAfterReplyingToAnEvent() throws Exception { // NOSONAR
    org.exoplatform.agenda.model.Event event = new org.exoplatform.agenda.model.Event();
    event.setCalendarId(spaceCalendar.getId());
    event.setStart(ZonedDateTime.now());
    event.setEnd(ZonedDateTime.now());

    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_EVENT_RESPONSE_SENT, new Listener<Long, Object>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<Long, Object> event) throws Exception {
        executeListener.set(true);
      }
    });

    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    org.exoplatform.agenda.model.Event createdEvent = createEvent(event, user1IdentityId, testuser1Identity);
    EventAttendeeResponse userResponse = EventAttendeeResponse.fromValue("ACCEPTED");
    agendaEventAttendeeService.sendEventResponse(createdEvent.getId(), user1IdentityId, userResponse);

    assertTrue(executeListener.get());
  }

}
