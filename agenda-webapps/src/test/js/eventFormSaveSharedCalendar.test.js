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

import AgendaEventForm from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventForm.vue';

/**
 * Saving from the event form when the event lives in a calendar a colleague
 * shared for editing (EXO-90378).
 *
 * <p>
 * This is the entry point the PO's save actually goes through, and the one the
 * service-level tests could not see: the form resolves the destination's
 * calendar by asking for <b>the owner's</b> calendars, a listing an editor is
 * served only in part — they may read the one calendar shared with them and
 * none of that owner's others. When that lookup answers nothing, saveEvent
 * used to dereference it and die in the browser with a TypeError: no request
 * was issued, nothing was logged server-side, and the form simply did nothing.
 */
describe('Saving an event of a calendar shared for editing', () => {

  Vue.config.ignoredElements = [/^v-/, 'agenda-notification-alerts'];

  /** Eric's calendar, shared with the signed-in user for editing. */
  const ERICS_CALENDAR = {
    id: 5,
    owner: {id: '8', providerId: 'organization', remoteId: 'eric'},
  };

  /** The event as the server hands it to the form: its calendar is Eric's. */
  function sharedEvent() {
    return {
      id: 42,
      summary: 'Dentist',
      location: 'Downtown',
      startDate: new Date('2026-10-01T09:00:00Z'),
      endDate: new Date('2026-10-01T10:00:00Z'),
      calendar: {id: ERICS_CALENDAR.id, owner: Object.assign({}, ERICS_CALENDAR.owner)},
      dateOptions: [],
      attendees: [],
    };
  }

  /**
   * Mounts the form over an event, without resolving any calendar — which is
   * what an editor gets, since the owner's listing does not carry the rest of
   * that owner's calendars to them.
   *
   * @param {Object} event the event being edited
   * @returns {Object} the mounted wrapper
   */
  function mountForm(event) {
    const wrapper = shallowMount(AgendaEventForm, {
      propsData: {event},
      mocks: {
        $t: key => key,
        $agendaUtils: {
          toRFC3339: date => new Date(date).toISOString(),
          initEventForm: () => {},
          isEventDetailsComplete: () => true,
          USER_TIMEZONE_ID: 'UTC',
        },
        $utils: {htmlToText: text => text},
        $identityService: {getIdentityByProviderIdAndRemoteId: jest.fn().mockResolvedValue(null)},
        $calendarService: {getCalendars: jest.fn().mockResolvedValue({calendars: []})},
      },
    });
    wrapper.rootEmit = jest.spyOn(wrapper.vm.$root, '$emit');
    return wrapper;
  }

  beforeAll(() => {
    global.eXo = {env: {portal: {language: 'en', userName: 'alice', userIdentityId: '5'}}};
  });

  it('saves the event when no calendar could be resolved, instead of dying on a null', () => {
    const event = sharedEvent();
    const wrapper = mountForm(event);
    // What an editor really has: the owner's calendar listing gave the form
    // nothing it could match, so there is no resolved calendar
    wrapper.vm.selectedCalendar = null;

    expect(() => wrapper.vm.saveEvent()).not.toThrow();

    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-event-save', event);
    // The owner block the server sent with the event is what reaches the
    // payload: the event is still Eric's calendar's
    expect(event.calendar.owner.id).toBe('8');
    expect(event.calendar.id).toBe(5);
    expect(event.start).toBe('2026-10-01T09:00:00.000Z');
    expect(event.end).toBe('2026-10-01T10:00:00.000Z');
  });

  it('still carries the resolved calendar owner onto the payload when there is one', () => {
    const event = sharedEvent();
    const wrapper = mountForm(event);
    // The owner's own form, where the lookup does answer
    wrapper.vm.selectedCalendar = {id: 5, owner: {id: '8', providerId: 'organization', remoteId: 'eric'}};

    wrapper.vm.saveEvent();

    expect(event.calendar.owner.id).toBe('8');
    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-event-save', event);
  });

  it('changes the owner when the user picked another destination', () => {
    const event = sharedEvent();
    const wrapper = mountForm(event);
    wrapper.vm.selectedCalendar = {id: 9, owner: {id: '5', providerId: 'organization', remoteId: 'alice'}};

    wrapper.vm.saveEvent();

    expect(event.calendar.owner.id).toBe('5');
  });

  it('does not die when the resolved calendar carries no owner either', () => {
    const event = sharedEvent();
    const wrapper = mountForm(event);
    wrapper.vm.selectedCalendar = {id: 5};

    expect(() => wrapper.vm.saveEvent()).not.toThrow();

    expect(wrapper.rootEmit).toHaveBeenCalledWith('agenda-event-save', event);
    expect(event.calendar.owner.id).toBe('8');
  });

});
