package org.exoplatform.agenda.service.notification.plugin;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Assert;
import org.junit.Test;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
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
                                                             agendaEventAttendeeService.getEventAttendees(createdEvent.getId()).getEventAttendees())
                                                     .append(EVENT_TITLE, createdEvent.getSummary())
                                                     .append(NotificationUtils.EVENT_MODIFICATION_TYPE,
                                                             AgendaEventModificationType.ADDED.name());

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
    List<String> recipients = notificationInfo.getSendToUserIds();
    // EXO-89975: the creator of the date poll is an attendee but must not be
    // notified about their own poll, only the invitees are
    Assert.assertFalse("Date poll creator must not be notified about their own poll",
                       recipients.contains(testuser1Identity.getRemoteId()));
    Assert.assertTrue(recipients.contains(testuser2Identity.getRemoteId()));
    Assert.assertTrue(recipients.contains(testuser3Identity.getRemoteId()));
    // every space member (its creator root included) is invited, except the poll creator
    Set<String> expectedRecipients = new HashSet<>(Arrays.asList(space.getMembers()));
    expectedRecipients.add(testuser2Identity.getRemoteId());
    expectedRecipients.add(testuser3Identity.getRemoteId());
    expectedRecipients.remove(testuser1Identity.getRemoteId());
    Assert.assertEquals(expectedRecipients, new HashSet<>(recipients));
  }

  @Test
  public void testMakeNotificationExcludesModifierWhenDifferentFromCreator() throws Exception {
    // Given
    ZonedDateTime start = ZonedDateTime.now().withNano(0);
    Event event = newEventInstance(start, start, false);
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser1Identity,
                                     testuser2Identity,
                                     testuser3Identity);

    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName("agenda.notification.plugin.key");
    value.setValue("111");
    initParams.addParam(value);

    DatePollNotificationPlugin datePollNotificationPlugin = new DatePollNotificationPlugin(initParams,
                                                                                           identityManager,
                                                                                           agendaCalendarService,
                                                                                           spaceService);

    // The notification context carries the user who performed the action, which
    // takes precedence over the persisted creator of the event
    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_AGENDA, createdEvent)
                                                     .append(NotificationUtils.EVENT_ATTENDEE,
                                                             agendaEventAttendeeService.getEventAttendees(createdEvent.getId()).getEventAttendees())
                                                     .append(NotificationUtils.EVENT_MODIFIER, Long.parseLong(testuser2Identity.getId()))
                                                     .append(NotificationUtils.EVENT_MODIFICATION_TYPE,
                                                             AgendaEventModificationType.ADDED.name());

    // When
    NotificationInfo notificationInfo = datePollNotificationPlugin.makeNotification(ctx);

    // Then
    List<String> recipients = notificationInfo.getSendToUserIds();
    Assert.assertFalse(recipients.contains(testuser2Identity.getRemoteId()));
    Assert.assertTrue(recipients.contains(testuser1Identity.getRemoteId()));
    Assert.assertTrue(recipients.contains(testuser3Identity.getRemoteId()));
    Assert.assertEquals(2, recipients.size());
  }
}
