/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
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
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);

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
    assertEquals(eventConferenceToStore.getUri(), eventConference.getUri());

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
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), creatorUserName, testuser2Identity);

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
    assertEquals(eventConferenceToStore.getUri(), eventConference.getUri());
  }

}
