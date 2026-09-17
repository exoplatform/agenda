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

import AgendaPersonalCalendarList from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaPersonalCalendarList.vue';

/**
 * The personal calendar rows follow eXo's own sharing state (EXO-90357):
 * Share sits in the menu of every calendar the server says its owner may
 * share — a CalDAV account or not — and a calendar shared with at least one
 * colleague in eXo carries the shared sign, opening the same drawer. The
 * counts come from one listing of eXo's records, read again when a share
 * changes; a share made on a server without eXo knowing marks nothing.
 */
describe('Calendar menus and rows follow the sharing state', () => {

  Vue.config.ignoredElements = [/^v-/];

  /** Work is shareable and shared, Home shareable and not, Feed a subscription nobody shares. */
  const PERSONAL = [
    {id: 10, name: 'Work', acl: {canShare: true}},
    {id: 11, name: 'Home', acl: {canShare: true}},
    {id: 12, name: 'Feed', subscription: true, acl: {canShare: false}},
  ];

  let shareService;

  /**
   * Waits for every pending promise callback.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Mounts the list over the three calendars.
   *
   * @returns {Promise<Object>} the wrapper
   */
  async function mountList() {
    const wrapper = shallowMount(AgendaPersonalCalendarList, {
      mocks: {
        $t: key => key,
        $calendarService: {getCalendars: jest.fn().mockResolvedValue({calendars: PERSONAL.map(calendar => ({...calendar}))})},
        $calendarLinkService: {getCalendarLinks: jest.fn().mockResolvedValue([])},
        $calendarShareService: shareService,
      },
      stubs: {'exo-confirm-dialog': true},
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    await flush();
    return wrapper;
  }

  /**
   * The row of a calendar, found by its label.
   *
   * @param {Object} wrapper the list
   * @param {String} label the calendar's label
   * @returns {Object} the row
   */
  function row(wrapper, label) {
    return wrapper.findAll('.agenda-calendar-settings').wrappers.find(one => one.find('v-checkbox').attributes('label') === label);
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1', language: 'en'}}};
    global.extensionRegistry = {loadExtensions: () => []};
  });

  beforeEach(() => {
    shareService = {getShareCounts: jest.fn().mockResolvedValue({10: 2})};
  });

  it('offers Share on the calendars the server says are shareable, and only those', async () => {
    const wrapper = await mountList();

    expect(row(wrapper, 'Work').find('.agenda-calendar-share-action').exists()).toBe(true);
    expect(row(wrapper, 'Home').find('.agenda-calendar-share-action').exists()).toBe(true);
    expect(row(wrapper, 'Feed').find('.agenda-calendar-share-action').exists()).toBe(false);
    expect(row(wrapper, 'Work').find('.agenda-calendar-share-action').text()).toContain('agenda.calendarShare.menuShared');
    expect(row(wrapper, 'Home').find('.agenda-calendar-share-action').text()).toContain('agenda.calendarShare.menu');
  });

  it('marks the calendars shared with a colleague in eXo, from the counts, and nothing else', async () => {
    const wrapper = await mountList();

    expect(row(wrapper, 'Work').find('.agenda-calendar-shared-icon').exists()).toBe(true);
    expect(row(wrapper, 'Work').find('.agenda-calendar-shared').attributes('title')).toBe('agenda.calendarShare.sharedTooltip');
    expect(row(wrapper, 'Home').find('.agenda-calendar-shared-icon').exists()).toBe(false);
    expect(row(wrapper, 'Feed').find('.agenda-calendar-shared-icon').exists()).toBe(false);
  });

  it('opens the share drawer from the entry and from the sign', async () => {
    const wrapper = await mountList();

    await row(wrapper, 'Home').find('.agenda-calendar-share-action').trigger('click');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-share-drawer-open', expect.objectContaining({id: 11}));

    await row(wrapper, 'Work').find('.agenda-calendar-shared').trigger('click');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-share-drawer-open', expect.objectContaining({id: 10}));
  });

  it('reads the counts again when the drawer says a share changed, and the sign follows at once', async () => {
    const wrapper = await mountList();
    shareService.getShareCounts.mockResolvedValue({10: 2, 11: 1});

    wrapper.vm.$root.$emit('agenda-calendar-shares-changed');
    await flush();

    expect(shareService.getShareCounts).toHaveBeenLastCalledWith(true);
    expect(row(wrapper, 'Home').find('.agenda-calendar-shared-icon').exists()).toBe(true);
  });

  it('keeps the signs drawn when the counts cannot be read', async () => {
    const wrapper = await mountList();
    shareService.getShareCounts.mockRejectedValue(new Error('down'));

    wrapper.vm.$root.$emit('agenda-refresh');
    await flush();

    expect(row(wrapper, 'Work').find('.agenda-calendar-shared-icon').exists()).toBe(true);
  });

});
