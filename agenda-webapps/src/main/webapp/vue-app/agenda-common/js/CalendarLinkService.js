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

/**
 * The private iCal link of a calendar (EXO-90252), served by agenda's Spring
 * REST under this WAR's own context, not the portal's legacy one.
 */
const AGENDA_REST_BASE = '/agenda/rest';

/**
 * Reads the status of a calendar's link: whether it exists, who created it,
 * when, whether it still answers — and its URL too, while the link answers and
 * can be displayed. The server answers only the calendar's owner or a current
 * manager of its space, and never lets the answer be cached: treat the URL as
 * the secret it is.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @returns {Promise<Object>} the status
 */
export function getCalendarLink(calendarId) {
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/link`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error retrieving the calendar link');
    }
    return resp.json();
  });
}

/**
 * Creates the link of a calendar, replacing the one it has. The answer carries
 * the new link's URL, as every later status read by its owner or a manager does.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @returns {Promise<Object>} the status of the new link, with its url
 */
export function saveCalendarLink(calendarId) {
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/link`, {
    method: 'POST',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error creating the calendar link');
    }
    return resp.json();
  });
}

/**
 * Deletes the link of a calendar.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @returns {Promise} resolved once deleted
 */
export function deleteCalendarLink(calendarId) {
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/link`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error deleting the calendar link');
    }
  });
}
