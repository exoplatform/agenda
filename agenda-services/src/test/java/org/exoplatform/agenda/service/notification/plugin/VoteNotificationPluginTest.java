package org.exoplatform.agenda.service.notification.plugin;

import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.notification.plugin.EventVoteNotificationPlugin;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.identity.model.Identity;

public class VoteNotificationPluginTest extends BaseAgendaEventTest {
  @Test
  public void testSendNotificationWhenVoteToDatePoll() throws Exception {
    // Given
    ZonedDateTime start = ZonedDateTime.now();

    boolean allDay = false;

    Event event = newEventInstance(start, start, allDay);
    long modifierId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, modifierId, testuser2Identity, testuser5Identity);

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    List<GuestUser> guestUsers = agendaEventGuestService.getEventGuests(event.getId());
    List<EventConference> conferences= agendaEventConferenceService.getEventConferences(event.getId());
    eventAttendees.add(new EventAttendee(0,
                                         event.getId(),
                                         Long.parseLong(testuser2Identity.getId()),
                                         EventAttendeeResponse.ACCEPTED));
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  guestUsers,
                                                  conferences,
                                                  modifierId,
                                                  false,
                                                  true,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              modifierId,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));
    agendaEventAttendeeService.sendEventResponse(event.getId(),
                                                 Long.parseLong(testuser2Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName(NotificationUtils.AGENDA_VOTE_NOTIFICATION_PLUGIN);
    value.setValue("#111111");
    initParams.addParam(value);
    value.setName("agenda.notification.plugin.key");
    value.setValue("111");
    initParams.addParam(value);

    EventVoteNotificationPlugin voteNotificationPlugin = new EventVoteNotificationPlugin(initParams,
                                                                                         identityManager,
                                                                                         agendaCalendarService,
                                                                                         agendaEventAttendeeService,
                                                                                         spaceService);
    NotificationContext ctx =
                            NotificationContextImpl.cloneInstance()
                                                   .append(NotificationUtils.EVENT_AGENDA, event)
                                                   .append(NotificationUtils.EVENT_PARTICIPANT_ID,
                                                           eventAttendees.get(2).getIdentityId())
                                                   .append(NotificationUtils.EVENT_RESPONSE, eventAttendees.get(2).getResponse());
    String eventUrl = System.getProperty("gatein.email.domain.url")
                            .concat("portal/classic/agenda?eventId=")
                            .concat(String.valueOf(event.getId()));

    String avatarUrl = "/portal/rest/v1/social/users/default-image/";
    Identity identity = identityManager.getIdentity(String.valueOf(testuser2Identity.getId()));

    // When
    NotificationInfo notificationInfo = voteNotificationPlugin.makeNotification(ctx);
    Assert.assertNotNull(notificationInfo);

    // Then
    Assert.assertEquals(String.valueOf(event.getId()),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_ID));
    Assert.assertEquals(event.getSummary(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_TITLE));
    Assert.assertTrue(notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_PARTICIPANT_AVATAR_URL)
                                      .startsWith(avatarUrl));
    Assert.assertTrue(notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL).startsWith(eventUrl));
    Assert.assertEquals(identity.getProfile().getFullName(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_PARTICIPANT_NAME));

  }
}
