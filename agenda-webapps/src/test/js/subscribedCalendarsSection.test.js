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

import AgendaLeftPanelSubscribedCalendars from '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanelSubscribedCalendars.vue';
import AgendaLeftPanel from '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanel.vue';
import {ROW_MENU_CLOSE_DELAY, ROW_MENU_CONTENT_CLASS} from '../../main/webapp/vue-app/agenda-common/js/CalendarRowMenuMixin.js';

/**
 * The Subscribed section of the left panel and the panel's "+" menu (EXO-90278).
 *
 * <p>As in calendarRowMenus.test.js, v-menu is a stub with Vuetify's model
 * contract — a value prop — and the pins are on the state the menus are bound
 * to and on the document listener that drives it.</p>
 */
describe('Subscribed calendars in the left panel', () => {

  Vue.config.ignoredElements = [/^v-(?!menu$)/];

  const VMenuStub = {
    name: 'VMenuStub',
    props: ['value', 'contentClass'],
    template: '<div class="v-menu-stub"><slot /></div>',
  };

  const SUBSCRIPTIONS = [
    {id: 11, calendarId: 77, name: 'Holidays', color: '#08a554', url: 'https://feeds.example.org/holidays.ics', lastError: null},
    {id: 12, calendarId: 78, name: 'Team', color: '#2aa8e2', url: 'https://feeds.example.org/team.ics', lastError: 'agenda.calendarSubscription.unreachable'},
  ];

  let service;

  let confirmStub;

  /**
   * Waits for every pending promise callback, with real timers.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Presses the mouse on an element, as a real press reaches the document.
   *
   * @param {Element} element the pressed element
   * @returns {void}
   */
  function press(element) {
    element.dispatchEvent(new MouseEvent('mousedown', {bubbles: true}));
  }

  /**
   * Mounts the section once its listing answered.
   *
   * @returns {Promise<Object>} the wrapper
   */
  async function mountSection() {
    const wrapper = shallowMount(AgendaLeftPanelSubscribedCalendars, {
      attachTo: document.body,
      mocks: {
        $t: (key, args) => args && `${key}(${Object.values(args).join('|')})` || key,
        $calendarSubscriptionService: service,
      },
      stubs: {
        'v-menu': VMenuStub,
        'exo-confirm-dialog': confirmStub,
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
    service = {
      getSubscriptions: jest.fn().mockResolvedValue(SUBSCRIPTIONS.map(subscription => ({...subscription}))),
      refreshSubscription: jest.fn(),
      deleteSubscription: jest.fn(),
    };
    confirmStub = {
      template: '<div class="confirm-stub"></div>',
      methods: {
        open: jest.fn(),
      },
    };
  });

  afterEach(() => {
    jest.useRealTimers();
    document.body.innerHTML = '';
  });

  it('draws nothing while the user has no subscription', async () => {
    service.getSubscriptions.mockResolvedValue([]);

    const wrapper = await mountSection();

    expect(wrapper.find('.agenda-subscribed-calendars').exists()).toBe(false);
  });

  it('lists each subscription with its sign, and a warning sign on the one whose refresh failed', async () => {
    const wrapper = await mountSection();

    expect(wrapper.findAll('.agenda-subscribed-calendar')).toHaveLength(2);
    expect(wrapper.findAll('.agenda-calendar-subscription-sign')).toHaveLength(2);
    const warnings = wrapper.findAll('.agenda-calendar-subscription-error-sign');
    expect(warnings).toHaveLength(1);
    expect(warnings.at(0).attributes('title'))
      .toBe('agenda.calendarSubscription.lastErrorTooltip(agenda.calendarSubscription.unreachable)');
  });

  it('closes a row menu on a press outside it, not on a press inside it, and when another calendar menu opens', async () => {
    const wrapper = await mountSection();
    jest.useFakeTimers();
    const menus = () => wrapper.findAllComponents(VMenuStub);

    wrapper.vm.toggleRowMenu(11, true);
    await wrapper.vm.$nextTick();
    expect(menus().at(0).props('value')).toBe(true);
    expect(menus().at(0).props('contentClass')).toBe(ROW_MENU_CONTENT_CLASS);

    const content = document.createElement('div');
    content.className = ROW_MENU_CONTENT_CLASS;
    document.body.appendChild(content);
    press(content);
    jest.advanceTimersByTime(ROW_MENU_CLOSE_DELAY);
    await wrapper.vm.$nextTick();
    expect(menus().at(0).props('value')).toBe(true);

    press(document.body);
    jest.advanceTimersByTime(ROW_MENU_CLOSE_DELAY);
    await wrapper.vm.$nextTick();
    expect(menus().at(0).props('value')).toBe(false);

    wrapper.vm.toggleRowMenu(12, true);
    await wrapper.vm.$nextTick();
    wrapper.vm.$root.$emit('agenda-calendar-row-menu-opened', -1);
    await wrapper.vm.$nextTick();
    expect(menus().at(1).props('value')).toBe(false);
  });

  it('remembers a hidden subscribed calendar for the user and tells the agenda', async () => {
    const wrapper = await mountSection();

    await wrapper.findAll('v-checkbox').at(0).trigger('change');

    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-subscribed-calendars-visibility-changed', [77]);
    expect(JSON.parse(localStorage.getItem('agenda.hiddenSubscribedCalendars.1'))).toEqual([77]);
  });

  it('takes a refresh answer into its row, says when the link failed, and says why a refresh was refused', async () => {
    const wrapper = await mountSection();
    service.refreshSubscription.mockResolvedValue({...SUBSCRIPTIONS[0], lastError: 'agenda.calendarSubscription.timeout'});

    await wrapper.findAll('.agenda-calendar-subscription-refresh').at(0).trigger('click');
    await flush();

    expect(service.refreshSubscription).toHaveBeenCalledWith(11);
    expect(wrapper.findAll('.agenda-calendar-subscription-error-sign')).toHaveLength(2);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarSubscription.timeout', 'warning');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-refresh');

    service.refreshSubscription.mockRejectedValue(new Error('agenda.calendarSubscription.refreshTooSoon'));
    await wrapper.findAll('.agenda-calendar-subscription-refresh').at(1).trigger('click');
    await flush();
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarSubscription.refreshTooSoon', 'error');
  });

  it('asks before unsubscribing, then removes the row and refreshes the agenda', async () => {
    service.deleteSubscription.mockResolvedValue(null);
    const wrapper = await mountSection();

    await wrapper.findAll('.agenda-calendar-subscription-unsubscribe').at(0).trigger('click');
    expect(confirmStub.methods.open).toHaveBeenCalled();
    expect(service.deleteSubscription).not.toHaveBeenCalled();

    wrapper.findComponent(confirmStub).vm.$emit('ok');
    await flush();

    expect(service.deleteSubscription).toHaveBeenCalledWith(11);
    expect(wrapper.findAll('.agenda-subscribed-calendar')).toHaveLength(1);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-refresh');
  });

  it('opens the drawer on a subscription to edit it', async () => {
    const wrapper = await mountSection();

    await wrapper.findAll('.agenda-calendar-subscription-edit').at(1).trigger('click');

    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-subscription-drawer-open', expect.objectContaining({id: 12}));
  });

  it('offers adding a calendar and subscribing to a link from the "+" menu of the panel', async () => {
    global.extensionRegistry = {loadExtensions: () => []};
    const panel = shallowMount(AgendaLeftPanel, {
      mocks: {
        $t: key => key,
        $agendaUtils: {getWeekSequenceFromDay: () => [1]},
        $vuetify: {rtl: false},
      },
      stubs: {
        'v-menu': VMenuStub,
      },
    });
    const emit = jest.spyOn(panel.vm.$root, '$emit');

    panel.vm.toggleRowMenu('add', true);
    await panel.vm.$nextTick();
    expect(panel.findAllComponents(VMenuStub).at(0).props('value')).toBe(true);

    await panel.find('.agenda-left-panel-add-calendar').trigger('click');
    expect(emit).toHaveBeenCalledWith('agenda-personal-calendar-drawer-open');
    await panel.find('.agenda-left-panel-subscribe').trigger('click');
    expect(emit).toHaveBeenCalledWith('agenda-calendar-subscription-drawer-open');

    const template = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanel.vue'), 'utf8');
    expect(template.indexOf('<agenda-left-panel-subscribed-calendars')).toBeGreaterThan(template.indexOf('<agenda-left-panel-remote-calendars'));
    expect(template.indexOf('<agenda-left-panel-subscribed-calendars')).toBeLessThan(template.indexOf("$t('agenda.leftPanel.spaces')"));
  });

});
