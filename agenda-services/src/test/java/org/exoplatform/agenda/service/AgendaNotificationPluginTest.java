package org.exoplatform.agenda.service;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.notification.plugin.AgendaNotificationPlugin;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.ArgumentLiteral;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.junit.Assert;
import org.junit.Test;

import java.time.ZonedDateTime;


public class AgendaNotificationPluginTest extends BaseAgendaEventTest {
  public static final ArgumentLiteral<String> EVENT_TITLE = new ArgumentLiteral<>(String.class, "eventTitle");


  @Test
  public void testMakeNotificationToAttendee() throws Exception {
    // Given
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);
    Event createdEvent = createEvent(event.clone(), creatorUserName, testuser2Identity, testuser3Identity);

    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName("template.EventAddedNotificationPlugin");
    value.setValue("#111111");
    initParams.addParam(value);
    value.setName("notification.id");
    value.setValue("111");
    initParams.addParam(value);
    AgendaNotificationPlugin agendaNotificationPlugin = new AgendaNotificationPlugin(initParams,
                                                                                     agendaEventService,
                                                                                     agendaEventAttendeeService,
                                                                                     agendaCalendarService);
    NotificationContext ctx = NotificationContextImpl.cloneInstance().append(NotificationUtils.EVENT_ID, createdEvent.getId())
            .append(EVENT_TITLE,createdEvent.getSummary());
    String eventUrl = System.getProperty("gatein.email.domain.url").concat("portal/classic/agenda?eventId=").concat(String.valueOf(createdEvent.getId()));
    long nbAttendee = agendaEventAttendeeService.getEventAttendees(createdEvent.getId()).size();
    long ownerId = agendaCalendarService.getCalendarById(createdEvent.getCalendarId()).getOwnerId();

    //When
    NotificationInfo notificationInfo = agendaNotificationPlugin.makeNotification(ctx);

    //Then
    Assert.assertEquals(String.valueOf(createdEvent.getId()),notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_ID));
    Assert.assertEquals(String.valueOf(ownerId), notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OWNER_ID));
    Assert.assertEquals(createdEvent.getSummary(), notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_TITLE));
    Assert.assertEquals(String.valueOf(nbAttendee),String.valueOf(notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_RECEIVERS).split(",").length));
    Assert.assertEquals(eventUrl, notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL));
    Assert.assertEquals(AgendaDateUtils.toRFC3339Date(createdEvent.getStart()),notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_START_DATE));
    Assert.assertEquals(AgendaDateUtils.toRFC3339Date(createdEvent.getEnd()),notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_END_DATE));
  }
}
