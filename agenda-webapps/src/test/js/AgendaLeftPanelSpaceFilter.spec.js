import {mount} from '@vue/test-utils';

import AgendaLeftPanel from '../../main/webapp/vue-app/agenda-common/components/left-panel/AgendaLeftPanel.vue';
import AgendaFilterCalendarList from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaFilterCalendarList.vue';
import AgendaFilterCalendarSearch from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaFilterCalendarSearch.vue';
import AgendaFilterCalendarItem from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaFilterCalendarItem.vue';

/*
 * EXO-89919 — filtering the Spaces section of the left panel.
 *
 * The list under that header could already search, server-side and across
 * every space the user belongs to: the filter drawer has always shown the
 * field. The left panel mounts the same list compact, which is the rendering
 * that hides the field, so a user in dozens of spaces paged through the
 * section to find one by name. What was missing was the affordance, not the
 * search — and the pins below hold the two together: the header offers the
 * platform's filter idiom, and typing in it reaches the same server query the
 * drawer's field issues. The assertions are on what the list ASKS the space
 * service for, in the style of the other filter specs.
 *
 * The second half is about the selection. An item materializes 'all' from the
 * rows it sees, so unticking one space out of 'all' behind a keyword used to
 * hand back the matches minus that space — every space the keyword kept out
 * silently dropped from the agenda. The drawer had that defect on mobile; the
 * panel would have brought it to the desktop. The last pins are the fix.
 */

const SPACE_A = 21;
const SPACE_B = 22;
const SPACE_C = 23;
const SPACE_D = 24;
const SPACE_E = 25;

/**
 * Builds a space as the REST returns it when expanded on its identity.
 *
 * @param {Number} identityId space identity id
 * @returns {Object} space
 */
function space(identityId) {
  return {id: identityId, identity: {id: `${identityId}`}};
}

/**
 * Builds a space calendar as the REST returns it.
 *
 * @param {Number} ownerId identity id of the owning space
 * @returns {Object} calendar
 */
function calendar(ownerId) {
  return {id: ownerId * 10, owner: {id: `${ownerId}`, space: {displayName: `Space ${ownerId}`}}, color: '#000'};
}

/**
 * A space service that records what it is asked and answers a page per
 * keyword: the whole membership without one, the matches with one.
 *
 * @param {Object} pages identity ids per keyword, the empty string being no
 *          keyword
 * @returns {Object} the service, carrying the recorded calls
 */
function spaceService(pages) {
  const calls = [];
  return {
    calls,
    getSpaces: (...args) => {
      calls.push(args);
      const ids = pages[args[0] || ''] || [];
      return Promise.resolve({spaces: ids.map(space), size: ids.length});
    },
  };
}

/**
 * Lets the watcher chain (search → panel → list prop → query → retrieval)
 * run to its end.
 *
 * @param {Object} wrapper the mounted wrapper
 * @returns {Promise} resolved after a few render ticks
 */
async function settle(wrapper) {
  for (let i = 0; i < 4; i++) {
    await wrapper.vm.$nextTick();
  }
}

/**
 * Mounts the left panel, expanded so that it loads its space calendars, with
 * the real filter list, search field and rows under it.
 *
 * @param {Object} service the space service stand-in
 * @returns {Object} the wrapper
 */
function mountPanel(service) {
  global.extensionRegistry = {loadExtensions: () => []};
  // mount, not shallowMount: the search field, the list and its rows are
  // exactly what the pins look at
  return mount(AgendaLeftPanel, {
    propsData: {selectedOwnerIds: [], expanded: true, connectors: [], settings: null, period: null},
    mocks: {
      $t: key => key,
      $vuetify: {rtl: false},
      $spaceService: service,
      $calendarService: {
        getCalendars: (offset, limit, returnSize, ownerIds) => Promise.resolve({calendars: ownerIds.map(calendar)}),
      },
    },
    stubs: {
      'v-date-picker': true,
      'agenda-connect-to-remote-button': true,
      'agenda-personal-calendar-list': true,
      'agenda-left-panel-remote-calendars': true,
      'agenda-filter-calendar-list': AgendaFilterCalendarList,
      'agenda-filter-calendar-search': AgendaFilterCalendarSearch,
      'agenda-filter-calendar-item': AgendaFilterCalendarItem,
    },
  });
}

/**
 * The Spaces section header, the last section title of the panel.
 *
 * @param {Object} wrapper the mounted panel
 * @returns {Object} the header wrapper
 */
function spacesHeader(wrapper) {
  const titles = wrapper.findAll('.agenda-left-panel-title');
  return titles.at(titles.length - 1);
}

describe('filtering the spaces from the left panel header', () => {
  afterEach(() => {
    jest.useRealTimers();
  });

  it('offers the filter icon on the Spaces header, and no field until it is asked for', async () => {
    const wrapper = mountPanel(spaceService({'': [SPACE_A, SPACE_B, SPACE_C]}));
    await settle(wrapper);

    const header = spacesHeader(wrapper);
    expect(header.text()).toContain('agenda.leftPanel.spaces');
    expect(header.find('[aria-label="agenda.leftPanel.filterSpaces"]').exists()).toBe(true);
    expect(wrapper.findComponent(AgendaFilterCalendarSearch).exists()).toBe(false);
    expect(wrapper.find('[aria-label="agenda.button.close"]').exists()).toBe(false);
  });

  /*
   * The drawer's field is always visible and belongs to the list; the panel's
   * belongs to the header. A compact list draws none of its own, so the two
   * renderings never end up with two fields.
   */
  it('keeps the field inside the list for the drawer, and out of it for the panel', () => {
    const mountList = compact => mount(AgendaFilterCalendarList, {
      propsData: {value: [], compact},
      mocks: {$t: key => key},
      stubs: {'agenda-filter-calendar-search': AgendaFilterCalendarSearch},
    });
    expect(mountList(false).findComponent(AgendaFilterCalendarSearch).exists()).toBe(true);
    expect(mountList(true).findComponent(AgendaFilterCalendarSearch).exists()).toBe(false);
  });

  it('turns the header into the field with a back arrow on the left', async () => {
    const wrapper = mountPanel(spaceService({'': [SPACE_A, SPACE_B, SPACE_C]}));
    await settle(wrapper);

    await spacesHeader(wrapper).find('[aria-label="agenda.leftPanel.filterSpaces"]').trigger('click');
    await settle(wrapper);

    const header = spacesHeader(wrapper);
    expect(header.text()).not.toContain('agenda.leftPanel.spaces');
    expect(header.find('[aria-label="agenda.leftPanel.filterSpaces"]').exists()).toBe(false);
    expect(header.find('[aria-label="agenda.button.close"]').exists()).toBe(true);
    expect(wrapper.findAllComponents(AgendaFilterCalendarSearch).length).toBe(1);
    // the very field the drawer shows, rendered for the one-line header
    expect(wrapper.findComponent(AgendaFilterCalendarSearch).props('compact')).toBe(true);
  });

  /*
   * THE load-bearing pin: the keyword typed in the header reaches the space
   * service through the list's own query — the same server-side search the
   * drawer issues, over every space the user belongs to, not a client-side
   * sieve over the loaded page.
   */
  it('sends the typed keyword to the server as the list query', async () => {
    jest.useFakeTimers();
    const service = spaceService({'': [SPACE_A, SPACE_B, SPACE_C], proj: [SPACE_B]});
    const wrapper = mountPanel(service);
    await settle(wrapper);
    expect(service.calls.map(call => call[0])).toEqual([null]);

    await spacesHeader(wrapper).find('[aria-label="agenda.leftPanel.filterSpaces"]').trigger('click');
    await settle(wrapper);
    // what v-model on the inner input writes; the field debounces it, once
    // its watcher has run
    wrapper.findComponent(AgendaFilterCalendarSearch).vm.query = 'proj';
    await wrapper.vm.$nextTick();
    jest.advanceTimersByTime(700);
    await settle(wrapper);

    expect(service.calls.length).toBe(2);
    expect(service.calls[1]).toEqual(['proj', 0, 20, 'lastVisited', 'identity']);
    expect(wrapper.findComponent(AgendaFilterCalendarList).vm.query).toBe('proj');
  });

  it('collapsing puts the title back and clears the keyword, so the whole list returns', async () => {
    jest.useFakeTimers();
    const service = spaceService({'': [SPACE_A, SPACE_B, SPACE_C], proj: [SPACE_B]});
    const wrapper = mountPanel(service);
    await settle(wrapper);
    await spacesHeader(wrapper).find('[aria-label="agenda.leftPanel.filterSpaces"]').trigger('click');
    await settle(wrapper);
    wrapper.findComponent(AgendaFilterCalendarSearch).vm.query = 'proj';
    await wrapper.vm.$nextTick();
    jest.advanceTimersByTime(700);
    await settle(wrapper);

    await spacesHeader(wrapper).find('[aria-label="agenda.button.close"]').trigger('click');
    await settle(wrapper);

    const header = spacesHeader(wrapper);
    expect(header.text()).toContain('agenda.leftPanel.spaces');
    expect(wrapper.findComponent(AgendaFilterCalendarSearch).exists()).toBe(false);
    expect(service.calls.length).toBe(3);
    expect(service.calls[2][0]).toBeNull();
    expect(wrapper.findComponent(AgendaFilterCalendarList).vm.query).toBeNull();
  });
});

/**
 * Runs the shipped selection change against a hand-built list state, the
 * way the item reports it: the loaded ids minus the unticked one.
 *
 * @param {Object} state pieces of the list state
 * @param {String} state.query the active keyword, if any
 * @param {Array} state.loaded identity ids of the loaded rows
 * @param {Number} state.limit the page size asked for, 20 unless given
 * @param {Number} state.totalSize the count the last retrieval reported
 * @param {Array} state.membership identity ids of every space of the user
 * @param {Array|boolean} state.value the selection before the change
 * @param {Array} reported what the item handed back
 * @returns {Promise} resolved with the emitted selections and the space
 *          service calls
 */
async function changeSelection(state, reported) {
  const emitted = [];
  const service = spaceService({'': state.membership});
  const vm = {
    value: state.value,
    query: state.query || null,
    spaces: state.loaded.map(space),
    limit: state.limit || 20,
    totalSize: state.totalSize,
    membershipSize: state.membership.length,
    selectAll: true,
    $emit: (event, value) => emitted.push(value),
    $spaceService: service,
  };
  ['changeSelection', 'checkAll', 'uncheckAll', 'narrowAllSelection', 'retrieveMembershipIdentityIds', 'toIdentityIds']
    .forEach(name => vm[name] = AgendaFilterCalendarList.methods[name]);
  ['spaceIdentityIds', 'allSelected', 'partiallyLoaded', 'hasMore']
    .forEach(name => Object.defineProperty(vm, name, {get: () => AgendaFilterCalendarList.computed[name].call(vm)}));
  await vm.changeSelection(reported);
  return {emitted, calls: service.calls};
}

describe('unticking a space out of "all" while the loaded rows are partial', () => {

  /*
   * THE case the panel would have brought to the desktop: out of 'all', the
   * item hands back the two matches minus the unticked one, and that list
   * taken as the selection would have left exactly one space displayed.
   */
  it('excludes the unticked space from the whole membership, not from the matches', async () => {
    const {emitted, calls} = await changeSelection({
      query: 'proj',
      loaded: [SPACE_A, SPACE_B],
      totalSize: 2,
      membership: [SPACE_A, SPACE_B, SPACE_C, SPACE_D, SPACE_E],
      value: [],
    }, [SPACE_B]);

    expect(emitted).toEqual([[SPACE_B, SPACE_C, SPACE_D, SPACE_E]]);
    // one page sized on the membership, without the keyword
    expect(calls).toEqual([[null, 0, 5, 'lastVisited', 'identity']]);
  });

  /*
   * A further page is the same defect without a keyword: the rows not loaded
   * yet were dropped with the unticked one.
   */
  it('keeps the spaces of the pages not loaded yet', async () => {
    const {emitted, calls} = await changeSelection({
      loaded: [SPACE_A, SPACE_B],
      limit: 2,
      totalSize: 5,
      membership: [SPACE_A, SPACE_B, SPACE_C, SPACE_D, SPACE_E],
      value: [],
    }, [SPACE_A]);

    expect(emitted).toEqual([[SPACE_A, SPACE_C, SPACE_D, SPACE_E]]);
    expect(calls).toEqual([[null, 0, 5, 'lastVisited', 'identity']]);
  });

  it('asks nothing more when the loaded rows are all the rows', async () => {
    const {emitted, calls} = await changeSelection({
      loaded: [SPACE_A, SPACE_B, SPACE_C],
      totalSize: 3,
      membership: [SPACE_A, SPACE_B, SPACE_C],
      value: [],
    }, [SPACE_B, SPACE_C]);

    expect(emitted).toEqual([[SPACE_B, SPACE_C]]);
    expect(calls).toEqual([]);
  });

  /*
   * An explicit selection is not 'all': the item removes from the list it was
   * given, and nothing outside it is at stake.
   */
  it('leaves an explicit selection to the item, keyword or not', async () => {
    const {emitted, calls} = await changeSelection({
      query: 'proj',
      loaded: [SPACE_A, SPACE_B],
      totalSize: 2,
      membership: [SPACE_A, SPACE_B, SPACE_C, SPACE_D, SPACE_E],
      value: [SPACE_A, SPACE_D],
    }, [SPACE_D]);

    expect(emitted).toEqual([[SPACE_D]]);
    expect(calls).toEqual([]);
  });
});
