package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.junit.Assert.*;

public class AgendaEventGamificationIntegrationListenerTest extends BaseAgendaEventTest {

  @Test
  public void testAddGamificationPointsAfterCreatingAnEvent() throws Exception { // NOSONAR
    org.exoplatform.agenda.model.Event event = new org.exoplatform.agenda.model.Event();
    event.setCalendarId(spaceCalendar.getId());
    event.setStart(ZonedDateTime.now());
    event.setEnd(ZonedDateTime.now());

    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_CREATE_AGENDA_EVENT_EVENT, new Listener<Long, Object>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<Long, Object> event) throws Exception {
        executeListener.set(true);
      }
    });

    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    agendaEventService.createEvent(event,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   true,
                                   user1IdentityId);

    assertTrue(executeListener.get());
  }

}
