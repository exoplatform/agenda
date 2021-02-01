package org.exoplatform.agenda.listener;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.constant.EventRecurrenceFrequency;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.agenda.model.EventRecurrence;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.commons.api.notification.service.WebNotificationService;
import org.junit.Assert;
import org.junit.Test;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AgendaVotesNotificationListenerTest extends BaseAgendaEventTest {
  @Test
  public void testSendNotificationWhenVoteForDate() throws Exception {
    Event event = new Event();
    event.setCalendarId(spaceCalendar.getId());
    EventRecurrence recurrence = new EventRecurrence();
    event.setRecurrence(recurrence);
    recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
    recurrence.setInterval(1);

    ZonedDateTime start = getDate();
    ZonedDateTime end = start;
    EventDateOption dateOption1 = new EventDateOption(0, 0, start, end, false, false, null);
    EventDateOption dateOption2 = new EventDateOption(0, 0, start.plusDays(1), end.plusDays(1), true, true, null);
    Event createdEvent = agendaEventService.createEvent(event,
                                                        Arrays.asList(new EventAttendee(0,
                                                                                        Long.parseLong(spaceIdentity.getId()),
                                                                                        EventAttendeeResponse.NEEDS_ACTION)),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Collections.emptyList(),
                                                        Arrays.asList(dateOption1, dateOption2),
                                                        null,
                                                        true,
                                                        Long.parseLong(testuser1Identity.getId()));

    long eventId = createdEvent.getId();
    List<EventDateOption> dateOptions = agendaEventDatePollService.getEventDateOptions(eventId, ZoneOffset.UTC);

    WebNotificationService webNotificationService = container.getComponentInstanceOfType(WebNotificationService.class);
    int initialNotificationsSize = webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId());

    agendaEventDatePollService.saveEventVotes(createdEvent.getId(),
                                              Collections.singletonList(dateOptions.get(0).getId()),
                                              Long.parseLong(testuser2Identity.getId()));

    AgendaVotesNotificationListener agendaVotesListener = new AgendaVotesNotificationListener(container);
    agendaVotesListener.onEvent(new org.exoplatform.services.listener.Event<Long, Long>(null,
                                                                                        eventId,
                                                                                        Long.parseLong(testuser2Identity.getId())));
    Assert.assertEquals(initialNotificationsSize + 1l, webNotificationService.getNumberOnBadge(testuser1Identity.getRemoteId()));
  }
}
