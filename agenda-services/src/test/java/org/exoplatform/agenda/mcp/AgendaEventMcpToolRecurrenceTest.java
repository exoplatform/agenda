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
package org.exoplatform.agenda.mcp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.AvailabilityConflicts;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.service.AgendaAvailabilityService;
import org.exoplatform.agenda.service.AgendaScheduleConflictService;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;

import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.social.translation.service.TranslationService;

/**
 * A change of an event's recurrence through the assistant, on the real
 * services: the full update it makes saves the lists it is given, so the
 * participants, their answers, the conferences and the caller's reminders
 * survive it only when the tool hands the stored ones back.
 */
public class AgendaEventMcpToolRecurrenceTest extends BaseAgendaEventTest {

  private AgendaEventMcpTool tool;

  /**
   * Builds the tool on the container's agenda services, as the current user
   * {@code testuser1}; the access checks and the availability report, which
   * this test does not cover, are stubbed.
   */
  @Before
  public void setUpTool() {
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(testuser1Identity.getRemoteId())));
    UserACL userAcl = mock(UserACL.class);
    when(userAcl.hasEditPermission(any(), any(), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    when(userAcl.hasAccessPermission(any(), any(), any(org.exoplatform.services.security.Identity.class))).thenReturn(true);
    AgendaAvailabilityService availabilityService = mock(AgendaAvailabilityService.class);
    when(availabilityService.getConflicts(any(), any(), any(), anyLong())).thenReturn(new AvailabilityConflicts(Collections.emptyList(),
                                                                                                              Collections.emptyList()));
    // The model built for the answer links each user to the default site
    UserPortalConfigService portalConfigService = mock(UserPortalConfigService.class);
    PortalConfig defaultSite = mock(PortalConfig.class);
    when(defaultSite.getName()).thenReturn("dw");
    when(portalConfigService.getDefaultSite(any())).thenReturn(defaultSite);
    tool = new AgendaEventMcpTool(agendaCalendarService,
                                  agendaEventService,
                                  availabilityService,
                                  mock(AgendaScheduleConflictService.class),
                                  agendaEventConferenceService,
                                  agendaEventAttendeeService,
                                  agendaEventDatePollService,
                                  agendaEventReminderService,
                                  agendaUserSettingsService,
                                  portalConfigService,
                                  mock(ProfilePropertyService.class),
                                  mock(PermanentLinkService.class),
                                  mock(TranslationService.class),
                                  identityManager,
                                  spaceService,
                                  userAcl);
  }

  /**
   * Clears the conversation state installed for the test.
   */
  @After
  public void tearDownTool() {
    ConversationState.setCurrent(null);
  }

  /**
   * Making a single event weekly through the tool keeps both participants'
   * rows with their answers, its conference and its organiser's reminder.
   * Mutant: the update given null lists, which deletes all of them.
   *
   * @throws Exception when the event can't be created, answered or updated
   */
  @Test
  public void aRecurrenceChangeKeepsTheParticipantsTheirAnswersTheConferencesAndTheReminders() throws Exception {
    long organiserId = Long.parseLong(testuser1Identity.getId());
    long acceptingId = Long.parseLong(testuser2Identity.getId());
    long decliningId = Long.parseLong(testuser3Identity.getId());
    ZonedDateTime start = getDate().withZoneSameInstant(ZoneOffset.UTC).plusDays(3);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(ZoneOffset.UTC);
    event.setRecurrence(null);
    event = createEvent(event, organiserId, testuser2Identity, testuser3Identity);
    long eventId = event.getId();
    agendaEventAttendeeService.sendEventResponse(eventId, acceptingId, EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendEventResponse(eventId, decliningId, EventAttendeeResponse.DECLINED);
    int rowsBefore = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees().size();
    assertEquals(1, agendaEventConferenceService.getEventConferences(eventId).size());
    assertEquals(1, agendaEventReminderService.getEventReminders(eventId, organiserId).size());

    assertNotNull(tool.updateAgendaEvent(eventId, null, null, null, null, null, "WEEKLY", 1, null, 3, false));

    Event updated = agendaEventService.getEventById(eventId);
    assertNotNull(updated.getRecurrence());
    List<EventAttendee> rows = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    Map<Long, EventAttendeeResponse> answers = rows.stream()
                                                   .collect(Collectors.toMap(EventAttendee::getIdentityId,
                                                                             EventAttendee::getResponse));
    assertEquals(EventAttendeeResponse.ACCEPTED, answers.get(acceptingId));
    assertEquals(EventAttendeeResponse.DECLINED, answers.get(decliningId));
    assertEquals(rowsBefore, rows.size());
    assertEquals(1, agendaEventConferenceService.getEventConferences(eventId).size());
    assertEquals(1, agendaEventReminderService.getEventReminders(eventId, organiserId).size());
  }

}
