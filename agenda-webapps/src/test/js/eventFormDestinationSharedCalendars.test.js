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

import AgendaEventFormDestination from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormDestination.vue';

/**
 * The destination of a new event when a colleague shared a calendar for
 * editing (EXO-90378): the select gains a group of those calendars, picking
 * one writes the <b>owner's</b> identity into the payload — which is what the
 * server checks the stored calendar row against — and an event already filed
 * in such a calendar stays there, since an editor may not move it out.
 */
describe('Event form destination with calendars shared for editing', () => {

  Vue.config.ignoredElements = [/^v-/];

  /** The signed-in user's own calendars. */
  const MY_CALENDARS = [
    {id: 1, system: true, name: null, color: '#111111'},
    {id: 2, system: false, name: 'Side project', color: '#222222'},
  ];

  /** Bob shared his calendar for editing; Carol for viewing; Dave's is hidden. */
  const SHARED = [
    {calendarId: 41, name: 'Bob', color: '#ff0000', ownerId: 9, ownerUsername: 'bob', ownerDisplayName: 'Bob Builder', access: 'EDIT', hidden: false},
    {calendarId: 42, name: 'Carol', color: '#00ff00', ownerId: 10, ownerUsername: 'carol', ownerDisplayName: 'Carol', access: 'VIEW', hidden: false},
    {calendarId: 43, name: 'Dave', color: '#0000ff', ownerId: 11, ownerUsername: 'dave', ownerDisplayName: 'Dave', access: 'EDIT', hidden: true},
  ];

  let calendarService;

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
   * Mounts the destination over an event payload.
   *
   * @param {Object} event the event being created or edited
   * @returns {Promise<Object>} the mounted wrapper, once loaded
   */
  async function mountDestination(event) {
    const wrapper = shallowMount(AgendaEventFormDestination, {
      propsData: {event},
      mocks: {
        $t: (key, args) => (args && `${key}(${Object.values(args).join('|')})`) || key,
        $calendarService: calendarService,
        $calendarShareService: shareService,
      },
    });
    await flush();
    return wrapper;
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {language: 'en', userName: 'alice', userIdentityId: '5'}}};
  });

  beforeEach(() => {
    calendarService = {getCalendars: jest.fn().mockResolvedValue({calendars: MY_CALENDARS.slice()})};
    shareService = {getSharedWithMe: jest.fn().mockResolvedValue(SHARED.slice())};
  });

  it('offers the calendars shared for editing under a group of their own, and leaves out the viewing and hidden ones', async () => {
    const wrapper = await mountDestination({calendar: {}});

    const items = wrapper.vm.destinationItems;
    const header = items.findIndex(item => item.header);
    expect(header).toBeGreaterThan(-1);
    expect(items[header].header).toBe('agenda.destination.sharedEditable');
    expect(items[header + 1]).toMatchObject({text: 'Bob', value: 'calendar-41'});
    expect(items.map(item => item.value)).not.toContain('calendar-42');
    expect(items.map(item => item.value)).not.toContain('calendar-43');
    // The user's own calendars come first and the space entry last, as before
    expect(items[0].value).toBe('calendar-1');
    expect(items[items.length - 1].value).toBe('spaces');
  });

  it('writes the calendar owner into the payload when the destination is a calendar shared for editing', async () => {
    const event = {calendar: {}};
    const wrapper = await mountDestination(event);

    wrapper.vm.selectedValue = 'calendar-41';
    await flush();

    expect(event.calendar.id).toBe(41);
    expect(event.calendar.owner).toEqual({id: '9', providerId: 'organization', remoteId: 'bob'});
  });

  it('writes the signed-in user into the payload for one of their own calendars', async () => {
    const event = {calendar: {}};
    const wrapper = await mountDestination(event);

    wrapper.vm.selectedValue = 'calendar-2';
    await flush();

    expect(event.calendar.id).toBe(2);
    expect(event.calendar.owner).toEqual({id: '5', providerId: 'organization', remoteId: 'alice'});
  });

  it('proposes the calendar owner as the only attendee of a new event filed in their calendar', async () => {
    const event = {calendar: {}, attendees: [{identity: {id: '5', providerId: 'organization', remoteId: 'alice'}}]};
    const wrapper = await mountDestination(event);

    wrapper.vm.selectedValue = 'calendar-41';
    await flush();

    expect(event.attendees).toHaveLength(1);
    expect(event.attendees[0].identity).toMatchObject({id: '9', remoteId: 'bob'});

    // Going back to one of the user's own calendars takes the proposal back;
    // the attendees component puts the signed-in user in again
    wrapper.vm.selectedValue = 'calendar-2';
    await flush();

    expect(event.attendees).toEqual([]);
  });

  it('never replaces attendees the user themselves picked', async () => {
    const chosen = [
      {identity: {id: '5', providerId: 'organization', remoteId: 'alice'}},
      {identity: {id: '77', providerId: 'organization', remoteId: 'zoe'}},
    ];
    const event = {calendar: {}, attendees: chosen};
    const wrapper = await mountDestination(event);

    wrapper.vm.selectedValue = 'calendar-41';
    await flush();

    expect(event.attendees).toBe(chosen);
  });

  it('proposes nothing on an event being edited', async () => {
    const event = {id: 77, calendar: {}, attendees: []};
    const wrapper = await mountDestination(event);

    wrapper.vm.selectedValue = 'calendar-41';
    await flush();

    expect(event.attendees).toEqual([]);
  });

  it('keeps an event of a calendar shared for editing where it is filed, rather than reading it as a space event', async () => {
    const event = {
      id: 77,
      calendar: {id: 41, owner: {id: '9', providerId: 'organization', remoteId: 'bob'}},
    };

    const wrapper = await mountDestination(event);

    expect(wrapper.vm.selectedValue).toBe('calendar-41');
  });

  it('draws only the user\'s own calendars when nothing is shared with them for editing', async () => {
    shareService.getSharedWithMe.mockResolvedValue([]);

    const wrapper = await mountDestination({calendar: {}});

    expect(wrapper.vm.destinationItems.some(item => item.header)).toBe(false);
    expect(wrapper.vm.editableShares).toEqual([]);
  });

  it('leaves the group empty when the shares cannot be read, rather than failing the form', async () => {
    shareService.getSharedWithMe.mockRejectedValue(new Error('down'));

    const wrapper = await mountDestination({calendar: {}});

    expect(wrapper.vm.editableShares).toEqual([]);
    expect(wrapper.vm.destinationItems[0].value).toBe('calendar-1');
    expect(wrapper.vm.initialized).toBe(true);
  });

});
