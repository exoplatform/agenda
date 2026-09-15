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

import AgendaUserPublishedCalendarsDrawer from '../../main/webapp/vue-app/agenda-user-setting/components/AgendaUserPublishedCalendarsDrawer.vue';

/**
 * The published calendars drawer of the user settings (EXO-90252): one entry
 * per published calendar, what it offers in each state, and closing itself once
 * the last calendar is unpublished — the shape of caldav's hidden calendars
 * drawer and its pins.
 */
describe('AgendaUserPublishedCalendarsDrawer', () => {

  Vue.config.ignoredElements = [/^v-/];

  const PERSONAL = {calendarId: 10, calendarKind: 'PERSONAL', calendarTitle: 'Mary', systemCalendar: true, exists: true,
    active: true, displayable: true, url: 'https://x/agenda/rest/ical/a.ics', creatorName: 'Mary', createdDate: Date.UTC(2026, 8, 1)};

  const SPACE_STOPPED = {calendarId: 20, calendarKind: 'SPACE', calendarTitle: 'Chemistry', spaceDisplayName: 'Chemistry',
    exists: true, active: false, displayable: false, creatorName: 'Paul', createdDate: Date.UTC(2026, 8, 2)};

  const SPACE_UNDISPLAYABLE = {calendarId: 21, calendarKind: 'SPACE', calendarTitle: 'Lab bookings', spaceDisplayName: 'Chemistry',
    exists: true, active: true, displayable: false, creatorName: 'Paul', createdDate: Date.UTC(2026, 8, 3)};

  let service;

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
   * Mounts the drawer over a fixed list, with a `$t` echoing its arguments.
   *
   * @param {Array} links the published calendars
   * @returns {Object} the wrapper
   */
  function mountDrawer(links) {
    const wrapper = shallowMount(AgendaUserPublishedCalendarsDrawer, {
      propsData: {links},
      mocks: {
        $t: (key, params) => (params ? `${key}(${Object.values(params).join(',')})` : key),
        $vuetify: {rtl: false},
        $calendarLinkService: service,
      },
      stubs: {
        'exo-drawer': {
          template: '<div><slot name="title"></slot><slot name="content"></slot></div>',
          methods: {
            open() {
              this.$emit('input', true);
            },
            close() {
              this.$emit('input', false);
              this.$emit('closed');
            },
          },
        },
        'exo-confirm-dialog': confirmStub,
      },
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    return wrapper;
  }

  /**
   * The entries of the drawer.
   *
   * @param {Object} wrapper the drawer
   * @returns {Array} the entry wrappers
   */
  function entries(wrapper) {
    return wrapper.findAll('.agenda-published-calendar').wrappers;
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {language: 'en'}}};
  });

  beforeEach(() => {
    service = {
      saveCalendarLink: jest.fn().mockResolvedValue({}),
      deleteCalendarLink: jest.fn().mockResolvedValue(),
    };
    confirmStub = {
      template: '<div class="confirm-stub"></div>',
      methods: {open: jest.fn()},
    };
  });

  it('describes a personal calendar and offers copy, reset and unpublish', () => {
    const [entry] = entries(mountDrawer([PERSONAL]));

    expect(entry.find('.agenda-published-calendar-kind').text()).toBe('fas fa-user');
    expect(entry.find('.agenda-published-calendar-title').text()).toBe('agenda.myCalendar');
    expect(entry.find('.agenda-published-calendar-owner').text()).toBe('agenda.calendarPublish.settings.personal');
    expect(entry.find('.agenda-published-calendar-date').text()).toMatch(/^agenda\.calendarPublish\.settings\.publishedOn\(/);
    expect(entry.find('.agenda-published-calendar-copy').exists()).toBe(true);
    expect(entry.find('.agenda-published-calendar-save').text()).toBe('agenda.calendarPublish.reset');
    expect(entry.find('.agenda-published-calendar-unpublish').text()).toBe('agenda.calendarPublish.delete');
    expect(entry.find('.agenda-published-calendar-stopped').exists()).toBe(false);
  });

  it('describes a stopped space calendar, says why, and offers to publish it again', () => {
    const [entry] = entries(mountDrawer([SPACE_STOPPED]));

    expect(entry.find('.agenda-published-calendar-kind').text()).toBe('fas fa-users');
    expect(entry.find('.agenda-published-calendar-owner').exists()).toBe(false);
    expect(entry.find('.agenda-published-calendar-date').text()).toMatch(/^agenda\.calendarPublish\.active\(Paul,/);
    expect(entry.find('.agenda-published-calendar-stopped').text()).toBe('agenda.calendarPublish.deadSpace(Paul)');
    expect(entry.find('.agenda-published-calendar-copy').exists()).toBe(false);
    expect(entry.find('.agenda-published-calendar-save').text()).toBe('agenda.calendarPublish.createNew');
  });

  it('names the space of a calendar whose title does not, and offers no copy when the link cannot be displayed', () => {
    const [entry] = entries(mountDrawer([SPACE_UNDISPLAYABLE]));

    expect(entry.find('.agenda-published-calendar-owner').text()).toBe('(Chemistry)');
    expect(entry.find('.agenda-published-calendar-copy').exists()).toBe(false);
    expect(entry.find('.agenda-published-calendar-save').text()).toBe('agenda.calendarPublish.reset');
  });

  it('unpublishes only once confirmed, then tells the row and the page', async () => {
    const wrapper = mountDrawer([PERSONAL]);

    await entries(wrapper)[0].find('.agenda-published-calendar-unpublish').trigger('click');
    expect(confirmStub.methods.open).toHaveBeenCalled();
    expect(service.deleteCalendarLink).not.toHaveBeenCalled();

    wrapper.findComponent(confirmStub).vm.$emit('ok');
    await flush();

    expect(service.deleteCalendarLink).toHaveBeenCalledWith(10);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarPublish.unpublished', 'success');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-calendar-links-changed');
    expect(wrapper.emitted('changed')).toHaveLength(1);
  });

  it('publishes a stopped calendar again only once confirmed', async () => {
    const wrapper = mountDrawer([SPACE_STOPPED]);

    await entries(wrapper)[0].find('.agenda-published-calendar-save').trigger('click');
    expect(service.saveCalendarLink).not.toHaveBeenCalled();
    wrapper.findComponent(confirmStub).vm.$emit('ok');
    await flush();

    expect(service.saveCalendarLink).toHaveBeenCalledWith(20);
    expect(wrapper.emitted('changed')).toHaveLength(1);
  });

  it('copies a working link and says so', async () => {
    const writeText = jest.fn().mockResolvedValue();
    Object.defineProperty(window.navigator, 'clipboard', {value: {writeText}, configurable: true});
    const wrapper = mountDrawer([PERSONAL]);

    await entries(wrapper)[0].find('.agenda-published-calendar-copy').trigger('click');
    await flush();

    expect(writeText).toHaveBeenCalledWith(PERSONAL.url);
    expect(wrapper.rootEmit).toHaveBeenCalledWith('alert-message', 'agenda.calendarPublish.copied', 'success');
  });

  it('closes itself once the list it was opened on becomes empty', async () => {
    const wrapper = mountDrawer([PERSONAL]);
    const close = jest.spyOn(wrapper.vm.$refs.publishedCalendarsDrawer, 'close');

    wrapper.vm.open();
    expect(wrapper.vm.opened).toBe(true);

    await wrapper.setProps({links: []});

    expect(close).toHaveBeenCalledTimes(1);
    expect(wrapper.vm.opened).toBe(false);
  });

  it('does not close a drawer nobody opened when the list becomes empty', async () => {
    const wrapper = mountDrawer([PERSONAL]);
    const close = jest.spyOn(wrapper.vm.$refs.publishedCalendarsDrawer, 'close');

    await wrapper.setProps({links: []});

    expect(close).not.toHaveBeenCalled();
    expect(wrapper.vm.opened).toBe(false);
  });
});
