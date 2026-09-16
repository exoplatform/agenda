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
import {mount} from '@vue/test-utils';

import AgendaEventFormAvailabilityVisibility from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormAvailabilityVisibility.vue';

/**
 * The Show as / Visibility row of the event form (EXO-90327 + EXO-90322).
 *
 * <p><b>What this harness can and cannot say.</b> Vuetify is not part of the
 * agenda build (the platform serves it), so v-tooltip and v-icon are ignored
 * elements here; the two controls are plain HTML selects, as the recurrence row
 * next to them is, so what they show and what they write to the event payload
 * is real. That the tooltip opens on hover is checked on a live page.</p>
 */
describe('The event form Show as / Visibility row', () => {

  Vue.config.ignoredElements = [/^v-/];

  /**
   * Mounts the row over an event payload.
   *
   * @param {Object} event the event the form is editing
   * @returns {Object} the wrapper
   */
  function mountRow(event) {
    return mount(AgendaEventFormAvailabilityVisibility, {
      propsData: {event},
      mocks: {$t: key => key},
    });
  }

  /**
   * The value a select currently shows.
   *
   * @param {Object} wrapper the mounted row
   * @param {String} ref which select
   * @returns {String} its value
   */
  function selected(wrapper, ref) {
    return wrapper.vm.$refs[ref].value;
  }

  it('defaults to Busy and the inherited visibility, and writes neither until the user picks', () => {
    const event = {summary: 'Weekly sync'};

    const wrapper = mountRow(event);

    expect(selected(wrapper, 'availability')).toBe('BUSY');
    expect(selected(wrapper, 'visibility')).toBe('DEFAULT');
    expect(event.availability).toBeUndefined();
    expect(event.visibility).toBeUndefined();
  });

  it.each([
    ['DEFAULT', 'BUSY'],
    ['BUSY', 'BUSY'],
    ['FREE', 'FREE'],
  ])('shows a stored %s availability as %s', (stored, shown) => {
    const wrapper = mountRow({availability: stored});

    expect(selected(wrapper, 'availability')).toBe(shown);
  });

  it.each(['DEFAULT', 'PUBLIC', 'PRIVATE'])('shows a stored %s visibility as it is', stored => {
    const wrapper = mountRow({visibility: stored});

    expect(selected(wrapper, 'visibility')).toBe(stored);
  });

  it('writes the picked availability to the event the form will send', async () => {
    const event = {summary: 'Conference week'};
    const wrapper = mountRow(event);

    await wrapper.find('.event-availability-select').setValue('FREE');

    expect(event.availability).toBe('FREE');
    expect(event.visibility).toBeUndefined();
  });

  it('writes the picked visibility to the event the form will send', async () => {
    const event = {summary: 'Salary review'};
    const wrapper = mountRow(event);

    await wrapper.find('.event-visibility-select').setValue('PRIVATE');

    expect(event.visibility).toBe('PRIVATE');
    expect(event.availability).toBeUndefined();
  });

  it('offers exactly the values the backend accepts', () => {
    const wrapper = mountRow({});

    expect(wrapper.findAll('.event-availability-select option').wrappers.map(option => option.element.value))
      .toEqual(['BUSY', 'FREE']);
    expect(wrapper.findAll('.event-visibility-select option').wrappers.map(option => option.element.value))
      .toEqual(['DEFAULT', 'PUBLIC', 'PRIVATE']);
  });

  /**
   * Reads a component file of the agenda webapp.
   *
   * @param {String} relative path under the vue-app folder
   * @returns {String} its content
   */
  function source(relative) {
    return fs.readFileSync(path.join(__dirname, '../../main/webapp/vue-app', relative), 'utf8');
  }

  it('is in the full form and the mobile form, and deliberately not in the quick-add drawer', () => {
    const tag = 'agenda-event-form-availability-visibility';

    expect(source('agenda-common/components/event/form/AgendaEventFormBasicInformation.vue')).toContain(tag);
    expect(source('agenda-common/components/event/form/mobile/AgendaEventMobileForm.vue')).toContain(tag);
    expect(source('agenda-common/components/event/form/AgendaEventQuickFormDrawer.vue')).not.toContain(tag);
    expect(source('agenda-common/initComponents.js')).toContain(`'${tag}'`);
  });

});
