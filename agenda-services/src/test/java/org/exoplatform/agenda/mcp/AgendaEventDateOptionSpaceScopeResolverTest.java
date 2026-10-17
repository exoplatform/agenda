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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.service.AgendaEventDatePollService;

/**
 * Pins the space a date poll's proposed date belongs to: its event's space.
 */
class AgendaEventDateOptionSpaceScopeResolverTest {

  private AgendaEventDatePollService              datePollService;

  private AgendaEventSpaceScopeResolver           eventResolver;

  private AgendaEventDateOptionSpaceScopeResolver resolver;

  /**
   * Builds the resolver on a mocked date poll service and event resolver.
   */
  @BeforeEach
  void setUp() {
    datePollService = mock(AgendaEventDatePollService.class);
    eventResolver = mock(AgendaEventSpaceScopeResolver.class);
    resolver = new AgendaEventDateOptionSpaceScopeResolver(datePollService, eventResolver);
  }

  /**
   * The resolver is the {@value AgendaEventDateOptionSpaceScopeResolver#NAME}
   * one, a named service of the mcp-server profile, as the server finds its
   * resolvers by name among the beans.
   */
  @Test
  void isTheNamedResolverOfTheMcpServerProfile() {
    assertEquals("event_date_option", resolver.getName());
    assertEquals("agendaEventDateOptionSpaceScopeResolver",
                 AgendaEventDateOptionSpaceScopeResolver.class.getAnnotation(Service.class).value());
    assertEquals("mcp-server", AgendaEventDateOptionSpaceScopeResolver.class.getAnnotation(Profile.class).value()[0]);
  }

  /**
   * A date belongs to the space of its event, read in UTC.
   * Mutant: the event's space not read, or another event's.
   */
  @Test
  void aDateBelongsToItsEventsSpace() {
    EventDateOption option = new EventDateOption();
    option.setEventId(100L);
    when(datePollService.getEventDateOption(7L, ZoneOffset.UTC)).thenReturn(option);
    when(eventResolver.spaceOf("100")).thenReturn(12L);

    assertEquals(Long.valueOf(12L), resolver.spaceOf("7"));
  }

  /**
   * A date whose event belongs to no space, a missing date, a lookup that
   * fails and an id that isn't one belong to no space, which the server
   * refuses.
   * Mutants: a missing date or a failure read as a space, or a non-numeric
   * id looked up.
   */
  @Test
  void aDateOfNoSpaceOrNoDateBelongsToNoSpace() {
    EventDateOption personal = new EventDateOption();
    personal.setEventId(200L);
    when(datePollService.getEventDateOption(8L, ZoneOffset.UTC)).thenReturn(personal);
    when(eventResolver.spaceOf("200")).thenReturn(null);
    when(datePollService.getEventDateOption(eq(10L), eq(ZoneOffset.UTC))).thenThrow(new IllegalStateException("down"));

    assertNull(resolver.spaceOf("8"));
    assertNull(resolver.spaceOf("9"));
    assertNull(resolver.spaceOf("10"));
    assertTrue(resolver.spaceOf("x") == null && resolver.spaceOf(null) == null && resolver.spaceOf("-7") == null);
  }

  /**
   * An id that isn't a positive number, a negative one or zero included, is
   * never looked up.
   * Mutants: only a null id skipped, so a negative id reaches the lookup, or
   * zero looked up.
   */
  @Test
  void aNonNumericIdIsNotLookedUp() {
    resolver.spaceOf("7 OR 1=1");
    resolver.spaceOf("-7");
    resolver.spaceOf("x");
    assertNull(resolver.spaceOf("0"));
    verifyNoInteractions(datePollService, eventResolver);
    when(datePollService.getEventDateOption(anyLong(), eq(ZoneOffset.UTC))).thenReturn(null);
    assertNull(resolver.spaceOf("11"));
  }

}
