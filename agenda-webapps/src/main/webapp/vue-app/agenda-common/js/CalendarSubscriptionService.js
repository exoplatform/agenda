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
 * The user's subscriptions to calendar links (EXO-90278), served by agenda's
 * Spring REST under this WAR's own context. Every answer carrying a subscription
 * carries its URL, which may embed a secret: nothing here caches one.
 */
const SUBSCRIPTIONS_URL = '/agenda/rest/calendars/subscriptions';

/**
 * The refusal codes the server answers, each with a sentence of its own in the
 * bundle. Anything else is shown as the generic error.
 */
export const ERROR_CODES = [
  'agenda.calendarSubscription.invalidUrl',
  'agenda.calendarSubscription.schemeNotAllowed',
  'agenda.calendarSubscription.portNotAllowed',
  'agenda.calendarSubscription.credentialsInUrl',
  'agenda.calendarSubscription.refusedAddress',
  'agenda.calendarSubscription.unresolvable',
  'agenda.calendarSubscription.unreachable',
  'agenda.calendarSubscription.timeout',
  'agenda.calendarSubscription.httpError',
  'agenda.calendarSubscription.tooLarge',
  'agenda.calendarSubscription.tooManyRedirects',
  'agenda.calendarSubscription.notACalendar',
  'agenda.calendarSubscription.malformedCalendar',
  'agenda.calendarSubscription.linkNotFound',
  'agenda.calendarSubscription.alreadyInAgenda',
  'agenda.calendarSubscription.alreadySubscribed',
  'agenda.calendarSubscription.tooManySubscriptions',
  'agenda.calendarSubscription.urlUnreadable',
  'agenda.calendarSubscription.refreshTooSoon',
  'agenda.calendarSubscription.refreshInProgress',
  'agenda.calendarSubscription.tooManyReads',
  'agenda.calendarSubscription.refreshFailed',
  'agenda.calendarSubscription.userDisabled',
  'agenda.calendarSubscription.nameTooLong',
  'agenda.calendarSubscription.invalidColor',
  'agenda.calendarSubscription.forbidden',
  'agenda.calendarSubscription.notFound',
  'agenda.calendarSubscription.invalidRequest',
  'agenda.calendarNameAlreadyExists',
];

/**
 * The bundle key of the sentence to show for a refusal code.
 *
 * @param {String} code the code the server answered, may be empty
 * @returns {String} the key: the code itself when known, the generic error otherwise
 */
export function errorMessageKey(code) {
  return ERROR_CODES.includes(code) ? code : 'agenda.calendarSubscription.error';
}

/**
 * Reads an answer: its JSON body, nothing for 204, and an error carrying the
 * server's refusal code as its message and the status otherwise.
 *
 * @param {Response} resp the answer
 * @returns {Promise} the body
 */
function handle(resp) {
  if (resp && resp.ok) {
    return resp.status === 204 ? null : resp.json();
  }
  const status = resp && resp.status || 0;
  const body = resp && typeof resp.json === 'function' ? resp.json().catch(() => ({})) : Promise.resolve({});
  return body.then(content => {
    const error = new Error(content && content.message || 'agenda.calendarSubscription.error');
    error.status = status;
    throw error;
  });
}

/**
 * Sends a JSON body.
 *
 * @param {String} url the resource
 * @param {String} method the verb
 * @param {Object} body the body, or nothing
 * @returns {Promise} the answer's body
 */
function send(url, method, body) {
  const options = {
    method,
    credentials: 'include',
  };
  if (body) {
    options.headers = {'Content-Type': 'application/json'};
    options.body = JSON.stringify(body);
  }
  return fetch(url, options).then(handle);
}

/**
 * Lists the user's subscriptions, oldest first.
 *
 * @returns {Promise<Array>} the subscriptions
 */
export function getSubscriptions() {
  return send(SUBSCRIPTIONS_URL, 'GET');
}

/**
 * Checks a link without subscribing: the server reads it once.
 *
 * @param {String} url the link as typed
 * @returns {Promise<Object>} {name}, the calendar's own name or null
 */
export function checkUrl(url) {
  return send(`${SUBSCRIPTIONS_URL}/check`, 'POST', {url});
}

/**
 * Subscribes to a link.
 *
 * @param {Object} subscription {url, name, color}; a blank name takes the
 *          calendar's own, a blank colour an automatic one
 * @returns {Promise<Object>} the subscription
 */
export function createSubscription(subscription) {
  return send(SUBSCRIPTIONS_URL, 'POST', subscription);
}

/**
 * Changes a subscription's name, colour or link.
 *
 * @param {Number} id the subscription
 * @param {Object} subscription {url, name, color}; blank fields are kept
 * @returns {Promise<Object>} the subscription as it now stands
 */
export function updateSubscription(id, subscription) {
  return send(`${SUBSCRIPTIONS_URL}/${id}`, 'PUT', subscription);
}

/**
 * Refreshes a subscription now. A link that fails is not an error of the
 * request: it comes back as the subscription's last error.
 *
 * @param {Number} id the subscription
 * @returns {Promise<Object>} the subscription after the refresh
 */
export function refreshSubscription(id) {
  return send(`${SUBSCRIPTIONS_URL}/${id}/refresh`, 'POST');
}

/**
 * Unsubscribes: the calendar and its events leave the agenda.
 *
 * @param {Number} id the subscription
 * @returns {Promise} resolved once removed
 */
export function deleteSubscription(id) {
  return send(`${SUBSCRIPTIONS_URL}/${id}`, 'DELETE');
}
