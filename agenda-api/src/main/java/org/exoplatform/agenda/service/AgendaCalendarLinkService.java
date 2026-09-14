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
package org.exoplatform.agenda.service;

import java.util.List;

import org.exoplatform.agenda.model.CalendarLink;
import org.exoplatform.commons.exception.ObjectNotFoundException;

/**
 * Publishes a calendar as a private, read-only iCal link that any calendar
 * application can subscribe to.
 * <p>
 * <b>Who manages a link</b>: the owner of a personal calendar; any manager of
 * the space for a space calendar. <b>Who reads it</b>: whoever holds the URL,
 * without an eXo session — which is why the link answers only while its
 * creator still holds the right that let them create it, evaluated on every
 * read.
 */
public interface AgendaCalendarLinkService {

  /**
   * Reads the link of a calendar for someone allowed to manage it. The token
   * comes with it — so the URL can be shown again — while the link still
   * answers and its stored copy can be decrypted; the digest and the encrypted
   * copy never do.
   *
   * @param calendarId technical identifier of the calendar
   * @param username user asking, who must be allowed to manage the link
   * @return the link, with {@code active} and {@code token} computed now, or
   *         null when the calendar has none
   * @throws ObjectNotFoundException when the calendar does not exist
   * @throws IllegalAccessException when the user may not manage its link
   */
  CalendarLink getCalendarLink(long calendarId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Lists the links of every calendar the user may manage a link for — their
   * own calendars and the calendars of the spaces they manage — that have one,
   * working or stopped, each read as {@link #getCalendarLink} reads it.
   *
   * @param username user asking
   * @return the links, newest first; bounded
   * @throws IllegalAccessException when the user has no usable identity
   */
  List<CalendarLink> getCalendarLinks(String username) throws IllegalAccessException;

  /**
   * Creates the link of a calendar, replacing any link it already has: the
   * previous URL stops answering at once.
   *
   * @param calendarId technical identifier of the calendar
   * @param username user asking, who becomes the link's creator
   * @return the token of the new link
   * @throws ObjectNotFoundException when the calendar does not exist
   * @throws IllegalAccessException when the user may not manage its link
   */
  String saveCalendarLink(long calendarId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Deletes the link of a calendar. Deleting a link that does not exist is not
   * an error.
   *
   * @param calendarId technical identifier of the calendar
   * @param username user asking, who must be allowed to manage the link
   * @throws ObjectNotFoundException when the calendar does not exist
   * @throws IllegalAccessException when the user may not manage its link
   */
  void deleteCalendarLink(long calendarId, String username) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Deletes the link of a calendar with no permission check, for the platform's
   * own clean-up once the calendar itself is gone.
   *
   * @param calendarId technical identifier of the calendar
   */
  void deleteCalendarLinks(long calendarId);

  /**
   * Renders the calendar a token publishes, as an iCalendar document.
   * <p>
   * An unknown token, a token replaced by a reset, a deleted link and a link
   * whose creator lost their right all answer the same way, so that holding a
   * URL tells nothing about why it stopped working.
   *
   * @param token the token presented in the URL
   * @return the iCalendar document
   * @throws ObjectNotFoundException when the token opens nothing
   */
  String getCalendarFeed(String token) throws ObjectNotFoundException;

}
