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
import {shallowMount} from '@vue/test-utils';

import AgendaLeftPanelRemoteCalendars from '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanelRemoteCalendars.vue';

/**
 * A resource the user subscribed to is drawn under "Shared with me" as a
 * resource, not as a person (EXO-90275).
 *
 * <p>The CalDAV connector answers `ownerKind: 'RESOURCE'` for a BlueMind
 * resource calendar — the pool vehicle of the rig — with the resource's name
 * as `ownerDisplayName` and no username. The row shows a neutral resource
 * glyph labelled "Resource: <name>" where a colleague's row shows her avatar,
 * keeps the Hide menu every shared row has, and nothing else.</p>
 *
 * <p><b>Harness.</b> Vuetify and the platform's avatar are not part of the
 * agenda build, so their tags are left as plain elements; the pins are on what
 * this component renders and says, which is where the change is.</p>
 */
describe('A resource calendar shared with the user is drawn as a resource', () => {

  Vue.config.ignoredElements = [/^v-/, 'exo-user-avatar'];

  const VEHICLE = {
    id: '/dav/calendars/__uids__/751E6D1A-7FDB-49B2-B668-B569E9A5A42D/calendar:7E3AE6F3-98DF-43D9-B071-AAB477AC2CD8/',
    name: 'Véhicule de pool 1',
    color: '#4a90d9',
    readOnly: true,
    shared: true,
    ownerIdentityId: null,
    ownerUsername: null,
    ownerDisplayName: 'Véhicule de pool 1',
    ownerKind: 'RESOURCE',
  };

  const CAMILLE = {
    id: '/dav/calendars/__uids__/751E6D1A-7FDB-49B2-B668-B569E9A5A42D/calendar:Default:9489C62D-0000-0000-0000-000000000000/',
    name: 'Camille',
    color: '#b8e986',
    readOnly: true,
    shared: true,
    ownerIdentityId: 5,
    ownerUsername: 'camille',
    ownerDisplayName: 'Camille Claudel',
    ownerKind: 'PERSON',
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
   * Mounts the panel over one connected CalDAV connector answering the given
   * calendars.
   *
   * @param {Array} calendars what the connector lists
   * @returns {Promise<Object>} the wrapper, once the listing is drawn
   */
  async function mountWith(calendars) {
    const connector = {
      name: 'agenda.caldavCalendar',
      isCaldav: true,
      canListCalendars: true,
      connected: true,
      listCalendars: () => Promise.resolve(calendars),
      hideCalendar: () => Promise.resolve(),
    };
    const wrapper = shallowMount(AgendaLeftPanelRemoteCalendars, {
      propsData: {connectors: [connector]},
      mocks: {
        $t: (key, params) => (params ? `${key}|${params[0]}` : key),
        $remoteEventConnector: {excludeMirrorCalendar: (_connector, listed) => listed},
      },
    });
    await flush();
    await wrapper.vm.$nextTick();
    return wrapper;
  }

  /**
   * The list item of a calendar, found by its name.
   *
   * @param {Object} wrapper the mounted panel
   * @param {String} name the calendar's name
   * @returns {Object} the row wrapper
   */
  function rowOf(wrapper, name) {
    // The Hide menu's own entry is a v-list-item too, and carries no checkbox.
    return wrapper.findAll('v-list-item')
      .filter(row => row.find('v-checkbox').exists() && row.find('v-checkbox').attributes('label') === name)
      .at(0);
  }

  it('draws the resource glyph labelled with the resource, and no avatar or lock', async () => {
    const wrapper = await mountWith([VEHICLE]);
    const row = rowOf(wrapper, 'Véhicule de pool 1');

    const glyph = row.find('.agenda-remote-calendar-resource');
    expect(glyph.exists()).toBe(true);
    expect(glyph.attributes('title')).toBe('agenda.leftPanel.resourceCalendar|Véhicule de pool 1');
    expect(glyph.attributes('aria-label')).toBe('agenda.leftPanel.resourceCalendar|Véhicule de pool 1');
    expect(glyph.text()).toContain('fa-cube');
    expect(row.find('exo-user-avatar').exists()).toBe(false);
    expect(row.html()).not.toContain('fa-lock');
    expect(row.find('v-list-item-content').attributes('title')).toBe('Véhicule de pool 1 — agenda.leftPanel.resourceCalendar|Véhicule de pool 1');
  });

  it('keeps the Hide menu on a resource row, as on every shared row, and offers nothing else', async () => {
    const wrapper = await mountWith([VEHICLE]);
    const row = rowOf(wrapper, 'Véhicule de pool 1');

    expect(row.find('.agenda-calendar-actions').exists()).toBe(true);
    const entries = row.findAll('v-list-item-title').wrappers.map(entry => entry.text());
    expect(entries).toEqual(['agenda.leftPanel.hideSharedCalendar']);
  });

  it('draws a person\'s share with her avatar and no resource glyph', async () => {
    const wrapper = await mountWith([CAMILLE]);
    const row = rowOf(wrapper, 'Camille');

    expect(row.find('exo-user-avatar').exists()).toBe(true);
    expect(row.find('.agenda-remote-calendar-resource').exists()).toBe(false);
    expect(row.find('v-list-item-content').attributes('title')).toBe('Camille — agenda.leftPanel.sharedBy|Camille Claudel');
  });

  it('draws no resource glyph on a calendar that is not shared, whatever kind it carries', async () => {
    const wrapper = await mountWith([{...VEHICLE, shared: false}]);
    const row = rowOf(wrapper, 'Véhicule de pool 1');

    expect(row.find('.agenda-remote-calendar-resource').exists()).toBe(false);
    expect(row.html()).toContain('fa-lock');
  });
});
