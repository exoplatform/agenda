// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;

public class AgendaEventReminderComputingListenerTest extends BaseAgendaEventTest {

  @Test
  public void testComputeEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = getDate();

    boolean allDay = false;

    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_CREATE_AGENDA_EVENT_EVENT, new Listener<AgendaEventModification, Object>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<AgendaEventModification, Object> event) throws Exception {
        if (executeListener.get()) {
          new AgendaEventReminderComputingListener().onEvent(event);
        }
      }
    });

    Event event = newEventInstance(start, start, allDay);
    try {
      event = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity);
    } finally {
      executeListener.set(false);
    }

    List<Event> events = agendaEventService.getEventOccurrencesInPeriod(event,
                                                                        start,
                                                                        start.plusDays(2),
                                                                        ZoneId.systemDefault(),
                                                                        0);
    assertNotNull(events);
    assertEquals(0, events.size());
  }

}
