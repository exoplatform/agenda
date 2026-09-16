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
 * The owner's share mark (EXO-90331). A calendar shared WITH the user already
 * carried its owner's avatar; one the user shares themselves carried nothing,
 * so the only way to know one's own exposure was to open the Share drawer on
 * each calendar in turn. The row now carries fas fa-share-alt whenever a
 * connector says colleagues can see it, beside the published sign and drawn the
 * same way — and clicking it opens the very drawer the row's menu offers.
 *
 * Counts come from the connectors, are re-asked on the four signals the
 * problems and the menu entries are, and a slower older answer never replaces a
 * newer one.
 */
describe('the owner sees which of their calendars are shared', () => {

  Vue.config.ignoredElements = [/^v-/];

  /** Work is shared with three colleagues, Home with nobody. */
  const PERSONAL = [{id: 10, name: 'Work'}, {id: 11, name: 'Home'}];

  let connector;

  /** Every list mounted by a test, destroyed after it. */
  let mounted;

  /**
   * Waits for every pending promise callback.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
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

  /**
   * Mounts the list over the two calendars.
   *
   * @returns {Object} the wrapper
   */
  async function mountList() {
    const wrapper = shallowMount(AgendaPersonalCalendarList, {
      mocks: {
        $t: (key, params) => (params ? `${key}(${Object.values(params).join(',')})` : key),
        $calendarService: {getCalendars: jest.fn().mockResolvedValue({calendars: PERSONAL.map(calendar => ({...calendar}))})},
        $calendarLinkService: {getCalendarLinks: jest.fn().mockResolvedValue([])},
      },
      stubs: {'exo-confirm-dialog': true},
    });
    mounted.push(wrapper);
    await flush();
    return wrapper;
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1', language: 'en'}}};
  });

  afterEach(() => {
    // Destroyed, not left behind: the list registers document listeners, and a
    // wrapper that outlives its test goes on answering the next test's signals
    // — which is how a "how many times was it asked" assertion comes back with
    // one count per list ever mounted.
    mounted.forEach(wrapper => wrapper.destroy());
  });

  beforeEach(() => {
    mounted = [];
    connector = {
      name: 'agenda.caldavCalendar',
      calendarShares: jest.fn().mockResolvedValue({10: {sharees: 3, actionId: 'caldavShareCalendar'}}),
      calendarActions: jest.fn().mockResolvedValue({10: [{id: 'caldavShareCalendar', label: 'Share', icon: 'fa-share-alt'}]}),
      runCalendarAction: jest.fn().mockResolvedValue(true),
    };
    global.extensionRegistry = {loadExtensions: () => [connector]};
  });

  it('marks a shared calendar, and says how many colleagues see it', async () => {
    const work = row(await mountList(), 'Work');

    const mark = work.find('.agenda-calendar-shared-icon');
    expect(mark.exists()).toBe(true);
    expect(mark.find('v-icon').text()).toBe('fas fa-share-alt');
    expect(mark.find('v-icon').classes()).toContain('text-light-color');
    expect(mark.find('v-icon').attributes('size')).toBe('14');
    const sign = mark.find('span');
    expect(sign.attributes('title')).toBe('agenda.calendars.sharedTooltip(3)');
    expect(sign.attributes('aria-label')).toBe('agenda.calendars.sharedTooltip(3)');
    expect(sign.attributes('role')).toBe('img');
  });

  it('draws no mark at all on a calendar nobody sees', async () => {
    const home = row(await mountList(), 'Home');

    expect(home.find('.agenda-calendar-shared-icon').exists()).toBe(false);
  });

  it('draws no mark when no connector answers the question', async () => {
    delete connector.calendarShares;

    const wrapper = await mountList();

    expect(wrapper.vm.shares).toEqual({});
    expect(wrapper.find('.agenda-calendar-shared-icon').exists()).toBe(false);
  });

  it('draws no mark when the connector cannot answer', async () => {
    connector.calendarShares.mockRejectedValue(new Error('500'));

    const wrapper = await mountList();

    expect(wrapper.find('.agenda-calendar-shared-icon').exists()).toBe(false);
  });

  it('sits at the end of the row, after the menu', async () => {
    const work = row(await mountList(), 'Work').element;
    const menu = work.querySelector('.agenda-calendar-actions');
    const mark = work.querySelector('.agenda-calendar-shared-icon');

    expect(menu).not.toBeNull();
    expect(mark).not.toBeNull();
    // DOCUMENT_POSITION_FOLLOWING: the mark comes after the menu.
    expect(menu.compareDocumentPosition(mark) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
  });

  it('opens the share drawer from the mark, through the connector that offered the action', async () => {
    const wrapper = await mountList();

    await row(wrapper, 'Work').find('.agenda-calendar-shared-icon').trigger('click');
    await flush();

    expect(connector.runCalendarAction).toHaveBeenCalledTimes(1);
    expect(connector.runCalendarAction).toHaveBeenCalledWith('caldavShareCalendar', expect.objectContaining({id: 10, name: 'Work'}));
  });

  it('does nothing when the connector no longer offers the action the mark names', async () => {
    // A calendar can be seen by colleagues while it can no longer be shared
    // from eXo — an unreachable server, a calendar no longer owned there. A
    // mark opening an empty drawer would be worse than one that only informs.
    connector.calendarActions.mockResolvedValue({});

    const wrapper = await mountList();

    expect(row(wrapper, 'Work').find('.agenda-calendar-shared-icon').exists()).toBe(true);
    await row(wrapper, 'Work').find('.agenda-calendar-shared-icon').trigger('click');
    await flush();

    expect(connector.runCalendarAction).not.toHaveBeenCalled();
  });

  it('asks again on each of the four signals the problems are asked on', async () => {
    const wrapper = await mountList();
    expect(connector.calendarShares).toHaveBeenCalledTimes(1);

    wrapper.vm.$root.$emit('agenda-refresh-personal-calendars');
    wrapper.vm.$root.$emit('agenda-refresh');
    document.dispatchEvent(new CustomEvent('agenda-refresh-personal-calendars'));
    document.dispatchEvent(new CustomEvent('agenda-connectors-refresh'));
    await flush();

    expect(connector.calendarShares).toHaveBeenCalledTimes(5);
  });

  it('drops a share that stops being reported, so a stale mark goes', async () => {
    const wrapper = await mountList();
    expect(row(wrapper, 'Work').find('.agenda-calendar-shared-icon').exists()).toBe(true);

    connector.calendarShares.mockResolvedValue({});
    wrapper.vm.$root.$emit('agenda-refresh');
    await flush();

    expect(row(wrapper, 'Work').find('.agenda-calendar-shared-icon').exists()).toBe(false);
  });

  it('keeps the latest answer when an older, slower one arrives after it', async () => {
    const wrapper = await mountList();
    let releaseSlow;
    connector.calendarShares.mockReturnValueOnce(new Promise(resolve => releaseSlow = resolve));
    wrapper.vm.retrieveShares();
    connector.calendarShares.mockResolvedValueOnce({11: {sharees: 7, actionId: 'caldavShareCalendar'}});
    await wrapper.vm.retrieveShares();

    releaseSlow({10: {sharees: 99, actionId: 'caldavShareCalendar'}});
    await flush();

    expect(wrapper.vm.shares).toEqual({11: {sharees: 7, actionId: 'caldavShareCalendar', connector: 'agenda.caldavCalendar'}});
  });
});
