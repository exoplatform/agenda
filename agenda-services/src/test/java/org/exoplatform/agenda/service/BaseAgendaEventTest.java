package org.exoplatform.agenda.service;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.TimeZone;

import org.junit.After;
import org.junit.Before;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public abstract class BaseAgendaEventTest {

  protected static final String                     CALENDAR_DESCRIPTION = "calendarDescription";

  protected static final String                     CALENDAR_COLOR       = "calendarColor";

  protected static final ArrayList<EventAttendee>   ATTENDEES            = new ArrayList<>();

  protected static final ArrayList<EventConference> CONFERENCES          = new ArrayList<>();

  protected static final ArrayList<EventAttachment> ATTACHMENTS          = new ArrayList<>();

  protected static final ArrayList<EventReminder>   REMINDERS            = new ArrayList<>();

  protected PortalContainer                         container;

  protected IdentityManager                         identityManager;

  protected SpaceService                            spaceService;

  protected AgendaCalendarService                   agendaCalendarService;

  protected AgendaEventService                      agendaEventService;

  protected AgendaEventConferenceService            agendaEventConferenceService;

  protected AgendaEventAttachmentService            agendaEventAttachmentService;

  protected AgendaEventAttendeeService              agendaEventAttendeeService;

  protected AgendaEventReminderService              agendaEventReminderService;

  protected RemoteProvider                          remoteProvider;

  protected Calendar                                calendar;

  protected Calendar                                spaceCalendar;

  protected Space                                   space;

  protected Identity                                spaceIdentity;

  protected Identity                                testuser1Identity;

  protected Identity                                testuser2Identity;

  protected Identity                                testuser3Identity;

  protected Identity                                testuser4Identity;

  protected Identity                                testuser5Identity;

  @Before
  public void setUp() throws ObjectNotFoundException {
    container = PortalContainer.getInstance();

    agendaCalendarService = container.getComponentInstanceOfType(AgendaCalendarService.class);
    agendaEventService = container.getComponentInstanceOfType(AgendaEventService.class);
    agendaEventConferenceService = container.getComponentInstanceOfType(AgendaEventConferenceService.class);
    agendaEventAttachmentService = container.getComponentInstanceOfType(AgendaEventAttachmentService.class);
    agendaEventAttendeeService = container.getComponentInstanceOfType(AgendaEventAttendeeService.class);
    agendaEventReminderService = container.getComponentInstanceOfType(AgendaEventReminderService.class);
    identityManager = container.getComponentInstanceOfType(IdentityManager.class);
    spaceService = container.getComponentInstanceOfType(SpaceService.class);

    TimeZone.setDefault(TimeZone.getTimeZone("US/Hawaii"));

    begin();
    injectData();
  }

  @After
  public void tearDown() throws ObjectNotFoundException {
    purgeData();
    end();
  }

  protected void injectData() throws ObjectNotFoundException {
    purgeData();

    testuser1Identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "testuser1");
    testuser2Identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "testuser2");
    testuser3Identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "testuser3");
    testuser4Identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "testuser4");
    testuser5Identity = identityManager.getOrCreateIdentity(OrganizationIdentityProvider.NAME, "testuser5");

    calendar = agendaCalendarService.createCalendar(new Calendar(0,
                                                                 Long.parseLong(testuser1Identity.getId()),
                                                                 false,
                                                                 null,
                                                                 CALENDAR_DESCRIPTION,
                                                                 null,
                                                                 null,
                                                                 CALENDAR_COLOR,
                                                                 null));

    String displayName = "testSpaceAgenda";
    space = spaceService.getSpaceByDisplayName(displayName);
    if (space == null) {
      space = createSpace(displayName,
                          testuser1Identity.getRemoteId(),
                          testuser2Identity.getRemoteId(),
                          testuser3Identity.getRemoteId());
    }
    if (!spaceService.isMember(space, testuser1Identity.getRemoteId())) {
      spaceService.addMember(space, testuser1Identity.getRemoteId());
    }
    if (!spaceService.isMember(space, testuser2Identity.getRemoteId())) {
      spaceService.addMember(space, testuser2Identity.getRemoteId());
    }
    if (!spaceService.isMember(space, testuser3Identity.getRemoteId())) {
      spaceService.addMember(space, testuser3Identity.getRemoteId());
    }
    spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());

    spaceCalendar = agendaCalendarService.createCalendar(new Calendar(0,
                                                                      Long.parseLong(spaceIdentity.getId()),
                                                                      false,
                                                                      null,
                                                                      CALENDAR_DESCRIPTION,
                                                                      null,
                                                                      null,
                                                                      CALENDAR_COLOR,
                                                                      null));
    if (remoteProvider == null) {
      remoteProvider = agendaEventService.saveRemoteProvider(new RemoteProvider(0, "newRemoteProvider"));
    }
  }

  protected void purgeData() throws ObjectNotFoundException {
    if (spaceCalendar != null) {
      agendaCalendarService.deleteCalendarById(spaceCalendar.getId());
      spaceCalendar = null;
    }
    if (calendar != null) {
      agendaCalendarService.deleteCalendarById(calendar.getId());
      calendar = null;
    }
  }

  @SuppressWarnings("unchecked")
  protected Event createEvent(Event event, String username, Identity... attendeesArray) throws Exception { // NOSONAR
    try { // NOSONAR
      ATTENDEES.clear();
      for (Identity attendeeIdentity : attendeesArray) {
        EventAttendee userAttendee = new EventAttendee(0, Long.parseLong(attendeeIdentity.getId()), null);
        ATTENDEES.add(userAttendee);
      }

      CONFERENCES.clear();
      EventConference conference = new EventConference(0, 0, "webrtc", "conf_uri", "+123456", "654321", "confDescription");
      CONFERENCES.add(conference);

      ATTACHMENTS.clear();
      EventAttachment eventAttachment = new EventAttachment(0,
                                                            "1",
                                                            0);
      ATTACHMENTS.add(eventAttachment);

      REMINDERS.clear();
      REMINDERS.add(new EventReminder(0, 1l, 1, ReminderPeriodType.MINUTE, null));

      return agendaEventService.createEvent(event.clone(),
                                            (ArrayList<EventAttendee>) ATTENDEES.clone(),
                                            (ArrayList<EventConference>) CONFERENCES.clone(),
                                            (ArrayList<EventAttachment>) ATTACHMENTS.clone(),
                                            (ArrayList<EventReminder>) REMINDERS.clone(),
                                            true,
                                            username);
    } finally {
      TimeZone.setDefault(TimeZone.getTimeZone("Japan"));
    }
  }

  protected Event newEventInstance(ZonedDateTime start, ZonedDateTime end, boolean allDay) {
    String remoteId = "5";
    long remoteProviderId = remoteProvider.getId();
    long calendarId = calendar.getId();
    long modifierId = 0;

    ZonedDateTime created = ZonedDateTime.now();
    ZonedDateTime updated = ZonedDateTime.now();

    String summary = "eventSummary";
    String description = "eventDescription";
    String location = "eventLocation";
    String color = "eventColor";

    ZonedDateTime until = end.plusDays(2);

    EventRecurrence recurrence = new EventRecurrence(0,
                                                     until,
                                                     0,
                                                     EventRecurrenceType.DAILY,
                                                     EventRecurrenceFrequency.DAILY,
                                                     1,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null,
                                                     null);

    EventOccurrence occurrence = null;
    return new Event(0l,
                     0l,
                     remoteId,
                     remoteProviderId,
                     calendarId,
                     0l,
                     modifierId,
                     created,
                     updated,
                     summary,
                     description,
                     location,
                     color,
                     start,
                     end,
                     allDay,
                     EventAvailability.FREE,
                     EventStatus.TENTATIVE,
                     recurrence,
                     occurrence,
                     null,
                     false,
                     false);
  }

  protected void begin() {
    ExoContainerContext.setCurrentContainer(container);
    RequestLifeCycle.begin(container);
  }

  protected void end() {
    RequestLifeCycle.end();
  }

  protected Space createSpace(String displayName, String... members) {
    Space newSpace = new Space();
    newSpace.setDisplayName(displayName);
    newSpace.setPrettyName(displayName);
    newSpace.setManagers(new String[] { "root" });
    newSpace.setMembers(members);
    newSpace.setRegistration(Space.OPEN);
    newSpace.setVisibility(Space.PRIVATE);
    return spaceService.createSpace(newSpace, "root");
  }
}
