/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <gnu.org/licenses>.
 */
package org.exoplatform.agenda.listener;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.exoplatform.agenda.constant.EventAttendeeResponse;
import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventAttendee;
import org.exoplatform.agenda.model.EventAttendeePromotion;
import org.exoplatform.agenda.model.WebSocketMessage;
import org.exoplatform.agenda.service.AgendaWebSocketService;
import org.exoplatform.agenda.service.BaseAgendaEventTest;
import org.exoplatform.agenda.util.Utils;

public class AgendaWebSocketEventReplyListenerTest extends BaseAgendaEventTest {

  /**
   * The calendar of an attendee who answered is told about their answer, and
   * only them: the event's creator is told of a seat given, not of an answer.
   *
   * @throws Exception when the event cannot be created
   */
  @Test
  public void testAnAnswerIsToldToTheAttendeeAlone() throws Exception {
    Event event = singleEvent();
    EventAttendee answered = new EventAttendee(0, event.getId(), id(), EventAttendeeResponse.ACCEPTED);

    assertTold(Utils.POST_EVENT_RESPONSE_SENT, answered, event, event.getId(), Set.of(testuser2Identity.getRemoteId()));
  }

  /**
   * The calendar of an attendee promoted from the waiting list is told about
   * their seat: the event's data is the promotion, not the attendee's row.
   * The event's creator is told too, so that the participant list their page
   * shows gives the seat without being reopened (EXO-91879).
   *
   * @throws Exception when the event cannot be created
   */
  @Test
  public void testAPromotionIsToldToThePromotedAttendeeAndTheCreator() throws Exception {
    Event event = singleEvent();
    EventAttendee promoted = new EventAttendee(0, event.getId(), id(), EventAttendeeResponse.ACCEPTED);

    assertToldToTheAttendeeAndTheCreator(Utils.POST_EVENT_ATTENDEE_PROMOTED,
                                         new EventAttendeePromotion(promoted, 2, false),
                                         event);
  }

  /**
   * A promotion from a date a series stores that gave other dates too names
   * the series, which the page reloads whole: the date would refresh that
   * date only, and leave the other dates promoted as they were.
   *
   * @throws Exception when the series cannot be created
   */
  @Test
  public void testAPromotionFromAStoredDateOverOtherDatesNamesTheSeries() throws Exception {
    Event series = series();
    Event date = storedDate(series);
    EventAttendee promoted = new EventAttendee(0, date.getId(), id(), EventAttendeeResponse.ACCEPTED);

    assertToldToTheAttendeeAndTheCreator(Utils.POST_EVENT_ATTENDEE_PROMOTED,
                                         new EventAttendeePromotion(promoted, 2, false),
                                         date,
                                         series.getId());
    assertToldToTheAttendeeAndTheCreator(Utils.POST_EVENT_ATTENDEE_PROMOTED,
                                         new EventAttendeePromotion(promoted, 0, true),
                                         date,
                                         series.getId());
  }

  /**
   * A promotion on a date a series stores, and on it alone, names that date,
   * which the page refreshes in place. The creator told is the date's, which
   * is its series' own.
   *
   * @throws Exception when the series cannot be created
   */
  @Test
  public void testAPromotionOnAStoredDateAloneNamesThatDate() throws Exception {
    Event series = series();
    Event date = storedDate(series);
    assertEquals(series.getCreatorId(), date.getCreatorId());
    EventAttendee promoted = new EventAttendee(0, date.getId(), id(), EventAttendeeResponse.ACCEPTED);

    assertToldToTheAttendeeAndTheCreator(Utils.POST_EVENT_ATTENDEE_PROMOTED,
                                         new EventAttendeePromotion(promoted, 0, false),
                                         date);
  }

  private void assertToldToTheAttendeeAndTheCreator(String eventName, Object data, Event event) throws Exception {
    assertToldToTheAttendeeAndTheCreator(eventName, data, event, event.getId());
  }

  private void assertToldToTheAttendeeAndTheCreator(String eventName,
                                                    Object data,
                                                    Event event,
                                                    long namedEventId) throws Exception {
    assertTold(eventName,
               data,
               event,
               namedEventId,
               Set.of(testuser2Identity.getRemoteId(), testuser1Identity.getRemoteId()));
  }

  @SuppressWarnings("unchecked")
  private void assertTold(String eventName,
                          Object data,
                          Event event,
                          long namedEventId,
                          Set<String> recipientsTold) throws Exception {
    AgendaWebSocketService webSocketService = mock(AgendaWebSocketService.class);
    AgendaWebSocketEventReplyListener listener = new AgendaWebSocketEventReplyListener(container,
                                                                                       agendaEventService,
                                                                                       identityManager,
                                                                                       webSocketService);
    EventAttendee before = new EventAttendee(0, event.getId(), id(), EventAttendeeResponse.WAITING);

    listener.onEvent(new org.exoplatform.services.listener.Event<>(eventName, before, data));

    ArgumentCaptor<WebSocketMessage> message = ArgumentCaptor.forClass(WebSocketMessage.class);
    ArgumentCaptor<Collection<String>> recipients = ArgumentCaptor.forClass(Collection.class);
    verify(webSocketService, times(1)).sendMessage(message.capture(), recipients.capture());
    assertEquals(recipientsTold, Set.copyOf(recipients.getValue()));
    assertEquals(eventName, FieldUtils.readField(message.getValue(), "wsEventName", true));
    Map<String, Object> params = (Map<String, Object>) FieldUtils.readField(message.getValue(), "params", true);
    assertEquals(namedEventId, params.get("eventId"));
    assertEquals(event.getCalendarId(), params.get("calendarId"));
  }

  private Event series() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    return createEvent(newEventInstance(start, start.plusHours(1), false),
                       Long.parseLong(testuser1Identity.getId()),
                       testuser2Identity);
  }

  /** The second date of a series, stored on a row of its own. */
  private Event storedDate(Event series) throws Exception {
    ZonedDateTime start = series.getStart();
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series,
                                                                             start.minusDays(1),
                                                                             start.plusDays(3),
                                                                             series.getTimeZoneId(),
                                                                             10);
    Event date = agendaEventService.saveEventExceptionalOccurrence(series.getId(), occurrences.get(1).getOccurrence().getId());
    assertEquals(series.getId(), date.getParentId());
    return date;
  }

  private Event singleEvent() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().plusDays(1).withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null);
    return createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser2Identity);
  }

  private long id() {
    return Long.parseLong(testuser2Identity.getId());
  }
}
