package org.exoplatform.agenda.service;

import java.time.ZonedDateTime;
import java.util.List;

import org.exoplatform.agenda.constant.EventModificationType;
import org.exoplatform.agenda.model.EventAttendee;
import org.junit.Assert;
import org.junit.Test;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.notification.plugin.AgendaNotificationPlugin;
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

public class AgendaNotificationPluginTest extends BaseAgendaEventTest {
  public static final ArgumentLiteral<String> EVENT_TITLE = new ArgumentLiteral<>(String.class, "eventTitle");

  @Test
  public void testMakeNotificationWhenCreateEvent() throws Exception {
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
    value.setValue("#111111");
    initParams.addParam(value);
    value.setName("agenda.notification.plugin.key");
    value.setValue("111");
    initParams.addParam(value);

    AgendaNotificationPlugin agendaNotificationPlugin = new AgendaNotificationPlugin(initParams,
                                                                                     identityManager,
                                                                                     agendaEventService,
                                                                                     agendaEventAttendeeService,
                                                                                     agendaCalendarService,
                                                                                     spaceService);
    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_AGENDA, createdEvent)
                                                     .append(NotificationUtils.EVENT_ATTENDEE,
                                                             agendaEventAttendeeService.getEventAttendees(createdEvent.getId()))
                                                     .append(EVENT_TITLE, createdEvent.getSummary())
                                                     .append(NotificationUtils.EVENT_MODIFICATION_TYPE,
                                                             EventModificationType.ADDED.name());
    String eventUrl = System.getProperty("gatein.email.domain.url")
                            .concat("portal/classic/agenda?eventId=")
                            .concat(String.valueOf(createdEvent.getId()));
    // Attendee: Root is the manager of space, testuser4Identity creator of
    // space,testuser2Identity, testuser3Identity
    long nbAttendee = agendaEventAttendeeService.getEventAttendees(createdEvent.getId()).size();
    long ownerId = agendaCalendarService.getCalendarById(createdEvent.getCalendarId()).getOwnerId();

    // When
    NotificationInfo notificationInfo = agendaNotificationPlugin.makeNotification(ctx);

    // Then
    Assert.assertEquals(String.valueOf(createdEvent.getId()),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_ID));
    Assert.assertEquals(String.valueOf(ownerId),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OWNER_ID));
    Assert.assertEquals(createdEvent.getSummary(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_TITLE));
    Assert.assertEquals(String.valueOf(nbAttendee), String.valueOf(notificationInfo.getSendToUserIds().size()));
    Assert.assertEquals(eventUrl, notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL));
  }

  @Test
  public void testMakeNotificationWhenUpdateEvent() throws Exception {
    // Given
    ZonedDateTime start = ZonedDateTime.now().withNano(0);

    boolean allDay = true;
    String creatorUserName = testuser1Identity.getRemoteId();

    Event event = newEventInstance(start, start, allDay);

    Space space = createSpace("Test space notifications2", testuser4Identity.getRemoteId());
    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());

    Event createdEvent = createEvent(event.clone(),
                                     creatorUserName,
                                     testuser1Identity,
                                     testuser2Identity,
                                     testuser3Identity,
                                     spaceIdentity);
    Event updatedEvent = agendaEventService.getEventById(createdEvent.getId(), null, testuser1Identity.getRemoteId());
    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(updatedEvent.getId());
    agendaEventService.updateEvent(updatedEvent, eventAttendees, null, null, null, true, testuser1Identity.getRemoteId());

    InitParams initParams = new InitParams();
    ValueParam value = new ValueParam();
    value.setName(NotificationUtils.AGENDA_EVENT_MODIFIED_NOTIFICATION_PLUGIN);
    value.setValue("#222222");
    initParams.addParam(value);
    value.setName("agenda.notification.plugin.key");
    value.setValue("222");
    initParams.addParam(value);

    AgendaNotificationPlugin agendaNotificationPlugin = new AgendaNotificationPlugin(initParams,
                                                                                     identityManager,
                                                                                     agendaEventService,
                                                                                     agendaEventAttendeeService,
                                                                                     agendaCalendarService,
                                                                                     spaceService);
    updatedEvent.setUpdated(start);
    updatedEvent.setModifierId(Long.parseLong(testuser1Identity.getId()));
    NotificationContext ctx = NotificationContextImpl.cloneInstance()
                                                     .append(NotificationUtils.EVENT_AGENDA, updatedEvent)
                                                     .append(NotificationUtils.EVENT_ATTENDEE,
                                                             agendaEventAttendeeService.getEventAttendees(updatedEvent.getId()))

                                                     .append(EVENT_TITLE, updatedEvent.getSummary())
                                                     .append(NotificationUtils.EVENT_MODIFICATION_TYPE,
                                                             EventModificationType.UPDATED.name());
    String eventUrl = System.getProperty("gatein.email.domain.url")
                            .concat("portal/classic/agenda?eventId=")
                            .concat(String.valueOf(createdEvent.getId()));
    /*
     * Attendee Root is manager of space, testuser4Identity creator of space,
     * testuser2Identity, testuser3Identity
     */
    long nbAttendee = agendaEventAttendeeService.getEventAttendees(updatedEvent.getId()).size();
    long ownerId = agendaCalendarService.getCalendarById(updatedEvent.getCalendarId()).getOwnerId();

    // When
    NotificationInfo notificationInfo = agendaNotificationPlugin.makeNotification(ctx);

    // Then
    Assert.assertEquals(String.valueOf(updatedEvent.getId()),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_ID));
    Assert.assertEquals(String.valueOf(ownerId),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_OWNER_ID));
    Assert.assertEquals(updatedEvent.getSummary(),
                        notificationInfo.getValueOwnerParameter(NotificationUtils.TEMPLATE_VARIABLE_EVENT_TITLE));
    Assert.assertEquals(String.valueOf(nbAttendee), String.valueOf(notificationInfo.getSendToUserIds().size()));
    Assert.assertEquals(eventUrl, notificationInfo.getValueOwnerParameter(NotificationUtils.STORED_PARAMETER_EVENT_URL));

  }
}
