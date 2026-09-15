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

import AgendaUserPublishedCalendarsSettings from '../../main/webapp/vue-app/agenda-user-setting/components/AgendaUserPublishedCalendarsSettings.vue';

/**
 * The published calendars settings row and its drawer (EXO-90252).
 *
 * <p>The row disappears once nothing is published, and the last Unpublish,
 * taken from the drawer, is that moment. The drawer must outlive the row:
 * destroyed while open, exo-drawer leaves its page overlay behind (EXO-90239).
 * Pinned on the drawer still being mounted after the list empties, as caldav's
 * hidden calendars section is.</p>
 */
describe('AgendaUserPublishedCalendarsSettings', () => {

  Vue.config.ignoredElements = [/^v-/];

  /** The last published calendar. */
  const LAST = {calendarId: 10, calendarKind: 'PERSONAL', exists: true, active: true};

  let service;

  /**
   * Mounts the row with the drawer stubbed.
   *
   * @returns {Object} the wrapper
   */
  function mountSection() {
    return shallowMount(AgendaUserPublishedCalendarsSettings, {
      mocks: {
        $t: (key, params) => (params ? `${key}(${Object.values(params).join(',')})` : key),
        $calendarLinkService: service,
      },
      stubs: {
        'agenda-user-published-calendars-drawer': {
          name: 'AgendaUserPublishedCalendarsDrawer',
          props: ['links'],
          template: '<div class="published-calendars-drawer-stub"></div>',
        },
      },
    });
  }

  /**
   * Resolves the pending listing promises and re-renders.
   *
   * @returns {Promise} settled once the row has re-rendered
   */
  async function settle() {
    await new Promise(resolve => setTimeout(resolve, 0));
    await Vue.nextTick();
  }

  beforeEach(() => {
    service = {getCalendarLinks: jest.fn()};
  });

  test('says how many calendars are published and opens the drawer', async () => {
    service.getCalendarLinks.mockResolvedValueOnce([LAST, {...LAST, calendarId: 11}]);
    const wrapper = mountSection();
    await settle();
    const rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');

    expect(wrapper.find('v-list-item').exists()).toBe(true);
    expect(wrapper.text()).toContain('agenda.calendarPublish.settings.title');
    expect(wrapper.text()).toContain('agenda.calendarPublish.settings.count(2)');
    await wrapper.find('v-btn').trigger('click');
    expect(rootEmit).toHaveBeenCalledWith('agenda-published-calendars-drawer-open');
  });

  test('keeps its drawer mounted when the last published calendar is unpublished', async () => {
    service.getCalendarLinks.mockResolvedValueOnce([LAST]);
    const wrapper = mountSection();
    await settle();
    expect(wrapper.find('.published-calendars-drawer-stub').exists()).toBe(true);

    service.getCalendarLinks.mockResolvedValueOnce([]);
    wrapper.vm.$root.$emit('agenda-calendar-links-changed');
    await settle();

    expect(wrapper.vm.links).toEqual([]);
    expect(wrapper.find('v-list-item').exists()).toBe(false);
    expect(wrapper.find('.published-calendars-drawer-stub').exists()).toBe(true);
    expect(wrapper.findComponent({name: 'AgendaUserPublishedCalendarsDrawer'}).props('links')).toEqual([]);
  });

  test('draws no row when nothing is published, but still holds the drawer', async () => {
    service.getCalendarLinks.mockResolvedValueOnce([]);
    const wrapper = mountSection();
    await settle();

    expect(wrapper.find('v-list-item').exists()).toBe(false);
    expect(wrapper.find('.published-calendars-drawer-stub').exists()).toBe(true);
  });

  test('draws no row when the listing fails', async () => {
    service.getCalendarLinks.mockRejectedValueOnce(new Error('500'));
    jest.spyOn(console, 'error').mockImplementation(() => {});
    const wrapper = mountSection();
    await settle();

    expect(wrapper.find('v-list-item').exists()).toBe(false);
    console.error.mockRestore();
  });
});
