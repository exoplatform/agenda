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
 * One "Shared with me" section (EXO-90357): the calendars colleagues shared
 * in eXo and the ones a CalDAV server lists as shared, in one list, each
 * calendar once — a share delivered to the server is drawn through its eXo
 * row and the server's copy is left out — with the same row whichever the
 * source, what the grid is told to draw, hiding for good, and rows that stay
 * mounted while the sources are asked again.
 */
describe('One "Shared with me" section for eXo shares and server shares', () => {

  Vue.config.ignoredElements = [/^v-/, 'exo-user-avatar'];

  /** Bob's calendar, shared in eXo and delivered to the CalDAV server. */
  const BOB_CAL = {calendarId: 41, name: 'Bob', color: '#ff0000', ownerId: 2, ownerUsername: 'bob', ownerDisplayName: 'Bob Builder', hidden: false, deliveredTo: 'caldav:1', deliveryRef: '/dav/calendars/__uids__/BOB-UID/calendar:Default:BOB/'};

  /** Carol's calendar, shared in eXo only. */
  const CAROL_CAL = {calendarId: 42, name: 'Carol', color: '#00ff00', ownerId: 3, ownerUsername: 'carol', ownerDisplayName: 'Carol', hidden: false, deliveredTo: null, deliveryRef: null};

  const HIDDEN_CAL = {calendarId: 43, name: 'Dave', color: '#0000ff', ownerId: 4, ownerUsername: 'dave', ownerDisplayName: 'Dave', hidden: true};

  /** The server's copy of Bob's share: the same collection, encoded differently. */
  const BOB_ON_SERVER = {id: '/dav/calendars/__uids__/BOB-UID/calendar%3ADefault%3ABOB/', name: 'Bob', shared: true, readOnly: true, ownerUsername: 'bob', ownerDisplayName: 'Bob Builder'};

  /** A share that exists on the server only. */
  const ERIC_ON_SERVER = {id: '/dav/calendars/__uids__/ERIC-UID/calendar:Default:ERIC/', name: 'Eric', shared: true, readOnly: true, ownerUsername: 'eric', ownerDisplayName: 'Eric'};

  let service;

  /**
   * Waits for every pending promise callback and the gathered retrieval's
   * timer, with real timers.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * A connected CalDAV connector whose listing the test answers by hand:
   * each call to listCalendars appends its resolver to `answers`.
   *
   * @returns {Object} `{connector, answers, hidden}`
   */
  function deferredConnector() {
    const answers = [];
    const hidden = [];
    return {
      answers,
      hidden,
      connector: {
        name: 'agenda.caldavCalendar',
        isCaldav: true,
        canListCalendars: true,
        connected: true,
        listCalendars: () => new Promise(resolve => answers.push(resolve)),
        hideCalendar: id => {
          hidden.push(id);
          return Promise.resolve();
        },
      },
    };
  }

  /**
   * Mounts the section over the given connectors, with the share service
   * mocked, and lets the first gathered retrieval go out.
   *
   * @param {Array} connectors the connectors, none by default
   * @returns {Promise<Object>} the wrapper
   */
  async function mountSection(connectors = []) {
    const wrapper = shallowMount(AgendaLeftPanelRemoteCalendars, {
      attachTo: document.body,
      propsData: {connectors},
      mocks: {
        $t: (key, args) => args && `${key}(${Object.values(args).join('|')})` || key,
        $calendarShareService: service,
        $remoteEventConnector: {excludeMirrorCalendar: (one, calendars) => Promise.resolve(calendars)},
      },
      stubs: {
        'v-menu': {name: 'VMenuStub', props: ['value'], template: '<div class="v-menu-stub"><slot /></div>'},
      },
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    await flush();
    return wrapper;
  }

  /**
   * Answers the connector's pending listing and lets the section redraw.
   *
   * @param {Object} deferred the connector harness
   * @param {Array} calendars the listing
   * @returns {Promise} resolved once drawn
   */
  async function answer(deferred, calendars) {
    deferred.answers.shift()(calendars);
    await flush();
    await flush();
  }

  /**
   * The names the section draws, in order.
   *
   * @param {Object} wrapper the wrapper
   * @returns {Array} the checkbox labels
   */
  function names(wrapper) {
    return wrapper.findAll('.agenda-calendar-settings').wrappers.map(row => row.find('v-checkbox').attributes('label'));
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {userIdentityId: '1', language: 'en'}}};
  });

  beforeEach(() => {
    localStorage.clear();
    // The stand-in remembers a hide, as the server does
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

  it('draws nothing while nothing is shared with the user, from either source', async () => {
    service.getSharedWithMe.mockResolvedValue([]);
    const deferred = deferredConnector();

    const wrapper = await mountSection([deferred.connector]);
    await answer(deferred, []);

    expect(wrapper.find('.agenda-left-panel-section').exists()).toBe(false);
  });

  it('draws the eXo shares without any connected account, and tells the grid which calendars to draw', async () => {
    const wrapper = await mountSection();

    expect(wrapper.findAll('.agenda-left-panel-title')).toHaveLength(1);
    expect(wrapper.find('.agenda-left-panel-title').text()).toBe('agenda.leftPanel.sharedWithMe');
    expect(names(wrapper)).toEqual(['Bob', 'Carol']);
    expect(wrapper.findAll('.agenda-calendar-settings').at(0).find('v-list-item-content').attributes('title')).toBe('Bob — agenda.leftPanel.sharedBy(Bob Builder)');
    expect(wrapper.findAll('.agenda-calendar-settings').at(0).find('exo-user-avatar').attributes('profile-id')).toBe('bob');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-shared-calendars-displayed-changed', [41, 42]);
  });

  it('draws one heading with an eXo share and a server share together, each calendar once, the delivered share through its eXo row', async () => {
    const deferred = deferredConnector();
    const wrapper = await mountSection([deferred.connector]);

    await answer(deferred, [BOB_ON_SERVER, ERIC_ON_SERVER]);

    expect(wrapper.findAll('.agenda-left-panel-title')).toHaveLength(1);
    expect(names(wrapper)).toEqual(['Bob', 'Carol', 'Eric']);
    const rows = wrapper.findAll('.agenda-calendar-settings');
    expect(rows.at(0).attributes('class')).toBe(rows.at(2).attributes('class'), 'the same row whichever the source');
    expect(rows.at(0).find('exo-user-avatar').attributes('profile-id')).toBe('bob');
    expect(rows.at(2).find('exo-user-avatar').attributes('profile-id')).toBe('eric');
    expect(rows.at(0).find('.agenda-shared-calendar-hide').exists()).toBe(true);
    expect(rows.at(2).find('.agenda-shared-calendar-hide').exists()).toBe(true);
    // The server's copy of Bob's share keeps its events off the grid too
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-remote-calendars-changed', [BOB_ON_SERVER.id]);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-shared-calendars-displayed-changed', [41, 42]);
  });

  it('keeps the rows mounted while both sources are asked again', async () => {
    const deferred = deferredConnector();
    const wrapper = await mountSection([deferred.connector]);
    await answer(deferred, [ERIC_ON_SERVER]);
    const before = wrapper.findAll('.agenda-calendar-settings').wrappers.map(row => row.element);

    wrapper.vm.$root.$emit('agenda-refresh');
    wrapper.vm.$root.$emit('agenda-refresh-shared-calendars');
    await flush();

    expect(deferred.answers).toHaveLength(1, 'one request for the burst');
    expect(names(wrapper)).toEqual(['Bob', 'Carol', 'Eric']);
    await answer(deferred, [ERIC_ON_SERVER]);
    const after = wrapper.findAll('.agenda-calendar-settings').wrappers.map(row => row.element);
    expect(after).toEqual(before);
    expect(service.getSharedWithMe).toHaveBeenCalledTimes(2);
  });

  it('unticking an eXo share takes it off the grid and remembers it for this user; unticking a server share hides its remote events', async () => {
    const deferred = deferredConnector();
    const wrapper = await mountSection([deferred.connector]);
    await answer(deferred, [ERIC_ON_SERVER]);

    wrapper.vm.toggle(wrapper.vm.groups[0].calendars[1]);
    wrapper.vm.toggle(wrapper.vm.groups[0].calendars[2]);
    await flush();

    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-shared-calendars-displayed-changed', [41]);
    expect(JSON.parse(localStorage.getItem('agenda.hiddenSharedCalendars.1'))).toEqual([42]);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-remote-calendars-changed', [ERIC_ON_SERVER.id]);
  });

  it('hiding an eXo share for good records the choice in agenda, keeps the share, and says where it comes back from', async () => {
    const wrapper = await mountSection();

    await wrapper.findAll('.agenda-shared-calendar-hide').at(0).trigger('click');
    await flush();

    expect(service.setHidden).toHaveBeenCalledWith(41, true);
    expect(names(wrapper)).toEqual(['Carol']);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.leftPanel.sharedCalendarHidden(Bob)', 'success');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-shared-calendars-displayed-changed', [42]);
  });

  it('hiding a server share goes through its connector', async () => {
    service.getSharedWithMe.mockResolvedValue([]);
    const deferred = deferredConnector();
    const wrapper = await mountSection([deferred.connector]);
    await answer(deferred, [ERIC_ON_SERVER]);

    await wrapper.find('.agenda-shared-calendar-hide').trigger('click');
    await flush();

    expect(deferred.hidden).toEqual([ERIC_ON_SERVER.id]);
    expect(service.setHidden).not.toHaveBeenCalled();
  });

  it('puts the row back and says so when hiding an eXo share fails', async () => {
    service.setHidden.mockImplementation(() => Promise.reject(new Error('agenda.share.error')));
    const wrapper = await mountSection();

    await wrapper.findAll('.agenda-shared-calendar-hide').at(0).trigger('click');
    await flush();

    expect(names(wrapper)).toEqual(['Bob', 'Carol']);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.leftPanel.hideSharedCalendarError', 'error');
  });

  it('draws the server row again once the eXo share is gone', async () => {
    const deferred = deferredConnector();
    const wrapper = await mountSection([deferred.connector]);
    await answer(deferred, [BOB_ON_SERVER]);
    expect(names(wrapper)).toEqual(['Bob', 'Carol']);

    service.getSharedWithMe.mockResolvedValue([CAROL_CAL]);
    wrapper.vm.$root.$emit('agenda-refresh');
    await flush();
    await answer(deferred, [BOB_ON_SERVER]);

    expect(names(wrapper)).toEqual(['Bob', 'Carol']);
    expect(wrapper.vm.groups[0].calendars[0].id).toBe(BOB_ON_SERVER.id);
    expect(wrapper.rootEmit).toHaveBeenLastCalledWith('agenda-remote-calendars-changed', []);
  });
});
