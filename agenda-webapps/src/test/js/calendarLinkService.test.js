/*
 * Copyright (C) 2026 eXo Platform SAS.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import * as calendarLinkService from '../../main/webapp/vue-app/agenda-common/js/CalendarLinkService.js';

/**
 * The shared listing of calendar links (EXO-90252): one request serves every
 * row asking within a second, and a listing that may predate a change is never
 * the one served after it.
 */
describe('CalendarLinkService listing', () => {

  let pending;

  /**
   * Answers the pending request of a method and URL.
   *
   * @param {String} key method and URL
   * @param {Object} body the JSON body
   * @returns {void}
   */
  function answer(key, body) {
    pending[key].shift()({ok: true, json: () => Promise.resolve(body)});
  }

  beforeEach(() => {
    pending = {};
    global.fetch = jest.fn((url, options) => new Promise(resolve => {
      const key = `${options.method} ${url}`;
      (pending[key] = pending[key] || []).push(resolve);
    }));
    calendarLinkService.forgetCalendarLinks();
  });

  it('serves every row asking within a second from one request', () => {
    calendarLinkService.getCalendarLinks(false);
    calendarLinkService.getCalendarLinks(false);
    calendarLinkService.getCalendarLinks(true);

    expect(fetch).toHaveBeenCalledTimes(1);
  });

  it.each([
    ['publishing', () => calendarLinkService.saveCalendarLink(10), 'POST /agenda/rest/calendars/10/link'],
    ['unpublishing', () => calendarLinkService.deleteCalendarLink(10), 'DELETE /agenda/rest/calendars/10/link'],
  ])('reads the links again after %s, even when a listing started during the change', async (label, change, key) => {
    const changing = change();
    const early = calendarLinkService.getCalendarLinks(false);
    answer('GET /agenda/rest/calendars/links', [{calendarId: 10, exists: false}]);
    await early;
    answer(key, {});
    await changing;

    calendarLinkService.getCalendarLinks(true);

    expect(fetch.mock.calls.filter(call => call[0] === '/agenda/rest/calendars/links')).toHaveLength(2);
  });
});
