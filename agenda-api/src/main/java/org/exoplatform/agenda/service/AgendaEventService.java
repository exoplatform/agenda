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

import org.exoplatform.agenda.exception.AgendaException;
import org.exoplatform.agenda.model.*;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaEventService {

  /**
   * Retrieves the list of events available for a designated user filtered by
   * criteria defined in a filter.
   * 
   * @param eventFilter a filter used to define criteria to get list of objects
   * @param userIdentityId user {@link Identity} identifier
   * @param userTimeZone User time zone
   * @return {@link List} of {@link Event} accessible to user
   * @throws IllegalAccessException when user is not allowed to access events
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
   * @throws IllegalAccessException when user is accessing not allowed event
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
   * Retrieves an event identified by its technical identifier.
   *
   * @param eventId technical identifier of parent recurrent event
   * @param occurrenceId technical occurrence event identifier
   * @return Exceptional {@link Event} if found, else null
   */
  Event getExceptionalOccurrenceEvent(long eventId, ZonedDateTime occurrenceId);

  /**
   * Create a new exceptional occurrence for a parent recurrent event
   * 
   * @param eventId technical identifier of parent recurrent event
   * @param attendees {@link List} of attendees
   * @param conferences {@link List} of conferences
   * @param attachments {@link List} of attachments
   * @param reminders {@link List} of reminders
   * @param occurrenceId event occurent identifier
   * @return newly created {@link Event}
   * @throws AgendaException when the event attributes aren't valid
   */
  Event createEventExceptionalOccurrence(long eventId,
                                         List<EventAttendee> attendees,
                                         List<EventConference> conferences,
                                         List<EventAttachment> attachments,
                                         List<EventReminder> reminders,
                                         ZonedDateTime occurrenceId) throws AgendaException;

  /**
   * Creates an exceptional occurrence for a recurrent event and occurrence ID
   * 
   * @param eventId parent recurrent event identifier
   * @param occurrenceId Occurrence identifier
   * @return created {@link Event}
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
   * @param attachments event attachment of type {@link EventAttachment}
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
   *           calendar
   * @throws AgendaException when the event attributes aren't valid
   */
  Event createEvent(Event event,
                    List<EventAttendee> attendees,
                    List<EventConference> conferences,
                    List<EventAttachment> attachments,
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
   * @param attachments event attachment of type {@link EventAttachment}
   * @param reminders {@link List} of preferred user reminders of type
   *          {@link EventReminder}
   * @param dateOptions {@link List} of {@link EventDateOption} of corresponding
   *          event
   * @param remoteEvent {@link RemoteEvent} of synchronized event by user to
   *          remote connector
   * @param sendInvitation whether re-send invitation to attendees or not
   * @param userIdentityId user {@link Identity} identifier
   * @return Updated {@link Event}
   * @throws IllegalAccessException when user is not allowed to update event
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   * @throws AgendaException when the event attributes aren't valid
   */
  Event updateEvent(Event event,
                    List<EventAttendee> attendees,
                    List<EventConference> conferences,
                    List<EventAttachment> attachments,
                    List<EventReminder> reminders,
                    List<EventDateOption> dateOptions,
                    RemoteEvent remoteEvent,
                    boolean sendInvitation,
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
   * @throws IllegalAccessException when user is not allowed to update event
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   * @throws AgendaException when the event attribute isn't valid
   */
  void updateEventFields(long eventId,
                         Map<String, List<String>> fields,
                         boolean updateAllOccurrences,
                         boolean sendInvitation,
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
   * @param userIdentityId user {@link Identity} identifier
   * @param userTimeZone used user timezone
   * @param query Term to search
   * @param offset offset
   * @param limit Limit of events to retrieve
   * @return {@link List} of {@link EventSearchResult}
   */
  List<EventSearchResult> search(long userIdentityId,
                                 ZoneId userTimeZone,
                                 String query,
                                 int offset,
                                 int limit);

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
   * @param ownerIds {@link List} of {@link Identity} technical identifier
   * @param attendeeId user {@link Identity} identifier
   * @param userTimeZone used to compute events dates switch given time zone
   * @param offset
   * @param limit maximum number of occurrences to retrieve
   * @return {@link List} date poll {@link Event} for user
   * @throws IllegalAccessException when user is not an allowed participant of
   *           the event who can modifies it
   */
  List<Event> getEventDatePolls(List<Long> ownerIds,
                                long attendeeId,
                                ZoneId userTimeZone,
                                int offset,
                                int limit) throws IllegalAccessException;

  /**
   * Count pending date polls where the current user is invited
   *
   * @param ownerIds {@link List} of {@link Identity} technical identifier
   * @param attendeeId user {@link Identity} identifier
   * @return {@link List} date poll {@link Event} for user
   * @throws IllegalAccessException when user is not an allowed participant of
   *           the event who can modifies it
   */
  long countEventDatePolls(List<Long> ownerIds, long attendeeId) throws IllegalAccessException;

}
