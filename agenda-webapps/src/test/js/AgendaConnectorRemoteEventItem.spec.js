import {shallowMount} from '@vue/test-utils';

import * as agendaUtils from '../../main/webapp/vue-app/agenda-common/js/AgendaUtils.js';
import AgendaConnectorRemoteEventItem
  from '../../main/webapp/vue-app/agenda-common/components/remote-event/AgendaConnectorRemoteEventItem.vue';

/*
 * The calendar colour rail of EXO-89840. The rows of the conflicts panel all
 * rendered in one blue, so which calendar an event came from could only be
 * had by hovering. The rail reinforces what the hover already says — it never
 * carries it alone.
 */

const CURRENT_EVENT = {
  id: 100,
  summary: 'This event',
  startDate: new Date('2026-09-01T10:00:00'),
  endDate: new Date('2026-09-01T11:00:00'),
};

function mountRow(remoteEvent, options) {
  const opts = options || {};
  return shallowMount(AgendaConnectorRemoteEventItem, {
    propsData: {
      remoteEvent,
      event: 'event' in opts ? opts.event : CURRENT_EVENT,
      connector: opts.connector || null,
      hoverTitle: opts.hoverTitle || '',
      isEventsList: opts.isEventsList !== false,
    },
    mocks: {
      $agendaUtils: agendaUtils,
    },
    stubs: {
      'agenda-connector-avatar': true,
      'date-format': true,
    },
  });
}

/**
 * An event of eXo's own calendars: the calendar it lives in carries the
 * colour, as /v1/agenda/events attaches it.
 */
function exoRow(id, summary, calendarColor) {
  return {
    id,
    summary,
    start: '2026-09-01T09:00:00',
    end: '2026-09-01T09:30:00',
    startDate: new Date('2026-09-01T09:00:00'),
    endDate: new Date('2026-09-01T09:30:00'),
    calendar: calendarColor ? {id: 187, title: 'Work', color: calendarColor} : null,
  };
}

/**
 * An event read live from an account, which carries the colour on the event
 * itself and has no eXo calendar behind it.
 */
function liveRow(id, summary, color) {
  return {
    id,
    summary,
    start: '2026-09-01T09:00:00',
    end: '2026-09-01T09:30:00',
    startDate: new Date('2026-09-01T09:00:00'),
    endDate: new Date('2026-09-01T09:30:00'),
    color,
  };
}

const rail = wrapper => wrapper.element.style.borderLeft
  || wrapper.element.style.borderLeftColor
  || '';

describe('AgendaConnectorRemoteEventItem calendar colour rail', () => {

  /*
   * The pin the rail exists for: a row says which calendar it came from
   * without being hovered.
   */
  it('draws the calendar colour of an event of the viewer\'s own calendars', () => {
    const wrapper = mountRow(exoRow(501, 'Materialised standup', '#98CC81'));

    expect(rail(wrapper)).toContain('#98CC81');
    wrapper.destroy();
  });

  /*
   * A CalDAV account carries no eXo calendar, and its colour arrives on the
   * event — CaldavReadService gives every occurrence its collection's colour
   * through CalendarPalette, which always yields one.
   */
  it('draws the colour a live account supplied on the event itself', () => {
    const wrapper = mountRow(liveRow('uid-dentist', 'Dentist', '#319AB3'));

    expect(rail(wrapper)).toContain('#319AB3');
    wrapper.destroy();
  });

  it('lets the calendar\'s colour win over the one set on the event, as the grid does', () => {
    const row = exoRow(501, 'Materialised standup', '#98CC81');
    row.color = '#FF0000';
    const wrapper = mountRow(row);

    expect(rail(wrapper)).toContain('#98CC81');
    expect(rail(wrapper)).not.toContain('#FF0000');
    wrapper.destroy();
  });

  /*
   * A row with nothing to say says nothing: the rail stays transparent rather
   * than becoming a neutral stub, which would read as "this calendar is
   * grey" — the same "a state that looks like an answer" trap this panel has
   * now been fixed for twice. Transparent rather than absent so the row's
   * text still lines up with its neighbours'.
   */
  it('shows no colour for a row that has none, and no stub either', () => {
    const wrapper = mountRow(exoRow(501, 'Materialised standup', null));

    expect(rail(wrapper)).toContain('transparent');
    expect(rail(wrapper)).toContain('4px');
    wrapper.destroy();
  });

  /*
   * Google, Office 365 and Exchange each write '#FFFFFF' on every event they
   * return, their providers giving them no calendar colour. A white rail is
   * invisible on a light ground and wrong on a dark one, so it is no colour.
   */
  it('treats the placeholder white those connectors write as no colour', () => {
    const wrapper = mountRow(liveRow('AAMkAD', 'Sprint review', '#FFFFFF'));

    expect(rail(wrapper)).toContain('transparent');
    wrapper.destroy();
  });

  /*
   * The current event is a solid block, and that highlight is what anchors
   * the list. A rail on it would compete with the one thing the list has to
   * say about where this event falls.
   */
  it('leaves the current event\'s highlight alone', () => {
    const row = exoRow(100, 'This event', '#98CC81');
    const wrapper = mountRow(row);

    expect(wrapper.classes()).toContain('primary');
    expect(rail(wrapper)).not.toContain('#98CC81');
    wrapper.destroy();
  });

  /*
   * The calendar grid already paints this colour on the .v-event around this
   * component. A rail inside that border is not more information.
   */
  it('draws no rail outside the list surfaces', () => {
    const wrapper = mountRow(exoRow(501, 'Materialised standup', '#98CC81'), {isEventsList: false});

    expect(rail(wrapper)).toBe('');
    wrapper.destroy();
  });

  /*
   * The rail reinforces; it never carries alone. Whatever the colour, the row
   * still says in words which calendar it came from.
   */
  it('keeps the hover text as the carrier the rail only reinforces', () => {
    const wrapper = mountRow(exoRow(501, 'Materialised standup', null), {
      hoverTitle: 'Materialised standup — in Work',
    });

    expect(wrapper.find('p').attributes('title')).toBe('Materialised standup — in Work');
    wrapper.destroy();
  });
});
