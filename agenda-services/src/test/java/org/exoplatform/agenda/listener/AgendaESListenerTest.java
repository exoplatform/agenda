/*
 * Copyright (C) 2026 eXo Platform SAS.
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
package org.exoplatform.agenda.listener;

import static org.mockito.Mockito.*;

import java.time.ZonedDateTime;

import org.junit.Test;

import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.search.AgendaIndexingServiceConnector;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.search.index.IndexingService;

/**
 * Verifies that {@link AgendaESListener} keeps the Elasticsearch index — whose
 * mapping indexes {@code calendarId} — in sync with calendar-membership
 * changes: a moved event must be reindexed, since its document would
 * otherwise keep referencing the deleted source calendar.
 */
public class AgendaESListenerTest extends BaseAgendaEventTest {

  /**
   * An {@code exo.agenda.event.moved} broadcast must reindex exactly the
   * moved event.
   *
   * @throws Exception when the listener or a service call fails unexpectedly
   */
  @Test
  public void testMovedEventIsReindexed() throws Exception { // NOSONAR
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    ZonedDateTime start = getDate();
    org.exoplatform.agenda.model.Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event.setCalendarId(calendar.getId());
    org.exoplatform.agenda.model.Event createdEvent = createEvent(event.clone(), userIdentityId, testuser1Identity);

    IndexingService indexingService = mock(IndexingService.class);
    AgendaESListener agendaESListener = new AgendaESListener(container, indexingService);

    AgendaEventModification eventModification = new AgendaEventModification(createdEvent.getId(),
                                                                            calendar.getId(),
                                                                            userIdentityId);
    agendaESListener.onEvent(new org.exoplatform.services.listener.Event<>(Utils.POST_MOVE_AGENDA_EVENT_EVENT,
                                                                           eventModification,
                                                                           null));

    verify(indexingService, times(1)).reindex(AgendaIndexingServiceConnector.INDEX, String.valueOf(createdEvent.getId()));
    verify(indexingService, never()).unindex(anyString(), anyString());
  }

  /**
   * A move broadcast for an event that no longer exists must not touch the
   * index (the listener re-reads the event before indexing).
   *
   * @throws Exception when the listener fails unexpectedly
   */
  @Test
  public void testMovedUnknownEventIsNotReindexed() throws Exception { // NOSONAR
    IndexingService indexingService = mock(IndexingService.class);
    AgendaESListener agendaESListener = new AgendaESListener(container, indexingService);

    AgendaEventModification eventModification = new AgendaEventModification(987654321L, calendar.getId(), 1L);
    agendaESListener.onEvent(new org.exoplatform.services.listener.Event<>(Utils.POST_MOVE_AGENDA_EVENT_EVENT,
                                                                           eventModification,
                                                                           null));

    verify(indexingService, never()).reindex(anyString(), anyString());
    verify(indexingService, never()).unindex(anyString(), anyString());
  }
}
