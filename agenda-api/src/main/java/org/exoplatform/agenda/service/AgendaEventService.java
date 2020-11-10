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
   * @param username User name requesting events
   * @param userTimeZone User time zone
   * @return {@link List} of {@link Event} accessible to user
   * @throws IllegalAccessException when user is not allowed to access events
   */
  public List<Event> getEvents(EventFilter eventFilter,
                               String username,
                               ZoneId userTimeZone) throws IllegalAccessException;

  /**
   * Retrieves an event identified by its technical identifier.
   * 
   * @param eventId technical identifier of event
   * @param timeZone used timezone to convert dates
   * @param username User name accessing event
   * @return Corresponding {@link Event} or null if not found
   * @throws IllegalAccessException when user is not allowed to access event
   */
  public Event getEventById(long eventId, ZoneId timeZone, String username) throws IllegalAccessException;

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
  public Event getEventById(long eventId);

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
  public Event getEventOccurrence(long parentEventId,
                                  ZonedDateTime occurrenceId,
                                  ZoneId userTimeZone,
                                  long identityId) throws IllegalAccessException;

  /**
   * Retrieves an event identified by its technical identifier.
   * 
   * @param eventId technical identifier of parent recurrent event
   * @param occurrenceId technical occurrence event identifier
   * @return Exceptional {@link Event} if found, else null
   */
  public Event getExceptionalOccurrenceEvent(long eventId, ZonedDateTime occurrenceId);

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
   * @param sendInvitation whether send invitation to attendees or not
   * @param username User name creating event
   * @return Created {@link Event} with technichal identifier
   * @throws IllegalAccessException when user is not allowed to create event on
   *           calendar
   * @throws AgendaException when the event attributes aren't valid
   */
  public Event createEvent(Event event,
                           List<EventAttendee> attendees,
                           List<EventConference> conferences,
                           List<EventAttachment> attachments,
                           List<EventReminder> reminders,
                           boolean sendInvitation,
                           String username) throws IllegalAccessException, AgendaException;

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
   * @param sendInvitation whether re-send invitation to attendees or not
   * @param username User name updating event
   * @return Updated {@link Event}
   * @throws IllegalAccessException when user is not allowed to update event
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   * @throws AgendaException when the event attributes aren't valid
   */
  public Event updateEvent(Event event,
                           List<EventAttendee> attendees,
                           List<EventConference> conferences,
                           List<EventAttachment> attachments,
                           List<EventReminder> reminders,
                           boolean sendInvitation,
                           String username) throws IllegalAccessException, ObjectNotFoundException, AgendaException;

  /**
   * Deletes an existing event
   * 
   * @param eventId Event technical identifier to delete
   * @param username User name deleting event
   * @throws IllegalAccessException when user is not authorized to delete the
   *           event
   * @throws ObjectNotFoundException when the event identified by its technical
   *           identifier is not found
   */
  void deleteEventById(long eventId, String username) throws IllegalAccessException, ObjectNotFoundException;

  /**
   * @return {@link List} of available events {@link RemoteProvider}
   */
  List<RemoteProvider> getRemoteProviders();

  /**
   * Creates a new events {@link RemoteProvider}
   * 
   * @param remoteProvider events remote provider to store
   * @return created {@link RemoteProvider}
   */
  RemoteProvider saveRemoteProvider(RemoteProvider remoteProvider);

  /**
   * Check whether user can access event or not.
   * 
   * @param event {@link Event} to check its permission
   * @param username user name wiling to access event
   * @return true if the user is a member of {@link Calendar} owner
   *         {@link Identity} or is an {@link EventAttendee}, else return false.
   */
  boolean canAccessEvent(Event event, String username);

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
   * @param username user name wiling to modify or delete the event
   * @return true if the user is a manager of {@link Calendar} owner
   *         {@link Identity} or is an {@link EventAttendee}, else return false.
   */
  boolean canUpdateEvent(Event event, String username);

  /**
   * Check whether user can create an event in selected Calendar or not.
   * 
   * @param calendar of type {@link Calendar}
   * @param username user name wiling to create an event
   * @return true if the user is a member of {@link Calendar} owner
   *         {@link Identity}, else return false.
   */
  boolean canCreateEvent(Calendar calendar, String username);

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
   * @throws IllegalAccessException when user is not authorized to create event
   * @throws ObjectNotFoundException when event with id wasn't found
   */
  public Event createEventExceptionalOccurrence(long eventId,
                                                List<EventAttendee> attendees,
                                                List<EventConference> conferences,
                                                List<EventAttachment> attachments,
                                                List<EventReminder> reminders,
                                                ZonedDateTime occurrenceId) throws IllegalAccessException,
                                                                            AgendaException,
                                                                            ObjectNotFoundException;

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

}
