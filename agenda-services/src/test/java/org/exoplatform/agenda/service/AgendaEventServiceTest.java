/*
 * Copyright (C) 2020 eXo Platform SAS.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either version 3
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, see<http://www.gnu.org/licenses/>.
*/
package org.exoplatform.agenda.service;

import static org.exoplatform.agenda.util.NotificationUtils.*;
import static org.exoplatform.agenda.util.Utils.generateIcsFile;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

import org.exoplatform.commons.api.notification.model.MessageInfo;
import org.exoplatform.commons.api.notification.model.NotificationInfo;
import org.exoplatform.container.component.RequestLifeCycle;
import org.exoplatform.services.mail.Attachment;
import org.junit.Test;

import org.exoplatform.agenda.constant.*;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.AgendaExceptionType;
import org.exoplatform.agenda.model.*;
import org.exoplatform.agenda.util.AgendaDateUtils;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.social.core.identity.model.Identity;
import org.exoplatform.social.core.identity.model.Profile;
import org.exoplatform.social.core.identity.provider.SpaceIdentityProvider;

public class AgendaEventServiceTest extends BaseAgendaEventTest {

  /**
   * eXIP 7.3.0.20 Open Event, US01 (EXO-89477): the flag is carried end to end
   * by the full saves that rebuild the Event positionally, is kept when a save
   * does not state it, and is never true on a personal calendar. Reads go
   * through the cached storage, which returns a clone: a clone() that dropped
   * the field would fail the first assertion.
   */
  @Test
  public void testOpenEventFlagCarriedByFullSaves() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    // Created open in a space calendar
    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    event.setRecurrence(null);
    event.setOpen(true);
    Event createdEvent = createEvent(event.clone(), userIdentityId, testuser2Identity);
    long eventId = createdEvent.getId();
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(eventId).getOpen());
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(eventId, null, userIdentityId).getOpen());

    // A full save that does not state the flag (null) keeps it, and reports no
    // toggle: the audit follows what was written, not what the payload carried
    Event storedEvent = agendaEventService.getEventById(eventId, ZoneOffset.UTC, userIdentityId);
    Event updatedEvent = storedEvent.clone();
    updatedEvent.setOpen(null);
    eventUpdateReference.set(null);
    agendaEventService.updateEvent(updatedEvent,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(eventId).getOpen());
    assertNotNull(eventUpdateReference.get());
    assertFalse(eventUpdateReference.get().hasModification(AgendaEventModificationType.OPEN_UPDATED));

    // An explicit value is applied by the same save
    updatedEvent = agendaEventService.getEventById(eventId, ZoneOffset.UTC, userIdentityId).clone();
    updatedEvent.setOpen(false);
    agendaEventService.updateEvent(updatedEvent,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());

    // An explicit true by an editor opens it through the same save
    updatedEvent = agendaEventService.getEventById(eventId, ZoneOffset.UTC, userIdentityId).clone();
    updatedEvent.setOpen(true);
    agendaEventService.updateEvent(updatedEvent,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(eventId).getOpen());

    // Moved to a personal calendar with the flag unspecified: the invariant is
    // evaluated on the target calendar, so the event is locked by the move —
    // and the audit reports that toggle, which the payload never asked for
    updatedEvent = agendaEventService.getEventById(eventId, ZoneOffset.UTC, userIdentityId).clone();
    updatedEvent.setCalendarId(calendar.getId());
    updatedEvent.setOpen(null);
    eventUpdateReference.set(null);
    agendaEventService.updateEvent(updatedEvent,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertNotNull(eventUpdateReference.get());
    assertTrue(eventUpdateReference.get().hasModification(AgendaEventModificationType.OPEN_UPDATED));

    // Absent at creation means locked
    Event lockedEvent = newEventInstance(start, start, true);
    lockedEvent.setCalendarId(spaceCalendar.getId());
    lockedEvent.setRecurrence(null);
    lockedEvent.setOpen(null);
    Event createdLockedEvent = createEvent(lockedEvent.clone(), userIdentityId, testuser2Identity);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(createdLockedEvent.getId()).getOpen());

    // Never open on a personal calendar, whatever the client sends
    Event personalEvent = newEventInstance(start, start, true);
    personalEvent.setRecurrence(null);
    personalEvent.setOpen(true);
    Event createdPersonalEvent = createEvent(personalEvent.clone(), userIdentityId, testuser2Identity);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(createdPersonalEvent.getId()).getOpen());

    // ... nor through a full save that states it
    Event personalUpdate = agendaEventService.getEventById(createdPersonalEvent.getId(), ZoneOffset.UTC, userIdentityId).clone();
    personalUpdate.setOpen(true);
    agendaEventService.updateEvent(personalUpdate,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(createdPersonalEvent.getId()).getOpen());

    // Never open on a date poll either (open date polls are eXIP 7.3.0.40): the
    // status is derived by the server from the date options, not sent by the
    // client, so the invariant reads the derived status
    Event datePoll = newEventInstance(start, start, true);
    datePoll.setCalendarId(spaceCalendar.getId());
    datePoll.setRecurrence(null);
    datePoll.setOpen(true);
    List<EventDateOption> dateOptions = Arrays.asList(new EventDateOption(0, 0, start, start.plusHours(1), false, false, null),
                                                      new EventDateOption(0, 0, start.plusDays(1), start.plusDays(1).plusHours(1), false, false, null));
    Event createdDatePoll = agendaEventService.createEvent(datePoll.clone(),
                                                           Collections.emptyList(),
                                                           Collections.emptyList(),
                                                           Collections.emptyList(),
                                                           dateOptions,
                                                           null,
                                                           false,
                                                           userIdentityId);
    assertEquals(EventStatus.TENTATIVE, agendaEventService.getEventById(createdDatePoll.getId()).getStatus());
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(createdDatePoll.getId()).getOpen());
  }

  /**
   * eXIP 7.3.0.20 Open Event, US02 (EXO-89478): the padlock of the event page
   * patches the flag. The right is the event's existing edit right; an
   * invariant of US01 is refused when the patch asks to open, and applied
   * silently — locking the event, as the full-save path does — when the patch
   * merely moves an already-open event; the toggle resets no participant
   * answer.
   */
  @Test
  public void testOpenEventPatchedFromTheEventPage() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorIdentityId = Long.parseLong(testuser1Identity.getId());
    long attendeeIdentityId = Long.parseLong(testuser2Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    event.setRecurrence(null);
    Event createdEvent = createEvent(event.clone(), creatorIdentityId, testuser2Identity);
    long eventId = createdEvent.getId();
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());

    // An attendee answers before the toggle
    agendaEventAttendeeService.sendEventResponse(eventId, attendeeIdentityId, EventAttendeeResponse.ACCEPTED);
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId, null, attendeeIdentityId));

    // A user without the edit right cannot toggle
    try {
      agendaEventService.updateEventFields(eventId, getFields("open", "true"), false, false, attendeeIdentityId);
      fail("An attendee without update right should not open the event");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());

    // The editor opens it, and no answer is reset by the toggle
    agendaEventService.updateEventFields(eventId, getFields("open", "true"), false, false, creatorIdentityId);
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(eventId).getOpen());
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId, null, attendeeIdentityId));
    assertNotNull(eventUpdateReference.get());
    assertTrue(eventUpdateReference.get().hasModification(AgendaEventModificationType.OPEN_UPDATED));

    // ... and locks it again
    agendaEventService.updateEventFields(eventId, getFields("open", "false"), false, false, creatorIdentityId);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertEquals(EventAttendeeResponse.ACCEPTED,
                 agendaEventAttendeeService.getEventResponse(eventId, null, attendeeIdentityId));

    // A patch that does not touch the flag leaves it, and reports no toggle
    eventUpdateReference.set(null);
    agendaEventService.updateEventFields(eventId, getFields("summary", "patched"), false, false, creatorIdentityId);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertFalse(eventUpdateReference.get().hasModification(AgendaEventModificationType.OPEN_UPDATED));

    // Opening a personal-calendar event is refused, not silently ignored
    Event personalEvent = newEventInstance(start, start, true);
    personalEvent.setRecurrence(null);
    Event createdPersonalEvent = createEvent(personalEvent.clone(), creatorIdentityId, testuser2Identity);
    try {
      agendaEventService.updateEventFields(createdPersonalEvent.getId(),
                                           getFields("open", "true"),
                                           false,
                                           false,
                                           creatorIdentityId);
      fail("A personal calendar event should not be opened");
    } catch (IllegalArgumentException e) {
      assertEquals("agenda.openEvent.notAllowed", e.getMessage());
    }
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(createdPersonalEvent.getId()).getOpen());

    // Being allowed to update an event does not grant filing it into a calendar
    // one may not create in: the patch path enforces this like the full save,
    // and canUpdateEvent is satisfied by a mere attendee when the event allows
    // attendees to update it
    Event sharedEvent = newEventInstance(start, start, true);
    sharedEvent.setCalendarId(spaceCalendar.getId());
    sharedEvent.setRecurrence(null);
    sharedEvent.setAllowAttendeeToUpdate(true);
    Event createdSharedEvent = createEvent(sharedEvent.clone(), creatorIdentityId, testuser2Identity);
    assertTrue(agendaEventService.canUpdateEvent(agendaEventService.getEventById(createdSharedEvent.getId()),
                                                 attendeeIdentityId));
    try {
      agendaEventService.updateEventFields(createdSharedEvent.getId(),
                                           getFields("calendarId", String.valueOf(calendar.getId())),
                                           false,
                                           false,
                                           attendeeIdentityId);
      fail("An attendee should not move the event into a calendar they cannot create in");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals(spaceCalendar.getId(), agendaEventService.getEventById(createdSharedEvent.getId()).getCalendarId());

    // An unsupported field is a caller error too (400), not a server error
    try {
      agendaEventService.updateEventFields(eventId, getFields("unknownField", "value"), false, false, creatorIdentityId);
      fail("An unsupported field should be refused");
    } catch (IllegalArgumentException e) {
      assertEquals("agenda.eventFieldNotSupported", e.getMessage());
    }

    // A patch that does not carry the flag never fails on it: an already-open
    // event stays patchable on any other field
    agendaEventService.updateEventFields(eventId, getFields("open", "true"), false, false, creatorIdentityId);
    agendaEventService.updateEventFields(eventId, getFields("location", "elsewhere"), false, false, creatorIdentityId);
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(eventId).getOpen());

    // ... and moving that open event to a personal calendar locks it silently,
    // as a full save of the same operation does — and the audit says so
    eventUpdateReference.set(null);
    agendaEventService.updateEventFields(eventId,
                                         getFields("calendarId", String.valueOf(calendar.getId())),
                                         false,
                                         false,
                                         creatorIdentityId);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(eventId).getOpen());
    assertNotNull(eventUpdateReference.get());
    assertTrue(eventUpdateReference.get().hasModification(AgendaEventModificationType.OPEN_UPDATED));
  }

  /**
   * eXIP 7.3.0.20 Open Event, US02 (EXO-89478): on the event page of an
   * occurrence, the padlock patches the SERIES id with
   * {@code updateAllOccurrences = false}. That boolean is load-bearing: with
   * {@code true}, {@code updateEventFields} deletes every exceptional
   * occurrence of the series, which is the very reason the flag is a property
   * of the series (spec §5). This pins the flow the page performs: the series
   * flips and reports the toggle once, the exceptional occurrence survives
   * with its own row still false (the wire carries the parent's value,
   * {@code RestEntityBuilderOpenFlagTest}), and the page still resolves that
   * surviving row for the occurrence.
   * <p>
   * Mutation note: with the {@code updateAllOccurrences} guard removed, the
   * run dies inside {@code deleteExceptionalOccurences} (the occurrence's
   * conference rows still reference the deleted event) before the survival
   * assertions are reached; the pin kills the mutant either way.
   */
  @Test
  public void testOpenEventPatchedOnTheSeriesKeepsItsExceptionalOccurrences() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorIdentityId = Long.parseLong(testuser1Identity.getId());

    Event series = newEventInstance(start, start, true);
    series.setCalendarId(spaceCalendar.getId());
    Event createdSeries = createEvent(series.clone(), creatorIdentityId, testuser2Identity);
    long seriesId = createdSeries.getId();
    assertNotNull(createdSeries.getRecurrence());
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(seriesId).getOpen());

    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(seriesId, start);
    assertNotNull(exceptionalOccurrence);
    assertEquals(1, agendaEventService.getExceptionalOccurrenceEvents(seriesId, ZoneOffset.UTC, creatorIdentityId).size());

    // What AgendaEventAttendees.toggleOpen sends for an occurrence of the series
    eventUpdateReference.set(null);
    agendaEventService.updateEventFields(seriesId, getFields("open", "true"), false, false, creatorIdentityId);

    assertEquals(Boolean.TRUE, agendaEventService.getEventById(seriesId).getOpen());
    assertNotNull(eventUpdateReference.get());
    assertTrue(eventUpdateReference.get().hasModification(AgendaEventModificationType.OPEN_UPDATED));

    // The exceptional occurrence survives the toggle, and its own row stays
    // false: the flag is read on the series
    List<Event> exceptionalOccurrences = agendaEventService.getExceptionalOccurrenceEvents(seriesId,
                                                                                            ZoneOffset.UTC,
                                                                                            creatorIdentityId);
    assertEquals(1, exceptionalOccurrences.size());
    assertEquals(exceptionalOccurrence.getId(), exceptionalOccurrences.get(0).getId());
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(exceptionalOccurrence.getId()).getOpen());

    // The occurrence the page resolves is that surviving row, under the series
    Event pageOccurrence = agendaEventService.getEventOccurrence(seriesId, start, ZoneOffset.UTC, creatorIdentityId);
    assertNotNull(pageOccurrence);
    assertEquals(exceptionalOccurrence.getId(), pageOccurrence.getId());
    assertEquals(seriesId, pageOccurrence.getParentId());
  }

  /**
   * eXIP 7.3.0.20 Open Event, US06 (EXO-90517): one date of a series may carry
   * its own open state. A materialised date inherits the state of its series,
   * and from then on it is its own — a date opened alone does not open its
   * series, and a date left alone follows its series.
   * <p>
   * Until US06 the flag belonged to the series alone and this test asserted
   * that an occurrence row stayed false whatever was written on it. The
   * contract changed once a change on a series stopped deleting the dates
   * modified under it: before that, a value written on such a row died at the
   * next save of its series.
   */
  @Test
  public void testOpenEventFlagOfOccurrenceIsItsOwn() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long userIdentityId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    Event createdEvent = createEvent(event.clone(), userIdentityId, testuser2Identity);
    assertNotNull(createdEvent.getRecurrence());
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(createdEvent.getId()).getOpen());

    // A date materialised out of an open series inherits its state
    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(createdEvent.getId(), start);
    assertNotNull(exceptionalOccurrence);
    assertTrue(exceptionalOccurrence.getParentId() > 0);
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(exceptionalOccurrence.getId()).getOpen());

    restartTransaction();

    // and can then be locked alone, without locking its series
    Event occurrenceUpdate = agendaEventService.getEventById(exceptionalOccurrence.getId(), ZoneOffset.UTC, userIdentityId)
                                               .clone();
    occurrenceUpdate.setOpen(false);
    agendaEventService.updateEvent(occurrenceUpdate,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(exceptionalOccurrence.getId()).getOpen());
    assertEquals("locking one date must not lock its series",
                 Boolean.TRUE,
                 agendaEventService.getEventById(createdEvent.getId()).getOpen());

    // The form's "this occurrence only" creates the date through createEvent,
    // and the state it carries is applied to that date alone
    ZonedDateTime otherOccurrenceId = start.plusDays(1);
    Event formOccurrence = newEventInstance(otherOccurrenceId, otherOccurrenceId, true);
    formOccurrence.setCalendarId(spaceCalendar.getId());
    formOccurrence.setRecurrence(null);
    formOccurrence.setParentId(createdEvent.getId());
    formOccurrence.setOccurrence(new EventOccurrence(otherOccurrenceId, true, false));
    formOccurrence.setOpen(false);
    Event createdOccurrence = createEvent(formOccurrence.clone(), userIdentityId, testuser2Identity);
    assertTrue(createdOccurrence.getParentId() > 0);
    assertEquals(Boolean.FALSE, agendaEventService.getEventById(createdOccurrence.getId()).getOpen());
    assertEquals(Boolean.TRUE, agendaEventService.getEventById(createdEvent.getId()).getOpen());
    // NOTE: RestEntityBuilder.fromEvent cannot be called from here: building the
    // calendar owner's identity entity goes through the social
    // EntityBuilder.updateCachedEtagValue, which dereferences
    // ApplicationContextImpl.getCurrent() (null outside a JAX-RS request:
    // NPE on ApplicationContext.getProperties(), observed 2026-09-17). The
    // resolution rule the wire applies is pinned on its own in
    // RestEntityBuilderOpenFlagTest.
  }

  /**
   * The state a date carries of its own survives a change on its series, while
   * a date that never had one follows it — the per-property rule of the PO
   * (2026-09-23) applied to the padlock.
   */
  @Test
  public void testOpenStateCustomisedOnOneDateSurvivesASeriesChange() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(true);
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 2);
    Event lockedDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                        occurrences.get(0).getOccurrence().getId());
    Event followingDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                           occurrences.get(1).getOccurrence().getId());
    restartTransaction();

    // One date is locked on its own, the other is left as it is
    Event lockedUpdate = agendaEventService.getEventById(lockedDate.getId(), ZoneOffset.UTC, creatorId).clone();
    lockedUpdate.setOpen(false);
    agendaEventService.updateEvent(lockedUpdate,
                                   agendaEventAttendeeService.getEventAttendees(lockedDate.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    // The organiser then corrects the summary of the whole series
    Event seriesToUpdate = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    seriesToUpdate.setSummary("weekly sync");
    agendaEventService.updateEvent(seriesToUpdate,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);

    assertEquals("the date locked on purpose stays locked",
                 Boolean.FALSE,
                 agendaEventService.getEventById(lockedDate.getId()).getOpen());
    assertEquals("the date that carried no state of its own stays with its series",
                 Boolean.TRUE,
                 agendaEventService.getEventById(followingDate.getId()).getOpen());
    assertEquals("and both take the change made on the series",
                 "weekly sync",
                 agendaEventService.getEventById(lockedDate.getId()).getSummary());
    // Known blind spot of a rule derived by comparison rather than recorded:
    // once the organiser locks the whole series, a date locked on purpose holds
    // the same value as one that merely followed, so re-opening the series
    // re-opens both. Recording the overridden properties on the row is what
    // would tell them apart; it is a schema change and a decision of its own.
  }

  /**
   * visibility (EXO-90322) reached the model after this merge was written, and
   * a property the merge does not name is one a series change never reaches on
   * a customised date — where the rows used to be recreated from the series and
   * inherited it. It decides what a published calendar link shows, so a date
   * left more visible than its series would leak through the feed. Both entry
   * points are exercised: the full save and the patch on every occurrence.
   */
  @Test
  public void testVisibilityOfTheSeriesReachesTheDatesIndividuallyModified() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 2);
    Event customisedDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                            occurrences.get(0).getOccurrence().getId());
    Event followingDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                           occurrences.get(1).getOccurrence().getId());
    restartTransaction();

    // One date is made private on its own
    Event customisedUpdate = agendaEventService.getEventById(customisedDate.getId(), ZoneOffset.UTC, creatorId).clone();
    customisedUpdate.setVisibility(EventVisibility.PRIVATE);
    agendaEventService.updateEvent(customisedUpdate,
                                   agendaEventAttendeeService.getEventAttendees(customisedDate.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    restartTransaction();

    // The organiser then makes the whole series public
    Event seriesToUpdate = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    seriesToUpdate.setVisibility(EventVisibility.PUBLIC);
    agendaEventService.updateEvent(seriesToUpdate,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);

    assertEquals("the date that carried no visibility of its own follows the series",
                 EventVisibility.PUBLIC,
                 agendaEventService.getEventById(followingDate.getId()).getVisibility());
    assertEquals("the date made private on purpose stays private",
                 EventVisibility.PRIVATE,
                 agendaEventService.getEventById(customisedDate.getId()).getVisibility());

    // The patch entry point is the second face of the same door
    agendaEventService.updateEventFields(series.getId(),
                                         getFields("visibility", EventVisibility.DEFAULT.name()),
                                         true,
                                         false,
                                         creatorId);
    assertEquals("a patch applied to every occurrence reaches the date that followed",
                 EventVisibility.DEFAULT,
                 agendaEventService.getEventById(followingDate.getId()).getVisibility());
    assertEquals("and still leaves the customised date alone",
                 EventVisibility.PRIVATE,
                 agendaEventService.getEventById(customisedDate.getId()).getVisibility());

    // The direction the harm names: the series made LESS visible, and the date
    // that followed it must not be left more visible than its series
    Event seriesToMask = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    seriesToMask.setVisibility(EventVisibility.PRIVATE);
    agendaEventService.updateEvent(seriesToMask,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    assertEquals("a date that followed its series is not left more visible than it",
                 EventVisibility.PRIVATE,
                 agendaEventService.getEventById(followingDate.getId()).getVisibility());
  }

  /**
   * Legacy data. VISIBILITY is nullable by design, and a database whose
   * addColumn does not backfill leaves every pre-1.0.0-44 row null. The first
   * full save of such a date writes DEFAULT onto its row while its series stays
   * null; compared as two different values, that date would read as customised
   * and stay published when the series is made private — the organiser never
   * chose DEFAULT for it, updateEvent's normalisation did. Null means "not
   * masked" everywhere the column is honoured, so the merge reads it as
   * DEFAULT. The legacy state is reproduced through the storage, the one writer
   * that passes a null through.
   */
  @Test
  public void testLegacyNullVisibilityIsMergedAsDefault() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 1);
    Event legacyDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                        occurrences.get(0).getOccurrence().getId());
    restartTransaction();

    // The legacy state, written past the service: series and date both null
    Event nullSeries = agendaEventStorage.getEventById(series.getId());
    nullSeries.setVisibility(null);
    agendaEventStorage.updateEvent(nullSeries);
    Event nullDate = agendaEventStorage.getEventById(legacyDate.getId());
    nullDate.setVisibility(null);
    agendaEventStorage.updateEvent(nullDate);
    restartTransaction();
    assertNull("precondition: the stored series carries a legacy null",
               agendaEventStorage.getEventById(series.getId()).getVisibility());
    assertNull("precondition: so does the stored date",
               agendaEventStorage.getEventById(legacyDate.getId()).getVisibility());

    // A plain edit of that date — its room — normalises its own row to DEFAULT
    Event dateUpdate = agendaEventService.getEventById(legacyDate.getId(), ZoneOffset.UTC, creatorId).clone();
    dateUpdate.setLocation("room B");
    agendaEventService.updateEvent(dateUpdate,
                                   agendaEventAttendeeService.getEventAttendees(legacyDate.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    restartTransaction();
    assertEquals("precondition: the edited date now reads DEFAULT",
                 EventVisibility.DEFAULT,
                 agendaEventService.getEventById(legacyDate.getId()).getVisibility());
    assertNull("precondition: while its series is still null",
               agendaEventStorage.getEventById(series.getId()).getVisibility());

    // The organiser makes the series private
    Event seriesToMask = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    seriesToMask.setVisibility(EventVisibility.PRIVATE);
    agendaEventService.updateEvent(seriesToMask,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);

    assertEquals("a date the organiser never made public follows its series into private",
                 EventVisibility.PRIVATE,
                 agendaEventService.getEventById(legacyDate.getId()).getVisibility());
  }

  /**
   * The mirror of the test above, and the reason the merged value is clamped
   * rather than written as it comes out of the merge: a date opened on its own
   * keeps that value through a change of the series — that is the rule — but
   * not through one that makes the event ineligible. Turning the series into a
   * date poll locks the series itself; a date left open would then be a state
   * the invariant forbids, on the very flag that decides who may answer.
   */
  @Test
  public void testDateOpenedOnItsOwnIsLockedWhenTheSeriesBecomesADatePoll() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    event.setOpen(false);
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 1);
    Event openedDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                        occurrences.get(0).getOccurrence().getId());
    restartTransaction();

    // That one date is opened, while its series stays locked
    Event openedUpdate = agendaEventService.getEventById(openedDate.getId(), ZoneOffset.UTC, creatorId).clone();
    openedUpdate.setOpen(true);
    agendaEventService.updateEvent(openedUpdate,
                                   agendaEventAttendeeService.getEventAttendees(openedDate.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    long spaceMemberNotInvited = Long.parseLong(testuser3Identity.getId());
    assertEquals("precondition: the date carries an open state of its own",
                 Boolean.TRUE,
                 agendaEventService.getEventById(openedDate.getId()).getOpen());
    assertTrue("precondition: while it is open, a member of the space may answer on that date",
               agendaEventAttendeeService.canRespondToEvent(agendaEventService.getEventById(openedDate.getId()),
                                                            spaceMemberNotInvited));
    restartTransaction();

    // The organiser then turns the series into a date poll
    Event seriesToUpdate = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    List<EventDateOption> dateOptions =
                                      Arrays.asList(new EventDateOption(0, 0, start, start.plusHours(1), false, false, null),
                                                    new EventDateOption(0,
                                                                        0,
                                                                        start.plusDays(1),
                                                                        start.plusDays(1).plusHours(1),
                                                                        false,
                                                                        false,
                                                                        null));
    agendaEventService.updateEvent(seriesToUpdate,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   dateOptions,
                                   null,
                                   false,
                                   creatorId);

    assertEquals("the series itself is locked by the invariant",
                 Boolean.FALSE,
                 agendaEventService.getEventById(series.getId()).getOpen());
    Event storedDate = agendaEventService.getEventById(openedDate.getId());
    assertNotNull("the date individually modified is still there", storedDate);
    assertEquals("and the state it carried is clamped with it: an open date poll is another eXip",
                 Boolean.FALSE,
                 storedDate.getOpen());
    assertFalse("so the member of the space who could answer a moment ago no longer can",
                agendaEventAttendeeService.canRespondToEvent(storedDate, spaceMemberNotInvited));
  }

  /** A link back to the event in eXo, of the shape NotificationUtils mints. */
  private static final String EVENT_LINK      = "http://localhost:8080/portal/dw/agenda?eventId=42";

  /** The video call, which is a different thing and has its own property. */
  private static final String CONFERENCE_LINK = "https://meet.example.com/room";


  @Test
  public void testCreateEvent() throws Exception { // NOSONAR
    try {
      Event event = new Event();
      event.setId(0);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     0l);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.createEvent(null,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(2);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(0);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now().plusDays(1));
      event.setEnd(ZonedDateTime.now());
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      long userIdentityId = 2000l;
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     userIdentityId);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(12);
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser4Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      spaceService.addRedactor(space, testuser1Identity.getRemoteId());

      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.createEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
    } catch (AgendaException e) {
      fail(e.getMessage());
    }

    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    LocalDate untilDate = event.getRecurrence().getUntil();
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser2Identity,
                                     testuser3Identity);

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);
    AgendaEventModification eventModification = eventCreationReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ADDED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 1,
                 eventModification.getModificationTypes().size());

    assertEquals(event.getSummary(), createdEvent.getSummary());
    assertEquals(event.getDescription(), createdEvent.getDescription());
    assertEquals(event.getCalendarId(), createdEvent.getCalendarId());
    assertEquals(event.getColor(), createdEvent.getColor());
    assertEquals(event.getStart().toLocalDate(), createdEvent.getStart().toLocalDate());
    assertEquals(event.getEnd().toLocalDate(), createdEvent.getEnd().toLocalDate());
    assertEquals(event.getLocation(), createdEvent.getLocation());
    assertEquals(Long.parseLong(testuser1Identity.getId()), createdEvent.getCreatorId());
    assertEquals(event.getAvailability(), createdEvent.getAvailability());
    assertEquals(event.getOccurrence(), createdEvent.getOccurrence());
    assertNotNull(createdEvent.getAcl());
    assertTrue(createdEvent.getAcl().isCanEdit());
    assertFalse(createdEvent.getAcl().isAttendee());

    assertNotNull(createdEvent.getCreated());
    assertNull(createdEvent.getUpdated());
    assertEquals(event.getModifierId(), createdEvent.getModifierId());

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertNotNull(createdEventRecurrence.getUntil());
    assertEquals(untilDate,
                 createdEventRecurrence.getUntil());
    assertEquals(createdEvent.getRecurrence().getOverallEnd().toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());

    assertNotNull(createdEventRecurrence.getOverallStart());
    assertEquals(start.toLocalDate(),
                 createdEventRecurrence.getOverallStart().toLocalDate());

    assertNotNull(createdEventRecurrence.getOverallEnd());
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getOverallEnd().toLocalDate());
  }

  @Test
  public void testCreateEvent_InSpace_AsMember() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setCalendarId(spaceCalendar.getId());
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser2Identity,
                                     testuser3Identity);

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);
    AgendaEventModification eventModification = eventCreationReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ADDED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 1,
                 eventModification.getModificationTypes().size());

    try {
      event = newEventInstance(start, start, allDay);
      event.setCalendarId(spaceCalendar.getId());
      createEvent(event.clone(), Long.parseLong(testuser5Identity.getId()), testuser2Identity, testuser3Identity);
      fail("testuser5 is not member of space and shouldn't be able to create an event");
    } catch (IllegalAccessException e) {
      // Expected
    }
  }

  @Test
  public void testCreateEvent_InSpace_AsManager() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;
    spaceService.addRedactor(space, testuser1Identity.getRemoteId());
    spaceService.setManager(space, testuser2Identity.getRemoteId(), true);
    Event event = newEventInstance(start, start, allDay);
    event.setCalendarId(spaceCalendar.getId());
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser2Identity.getId()),
                                     testuser3Identity);

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);
    AgendaEventModification eventModification = eventCreationReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ADDED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 1,
                 eventModification.getModificationTypes().size());
  }

  @Test
  public void testGetEventById() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    try {
      agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser3Identity.getId()));
      fail("Should fail when a non attendee attempts to access event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser3Identity.getId()));
      fail("Should fail when a non attendee attempts to access event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event createdEvent = agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    assertEquals(event.getSummary(), createdEvent.getSummary());
    assertEquals(event.getDescription(), createdEvent.getDescription());
    assertEquals(event.getCalendarId(), createdEvent.getCalendarId());
    assertEquals(event.getColor(), createdEvent.getColor());
    assertEquals(event.getLocation(), createdEvent.getLocation());
    assertEquals(event.getCreatorId(), createdEvent.getCreatorId());
    assertEquals(event.getAvailability(), createdEvent.getAvailability());
    assertEquals(event.getOccurrence(), createdEvent.getOccurrence());
    assertEquals(event.getStart().withZoneSameLocal(ZoneOffset.UTC), createdEvent.getStart().withZoneSameLocal(ZoneOffset.UTC));
    assertEquals(event.getEnd().withZoneSameLocal(ZoneOffset.UTC), createdEvent.getEnd().withZoneSameLocal(ZoneOffset.UTC));
    assertNotNull(createdEvent.getAcl());
    assertFalse(createdEvent.getAcl().isCanEdit());
    assertTrue(createdEvent.getAcl().isAttendee());

    assertNotNull(createdEvent.getCreated());
    assertNull(createdEvent.getUpdated());
    assertEquals(0, createdEvent.getModifierId());

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(createdEvent.getRecurrence().getOverallEnd().toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());

    assertNotNull(createdEventRecurrence.getOverallStart());
    assertEquals(start.toLocalDate(),
                 createdEventRecurrence.getOverallStart().toLocalDate());

    assertNotNull(createdEventRecurrence.getOverallEnd());
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getOverallEnd().toLocalDate());
  }

  @Test
  public void testGetEventById_Recurrent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    ZonedDateTime end = getDate().withNano(0).plusHours(2);

    boolean allDay = false;

    Event event = newEventInstance(start, end, allDay);
    Event createdEvent = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);
    createdEvent = agendaEventService.getEventById(createdEvent.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    assertEquals(event.getSummary(), createdEvent.getSummary());
    assertEquals(event.getDescription(), createdEvent.getDescription());
    assertEquals(event.getCalendarId(), createdEvent.getCalendarId());
    assertEquals(event.getColor(), createdEvent.getColor());
    assertEquals(event.getLocation(), createdEvent.getLocation());
    assertEquals(Long.parseLong(testuser1Identity.getId()), createdEvent.getCreatorId());
    assertEquals(event.getAvailability(), createdEvent.getAvailability());
    assertEquals(event.getOccurrence(), createdEvent.getOccurrence());
    assertEquals(event.getStart().withZoneSameInstant(ZoneOffset.UTC),
                 createdEvent.getStart().withZoneSameInstant(ZoneOffset.UTC));
    assertEquals(event.getEnd().withZoneSameInstant(ZoneOffset.UTC), createdEvent.getEnd().withZoneSameInstant(ZoneOffset.UTC));

    assertNotNull(createdEvent.getCreated());
    assertNull(createdEvent.getUpdated());
    assertEquals(0, createdEvent.getModifierId());

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertNotNull(createdEventRecurrence.getUntil());

    assertEquals(end.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(createdEventRecurrence.getUntil(),
                 createdEvent.getRecurrence().getOverallEnd().withZoneSameInstant(ZoneOffset.UTC).toLocalDate());

    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());

    assertNotNull(createdEventRecurrence.getOverallStart());
    assertEquals(start.toLocalDate(),
                 createdEventRecurrence.getOverallStart().toLocalDate());

    assertNotNull(createdEventRecurrence.getOverallEnd());
    assertEquals(end.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getOverallEnd().toLocalDate());
    assertEquals(end.plusDays(2).getHour(),
                 createdEventRecurrence.getOverallEnd().getHour());
    assertEquals(end.plusDays(2).getMinute(),
                 createdEventRecurrence.getOverallEnd().getMinute());
    assertEquals(end.plusDays(2).getSecond(),
                 createdEventRecurrence.getOverallEnd().getSecond());
  }

  @Test
  public void testGetEventById_RecurrenceAttributes() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.getRecurrence().setType(EventRecurrenceType.YEARLY);
    event.getRecurrence().setFrequency(EventRecurrenceFrequency.YEARLY);
    event.getRecurrence().setBySecond(Collections.singletonList("1"));
    event.getRecurrence().setByMinute(Collections.singletonList("1"));
    event.getRecurrence().setByHour(Collections.singletonList("1"));
    event.getRecurrence().setByDay(Collections.singletonList("TU"));
    event.getRecurrence().setByMonthDay(Collections.singletonList("2"));
    event.getRecurrence().setByMonth(Collections.singletonList("3"));
    event.getRecurrence().setByWeekNo(Collections.singletonList("30"));
    event.getRecurrence().setByYearDay(Collections.singletonList("165"));
    event.getRecurrence().setBySetPos(Collections.singletonList("-1"));
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    Event createdEvent = agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
  }

  @Test
  public void testGetEventById_Recurrent_AllDayEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    try {
      agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser3Identity.getId()));
      fail("Should fail when a non attendee attempts to access event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event createdEvent = agendaEventService.getEventById(event.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    assertEquals(event.getSummary(), createdEvent.getSummary());
    assertEquals(event.getDescription(), createdEvent.getDescription());
    assertEquals(event.getCalendarId(), createdEvent.getCalendarId());
    assertEquals(event.getColor(), createdEvent.getColor());
    assertEquals(event.getLocation(), createdEvent.getLocation());
    assertEquals(event.getCreatorId(), createdEvent.getCreatorId());
    assertEquals(event.getAvailability(), createdEvent.getAvailability());
    assertEquals(event.getOccurrence(), createdEvent.getOccurrence());
    assertEquals(event.getStart().withZoneSameLocal(ZoneOffset.UTC), createdEvent.getStart().withZoneSameLocal(ZoneOffset.UTC));
    assertEquals(event.getEnd().withZoneSameLocal(ZoneOffset.UTC), createdEvent.getEnd().withZoneSameLocal(ZoneOffset.UTC));
    assertEquals(event.getStart().toLocalDate(), createdEvent.getEnd().toLocalDate());

    assertNotNull(createdEvent.getCreated());
    assertNull(createdEvent.getUpdated());
    assertEquals(0, createdEvent.getModifierId());

    EventRecurrence createdEventRecurrence = createdEvent.getRecurrence();
    assertNotNull(createdEventRecurrence);
    assertTrue(createdEventRecurrence.getId() > 0);

    EventRecurrence eventRecurrence = event.getRecurrence();
    assertEquals(eventRecurrence.getFrequency(), createdEventRecurrence.getFrequency());
    assertEquals(eventRecurrence.getType(), createdEventRecurrence.getType());
    assertEquals(eventRecurrence.getInterval(), createdEventRecurrence.getInterval());
    assertTrue(createdEventRecurrence.getCount() == 0);
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(createdEvent.getRecurrence().getOverallEnd().toLocalDate(),
                 createdEventRecurrence.getUntil());
    assertEquals(eventRecurrence.getBySecond(), createdEventRecurrence.getBySecond());
    assertEquals(eventRecurrence.getByMinute(), createdEventRecurrence.getByMinute());
    assertEquals(eventRecurrence.getByHour(), createdEventRecurrence.getByHour());
    assertEquals(eventRecurrence.getByDay(), createdEventRecurrence.getByDay());
    assertEquals(eventRecurrence.getByMonthDay(), createdEventRecurrence.getByMonthDay());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());
    assertEquals(eventRecurrence.getByMonth(), createdEventRecurrence.getByMonth());
    assertEquals(eventRecurrence.getByYearDay(), createdEventRecurrence.getByYearDay());

    assertNotNull(createdEventRecurrence.getOverallStart());
    assertEquals(start.toLocalDate(),
                 createdEventRecurrence.getOverallStart().toLocalDate());

    assertNotNull(createdEventRecurrence.getOverallEnd());
    assertEquals(start.plusDays(2).toLocalDate(),
                 createdEventRecurrence.getOverallEnd().toLocalDate());
  }

  @Test
  public void testCreateEventExceptionalOccurrence() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     start.plusDays(2).toLocalDate(),
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
    event.setRecurrence(recurrence);

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    try {
      agendaEventService.createEventExceptionalOccurrence(5500l,
                                                          ATTENDEES,
                                                          CONFERENCES,
                                                          REMINDERS,
                                                          start.plusDays(2));
    } catch (AgendaException e) {
      // Expected, not existing id
    }

    try {
      agendaEventService.createEventExceptionalOccurrence(event.getId(),
                                                          ATTENDEES,
                                                          CONFERENCES,
                                                          REMINDERS,
                                                          start.plusDays(3));
    } catch (IllegalStateException e) {
      // Expected, not existing occurrence
    }

    try {
      agendaEventService.createEventExceptionalOccurrence(event.getId(),
                                                          ATTENDEES,
                                                          CONFERENCES,
                                                          REMINDERS,
                                                          start.minusDays(1));
    } catch (IllegalStateException e) {
      // Expected, not existing occurrence
    }

    ZonedDateTime occurrenceId = start.plusDays(1);
    Event exceptionalOccurrence = agendaEventService.createEventExceptionalOccurrence(event.getId(),
                                                                                      ATTENDEES,
                                                                                      CONFERENCES,
                                                                                      REMINDERS,
                                                                                      occurrenceId);

    assertNotNull(exceptionalOccurrence);
    assertNotNull(exceptionalOccurrence.getOccurrence());
    assertEquals(occurrenceId.withZoneSameInstant(ZoneOffset.UTC).toLocalDate(),
                 exceptionalOccurrence.getOccurrence().getId().withZoneSameInstant(ZoneOffset.UTC).toLocalDate());

    long eventId = exceptionalOccurrence.getId();
    assertTrue(eventId > 0);
    assertEquals(event.getId(), exceptionalOccurrence.getParentId());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertTrue(eventAttendees != null && !eventAttendees.isEmpty());

    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    assertTrue(eventConferences != null && !eventConferences.isEmpty());

    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    List<EventReminder> eventReminders = agendaEventReminderService.getEventReminders(eventId, userIdentityId);
    assertTrue(eventReminders != null && !eventReminders.isEmpty());
  }

  @Test
  public void testSaveEventExceptionalOccurrenceWithDST() throws Exception { // NOSONAR
    ZoneId dstTimeZone = ZoneId.of("Europe/Paris");
    ZonedDateTime start = ZonedDateTime.of(2021, 9, 15, 10, 0, 0, 0, dstTimeZone);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(dstTimeZone);
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     null,
                                                     0,
                                                     EventRecurrenceType.WEEKLY,
                                                     EventRecurrenceFrequency.WEEKLY,
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
    event.setRecurrence(recurrence);

    event = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    ZonedDateTime periodStart = ZonedDateTime.of(2021, 12, 3, 0, 0, 0, 0, dstTimeZone);
    List<Event> eventOccurrencesInPeriod = agendaEventService.getEventOccurrencesInPeriod(event, periodStart, periodStart.plusWeeks(1), dstTimeZone, 0);
    assertNotNull(eventOccurrencesInPeriod);
    assertEquals(1, eventOccurrencesInPeriod.size());
    Event occurrence = eventOccurrencesInPeriod.get(0);
    assertNotNull(occurrence);
    assertNotNull(occurrence.getOccurrence());

    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(event.getId(), occurrence.getOccurrence().getId());
    ZonedDateTime exceptionalEventStart = exceptionalOccurrence.getStart().withZoneSameInstant(dstTimeZone);

    assertNotNull(exceptionalOccurrence);
    assertNotNull(exceptionalOccurrence.getOccurrence());
    assertEquals(LocalDate.of(2021, 12, 8),
                 exceptionalEventStart.toLocalDate());
    assertEquals(start.getHour(),
                 exceptionalEventStart.getHour());
    assertEquals(start.getMinute(),
                 exceptionalEventStart.getMinute());
  }

  @Test
  public void testBusyEventsAreOnlyAcceptedOnes() throws Exception { // NOSONAR
    // Backs the MCP free/busy primitive (get_availability / suggest_meeting_time / conflicts): a user is busy only for
    // events they ACCEPTED. An invited-but-not-yet-accepted event must NOT make them busy; accepting flips it to busy.
    ZonedDateTime start = getDate().withNano(0);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setRecurrence(null); // single (non-recurring) event
    event = createEvent(event.clone(),
                        Long.parseLong(testuser1Identity.getId()),
                        testuser1Identity,
                        testuser2Identity);

    long user2 = Long.parseLong(testuser2Identity.getId());
    EventFilter acceptedFilter = new EventFilter(user2,
                                                 null,
                                                 Collections.singletonList(EventAttendeeResponse.ACCEPTED),
                                                 start.minusHours(1),
                                                 start.plusHours(2),
                                                 10);
    // testuser2 was only invited -> NEEDS_ACTION -> not busy
    List<Event> before = agendaEventService.getEvents(acceptedFilter.clone(), ZoneOffset.UTC, user2);
    assertTrue("an invited-but-not-accepted event must NOT count as busy", before.isEmpty());

    // testuser2 accepts -> now busy
    agendaEventAttendeeService.sendEventResponse(event.getId(), user2, EventAttendeeResponse.ACCEPTED);
    List<Event> after = agendaEventService.getEvents(acceptedFilter.clone(), ZoneOffset.UTC, user2);
    assertEquals("an accepted event must count as busy", 1, after.size());
  }

  @Test
  public void testCancelSingleOccurrenceKeepsSurroundingOccurrences() throws Exception { // NOSONAR
    // Regression for EXO-88461: cancelling ONE occurrence of a recurring series must remove only that date and keep
    // every occurrence before AND after it (the MCP cancel_agenda_event(occurrence_id) bug used to drop this-and-future).
    ZoneId timeZone = ZoneOffset.UTC;
    ZonedDateTime start = ZonedDateTime.of(2022, 3, 7, 10, 0, 0, 0, timeZone);
    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setTimeZoneId(timeZone);
    // Weekly, 6 occurrences
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     null,
                                                     6,
                                                     EventRecurrenceType.WEEKLY,
                                                     EventRecurrenceFrequency.WEEKLY,
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
    event.setRecurrence(recurrence);
    event = createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusWeeks(6).plusDays(1);
    List<Event> before = agendaEventService.getEventOccurrencesInPeriod(event, periodStart, periodEnd, timeZone, 0);
    assertEquals("expected 6 weekly occurrences before cancellation", 6, before.size());
    LocalDate secondDate = before.get(1).getStart().withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    LocalDate thirdDate = before.get(2).getStart().withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    LocalDate fourthDate = before.get(3).getStart().withZoneSameInstant(ZoneOffset.UTC).toLocalDate();

    // Cancel ONLY the 3rd occurrence exactly as the MCP tool does: materialize the exceptional occurrence, then
    // set its status to CANCELLED.
    Event exceptional = agendaEventService.saveEventExceptionalOccurrence(event.getId(),
                                                                          before.get(2).getOccurrence().getId());
    agendaEventService.updateEventFields(exceptional.getId(),
                                         Collections.singletonMap("status",
                                                                  Collections.singletonList(EventStatus.CANCELLED.name())),
                                         false,
                                         false,
                                         Long.parseLong(testuser1Identity.getId()));

    // The cancelled exceptional occurrence must be persisted as CANCELLED (so it is hidden from listings)
    assertEquals(EventStatus.CANCELLED, agendaEventService.getEventById(exceptional.getId()).getStatus());

    List<Event> after = agendaEventService.getEventOccurrencesInPeriod(event, periodStart, periodEnd, timeZone, 0);
    List<LocalDate> remainingDates = after.stream()
                                          .map(o -> o.getStart().withZoneSameInstant(ZoneOffset.UTC).toLocalDate())
                                          .toList();
    assertEquals("expected 5 occurrences after cancelling one", 5, after.size());
    assertFalse("cancelled 3rd occurrence must be gone", remainingDates.contains(thirdDate));
    // The occurrences surrounding the cancelled one (before AND after it) must still exist
    assertTrue("occurrence before the cancelled date must remain", remainingDates.contains(secondDate));
    assertTrue("occurrence after the cancelled date must remain", remainingDates.contains(fourthDate));
  }

  @Test
  public void testGetParentRecurrentEvents() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     start.plusDays(2).toLocalDate(),
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
    event.setRecurrence(recurrence);

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);
    List<Event> parentRecurrentEvents = agendaEventService.getParentRecurrentEvents(start,
                                                                                    start.plusDays(2),
                                                                                    ZoneId.systemDefault());
    assertNotNull(parentRecurrentEvents);
    assertEquals(1, parentRecurrentEvents.size());

    Event exceptionalOccurrence = agendaEventService.createEventExceptionalOccurrence(event.getId(),
                                                                                      ATTENDEES,
                                                                                      CONFERENCES,
                                                                                      REMINDERS,
                                                                                      start.plusDays(1));

    assertNotNull(exceptionalOccurrence);
    parentRecurrentEvents = agendaEventService.getParentRecurrentEvents(start,
                                                                        start.plusDays(2),
                                                                        ZoneId.systemDefault());
    assertNotNull(parentRecurrentEvents);
    assertEquals(1, parentRecurrentEvents.size());
  }

  @Test
  public void testGetOccurrenceFromRecurrentEventByCount() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);

    int count = 3;
    int interval = 1;
    LocalDate untilDate = start.plusDays(6).toLocalDate();
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     untilDate,
                                                     count,
                                                     EventRecurrenceType.DAILY,
                                                     EventRecurrenceFrequency.DAILY,
                                                     interval,
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
    event.setRecurrence(recurrence);

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    ZonedDateTime occurrenceDate = event.getStart().withZoneSameInstant(event.getTimeZoneId());
    Event eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                                  occurrenceDate,
                                                                  event.getTimeZoneId(),
                                                                  Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(interval).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(2).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(count).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(2).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);
    assertNotNull(eventOccurrence.getOccurrence());
    assertNotNull(eventOccurrence.getOccurrence().getId());
    assertTrue(eventOccurrence.getId() == 0);
    assertEquals(occurrenceDate.toLocalDate(), eventOccurrence.getOccurrence().getId().toLocalDate());

    assertNotNull(eventOccurrence.getAcl());
    assertTrue(eventOccurrence.getAcl().isCanEdit());
    assertTrue(eventOccurrence.getAcl().isAttendee());
  }

  @Test
  public void testGetOccurrenceFromRecurrentEventByInterval() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);

    int count = 0;
    int interval = 3;
    LocalDate untilDate = start.plusDays(interval * 2l).toLocalDate();
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     untilDate,
                                                     count,
                                                     EventRecurrenceType.DAILY,
                                                     EventRecurrenceFrequency.DAILY,
                                                     interval,
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
    event.setRecurrence(recurrence);

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    ZonedDateTime occurrenceDate = event.getStart().withZoneSameInstant(event.getTimeZoneId());
    Event eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                                  occurrenceDate,
                                                                  event.getTimeZoneId(),
                                                                  Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(interval).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(interval + 1l).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNull(eventOccurrence);

    occurrenceDate = event.getStart().plusDays(interval * 2l).withZoneSameInstant(event.getTimeZoneId());
    eventOccurrence = agendaEventService.getEventOccurrence(event.getId(),
                                                            occurrenceDate,
                                                            event.getTimeZoneId(),
                                                            Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);
    assertNotNull(eventOccurrence.getOccurrence());
    assertNotNull(eventOccurrence.getOccurrence().getId());
    assertTrue(eventOccurrence.getId() == 0);
    assertEquals(occurrenceDate.toLocalDate(), eventOccurrence.getOccurrence().getId().toLocalDate());

    assertNotNull(eventOccurrence.getAcl());
    assertTrue(eventOccurrence.getAcl().isCanEdit());
    assertTrue(eventOccurrence.getAcl().isAttendee());
  }

  @Test
  public void testGetExceptionalOccurrenceEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    EventRecurrence recurrence = new EventRecurrence(0,
                                                     start.plusDays(2).toLocalDate(),
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
    event.setRecurrence(recurrence);

    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);
    long eventId = event.getId();

    List<Event> exceptionalOccurrenceEvents =
                                            agendaEventService.getExceptionalOccurrenceEvents(2000l,
                                                                                              null,
                                                                                              Long.parseLong(testuser1Identity.getId()));
    assertNotNull(exceptionalOccurrenceEvents);
    assertTrue(exceptionalOccurrenceEvents.isEmpty());

    try {
      agendaEventService.getExceptionalOccurrenceEvents(eventId,
                                                        null,
                                                        Long.parseLong(testuser3Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event exceptionalOccurrence = agendaEventService.createEventExceptionalOccurrence(eventId,
                                                                                      ATTENDEES,
                                                                                      CONFERENCES,
                                                                                      REMINDERS,
                                                                                      start.plusDays(1));

    assertNotNull(exceptionalOccurrence);
    assertTrue(exceptionalOccurrence.getId() > 0);
    assertNotNull(exceptionalOccurrence.getOccurrence());
    assertNotNull(exceptionalOccurrence.getOccurrence().getId());

    exceptionalOccurrenceEvents = agendaEventService.getExceptionalOccurrenceEvents(eventId,
                                                                                    null,
                                                                                    Long.parseLong(testuser1Identity.getId()));

    assertTrue(exceptionalOccurrenceEvents.stream()
                                          .anyMatch(exceptionalOccurrenceEvent -> exceptionalOccurrenceEvent.getId() == exceptionalOccurrence.getId()));

    Event eventOccurrence = agendaEventService.getEventOccurrence(exceptionalOccurrence.getParentId(),
                                                                  exceptionalOccurrence.getOccurrence().getId(),
                                                                  ZoneId.systemDefault(),
                                                                  Long.parseLong(testuser1Identity.getId()));
    assertNotNull(eventOccurrence);
    assertEquals(eventOccurrence.getId(), exceptionalOccurrence.getId());

    assertNotNull(eventOccurrence.getAcl());
    assertTrue(eventOccurrence.getAcl().isCanEdit());
    assertTrue(eventOccurrence.getAcl().isAttendee());
  }

  @Test
  public void testUpdateEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = false;

    Event createdEvent = newEventInstance(start, start, allDay);
    createdEvent = createEvent(createdEvent.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = createdEvent.getId();
    Event storedEvent = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(storedEvent);
    assertTrue(storedEvent.getId() > 0);
    assertNotNull(storedEvent.getRecurrence());

    try {
      Event event = new Event();
      event.setParameters(createdEvent.getParameters());
      event.setId(eventId);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     0l);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEvent(null,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(0);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      event.setParentId(eventId);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now().plusDays(1));
      event.setEnd(ZonedDateTime.now());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     3000l);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      event.setRecurrence(new EventRecurrence());
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     3000l);
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(1200);
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser4Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(15000);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      event = agendaEventService.createEvent(event,
                                             Collections.singletonList(new EventAttendee(0,
                                                                                         Long.parseLong(testuser2Identity.getId()),
                                                                                         EventAttendeeResponse.ACCEPTED)),
                                             Collections.emptyList(),
                                             Collections.emptyList(),
                                             null,
                                             null,
                                             true,
                                             Long.parseLong(testuser2Identity.getId()));
      agendaEventService.updateEvent(event,
                                     Collections.singletonList(new EventAttendee(0,
                                                                                 Long.parseLong(testuser2Identity.getId()),
                                                                                 EventAttendeeResponse.ACCEPTED)),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
    } catch (Exception e) {
      fail(e.getMessage());
    }

    try {
      spaceService.addRedactor(space, testuser1Identity.getRemoteId());

      Event event = new Event();
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      event = agendaEventService.createEvent(event,
                                             Collections.singletonList(new EventAttendee(0,
                                                                                         Long.parseLong(testuser2Identity.getId()),
                                                                                         EventAttendeeResponse.ACCEPTED)),
                                             Collections.emptyList(),
                                             Collections.emptyList(),
                                             null,
                                             null,
                                             true,
                                             Long.parseLong(testuser2Identity.getId()));
      agendaEventService.updateEvent(event,
                                     Collections.singletonList(new EventAttendee(0,
                                                                                 Long.parseLong(testuser2Identity.getId()),
                                                                                 EventAttendeeResponse.ACCEPTED)),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      Event event = new Event();
      event.setId(eventId);
      event.setCalendarId(spaceCalendar.getId());
      event.setStart(ZonedDateTime.now());
      event.setEnd(ZonedDateTime.now());
      EventRecurrence recurrence = new EventRecurrence();
      event.setRecurrence(recurrence);
      recurrence.setFrequency(EventRecurrenceFrequency.DAILY);
      recurrence.setInterval(1);
      agendaEventService.updateEvent(event,
                                     Collections.singletonList(new EventAttendee(0,
                                                                                 storedEvent.getCreatorId(),
                                                                                 EventAttendeeResponse.ACCEPTED)),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     Long.parseLong(testuser2Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    createdEvent = newEventInstance(start, start, allDay);
    createdEvent.setRecurrence(null);
    createdEvent = createEvent(createdEvent.clone(),
                               Long.parseLong(testuser1Identity.getId()),
                               testuser1Identity,
                               testuser2Identity);

    eventId = createdEvent.getId();

    List<EventReminder> reminders = agendaEventReminderService.getEventReminders(eventId,
                                                                                 Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());

    EventReminder eventReminder = reminders.get(0);
    assertNotNull(eventReminder);

    storedEvent = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser2Identity.getId()));

    storedEvent.setRecurrence(null);

    agendaEventService.updateEvent(storedEvent,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));

    AgendaEventModification eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.REMINDER_DELETED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.CONFERENCE_DELETED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ATTENDEE_DELETED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 4,
                 eventModification.getModificationTypes().size());

    Event updatedEvent = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(updatedEvent);
    assertNull(updatedEvent.getRecurrence());
    assertNull(updatedEvent.getOccurrence());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertTrue(eventAttendees == null || eventAttendees.isEmpty());
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    assertTrue(eventConferences == null || eventConferences.isEmpty());

    updatedEvent.setAllowAttendeeToUpdate(true);
    updatedEvent.setAllowAttendeeToInvite(false);

    EventAttendee eventAttendeeTestUser2 = new EventAttendee(0,
                                                             updatedEvent.getId(),
                                                             Long.parseLong(testuser2Identity.getId()),
                                                             null);
    EventAttendee eventAttendeeTestUser1 = new EventAttendee(0,
                                                             updatedEvent.getId(),
                                                             Long.parseLong(testuser1Identity.getId()),
                                                             null);
    updatedEvent = agendaEventService.updateEvent(updatedEvent,
                                                  Arrays.asList(eventAttendeeTestUser1, eventAttendeeTestUser2),
                                                  null,
                                                  reminders,
                                                  null,
                                                  null,
                                                  false,
                                                  Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.REMINDER_ADDED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ALLOW_MODIFY_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ATTENDEE_ADDED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 4,
                 eventModification.getModificationTypes().size());

    try {
      updatedEvent = agendaEventService.updateEvent(updatedEvent,
                                                    Arrays.asList(eventAttendeeTestUser1, eventAttendeeTestUser2),
                                                    null,
                                                    null,
                                                    null,
                                                    null,
                                                    false,
                                                    Long.parseLong(testuser3Identity.getId()));
      fail("shouldn't allow other attendee to update event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    assertTrue(updatedEvent.isAllowAttendeeToUpdate());
    assertTrue("allowAttendeeToInvite should be true automatically when allowAttendeeToUpdate is set to true",
               updatedEvent.isAllowAttendeeToInvite());

    reminders = agendaEventReminderService.getEventReminders(eventId, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());
    EventReminder sameEventReminder = reminders.get(0);
    assertNotNull(sameEventReminder);
    assertEquals(eventReminder.getDatetime(), sameEventReminder.getDatetime());

    updatedEvent.setAllowAttendeeToUpdate(false);
    updatedEvent.setStart(updatedEvent.getStart().plusDays(1));
    updatedEvent.setEnd(updatedEvent.getEnd().plusDays(1));
    updatedEvent = agendaEventService.updateEvent(updatedEvent,
                                                  Arrays.asList(eventAttendeeTestUser1, eventAttendeeTestUser2),
                                                  null,
                                                  null,
                                                  null,
                                                  null,
                                                  false,
                                                  Long.parseLong(testuser2Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ALLOW_MODIFY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 4,
                 eventModification.getModificationTypes().size());

    assertTrue("Attendees shouldn't be able to modify allowAttendeeToInvite and allowAttendeeToUpdate",
               updatedEvent.isAllowAttendeeToUpdate());
    assertTrue(updatedEvent.isAllowAttendeeToInvite());

    reminders = agendaEventReminderService.getEventReminders(eventId, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());
    sameEventReminder = reminders.get(0);
    assertNotNull(sameEventReminder);
    assertEquals(sameEventReminder.getDatetime(), eventReminder.getDatetime().plusDays(1));
  }

  @Test
  public void testUpdateEventFields() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event createdEvent = newEventInstance(start, start, allDay);
    createdEvent = createEvent(createdEvent.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = createdEvent.getId();
    Event storedEvent = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(storedEvent);
    assertTrue(storedEvent.getId() > 0);
    assertNotNull(storedEvent.getRecurrence());

    try {
      Event event = new Event();
      event.setId(eventId);
      agendaEventService.updateEvent(event,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     true,
                                     0l);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    Map<String, List<String>> fields = getFields("summary", "fieldValue");
    try {
      agendaEventService.updateEventFields(0,
                                           fields,
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           fields,
                                           true,
                                           true,
                                           0l);
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           fields,
                                           true,
                                           true,
                                           2000l);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           fields,
                                           true,
                                           true,
                                           Long.parseLong(testuser5Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", "-1"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", "500000"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("timeZoneId", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("timeZoneId", "Not existant"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (Exception e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("start", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("end", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("start", "2080-10-10"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("end", "2010-10-10"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (AgendaException e) {
      // Expected
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("recurrence", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected: an unsupported field is a caller error (400), was 500
      assertEquals("agenda.eventFieldNotSupported", e.getMessage());
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("id", "2553"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected: an unsupported field is a caller error (400), was 500
      assertEquals("agenda.eventFieldNotSupported", e.getMessage());
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("parentId", "2553"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected: an unsupported field is a caller error (400), was 500
      assertEquals("agenda.eventFieldNotSupported", e.getMessage());
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("created", "2020-10-10"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected: an unsupported field is a caller error (400), was 500
      assertEquals("agenda.eventFieldNotSupported", e.getMessage());
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("occurrence", "2020-10-10"),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected: an unsupported field is a caller error (400), was 500
      assertEquals("agenda.eventFieldNotSupported", e.getMessage());
    }

    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("acl", ""),
                                           true,
                                           true,
                                           Long.parseLong(testuser1Identity.getId()));
      fail();
    } catch (IllegalArgumentException e) {
      // Expected: an unsupported field is a caller error (400), was 500
      assertEquals("agenda.eventFieldNotSupported", e.getMessage());
    }

    createdEvent = newEventInstance(start, start, allDay);
    createdEvent = createEvent(createdEvent.clone(),
                               Long.parseLong(testuser1Identity.getId()),
                               testuser1Identity,
                               testuser2Identity,
                               testuser3Identity);
    eventId = createdEvent.getId();

    List<EventReminder> reminders = agendaEventReminderService.getEventReminders(eventId,
                                                                                 Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());

    EventReminder eventReminder = reminders.get(0);
    assertNotNull(eventReminder);

    String fieldName = "calendarId";
    String fieldValue = String.valueOf(spaceCalendar.getId());
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    AgendaEventModification eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.OWNER_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    Event event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getCalendarId()));

    fieldName = "summary";
    fieldValue = "summaryValue";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.SUMMARY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getSummary()));

    fieldName = "description";
    fieldValue = "descriptionValue";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.DESCRIPTION_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getDescription()));

    fieldName = "location";
    fieldValue = "locationValue";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.LOCATION_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getLocation()));

    fieldName = "color";
    fieldValue = "colorValue";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.COLOR_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.getColor()));

    fieldName = "timeZoneId";
    fieldValue = "Europe/Paris";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.TIMEZONE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getTimeZoneId().getId());

    reminders = agendaEventReminderService.getEventReminders(eventId, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());
    EventReminder sameEventReminder = reminders.get(0);
    assertNotNull(sameEventReminder);
    assertEquals(sameEventReminder.getDatetime(), eventReminder.getDatetime());

    fieldName = "start";
    fieldValue = AgendaDateUtils.toRFC3339Date(start.minusDays(1), ZoneId.systemDefault(), allDay);
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, AgendaDateUtils.toRFC3339Date(event.getStart(), ZoneId.systemDefault(), allDay));

    reminders = agendaEventReminderService.getEventReminders(eventId, Long.parseLong(testuser1Identity.getId()));
    assertNotNull(reminders);
    assertEquals(1, reminders.size());

    EventReminder updatedEventReminder = reminders.get(0);
    assertNotNull(updatedEventReminder);
    assertNotEquals(updatedEventReminder.getDatetime(), eventReminder.getDatetime());

    fieldName = "end";
    fieldValue = AgendaDateUtils.toRFC3339Date(start.plusDays(2), ZoneId.systemDefault(), allDay);
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, AgendaDateUtils.toRFC3339Date(event.getEnd(), ZoneId.systemDefault(), allDay));

    fieldName = "allDay";
    fieldValue = String.valueOf(!allDay);
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.START_DATE_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.END_DATE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 3,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.isAllDay()));

    fieldName = "availability";
    fieldValue = EventAvailability.BUSY.name();
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.AVAILABILITY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getAvailability().name());

    // EXO-90322: the same walk for the visibility, because this is the one path
    // that can un-mask a private event — a blank value resets it to DEFAULT —
    // and because the modification set is what decides whether a connector
    // rewrites every attendee's synchronised copy
    fieldName = "visibility";
    fieldValue = EventVisibility.PRIVATE.name();
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.VISIBILITY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getVisibility().name());

    fieldValue = "";
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    event = agendaEventService.getEventById(eventId);
    assertEquals("a blank value resets the visibility to DEFAULT, which publishes in full",
                 EventVisibility.DEFAULT,
                 event.getVisibility());

    fieldName = "status";
    fieldValue = EventStatus.TENTATIVE.name();
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.STATUS_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.SWITCHED_EVENT_TO_DATE_POLL));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 3,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getStatus().name());

    fieldName = "status";
    fieldValue = EventStatus.CONFIRMED.name();
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.STATUS_UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.SWITCHED_DATE_POLL_TO_EVENT));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 3,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, event.getStatus().name());

    fieldName = "allowAttendeeToUpdate";
    fieldValue = String.valueOf(!event.isAllowAttendeeToUpdate());
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ALLOW_MODIFY_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.isAllowAttendeeToUpdate()));

    fieldName = "allowAttendeeToInvite";
    fieldValue = String.valueOf(!event.isAllowAttendeeToInvite());
    agendaEventService.updateEventFields(eventId,
                                         getFields(fieldName, fieldValue),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    eventModification = eventUpdateReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.UPDATED));
    assertTrue(eventModification.hasModification(AgendaEventModificationType.ALLOW_INVITE_UPDATED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 2,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId);
    assertEquals(fieldValue, String.valueOf(event.isAllowAttendeeToInvite()));
  }

  /**
   * An update that does not state the visibility or the availability keeps the
   * stored ones, and an update that states them stores what it states. The
   * caller shape is caldav-integration's inbound sync, whose
   * IcsEventMapper.toEvent builds a fresh Event with no visibility before
   * calling updateEvent: a PRIVATE event must stay masked on the calendar-link
   * feed after it. An explicit reset stays updateEventFields' blank value,
   * pinned in testUpdateEventFields. The stated branch is the form's own save
   * path (a full PUT through updateEvent), so a guard that always kept the stored
   * value would make the visibility and availability selects no-ops.
   *
   * @throws Exception when the update fails
   */
  @Test
  public void testUpdateEventWithoutVisibilityKeepsTheStoredOne() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    Event createdEvent = newEventInstance(start, start, false);
    createdEvent = createEvent(createdEvent.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);
    long eventId = createdEvent.getId();
    agendaEventService.updateEventFields(eventId,
                                         getFields("visibility", EventVisibility.PRIVATE.name()),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));
    agendaEventService.updateEventFields(eventId,
                                         getFields("availability", EventAvailability.FREE.name()),
                                         true,
                                         true,
                                         Long.parseLong(testuser1Identity.getId()));

    Event event = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
    event.setVisibility(null);
    event.setAvailability(null);
    agendaEventService.updateEvent(event,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));

    Event stored = agendaEventService.getEventById(eventId);
    assertEquals("an update that states no visibility must not un-mask a private event",
                 EventVisibility.PRIVATE,
                 stored.getVisibility());
    assertEquals("an update that states no availability must not turn a free event busy",
                 EventAvailability.FREE,
                 stored.getAvailability());

    event = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
    event.setVisibility(EventVisibility.PUBLIC);
    event.setAvailability(EventAvailability.BUSY);
    agendaEventService.updateEvent(event,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));

    stored = agendaEventService.getEventById(eventId);
    assertEquals("a stated visibility is stored", EventVisibility.PUBLIC, stored.getVisibility());
    assertEquals("a stated availability is stored", EventAvailability.BUSY, stored.getAvailability());
  }

  @Test
  public void testUpdateEvent_InSpace_AsMember() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event.setCalendarId(spaceCalendar.getId());
    event.setAllowAttendeeToUpdate(true);
    Event createdEvent = createEvent(event.clone(),
                                     Long.parseLong(testuser1Identity.getId()),
                                     testuser2Identity,
                                     testuser3Identity);

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    String newDescription = "Desc2";
    createdEvent.setDescription(newDescription);
    agendaEventService.updateEvent(createdEvent,
                                   null,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser2Identity.getId()));

    Event updatedEvent = agendaEventService.getEventById(createdEvent.getId(), null, Long.parseLong(testuser2Identity.getId()));

    assertNotNull(updatedEvent);
    assertEquals(newDescription, updatedEvent.getDescription());

    spaceService.removeMember(space, testuser2Identity.getRemoteId());
    try {
      agendaEventService.updateEvent(updatedEvent,
                                     null,
                                     null,
                                     null,
                                     null,
                                     null,
                                     false,
                                     Long.parseLong(testuser2Identity.getId()));
      fail("testuser2 shouldn't be able to update a previously created event by him, while he's not member of space anymore");
    } catch (IllegalAccessException e) {
      // Expected
    }
  }

  @Test
  public void testDeleteEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    long eventId = event.getId();
    try {
      agendaEventService.deleteEventById(eventId, Long.parseLong(testuser2Identity.getId()));
      fail("Event with id " + eventId + " shouldn't be deletable by an attendee");
    } catch (IllegalAccessException e) {
      // Expected to have this exception, just check if event really always
      // exists
      event = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
      assertNotNull(event);
    }

    RequestLifeCycle.restartTransaction();
    agendaEventService.deleteEventById(eventId, Long.parseLong(testuser1Identity.getId()));
    AgendaEventModification eventModification = eventDeletionReference.get();
    assertNotNull(eventModification);
    assertTrue(eventModification.hasModification(AgendaEventModificationType.DELETED));
    assertEquals("Modification types are more than expected : " + eventModification.getModificationTypes(),
                 1,
                 eventModification.getModificationTypes().size());

    event = agendaEventService.getEventById(eventId, null, Long.parseLong(testuser1Identity.getId()));
    assertNull(event);

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(eventId).getEventAttendees();
    assertTrue(eventAttendees == null || eventAttendees.isEmpty());
    List<EventConference> eventConferences = agendaEventConferenceService.getEventConferences(eventId);
    assertTrue(eventConferences == null || eventConferences.isEmpty());
  }

  @Test
  public void testGetEvents_Recurrent_WithExceptionalEvent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    ZonedDateTime end = getDate().plusHours(2).withNano(0);

    boolean allDay = false;

    Event event = newEventInstance(start, end, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);
    Event createdEvent = agendaEventService.getEventById(event.getId(),
                                                         ZoneId.systemDefault(),
                                                         Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);
    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              null,
                                              null,
                                              getDate().plusHours(1),
                                              getDate().plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(1, events.size());
    assertEquals(0, events.get(0).getId());

    Event exceptionalEvent = events.get(0).clone();
    exceptionalEvent = createEvent(exceptionalEvent, Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1),
                                  getDate().plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(1, events.size());
    assertTrue(events.get(0).getId() > 0);
    assertEquals(exceptionalEvent.getId(), events.get(0).getId());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1).plusDays(1),
                                  getDate().plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(1, events.size());
    assertEquals(0, events.get(0).getId());

    exceptionalEvent.setEnd(exceptionalEvent.getEnd().plusDays(1));
    exceptionalEvent.setStart(exceptionalEvent.getStart().plusDays(1));
    agendaEventService.updateEvent(exceptionalEvent,
                                   ATTENDEES,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   Long.parseLong(testuser1Identity.getId()));

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1),
                                  getDate().plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1).plusDays(1),
                                  getDate().plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(2, events.size());
    assertTrue(events.stream().noneMatch(occurrenceEvent -> occurrenceEvent.getOccurrence() == null));
    assertTrue(events.stream().anyMatch(occurrenceEvent -> occurrenceEvent.getOccurrence().isExceptional()));
    assertTrue(events.stream().anyMatch(occurrenceEvent -> !occurrenceEvent.getOccurrence().isExceptional()));
  }

  @Test
  public void testGetEvents_SpaceMembers() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);

    boolean allDay = true;

    Identity spaceIdentity = identityManager.getOrCreateIdentity(SpaceIdentityProvider.NAME, space.getPrettyName());

    Event event = newEventInstance(start, start, allDay);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), spaceIdentity);
    Event createdEvent = agendaEventService.getEventById(event.getId(),
                                                         null,
                                                         Long.parseLong(testuser2Identity.getId()));

    assertNotNull(createdEvent);
    assertTrue(createdEvent.getId() > 0);

    try {
      agendaEventService.getEventById(event.getId(),
                                      null,
                                      Long.parseLong(testuser4Identity.getId()));
      fail("Should throw an exception when a non member user attempts to access a space event");
    } catch (IllegalAccessException e) {
      // Expected
    }

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              null,
                                              null,
                                              getDate().plusHours(1),
                                              getDate().plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser4Identity.getId()),
                                  null,
                                  null,
                                  getDate().plusHours(1),
                                  getDate().plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter, ZoneId.systemDefault(), Long.parseLong(testuser4Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());
  }

  @Test
  public void testGetEvents() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    Event event = newEventInstance(start, end, false);
    Event event1 = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);
    event1 = agendaEventService.getEventById(event1.getId(),
                                             null,
                                             Long.parseLong(testuser1Identity.getId()));
    assertNotNull(event1);

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              null,
                                              null,
                                              start,
                                              end,
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    Event occurrenceEvent = events.get(0);
    assertEquals(0, occurrenceEvent.getId());
    assertEquals(event1.getId(), occurrenceEvent.getParentId());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    assertEquals(0, occurrenceEvent.getId());
    assertEquals(event1.getId(), occurrenceEvent.getParentId());

    event = newEventInstance(start, start, true);
    createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity);

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(2, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(4, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  event1.getRecurrence()
                                        .getUntil()
                                        .plusDays(2)
                                        .atStartOfDay(ZoneId.systemDefault()),
                                  event1.getRecurrence()
                                        .getUntil()
                                        .plusDays(4)
                                        .atStartOfDay(ZoneId.systemDefault())
                                        .minusSeconds(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    createEvent(event, Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(6, events.size());

    spaceService.deleteSpace(space);

    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(4, events.size());
  }

  @Test
  public void testGetEventsByOwner() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    long testuser1Id = Long.parseLong(testuser1Identity.getId());
    try {
      EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                                Collections.singletonList(testuser1Id),
                                                null,
                                                date.plusHours(1),
                                                date.plusMinutes(90),
                                                0);
      agendaEventService.getEvents(eventFilter,
                                   ZoneId.systemDefault(),
                                   Long.parseLong(testuser2Identity.getId()));
      fail("User 'testuser2' shouldn't be able to access calendar of user 'testuser1'");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event event = newEventInstance(start, end, false);
    Event event1 = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);
    event1 = agendaEventService.getEventById(event1.getId(),
                                             null,
                                             Long.parseLong(testuser1Identity.getId()));

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser1Identity.getId()),
                                              Collections.singletonList(testuser1Id),
                                              null,
                                              date.plusHours(1),
                                              date.plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser1Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    Event occurrenceEvent = events.get(0);
    assertEquals(0, occurrenceEvent.getId());
    assertEquals(event1.getId(), occurrenceEvent.getParentId());

    eventFilter = new EventFilter(Long.parseLong(testuser1Identity.getId()),
                                  Collections.singletonList(testuser1Id),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser1Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    assertEquals(0, occurrenceEvent.getId());
    assertEquals(event1.getId(), occurrenceEvent.getParentId());

    event = newEventInstance(start, start, true);
    createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    eventFilter = new EventFilter(Long.parseLong(testuser1Identity.getId()),
                                  Collections.singletonList(testuser1Id),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser1Identity.getId()));
    assertNotNull(events);
    assertEquals(2, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser1Identity.getId()),
                                  Collections.singletonList(testuser1Id),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90).plusDays(1),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser1Identity.getId()));
    assertNotNull(events);
    assertEquals(4, events.size());
  }

  @Test
  public void testGetEventsByOwnersAndAttendee() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    long testuser1Id = Long.parseLong(testuser1Identity.getId());
    try {
      EventFilter eventFilter = new EventFilter(testuser1Id,
                                                Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                                null,
                                                date.plusHours(1),
                                                date.plusMinutes(90),
                                                0);
      agendaEventService.getEvents(eventFilter,
                                   ZoneId.systemDefault(),
                                   Long.parseLong(testuser2Identity.getId()));
      fail("User 'testuser2' shouldn't be able to access calendar of user 'testuser1'");
    } catch (IllegalAccessException e) {
      // Expected
    }

    try {
      EventFilter eventFilter = new EventFilter(Long.parseLong(testuser5Identity.getId()),
                                                Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                                null,
                                                date.plusHours(1),
                                                date.plusMinutes(90),
                                                0);
      agendaEventService.getEvents(eventFilter,
                                   ZoneId.systemDefault(),
                                   Long.parseLong(testuser5Identity.getId()));
      fail("User 'testuser2' shouldn't be able to access calendar of user 'testuser1'");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);
    event = agendaEventService.getEventById(event.getId(),
                                            null,
                                            Long.parseLong(testuser1Identity.getId()));

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                              null,
                                              date.plusHours(1),
                                              date.plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(1, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                  Collections.singletonList(Long.parseLong(testuser2Identity.getId())),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()),
                                  Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    eventAttendees.add(new EventAttendee(0, event.getId(), Long.parseLong(spaceIdentity.getId()), null));
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  testuser1Id,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              testuser1Id,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));

    eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()),
                                  Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());
  }

  @Test
  public void testGetEventsByLimit() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();
    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    Event event = newEventInstance(start, end, false);
    event.setRecurrence(new EventRecurrence(0,
                                            null,
                                            0,
                                            EventRecurrenceType.DAILY,
                                            EventRecurrenceFrequency.DAILY,
                                            2,
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
                                            null));
    event.setCalendarId(spaceCalendar.getId());
    createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              Collections.singletonList(Long.parseLong(spaceIdentity.getId())),
                                              null,
                                              date.plusHours(1),
                                              null,
                                              10);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));

    assertNotNull(events);
    assertEquals(10, events.size());
  }

  @Test
  public void testGetEventsByAttendee() throws Exception { // NOSONAR
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    long testuser1Id = Long.parseLong(testuser1Identity.getId());
    try {
      EventFilter eventFilter = new EventFilter(testuser1Id,
                                                null,
                                                null,
                                                date.plusHours(1),
                                                date.plusMinutes(90),
                                                0);
      agendaEventService.getEvents(eventFilter,
                                   ZoneId.systemDefault(),
                                   Long.parseLong(testuser2Identity.getId()));
      fail("User 'testuser2' shouldn't be able to access calendar of user 'testuser1'");
    } catch (IllegalAccessException e) {
      // Expected
    }

    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser1Identity, testuser2Identity);
    event = agendaEventService.getEventById(event.getId(),
                                            null,
                                            Long.parseLong(testuser1Identity.getId()));

    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser2Identity.getId()),
                                              null,
                                              null,
                                              date.plusHours(1),
                                              date.plusMinutes(90),
                                              0);
    List<Event> events = agendaEventService.getEvents(eventFilter,
                                                      ZoneId.systemDefault(),
                                                      Long.parseLong(testuser2Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    List<EventAttendee> eventAttendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    eventAttendees.add(new EventAttendee(0, event.getId(), Long.parseLong(spaceIdentity.getId()), null));
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  testuser1Id,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              testuser1Id,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));

    eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());

    eventFilter = new EventFilter(Long.parseLong(testuser4Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser4Identity.getId()));
    assertNotNull(events);
    assertEquals(0, events.size());

    eventAttendees = agendaEventAttendeeService.getEventAttendees(event.getId()).getEventAttendees();
    eventAttendees.add(new EventAttendee(0, event.getId(), Long.parseLong(testuser4Identity.getId()), null));
    agendaEventAttendeeService.saveEventAttendees(event,
                                                  eventAttendees,
                                                  testuser1Id,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(event.getId(),
                                                                              event.getCalendarId(),
                                                                              testuser1Id,
                                                                              Collections.singleton(AgendaEventModificationType.ADDED)));

    eventFilter = new EventFilter(Long.parseLong(testuser4Identity.getId()),
                                  null,
                                  null,
                                  date.plusHours(1),
                                  date.plusMinutes(90),
                                  0);
    events = agendaEventService.getEvents(eventFilter,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser4Identity.getId()));
    assertNotNull(events);
    assertEquals(1, events.size());
  }

  @Test
  public void testGetEventsByResponseTypes() throws Exception {
    ZonedDateTime date = getDate();

    ZonedDateTime start = date.withNano(0);
    ZonedDateTime end = start.plusHours(2);

    Event event = newEventInstance(start, end, false);
    event = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);

    List<EventAttendeeResponse> responseTypes = new ArrayList<>();
    List<Event> events = new ArrayList<>();
    agendaEventAttendeeService.sendEventResponse(event.getId(),
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);
    responseTypes.add(EventAttendeeResponse.TENTATIVE);
    EventFilter eventFilter = new EventFilter(Long.parseLong(testuser3Identity.getId()), null, responseTypes, start, end, 0);
    try {
      events = agendaEventService.getEvents(eventFilter,
                                            ZoneId.systemDefault(),
                                            Long.parseLong(testuser3Identity.getId()));
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertNotNull(events);
    assertEquals(1, events.size());

    event = newEventInstance(start, end, false);
    Event event1 = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);
    agendaEventAttendeeService.sendEventResponse(event1.getId(),
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.TENTATIVE);

    List<EventAttendeeResponse> responseTypes1 = new ArrayList<>();
    responseTypes1.add(EventAttendeeResponse.TENTATIVE);
    EventFilter eventFilter1 = new EventFilter(Long.parseLong(testuser3Identity.getId()), null, responseTypes1, start, end, 0);
    events = agendaEventService.getEvents(eventFilter1,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));

    assertNotNull(events);
    assertEquals(2, events.size());

    event = newEventInstance(start, end, false);
    Event event2 = createEvent(event.clone(), Long.parseLong(testuser1Identity.getId()), testuser2Identity, testuser3Identity);
    agendaEventAttendeeService.sendEventResponse(event2.getId(),
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);
    List<EventAttendeeResponse> responseTypes2 = new ArrayList<>();
    responseTypes2.add(EventAttendeeResponse.TENTATIVE);
    responseTypes2.add(EventAttendeeResponse.ACCEPTED);
    EventFilter eventFilter2 = new EventFilter(Long.parseLong(testuser3Identity.getId()), null, responseTypes2, start, end, 0);
    events = agendaEventService.getEvents(eventFilter2,
                                          ZoneId.systemDefault(),
                                          Long.parseLong(testuser3Identity.getId()));
    assertNotNull(events);
    assertEquals(3, events.size());
  }

  @Test
  public void testCountPendingEvents() throws Exception {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    long countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(0, countPendingEvents);

    ZonedDateTime occurrenceId = start.plusDays(1);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId.plusDays(1));
    Event exceptionalOccurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceId);
    assertNotNull(exceptionalOccurrenceEvent);
    assertNotNull(exceptionalOccurrenceEvent.getOccurrence());

    countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(0, countPendingEvents);

    String exceptionalEventEndDateRFC3339 = AgendaDateUtils.toRFC3339Date(exceptionalOccurrenceEvent.getEnd().plusHours(1));
    Map<String, List<String>> dateFields = getFields("end", exceptionalEventEndDateRFC3339);
    agendaEventService.updateEventFields(exceptionalOccurrenceEvent.getId(), dateFields, false, false, userIdentityId);

    countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(0, countPendingEvents);

    exceptionalOccurrenceEvent = agendaEventService.getEventById(exceptionalOccurrenceEvent.getId());
    List<EventAttendee> eventAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId)
                                                                                   .getEventAttendees());
    eventAttendees.add(new EventAttendee(0,
                                         0,
                                         Long.parseLong(testuser5Identity.getId()),
                                         null));

    agendaEventService.updateEvent(exceptionalOccurrenceEvent,
                                   eventAttendees,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(1, countPendingEvents);
  }

  @Test
  public void testCountPastPendingEvents() throws Exception {
    ZonedDateTime start = ZonedDateTime.now().minusHours(1);
    ZonedDateTime end = ZonedDateTime.now().minusMinutes(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    event.setRecurrence(null);
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    long countPendingEvents = agendaEventService.countPendingEvents(null, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser2Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser4Identity.getId()));
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(null, Long.parseLong(testuser5Identity.getId()));
    assertEquals(0, countPendingEvents);
  }

  @Test
  public void testGetPendingEvents() throws Exception {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    List<Event> pendingEvents = agendaEventService.getPendingEvents(null, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    ZonedDateTime occurrenceId = start.plusDays(1);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId.plusDays(1));
    Event exceptionalOccurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceId);
    assertNotNull(exceptionalOccurrenceEvent);
    assertNotNull(exceptionalOccurrenceEvent.getOccurrence());

    pendingEvents = agendaEventService.getPendingEvents(null, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    String exceptionalEventEndDateRFC3339 = AgendaDateUtils.toRFC3339Date(exceptionalOccurrenceEvent.getEnd().plusHours(1));
    Map<String, List<String>> dateFields = getFields("end", exceptionalEventEndDateRFC3339);
    agendaEventService.updateEventFields(exceptionalOccurrenceEvent.getId(), dateFields, false, false, userIdentityId);

    pendingEvents = agendaEventService.getPendingEvents(null, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    exceptionalOccurrenceEvent = agendaEventService.getEventById(exceptionalOccurrenceEvent.getId());
    List<EventAttendee> eventAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId)
                                                                                   .getEventAttendees());
    eventAttendees.add(new EventAttendee(0,
                                         0,
                                         Long.parseLong(testuser5Identity.getId()),
                                         null));

    agendaEventService.updateEvent(exceptionalOccurrenceEvent,
                                   eventAttendees,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    pendingEvents = agendaEventService.getPendingEvents(null, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents = agendaEventService.getPendingEvents(null, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
  }

  @Test
  public void testCountPendingEventsByOwnerIds() throws Exception {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    List<Long> ownerIds = Collections.singletonList(Long.parseLong(spaceIdentity.getId()));
    long countPendingEvents = agendaEventService.countPendingEvents(ownerIds, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);
    try {
      agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser4Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }
    try {
      agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser5Identity.getId()));
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    ZonedDateTime occurrenceId = start.plusDays(1);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId.plusDays(1));
    Event exceptionalOccurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceId);
    assertNotNull(exceptionalOccurrenceEvent);
    assertNotNull(exceptionalOccurrenceEvent.getOccurrence());

    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()));
    assertEquals(1, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()));
    assertEquals(0, countPendingEvents);

    String exceptionalEventEndDateRFC3339 = AgendaDateUtils.toRFC3339Date(exceptionalOccurrenceEvent.getEnd().plusHours(1));
    Map<String, List<String>> dateFields = getFields("end", exceptionalEventEndDateRFC3339);
    agendaEventService.updateEventFields(exceptionalOccurrenceEvent.getId(), dateFields, false, false, userIdentityId);

    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()));
    assertEquals(1, countPendingEvents);

    exceptionalOccurrenceEvent = agendaEventService.getEventById(exceptionalOccurrenceEvent.getId());
    List<EventAttendee> eventAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId)
                                                                                   .getEventAttendees());
    eventAttendees.add(new EventAttendee(0,
                                         0,
                                         Long.parseLong(testuser5Identity.getId()),
                                         null));

    agendaEventService.updateEvent(exceptionalOccurrenceEvent,
                                   eventAttendees,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, userIdentityId);
    assertEquals(0, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()));
    assertEquals(2, countPendingEvents);
    countPendingEvents = agendaEventService.countPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()));
    assertEquals(1, countPendingEvents);
  }

  @Test
  public void testGetPendingEventsByOwnerIds() throws Exception {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    Event event = newEventInstance(start, end, false);
    event.setCalendarId(spaceCalendar.getId());
    long userIdentityId = Long.parseLong(testuser1Identity.getId());
    event = createEvent(event, userIdentityId, spaceIdentity, testuser4Identity);
    long eventId = event.getId();

    agendaEventAttendeeService.sendEventResponse(eventId, userIdentityId, EventAttendeeResponse.DECLINED);
    agendaEventAttendeeService.sendEventResponse(eventId,
                                                 Long.parseLong(testuser3Identity.getId()),
                                                 EventAttendeeResponse.ACCEPTED);

    List<Long> ownerIds = Collections.singletonList(Long.parseLong(spaceIdentity.getId()));
    List<Event> pendingEvents = agendaEventService.getPendingEvents(ownerIds, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    try {
      agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser4Identity.getId()), ZoneOffset.UTC, 0, 10);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }
    try {
      agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser5Identity.getId()), ZoneOffset.UTC, 0, 10);
      fail();
    } catch (IllegalAccessException e) {
      // Expected
    }

    ZonedDateTime occurrenceId = start.plusDays(1);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId);
    agendaEventService.saveEventExceptionalOccurrence(eventId, occurrenceId.plusDays(1));
    Event exceptionalOccurrenceEvent = agendaEventService.getExceptionalOccurrenceEvent(eventId, occurrenceId);
    assertNotNull(exceptionalOccurrenceEvent);
    assertNotNull(exceptionalOccurrenceEvent.getOccurrence());

    pendingEvents = agendaEventService.getPendingEvents(ownerIds, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());

    String exceptionalEventEndDateRFC3339 = AgendaDateUtils.toRFC3339Date(exceptionalOccurrenceEvent.getEnd().plusHours(1));
    Map<String, List<String>> dateFields = getFields("end", exceptionalEventEndDateRFC3339);
    agendaEventService.updateEventFields(exceptionalOccurrenceEvent.getId(), dateFields, false, false, userIdentityId);

    pendingEvents = agendaEventService.getPendingEvents(ownerIds, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());

    exceptionalOccurrenceEvent = agendaEventService.getEventById(exceptionalOccurrenceEvent.getId());
    List<EventAttendee> eventAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(eventId)
                                                                                   .getEventAttendees());
    eventAttendees.add(new EventAttendee(0,
                                         0,
                                         Long.parseLong(testuser5Identity.getId()),
                                         null));

    agendaEventService.updateEvent(exceptionalOccurrenceEvent,
                                   eventAttendees,
                                   null,
                                   null,
                                   null,
                                   null,
                                   false,
                                   userIdentityId);
    pendingEvents = agendaEventService.getPendingEvents(ownerIds, userIdentityId, ZoneOffset.UTC, 0, 10);
    assertEquals(0, pendingEvents.size());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser2Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(2, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
    assertEquals(event.getId(), pendingEvents.get(1).getId());
    pendingEvents =
                  agendaEventService.getPendingEvents(ownerIds, Long.parseLong(testuser3Identity.getId()), ZoneOffset.UTC, 0, 10);
    assertEquals(1, pendingEvents.size());
    assertEquals(exceptionalOccurrenceEvent.getId(), pendingEvents.get(0).getId());
  }

  @Test
  public void testAddIcsFile() throws IOException {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    ZoneId dstTimeZone = ZoneId.of("Europe/Paris");

    NotificationInfo notification = new NotificationInfo();
    notification.with(STORED_PARAMETER_EVENT_TITLE, "eventSummary")
            .with(STORED_PARAMETER_EVENT_CREATOR, "Root Root")
            .with(STORED_PARAMETER_EVENT_START_DATE, AgendaDateUtils.toRFC3339Date(start))
            .with(STORED_PARAMETER_EVENT_END_DATE, AgendaDateUtils.toRFC3339Date(end))
            .with(STORED_PARAMETER_EVENT_LOCATION, "eXo office, Earth");

    MessageInfo messageInfo = new MessageInfo();
    Attachment attachment = new Attachment();

    byte[] icsContent = generateIcsFile("42",
            "ownerId",
            "eventSummary",
            "eventDescription",
            AgendaDateUtils.toRFC3339Date(start),
            AgendaDateUtils.toRFC3339Date(end),
            "eventConference",
            "eventModifierId",
            "eventCreator",
            "location",
            "https://exo.example.com/portal/dw/agenda?eventId=42",
            EventAvailability.DEFAULT,
            Locale.getDefault(),
            dstTimeZone);
    attachment.setMimeType("text/calendar;charset=utf-8;method=PUBLISH");
    attachment.setInputStream(new ByteArrayInputStream(icsContent));
    messageInfo.addAttachment(attachment);
    assertNotNull(messageInfo.getAttachment());
    assertEquals(1, messageInfo.getAttachment().size());
    String text = new String(messageInfo.getAttachment().get(0).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    List<String> lines = text.contains("\r\n") ? List.of(text.split("\r\n")) : List.of(text.split("\n"));
    String dtStart = lines.stream().filter(s -> s.contains("DTSTART;TZID")).findAny().get();
    String dtEnd = lines.stream().filter(s -> s.contains("DTEND;TZID")).findAny().get();
    assertNotNull(dtStart);
    assertNotNull(dtEnd);
    String icsStartDate = dtStart.substring(dtStart.indexOf(":") + 1);
    String icsEndDate = dtEnd.substring(dtEnd.indexOf(":") + 1);

    String startDateRFC3339 = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_START_DATE);
    ZonedDateTime startDate = ZonedDateTime.parse(startDateRFC3339).withZoneSameInstant(dstTimeZone);
    String startDateFormatted = AgendaDateUtils.formatDateTimeWithSeconds(startDate);

    String endDateRFC3339 = notification.getValueOwnerParameter(STORED_PARAMETER_EVENT_END_DATE);
    ZonedDateTime endDate = ZonedDateTime.parse(endDateRFC3339).withZoneSameInstant(dstTimeZone);
    String endDateFormatted = AgendaDateUtils.formatDateTimeWithSeconds(endDate);

    assertEquals(icsStartDate, startDateFormatted);
    assertEquals(icsEndDate, endDateFormatted);
  }

  /**
   * Reads the iCalendar document eXo would mail for an event, already unfolded
   * so a property can be matched whole.
   *
   * <p>
   * RFC 5545 &sect;3.1 lets a writer break any line after 75 octets and
   * continue it with a leading space, which the eXo writer does: without
   * unfolding, a DESCRIPTION long enough to be broken cannot be asserted on.
   *
   * @param eventDescription description to pass to the generator, HTML as the
   *          editor would store it
   * @param userLocale locale the labels are read in
   * @param eventModifierId identity id to write as ORGANIZER, blank for none
   * @return the unfolded document
   */
  private String generateIcs(String eventDescription, Locale userLocale, String eventModifierId) {
    return generateIcs(eventDescription, userLocale, eventModifierId, EVENT_LINK);
  }

  /**
   * The same, choosing what link back to eXo the document is given — null
   * standing for the guest case, where the caller withholds it.
   *
   * @param eventDescription description to pass to the generator, HTML as the
   *          editor would store it
   * @param userLocale locale the labels are read in
   * @param eventModifierId identity id to write as ORGANIZER, blank for none
   * @param eventUrl link back to the event in eXo, null for a guest
   * @return the unfolded document
   */
  private String generateIcs(String eventDescription, Locale userLocale, String eventModifierId, String eventUrl) {
    return generateIcs(eventDescription, userLocale, eventModifierId, eventUrl, EventAvailability.DEFAULT);
  }

  /**
   * The same, choosing what the event does to the recipient's time.
   *
   * @param eventDescription description to pass to the generator, HTML as the
   *          editor would store it
   * @param userLocale locale the labels are read in
   * @param eventModifierId identity id to write as ORGANIZER, blank for none
   * @param eventUrl link back to the event in eXo, null for a guest
   * @param availability what the organiser chose on the form, null for an
   *          event that carries none
   * @return the unfolded document
   */
  private String generateIcs(String eventDescription,
                             Locale userLocale,
                             String eventModifierId,
                             String eventUrl,
                             EventAvailability availability) {
    ZonedDateTime start = getDate();
    ZonedDateTime end = start.plusHours(1);
    byte[] icsContent = generateIcsFile("42",
                                        spaceIdentity.getId(),
                                        "eventSummary",
                                        eventDescription,
                                        AgendaDateUtils.toRFC3339Date(start),
                                        AgendaDateUtils.toRFC3339Date(end),
                                        CONFERENCE_LINK,
                                        eventModifierId,
                                        "Root Root",
                                        "location",
                                        eventUrl,
                                        availability,
                                        userLocale,
                                        ZoneId.of("Europe/Paris"));
    String text = new String(icsContent, StandardCharsets.UTF_8);
    return text.replace("\r\n ", "").replace("\r\n\t", "").replace("\n ", "").replace("\n\t", "");
  }

  /**
   * Reads one property out of an unfolded iCalendar document.
   *
   * @param ics unfolded document
   * @param propertyName property name, without its parameters
   * @return the whole property line, or null when the document has none
   */
  private String icsProperty(String ics, String propertyName) {
    return Arrays.stream(ics.split("\\R"))
                 .filter(line -> line.equals(propertyName) || line.startsWith(propertyName + ":")
                     || line.startsWith(propertyName + ";"))
                 .findFirst()
                 .orElse(null);
  }

  /**
   * EXO-90327: the document a recipient's calendar imports — the invitation
   * mail's <code>event.ics</code> and the <code>/ics</code> download, both
   * written by {@code Utils.generateIcsFile} — says what the organiser chose on
   * the form. A <code>FREE</code> event is transparent; <code>BUSY</code>,
   * <code>DEFAULT</code> and an event that carries none are opaque.
   * <p>
   * <strong>TRANSP has to be present, not merely correct.</strong> RFC 5545
   * §3.8.2.7 makes an absent <code>TRANSP</code> default to
   * <code>OPAQUE</code>, so the document that omits it books the slot — which
   * is what this generator did before EXO-90327, and what no test could see,
   * because a missing property and a busy one are the same answer. The pin
   * therefore asserts the line, not just the value.
   *
   */
  @Test
  public void testIcsCarriesTheAvailabilityAsTransp() {
    Map<EventAvailability, String> expected = new LinkedHashMap<>();
    expected.put(EventAvailability.FREE, "TRANSP:TRANSPARENT");
    expected.put(EventAvailability.BUSY, "TRANSP:OPAQUE");
    expected.put(EventAvailability.DEFAULT, "TRANSP:OPAQUE");
    expected.put(null, "TRANSP:OPAQUE");

    expected.forEach((availability, line) -> {
      String ics = generateIcs("eventDescription", Locale.ENGLISH, "", EVENT_LINK, availability);
      assertEquals("availability " + availability + " must be written as " + line, line, icsProperty(ics, "TRANSP"));
    });
  }

  /**
   * PRODID must carry its value alone: the writer emits the property name
   * itself, so a value that repeats it puts PRODID:PRODID: on the wire.
   */
  @Test
  public void testIcsFileNamesTheProductOnce() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");
    String prodId = icsProperty(ics, "PRODID");
    assertNotNull("the document must name the product that wrote it", prodId);
    assertFalse("PRODID must not repeat its own property name: " + prodId, prodId.startsWith("PRODID:PRODID:"));
    assertTrue("PRODID value must be an FPI: " + prodId, prodId.startsWith("PRODID:-//"));
  }

  /**
   * The body must declare the same method as the MIME part that carries it,
   * and that method is PUBLISH: answering is done through the links in the
   * mail, not through iMIP.
   */
  @Test
  public void testIcsFileDeclaresItsMethodInTheBody() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");
    assertEquals("METHOD:PUBLISH", icsProperty(ics, "METHOD"));
  }

  /**
   * ORGANIZER carries a CAL-ADDRESS, which RFC 5545 &sect;3.3.3 defines as a
   * URI: a bare mail address has no scheme and is dropped by a client that
   * validates the value.
   */
  @Test
  public void testIcsFileWritesOrganizerAsAMailtoUri() {
    Profile profile = testuser1Identity.getProfile();
    profile.setProperty(Profile.EMAIL, "testuser1@example.com");
    identityManager.updateProfile(profile);

    String ics = generateIcs(null, Locale.ENGLISH, testuser1Identity.getId());
    String organizer = icsProperty(ics, "ORGANIZER");
    assertNotNull("the invitation must say who sent it", organizer);
    assertTrue("ORGANIZER must be a mailto: URI: " + organizer, organizer.endsWith(":mailto:testuser1@example.com"));
  }

  /**
   * DESCRIPTION is plain text by definition; the HTML flavour belongs to
   * X-ALT-DESC, which must keep carrying it.
   */
  @Test
  public void testIcsFileDescriptionIsPlainTextAndAltDescIsHtml() {
    String ics = generateIcs("<p>Bring the <b>slides</b>.</p>", Locale.ENGLISH, "unknownModifier");

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertFalse("DESCRIPTION must not carry markup: " + description, description.contains("<"));
    assertFalse("DESCRIPTION must not carry markup: " + description, description.contains("&lt;"));
    assertTrue("DESCRIPTION must keep the text of the description: " + description,
               description.contains("Bring the slides."));

    String altDescription = icsProperty(ics, "X-ALT-DESC");
    assertNotNull("the HTML flavour must still be offered", altDescription);
    assertTrue("X-ALT-DESC must carry the HTML: " + altDescription, altDescription.contains("<html><body>"));
    assertTrue("X-ALT-DESC must be typed as HTML: " + altDescription, altDescription.contains("FMTTYPE=text/html"));
  }

  /**
   * The space attribution is what the invitation is for: it must survive
   * DESCRIPTION becoming plain text.
   */
  @Test
  public void testIcsFileStillAttributesTheInvitationToItsSpace() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");
    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertTrue("the sender must be named: " + description, description.contains("Root Root"));
    assertTrue("the space must be named: " + description, description.contains(space.getDisplayName()));
  }

  /**
   * An event belonging to no space attributes itself to its sender alone.
   *
   * <p>
   * The attribution used to be concatenated unconditionally, so an event on a
   * personal calendar — whose owner identity resolves to no space — read
   * "Invitation sent by Root Root in space null". Harmless-looking in a mail
   * nobody re-reads, and much less so now that the very same sentence is what
   * the CalDAV copy carries into the user's own calendar (EXO-89732): the
   * clause is dropped when there is no space to name.
   */
  @Test
  public void testIcsFileNamesNoSpaceWhenTheEventBelongsToNone() {
    byte[] icsContent = generateIcsFile("42",
                                              testuser1Identity.getId(),
                                              "eventSummary",
                                              null,
                                              AgendaDateUtils.toRFC3339Date(getDate()),
                                              AgendaDateUtils.toRFC3339Date(getDate().plusHours(1)),
                                              null,
                                              "unknownModifier",
                                              "Root Root",
                                              "location",
                                              EVENT_LINK,
                                              EventAvailability.DEFAULT,
                                              Locale.ENGLISH,
                                              ZoneId.of("Europe/Paris"));
    String ics = new String(icsContent, StandardCharsets.UTF_8).replace("\r\n ", "").replace("\n ", "");

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertTrue("the sender must still be named: " + description, description.contains("Root Root"));
    assertFalse("an absent space must not be written as the word null: " + description, description.contains("null"));
    assertFalse("and the clause introducing it must be dropped with it: " + description, description.contains("in space"));
  }

  /**
   * URL means "where this event lives" (RFC 5545 &sect;3.8.4.6), so it must
   * name the event in eXo — not the video call, which is a different thing and
   * has a property of its own.
   *
   * <p>
   * The mailed document used to set URL from the conference link, which both
   * said the wrong thing and left the event's own address out of the document
   * altogether (EXO-89751).
   */
  @Test
  public void testIcsFileUrlIsTheEventAndNotTheConference() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");

    String url = icsProperty(ics, "URL");
    assertNotNull("the mailed document must say where the event lives", url);
    assertEquals("URL must be the event in eXo", "URL:" + EVENT_LINK, url);
    assertFalse("URL must not be the conference link: " + url, url.contains(CONFERENCE_LINK));
  }

  /**
   * The link is also written into the description, beside the conference line
   * already there: many calendar clients never surface URL, and the
   * description is what a person reads.
   */
  @Test
  public void testIcsFileDescriptionCarriesTheEventLinkBesideTheConferenceOne() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier");

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertTrue("the description must carry the event link: " + description, description.contains(EVENT_LINK));
    // The label reads as its key here: no resource bundle is registered in
    // the test container, and EventIcsBuilder answers the key rather than
    // failing the push. What is pinned is that the line is introduced by the
    // agenda.eventLink label at all — the English text of it lives in
    // Agenda_en.properties.
    assertTrue("and it must be labelled: " + description, description.contains("agenda.eventLink " + EVENT_LINK));
    assertTrue("the conference line must still be there: " + description, description.contains(CONFERENCE_LINK));

    String altDescription = icsProperty(ics, "X-ALT-DESC");
    assertNotNull(altDescription);
    assertTrue("the HTML flavour must carry it too: " + altDescription, altDescription.contains(EVENT_LINK));
  }

  /**
   * A guest has no eXo account, so the link lands them on a login screen. The
   * mail is the one channel that knows who it is going to, and it withholds
   * the link there — from URL and from the description alike.
   */
  @Test
  public void testIcsFileWithholdsTheEventLinkFromAGuest() {
    String ics = generateIcs(null, Locale.ENGLISH, "unknownModifier", null);

    assertNull("a guest's document must carry no URL: " + icsProperty(ics, "URL"), icsProperty(ics, "URL"));

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertFalse("nor the labelled line: " + description, description.contains("agenda.eventLink"));
    assertFalse("nor the link anywhere in it: " + description, description.contains("agenda?eventId="));
    assertTrue("the conference link is not withheld — a guest can join the call: " + description,
               description.contains(CONFERENCE_LINK));

    String altDescription = icsProperty(ics, "X-ALT-DESC");
    assertNotNull(altDescription);
    assertFalse("nor the HTML flavour of the line: " + altDescription, altDescription.contains("agenda?eventId="));
  }

  /**
   * A non-ASCII character must reach DESCRIPTION as itself.
   *
   * <p>
   * The HTML flavour runs its content through an entity encoder, so an
   * accented character becomes &amp;eacute; or &amp;#xe9;, whose semicolon the
   * iCalendar writer then escapes into \; — the character mangled twice over,
   * which is what a French recipient was shown. Plain text must be built from
   * the source values, not by unescaping the HTML one.
   */
  @Test
  public void testIcsFileDescriptionKeepsNonAsciiCharactersIntact() {
    String ics = generateIcs("<p>Réunion reportée à 20h &amp; suivante</p>", Locale.FRENCH, "unknownModifier");

    String description = icsProperty(ics, "DESCRIPTION");
    assertNotNull(description);
    assertTrue("the accented text must reach DESCRIPTION as itself: " + description,
               description.contains("Réunion reportée à 20h"));
    assertFalse("DESCRIPTION must carry no HTML entity: " + description, description.contains("&eacute"));
    assertFalse("DESCRIPTION must carry no HTML entity: " + description, description.contains("&#x"));
    assertFalse("DESCRIPTION must carry no escaped entity semicolon: " + description, description.contains("\\;"));
    assertTrue("an escaped ampersand must be decoded, not left as an entity: " + description,
               description.contains("& suivante"));
  }

  /**
   * Moving an event to another calendar through update requires the right to
   * create events in the <b>target</b> calendar (derived from the stored
   * calendar row): a user allowed to update an event must not be able to file
   * it into someone else's calendar, while moving between the user's own
   * calendars must work.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventMoveChecksTargetCalendarAcl() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    Event createdEvent = createEvent(eventInstance.clone(), user1IdentityId, testuser1Identity);
    assertEquals(calendar.getId(), createdEvent.getCalendarId());

    // 1. Moving the event to another user's personal calendar must be refused
    org.exoplatform.agenda.model.Calendar user2Calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(Long.parseLong(testuser2Identity.getId()));
    Event eventToMove = agendaEventService.getEventById(createdEvent.getId(), null, user1IdentityId).clone();
    eventToMove.setCalendarId(user2Calendar.getId());
    try {
      agendaEventService.updateEvent(eventToMove,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     false,
                                     user1IdentityId);
      fail("Shouldn't allow to move an event into another user's calendar");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved",
                 calendar.getId(),
                 agendaEventService.getEventById(createdEvent.getId()).getCalendarId());

    // 2. Moving the event between the user's own calendars must work
    org.exoplatform.agenda.model.Calendar secondCalendar =
                                                          new org.exoplatform.agenda.model.Calendar(0, user1IdentityId, false, null, null, null, null, null, null);
    secondCalendar.setName("Second calendar");
    secondCalendar = agendaCalendarService.createCalendar(secondCalendar, testuser1Identity.getRemoteId());
    try {
      eventToMove = agendaEventService.getEventById(createdEvent.getId(), null, user1IdentityId).clone();
      eventToMove.setCalendarId(secondCalendar.getId());
      agendaEventService.updateEvent(eventToMove,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     false,
                                     user1IdentityId);
      assertEquals("The event must have moved to the user's second calendar",
                   secondCalendar.getId(),
                   agendaEventService.getEventById(createdEvent.getId()).getCalendarId());
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
      agendaCalendarService.deleteCalendarById(user2Calendar.getId());
    }
  }

  /**
   * Moving an event to another calendar through a field patch (the
   * {@code calendarId} field of {@code PATCH /v1/agenda/events/{id}}) requires
   * the right to create events in the target calendar, as the full update does
   * (EXO-90381): the creator of an event may not file it into a space calendar
   * where they aren't a redactor, nor into another user's personal calendar. A
   * target calendar that doesn't exist is still reported as such, before any
   * permission is checked.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventFieldsMoveIntoCalendarWithoutCreateRightIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    Event createdEvent = createEvent(eventInstance.clone(), user1IdentityId, testuser1Identity);
    long eventId = createdEvent.getId();
    assertEquals(calendar.getId(), createdEvent.getCalendarId());

    // A space with a redactor: its other members can no longer add events
    spaceService.addRedactor(space, testuser3Identity.getRemoteId());
    assertTrue("testuser1 can update the event he created", agendaEventService.canUpdateEvent(createdEvent, user1IdentityId));
    assertFalse("testuser1 isn't a redactor of the space", agendaEventService.canCreateEvent(spaceCalendar, user1IdentityId));

    // 1. Into a space calendar where the user isn't a redactor
    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(spaceCalendar.getId())),
                                           false,
                                           false,
                                           user1IdentityId);
      fail("Shouldn't allow to move an event into a space calendar where the user can't create events");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved", calendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());

    // 1b. A patch that changes another field first stores nothing either
    Map<String, List<String>> fields = new LinkedHashMap<>();
    fields.put("summary", Collections.singletonList("Patched before the refused move"));
    fields.put("calendarId", Collections.singletonList(String.valueOf(spaceCalendar.getId())));
    try {
      agendaEventService.updateEventFields(eventId, fields, false, false, user1IdentityId);
      fail("Shouldn't allow to move an event into a space calendar where the user can't create events");
    } catch (IllegalAccessException e) {
      // Expected
    }
    Event storedEvent = agendaEventService.getEventById(eventId);
    assertEquals("The event must not have moved", calendar.getId(), storedEvent.getCalendarId());
    assertEquals("A field patched before the refused move must not be stored",
                 createdEvent.getSummary(),
                 storedEvent.getSummary());

    // 2. Into another user's personal calendar
    org.exoplatform.agenda.model.Calendar user2Calendar =
                                                         agendaCalendarService.getOrCreateCalendarByOwnerId(Long.parseLong(testuser2Identity.getId()));
    try {
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(user2Calendar.getId())),
                                           false,
                                           false,
                                           user1IdentityId);
      fail("Shouldn't allow to move an event into another user's calendar");
    } catch (IllegalAccessException e) {
      // Expected
    } finally {
      agendaCalendarService.deleteCalendarById(user2Calendar.getId());
    }
    assertEquals("The event must not have moved", calendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());

    // 3. A target calendar that doesn't exist is reported as not found
    assertThrows(IllegalArgumentException.class,
                 () -> agendaEventService.updateEventFields(eventId,
                                                            getFields("calendarId", String.valueOf(Integer.MAX_VALUE)),
                                                            false,
                                                            false,
                                                            user1IdentityId));
  }

  /**
   * The full update of an event stays refused when it moves the event into a
   * space calendar where the user isn't a redactor (EXO-90381), as it already
   * was for another user's personal calendar.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventMoveIntoSpaceCalendarWithoutCreateRightIsRefused() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    Event createdEvent = createEvent(eventInstance.clone(), user1IdentityId, testuser1Identity);
    long eventId = createdEvent.getId();

    spaceService.addRedactor(space, testuser3Identity.getRemoteId());
    assertFalse("testuser1 isn't a redactor of the space", agendaEventService.canCreateEvent(spaceCalendar, user1IdentityId));

    Event eventToMove = agendaEventService.getEventById(eventId, null, user1IdentityId).clone();
    eventToMove.setCalendarId(spaceCalendar.getId());
    try {
      agendaEventService.updateEvent(eventToMove,
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     Collections.emptyList(),
                                     null,
                                     null,
                                     false,
                                     user1IdentityId);
      fail("Shouldn't allow to move an event into a space calendar where the user can't create events");
    } catch (IllegalAccessException e) {
      // Expected
    }
    assertEquals("The event must not have moved", calendar.getId(), agendaEventService.getEventById(eventId).getCalendarId());
  }

  /**
   * Legitimate calendar changes through a field patch keep working
   * (EXO-90381): the owner moves an event between their own calendars, a space
   * redactor moves it into the space calendar, and a patch that keeps the
   * event in its calendar needs no right to add events there.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testUpdateEventFieldsLegitimateCalendarChangesSucceed() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());

    Event eventInstance = newEventInstance(start, start.plusHours(1), false);
    eventInstance.setRecurrence(null);
    Event createdEvent = createEvent(eventInstance.clone(), user1IdentityId, testuser1Identity);
    long eventId = createdEvent.getId();

    org.exoplatform.agenda.model.Calendar secondCalendar =
                                                          new org.exoplatform.agenda.model.Calendar(0, user1IdentityId, false, null, null, null, null, null, null);
    secondCalendar.setName("Second calendar");
    secondCalendar = agendaCalendarService.createCalendar(secondCalendar, testuser1Identity.getRemoteId());
    try {
      // 1. Between the owner's own calendars
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(secondCalendar.getId())),
                                           false,
                                           false,
                                           user1IdentityId);
      assertEquals("The event must have moved to the user's second calendar",
                   secondCalendar.getId(),
                   agendaEventService.getEventById(eventId).getCalendarId());

      // 2. A space redactor into the space calendar
      spaceService.addRedactor(space, testuser1Identity.getRemoteId());
      agendaEventService.updateEventFields(eventId,
                                           getFields("calendarId", String.valueOf(spaceCalendar.getId())),
                                           false,
                                           false,
                                           user1IdentityId);
      assertEquals("The event must have moved to the space calendar",
                   spaceCalendar.getId(),
                   agendaEventService.getEventById(eventId).getCalendarId());

      // 3. Keeping the calendar: no longer a redactor, still the event's creator
      spaceService.addRedactor(space, testuser3Identity.getRemoteId());
      spaceService.removeRedactor(space, testuser1Identity.getRemoteId());
      assertFalse("testuser1 isn't a redactor of the space anymore",
                  agendaEventService.canCreateEvent(spaceCalendar, user1IdentityId));
      Map<String, List<String>> fields = getFields("calendarId", String.valueOf(spaceCalendar.getId()));
      fields.put("summary", Collections.singletonList("Kept in its calendar"));
      agendaEventService.updateEventFields(eventId, fields, false, false, user1IdentityId);
      Event storedEvent = agendaEventService.getEventById(eventId);
      assertEquals(spaceCalendar.getId(), storedEvent.getCalendarId());
      assertEquals("Kept in its calendar", storedEvent.getSummary());
    } finally {
      agendaCalendarService.deleteCalendarById(secondCalendar.getId());
    }
  }

  /**
   * Creating an exceptional occurrence of a recurring event through
   * {@code createEvent} (an event carrying a parent, as the web UI does when a
   * single computed occurrence is edited) requires the right to update that
   * series (EXO-90381): the right to add events to one's own calendar isn't
   * enough, or anyone could remove an occurrence of someone else's series from
   * its owner's views. A parent that doesn't exist is reported as not found,
   * and the creator of the series still creates its occurrences.
   *
   * @throws Exception when a service call fails unexpectedly
   */
  @Test
  public void testCreateEventOccurrenceRequiresUpdateRightOnSeries() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long user1IdentityId = Long.parseLong(testuser1Identity.getId());
    long user2IdentityId = Long.parseLong(testuser2Identity.getId());

    Event series = createEvent(newEventInstance(start, start.plusHours(1), false), user1IdentityId, testuser1Identity);
    long seriesId = series.getId();
    ZonedDateTime periodStart = start.minusDays(1);
    ZonedDateTime periodEnd = start.plusDays(5);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, periodStart, periodEnd, series.getTimeZoneId(), 0);
    assertTrue("The series must have several occurrences", occurrences.size() > 1);
    Event occurrence = occurrences.get(1);

    org.exoplatform.agenda.model.Calendar user2Calendar = agendaCalendarService.getOrCreateCalendarByOwnerId(user2IdentityId);
    try {
      assertFalse("testuser2 can't update testuser1's series", agendaEventService.canUpdateEvent(series, user2IdentityId));
      assertTrue("testuser2 can add events to his own calendar",
                 agendaEventService.canCreateEvent(user2Calendar, user2IdentityId));

      // 1. Another user, into his own calendar
      try {
        createEvent(newOccurrenceInstance(seriesId, occurrence, user2Calendar.getId()), user2IdentityId);
        fail("Shouldn't allow to create an occurrence of a series the user can't update");
      } catch (IllegalAccessException e) {
        // Expected
      }
      assertEquals("The occurrence must still be in the series",
                   occurrences.size(),
                   agendaEventService.getEventOccurrencesInPeriod(series, periodStart, periodEnd, series.getTimeZoneId(), 0).size());

      // 2. A parent that doesn't exist is reported as not found
      try {
        createEvent(newOccurrenceInstance(Integer.MAX_VALUE, occurrence, user2Calendar.getId()), user2IdentityId);
        fail("Shouldn't allow to create an occurrence of a series that doesn't exist");
      } catch (AgendaException e) {
        assertEquals(AgendaExceptionType.EVENT_NOT_FOUND, e.getAgendaExceptionType());
      }

      // 3. The creator of the series, in its calendar
      Event created = createEvent(newOccurrenceInstance(seriesId, occurrence, calendar.getId()), user1IdentityId);
      assertEquals(seriesId, created.getParentId());
      assertTrue("The creator's occurrence must be listed as an exceptional occurrence",
                 agendaEventService.getExceptionalOccurrenceEvents(seriesId, null, user1IdentityId)
                                   .stream()
                                   .anyMatch(exceptional -> exceptional.getId() == created.getId()));
    } finally {
      agendaCalendarService.deleteCalendarById(user2Calendar.getId());
    }
  }

  /**
   * Builds the payload the web UI sends when a single computed occurrence of a
   * series is edited: no identifier, the series as parent, the occurrence
   * identifier and dates, and no recurrence.
   *
   * @param parentId the identifier of the series
   * @param occurrence the computed occurrence being edited
   * @param calendarId the calendar the occurrence is filed into
   * @return the event to pass to {@code createEvent}
   */
  private Event newOccurrenceInstance(long parentId, Event occurrence, long calendarId) {
    Event event = occurrence.clone();
    event.setId(0);
    event.setParentId(parentId);
    event.setCalendarId(calendarId);
    event.setRecurrence(null);
    event.setOccurrence(new EventOccurrence(occurrence.getOccurrence().getId()));
    return event;
  }

  /**
   * Pre-existing gap closed with eXIP 7.3.0.20 (EXO-89479, closing review):
   * getEventOccurrence checked canAccessEvent only when a stored exceptional
   * occurrence existed, so a computed occurrence of any recurring event was
   * served to any authenticated caller through
   * GET /v1/agenda/events/occurrence/{parentEventId}/{occurrenceId}. The check
   * now runs on the parent, for both branches.
   */
  @Test
  public void testGetEventOccurrenceRefusesWhoeverCannotAccessTheParent() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long memberId = Long.parseLong(testuser2Identity.getId()); // member of the space, not invited
    long outsiderId = Long.parseLong(testuser4Identity.getId()); // neither member nor invited

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 2);
    ZonedDateTime occurrenceId = occurrences.get(1).getOccurrence().getId();

    // A computed occurrence: served to whoever can access the series
    assertNotNull(agendaEventService.getEventOccurrence(series.getId(), occurrenceId, ZoneOffset.UTC, memberId));
    try {
      agendaEventService.getEventOccurrence(series.getId(), occurrenceId, ZoneOffset.UTC, outsiderId);
      fail("a user who can't access the series can't read one of its computed occurrences");
    } catch (IllegalAccessException e) {
      // Expected
    }

    // Once stored as an exceptional occurrence, same rule (as before)
    Event exceptionalOccurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(), occurrenceId);
    assertNotNull(exceptionalOccurrence);
    assertNotNull(agendaEventService.getEventOccurrence(series.getId(), occurrenceId, ZoneOffset.UTC, memberId));
    try {
      agendaEventService.getEventOccurrence(series.getId(), occurrenceId, ZoneOffset.UTC, outsiderId);
      fail("a user who can't access the series can't read one of its exceptional occurrences");
    } catch (IllegalAccessException e) {
      // Expected
    }
  }

  /**
   * The check is on the event served, not on the parent for every branch:
   * someone invited on one stored occurrence only, neither a member of the
   * space nor an attendee of the series, reads that occurrence through the
   * occurrence address exactly as by id (the event dialog refreshes a stored
   * occurrence through that address), and still nothing else of the series.
   */
  @Test
  public void testGetEventOccurrenceServesAnInviteeOfTheStoredOccurrenceOnly() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long rowInviteeId = Long.parseLong(testuser5Identity.getId()); // not a member, not on the series

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 3);

    // The organiser invites testuser5 on one occurrence only (the form's
    // "this occurrence" save, or the attendee drawer of that occurrence)
    Event storedOccurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                              occurrences.get(0).getOccurrence().getId());
    // The address the client uses is the one the row carries on the wire
    ZonedDateTime storedOccurrenceId = storedOccurrence.getOccurrence().getId();
    ZonedDateTime computedOccurrenceId = occurrences.get(2).getOccurrence().getId();
    // Both branches are genuinely exercised below
    assertNotNull("the stored row must be reachable by its own occurrence id",
                  agendaEventService.getExceptionalOccurrenceEvent(series.getId(), storedOccurrenceId));
    assertNull("the other date must have no stored row, else the computed branch is not exercised",
               agendaEventService.getExceptionalOccurrenceEvent(series.getId(), computedOccurrenceId));
    List<EventAttendee> rowAttendees = new ArrayList<>(agendaEventAttendeeService.getEventAttendees(storedOccurrence.getId())
                                                                                  .getEventAttendees());
    rowAttendees.add(new EventAttendee(0, storedOccurrence.getId(), rowInviteeId, null));
    agendaEventAttendeeService.saveEventAttendees(agendaEventService.getEventById(storedOccurrence.getId()),
                                                  rowAttendees,
                                                  creatorId,
                                                  false,
                                                  false,
                                                  new AgendaEventModification(storedOccurrence.getId(),
                                                                              storedOccurrence.getCalendarId(),
                                                                              creatorId));
    assertTrue(agendaEventAttendeeService.isEventAttendee(storedOccurrence.getId(), rowInviteeId));
    assertFalse(agendaEventAttendeeService.isEventAttendee(series.getId(), rowInviteeId));

    // Served through the occurrence address, as by id
    assertNotNull(agendaEventService.getEventOccurrence(series.getId(), storedOccurrenceId, ZoneOffset.UTC, rowInviteeId));
    assertNotNull(agendaEventService.getEventById(storedOccurrence.getId(), ZoneOffset.UTC, rowInviteeId));
    // and refused on a computed occurrence of the same series, whose parent they can't access
    try {
      agendaEventService.getEventOccurrence(series.getId(), computedOccurrenceId, ZoneOffset.UTC, rowInviteeId);
      fail("an invitee of one stored occurrence can't read the other occurrences of the series");
    } catch (IllegalAccessException e) {
      // Expected
    }
  }

  /*
   * eXIP 7.3.0.20, US06 prerequisite: a change on a series reaches the dates
   * that were individually modified instead of deleting them. Until this
   * change, any save of a recurrent event dropped every exceptional occurrence
   * it had, so correcting a summary discarded a room changed on one date, a
   * cancelled date, and the attendees and answers attached to both.
   */

  @Test
  public void testSeriesChangeKeepsTheDatesThatWereIndividuallyModified() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity, testuser2Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 2);

    // The organiser changes the room of one date only
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                        occurrences.get(1).getOccurrence().getId());
    List<EventAttendee> occurrenceAttendees = agendaEventAttendeeService.getEventAttendees(occurrence.getId())
                                                                       .getEventAttendees();
    Event occurrenceToUpdate = agendaEventService.getEventById(occurrence.getId(), ZoneOffset.UTC, creatorId).clone();
    occurrenceToUpdate.setLocation("room B");
    agendaEventService.updateEvent(occurrenceToUpdate,
                                   occurrenceAttendees,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    assertEquals("room B", agendaEventService.getEventById(occurrence.getId()).getLocation());

    // The organiser's next gesture is another request
    restartTransaction();

    // then corrects the summary of the whole series
    Event seriesToUpdate = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    seriesToUpdate.setSummary("weekly sync");
    agendaEventService.updateEvent(seriesToUpdate,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);

    Event keptOccurrence = agendaEventService.getEventById(occurrence.getId());
    assertNotNull("a change on the series must not delete the dates that were individually modified", keptOccurrence);
    assertEquals("the property customised on that date is kept", "room B", keptOccurrence.getLocation());
    assertEquals("the property changed on the series reaches that date", "weekly sync", keptOccurrence.getSummary());
    assertFalse("the attendee list of that date survives with it",
                agendaEventAttendeeService.getEventAttendees(keptOccurrence.getId()).getEventAttendees().isEmpty());
  }

  @Test
  public void testSeriesChangeDropsADateItsRecurrenceNoLongerProduces() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 3);
    Event lastOccurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                            occurrences.get(2).getOccurrence().getId());
    assertNotNull(agendaEventService.getEventById(lastOccurrence.getId()));

    // The organiser's next gesture is another request
    restartTransaction();

    // The organiser makes the meeting weekly instead of daily, so the days in
    // between are no longer produced
    ZonedDateTime lastOccurrenceId = lastOccurrence.getOccurrence().getId();
    Event seriesToUpdate = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    seriesToUpdate.getRecurrence().setType(EventRecurrenceType.WEEKLY);
    seriesToUpdate.getRecurrence().setFrequency(EventRecurrenceFrequency.WEEKLY);
    agendaEventService.updateEvent(seriesToUpdate,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);

    Event reloadedSeries = agendaEventService.getEventById(series.getId());
    assertNotNull(reloadedSeries.getRecurrence());
    LocalDate droppedDay = lastOccurrenceId.withZoneSameInstant(ZoneOffset.UTC).toLocalDate();
    assertTrue("precondition: the shortened recurrence must no longer produce that date",
               Utils.getOccurrences(reloadedSeries, droppedDay.minusDays(1), droppedDay.plusDays(1), 3)
                    .stream()
                    .noneMatch(occ -> occ.getOccurrence() != null
                        && occ.getOccurrence().getId().withZoneSameInstant(ZoneOffset.UTC).toLocalDate().isEqual(droppedDay)));

    assertNull("a date the recurrence no longer produces is the one case where its row is dropped",
               agendaEventService.getEventById(lastOccurrence.getId()));
  }

  /**
   * The people a series gains and loses reach the dates individually modified,
   * while the people invited on one of those dates alone stay there — the
   * per-property rule of the PO (2026-09-23) applied to a set. Before the dates
   * survived a series save this held by accident: the rows were deleted and
   * rebuilt from the new list.
   */
  @Test
  public void testSeriesMembershipChangeReachesTheDatesIndividuallyModified() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long removedId = Long.parseLong(testuser2Identity.getId());
    long addedId = Long.parseLong(testuser3Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity, testuser2Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 2);
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                        occurrences.get(1).getOccurrence().getId());
    assertTrue(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), removedId));
    assertFalse(agendaEventAttendeeService.isEventAttendee(occurrence.getId(), addedId));

    restartTransaction();

    // The participant about to be removed has set a reminder on that date
    List<EventReminder> removedAttendeeReminders = new ArrayList<>();
    removedAttendeeReminders.add(new EventReminder(removedId, 30, ReminderPeriodType.MINUTE));
    agendaEventReminderService.saveEventReminders(agendaEventService.getEventById(occurrence.getId()),
                                                  removedAttendeeReminders,
                                                  removedId);
    restartTransaction();
    assertFalse("precondition: the reminder is stored on that date",
                agendaEventReminderService.getEventReminders(occurrence.getId(), removedId).isEmpty());

    // The organiser swaps one participant for another on the whole series
    Event seriesToUpdate = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    List<EventAttendee> newSeriesAttendees = new ArrayList<>();
    newSeriesAttendees.add(new EventAttendee(0, series.getId(), creatorId, null));
    newSeriesAttendees.add(new EventAttendee(0, series.getId(), addedId, null));
    agendaEventService.updateEvent(seriesToUpdate,
                                   newSeriesAttendees,
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);

    assertFalse("someone removed from the series loses the dates individually modified too",
                agendaEventAttendeeService.isEventAttendee(occurrence.getId(), removedId));
    assertTrue("and someone added to the series gains them",
               agendaEventAttendeeService.isEventAttendee(occurrence.getId(), addedId));
    // A reminder is sent from its own stored trigger date with no attendance
    // check, so leaving it behind would keep reminding someone of a date they
    // are no longer invited to. The row used to be deleted and the database
    // cascaded them; now it is the merge's job
    assertTrue("and their reminders on that date go with them",
               agendaEventReminderService.getEventReminders(occurrence.getId(), removedId).isEmpty());
  }

  /**
   * The date half of the rule: a date whose times were moved on purpose keeps
   * them when the series moves, a date that never was follows the series.
   */
  @Test
  public void testOccurrenceTimesFollowTheSeriesUnlessTheyWereMoved() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0).withHour(9).withMinute(0).withSecond(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 2);
    Event followingDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                           occurrences.get(0).getOccurrence().getId());
    Event movedDate = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                       occurrences.get(1).getOccurrence().getId());
    restartTransaction();

    // One date is moved two hours later on its own
    Event movedUpdate = agendaEventService.getEventById(movedDate.getId(), ZoneOffset.UTC, creatorId).clone();
    ZonedDateTime movedStart = movedUpdate.getStart().plusHours(2);
    movedUpdate.setStart(movedStart);
    movedUpdate.setEnd(movedUpdate.getEnd().plusHours(2));
    agendaEventService.updateEvent(movedUpdate,
                                   agendaEventAttendeeService.getEventAttendees(movedDate.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    assertTrue("precondition: saving an occurrence with other times marks it as moved",
               agendaEventService.getEventById(movedDate.getId()).getOccurrence().isDatesModified());
    ZonedDateTime keptStart = agendaEventService.getEventById(movedDate.getId()).getStart();
    ZonedDateTime followingStartBefore = agendaEventService.getEventById(followingDate.getId()).getStart();

    restartTransaction();

    // The organiser then moves the whole series one hour later
    Event seriesToUpdate = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    seriesToUpdate.setStart(seriesToUpdate.getStart().plusHours(1));
    seriesToUpdate.setEnd(seriesToUpdate.getEnd().plusHours(1));
    agendaEventService.updateEvent(seriesToUpdate,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);

    assertEquals("a date that was never moved follows the series",
                 followingStartBefore.plusHours(1).toInstant(),
                 agendaEventService.getEventById(followingDate.getId()).getStart().toInstant());
    assertEquals("a date moved on purpose keeps its own times",
                 keptStart.toInstant(),
                 agendaEventService.getEventById(movedDate.getId()).getStart().toInstant());
  }

  /**
   * The merge is reachable from two entry points: the full save above, and a
   * patch that says it applies to every occurrence — the one the padlock of the
   * event page and a drag on the calendar both use.
   */
  @Test
  public void testWholeSeriesPatchKeepsTheDatesIndividuallyModified() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());

    Event event = newEventInstance(start, start, true);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 2);
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                        occurrences.get(1).getOccurrence().getId());
    restartTransaction();

    Event occurrenceToUpdate = agendaEventService.getEventById(occurrence.getId(), ZoneOffset.UTC, creatorId).clone();
    occurrenceToUpdate.setLocation("room B");
    agendaEventService.updateEvent(occurrenceToUpdate,
                                   agendaEventAttendeeService.getEventAttendees(occurrence.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);
    restartTransaction();

    // The organiser patches the summary of every occurrence
    agendaEventService.updateEventFields(series.getId(), getFields("summary", "weekly sync"), true, false, creatorId);

    Event keptOccurrence = agendaEventService.getEventById(occurrence.getId());
    assertNotNull("a patch applied to every occurrence must not delete the dates individually modified", keptOccurrence);
    assertEquals("the property customised on that date is kept", "room B", keptOccurrence.getLocation());
    assertEquals("the patched property reaches that date", "weekly sync", keptOccurrence.getSummary());
  }

  /**
   * When a date follows a move of its series, the reminders of every attendee
   * follow it too. A trigger date is stored, never recomputed when it falls
   * due, and the recomputation that reaches occurrences elsewhere works on the
   * acting user alone — so the attendee who is not the one saving is the case
   * to pin.
   */
  @Test
  public void testRemindersOfEveryAttendeeFollowAMovedOccurrence() throws Exception { // NOSONAR
    ZonedDateTime start = getDate().withNano(0).withHour(9).withMinute(0).withSecond(0);
    long creatorId = Long.parseLong(testuser1Identity.getId());
    long otherAttendeeId = Long.parseLong(testuser2Identity.getId());

    Event event = newEventInstance(start, start.plusHours(1), false);
    event.setCalendarId(spaceCalendar.getId());
    Event series = createEvent(event.clone(), creatorId, testuser1Identity, testuser2Identity);
    List<Event> occurrences = agendaEventService.getEventOccurrencesInPeriod(series, start, start.plusDays(2), ZoneOffset.UTC, 0);
    assertTrue(occurrences.size() >= 1);
    Event occurrence = agendaEventService.saveEventExceptionalOccurrence(series.getId(),
                                                                        occurrences.get(0).getOccurrence().getId());
    restartTransaction();

    // The other attendee sets a reminder of their own on that date
    List<EventReminder> otherAttendeeReminders = new ArrayList<>();
    otherAttendeeReminders.add(new EventReminder(otherAttendeeId, 30, ReminderPeriodType.MINUTE));
    agendaEventReminderService.saveEventReminders(agendaEventService.getEventById(occurrence.getId()),
                                                  otherAttendeeReminders,
                                                  otherAttendeeId);
    restartTransaction();

    List<EventReminder> before = agendaEventReminderService.getEventReminders(occurrence.getId(), otherAttendeeId);
    assertFalse("precondition: the other attendee has a reminder on that date", before.isEmpty());
    ZonedDateTime triggerBefore = before.get(0).getDatetime();
    assertNotNull(triggerBefore);

    // The organiser moves the whole series one hour later
    Event seriesToUpdate = agendaEventService.getEventById(series.getId(), ZoneOffset.UTC, creatorId).clone();
    seriesToUpdate.setStart(seriesToUpdate.getStart().plusHours(1));
    seriesToUpdate.setEnd(seriesToUpdate.getEnd().plusHours(1));
    agendaEventService.updateEvent(seriesToUpdate,
                                   agendaEventAttendeeService.getEventAttendees(series.getId()).getEventAttendees(),
                                   Collections.emptyList(),
                                   Collections.emptyList(),
                                   null,
                                   null,
                                   false,
                                   creatorId);

    List<EventReminder> after = agendaEventReminderService.getEventReminders(occurrence.getId(), otherAttendeeId);
    assertFalse("the reminder of an attendee who is not saving must survive the move", after.isEmpty());
    assertEquals("and must point at the new time, not the old one",
                 triggerBefore.plusHours(1).toInstant(),
                 after.get(0).getDatetime().toInstant());
  }
}
