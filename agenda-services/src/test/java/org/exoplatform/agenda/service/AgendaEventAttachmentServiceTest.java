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
import org.exoplatform.agenda.model.EventAttachment;

public class AgendaEventAttachmentServiceTest extends BaseAgendaEventTest {

  @Test
  public void testSaveEventAttachments() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = event.getId();
    List<EventAttachment> eventAttachments = agendaEventAttachmentService.getEventAttachments(eventId);
    assertNotNull(eventAttachments);
    assertEquals(1, eventAttachments.size());

    EventAttachment eventAttachmentToStore = ATTACHMENTS.get(0);

    EventAttachment eventAttachment = eventAttachments.get(0);
    assertNotNull(eventAttachment);
    assertTrue(eventAttachment.getId() > 0);
    assertEquals(eventId, eventAttachment.getEventId());
    assertEquals(eventAttachmentToStore.getFileId(), eventAttachment.getFileId());

    eventAttachment = eventAttachment.clone();
    eventAttachment.setId(0);
    eventAttachments.add(eventAttachment);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    agendaEventAttachmentService.saveEventAttachments(eventId, eventAttachments, creatorId);

    eventAttachments = agendaEventAttachmentService.getEventAttachments(eventId);
    assertNotNull(eventAttachments);
    assertEquals(1, eventAttachments.size());

    eventAttachment = eventAttachment.clone();
    eventAttachment.setId(0);
    eventAttachment.setFileId("500");
    eventAttachments.add(eventAttachment);
    agendaEventAttachmentService.saveEventAttachments(eventId, eventAttachments, creatorId);

    eventAttachments = agendaEventAttachmentService.getEventAttachments(eventId);
    assertNotNull(eventAttachments);
    assertEquals(2, eventAttachments.size());

    agendaEventAttachmentService.saveEventAttachments(eventId, Collections.emptyList(), creatorId);

    eventAttachments = agendaEventAttachmentService.getEventAttachments(eventId);
    assertNotNull(eventAttachments);
    assertEquals(0, eventAttachments.size());
  }

  @Test
  public void testGetEventAttachments() throws Exception { // NOSONAR
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = event.getId();
    List<EventAttachment> eventAttachments = agendaEventAttachmentService.getEventAttachments(eventId);
    assertNotNull(eventAttachments);
    assertEquals(1, eventAttachments.size());

    EventAttachment eventAttachmentToStore = ATTACHMENTS.get(0);

    EventAttachment eventAttachment = eventAttachments.get(0);
    assertNotNull(eventAttachment);
    assertTrue(eventAttachment.getId() > 0);
    assertEquals(eventId, eventAttachment.getEventId());
    assertEquals(eventAttachmentToStore.getFileId(), eventAttachment.getFileId());
  }

}
