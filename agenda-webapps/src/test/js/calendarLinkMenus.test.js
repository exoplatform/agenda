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
 * published offers Publish; published says so with a check; stopped says so with
 * a warning. Each state is one entry opening the drawer, the only place a
 * calendar is unpublished — the menus carry no Unpublish of their own. The row
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

  /**
   * The menu entry titles of a menu, in the order they are drawn.
   *
   * @param {Object} menuHolder a wrapper holding one menu
   * @returns {Array} the entry titles
   */
  function titlesOf(menuHolder) {
    return menuHolder.findAll('v-list-item-title').wrappers.map(title => title.text());
  }

  /**
   * Where an entry sits in a menu. An entry's title can carry its state icon's
   * name before its label, so it is found by what it contains.
   *
   * @param {Array} titles the entry titles, in order
   * @param {String} key the label key the entry shows
   * @returns {Number} its position, -1 when absent
   */
  function at(titles, key) {
    return titles.findIndex(title => title.split(/\s+/).includes(key));
  }

  /**
   * Whether a menu offers an Unpublish entry, recognised by its label or by the
   * class it used to carry.
   *
   * @param {Object} menuHolder a wrapper holding one menu
   * @returns {Boolean} true when an Unpublish entry is drawn
   */
  function offersUnpublish(menuHolder) {
    return at(titlesOf(menuHolder), 'agenda.calendarPublish.delete') >= 0
      || menuHolder.find('.agenda-calendar-unpublish-action').exists();
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
      expect(work.find('.agenda-calendar-published-icon').exists()).toBe(false);
    });

    it('says Published with a check, and signs the row', async () => {
      const home = row(await mountList(), 'Home');

      const entry = home.find('.agenda-calendar-link-action');
      expect(entry.classes()).toContain('agenda-calendar-link-state-published');
      expect(entry.text()).toContain('agenda.calendarPublish.published');
      expect(entry.find('v-icon').text()).toBe('fas fa-check');
      const sign = home.find('.agenda-calendar-published-published');
      expect(sign.attributes('title')).toBe('agenda.calendarPublish.publishedTooltip');
      expect(sign.find('v-icon').text()).toBe('fas fa-link');
    });

    it('draws the published sign at the end of the row, to the right of the menu', async () => {
      const home = row(await mountList(), 'Home').element;
      const menu = home.querySelector('.agenda-calendar-actions');
      const sign = home.querySelector('.agenda-calendar-published-icon');
      expect(menu).not.toBeNull();
      expect(sign).not.toBeNull();
      // DOCUMENT_POSITION_FOLLOWING: the sign comes after the menu.
      expect(menu.compareDocumentPosition(sign) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('says Publishing stopped with a warning, and signs the row differently', async () => {
      const trips = row(await mountList(), 'Trips');

      const entry = trips.find('.agenda-calendar-link-action');
      expect(entry.classes()).toContain('agenda-calendar-link-state-stopped');
      expect(entry.text()).toContain('agenda.calendarPublish.stopped');
      expect(entry.find('v-icon').text()).toBe('fas fa-exclamation-triangle');
      const sign = trips.find('.agenda-calendar-published-stopped');
      expect(sign.attributes('title')).toBe('agenda.calendarPublish.stoppedTooltip');
      expect(sign.find('v-icon').classes()).toContain('warning--text');
    });

    it('offers no Unpublish in the menu, whatever the state: a calendar is unpublished in the drawer', async () => {
      const wrapper = await mountList();

      ['Work', 'Home', 'Trips'].forEach(label => expect(offersUnpublish(row(wrapper, label))).toBe(false));
    });

    it('lists the publishing entry after Edit and before Delete, as BlueMind orders Modifier, Partager, Publier', async () => {
      const wrapper = await mountList();

      const published = titlesOf(row(wrapper, 'Home'));
      expect(published).toHaveLength(3);
      expect(at(published, 'agenda.calendar.edit')).toBe(0);
      expect(at(published, 'agenda.calendarPublish.published')).toBe(1);
      expect(at(published, 'agenda.calendar.delete')).toBe(2);

      const notPublished = titlesOf(row(wrapper, 'Work'));
      expect(notPublished).toHaveLength(3);
      expect(at(notPublished, 'agenda.calendar.edit')).toBe(0);
      expect(at(notPublished, 'agenda.calendarPublish.menu')).toBe(1);
      expect(at(notPublished, 'agenda.calendar.delete')).toBe(2);
    });

    it('opens the drawer from the state entry', async () => {
      const wrapper = await mountList();

      await row(wrapper, 'Home').find('.agenda-calendar-link-action').trigger('click');

      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-link-drawer-open', expect.objectContaining({id: 11}));
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
      expect(wrapper.find('.agenda-calendar-published-icon').exists()).toBe(false);
    });

    it('says Published to a manager, opens the drawer from it, and signs the row', async () => {
      const wrapper = await mountItem(space(20, true));

      expect(wrapper.find('.agenda-calendar-link-action').text()).toContain('agenda.calendarPublish.published');
      await wrapper.find('.agenda-calendar-link-action').trigger('click');
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-link-drawer-open', expect.objectContaining({id: 20}));
      expect(wrapper.find('.agenda-calendar-published-published').exists()).toBe(true);
    });

    it('says Publishing stopped to a manager, with the stopped sign', async () => {
      const wrapper = await mountItem(space(21, true));

      expect(wrapper.find('.agenda-calendar-link-action').classes()).toContain('agenda-calendar-link-state-stopped');
      expect(wrapper.find('.agenda-calendar-published-stopped').exists()).toBe(true);
    });

    it('draws the published sign to the right of the menu', async () => {
      const item = (await mountItem(space(20, true))).element;
      const menu = item.querySelector('.agenda-calendar-actions');
      const sign = item.querySelector('.agenda-calendar-published-icon');
      expect(menu).not.toBeNull();
      expect(sign).not.toBeNull();
      expect(menu.compareDocumentPosition(sign) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    });

    it('offers no Unpublish in the menu, whatever the state: a calendar is unpublished in the drawer', async () => {
      for (const id of [20, 21, 22]) {
        const wrapper = await mountItem(space(id, true));
        expect(titlesOf(wrapper)).toHaveLength(1);
        expect(offersUnpublish(wrapper)).toBe(false);
      }
    });

    it('offers no publishing entry to a member', async () => {
      const wrapper = await mountItem(space(20, false));

      expect(wrapper.find('.agenda-calendar-link-action').exists()).toBe(false);
      expect(titlesOf(wrapper)).toHaveLength(0);
    });
  });

});
