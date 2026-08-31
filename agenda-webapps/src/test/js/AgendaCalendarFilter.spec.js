import Agenda from '../../main/webapp/vue-app/agenda/components/Agenda.vue';

/*
 * The regression this file exists for: EXO-89818, "filtering on a space
 * calendar hides every personal calendar, because the filter is keyed on the
 * owner".
 *
 * The Spaces section of the left panel selects spaces, and its selection used
 * to travel to the events REST untouched as `ownerIds`. Every personal
 * calendar of a user shares ONE owner — the user identity — while each space
 * calendar has its own, so asking for a space asked for that owner and nothing
 * else. The personal calendars were never hidden by a display rule: they were
 * not asked for.
 *
 * The assertions below are therefore made on what the application ASKS FOR,
 * not on what it draws: they call the shipped computed and the shipped
 * retrieval method and read the arguments handed to the events service.
 */

const USER_IDENTITY_ID = 13;
const SPACE_IDENTITY_ID = 14;
const OTHER_SPACE_IDENTITY_ID = 15;

/**
 * Evaluates the shipped effectiveOwnerIds computed against a selection.
 *
 * @param {Array|boolean} ownerIds the selection held by the application
 * @param {String} spaceId the portal space the agenda renders in, if any
 * @returns {Array|boolean} the owner ids the application would request
 */
function effectiveOwnerIds(ownerIds, spaceId) {
  eXo.env.portal.spaceId = spaceId || null;
  return Agenda.computed.effectiveOwnerIds.call({
    ownerIds,
    leftPanelAvailable: Agenda.computed.leftPanelAvailable.call({}),
  });
}

/**
 * Runs the shipped retrieval method with a stubbed events service and returns
 * the arguments it was called with, so the query itself is the assertion
 * subject.
 *
 * @param {Object} state the pieces of application state the method reads
 * @returns {Array} the arguments passed to $eventService.getEvents, or null
 *          when no query was issued at all
 */
function eventsQueryArguments(state) {
  eXo.env.portal.spaceId = null;
  let callArguments = null;
  const vm = Object.assign({
    leftPanelAvailable: true,
    eventsRequestId: 0,
    loading: false,
    initialized: false,
    hasMore: false,
    events: [],
    limit: 0,
    searchTerm: null,
    eventType: 'myEvents',
    filterCanceledEvents: true,
    hiddenPersonalCalendarIds: [],
    period: {start: new Date(), end: new Date()},
    $agendaUtils: {
      toRFC3339: () => '2026-08-31T00:00:00Z',
      toDate: value => value,
    },
    $eventService: {
      getEvents: (...args) => {
        callArguments = args;
        return Promise.resolve({events: []});
      },
    },
  }, state);
  vm.effectiveOwnerIds = Agenda.computed.effectiveOwnerIds.call(vm);
  Agenda.methods.retrieveEventsFromStore.call(vm);
  return callArguments;
}

describe('agenda calendar selection sent to the server', () => {
  beforeEach(() => {
    eXo.env.portal.userIdentityId = USER_IDENTITY_ID;
    eXo.env.portal.spaceId = null;
  });

  it('asks for every calendar when nothing is restricted', () => {
    expect(effectiveOwnerIds([])).toEqual([]);
  });

  /*
   * THE load-bearing assertion. Pre-fix this returned [SPACE_IDENTITY_ID], and
   * the whole of EXO-89818 followed from it: the user's own owner was not in
   * the query, so none of their calendars could come back.
   */
  it('keeps the user own owner when a space is selected', () => {
    expect(effectiveOwnerIds([SPACE_IDENTITY_ID])).toEqual([SPACE_IDENTITY_ID, USER_IDENTITY_ID]);
  });

  it('keeps the user own owner when several spaces are selected', () => {
    expect(effectiveOwnerIds([SPACE_IDENTITY_ID, OTHER_SPACE_IDENTITY_ID]))
      .toEqual([SPACE_IDENTITY_ID, OTHER_SPACE_IDENTITY_ID, USER_IDENTITY_ID]);
  });

  it('does not repeat the user own owner when it is already selected', () => {
    expect(effectiveOwnerIds([SPACE_IDENTITY_ID, USER_IDENTITY_ID])).toEqual([SPACE_IDENTITY_ID, USER_IDENTITY_ID]);
  });

  it('compares owner ids as numbers, whichever way the selection carries them', () => {
    // the selection is built from identity ids that reach the front end as
    // strings; a string/number mismatch would append a duplicate owner
    expect(effectiveOwnerIds(['14', '13'])).toEqual([SPACE_IDENTITY_ID, USER_IDENTITY_ID]);
  });

  /*
   * Unticking every space is a statement about spaces, not about the user's
   * own calendars: it used to empty the whole grid.
   */
  it('still asks for the user own calendars when no space is selected', () => {
    expect(effectiveOwnerIds(false)).toEqual([USER_IDENTITY_ID]);
  });

  /*
   * A space agenda has no left panel: the selection IS the space, and adding
   * the viewer's own calendars would put personal events on a space's page.
   */
  it('leaves a space agenda selection untouched', () => {
    expect(effectiveOwnerIds([SPACE_IDENTITY_ID], '42')).toEqual([SPACE_IDENTITY_ID]);
  });
});

describe('agenda events query', () => {
  beforeEach(() => {
    eXo.env.portal.userIdentityId = USER_IDENTITY_ID;
    eXo.env.portal.spaceId = null;
  });

  it('sends the space beside the user own owner', () => {
    const args = eventsQueryArguments({ownerIds: [SPACE_IDENTITY_ID]});
    expect(args).not.toBeNull();
    expect(args[1]).toEqual([SPACE_IDENTITY_ID, USER_IDENTITY_ID]);
  });

  /*
   * The second half of the same root cause: three personal calendars are
   * indistinguishable to an owner-keyed filter, so picking one of them is
   * expressed by excluding the other two — by calendar id.
   */
  it('sends the hidden personal calendars as an exclusion list', () => {
    const args = eventsQueryArguments({
      ownerIds: [SPACE_IDENTITY_ID],
      hiddenPersonalCalendarIds: [1, 187],
    });
    expect(args[8]).toEqual([1, 187]);
  });

  it('sends no exclusion list when every personal calendar is displayed', () => {
    const args = eventsQueryArguments({ownerIds: [SPACE_IDENTITY_ID]});
    expect(args[8]).toEqual([]);
  });

  it('queries the user own calendars even when no space is selected', () => {
    const args = eventsQueryArguments({ownerIds: false});
    expect(args).not.toBeNull();
    expect(args[1]).toEqual([USER_IDENTITY_ID]);
  });
});

describe('agenda personal calendar visibility', () => {
  beforeEach(() => {
    eXo.env.portal.userIdentityId = USER_IDENTITY_ID;
  });

  /*
   * Hiding a calendar has to reach the server too: its events used to be
   * counted against the requested `limit` and only dropped afterwards, so
   * hiding a calendar quietly returned a short page.
   */
  it('re-queries when the personal calendar visibility changes', () => {
    let retrieved = 0;
    const vm = {
      initialized: true,
      updateDisplayedEvents: () => {},
      retrieveEvents: () => retrieved++,
    };
    Agenda.watch.hiddenPersonalCalendarIds.call(vm);
    expect(retrieved).toBe(1);
  });

  it('does not query while restoring the persisted visibility at startup', () => {
    // created() restores it before the first query is issued, and that query
    // already carries it
    let retrieved = 0;
    const vm = {
      initialized: false,
      updateDisplayedEvents: () => {},
      retrieveEvents: () => retrieved++,
    };
    Agenda.watch.hiddenPersonalCalendarIds.call(vm);
    expect(retrieved).toBe(0);
  });
});
