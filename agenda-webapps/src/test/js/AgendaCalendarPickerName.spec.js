import {mount, shallowMount} from '@vue/test-utils';

import AgendaEventFormDestination
  from '../../main/webapp/vue-app/agenda-common/components/event/form/AgendaEventFormDestination.vue';
import AgendaPersonalCalendarList
  from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaPersonalCalendarList.vue';

/*
 * EXO-89845 — a calendar picker broke its own geometry on a long calendar
 * name, and cut the name where the account begins.
 *
 * The geometry half of the fix is skin, and a jsdom test cannot see it: no
 * layout runs here, so nothing in this file can say whether the box stayed
 * 40px next to its siblings or whether the arrow kept its place. That was
 * measured in a browser against the live rig and belongs in the report, not
 * in an assertion pretending to check it.
 *
 * What IS pinnable is the markup the skin acts on and the identity the clip
 * takes away:
 *
 *   - the row is a bounded box per part (dot, name, arrow) rather than one
 *     text run, which is the shape the skin needs to hold the box's height;
 *   - the whole name reaches a title, on the closed row and on the menu row
 *     alike;
 *   - two calendars whose names differ only past the cut stay distinguishable
 *     through it — the real case, since a user with two connected accounts
 *     gets two collections that diverge only at the address;
 *   - a short name gains nothing: same text, same title, no decoration.
 */

const CONNECTED_A = 'Stalwart Calendar (alice@stalwart.local)';
const CONNECTED_B = 'Stalwart Calendar (bob@stalwart.local)';
const SHORT = 'Work';

/*
 * The picker's last entry, which leads to the space flow rather than naming a
 * calendar. It has no colour and so no dot, and it gets the same row markup
 * as every calendar above it — the $t mock returns the key.
 */
const SPACES_ENTRY = 'agenda.destination.spaces';

/*
 * The real-world pair: one user, two accounts on the same server, so two
 * collections carrying the same calendar name and differing only in the
 * address the server writes at the end of it.
 */
const TWIN_A = 'Stalwart Calendar for the Product and Platform Engineering Team (alice@stalwart.local)';
const TWIN_B = 'Stalwart Calendar for the Product and Platform Engineering Team (bob@stalwart.local)';

/*
 * How much of a name the 500px destination select draws before the ellipsis,
 * counted off the browser at the rig's default type size — the fixed
 * geometry makes it a count of characters for this text. Both names above are
 * identical well past it, so what the row shows is the same for either and
 * the hover is the only thing left that separates them.
 */
const CLIPPED_AT = 57;

/**
 * A stand-in for v-select that renders the two scoped slots the component
 * supplies, so the assertions are made on the shipped slot markup rather than
 * on a copy of it. Vuetify is not a dependency of this package, so the real
 * component cannot be mounted; what matters here is only that each slot is
 * called once per item with the item it is given.
 */
const VSelectStub = {
  name: 'VSelectStub',
  props: {
    items: {
      type: Array,
      default: () => [],
    },
    value: {
      type: [String, Number],
      default: null,
    },
  },
  /**
   * Renders every non-divider item through both slots, in two labelled
   * containers so a test can address the closed row and the menu row apart.
   *
   * @param {Function} h the Vue create-element function
   * @returns {Object} the rendered node
   */
  render(h) {
    const rows = (this.items || []).filter(item => !item.divider);
    return h('div', {class: 'v-select-stub'}, [
      h('div', {class: 'selection-rows'}, rows.map(item => this.$scopedSlots.selection({item}))),
      h('div', {class: 'menu-rows'}, rows.map(item => this.$scopedSlots.item({item}))),
    ]);
  },
};

/**
 * A personal calendar as /v1/agenda/calendars returns it.
 *
 * @param {Number} id the calendar id
 * @param {String} name the display name, which for a connected calendar is
 *        the one the remote server published
 * @param {String} description the optional description
 * @returns {Object} the calendar
 */
function calendar(id, name, description) {
  return {
    id,
    name,
    description: description || null,
    color: '#e5282c',
    system: false,
  };
}

/**
 * Mounts the destination picker over a set of personal calendars and waits
 * for its initial retrieval to settle.
 *
 * @param {Array} calendars the calendars the service answers with
 * @returns {Promise} resolves with the mounted wrapper
 */
async function mountPicker(calendars) {
  eXo.env.portal.userIdentityId = 13;
  const wrapper = mount(AgendaEventFormDestination, {
    propsData: {
      event: {},
      currentSpace: null,
      inline: true,
    },
    mocks: {
      $t: key => key,
      $calendarService: {
        getCalendars: () => Promise.resolve({calendars}),
      },
    },
    stubs: {
      'v-select': VSelectStub,
      'v-icon': true,
      'agenda-event-form-calendar-owner': true,
    },
  });
  await new Promise(resolve => setTimeout(resolve));
  await wrapper.vm.$nextTick();
  return wrapper;
}

/**
 * The name-bearing elements of one of the stub's two containers.
 *
 * @param {Object} wrapper the mounted picker
 * @param {String} container 'selection-rows' or 'menu-rows'
 * @returns {Array} the wrappers of the elements carrying a calendar name
 */
function nameCells(wrapper, container) {
  return wrapper.findAll(`.${container} .agenda-destination-option span`).wrappers;
}

/**
 * Mounts the personal calendar list over a set of calendars.
 *
 * @param {Array} calendars the calendars the service answers with
 * @returns {Promise} resolves with the mounted wrapper
 */
async function mountCalendarList(calendars) {
  eXo.env.portal.userIdentityId = 13;
  global.extensionRegistry = {loadExtensions: () => []};
  const wrapper = shallowMount(AgendaPersonalCalendarList, {
    mocks: {
      $t: key => key,
      $calendarService: {
        getCalendars: () => Promise.resolve({calendars}),
      },
    },
  });
  await new Promise(resolve => setTimeout(resolve));
  await wrapper.vm.$nextTick();
  return wrapper;
}

/**
 * The hover text of every calendar row of the list. The Vuetify components
 * are not registered in this harness, so they render as their own unknown
 * elements and the bound attribute is readable straight off them.
 *
 * @param {Object} wrapper the mounted list
 * @returns {Array} one title per row, in the order the list draws them
 */
function rowTitles(wrapper) {
  return wrapper.findAll('v-list-item-content').wrappers.map(row => row.attributes('title'));
}

describe('EXO-89845 the destination picker keeps the calendar name identifiable', () => {

  /*
   * The shape the skin acts on. The row used to be one text run with the
   * colour dot inline inside it and no box around the name, so there was
   * nothing for a width to be applied to and the field's height took the
   * overflow instead. Each part now has its own element: the dot, the name,
   * and — supplied by the select itself — the arrow.
   */
  it('gives the colour dot and the name each their own element in the row', async () => {
    const wrapper = await mountPicker([calendar(1, CONNECTED_A)]);
    const rows = wrapper.findAll('.selection-rows .agenda-destination-option').wrappers;

    // the calendar, then the entry that leads to the space flow
    expect(rows).toHaveLength(2);
    expect(rows[0].find('v-icon-stub').exists()).toBe(true);
    // the space entry has no calendar colour, so it shows no dot at all
    expect(rows[1].find('v-icon-stub').exists()).toBe(false);

    const names = nameCells(wrapper, 'selection-rows');
    expect(names).toHaveLength(2);
    // the name is its own element, and it is the one allowed to give way
    expect(names[0].classes()).toContain('text-truncate');
    expect(names[0].text()).toBe(CONNECTED_A);
  });

  /*
   * The full name reaches the title — on the row the closed select shows and
   * on the row the menu offers, which are the same row and must not drift
   * apart.
   */
  it('carries the whole calendar name on the row that shows it clipped', async () => {
    const wrapper = await mountPicker([calendar(1, CONNECTED_A)]);

    expect(nameCells(wrapper, 'selection-rows').map(cell => cell.attributes('title')))
      .toEqual([CONNECTED_A, SPACES_ENTRY]);
    expect(nameCells(wrapper, 'menu-rows').map(cell => cell.attributes('title')))
      .toEqual([CONNECTED_A, SPACES_ENTRY]);
  });

  /*
   * The case the title exists for: two connected accounts give two
   * collections whose names are identical up to the address and differ only
   * after it — past where the control clips.
   */
  it('tells apart two calendars whose names differ only past the cut', async () => {
    // the premise the hover has to rescue: the visible run is the same for both
    expect(TWIN_A.slice(0, CLIPPED_AT)).toBe(TWIN_B.slice(0, CLIPPED_AT));
    expect(TWIN_A).not.toBe(TWIN_B);

    const wrapper = await mountPicker([calendar(1, TWIN_A), calendar(2, TWIN_B)]);
    const titles = nameCells(wrapper, 'selection-rows').map(cell => cell.attributes('title'));

    expect(titles).toEqual([TWIN_A, TWIN_B, SPACES_ENTRY]);
    expect(new Set(titles).size).toBe(3);
  });

  /*
   * A short name gains nothing at all: the same text, a title that is exactly
   * the name, and no marker of any kind added beside it.
   */
  it('leaves a short name exactly as it was', async () => {
    const wrapper = await mountPicker([calendar(1, SHORT)]);
    const cells = nameCells(wrapper, 'selection-rows');

    // asserted before the rows are read, so a row that lost its name element
    // fails here rather than crashing on an empty list
    expect(cells).toHaveLength(2);
    expect(cells[0].text()).toBe(SHORT);
    expect(cells[0].attributes('title')).toBe(SHORT);
  });
});

describe('EXO-89845 the personal calendar list hover names the calendar', () => {

  /*
   * The row's hover used to be `description || name`, so a calendar with a
   * description answered a question nobody asked and dropped the only thing
   * that told two truncated rows apart.
   */
  it('keeps the name in the hover, first, when the calendar has a description', async () => {
    const wrapper = await mountCalendarList([calendar(1, CONNECTED_A, 'Synchronised every 15 minutes')]);
    const title = rowTitles(wrapper)[0];

    expect(title.indexOf(CONNECTED_A)).toBe(0);
    expect(title).toContain('Synchronised every 15 minutes');
  });

  it('tells apart two described calendars whose names differ only past the cut', async () => {
    const wrapper = await mountCalendarList([
      calendar(1, CONNECTED_A, 'A connected calendar'),
      calendar(2, CONNECTED_B, 'A connected calendar'),
    ]);
    const titles = rowTitles(wrapper);

    expect(titles).toHaveLength(2);
    expect(new Set(titles).size).toBe(2);
    expect(titles[0]).toContain(CONNECTED_A);
    expect(titles[1]).toContain(CONNECTED_B);
  });

  it('leaves the hover of a calendar without a description as just its name', async () => {
    const wrapper = await mountCalendarList([calendar(1, SHORT)]);

    expect(rowTitles(wrapper)).toEqual([SHORT]);
  });
});
