// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.listener;

import static org.junit.Assert.*;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Test;

import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.Listener;

public class AgendaEventReminderCleanerListenerTest extends BaseAgendaEventTest {

  @Test
  public void testCleanEventReminders() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = false;

    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), userIdentityId, testuser4Identity, testuser5Identity);

    long eventId = event.getId();
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertNotNull(eventReminders);
    assertEquals(1, eventReminders.size());

    AtomicBoolean executeListener = new AtomicBoolean(true);
    listenerService.addListener(Utils.POST_UPDATE_AGENDA_EVENT_EVENT, new Listener<AgendaEventModification, Object>() {
      @Override
      public void onEvent(org.exoplatform.services.listener.Event<AgendaEventModification, Object> event) throws Exception {
        if (executeListener.get()) {
          new AgendaEventReminderCleanerListener().onEvent(event);
        }
      }
    });

    try {
      event.setStatus(EventStatus.CANCELLED);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     eventReminders,
                                     Collections.emptyList(),
                                     null,
                                     false,
                                     userIdentityId);
    } finally {
      executeListener.set(false);
    }
    eventReminders = agendaEventReminderService.getEventReminders(eventId);
    assertNotNull(eventReminders);
    assertTrue(eventReminders.isEmpty());
  }

}
