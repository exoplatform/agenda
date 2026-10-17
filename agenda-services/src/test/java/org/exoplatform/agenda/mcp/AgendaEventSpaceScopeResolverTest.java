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
package org.exoplatform.agenda.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Pins the space of an agenda event as the space scope sees it: the space that
 * owns the event's calendar, nothing otherwise, and nothing when the lookup
 * fails.
 */
class AgendaEventSpaceScopeResolverTest {

  private static final long             EVENT_ID    = 5L;

  private static final long             CALENDAR_ID = 7L;

  private static final long             OWNER_ID    = 9L;

  private AgendaEventService            agendaEventService;

  private AgendaCalendarService         agendaCalendarService;

  private IdentityManager               identityManager;

  private SpaceService                  spaceService;

  private AgendaEventSpaceScopeResolver resolver;

  /**
   * Gives the resolver an event in a calendar owned by an identity, with the
   * space and the user identities each answering to their kind.
   */
  @BeforeEach
  void setUp() {
    agendaEventService = mock(AgendaEventService.class);
    agendaCalendarService = mock(AgendaCalendarService.class);
    identityManager = mock(IdentityManager.class);
    spaceService = mock(SpaceService.class);
    resolver = new AgendaEventSpaceScopeResolver(agendaEventService, agendaCalendarService, identityManager, spaceService);
    Event event = mock(Event.class);
    when(event.getCalendarId()).thenReturn(CALENDAR_ID);
    when(agendaEventService.getEventById(EVENT_ID)).thenReturn(event);
    Calendar calendar = mock(Calendar.class);
    when(calendar.getOwnerId()).thenReturn(OWNER_ID);
    when(agendaCalendarService.getCalendarById(CALENDAR_ID)).thenReturn(calendar);
  }

  /**
   * The name is the one the agenda tools' declarations give.
   */
  @Test
  void nameIsTheOneTheDeclarationsGive() {
    assertEquals("event", resolver.getName());
  }

  /**
   * An event of a space calendar belongs to that space, whoever it invites:
   * the attendees are never read.
   */
  @Test
  void spaceOfEventOfSpaceCalendar() {
    givenOwner(identity(true, false, "alpha"));
    Space alpha = space("12");
    when(spaceService.getSpaceByPrettyName("alpha")).thenReturn(alpha);

    assertEquals(Long.valueOf(12), resolver.spaceOf("5"));
  }

  /**
   * An event of a personal calendar belongs to no space, whoever it invites,
   * a space included.
   */
  @Test
  void noSpaceForEventOfPersonalCalendar() {
    givenOwner(identity(false, false, "john"));

    assertNull(resolver.spaceOf("5"));
    verifyNoInteractions(spaceService);
  }

  /**
   * A calendar whose owner identity is gone, or deleted, belongs to no space.
   */
  @Test
  void noSpaceWhenOwnerIsGone() {
    assertNull(resolver.spaceOf("5"));
    givenOwner(identity(true, true, "alpha"));

    assertNull(resolver.spaceOf("5"));
    verifyNoInteractions(spaceService);
  }

  /**
   * An owner whose space no longer exists, or has no numeric id, belongs to
   * no space.
   */
  @Test
  void noSpaceWhenSpaceIsGone() {
    givenOwner(identity(true, false, "alpha"));
    assertNull(resolver.spaceOf("5"));

    Space withoutNumericId = space("x");
    when(spaceService.getSpaceByPrettyName("alpha")).thenReturn(withoutNumericId);
    assertNull(resolver.spaceOf("5"));
  }

  /**
   * A missing event, and a calendar that can't be found, belong to no space.
   */
  @Test
  void noSpaceWhenEventOrCalendarIsMissing() {
    assertNull(resolver.spaceOf("6"));

    when(agendaCalendarService.getCalendarById(CALENDAR_ID)).thenReturn(null);
    assertNull(resolver.spaceOf("5"));
  }

  /**
   * An id that isn't decimal digits is answered without reading anything.
   */
  @Test
  void noSpaceForIdThatIsNotOne() {
    assertNull(resolver.spaceOf(null));
    assertNull(resolver.spaceOf(""));
    assertNull(resolver.spaceOf("-5"));
    assertNull(resolver.spaceOf("5; drop"));
    assertNull(resolver.spaceOf("99999999999999999999"));
    verifyNoInteractions(agendaEventService);
  }

  /**
   * A lookup that fails, whichever service it is in, is read as no space:
   * the call is refused, never let through.
   */
  @Test
  void noSpaceWhenALookupFails() {
    doThrow(new IllegalStateException("storage down")).when(agendaEventService).getEventById(anyLong());
    assertNull(resolver.spaceOf("5"));

    doReturn(mock(Event.class)).when(agendaEventService).getEventById(anyLong());
    doThrow(new IllegalStateException("storage down")).when(agendaCalendarService).getCalendarById(anyLong());
    assertNull(resolver.spaceOf("5"));

    doReturn(mock(Calendar.class)).when(agendaCalendarService).getCalendarById(anyLong());
    doThrow(new IllegalStateException("index down")).when(identityManager).getIdentity(anyLong());
    assertNull(resolver.spaceOf("5"));

    Identity owner = identity(true, false, "alpha");
    doReturn(owner).when(identityManager).getIdentity(anyLong());
    doThrow(new IllegalStateException("index down")).when(spaceService).getSpaceByPrettyName("alpha");
    assertNull(resolver.spaceOf("5"));
  }

  /**
   * @param owner the identity that owns the event's calendar
   */
  private void givenOwner(Identity owner) {
    when(identityManager.getIdentity(OWNER_ID)).thenReturn(owner);
  }

  /**
   * @param space    true for the identity of a space
   * @param deleted  true for a deleted identity
   * @param remoteId the identity's remote id
   * @return the identity
   */
  private static Identity identity(boolean space, boolean deleted, String remoteId) {
    Identity identity = mock(Identity.class);
    when(identity.isSpace()).thenReturn(space);
    when(identity.isDeleted()).thenReturn(deleted);
    when(identity.getRemoteId()).thenReturn(remoteId);
    return identity;
  }

  /**
   * @param id the space id
   * @return the space
   */
  private static Space space(String id) {
    Space space = mock(Space.class);
    when(space.getId()).thenReturn(id);
    return space;
  }

}
