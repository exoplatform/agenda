import AgendaFilterCalendarList from '../../main/webapp/vue-app/agenda-common/components/filter/AgendaFilterCalendarList.vue';

/*
 * EXO-89908, "the Spaces filter is alphabetical, so a user in dozens of spaces
 * pages through an alphabet before meeting the spaces they work in".
 *
 * Two things had to hold, and only the pair of them is the fix:
 *
 *  - the SPACES are asked for in the platform's recent order. The ordering is
 *    decided server-side, before the page is cut, so this is a property of the
 *    request and not of what comes back — hence the assertion on the arguments
 *    handed to the space service, in the style of AgendaCalendarFilter.spec.js.
 *
 *  - the CALENDARS are put back in that order. The calendars REST answers a
 *    set of owners, not a sequence: saved calendars come ordered by their own
 *    id and unsaved ones are appended. Asking for recent spaces and rendering
 *    that answer as it comes would throw the recency away one call later, and
 *    nothing on screen would have changed.
 */

const RECENT_SPACE_IDENTITY_ID = 40;
const OLDER_SPACE_IDENTITY_ID = 10;
const OLDEST_SPACE_IDENTITY_ID = 25;

/**
 * Runs the shipped retrieval method against stubbed services.
 *
 * @param {Array} spaces spaces the space service answers, in the order the
 *          server would return them
 * @param {Array} calendars calendars the calendar service answers, in the
 *          order the server would return them
 * @returns {Promise} resolved with the space service arguments and the
 *          calendars the component ended up holding
 */
function retrieve(spaces, calendars) {
  let spaceQueryArguments = null;
  const vm = {
    query: null,
    limit: 20,
    spaces: [],
    calendars: [],
    totalSize: 0,
    loading: false,
    initialized: false,
    sortByOwnerOrder: AgendaFilterCalendarList.methods.sortByOwnerOrder,
    toIdentityIds: AgendaFilterCalendarList.methods.toIdentityIds,
    $spaceService: {
      getSpaces: (...args) => {
        spaceQueryArguments = args;
        return Promise.resolve({spaces, size: spaces.length});
      },
    },
    $calendarService: {
      getCalendars: () => Promise.resolve({calendars}),
    },
  };
  // the shipped computed, so the test reads the owner ids the way the
  // component does rather than restating the mapping
  Object.defineProperty(vm, 'spaceIdentityIds', {
    get: () => AgendaFilterCalendarList.computed.spaceIdentityIds.call(vm),
  });
  return AgendaFilterCalendarList.methods.retrieveCalendars.call(vm)
    .then(() => ({spaceQueryArguments, calendars: vm.calendars}));
}

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
 * @param {Number} id calendar id, 0 for a space that has no saved calendar
 * @param {Number} ownerId identity id of the owning space
 * @returns {Object} calendar
 */
function calendar(id, ownerId) {
  return {id, owner: {id: `${ownerId}`}};
}

describe('agenda space filter ordering', () => {
  /*
   * THE load-bearing assertion. This argument was 'member' before the fix,
   * which is what made the list alphabetical: with no sort given, the spaces
   * REST falls back to title ascending.
   *
   * It must also not become 'member' with a lastVisited sort: that variant is
   * cached under the member key, which the visit-time eviction does not
   * invalidate, so it would answer a stale order for as long as the cache
   * holds it.
   */
  it('asks for the spaces in the platform recent order', () => {
    return retrieve([space(RECENT_SPACE_IDENTITY_ID)], [calendar(1, RECENT_SPACE_IDENTITY_ID)])
      .then(({spaceQueryArguments}) => {
        expect(spaceQueryArguments[3]).toBe('lastVisited');
      });
  });

  it('still asks for one page at a time, from the start', () => {
    return retrieve([space(RECENT_SPACE_IDENTITY_ID)], [calendar(1, RECENT_SPACE_IDENTITY_ID)])
      .then(({spaceQueryArguments}) => {
        expect(spaceQueryArguments[1]).toBe(0);
        expect(spaceQueryArguments[2]).toBe(20);
      });
  });

  /*
   * The second half of the fix. The calendars below come back id-ordered, the
   * order the server actually answers in — the reverse of the recency the
   * spaces call established.
   */
  it('renders the calendars in the order their spaces were asked in', () => {
    const spaces = [
      space(RECENT_SPACE_IDENTITY_ID),
      space(OLDER_SPACE_IDENTITY_ID),
      space(OLDEST_SPACE_IDENTITY_ID),
    ];
    const calendars = [
      calendar(3, OLDER_SPACE_IDENTITY_ID),
      calendar(7, OLDEST_SPACE_IDENTITY_ID),
      calendar(9, RECENT_SPACE_IDENTITY_ID),
    ];
    return retrieve(spaces, calendars).then(result => {
      expect(result.calendars.map(cal => Number(cal.owner.id)))
        .toEqual([RECENT_SPACE_IDENTITY_ID, OLDER_SPACE_IDENTITY_ID, OLDEST_SPACE_IDENTITY_ID]);
    });
  });

  /*
   * A space with no calendar yet is served as an unsaved one with id 0,
   * appended after the saved ones. It is a row like any other and must sit at
   * its space's rank, not at the end.
   */
  it('places a space that has no saved calendar at its own rank', () => {
    const spaces = [
      space(RECENT_SPACE_IDENTITY_ID),
      space(OLDER_SPACE_IDENTITY_ID),
    ];
    const calendars = [
      calendar(5, OLDER_SPACE_IDENTITY_ID),
      calendar(0, RECENT_SPACE_IDENTITY_ID),
    ];
    return retrieve(spaces, calendars).then(result => {
      expect(result.calendars.map(cal => Number(cal.owner.id)))
        .toEqual([RECENT_SPACE_IDENTITY_ID, OLDER_SPACE_IDENTITY_ID]);
    });
  });

  it('keeps a calendar whose owner is not in the page, at the end', () => {
    const spaces = [space(RECENT_SPACE_IDENTITY_ID)];
    const calendars = [
      calendar(4, 999),
      calendar(6, RECENT_SPACE_IDENTITY_ID),
    ];
    return retrieve(spaces, calendars).then(result => {
      expect(result.calendars.map(cal => Number(cal.owner.id))).toEqual([RECENT_SPACE_IDENTITY_ID, 999]);
    });
  });

  it('asks for no calendar at all when the user is in no space', () => {
    return retrieve([], []).then(result => {
      expect(result.calendars).toEqual([]);
    });
  });
});
