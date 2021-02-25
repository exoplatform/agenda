package org.exoplatform.agenda.service;

import java.time.*;
import java.util.ArrayList;
import java.util.TimeZone;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.storage.AgendaEventStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.container.ExoContainerContext;
import org.exoplatform.container.PortalContainer;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.listener.Listener;
import org.exoplatform.services.listener.ListenerService;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.provider.OrganizationIdentityProvider;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;
import org.exoplatform.social.core.manager.IdentityManager;
import org.exoplatform.social.core.space.model.Space;
import org.exoplatform.social.core.space.spi.SpaceService;

public abstract class BaseAgendaEventTest {

  protected static final String                             CALENDAR_DESCRIPTION = "calendarDescription";

  protected static final String                             CALENDAR_COLOR       = "calendarColor";

  protected static final ArrayList<EventAttendee>           ATTENDEES            = new ArrayList<>();

  protected static final ArrayList<EventConference>         CONFERENCES          = new ArrayList<>();

  protected static final ArrayList<EventAttachment>         ATTACHMENTS          = new ArrayList<>();

  protected static final ArrayList<EventReminder>           REMINDERS            = new ArrayList<>();

  protected static final RemoteEvent                        REMOTE_EVENT         = null;

  protected static AtomicReference<AgendaEventModification> eventCreationReference;

  protected static AtomicReference<AgendaEventModification> eventDeletionReference;

  protected static AtomicReference<AgendaEventModification> eventUpdateReference;

  protected PortalContainer                                 container;

  protected IdentityManager                                 identityManager;

  protected SpaceService                                    spaceService;

  protected AgendaCalendarService                           agendaCalendarService;

  protected AgendaUserSettingsService                       agendaUserSettingsService;

  protected AgendaEventService                              agendaEventService;

  protected AgendaEventConferenceService                    agendaEventConferenceService;

  protected AgendaEventAttachmentService                    agendaEventAttachmentService;

  protected AgendaEventAttendeeService                      agendaEventAttendeeService;

  protected AgendaEventReminderService                      agendaEventReminderService;

  protected AgendaRemoteEventService                        agendaRemoteEventService;

  protected AgendaEventDatePollService                      agendaEventDatePollService;

  protected ListenerService                                 listenerService;

  protected AgendaEventStorage                              agendaEventStorage;

  protected RemoteProvider                                  remoteProvider;

  protected Calendar                                        calendar;

  protected Calendar                                        spaceCalendar;

  protected Space                                           space;

  protected Identity                                        spaceIdentity;

  protected Identity                                        testuser1Identity;

  protected Identity                                        testuser2Identity;

  protected Identity                                        testuser3Identity;

  protected Identity                                        testuser4Identity;

  protected Identity                                        testuser5Identity;

  @Before
  public void setUp() throws ObjectNotFoundException {
    container = PortalContainer.getInstance();

    initServices();

    TimeZone.setDefault(TimeZone.getTimeZone("US/Hawaii"));

    begin();
    injectData();
  }

  private void initServices() {
    agendaCalendarService = container.getComponentInstanceOfType(AgendaCalendarService.class);
    agendaEventService = container.getComponentInstanceOfType(AgendaEventService.class);
    agendaUserSettingsService = container.getComponentInstanceOfType(AgendaUserSettingsService.class);
    agendaEventConferenceService = container.getComponentInstanceOfType(AgendaEventConferenceService.class);
    agendaEventAttachmentService = container.getComponentInstanceOfType(AgendaEventAttachmentService.class);
    agendaEventAttendeeService = container.getComponentInstanceOfType(AgendaEventAttendeeService.class);
    agendaEventReminderService = container.getComponentInstanceOfType(AgendaEventReminderService.class);
    agendaRemoteEventService = container.getComponentInstanceOfType(AgendaRemoteEventService.class);
    agendaEventDatePollService = container.getComponentInstanceOfType(AgendaEventDatePollService.class);
    identityManager = container.getComponentInstanceOfType(IdentityManager.class);
    spaceService = container.getComponentInstanceOfType(SpaceService.class);
    listenerService = container.getComponentInstanceOfType(ListenerService.class);
    agendaEventStorage = container.getComponentInstanceOfType(AgendaEventStorage.class);

    if (eventCreationReference == null) {
      eventCreationReference = new AtomicReference<>();// NOSONAR
      Listener<AgendaEventModification, Object> creationListener = new Listener<AgendaEventModification, Object>() {
        @Override
        public void onEvent(org.exoplatform.services.listener.Event<AgendaEventModification, Object> event) throws Exception {
          eventCreationReference.set(event.getSource());
        }
      };
      listenerService.addListener(Utils.POST_CREATE_AGENDA_EVENT_EVENT, creationListener);
    } else {
      eventCreationReference.set(null);
    }
    if (eventUpdateReference == null) {
      eventUpdateReference = new AtomicReference<>();// NOSONAR
      Listener<AgendaEventModification, Object> updateListener = new Listener<AgendaEventModification, Object>() {
        @Override
        public void onEvent(org.exoplatform.services.listener.Event<AgendaEventModification, Object> event) throws Exception {
          eventUpdateReference.set(event.getSource());
        }
      };
      listenerService.addListener(Utils.POST_UPDATE_AGENDA_EVENT_EVENT, updateListener);
    } else {
      eventUpdateReference.set(null);
    }
    if (eventDeletionReference == null) {
      eventDeletionReference = new AtomicReference<>();// NOSONAR
      Listener<AgendaEventModification, Object> deletionListener = new Listener<AgendaEventModification, Object>() {
        @Override
        public void onEvent(org.exoplatform.services.listener.Event<AgendaEventModification, Object> event) throws Exception {
          eventDeletionReference.set(event.getSource());
        }
      };
      listenerService.addListener(Utils.POST_DELETE_AGENDA_EVENT_EVENT, deletionListener);
    } else {
      eventDeletionReference.set(null);
    }
  }

  @After
  public void tearDown() throws ObjectNotFoundException {
    purgeData();
    end();
  }

  protected ZonedDateTime getDate() {
    return ZonedDateTime.of(LocalDate.now(), LocalTime.of(10, 0), ZoneOffset.UTC).withZoneSameInstant(ZoneId.systemDefault());
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
      remoteProvider = agendaRemoteEventService.saveRemoteProvider(new RemoteProvider(0,
                                                                                      "newRemoteProvider",
                                                                                      "Client API Key",
                                                                                      true));
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
  protected Event createEvent(Event event, long userIdentityId, Identity... attendeesArray) throws Exception { // NOSONAR
    try { // NOSONAR
      ATTENDEES.clear();
      for (Identity attendeeIdentity : attendeesArray) {
        EventAttendee userAttendee = new EventAttendee(0, 0, Long.parseLong(attendeeIdentity.getId()), null);
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
      REMINDERS.add(new EventReminder(0, 0, 1l, 1, ReminderPeriodType.MINUTE, null));

      return agendaEventService.createEvent(event.clone(),
                                            (ArrayList<EventAttendee>) ATTENDEES.clone(),
                                            (ArrayList<EventConference>) CONFERENCES.clone(),
                                            (ArrayList<EventAttachment>) ATTACHMENTS.clone(),
                                            (ArrayList<EventReminder>) REMINDERS.clone(),
                                            null,
                                            REMOTE_EVENT,
                                            true,
                                            userIdentityId);
    } finally {
      TimeZone.setDefault(TimeZone.getTimeZone("Japan"));
    }
  }

  protected Event newEventInstance(ZonedDateTime start, ZonedDateTime end, boolean allDay) {
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
                                                     until.toLocalDate(),
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
                     calendarId,
                     0l,
                     modifierId,
                     created,
                     updated,
                     summary,
                     description,
                     location,
                     color,
                     ZoneId.systemDefault(),
                     start,
                     end,
                     allDay,
                     EventAvailability.FREE,
                     EventStatus.CONFIRMED,
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

  protected void restartTransaction() {
    int i = 0;
    // Close transactions until no encapsulated transaction
    boolean success = true;
    do {
      try {
        end();
        i++;
      } catch (IllegalStateException e) {
        success = false;
      }
    } while (success);

    // Restart transactions with the same number of encapsulations
    for (int j = 0; j < i; j++) {
      begin();
    }
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
