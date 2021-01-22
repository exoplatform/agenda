package org.exoplatform.agenda.service.notification.plugin;

import org.exoplatform.agenda.constant.EventModificationType;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.notification.plugin.DatePollNotificationPlugin;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
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

public class DatePollNotificationPluginTest extends BaseAgendaEventTest {
  public static final ArgumentLiteral<String> EVENT_TITLE = new ArgumentLiteral<>(String.class, "eventTitle");

  @Test
  public void testMakeNotificationWhenSuggestDates() throws Exception {
    // Given
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event event = newEventInstance(start, start, false);
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser1Identity,
                                     testuser2Identity,
                                     testuser3Identity,
                                     spaceIdentity);

    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName(NotificationUtils.AGENDA_DATE_POLL_NOTIFICATION_PLUGIN);
    value.setValue("#111111");
    initParams.addParam(value);
    value.setName("agenda.notification.plugin.key");
    value.setValue("111");
    initParams.addParam(value);

    DatePollNotificationPlugin datePollNotificationPlugin = new DatePollNotificationPlugin(initParams,
                                                                                           identityManager,
                                                                                           agendaCalendarService,
                                                                                           spaceService);

    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_AGENDA, createdEvent)
                                                     .append(NotificationUtils.EVENT_ATTENDEE,
                                                             agendaEventAttendeeService.getEventAttendees(createdEvent.getId()))
                                                     .append(EVENT_TITLE, createdEvent.getSummary())
                                                     .append(NotificationUtils.EVENT_MODIFICATION_TYPE,
                                                             EventModificationType.ADDED.name());

    long nbAttendee = agendaEventAttendeeService.getEventAttendees(createdEvent.getId()).size();
    long ownerId = agendaCalendarService.getCalendarById(createdEvent.getCalendarId()).getOwnerId();
    String creatorName = identityManager.getIdentity(String.valueOf(createdEvent.getCreatorId())).getProfile().getFullName();

    // When
    NotificationInfo notificationInfo = datePollNotificationPlugin.makeNotification(ctx);

    // Then
    Assert.assertEquals(String.valueOf(createdEvent.getId()),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_ID));
    Assert.assertEquals(String.valueOf(ownerId),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OWNER_ID));
    Assert.assertEquals(createdEvent.getSummary(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_TITLE));
    Assert.assertEquals(creatorName, notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_CREATOR));
    Assert.assertEquals(nbAttendee - 1, notificationInfo.getSendToUserIds().size());
  }
}
