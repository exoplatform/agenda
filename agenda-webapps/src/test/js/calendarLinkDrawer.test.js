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
import fs from 'fs';
import path from 'path';
import Vue from 'vue';
import {shallowMount} from '@vue/test-utils';

import AgendaCalendarLinkDrawer from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaCalendarLinkDrawer.vue';
import AgendaFilterCalendarItem from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaFilterCalendarItem.vue';

/**
 * The private iCal link drawer (EXO-90252): what a manager sees and does, and
 * where the drawer lives.
 */
describe('Calendar link drawer', () => {

  Vue.config.ignoredElements = [/^v-/];

  const URL = 'https://tribe.example.org/agenda/rest/ical/Wz3pQyv5Hq0dS9bTf2LkMn8Rc1XeUa7GjYo4NiVhB6s.ics';

  const OTHER_URL = 'https://tribe.example.org/agenda/rest/ical/Q1w2E3r4T5y6U7i8O9p0A1s2D3f4G5h6J7k8L9z0X1c.ics';

  const SPACE_CALENDAR = {id: 20, owner: {id: 100, providerId: 'space', space: {displayName: 'Chemistry'}}, acl: {canEdit: true}};

  const PERSONAL_CALENDAR = {id: 10, name: 'Work', owner: {id: 1, providerId: 'organization', profile: {fullname: 'John'}}};

  let service;

  let drawerStub;

  let confirmStub;

  /**
   * Waits for every pending promise callback.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Mounts the drawer with eXo's $t, the service mocked, and exo-drawer and the
   * confirmation dialog stubbed so their open/close calls are observable.
   *
   * @returns {Object} the wrapper
   */
  function mountDrawer() {
    const wrapper = shallowMount(AgendaCalendarLinkDrawer, {
      mocks: {
        $t: (key, args) => args && `${key}(${Object.values(args).join('|')})` || key,
        $calendarLinkService: service,
      },
      stubs: {
        'exo-drawer': drawerStub,
        'exo-confirm-dialog': confirmStub,
      },
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    return wrapper;
  }

  /**
   * Clicks a button by class.
   *
   * @param {Object} wrapper the wrapper
   * @param {String} selector the button's class selector
   * @returns {Promise} resolved once the click's promises settle
   */
  async function click(wrapper, selector) {
    await wrapper.find(selector).trigger('click');
    await flush();
  }

  /**
   * A working link status as the server answers it to a manager.
   *
   * @param {Object} overrides fields to change
   * @returns {Object} the status
   */
  function activeStatus(overrides) {
    return {exists: true, active: true, displayable: true, creatorName: 'Mary Manager', createdDate: Date.UTC(2026, 8, 1), url: URL, ...overrides};
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {language: 'en'}}};
  });

  beforeEach(() => {
    service = {
      getCalendarLink: jest.fn(),
      saveCalendarLink: jest.fn(),
      deleteCalendarLink: jest.fn(),
    };
    drawerStub = {
      template: '<div class="drawer-stub"><slot name="title"></slot><slot name="content"></slot><slot name="footer"></slot></div>',
      methods: {
        open: jest.fn(),
        close: jest.fn(),
      },
    };
    confirmStub = {
      template: '<div class="confirm-stub"></div>',
      methods: {
        open: jest.fn(),
      },
    };
  });

  it('offers to create a link for a calendar that has none, and shows its URL once created', async () => {
    service.getCalendarLink.mockResolvedValue({calendarId: 10, exists: false, active: false});
    service.saveCalendarLink.mockResolvedValue(activeStatus({creatorName: 'John'}));
    const wrapper = mountDrawer();

    wrapper.vm.$root.$emit('agenda-calendar-link-drawer-open', PERSONAL_CALENDAR);
    await flush();

    expect(drawerStub.methods.open).toHaveBeenCalled();
    expect(service.getCalendarLink).toHaveBeenCalledWith(10);
    expect(wrapper.find('.agenda-calendar-link-none').exists()).toBe(true);
    expect(wrapper.find('.agenda-calendar-link-save').text()).toBe('agenda.calendarLink.create');
    expect(wrapper.find('.agenda-calendar-link-delete').exists()).toBe(false);

    await click(wrapper, '.agenda-calendar-link-save');

    expect(confirmStub.methods.open).not.toHaveBeenCalled();
    expect(service.saveCalendarLink).toHaveBeenCalledWith(10);
    expect(wrapper.find('.agenda-calendar-link-url').attributes('value')).toBe(URL);
  });

  it('shows the URL of a working link every time the drawer opens', async () => {
    service.getCalendarLink.mockResolvedValue(activeStatus());
    const wrapper = mountDrawer();

    await wrapper.vm.open(SPACE_CALENDAR);
    expect(wrapper.find('.agenda-calendar-link-active').text()).toContain('Mary Manager');
    expect(wrapper.find('.agenda-calendar-link-url').attributes('value')).toBe(URL);

    wrapper.findComponent(drawerStub).vm.$emit('closed');
    await flush();
    expect(wrapper.find('.agenda-calendar-link-url').exists()).toBe(false);

    await wrapper.vm.open(SPACE_CALENDAR);
    expect(service.getCalendarLink).toHaveBeenCalledTimes(2);
    expect(wrapper.find('.agenda-calendar-link-url').attributes('value')).toBe(URL);
  });

  it('copies the URL to the clipboard and says so', async () => {
    service.getCalendarLink.mockResolvedValue(activeStatus());
    const writeText = jest.fn().mockResolvedValue();
    Object.defineProperty(window.navigator, 'clipboard', {value: {writeText}, configurable: true});
    const wrapper = mountDrawer();
    await wrapper.vm.open(PERSONAL_CALENDAR);

    await click(wrapper, '.agenda-calendar-link-copy');

    expect(writeText).toHaveBeenCalledWith(URL);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarLink.copied', 'success');
  });

  it('resets a working link only once confirmed, and shows the new URL', async () => {
    service.getCalendarLink.mockResolvedValue(activeStatus());
    service.saveCalendarLink.mockResolvedValue(activeStatus({creatorName: 'Paul Manager', url: OTHER_URL}));
    const wrapper = mountDrawer();
    await wrapper.vm.open(SPACE_CALENDAR);
    expect(wrapper.find('.agenda-calendar-link-save').text()).toBe('agenda.calendarLink.reset');

    await click(wrapper, '.agenda-calendar-link-save');

    expect(confirmStub.methods.open).toHaveBeenCalled();
    expect(service.saveCalendarLink).not.toHaveBeenCalled();

    wrapper.findComponent(confirmStub).vm.$emit('ok');
    await flush();

    expect(service.saveCalendarLink).toHaveBeenCalledWith(20);
    expect(wrapper.find('.agenda-calendar-link-url').attributes('value')).toBe(OTHER_URL);
  });

  it('says a working link whose URL cannot be displayed any more, and offers Reset', async () => {
    service.getCalendarLink.mockResolvedValue(activeStatus({displayable: false, url: null}));
    const wrapper = mountDrawer();

    await wrapper.vm.open(PERSONAL_CALENDAR);

    expect(wrapper.find('.agenda-calendar-link-undisplayable').text()).toContain('agenda.calendarLink.undisplayable');
    expect(wrapper.find('.agenda-calendar-link-url').exists()).toBe(false);
    expect(wrapper.find('.agenda-calendar-link-save').text()).toBe('agenda.calendarLink.reset');
  });

  it('names the creator of a space link that stopped working, shows no URL, and offers a new link', async () => {
    service.getCalendarLink.mockResolvedValue({exists: true, active: false, displayable: false, creatorName: 'Mary Manager', createdDate: 1});
    const wrapper = mountDrawer();

    await wrapper.vm.open(SPACE_CALENDAR);

    expect(wrapper.find('.agenda-calendar-link-dead').text()).toBe('agenda.calendarLink.deadSpace(Mary Manager)');
    expect(wrapper.find('.agenda-calendar-link-url').exists()).toBe(false);
    expect(wrapper.find('.agenda-calendar-link-save').text()).toBe('agenda.calendarLink.createNew');
    expect(wrapper.find('.agenda-calendar-link-delete').exists()).toBe(true);
  });

  it('deletes a link only once confirmed, and stays open on the calendar', async () => {
    service.getCalendarLink.mockResolvedValue(activeStatus());
    service.deleteCalendarLink.mockResolvedValue();
    const wrapper = mountDrawer();
    await wrapper.vm.open(PERSONAL_CALENDAR);

    await click(wrapper, '.agenda-calendar-link-delete');
    expect(confirmStub.methods.open).toHaveBeenCalled();
    expect(service.deleteCalendarLink).not.toHaveBeenCalled();

    wrapper.findComponent(confirmStub).vm.$emit('ok');
    await flush();

    expect(service.deleteCalendarLink).toHaveBeenCalledWith(10);
    expect(drawerStub.methods.close).not.toHaveBeenCalled();
    expect(wrapper.findComponent(drawerStub).exists()).toBe(true);
    expect(wrapper.find('.agenda-calendar-link-none').exists()).toBe(true);
    expect(wrapper.find('.agenda-calendar-link-url').exists()).toBe(false);
    expect(wrapper.find('.agenda-calendar-link-save').text()).toBe('agenda.calendarLink.create');
  });

  it('is mounted once, at the application level, and never inside a calendar row', () => {
    const vueApp = path.resolve(__dirname, '../../main/webapp/vue-app');
    const agenda = fs.readFileSync(path.join(vueApp, 'agenda/components/Agenda.vue'), 'utf8');
    const personalList = fs.readFileSync(path.join(vueApp, 'agenda-common/components/filter/AgendaPersonalCalendarList.vue'), 'utf8');
    const spaceItem = fs.readFileSync(path.join(vueApp, 'agenda-common/components/filter/AgendaFilterCalendarItem.vue'), 'utf8');

    expect(agenda.match(/<agenda-calendar-link-drawer\b/g)).toHaveLength(1);
    expect(personalList).not.toMatch(/<agenda-calendar-link-drawer\b/);
    expect(spaceItem).not.toMatch(/<agenda-calendar-link-drawer\b/);
  });

  describe('space calendar row', () => {

    /**
     * Mounts a space calendar row.
     *
     * @param {Object} calendar the calendar
     * @returns {Object} the wrapper
     */
    function mountItem(calendar) {
      const wrapper = shallowMount(AgendaFilterCalendarItem, {
        propsData: {calendar, ownerIds: [100], selectedOwnerIds: []},
        mocks: {$t: key => key},
      });
      wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
      return wrapper;
    }

    it('offers the calendar link to a manager, through the application drawer', async () => {
      const wrapper = mountItem(SPACE_CALENDAR);

      await wrapper.find('.agenda-calendar-link-action').trigger('click');

      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-link-drawer-open', SPACE_CALENDAR);
    });

    it('offers nothing to a member who does not manage the space, nor on an unsaved calendar', () => {
      expect(mountItem({...SPACE_CALENDAR, acl: {canEdit: false}}).find('.agenda-calendar-link-action').exists()).toBe(false);
      expect(mountItem({...SPACE_CALENDAR, acl: null}).find('.agenda-calendar-link-action').exists()).toBe(false);
      expect(mountItem({...SPACE_CALENDAR, id: 0}).find('.agenda-calendar-link-action').exists()).toBe(false);
    });
  });

});
