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

import AgendaCalendarSubscriptionDrawer from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaCalendarSubscriptionDrawer.vue';
import {ERROR_CODES, errorMessageKey} from '../../main/webapp/vue-app/agenda-common/js/CalendarSubscriptionService.js';

/**
 * The drawer subscribing to a calendar link (EXO-90278): its states — empty,
 * checking, checked, refused, saving — what it sends, and where it lives.
 *
 * <p>Vuetify is not part of the agenda build, so its fields are inert elements
 * here: the tests set the drawer's data and call its methods, and read what it
 * renders. That a v-text-field feeds v-model is checked on a live page.</p>
 */
describe('Calendar subscription drawer', () => {

  Vue.config.ignoredElements = [/^v-/];

  const URL = 'https://feeds.example.org/holidays.ics';

  let service;

  let drawerStub;

  /**
   * Waits for every pending promise callback.
   *
   * @returns {Promise} resolved on the next macrotask
   */
  function flush() {
    return new Promise(resolve => setTimeout(resolve));
  }

  /**
   * Mounts the drawer with eXo's $t, the service mocked and exo-drawer stubbed.
   *
   * @returns {Object} the wrapper
   */
  function mountDrawer() {
    const wrapper = shallowMount(AgendaCalendarSubscriptionDrawer, {
      mocks: {
        $t: (key, args) => args && `${key}(${Object.values(args).join('|')})` || key,
        $calendarSubscriptionService: service,
        $agendaUtils: {EVENT_COLOR_SWATCHES: [['#08a554']]},
      },
      stubs: {
        'exo-drawer': drawerStub,
      },
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    return wrapper;
  }

  beforeEach(() => {
    service = {
      checkUrl: jest.fn(),
      createSubscription: jest.fn(),
      updateSubscription: jest.fn(),
    };
    drawerStub = {
      template: '<div class="drawer-stub"><slot name="title"></slot><slot name="content"></slot><slot name="footer"></slot></div>',
      methods: {
        open: jest.fn(),
        close: jest.fn(),
      },
    };
  });

  it('opens empty on its root event, Subscribe waiting for a link', async () => {
    const wrapper = mountDrawer();

    wrapper.vm.$root.$emit('agenda-calendar-subscription-drawer-open');
    await wrapper.vm.$nextTick();

    expect(drawerStub.methods.open).toHaveBeenCalled();
    expect(wrapper.text()).toContain('agenda.calendarSubscription.subscribeTitle');
    expect(wrapper.find('.agenda-calendar-subscription-save').attributes('disabled')).toBeTruthy();
    expect(wrapper.find('.agenda-calendar-subscription-error').exists()).toBe(false);
  });

  it('checks a link and names the calendar after it', async () => {
    service.checkUrl.mockResolvedValue({name: 'Public holidays'});
    const wrapper = mountDrawer();
    wrapper.vm.open();
    await wrapper.setData({url: ` webcal://feeds.example.org/holidays.ics `});

    await wrapper.vm.check();
    await wrapper.vm.$nextTick();

    expect(service.checkUrl).toHaveBeenCalledWith('webcal://feeds.example.org/holidays.ics');
    expect(wrapper.vm.name).toBe('Public holidays');
    expect(wrapper.find('.agenda-calendar-subscription-checked').exists()).toBe(true);
    expect(wrapper.find('.agenda-calendar-subscription-save').attributes('disabled')).toBeFalsy();
  });

  it('keeps a name the user typed when the link is checked', async () => {
    service.checkUrl.mockResolvedValue({name: 'Public holidays'});
    const wrapper = mountDrawer();
    wrapper.vm.open();
    await wrapper.setData({url: URL, name: 'Mine', nameTouched: true});

    await wrapper.vm.check();

    expect(wrapper.vm.name).toBe('Mine');
  });

  it('is busy while the server reads the link', async () => {
    service.checkUrl.mockReturnValue(new Promise(() => {}));
    const wrapper = mountDrawer();
    wrapper.vm.open();
    await wrapper.setData({url: URL});

    wrapper.vm.check();
    await wrapper.vm.$nextTick();

    expect(wrapper.vm.state).toBe('checking');
    expect(wrapper.find('.agenda-calendar-subscription-save').attributes('disabled')).toBeTruthy();
    expect(wrapper.find('.agenda-calendar-subscription-check').attributes('disabled')).toBeTruthy();
  });

  it('shows the sentence of a refusal, the generic one for an unknown refusal, and forgets it when the link changes', async () => {
    const wrapper = mountDrawer();
    wrapper.vm.open();
    await wrapper.setData({url: 'http://10.0.0.5/cal.ics'});

    service.checkUrl.mockRejectedValue(new Error('agenda.calendarSubscription.refusedAddress'));
    await wrapper.vm.check();
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.agenda-calendar-subscription-error').text()).toBe('agenda.calendarSubscription.refusedAddress');

    service.checkUrl.mockRejectedValue(new Error('Something unexpected'));
    await wrapper.vm.check();
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.agenda-calendar-subscription-error').text()).toBe('agenda.calendarSubscription.error');

    wrapper.vm.urlChanged();
    await wrapper.vm.$nextTick();
    expect(wrapper.find('.agenda-calendar-subscription-error').exists()).toBe(false);
  });

  it('subscribes with the link, the name and the colour, then refreshes the agenda and closes', async () => {
    service.createSubscription.mockResolvedValue({id: 11, name: 'Holidays'});
    const wrapper = mountDrawer();
    wrapper.vm.open();
    await wrapper.setData({url: URL, name: ' Holidays ', color: '#112233'});

    await wrapper.vm.save();
    await flush();

    expect(service.createSubscription).toHaveBeenCalledWith({url: URL, name: 'Holidays', color: '#112233'});
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-refresh-subscribed-calendars');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-refresh');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarSubscription.subscribed(Holidays)', 'success');
    expect(drawerStub.methods.close).toHaveBeenCalled();
  });

  it('stays open on a refused subscription, with its sentence', async () => {
    service.createSubscription.mockRejectedValue(new Error('agenda.calendarSubscription.alreadySubscribed'));
    const wrapper = mountDrawer();
    wrapper.vm.open();
    await wrapper.setData({url: URL});

    await wrapper.vm.save();
    await wrapper.vm.$nextTick();

    expect(drawerStub.methods.close).not.toHaveBeenCalled();
    expect(wrapper.find('.agenda-calendar-subscription-error').text()).toBe('agenda.calendarSubscription.alreadySubscribed');
    expect(wrapper.rootEmit).not.toHaveBeenCalledWith('agenda-refresh-subscribed-calendars');
  });

  it('edits a subscription: an unchanged link is not sent again, a changed one is', async () => {
    service.updateSubscription.mockResolvedValue({id: 11});
    const wrapper = mountDrawer();
    wrapper.vm.open({id: 11, url: URL, name: 'Holidays', color: '#08a554'});
    await wrapper.vm.$nextTick();
    expect(wrapper.text()).toContain('agenda.calendarSubscription.editTitle');
    expect(wrapper.vm.nameTouched).toBe(true);

    await wrapper.setData({name: 'Renamed'});
    await wrapper.vm.save();
    expect(service.updateSubscription).toHaveBeenLastCalledWith(11, {url: null, name: 'Renamed', color: '#08a554'});

    wrapper.vm.open({id: 11, url: URL, name: 'Holidays', color: '#08a554'});
    await wrapper.setData({url: 'https://feeds.example.org/other.ics'});
    await wrapper.vm.save();
    expect(service.updateSubscription).toHaveBeenLastCalledWith(11, {url: 'https://feeds.example.org/other.ics', name: 'Holidays', color: '#08a554'});
    expect(wrapper.rootEmit).not.toHaveBeenCalledWith('alert-message', expect.anything(), 'success');
  });

  it('lets a subscription whose link cannot be read be renamed', async () => {
    service.updateSubscription.mockResolvedValue({id: 12});
    const wrapper = mountDrawer();
    wrapper.vm.open({id: 12, url: null, name: 'Old', color: null});
    await wrapper.vm.$nextTick();

    expect(wrapper.find('.agenda-calendar-subscription-save').attributes('disabled')).toBeFalsy();
    await wrapper.vm.save();
    expect(service.updateSubscription).toHaveBeenCalledWith(12, {url: null, name: 'Old', color: null});
  });

  it('is mounted once, in Agenda.vue, never in a row of the panel', () => {
    const agenda = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/vue-app/agenda/components/Agenda.vue'), 'utf8');
    const section = fs.readFileSync(path.resolve(__dirname, '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanelSubscribedCalendars.vue'), 'utf8');

    expect(agenda.match(/<agenda-calendar-subscription-drawer\b/g)).toHaveLength(1);
    expect(section).not.toContain('<agenda-calendar-subscription-drawer');
  });

  it('has an English sentence for every refusal the server answers', () => {
    const bundle = fs.readFileSync(path.resolve(__dirname, '../../main/resources/locale/portlet/Agenda_en.properties'), 'utf8');

    for (const code of ERROR_CODES.concat('agenda.calendarSubscription.error')) {
      expect(bundle).toMatch(new RegExp(`^${code.replace(/\./g, '\\.')}=.+$`, 'm'));
    }
    expect(errorMessageKey('agenda.calendarSubscription.timeout')).toBe('agenda.calendarSubscription.timeout');
    expect(errorMessageKey('java.lang.NullPointerException')).toBe('agenda.calendarSubscription.error');
    expect(errorMessageKey(undefined)).toBe('agenda.calendarSubscription.error');
  });

});
