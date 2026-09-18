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
import {mount} from '@vue/test-utils';

import AgendaTimeline from '../../main/webapp/vue-app/agenda-common/components/calendar-body/mobile/AgendaTimeline.vue';

/**
 * A row read live from a connected account is written like every other row of
 * the mobile list (EXO-90393).
 *
 * <p>Every row of this list is painted with its calendar's colour and written
 * in white over it. A live-read row used to have the theme primary forced onto
 * its title and its time line on top of that white — a colour picked when such
 * a row was drawn on the card's own background — which left the one row of the
 * list nobody could read.</p>
 *
 * <p><b>Harness.</b> Vuetify and the agenda's own globally-registered
 * components are not part of this build, so their tags are left as plain
 * elements; the pins are on the classes this component puts on a row, which is
 * where the change is.</p>
 */
describe('A live-read row of the mobile timeline', () => {

  Vue.config.ignoredElements = [/^v-/, 'date-format', 'agenda-connector-avatar', 'agenda-empty-timeline'];

  const PERIOD_START = new Date(2026, 8, 1);

  const STORED_EVENT = {
    id: 7,
    summary: 'Sprint review',
    startDate: new Date(2026, 8, 21, 9, 0, 0),
    endDate: new Date(2026, 8, 21, 10, 0, 0),
    allDay: false,
    calendar: {color: '#319ab3'},
  };

  const LIVE_READ_EVENT = {
    summary: 'Dentist',
    type: 'remoteEvent',
    startDate: new Date(2026, 8, 21, 11, 0, 0),
    endDate: new Date(2026, 8, 21, 12, 0, 0),
    allDay: false,
    color: '#bc4b4b',
    connector: {name: 'agenda.caldavCalendar', user: 'camille@acme.org'},
  };

  /**
   * Mounts the list over the given events.
   *
   * @param {Array} events the events the list draws
   * @returns {Object} the wrapper
   */
  function mountWith(events) {
    return mount(AgendaTimeline, {
      propsData: {
        events: events,
        periodStartDate: PERIOD_START,
        limit: 10,
      },
      mocks: {
        $t: key => key,
        $agendaUtils: {areDatesOnSameDay: () => true},
      },
    });
  }

  it('forces no text colour of its own, on the title or on the time line', () => {
    const rows = mountWith([STORED_EVENT, LIVE_READ_EVENT]).findAll('.event-timeline-detail');

    expect(rows.length).toBe(2);
    rows.wrappers.forEach(row => {
      expect(row.classes()).toContain('white--text');
      expect(row.find('.event-timeline-detail-content strong').classes()).not.toContain('primary--text');
      expect(row.find('.event-timeline-detail-content .flex-row').classes()).not.toContain('primary--text');
    });
  });

  it('leaves the rows of the stored events exactly as they were', () => {
    const storedOnly = mountWith([STORED_EVENT]);
    const mixed = mountWith([STORED_EVENT, LIVE_READ_EVENT]);

    expect(mixed.findAll('.event-timeline-detail').at(0).html())
      .toBe(storedOnly.findAll('.event-timeline-detail').at(0).html());
  });

  it('draws the account marker white and without a tile of its own, since the row is coloured', () => {
    const marker = mountWith([LIVE_READ_EVENT]).find('agenda-connector-avatar');

    expect(marker.exists()).toBe(true);
    expect(marker.attributes('icon-class')).toBe('white--text');
    expect(marker.classes()).not.toContain('white');
  });


});
