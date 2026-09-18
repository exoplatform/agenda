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
import Vue from 'vue';
import {mount} from '@vue/test-utils';

import AgendaEventDetailsMobileToolbar from '../../main/webapp/vue-app/agenda-common/components/event/view/mobile/AgendaEventDetailsMobileToolbar.vue';

/**
 * The mobile event details name the calendar a live-read event lives in
 * (EXO-90393), the way the desktop header already did (EXO-89825).
 *
 * <p>An event fetched live from a connected account carries no eXo calendar and
 * no owner: the mobile header asked for an owner display name, got nothing, and
 * printed its "in" followed by empty space. It now names the collection the
 * account itself calls it, falls back to the account, then to the unnamed
 * fallback — and drops the whole line rather than print a bare preposition.</p>
 *
 * <p><b>Harness.</b> Vuetify and the agenda's globally-registered components
 * are not part of this build, so their tags are left as plain elements. `$t`
 * echoes the key and its first parameter, which is what makes the three
 * fallbacks tellable apart.</p>
 */
describe('The mobile event details header', () => {

  Vue.config.ignoredElements = [/^v-/, 'agenda-connector-avatar'];

  beforeAll(() => {
    global.eXo = {env: {portal: {context: '/portal', portalName: 'dw'}}};
  });

  const CONNECTOR = {name: 'agenda.caldavCalendar', user: 'camille@acme.org'};

  const LIVE_READ_EVENT = {
    summary: 'Dentist',
    type: 'remoteEvent',
    calendarId: '/dav/calendars/__uids__/751E/calendar:7E3A/',
    connector: CONNECTOR,
  };

  /**
   * Waits for every pending promise callback.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Mounts the header over one event, with an account answering the given
   * collection name.
   *
   * @param {Object} event the event on display
   * @param {String} resolvedName what the account calls the collection, null
   *          when it cannot name it
   * @returns {Promise<Object>} the wrapper, once the resolution has settled
   */
  async function mountWith(event, resolvedName) {
    const wrapper = mount(AgendaEventDetailsMobileToolbar, {
      propsData: {event: event},
      mocks: {
        $t: (key, params) => params && `${key}:${params[0]}` || key,
        $remoteEventConnector: {
          remoteCalendarName: () => Promise.resolve(resolvedName || null),
        },
      },
    });
    await flush();
    return wrapper;
  }

  it('names the collection the account calls it', async () => {
    const wrapper = await mountWith(LIVE_READ_EVENT, 'Perso');

    expect(wrapper.find('.remote-calendar-label').text()).toBe('Perso');
    expect(wrapper.text()).toContain('agenda.label.in');
  });

  it('names the account when the collection cannot be named', async () => {
    const wrapper = await mountWith(LIVE_READ_EVENT, null);

    expect(wrapper.find('.remote-calendar-label').text())
      .toBe('agenda.remoteEvent.calendarOfAccount:camille@acme.org');
  });

  it('falls back to the unnamed calendar when there is not even an account to name', async () => {
    const wrapper = await mountWith(Object.assign({}, LIVE_READ_EVENT, {connector: {name: 'agenda.caldavCalendar'}}), null);

    expect(wrapper.find('.remote-calendar-label').text())
      .toBe('agenda.remoteEvent.unnamedCalendar');
  });

  it('hangs the collection href on the line, which is what tells two same-named collections apart', async () => {
    const wrapper = await mountWith(LIVE_READ_EVENT, 'Perso');

    expect(wrapper.find('.remote-calendar-label').element.parentElement.getAttribute('title'))
      .toBe(`agenda.remoteEvent.calendarLocation:${LIVE_READ_EVENT.calendarId}`);
  });

  it('draws the connector identity rather than an empty owner avatar', async () => {
    const wrapper = await mountWith(LIVE_READ_EVENT, 'Perso');

    expect(wrapper.find('agenda-connector-avatar').exists()).toBe(true);
    expect(wrapper.find('.space-avatar-header').exists()).toBe(false);
  });

  it('renders no line at all rather than a bare "in" when there is nothing to name', async () => {
    const wrapper = await mountWith({summary: 'Orphan', calendar: {}}, null);

    expect(wrapper.text()).not.toContain('agenda.label.in');
    expect(wrapper.find('.calendar-owner-link').exists()).toBe(false);
  });

  it('keeps naming a stored event by its calendar, with the owner link', async () => {
    const wrapper = await mountWith({
      summary: 'Sprint review',
      calendar: {
        name: 'Team',
        owner: {providerId: 'organization', remoteId: 'camille', profile: {displayName: 'Camille Claudel'}},
      },
    }, null);

    expect(wrapper.find('.calendar-owner-link').text()).toBe('Team');
    expect(wrapper.find('agenda-connector-avatar').exists()).toBe(false);
    expect(wrapper.find('.space-avatar-header').exists()).toBe(true);
  });

});
