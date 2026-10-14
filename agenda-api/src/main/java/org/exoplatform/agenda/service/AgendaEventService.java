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

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import org.exoplatform.agenda.constant.EventAccess;
import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.exception.CapacityBelowAcceptedException;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaEventService {

  /**
   * Broadcast once a colleague holding an <b>edit share</b> on a personal
   * calendar (EXO-90378) — and holding no older right over the event — added,
   * changed or removed one of its events; source the
   * {@link org.exoplatform.agenda.model.CalendarEditorChange}, data the
   * calendar owner's identity identifier.
   * <p>
   * Raised by the event service alone, which is the one place that knows by
   * what right a writer wrote. The owner is told about the one case they
   * cannot otherwise see coming; a change of their own, of a space manager, of
   * the event's creator or of an attendee allowed to update raises nothing.
   */
  String CALENDAR_EDITED_BY_SHAREE_EVENT = "exo.agenda.calendar.editedBySharee";

  /**
   * Retrieves the list of events available for a designated user filtered by
   * criteria defined in a filter.
   * <p>
   * The filter's {@code calendarIds} ({@link EventFilter}) name the
   * calendars other users shared with the reader (EXO-90357): each is checked
   * against the reader — accessible, or shared with them — and the whole
   * request is refused otherwise. A private event of a shared calendar comes
   * back masked ({@code Event.isMasked()}).
   *
   * @param eventFilter a filter used to define criteria to get list of objects
   * @param userIdentityId user {@link Identity} identifier
   * @param userTimeZone User time zone
   * @return {@link List} of {@link Event} accessible to user
   * @throws IllegalAccessException when user is not allowed to access events,
   *           or names a calendar in {@code calendarIds} they may not read
   */
  List<Event> getEvents(EventFilter eventFilter,
                        ZoneId userTimeZone,
                        long userIdentityId) throws IllegalAccessException;

  /**
   * Retrieve parent recurrent events switch event filter.
   * 
   * @param start start of period of events to retrieve
   * @param end end of period of events to retrieve
   * @param timeZone used time zone to compute dates
   * @return {@link List} of {@link Event}
   */
  List<Event> getParentRecurrentEvents(ZonedDateTime start, ZonedDateTime end, ZoneId timeZone);

  /**
   * Retrieves an event identified by its technical identifier.
   * 
   * @param eventId technical identifier of event
   * @param timeZone used timezone to convert dates
   * @param identityId {@link Identity} technical identifier
   * @return Corresponding {@link Event} or null if not found
   * @throws IllegalAccessException when user is not allowed to access event
   */
  Event getEventById(long eventId, ZoneId timeZone, long identityId) throws IllegalAccessException;

  /**
   * Retrieves an event identified by its technical identifier.
   * 
   * @param eventId technical identifier of event
   * @return Corresponding {@link Event} or null if not found
   */
  Event getEventById(long eventId);

  /**
   * Retrieves Event Occurrence identified by parent recurrence Event identifier
   * and occurrence date
   * 
   * @param parentEventId {@link Event} technical identifier
   * @param occurrenceId Date of occurrence
   * @param userTimeZone used user timezone
   * @param identityId user {@link Identity} identifier requesting event
   * @return {@link Event} representing occurrence of parent recurrent event
   * @throws IllegalAccessException when the user can't access the event
   *           served (see {@link #canAccessEvent(Event, long)}): a stored
   *           exceptional occurrence is checked through its own row, as an
   *           event of its own; a computed occurrence through its parent
   */
  Event getEventOccurrence(long parentEventId,
                           ZonedDateTime occurrenceId,
                           ZoneId userTimeZone,
                           long identityId) throws IllegalAccessException;

  /**
   * Retrieves the list of exceptional occurrences of an event identified by its
   * technical identifier.
   * 
   * @param eventId technical identifier of parent recurrent event
   * @param timeZone used timezone to compute events
   * @param userIdentityId
   * @return list of Exceptional {@link Event}s if found, else null
   * @throws IllegalAccessException when user is not allowed to access event
   */
  List<Event> getExceptionalOccurrenceEvents(long eventId, ZoneId timeZone, long userIdentityId) throws IllegalAccessException;

  /**
   * Moves the exceptional occurrences of a recurring event dated from a given
   * day on under another recurring event: the second half of a "this and
   * upcoming" split, whose continuing series is created first so that the
   * dates the organiser had customised from the split point on survive the
   * shortening of the first series instead of being dropped with their
   * answers. Each moved row is then merged into its new series as a date
   * individually modified is merged into a series change: what was customised
   * on the date is kept, what was not follows the new series, and a day the
   * new series does not produce is dropped, as a series change drops it. A
   * row the new series already holds for a date a kept row moved to — the
   * reminder computing listener materialises the next days of a series on
   * its creation — is deleted when it carries nothing of its own against the
   * new series, so that a date keeps one row.
   *
   * @param fromEventId the recurring event the rows belong to
   * @param toEventId the recurring event they move under, in the same calendar
   * @param fromDate the occurrence the split starts from, inclusive, compared
   *          as occurrences are, within twelve hours
   * @param userIdentityId who is splitting; must be allowed to update both
   *          events
   * @return the identifiers of the rows moved and kept by the merge
   * @throws IllegalAccessException when the user may not update one of the two
   *           events
   * @throws AgendaException when one of the two events does not exist
   * @throws IllegalArgumentException when the two events are one, when one of
   *           them is not a recurring series or when they live in different
   *           calendars; the message is the code
   *           {@code agenda.seriesSplit.notAllowed}
   */
  List<Long> moveExceptionalOccurrences(long fromEventId,
                                        long toEventId,
                                        ZonedDateTime fromDate,
                                        long userIdentityId) throws IllegalAccessException, AgendaException;

  /**
   * Splits a recurring event at one of its dates: a "this and upcoming"
   * change. A series continuing the first one from that date on is created,
   * the dates customised from it on move under it, and the first series is
   * shortened to the day before, all in one transaction.
   * <p>
   * The continuation keeps the series' creator and calendar, and takes from
   * the change only the fields {@link EventSplit#event()} lists. When the
   * change moves no date (same first date, times, duration, time zone,
   * all-day flag and recurrence pattern), every attendee keeps their answers
   * and their places on the waiting list, which takes the seats the change
   * frees; when it moves one, every answer but the user's starts over.
   *
   * @param seriesId technical identifier of the recurring event
   * @param split what the change asks of the continuation
   * @param splitFrom the occurrence id of the continuation's first date, a
   *          date of the series after its first one
   * @param sendInvitation whether the attendees are notified
   * @param confirmClose whether the user confirmed a change that closes the
   *          event while people are waiting
   * @param userIdentityId who is splitting; must be allowed to update the
   *          series and to create an event in its calendar
   * @return the continuation, as stored
   * @throws IllegalAccessException when the user may not update the series or
   *           create an event in its calendar
   * @throws AgendaException when the series does not exist, or the
   *           continuation is invalid
   * @throws CapacityBelowAcceptedException when the continuation's limit, or
   *           the limit a moved date gets, is below the Yes answers of a date
   * @throws IllegalArgumentException with the code
   *           {@code agenda.seriesSplit.notAllowed} when the event is not a
   *           series or the date is not one of its dates after the first,
   *           {@code agenda.capacity.invalid} for a negative limit, and
   *           {@code agenda.capacity.closeNeedsConfirmation} when the change
   *           closes the event while people are waiting and is not confirmed
   */
  Event splitEvent(long seriesId,
                   EventSplit split,
                   ZonedDateTime splitFrom,
                   boolean sendInvitation,
                   boolean confirmClose,
                   long userIdentityId) throws IllegalAccessException, AgendaException;

  /**
   * Retrieves the exceptional occurrence a recurrent event holds for one of
   * its dates: the row whose occurrence id designates the same date of the
   * series — a timed id read in the series' zone, an all-day id as the later
   * of its UTC date and its date in that zone — or, on a series with several
   * dates a day, the row stored at that instant; the first created when the
   * date has several.
   *
   * @param eventId technical identifier of parent recurrent event
   * @param occurrenceId technical occurrence event identifier
   * @return Exceptional {@link Event} if found, else null
   */
  Event getExceptionalOccurrenceEvent(long eventId, ZonedDateTime occurrenceId);

  /**
   * Creates the exceptional occurrence of one date of a parent recurrent
   * event, under the lock of the series. A date has one row: when it already
   * has one, as {@link #getExceptionalOccurrenceEvent} finds it, that row is
   * returned as it is, and the attendees, conferences and reminders given are
   * not applied to it.
   *
   * @param eventId technical identifier of parent recurrent event
   * @param attendees {@link List} of attendees of a row created
   * @param conferences {@link List} of conferences of a row created
   * @param reminders {@link List} of reminders of a row created
   * @param occurrenceId event occurent identifier
   * @return the {@link Event} created, or the date's existing row
   * @throws AgendaException when the event attributes aren't valid
   */
  Event createEventExceptionalOccurrence(long eventId,
                                         List<EventAttendee> attendees,
                                         List<EventConference> conferences,
                                         List<EventReminder> reminders,
                                         ZonedDateTime occurrenceId) throws AgendaException;

  /**
   * Finds the exceptional occurrence of one date of a recurrent event, as
   * {@link #getExceptionalOccurrenceEvent} does, or creates it as a copy of
   * the series, with the series' attendees valid at that date, its
   * conferences and its reminders, under the lock of the series.
   *
   * @param eventId parent recurrent event identifier
   * @param occurrenceId Occurrence identifier
   * @return the date's existing row, or the {@link Event} created
   * @throws AgendaException when an error occurs hile creating event
   */
  Event saveEventExceptionalOccurrence(long eventId, ZonedDateTime occurrenceId) throws AgendaException;

  /**
   * Creates an event in designated calendar or in user personal calendar if
   * null
   * 
   * @param event {@link Event} to create
   * @param attendees event attendees of type {@link EventAttendee}
   * @param conferences event conferences of type {@link EventConference}
   * @param reminders {@link List} of preferred user reminders of type
   *          {@link EventReminder}
   * @param dateOptions {@link List} of {@link EventDateOption} of corresponding
   *          event
   * @param remoteEvent {@link RemoteEvent} of synchronized event by user to
   *          remote connector
   * @param sendInvitation whether send invitation to attendees or not
   * @param userIdentityId user {@link Identity} identifier
   * @return Created {@link Event} with technichal identifier
   * @throws IllegalAccessException when user is not allowed to create event on
   *           calendar, or creates an exceptional occurrence of a recurring
   *           event (a parent) that they aren't allowed to update
   * @throws AgendaException when the event attributes aren't valid, or when the
   *           parent event isn't found
   * @throws IllegalArgumentException with the message code
   *           {@code agenda.capacity.invalid} when the capacity asked is
   *           negative; on a date of a series that got a row meanwhile, which
   *           is then updated, the codes of
   *           {@link #updateEvent(Event, List, List, List, List, RemoteEvent, boolean, boolean, long)}
   */
  Event createEvent(Event event,
                    List<EventAttendee> attendees,
                    List<EventConference> conferences,
                    List<EventReminder> reminders,
                    List<EventDateOption> dateOptions,
                    RemoteEvent remoteEvent,
                    boolean sendInvitation,
                    long userIdentityId) throws IllegalAccessException, AgendaException;

  /**
   * Updates an existing event in-place when the user is owner of parent
   * {@link Calendar}, else this will duplicate the event and add it into his
   * personal calendar
   * 
   * @param event {@link Event} to create
   * @param attendees event attendees of type {@link EventAttendee}
   * @param conferences event conferences of type {@link EventConference}
   * @param reminders {@link List} of preferred user reminders of type
   *          {@link EventReminder}
   * @param dateOptions {@link List} of {@link EventDateOption} of corresponding
   *          event
   * @param remoteEvent {@link RemoteEvent} of synchronized event by user to
   *          remote connector
   * @param sendInvitation whether re-send invitation to attendees or not
   * @param userIdentityId user {@link Identity} identifier
   * @return Updated {@link Event}
   * @throws IllegalAccessException when user is not allowed to update event,
   *           or moves it to another calendar in which they can't create events
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   * @throws AgendaException when the event attributes aren't valid
   */
  default Event updateEvent(Event event,
                            List<EventAttendee> attendees,
                            List<EventConference> conferences,
                            List<EventReminder> reminders,
                            List<EventDateOption> dateOptions,
                            RemoteEvent remoteEvent,
                            boolean sendInvitation,
                            long userIdentityId) throws IllegalAccessException, ObjectNotFoundException, AgendaException {
    return updateEvent(event,
                       attendees,
                       conferences,
                       reminders,
                       dateOptions,
                       remoteEvent,
                       sendInvitation,
                       false,
                       userIdentityId);
  }

  /**
   * Updates an existing event, as
   * {@link #updateEvent(Event, List, List, List, List, RemoteEvent, boolean, long)}
   * does, once the user has confirmed closing it when the save closes an open
   * event whose waiting list it would confirm.
   *
   * @param event {@link Event} to update
   * @param attendees event attendees of type {@link EventAttendee}
   * @param conferences event conferences of type {@link EventConference}
   * @param reminders {@link List} of preferred user reminders of type
   *          {@link EventReminder}
   * @param dateOptions {@link List} of {@link EventDateOption} of corresponding
   *          event
   * @param remoteEvent {@link RemoteEvent} of synchronized event by user to
   *          remote connector
   * @param sendInvitation whether re-send invitation to attendees or not
   * @param confirmClose whether the user confirmed that closing the event
   *          removes its capacity limit and confirms its waiting list
   * @param userIdentityId user {@link Identity} identifier
   * @return Updated {@link Event}
   * @throws IllegalAccessException when user is not allowed to update event,
   *           or moves it to another calendar in which they can't create events
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   * @throws AgendaException when the event attributes aren't valid
   * @throws IllegalArgumentException with the message code
   *           {@code agenda.capacity.invalid} or
   *           {@code agenda.capacity.belowAccepted} when the capacity asked is
   *           refused, the latter a {@link CapacityBelowAcceptedException}
   *           naming the date of a series that blocks,
   *           {@code agenda.capacity.closeNeedsConfirmation} when the save
   *           closes an event with a waiting list without confirmClose
   */
  Event updateEvent(Event event,
                    List<EventAttendee> attendees,
                    List<EventConference> conferences,
                    List<EventReminder> reminders,
                    List<EventDateOption> dateOptions,
                    RemoteEvent remoteEvent,
                    boolean sendInvitation,
                    boolean confirmClose,
                    long userIdentityId) throws IllegalAccessException, ObjectNotFoundException, AgendaException;

  /**
   * Updates a single field for a given {@link Event} identified by its
   * technical identifier. When the event is of type 'recurrent', if parameter
   * updateAllOccurrences is set to true, it will update the event field for all
   * exceptional occurrences as well.
   * 
   * @param eventId Event technical identifier to update
   * @param fields {@link Event} fields with field name as map key and field
   *          value as map value
   * @param updateAllOccurrences whether apply modification on all exceptional
   *          occurrences as well or not
   * @param sendInvitation whether re-send invitation to attendees or not
   * @param userIdentityId user {@link Identity} identifier
   * @throws IllegalAccessException when user is not allowed to update event,
   *           or moves it to another calendar in which they can't create events
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   * @throws AgendaException when the event attribute isn't valid
   */
  default void updateEventFields(long eventId,
                                 Map<String, List<String>> fields,
                                 boolean updateAllOccurrences,
                                 boolean sendInvitation,
                                 long userIdentityId) throws IllegalAccessException, ObjectNotFoundException, AgendaException {
    updateEventFields(eventId, fields, updateAllOccurrences, sendInvitation, false, userIdentityId);
  }

  /**
   * Updates fields of an event, as
   * {@link #updateEventFields(long, Map, boolean, boolean, long)} does, once
   * the user has confirmed closing it when the patch closes an open event
   * whose waiting list it would confirm.
   *
   * @param eventId Event technical identifier to update
   * @param fields {@link Event} fields with field name as map key and field
   *          value as map value
   * @param updateAllOccurrences whether apply modification on all exceptional
   *          occurrences as well or not
   * @param sendInvitation whether re-send invitation to attendees or not
   * @param confirmClose whether the user confirmed that closing the event
   *          removes its capacity limit and confirms its waiting list
   * @param userIdentityId user {@link Identity} identifier
   * @throws IllegalAccessException when user is not allowed to update event,
   *           or moves it to another calendar in which they can't create events
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   * @throws AgendaException when the event attribute isn't valid
   * @throws IllegalArgumentException with the message code
   *           {@code agenda.capacity.invalid},
   *           {@code agenda.capacity.notAllowed} or
   *           {@code agenda.capacity.belowAccepted} when the capacity asked is
   *           refused, the latter a {@link CapacityBelowAcceptedException}
   *           naming the date of a series that blocks,
   *           {@code agenda.capacity.closeNeedsConfirmation} when the patch
   *           closes an event with a waiting list without confirmClose
   */
  void updateEventFields(long eventId,
                         Map<String, List<String>> fields,
                         boolean updateAllOccurrences,
                         boolean sendInvitation,
                         boolean confirmClose,
                         long userIdentityId) throws IllegalAccessException, ObjectNotFoundException, AgendaException;

  /**
   * Deletes an existing event
   * 
   * @param eventId Event technical identifier to delete
   * @param userIdentityId user {@link Identity} identifier
   * @return deleted {@link Event}
   * @throws IllegalAccessException when user is not authorized to delete the
   *           event
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   */
  Event deleteEventById(long eventId, long userIdentityId) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * Check whether user can access event or not.
   * 
   * @param event {@link Event} to check its permission
   * @param identityId {@link Identity} technical identifier
   * @return true if the user is a member of {@link Calendar} owner
   *         {@link Identity} or is an {@link EventAttendee}, else return false.
   */
  boolean canAccessEvent(Event event, long identityId);

  /**
   * How a user may read an event (EXO-90357): fully, only through a calendar
   * share — in which case a private event is rendered as busy time — or not at
   * all. {@link #canAccessEvent} is this answer being anything but
   * {@link EventAccess#NONE}.
   *
   * @param event {@link Event} to check
   * @param identityId {@link Identity} technical identifier of the reader
   * @return the access, never null
   */
  EventAccess getEventAccess(Event event, long identityId);

  /**
   * Check whether user can update or delete an event or not.
   * 
   * @param event {@link Event} to check its permission
   * @param userIdentityId user {@link Identity} identifier
   * @return true if the user is a manager of {@link Calendar} owner
   *         {@link Identity} or is an {@link EventAttendee}, else return false.
   */
  boolean canUpdateEvent(Event event, long userIdentityId);

  /**
   * Check whether user can create an event in selected Calendar or not.
   * 
   * @param calendar of type {@link Calendar}
   * @param userIdentityId user {@link Identity} identifier
   * @return true if the user is a member of {@link Calendar} owner
   *         {@link Identity}, else return false.
   */
  boolean canCreateEvent(Calendar calendar, long userIdentityId);

  /**
   * Search the list of events available with query for the currentUser
   * 
   * @param filter {@link AgendaEventSearchFilter} used to filter the events
   * @return {@link List} of {@link EventSearchResult}
   */
  List<EventSearchResult> search(AgendaEventSearchFilter filter);

  /**
   * Retrieves occurrences, without exceptional, of a recurrent event in a given
   * period of time.
   * 
   * @param recurrentEvent {@link Event} of type recurrence
   * @param start Period start date
   * @param end Period end date
   * @param timezone used timezone to compute events
   * @param limit maximum number of occurrences to retrieve
   * @return {@link List} of {@link Event} of type Occurrence (not Exceptional
   *         occurrences)
   */
  List<Event> getEventOccurrencesInPeriod(Event recurrentEvent,
                                          ZonedDateTime start,
                                          ZonedDateTime end,
                                          ZoneId timezone,
                                          int limit);

  /**
   * Choose a selected {@link EventDateOption} for a given {@link Event}
   * 
   * @param eventId technical identifier of {@link Event}
   * @param dateOptionId Technical identifier of {@link EventDateOption}
   * @param userIdentityId user {@link Identity} identifier
   * @throws ObjectNotFoundException when {@link Event} is not found using
   *           identifier or {@link EventDateOption} is not found using
   *           technical identifier
   * @throws IllegalAccessException when user is not an allowed participant of
   *           the event who can modifies it
   */
  void selectEventDateOption(long eventId, long dateOptionId, long userIdentityId) throws ObjectNotFoundException,
                                                                                   IllegalAccessException;

  /**
   * Retrieves the list of pending date polls where the current user is invited
   *
   * @param eventFilter a filter used to define criteria to get list of objects
   * @param userTimeZone used to compute events dates switch given time zone
   * @param attendeeId user {@link Identity} identifier
   * @return {@link List} date poll {@link Event} for user
   * @throws IllegalAccessException when user is not an allowed to access one of
   *           ownerIds events
   */
  List<Event> getEventDatePolls(EventFilter eventFilter,
                                ZoneId userTimeZone,
                                long attendeeId) throws IllegalAccessException;

  /**
   * Count pending date polls where the current user is invited
   *
   * @param ownerIds {@link List} of {@link Identity} technical identifier
   * @param attendeeId user {@link Identity} identifier
   * @return {@link List} date poll {@link Event} for user
   * @throws IllegalAccessException when user is not an allowed to access one of
   *           ownerIds events
   */
  long countEventDatePolls(List<Long> ownerIds, long attendeeId) throws IllegalAccessException;

  /**
   * Retrieve pending events that the selected user didn't answered yet
   * 
   * @param ownerIds {@link List} of {@link Identity} technical identifier
   * @param userIdentityId user {@link Identity} identifier
   * @param userTimeZone User time zone
   * @param offset offset of the events to retrieve
   * @param limit Limit of events to retrieve
   * @return {@link List} of {@link Event}
   * @throws IllegalAccessException when user is not an allowed to access one of
   *           ownerIds events
   */
  List<Event> getPendingEvents(List<Long> ownerIds,
                               long userIdentityId,
                               ZoneId userTimeZone,
                               int offset,
                               int limit) throws Exception;

  /**
   * Count pending events that the selected user didn't answered yet
   * 
   * @param ownerIds {@link List} of {@link Identity} technical identifier
   * @param userIdentityId user {@link Identity} identifier
   * @return pending events count
   * @throws IllegalAccessException when user is not an allowed to access one of
   *           ownerIds events
   */
  long countPendingEvents(List<Long> ownerIds, long userIdentityId) throws Exception;

}
