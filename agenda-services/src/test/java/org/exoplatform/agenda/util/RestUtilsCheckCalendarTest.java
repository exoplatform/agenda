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
package org.exoplatform.agenda.util;

import static org.junit.Assert.*;

import java.time.ZonedDateTime;

import org.junit.Before;
import org.junit.Test;

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.rest.model.EventEntity;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.rest.entity.IdentityEntity;

/**
 * Container-backed tests of the destination-calendar resolution of
 * {@link RestUtils#checkCalendar}: the single spot where a client-supplied
 * calendar id enters the write path. Every resolution must end on a
 * <b>stored</b> calendar row rebuilt into the payload — never on
 * client-supplied calendar data.
 */
public class RestUtilsCheckCalendarTest extends BaseAgendaEventTest {

  /**
   * The payload rebuild goes through the social {@code EntityBuilder}, which
   * requires an authenticated conversation state — as every real REST call
   * has. Sets it up for the test user.
   */
  @Before
  public void setUpConversationState() {
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(testuser1Identity.getRemoteId())));
  }

  /**
   * Clears the conversation state installed for the test.
   */
  @org.junit.After
  public void tearDownConversationState() {
    ConversationState.setCurrent(null);
  }

  /**
   * An explicit {@code calendar.id} consistent with the sent owner must
   * resolve to exactly that calendar — not to the owner's first/default one —
   * and the payload's calendar block must be rebuilt from the stored row.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testExplicitCalendarIdIsHonored() throws Exception { // NOSONAR
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    Calendar secondCalendar = newPersonalCalendar(userIdentityId, "Work");
    try {
      EventEntity eventEntity = new EventEntity();
      org.exoplatform.agenda.rest.model.CalendarEntity payloadCalendar = new org.exoplatform.agenda.rest.model.CalendarEntity();
      payloadCalendar.setId(secondCalendar.getId());
      payloadCalendar.setOwner(identityEntityOf(testuser1Identity.getId()));
      // Forged display data that must never survive the resolution
      payloadCalendar.setTitle("forged title");
      eventEntity.setCalendar(payloadCalendar);

      RestUtils.checkCalendar(identityManager, agendaCalendarService, eventEntity);

      assertEquals("The explicitly designated calendar must be honored",
                   secondCalendar.getId(),
                   eventEntity.getCalendar().getId());
      assertEquals("The payload's calendar owner must be rebuilt from the stored row",
                   testuser1Identity.getId(),
                   eventEntity.getCalendar().getOwner().getId());
      assertNull("The client-forged calendar data must be discarded by the rebuild",
                 eventEntity.getCalendar().getTitle());
      assertEquals("The rebuilt owner block must reference the stored row's owner",
                   testuser1Identity.getRemoteId(),
                   eventEntity.getCalendar().getOwner().getRemoteId());
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
    }
  }

  /**
   * When the client changes the destination owner while round-tripping the
   * old calendar id — the historical space-change flow of the web form — the
   * owner must win and resolve to its default calendar.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testChangedOwnerWinsOverRoundTrippedCalendarId() throws Exception { // NOSONAR
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    Calendar secondCalendar = newPersonalCalendar(userIdentityId, "Work");
    try {
      EventEntity eventEntity = new EventEntity();
      org.exoplatform.agenda.rest.model.CalendarEntity payloadCalendar = new org.exoplatform.agenda.rest.model.CalendarEntity();
      // Old calendar id round-tripped from the loaded event...
      payloadCalendar.setId(secondCalendar.getId());
      // ...but the user switched the destination to the space
      payloadCalendar.setOwner(identityEntityOf(spaceIdentity.getId()));
      eventEntity.setCalendar(payloadCalendar);

      RestUtils.checkCalendar(identityManager, agendaCalendarService, eventEntity);

      assertEquals("A changed owner must re-home to that owner's default calendar",
                   spaceCalendar.getId(),
                   eventEntity.getCalendar().getId());
      assertEquals(spaceIdentity.getId(), eventEntity.getCalendar().getOwner().getId());
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
    }
  }

  /**
   * On update, a payload without an explicit calendar id whose owner matches
   * the stored event's calendar owner must preserve the event's calendar —
   * the historical behavior snapped every updated event back to the owner's
   * default calendar, which destroys any filing into a secondary calendar.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateWithoutExplicitIdPreservesStoredCalendar() throws Exception { // NOSONAR
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    Calendar secondCalendar = newPersonalCalendar(userIdentityId, "Work");
    try {
      ZonedDateTime start = getDate();
      Event eventInstance = newEventInstance(start, start.plusHours(1), false);
      eventInstance.setRecurrence(null);
      eventInstance.setCalendarId(secondCalendar.getId());
      Event storedEvent = createEvent(eventInstance.clone(), userIdentityId, testuser1Identity);

      // 1. Owner sent (as the stock web form does), no explicit id
      EventEntity eventEntity = new EventEntity();
      eventEntity.setId(storedEvent.getId());
      org.exoplatform.agenda.rest.model.CalendarEntity payloadCalendar = new org.exoplatform.agenda.rest.model.CalendarEntity();
      payloadCalendar.setOwner(identityEntityOf(testuser1Identity.getId()));
      eventEntity.setCalendar(payloadCalendar);

      RestUtils.checkCalendar(identityManager, agendaCalendarService, eventEntity, storedEvent);
      assertEquals("An update without explicit destination must preserve the stored calendar",
                   secondCalendar.getId(),
                   eventEntity.getCalendar().getId());

      // 2. No calendar block at all
      eventEntity.setCalendar(null);
      RestUtils.checkCalendar(identityManager, agendaCalendarService, eventEntity, storedEvent);
      assertEquals(secondCalendar.getId(), eventEntity.getCalendar().getId());

      // 3. A changed owner still re-homes to that owner's default calendar
      eventEntity.setCalendar(new org.exoplatform.agenda.rest.model.CalendarEntity());
      eventEntity.getCalendar().setOwner(identityEntityOf(spaceIdentity.getId()));
      RestUtils.checkCalendar(identityManager, agendaCalendarService, eventEntity, storedEvent);
      assertEquals(spaceCalendar.getId(), eventEntity.getCalendar().getId());
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
    }
  }

  /**
   * Unresolvable destinations must fail with the accurate exception type: an
   * explicit id pointing at no stored calendar, and a creation without any
   * destination.
   */
  @Test
  public void testUnresolvableDestinations() {
    // 1. Nonexistent explicit calendar id
    EventEntity eventEntity = new EventEntity();
    org.exoplatform.agenda.rest.model.CalendarEntity payloadCalendar = new org.exoplatform.agenda.rest.model.CalendarEntity();
    payloadCalendar.setId(987654321L);
    payloadCalendar.setOwner(identityEntityOf(testuser1Identity.getId()));
    eventEntity.setCalendar(payloadCalendar);
    try {
      RestUtils.checkCalendar(identityManager, agendaCalendarService, eventEntity);
      fail("A nonexistent explicit calendar id must be refused");
    } catch (AgendaException e) {
      assertEquals(AgendaExceptionType.CALENDAR_NOT_FOUND, e.getAgendaExceptionType());
    }

    // 2. Creation without any destination
    EventEntity emptyEventEntity = new EventEntity();
    emptyEventEntity.setCalendar(new org.exoplatform.agenda.rest.model.CalendarEntity());
    try {
      RestUtils.checkCalendar(identityManager, agendaCalendarService, emptyEventEntity);
      fail("A creation without any destination must be refused");
    } catch (AgendaException e) {
      assertEquals(AgendaExceptionType.CALENDAR_OWNER_NOT_FOUND, e.getAgendaExceptionType());
    }
  }

  /**
   * Creates a named personal (non-system) calendar for the given owner
   * through the user service path.
   *
   * @param ownerId technical identifier of the owner identity
   * @param name user-defined name of the calendar
   * @return the created {@link Calendar}
   * @throws Exception when the creation fails
   */
  private Calendar newPersonalCalendar(long ownerId, String name) throws Exception {
    Calendar personalCalendar = new Calendar(0, ownerId, false, null, null, null, null, null, null);
    personalCalendar.setName(name);
    return agendaCalendarService.createCalendar(personalCalendar, testuser1Identity.getRemoteId());
  }

  /**
   * Builds a minimal payload identity block carrying only the identity id, as
   * clients send it.
   *
   * @param identityId technical identifier of the identity, as {@link String}
   * @return a minimal {@link IdentityEntity}
   */
  private IdentityEntity identityEntityOf(String identityId) {
    IdentityEntity identityEntity = new IdentityEntity();
    identityEntity.setId(identityId);
    return identityEntity;
  }
}
