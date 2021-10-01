// Copyright (C) 2020 eXo Platform SAS.
// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

package org.exoplatform.agenda.service;

import static org.junit.Assert.*;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventConference;

public class AgendaEventConferenceServiceTest extends BaseAgendaEventTest {

  @Test
  public void testSaveEventConferences() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = event.getId();
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);

    assertNotNull(eventConferences);
    assertEquals(1, eventConferences.size());

    EventConference eventConferenceToStore = CONFERENCES.get(0);

    EventConference eventConference = eventConferences.get(0);
    assertNotNull(eventConference);
    assertTrue(eventConference.getId() > 0);
    assertEquals(eventConferenceToStore.getAccessCode(), eventConference.getAccessCode());
    assertEquals(eventConferenceToStore.getDescription(), eventConference.getDescription());
    assertEquals(eventConferenceToStore.getEventId(), eventConference.getEventId());
    assertEquals(eventConferenceToStore.getPhone(), eventConference.getPhone());
    assertEquals(eventConferenceToStore.getType(), eventConference.getType());
    assertEquals(eventConferenceToStore.getUrl(), eventConference.getUrl());

    eventConference = new EventConference(0, 0, "type", "uri", "phone", "accessCode", "description");
    eventConferences.add(eventConference);

    agendaEventConferenceService.saveEventConferences(eventId, eventConferences);
    eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    assertEquals(2, eventConferences.size());

    agendaEventConferenceService.saveEventConferences(eventId, Collections.emptyList());
    eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    assertEquals(0, eventConferences.size());
  }

  @Test
  public void testGetEventConferences() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    long eventId = event.getId();
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);

    assertNotNull(eventConferences);
    assertEquals(1, eventConferences.size());

    EventConference eventConferenceToStore = CONFERENCES.get(0);

    EventConference eventConference = eventConferences.get(0);
    assertNotNull(eventConference);
    assertTrue(eventConference.getId() > 0);
    assertEquals(eventConferenceToStore.getAccessCode(), eventConference.getAccessCode());
    assertEquals(eventConferenceToStore.getDescription(), eventConference.getDescription());
    assertEquals(eventConferenceToStore.getEventId(), eventConference.getEventId());
    assertEquals(eventConferenceToStore.getPhone(), eventConference.getPhone());
    assertEquals(eventConferenceToStore.getType(), eventConference.getType());
    assertEquals(eventConferenceToStore.getUrl(), eventConference.getUrl());
  }

}
