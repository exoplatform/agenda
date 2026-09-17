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

import AgendaLeftPanelSharedCalendars from '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanelSharedCalendars.vue';
import AgendaLeftPanelRemoteCalendars from '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanelRemoteCalendars.vue';

/**
 * The native "Shared with me" section (EXO-90357): the calendars colleagues
 * shared in eXo, what the grid is told to draw, hiding for good, and how the
 * CalDAV section leaves out the server's copy of a share eXo draws already.
 */
describe('Calendars shared with me in eXo', () => {

  Vue.config.ignoredElements = [/^v-/, 'exo-user-avatar'];

  const BOB_CAL = {calendarId: 41, name: 'Bob', color: '#ff0000', ownerId: 2, ownerUsername: 'bob', ownerDisplayName: 'Bob Builder', hidden: false, deliveredTo: 'caldav:1', deliveryRef: '/dav/calendars/__uids__/BOB-UID/calendar:Default:BOB/'};

  const CAROL_CAL = {calendarId: 42, name: 'Carol', color: '#00ff00', ownerId: 3, ownerUsername: 'carol', ownerDisplayName: 'Carol', hidden: false, deliveredTo: null, deliveryRef: null};

  const HIDDEN_CAL = {calendarId: 43, name: 'Dave', color: '#0000ff', ownerId: 4, ownerUsername: 'dave', ownerDisplayName: 'Dave', hidden: true};

  let service;

  /**
   * Waits for every pending promise callback, with real timers.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Mounts the native section once its listing answered.
   *
   * @returns {Promise<Object>} the wrapper
   */
  async function mountSection() {
    const wrapper = shallowMount(AgendaLeftPanelSharedCalendars, {
      attachTo: document.body,
      mocks: {
        $t: (key, args) => args && `${key}(${Object.values(args).join('|')})` || key,
        $calendarShareService: service,
      },
      stubs: {
        'v-menu': {name: 'VMenuStub', props: ['value'], template: '<div class="v-menu-stub"><slot /></div>'},
      },
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    await flush();
    return wrapper;
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1', language: 'en'}}};
  });

  beforeEach(() => {
    localStorage.clear();
    // The stand-in remembers a hide, as the server does: the section reads
    // the listing again once it told the settings page, and must find the
    // calendar hidden there
    const rows = [BOB_CAL, CAROL_CAL, HIDDEN_CAL].map(calendar => ({...calendar}));
    service = {
      getSharedWithMe: jest.fn().mockImplementation(() => Promise.resolve(rows.map(calendar => ({...calendar})))),
      setHidden: jest.fn().mockImplementation((calendarId, hidden) => {
        rows.forEach(calendar => {
          if (calendar.calendarId === calendarId) {
            calendar.hidden = hidden;
          }
        });
        return Promise.resolve();
      }),
    };
  });

  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('draws nothing while nothing is shared with the user', async () => {
    service.getSharedWithMe.mockResolvedValue([]);

    const wrapper = await mountSection();

    expect(wrapper.find('.agenda-shared-calendars').exists()).toBe(false);
  });

  it('lists the shares the user did not hide, and tells the grid which calendars to draw', async () => {
    const wrapper = await mountSection();

    expect(wrapper.findAll('.agenda-shared-calendar')).toHaveLength(2);
    expect(wrapper.findAll('.agenda-shared-calendar').at(0).find('v-list-item-content').attributes('title')).toBe('Bob — agenda.leftPanel.sharedBy(Bob Builder)');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-shared-calendars-displayed-changed', [41, 42]);
  });

  it('unticking a calendar takes it off the grid and remembers it for this user', async () => {
    const wrapper = await mountSection();

    wrapper.vm.toggle(CAROL_CAL);
    await flush();

    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-shared-calendars-displayed-changed', [41]);
    expect(JSON.parse(localStorage.getItem('agenda.hiddenSharedCalendars.1'))).toEqual([42]);
  });

  it('hiding for good records the choice on the server, keeps the share, and says where it comes back from', async () => {
    const wrapper = await mountSection();

    await wrapper.find('.agenda-shared-calendar-hide').trigger('click');
    await flush();

    expect(service.setHidden).toHaveBeenCalledWith(41, true);
    expect(wrapper.findAll('.agenda-shared-calendar')).toHaveLength(1);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.leftPanel.sharedCalendarHidden(Bob)', 'success');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-shared-calendars-displayed-changed', [42]);
  });

  it('puts the row back and says so when hiding fails', async () => {
    service.setHidden.mockImplementation(() => Promise.reject(new Error('agenda.share.error')));
    const wrapper = await mountSection();

    await wrapper.find('.agenda-shared-calendar-hide').trigger('click');
    await flush();

    expect(wrapper.findAll('.agenda-shared-calendar')).toHaveLength(2);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.leftPanel.hideSharedCalendarError', 'error');
  });

  it('publishes the delivery references of its shares, so the CalDAV section leaves out the server copy', async () => {
    const wrapper = await mountSection();

    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-native-shared-calendars', [BOB_CAL.deliveryRef]);
  });

  describe('the CalDAV section', () => {

    /**
     * A connected CalDAV connector listing Bob's calendar twice over: the
     * collection a native share was delivered to, and a colleague's share
     * that exists on the server only.
     *
     * @returns {Object} the connector
     */
    function connector() {
      return {
        name: 'agenda.caldavCalendar',
        isCaldav: true,
        canListCalendars: true,
        connected: true,
        listCalendars: () => Promise.resolve([
          {id: '/dav/calendars/__uids__/BOB-UID/calendar%3ADefault%3ABOB/', name: 'Bob', shared: true, readOnly: true, ownerUsername: 'bob', ownerDisplayName: 'Bob Builder'},
          {id: '/dav/calendars/__uids__/ERIC-UID/calendar:Default:ERIC/', name: 'Eric', shared: true, readOnly: true, ownerUsername: 'eric', ownerDisplayName: 'Eric'},
        ]),
        hideCalendar: () => Promise.resolve(),
      };
    }

    /**
     * Mounts the CalDAV section over one connector, with the mirror exclusion
     * answering the listing unchanged.
     *
     * @returns {Promise<Object>} the wrapper
     */
    async function mountRemote() {
      const wrapper = shallowMount(AgendaLeftPanelRemoteCalendars, {
        propsData: {connectors: [connector()]},
        mocks: {
          $t: key => key,
          $remoteEventConnector: {excludeMirrorCalendar: (one, calendars) => Promise.resolve(calendars)},
        },
        stubs: {
          'v-menu': {name: 'VMenuStub', props: ['value'], template: '<div class="v-menu-stub"><slot /></div>'},
        },
      });
      wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
      await flush();
      await flush();
      return wrapper;
    }

    it('asks the native section what it draws when it mounts', async () => {
      // The ask happens in created(), before any spy on the instance exists
      const emit = jest.spyOn(Vue.prototype, '$emit');
      try {
        const wrapper = await mountRemote();

        expect(emit).toHaveBeenCalledWith('agenda-native-shared-calendars-requested');
        expect(wrapper.findAll('.agenda-calendar-settings')).toHaveLength(2);
      } finally {
        emit.mockRestore();
      }
    });

    it('leaves out the server copy of a share eXo draws, compared on decoded paths, and keeps its events off the grid', async () => {
      const wrapper = await mountRemote();

      wrapper.vm.$root.$emit('agenda-native-shared-calendars', [BOB_CAL.deliveryRef]);
      await flush();

      const rows = wrapper.findAll('.agenda-calendar-settings');
      expect(rows).toHaveLength(1);
      expect(rows.at(0).find('v-checkbox').attributes('label')).toBe('Eric');
      expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-remote-calendars-changed',
        ['/dav/calendars/__uids__/BOB-UID/calendar%3ADefault%3ABOB/']);
    });

    it('draws the row again once the native share is gone', async () => {
      const wrapper = await mountRemote();
      wrapper.vm.$root.$emit('agenda-native-shared-calendars', [BOB_CAL.deliveryRef]);
      await flush();

      wrapper.vm.$root.$emit('agenda-native-shared-calendars', []);
      await flush();

      expect(wrapper.findAll('.agenda-calendar-settings')).toHaveLength(2);
      expect(wrapper.rootEmit).toHaveBeenLastCalledWith('agenda-remote-calendars-changed', []);
    });
  });

});
