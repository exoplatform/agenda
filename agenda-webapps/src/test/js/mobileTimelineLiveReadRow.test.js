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
import {paintableColor} from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';

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
        // the real rule, not a stub: which colours count as paintable is
        // exactly what the white-row pins below are about
        $agendaUtils: {areDatesOnSameDay: () => true, paintableColor},
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

  /**
   * The stored row is pinned against what it rendered before EXO-90393, not
   * against itself: an earlier version of this test mounted the same code
   * twice and compared the two, which holds for any code at all. These are the
   * literal classes and inline style the base revision emitted.
   */
  it('leaves a stored row painted and written exactly as it was', () => {
    const row = mountWith([STORED_EVENT, LIVE_READ_EVENT]).findAll('.event-timeline-detail').at(0);

    expect(row.attributes('style'))
      .toBe('background: rgb(49, 154, 179); border-left: 5px solid #319ab3;');
    expect(row.classes()).toContain('white--text');
    expect(row.classes()).not.toContain('remote-event');
    expect(row.find('.event-timeline-detail-content strong').text()).toBe('Sprint review');
  });

  /**
   * A live-read row does not change the row beside it — the property the
   * replaced test was actually able to check, kept for what it is worth.
   */
  it('does not alter the row beside it', () => {
    const storedOnly = mountWith([STORED_EVENT]);
    const mixed = mountWith([STORED_EVENT, LIVE_READ_EVENT]);

    expect(mixed.findAll('.event-timeline-detail').at(0).html())
      .toBe(storedOnly.findAll('.event-timeline-detail').at(0).html());
  });

  /**
   * The regression the removal of `primary--text` opened, and the reason the
   * row's colour now goes through the shared paintable rule.
   *
   * <p>Office 365 and Exchange write `#FFFFFF` on every event they return
   * (AgendaUtils' PLACEHOLDER_EVENT_COLOR), their providers giving them no
   * calendar colour to pass on. Painted, that put the row's own white text on
   * a white ground — the whole row invisible, on the mobile list and on the
   * desktop home-page widget that mounts this same component. Before
   * EXO-90393 the forced `primary--text` hid the problem; nothing hides it
   * now, so the colour itself has to be refused.</p>
   */
  it('refuses the placeholder white those connectors declare, rather than painting it', () => {
    ['#FFFFFF', '#ffffff', 'white'].forEach(declared => {
      const row = mountWith([Object.assign({}, LIVE_READ_EVENT, {color: declared})])
        .findAll('.event-timeline-detail').at(0);

      expect(row.attributes('style'))
        .toBe('background: rgb(33, 150, 243); border-left: 5px solid #2196F3;');
    });
  });

  /**
   * The same invisibility reached by the other door: a connector that declares
   * no colour at all left the row with no background, i.e. the card's own
   * white, under the very same white text. The left edge always had the
   * default; the body now has it too.
   */
  it('falls back to the default colour when nothing declares one, rather than to the card', () => {
    const bare = Object.assign({}, LIVE_READ_EVENT);
    delete bare.color;
    const row = mountWith([bare]).findAll('.event-timeline-detail').at(0);

    expect(row.attributes('style'))
      .toBe('background: rgb(33, 150, 243); border-left: 5px solid #2196F3;');
  });

  /**
   * A colour a connector genuinely publishes is still the row's colour — the
   * guard refuses the placeholder, not every colour it is given.
   */
  it('still paints a colour a connector genuinely publishes', () => {
    const row = mountWith([LIVE_READ_EVENT]).findAll('.event-timeline-detail').at(0);

    expect(row.attributes('style'))
      .toBe('background: rgb(188, 75, 75); border-left: 5px solid #bc4b4b;');
  });

  it('draws the account marker white and without a tile of its own, since the row is coloured', () => {
    const marker = mountWith([LIVE_READ_EVENT]).find('agenda-connector-avatar');

    expect(marker.exists()).toBe(true);
    expect(marker.attributes('icon-class')).toBe('white--text');
    expect(marker.classes()).not.toContain('white');
  });


});
