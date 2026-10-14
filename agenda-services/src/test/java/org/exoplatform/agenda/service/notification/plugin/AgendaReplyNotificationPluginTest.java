package org.exoplatform.agenda.service.notification.plugin;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.notification.plugin.EventReplyNotificationPlugin;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.identity.model.Identity;

public class AgendaReplyNotificationPluginTest extends BaseAgendaEventTest {
  @Test
  public void testSendNotificationWhenReplyToEvent() throws Exception {
    // Given
    calendar.setName("Travel");
    agendaCalendarService.updateCalendar(calendar);
    ZonedDateTime start = ZonedDateTime.now();

    boolean allDay = false;

    Event event = newEventInstance(start, start, allDay);
    long modifierId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, modifierId, testuser2Identity, testuser5Identity);

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    eventAttendees.add(new EventAttendee(0,
                                         event.getId(),
                                         Long.parseLong(testuser2Identity.getId()),
                                         EventAttendeeResponse.ACCEPTED));
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
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
    value.setName(NotificationUtils.AGENDA_REPLY_NOTIFICATION_PLUGIN);
    value.setValue("#111111");
    initParams.addParam(value);
    value.setName("agenda.notification.plugin.key");
    value.setValue("111");
    initParams.addParam(value);

    EventReplyNotificationPlugin replyNotificationPlugin = new EventReplyNotificationPlugin(initParams,
                                                                                            identityManager,
                                                                                            agendaCalendarService,
                                                                                            agendaEventAttendeeService,
                                                                                            agendaEventService,
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

    Identity identity = identityManager.getIdentity(String.valueOf(testuser2Identity.getId()));
    String avatarUrl = "/portal/rest/v1/social/users/"+ identity.getId() + "/avatar";

    // When
    NotificationInfo notificationInfo = replyNotificationPlugin.makeNotification(ctx);
    Assert.assertNotNull(notificationInfo);

    // Then
    Assert.assertEquals(String.valueOf(event.getId()),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_ID));
    Assert.assertEquals(event.getSummary(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_TITLE));
    Assert.assertTrue(notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_PARTICIPANT_AVATAR_URL)
                                      .startsWith(avatarUrl));
    Assert.assertTrue(notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL).startsWith(eventUrl));
    Assert.assertEquals("the creator's calendar name travels with the reply notification",
                        "Travel",
                        notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_CALENDAR_NAME));
    Assert.assertEquals(eventAttendees.get(2).getResponse().name(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_RESPONSE));
    Assert.assertEquals(identity.getProfile().getFullName(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_PARTICIPANT_NAME));

  }

  /**
   * A reply given on a date a series stores is told with that date's row as
   * its event. Its link names the series and the date, which the page opens
   * back on the row: the row is no series, and a date read on it fails.
   */
  @Test
  public void testReplyOnAStoredDateLinksToTheDateOfItsSeries() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    Event series = createEvent(newEventInstance(start, start.plusHours(1), false), creatorId, testuser2Identity);
    ZonedDateTime occurrenceId = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                                start.minusDays(1),
                                                                                start.plusDays(3),
                                                                                series.getTimeZoneId(),
                                                                                10)
                                                   .get(1)
                                                   .getOccurrence()
                                                   .getId();
    Event date = agendaEventService.saveEventExceptionalOccurrence(series.getId(), occurrenceId);
    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_AGENDA, date)
                                                     .append(NotificationUtils.EVENT_PARTICIPANT_ID,
                                                             Long.parseLong(testuser2Identity.getId()))
                                                     .append(NotificationUtils.EVENT_RESPONSE, EventAttendeeResponse.ACCEPTED);

    NotificationInfo notificationInfo = newReplyNotificationPlugin().makeNotification(ctx);

    String webUrl = notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_WEB_EVENT_URL);
    String query = webUrl.substring(webUrl.indexOf('?') + 1);
    Map<String, String> parameters = new HashMap<>();
    for (String parameter : query.split("&")) {
      parameters.put(parameter.substring(0, parameter.indexOf('=')), parameter.substring(parameter.indexOf('=') + 1));
    }
    Assert.assertEquals(String.valueOf(series.getId()), parameters.get("parentId"));
    Assert.assertTrue("the mail's link opens the same date",
                      notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL).endsWith("?" + query));
    Event opened = agendaEventService.getEventOccurrence(Long.parseLong(parameters.get("parentId")),
                                                         AgendaDateUtils.parseRFC3339ToZonedDateTime(parameters.get("occurrenceId"),
                                                                                                     ZoneOffset.UTC),
                                                         ZoneOffset.UTC,
                                                         creatorId);
    Assert.assertEquals(date.getId(), opened.getId());
  }

  private EventReplyNotificationPlugin newReplyNotificationPlugin() {
    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName("agenda.notification.plugin.key");
    value.setValue(NotificationUtils.AGENDA_REPLY_NOTIFICATION_PLUGIN);
    initParams.addParam(value);
    return new EventReplyNotificationPlugin(initParams,
                                            identityManager,
                                            agendaCalendarService,
                                            agendaEventAttendeeService,
                                            agendaEventService,
                                            spaceService);
  }
}
