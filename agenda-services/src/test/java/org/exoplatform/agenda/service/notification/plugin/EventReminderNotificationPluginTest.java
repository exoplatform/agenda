package org.exoplatform.agenda.service.notification.plugin;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventReminder;
import org.exoplatform.agenda.notification.plugin.EventReminderNotificationPlugin;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.NotificationUtils;
import org.exoplatform.commons.api.notification.NotificationContext;
import org.exoplatform.commons.api.notification.model.ArgumentLiteral;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.commons.notification.impl.NotificationContextImpl;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.container.xml.ValueParam;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.space.model.Space;

public class EventReminderNotificationPluginTest extends BaseAgendaEventTest {
  public static final ArgumentLiteral<String> EVENT_TITLE = new ArgumentLiteral<>(String.class, "eventTitle");

  @Test
  public void testMakeNotificationWhenReminderTriggered() throws Exception {
    // Given
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);

    Space space = createSpace("Test space notifications", testuser4Identity.getRemoteId());
    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());

    Event createdEvent = createEvent(event.clone(),
                                     creatorUserName,
                                     testuser1Identity,
                                     testuser2Identity,
                                     testuser3Identity,
                                     spaceIdentity);

    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName(NotificationUtils.AGENDA_EVENT_ADDED_NOTIFICATION_PLUGIN);
    value.setValue("#2222");
    initParams.addParam(value);
    value.setName("agenda.notification.plugin.key");
    value.setValue("2222");
    initParams.addParam(value);

    EventReminderNotificationPlugin eventReminderNotificationPlugin = new EventReminderNotificationPlugin(initParams,
                                                                                                          identityManager,
                                                                                                          agendaEventService,
                                                                                                          agendaCalendarService);

    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(createdEvent.getId(),
                                                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventReminders);
    assertFalse(eventReminders.isEmpty());

    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_AGENDA_REMINDER, eventReminders.get(0));
    String eventUrl = System.getProperty("gatein.email.domain.url")
                            .concat("portal/classic/agenda?eventId=")
                            .concat(String.valueOf(createdEvent.getId()));

    // When
    NotificationInfo notificationInfo = eventReminderNotificationPlugin.makeNotification(ctx);
    assertNotNull(notificationInfo);

    // Then
    Assert.assertEquals(String.valueOf(createdEvent.getId()),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_ID));
    Assert.assertEquals(String.valueOf(createdEvent.getCreatorId()),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OWNER_ID));
    Assert.assertEquals(createdEvent.getSummary(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_TITLE));
    Assert.assertEquals(1, notificationInfo.getSendToUserIds().size());
    Assert.assertEquals(eventUrl, notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL));
  }

}
