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
import static org.junit.Assert.assertTrue;
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
import org.exoplatform.agenda.mcp.model.AgendaEventModel;
import org.exoplatform.agenda.model.AvailabilityConflicts;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.RemoteEvent;
import org.exoplatform.agenda.service.AgendaAvailabilityService;
import org.exoplatform.agenda.service.AgendaScheduleConflictService;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.portal.config.UserACL;
import org.exoplatform.portal.config.UserPortalConfigService;
import org.exoplatform.portal.config.model.PortalConfig;
import org.exoplatform.services.security.ConversationState;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.profileproperty.ProfilePropertyService;

import io.meeds.portal.permlink.service.PermanentLinkService;
import io.meeds.social.translation.service.TranslationService;

/**
 * The assistant's agenda tool on the real agenda services: what its writes
 * keep, and what its reads show, against the rows the services store.
 */
public class AgendaEventMcpToolServicesTest extends BaseAgendaEventTest {

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
                                  agendaRemoteEventService,
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
   * Makes a user the current one, whom the tool acts and reads for.
   *
   * @param identity the user's identity
   */
  private void actAs(Identity identity) {
    ConversationState.setCurrent(new ConversationState(new org.exoplatform.services.security.Identity(identity.getRemoteId())));
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
   * rows with their answers, its conference, its organiser's reminder and
   * the organiser's link to the event's copy in a remote calendar.
   * Mutant: the update given null lists, or no remote copy, which deletes
   * them.
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
    agendaRemoteEventService.saveRemoteEvent(eventId,
                                             new RemoteEvent(0,
                                                             eventId,
                                                             organiserId,
                                                             "copy-in-the-remote-calendar",
                                                             remoteProvider.getId(),
                                                             remoteProvider.getName()),
                                             organiserId);

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
    RemoteEvent remoteCopy = agendaRemoteEventService.findRemoteEvent(eventId, organiserId);
    assertNotNull("the link to the copy in the remote calendar was dropped", remoteCopy);
    assertEquals("copy-in-the-remote-calendar", remoteCopy.getRemoteId());
  }

  /**
   * A user who declined a series and then said Yes from its second date holds
   * two rows. A read of the series lists them once, with the answer valid at
   * its next date, here the third: the Yes, as the event page shows it.
   * Mutant: the answer read from the first row, and one entry per row.
   *
   * @throws Exception when the series can't be created or answered
   */
  @Test
  public void aSeriesReadListsEachAttendeeOnceWithTheAnswerValidAtItsNextDate() throws Exception {
    long userId = Long.parseLong(testuser5Identity.getId());
    ZonedDateTime start = ZonedDateTime.now().minusHours(30).withNano(0).withSecond(0).withMinute(0);
    Event series = newEventInstance(start, start.plusHours(1), false);
    long seriesId = createEvent(series, Long.parseLong(testuser1Identity.getId()), testuser5Identity).getId();
    List<ZonedDateTime> dates = occurrenceIds(seriesId);
    agendaEventAttendeeService.sendEventResponse(seriesId, userId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendUpcomingEventResponse(seriesId, dates.get(1), userId, EventAttendeeResponse.ACCEPTED);
    assertEquals(2, agendaEventAttendeeService.getEventAttendees(seriesId).getEventAttendees(userId).size());
    assertTrue("the third date is the next one", dates.get(2).isAfter(ZonedDateTime.now()));

    actAs(testuser5Identity);
    AgendaEventModel model = tool.getAgendaEventById(seriesId);

    assertEquals(EventAttendeeResponse.ACCEPTED, model.getUserAnswer());
    assertEquals(agendaEventAttendeeService.getSeriesResponse(seriesId, userId), model.getUserAnswer());
    assertEquals(1,
                 model.getAttendees()
                      .stream()
                      .filter(attendee -> attendee.getUser() != null
                          && testuser5Identity.getRemoteId().equals(attendee.getUser().getUsername()))
                      .count());
  }

  /**
   * A read of an event with a capacity limit gives its seats and the
   * reader's place on its waiting list, as the event page does.
   * Mutant: the seats left unfilled.
   *
   * @throws Exception when the event can't be created or answered
   */
  @Test
  public void aReadOfALimitedEventGivesItsSeatsAndTheReadersPlaceOnTheWaitingList() throws Exception {
    ZonedDateTime start = getDate().withZoneSameInstant(ZoneOffset.UTC).plusDays(3);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    event.setCapacity(1);
    long eventId = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser4Identity, testuser5Identity).getId();
    agendaEventAttendeeService.sendEventResponse(eventId, Long.parseLong(testuser4Identity.getId()), EventAttendeeResponse.ACCEPTED);
    agendaEventAttendeeService.sendEventResponse(eventId, Long.parseLong(testuser5Identity.getId()), EventAttendeeResponse.ACCEPTED);

    actAs(testuser5Identity);
    AgendaEventModel model = tool.getAgendaEventById(eventId);

    assertEquals(EventAttendeeResponse.WAITING, model.getUserAnswer());
    assertEquals(Integer.valueOf(1), model.getCapacity());
    assertEquals(Integer.valueOf(1), model.getAcceptedCount());
    assertEquals(Integer.valueOf(1), model.getWaitingCount());
    assertEquals(Integer.valueOf(1), model.getWaitingListPosition());
  }

  private List<ZonedDateTime> occurrenceIds(long seriesId) {
    Event stored = agendaEventService.getEventById(seriesId);
    ZonedDateTime start = stored.getStart();
    return agendaEventService.getEventOccurrencesInPeriod(stored, start.minusDays(1), start.plusDays(5), stored.getTimeZoneId(), 0)
                             .stream()
                             .map(occurrence -> occurrence.getOccurrence().getId())
                             .toList();
  }

}
