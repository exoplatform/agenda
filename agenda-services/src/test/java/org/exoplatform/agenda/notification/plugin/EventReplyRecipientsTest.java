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
package org.exoplatform.agenda.notification.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.exoplatform.agenda.constant.CalendarShareLevel;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventVisibility;
import org.exoplatform.agenda.model.Calendar;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.service.AgendaCalendarService;
import org.exoplatform.agenda.service.AgendaCalendarShareService;
import org.exoplatform.agenda.service.AgendaEventAttendeeService;
import org.exoplatform.agenda.service.AgendaEventService;
import org.exoplatform.agenda.service.CalendarShareAccess;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.spi.SpaceService;

/**
 * Who hears an attendee's reply to a meeting a colleague created in someone
 * else's personal calendar (EXO-90378): the creator while their share lets
 * them read the event in full, and the owner of the calendar in every case.
 */
class EventReplyRecipientsTest {

  private static final long          OWNER    = 1L;

  private static final long          EDITOR   = 2L;

  private static final long          REPLIER  = 3L;

  private static final long          SPACE    = 50L;

  private static final long          CALENDAR = 10L;

  private AgendaCalendarShareService shareService;

  private AgendaEventService         eventService;

  private AgendaEventAttendeeService attendeeService;

  private EventReplyNotificationPlugin plugin;

  @BeforeEach
  void setUp() {
    IdentityManager identityManager = mock(IdentityManager.class);
    when(identityManager.getIdentity(String.valueOf(OWNER))).thenReturn(user(OWNER, "owner"));
    when(identityManager.getIdentity(String.valueOf(EDITOR))).thenReturn(user(EDITOR, "editor"));
    when(identityManager.getIdentity(String.valueOf(REPLIER))).thenReturn(user(REPLIER, "replier"));
    Identity space = new Identity(SpaceIdentityProvider.NAME, "team");
    space.setId(String.valueOf(SPACE));
    when(identityManager.getIdentity(String.valueOf(SPACE))).thenReturn(space);
    eventService = mock(AgendaEventService.class);
    shareService = mock(AgendaCalendarShareService.class);
    attendeeService = mock(AgendaEventAttendeeService.class);
    InitParams initParams = new InitParams();
    ValueParam key = new ValueParam();
    key.setName("agenda.notification.plugin.key");
    key.setValue("EventReplyNotificationPlugin");
    initParams.addParam(key);
    plugin = new EventReplyNotificationPlugin(initParams,
                                              identityManager,
                                              mock(AgendaCalendarService.class),
                                              attendeeService,
                                              eventService,
                                              mock(SpaceService.class));
    plugin.setCalendarShareAccess(new CalendarShareAccess(shareService));
  }

  private static Identity user(long id, String username) {
    Identity identity = new Identity(OrganizationIdentityProvider.NAME, username);
    identity.setId(String.valueOf(id));
    return identity;
  }

  private static Calendar calendarOf(long ownerId) {
    Calendar calendar = new Calendar();
    calendar.setId(CALENDAR);
    calendar.setOwnerId(ownerId);
    return calendar;
  }

  private static Event event(long creatorId, EventVisibility visibility) {
    Event event = new Event();
    event.setId(100L);
    event.setCalendarId(CALENDAR);
    event.setCreatorId(creatorId);
    event.setVisibility(visibility);
    return event;
  }

  private Set<Long> recipientsOfAReplyTo(Event event, Calendar calendar) {
    return plugin.replyRecipients(event, calendar, REPLIER, EventAttendeeResponse.ACCEPTED, null);
  }

  /** A colleague who still edits the calendar hears the replies, and so does its owner. */
  @Test
  void anEditorStillHearsTheRepliesBesideTheOwner() {
    when(shareService.getShareLevel(CALENDAR, EDITOR)).thenReturn(CalendarShareLevel.EDIT);

    assertEquals(Set.of(EDITOR, OWNER), recipientsOfAReplyTo(event(EDITOR, EventVisibility.PRIVATE), calendarOf(OWNER)));
  }

  /** Taken back to Can view, the creator still reads an event that is not private in full, and keeps its replies. */
  @Test
  void aCreatorDowngradedToViewKeepsTheRepliesOfAnEventThatIsNotPrivate() {
    when(shareService.getShareLevel(CALENDAR, EDITOR)).thenReturn(CalendarShareLevel.VIEW);

    assertEquals(Set.of(EDITOR, OWNER), recipientsOfAReplyTo(event(EDITOR, EventVisibility.PUBLIC), calendarOf(OWNER)));
  }

  /** Taken back to Can view on a private event, which they now see as busy time only: the owner alone hears its replies. */
  @Test
  void aCreatorDowngradedToViewNoLongerHearsTheRepliesOfAPrivateEvent() {
    when(shareService.getShareLevel(CALENDAR, EDITOR)).thenReturn(CalendarShareLevel.VIEW);

    assertEquals(Set.of(OWNER), recipientsOfAReplyTo(event(EDITOR, EventVisibility.PRIVATE), calendarOf(OWNER)));
  }

  /** A creator whose share was removed no longer hears the replies: the owner does. */
  @Test
  void aCreatorWhoseShareWasRemovedNoLongerHearsTheReplies() {
    when(shareService.getShareLevel(CALENDAR, EDITOR)).thenReturn(null);

    assertEquals(Set.of(OWNER), recipientsOfAReplyTo(event(EDITOR, EventVisibility.PUBLIC), calendarOf(OWNER)));
  }

  /**
   * A former editor who attends the meeting they created still reads it in
   * full, as any attendee does, and keeps hearing its replies beside the owner,
   * even with the share removed and the event private. Killed by the mutant
   * that drops the attendance check from creatorHearsReplies.
   */
  @Test
  void aFormerEditorWhoAttendsTheMeetingKeepsHearingItsReplies() {
    when(shareService.getShareLevel(CALENDAR, EDITOR)).thenReturn(null);
    when(attendeeService.isEventAttendee(100L, EDITOR)).thenReturn(true);

    assertEquals(Set.of(EDITOR, OWNER), recipientsOfAReplyTo(event(EDITOR, EventVisibility.PRIVATE), calendarOf(OWNER)));
  }

  /**
   * An event with no id of its own is answered on its series: a former editor
   * who attends the series keeps hearing the replies. Killed by the mutant that
   * asks the attendance on the event's own id alone.
   */
  @Test
  void aFormerEditorWhoAttendsTheSeriesKeepsHearingTheRepliesToAnOccurrence() {
    when(shareService.getShareLevel(CALENDAR, EDITOR)).thenReturn(null);
    when(attendeeService.isEventAttendee(100L, EDITOR)).thenReturn(true);
    Event occurrence = event(EDITOR, EventVisibility.PRIVATE);
    occurrence.setId(0L);
    occurrence.setParentId(100L);

    assertEquals(Set.of(EDITOR, OWNER), recipientsOfAReplyTo(occurrence, calendarOf(OWNER)));
  }

  /** The owner who created the meeting hears its replies once, and no share is read. */
  @Test
  void theOwnerWhoCreatedTheMeetingHearsItsReplies() {
    assertEquals(Set.of(OWNER), recipientsOfAReplyTo(event(OWNER, EventVisibility.PRIVATE), calendarOf(OWNER)));

    verify(shareService, never()).getShareLevel(anyLong(), anyLong());
  }

  /**
   * A space calendar keeps its creator as the one who hears the replies: its
   * owner, the space, is nobody to notify, and no share is read.
   */
  @Test
  void theCreatorOfASpaceEventKeepsItsReplies() {
    assertEquals(Set.of(EDITOR), recipientsOfAReplyTo(event(EDITOR, EventVisibility.PRIVATE), calendarOf(SPACE)));

    verify(shareService, never()).getShareLevel(anyLong(), anyLong());
  }
}
