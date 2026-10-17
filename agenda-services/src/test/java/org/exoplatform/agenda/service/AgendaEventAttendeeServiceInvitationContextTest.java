/**
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.exoplatform.agenda.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.AgendaEventModification;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;

/**
 * Pins what an invitation hands its notification: the newly invited
 * attendees, and the users who already attend, for the notification to leave
 * them out.
 */
class AgendaEventAttendeeServiceInvitationContextTest {

  /**
   * An invitation with already-invited users puts them in the notification
   * context beside the invited attendees; one without them, as every other
   * caller sends, puts none.
   * Mutants: the already-invited users not put in the context, or the
   * three-argument call passing some.
   */
  @Test
  void theAlreadyInvitedUsersTravelWithTheInvitation() {
    AgendaEventAttendeeServiceImpl service = new AgendaEventAttendeeServiceImpl(null, null, null, null, null, null, null);
    Event event = new Event();
    event.setId(42L);
    List<EventAttendee> invited = List.of(new EventAttendee(0L, 300L, EventAttendeeResponse.NEEDS_ACTION));
    AgendaEventModification modification = new AgendaEventModification(42L, 5L, 7L);
    modification.addModificationType(AgendaEventModificationType.ADDED);
    NotificationContext ctx = mock(NotificationContext.class);
    when(ctx.append(any(), any())).thenReturn(ctx);
    NotificationContext plainCtx = mock(NotificationContext.class);
    when(plainCtx.append(any(), any())).thenReturn(plainCtx);
    try (MockedStatic<NotificationContextImpl> contexts = mockStatic(NotificationContextImpl.class)) {
      contexts.when(NotificationContextImpl::cloneInstance).thenReturn(ctx, plainCtx);

      service.sendInvitations(event, invited, Set.of("mary"), modification);
      service.sendInvitations(event, invited, modification);
    }

    verify(ctx).append(NotificationUtils.EVENT_ATTENDEE, invited);
    verify(ctx).append(NotificationUtils.EVENT_ALREADY_INVITED, Set.of("mary"));
    verify(plainCtx).append(NotificationUtils.EVENT_ATTENDEE, invited);
    verify(plainCtx, never()).append(eq(NotificationUtils.EVENT_ALREADY_INVITED), any());
  }

}
