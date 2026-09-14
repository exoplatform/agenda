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
import AgendaFilterCalendarItem from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaFilterCalendarItem.vue';

/**
 * The calendar menus and rows follow the publishing state (EXO-90252): not
 * published offers Publish; published says so with a check and offers
 * Unpublish; stopped says so with a warning and offers Unpublish. The row
 * carries a sign whenever the calendar is published, working or not. States
 * come from one listing, read again when a link changes.
 */
describe('Calendar menus and rows follow the publishing state', () => {

  Vue.config.ignoredElements = [/^v-/];

  /** Work is not published, Home is, Trips stopped. */
  const PERSONAL = [{id: 10, name: 'Work'}, {id: 11, name: 'Home'}, {id: 12, name: 'Trips'}];

  const LINKS = [
    {calendarId: 11, exists: true, active: true, displayable: true, url: 'https://x/agenda/rest/ical/a.ics'},
    {calendarId: 12, exists: true, active: false, displayable: false},
    {calendarId: 20, exists: true, active: true, displayable: false},
    {calendarId: 21, exists: true, active: false, displayable: false},
  ];

  let linkService;

  /**
   * Waits for every pending promise callback.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1', language: 'en'}}};
    global.extensionRegistry = {loadExtensions: () => []};
  });

  beforeEach(() => {
    linkService = {getCalendarLinks: jest.fn().mockResolvedValue(LINKS)};
  });

  describe('personal calendar list', () => {

    /**
     * Mounts the list over three calendars.
     *
     * @returns {Object} the wrapper
     */
    async function mountList() {
      const wrapper = shallowMount(AgendaPersonalCalendarList, {
        mocks: {
          $t: key => key,
          $calendarService: {getCalendars: jest.fn().mockResolvedValue({calendars: PERSONAL.map(calendar => ({...calendar}))})},
          $calendarLinkService: linkService,
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

    it('offers Publish on a calendar that is not published, and draws no sign', async () => {
      const work = row(await mountList(), 'Work');

      expect(work.find('.agenda-calendar-link-action').classes()).toContain('agenda-calendar-link-state-none');
      expect(work.find('.agenda-calendar-link-action').text()).toBe('agenda.calendarPublish.menu');
      expect(work.find('.agenda-calendar-unpublish-action').exists()).toBe(false);
      expect(work.find('.agenda-calendar-published-icon').exists()).toBe(false);
    });

    it('says Published with a check, offers Unpublish, and signs the row', async () => {
      const home = row(await mountList(), 'Home');

      const entry = home.find('.agenda-calendar-link-action');
      expect(entry.classes()).toContain('agenda-calendar-link-state-published');
      expect(entry.text()).toContain('agenda.calendarPublish.published');
      expect(entry.find('v-icon').text()).toBe('fas fa-check');
      expect(home.find('.agenda-calendar-unpublish-action').text()).toBe('agenda.calendarPublish.delete');
      const sign = home.find('.agenda-calendar-published-published');
      expect(sign.attributes('title')).toBe('agenda.calendarPublish.publishedTooltip');
      expect(sign.find('v-icon').text()).toBe('fas fa-link');
    });

    it('says Publishing stopped with a warning, offers Unpublish, and signs the row differently', async () => {
      const trips = row(await mountList(), 'Trips');

      const entry = trips.find('.agenda-calendar-link-action');
      expect(entry.classes()).toContain('agenda-calendar-link-state-stopped');
      expect(entry.text()).toContain('agenda.calendarPublish.stopped');
      expect(entry.find('v-icon').text()).toBe('fas fa-exclamation-triangle');
      expect(trips.find('.agenda-calendar-unpublish-action').exists()).toBe(true);
      const sign = trips.find('.agenda-calendar-published-stopped');
      expect(sign.attributes('title')).toBe('agenda.calendarPublish.stoppedTooltip');
      expect(sign.find('v-icon').classes()).toContain('warning--text');
    });

    it('opens the drawer from the state entry and asks the drawer to unpublish', async () => {
      const wrapper = await mountList();
      const home = row(wrapper, 'Home');

      await home.find('.agenda-calendar-link-action').trigger('click');
      await home.find('.agenda-calendar-unpublish-action').trigger('click');

      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-link-drawer-open', expect.objectContaining({id: 11}));
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-link-unpublish', expect.objectContaining({id: 11}));
    });

    it('reads the states once, and again when a link changes', async () => {
      const wrapper = await mountList();
      expect(linkService.getCalendarLinks).toHaveBeenCalledTimes(1);

      linkService.getCalendarLinks.mockResolvedValue([]);
      wrapper.vm.$root.$emit('agenda-calendar-links-changed');
      await flush();

      expect(linkService.getCalendarLinks).toHaveBeenLastCalledWith(true);
      expect(row(wrapper, 'Home').find('.agenda-calendar-link-action').classes()).toContain('agenda-calendar-link-state-none');
      expect(row(wrapper, 'Home').find('.agenda-calendar-published-icon').exists()).toBe(false);
    });
  });

  describe('space calendar row', () => {

    /**
     * Mounts a space calendar row.
     *
     * @param {Object} calendar the calendar
     * @returns {Object} the wrapper
     */
    async function mountItem(calendar) {
      const wrapper = shallowMount(AgendaFilterCalendarItem, {
        propsData: {calendar, ownerIds: [100], selectedOwnerIds: []},
        mocks: {$t: key => key, $calendarLinkService: linkService},
      });
      wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
      await flush();
      return wrapper;
    }

    const space = (id, canEdit) => ({id, owner: {id: 100, providerId: 'space', space: {displayName: 'Chemistry'}}, acl: {canEdit}});

    it('offers Publish to a manager of a calendar that is not published', async () => {
      const wrapper = await mountItem(space(22, true));

      expect(wrapper.find('.agenda-calendar-link-action').classes()).toContain('agenda-calendar-link-state-none');
      expect(wrapper.find('.agenda-calendar-unpublish-action').exists()).toBe(false);
      expect(wrapper.find('.agenda-calendar-published-icon').exists()).toBe(false);
    });

    it('says Published and offers Unpublish to a manager, with the sign on the row', async () => {
      const wrapper = await mountItem(space(20, true));

      expect(wrapper.find('.agenda-calendar-link-action').text()).toContain('agenda.calendarPublish.published');
      await wrapper.find('.agenda-calendar-unpublish-action').trigger('click');
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-link-unpublish', expect.objectContaining({id: 20}));
      expect(wrapper.find('.agenda-calendar-published-published').exists()).toBe(true);
    });

    it('says Publishing stopped to a manager, with the stopped sign', async () => {
      const wrapper = await mountItem(space(21, true));

      expect(wrapper.find('.agenda-calendar-link-action').classes()).toContain('agenda-calendar-link-state-stopped');
      expect(wrapper.find('.agenda-calendar-unpublish-action').exists()).toBe(true);
      expect(wrapper.find('.agenda-calendar-published-stopped').exists()).toBe(true);
    });

    it('offers no publishing entry to a member', async () => {
      const wrapper = await mountItem(space(20, false));

      expect(wrapper.find('.agenda-calendar-link-action').exists()).toBe(false);
      expect(wrapper.find('.agenda-calendar-unpublish-action').exists()).toBe(false);
    });
  });

});
