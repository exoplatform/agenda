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
 * Sharing a personal calendar with colleagues (EXO-90357), served by agenda's
 * Spring REST under this WAR's own context, not the portal's legacy one.
 */
const AGENDA_REST_BASE = '/agenda/rest';

/**
 * How long the sharee counts are shared by whoever asks for them: every row
 * of the personal list asks when it is drawn and again on every refresh, and
 * within this window they all get the one request the first of them made —
 * the idiom of CalendarLinkService.
 */
const COUNTS_SHARED_MS = 1000;

let counts = null;

let countsStartedAt = 0;

/**
 * Reads a JSON answer, or rejects with the server's message code — the reason
 * Spring MVC writes for a ResponseStatusException — so a drawer can word the
 * refusal.
 *
 * @param {Response} resp the fetch response
 * @param {String} fallback the message when the server gave none
 * @returns {Promise<Object>} the body, or nothing for a 204
 */
function readJson(resp, fallback) {
  if (!resp) {
    return Promise.reject(new Error(fallback));
  }
  if (resp.ok) {
    return resp.status === 204 ? Promise.resolve() : resp.json();
  }
  return resp.text()
    .catch(() => '')
    .then(text => {
      let message = text;
      try {
        const body = text && JSON.parse(text);
        message = body && (body.message || body.error) || text;
      } catch (e) {
        // A plain text reason is the message
      }
      throw new Error(message && message.trim() || fallback);
    });
}

/**
 * How many colleagues each of the user's own calendars is shared with: what
 * draws the shared sign on the rows. One request serves everybody asking
 * within a second; asking again later reuses the last answer, unless asked to
 * refresh.
 *
 * @param {Boolean} refresh whether the answer must be newer than the last change
 * @returns {Promise<Object>} sharee counts by calendar identifier
 */
export function getShareCounts(refresh) {
  const now = Date.now();
  if (counts && (!refresh || now - countsStartedAt < COUNTS_SHARED_MS)) {
    return counts;
  }
  countsStartedAt = now;
  counts = fetch(`${AGENDA_REST_BASE}/calendars/share-counts`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => readJson(resp, 'agenda.share.error')).catch(error => {
    counts = null;
    throw error;
  });
  return counts;
}

/**
 * Forgets the shared counts, so the next ask reads the server again. Called
 * when a share changes.
 *
 * @returns {void}
 */
export function forgetShareCounts() {
  counts = null;
  countsStartedAt = 0;
}

/**
 * The colleagues a calendar is shared with, and the shares of it a channel's
 * server holds that eXo does not record. Owner only.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @returns {Promise<Object>} {shares, externalShares}
 */
export function getShares(calendarId) {
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/shares`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => readJson(resp, 'agenda.share.loadError'));
}

/**
 * Shares a calendar with a colleague.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @param {String} username the colleague
 * @param {String} access what the colleague may do with it, VIEW or EDIT
 *        (EXO-90378); left out to share at VIEW, the default
 * @returns {Promise<Object>} the share
 */
export function share(calendarId, username, access) {
  forgetShareCounts();
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/shares`, {
    method: 'POST',
    credentials: 'include',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify(access ? {username, access} : {username}),
  }).then(resp => readJson(resp, 'agenda.share.error')).finally(forgetShareCounts);
}

/**
 * Changes what a colleague may do with a calendar shared with them
 * (EXO-90378). The owner's call, and only theirs.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @param {Number} shareeIdentityId identity identifier of the colleague
 * @param {String} access the new level, VIEW or EDIT
 * @returns {Promise} resolved once the level is recorded
 */
export function setLevel(calendarId, shareeIdentityId, access) {
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/shares/${shareeIdentityId}`, {
    method: 'PUT',
    credentials: 'include',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({access}),
  }).then(resp => readJson(resp, 'agenda.share.error'));
}

/**
 * Stops sharing a calendar with a colleague.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @param {Number} shareeIdentityId identity identifier of the colleague
 * @returns {Promise} resolved once deleted
 */
export function unshare(calendarId, shareeIdentityId) {
  forgetShareCounts();
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/shares/${shareeIdentityId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => readJson(resp, 'agenda.share.error')).finally(forgetShareCounts);
}

/**
 * Removes a share a channel's server holds without an eXo record.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @param {String} channelId the channel that listed the share
 * @param {String} externalId the channel's identifier of the share
 * @returns {Promise} resolved once removed
 */
export function removeExternalShare(calendarId, channelId, externalId) {
  return fetch(`${AGENDA_REST_BASE}/calendars/${calendarId}/external-shares/${encodeURIComponent(channelId)}/${encodeURIComponent(externalId)}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => readJson(resp, 'agenda.share.error'));
}

/**
 * The calendars shared with the user, hidden ones included: each says so.
 *
 * @returns {Promise<Array>} the calendars, newest share first
 */
export function getSharedWithMe() {
  return fetch(`${AGENDA_REST_BASE}/calendars/shared-with-me`, {
    method: 'GET',
    credentials: 'include',
  }).then(resp => readJson(resp, 'agenda.share.loadError'));
}

/**
 * Hides, or shows again, a calendar shared with the user. Never deletes the
 * share.
 *
 * @param {Number} calendarId technical identifier of the calendar
 * @param {Boolean} hidden whether to hide it
 * @returns {Promise} resolved once recorded
 */
export function setHidden(calendarId, hidden) {
  return fetch(`${AGENDA_REST_BASE}/calendars/shared-with-me/${calendarId}/hidden`, {
    method: 'PUT',
    credentials: 'include',
    headers: {'Content-Type': 'application/json'},
    body: JSON.stringify({hidden: !!hidden}),
  }).then(resp => readJson(resp, 'agenda.share.error'));
}
