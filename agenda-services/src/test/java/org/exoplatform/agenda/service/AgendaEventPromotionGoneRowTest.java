/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.storage.AgendaEventAttendeeStorage;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;
import org.exoplatform.web.security.codec.CodecInitializer;

/**
 * The promotion of a pool's waiting list when the pool's event row is not
 * found. {@code AgendaEventStorage#getEventById} answers null for a row it
 * does not find; no path is known that reaches the promotion with the row
 * gone, so the check is defensive, and the test gives the storage's answer
 * directly. The pin guards the {@code pool == null} return of
 * {@link AgendaEventAttendeeServiceImpl#promoteWaitingAttendees(long)}:
 * without it, the step fails on a null pool and rolls back the decision that
 * freed the seat.
 */
class AgendaEventPromotionGoneRowTest {

  @Test
  void aPoolWhoseRowIsGoneGivesNoSeat() {
    AgendaEventAttendeeStorage attendeeStorage = mock(AgendaEventAttendeeStorage.class);
    AgendaEventStorage eventStorage = mock(AgendaEventStorage.class);
    long eventId = 12L;
    when(attendeeStorage.getWaitingAttendees(eventId)).thenReturn(List.of(new EventAttendee()));
    when(eventStorage.getEventById(eventId)).thenReturn(null);
    AgendaEventAttendeeServiceImpl attendeeService = new AgendaEventAttendeeServiceImpl(attendeeStorage,
                                                                                        eventStorage,
                                                                                        mock(AgendaCalendarService.class),
                                                                                        mock(ListenerService.class),
                                                                                        mock(IdentityManager.class),
                                                                                        mock(SpaceService.class),
                                                                                        mock(CodecInitializer.class));

    assertDoesNotThrow(() -> attendeeService.promoteWaitingAttendees(eventId));
    verify(attendeeStorage).getWaitingAttendees(eventId);
    verifyNoMoreInteractions(attendeeStorage);
  }

}
