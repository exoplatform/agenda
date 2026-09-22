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
 * The refusal codes the server answers on the link endpoints, each with a
 * sentence of its own in the bundle -- these are the codes
 * AgendaCalendarLinkRest actually throws (agenda.calendarLink.*, read from
 * AgendaCalendarLinkServiceImpl and AgendaCalendarLinkRest at
 * develop/backport/EXO-90236), not a separate namespace of their own.
 */
export const ERROR_CODES = [
  'agenda.calendarLink.forbidden',
  'agenda.calendarLink.calendarNotFound',
  'agenda.calendarLink.invalidCalendar',
];

/**
 * The bundle key of the sentence to show for a refusal code, falling back to
 * one generic key per kind of operation rather than the sibling
 * CalendarSubscriptionService's single flat fallback: a read and a write
 * failing for an unrecognised reason already had distinct wordings here
 * (agenda.calendarPublish.loadError / .error), and losing that distinction
 * would be a regression this fix does not need to make.
 *
 * @param {String} code the code the server answered, may be empty
 * @param {String} fallback the generic key to fall back to
 * @returns {String} the bundle key
 */
export function errorMessageKey(code, fallback) {
  return ERROR_CODES.includes(code) ? code : fallback;
}

/**
 * Reads an answer: its JSON body, nothing for 204, and an error carrying the
 * server's refusal code as its message otherwise -- the code, never a
 * hard-coded sentence, so the caller can translate it (or fall back) itself.
 *
 * @param {Response} resp the answer
 * @returns {Promise} the body
 */
function handle(resp) {
  if (resp && resp.ok) {
    return resp.status === 204 ? null : resp.json();
  }
  const body = resp && typeof resp.json === 'function' ? resp.json().catch(() => ({})) : Promise.resolve({});
  return body.then(content => {
    throw new Error(content && content.message || '');
  });
}

/**
 * How long a listing of every link is shared by whoever asks for it. Every row
 * of the left panel asks when it is drawn and again on every refresh event;
 * within this window they all get the one request the first of them made.
 */
const LISTING_SHARED_MS = 1000;

let listing = null;

let listingStartedAt = 0;

/**
 * Lists the link of every calendar the user may manage one for and that has
 * one, working or stopped: the user's own calendars and the calendars of the
 * spaces they manage. An entry carries its URL while the link answers and can
 * be displayed — treat it as the secret it is.
 *
 * One request serves everybody asking within a second; asking again later
 * reuses the last answer, unless asked to refresh.
 *
 * @param {Boolean} refresh whether the answer must be newer than the last change
 * @returns {Promise<Array>} the links
 */
export function getCalendarLinks(refresh) {
  const now = Date.now();
  if (listing && (!refresh || now - listingStartedAt < LISTING_SHARED_MS)) {
    return listing;
  }
  listingStartedAt = now;
  listing = fetch(`${AGENDA_REST_BASE}/calendars/links`, {
    method: 'GET',
    credentials: 'include',
  }).then(handle).catch(error => {
    listing = null;
    throw error;
  });
  return listing;
}

/**
 * Forgets the shared listing, so the next ask reads the server again. Called
 * when a change to a link starts and again once it is done: a listing that
 * started while the change was in flight may carry the state before it, and
 * must not be the one served to the lists refreshing after it.
 *
 * @returns {void}
 */
export function forgetCalendarLinks() {
  listing = null;
  listingStartedAt = 0;
}

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
  }).then(handle);
}

/**
 * Creates the link of a calendar, replacing the one it has. The answer carries
 * the new link's URL, as every later status read by its owner or a manager does
 * while the link answers and can be displayed.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @returns {Promise<Object>} the status of the new link, with its url
 */
export function saveCalendarLink(calendarId) {
  forgetCalendarLinks();
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/link`, {
    method: 'POST',
    credentials: 'include',
  }).then(handle).then(status => {
    forgetCalendarLinks();
    return status;
  });
}

/**
 * Deletes the link of a calendar.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @returns {Promise} resolved once deleted
 */
export function deleteCalendarLink(calendarId) {
  forgetCalendarLinks();
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/link`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(handle).then(() => forgetCalendarLinks());
}
