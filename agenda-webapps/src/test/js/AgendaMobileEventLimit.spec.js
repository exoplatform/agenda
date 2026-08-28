import Agenda from '../../main/webapp/vue-app/agenda/components/Agenda.vue';

/*
 * The regression this file exists for: EXO-89791, "the agenda shows only 10
 * events until the page is refreshed".
 *
 * Nothing here renders a component at a chosen width, because that is exactly
 * what the shipped bug survived. The bug lived in an INITIAL VALUE and an
 * ORDERING, not in a layout decision made at a known width:
 *
 *   1. the root data starts with bodyElementWidth = 0, because the width only
 *      arrives from the ResizeObserver installed in mounted();
 *   2. isMobile read that 0 as "narrower than sm", so the application started
 *      in mobile mode on EVERY viewport;
 *   3. the mobile timeline asks for one page from its created() hook, which
 *      put limit=10 on the events query;
 *   4. the observer then reported the real width, isMobile flipped to false,
 *      the timeline was destroyed and the desktop grid took over — but limit
 *      stayed at 10 and nothing re-issued the query.
 *
 * So the two assertions that matter are the two below: the unmeasured width
 * must not read as mobile, and leaving the mobile layout must give the page
 * size back. Both are written to fail against the pre-fix code.
 */

const MAIN_JS = '../../main/webapp/vue-app/agenda/main.js';

const THRESHOLDS = {xs: 0, sm: 600, md: 960, lg: 1264, xl: 1904};

const DESKTOP_BREAKPOINT = {name: 'lg', width: 1280, thresholds: THRESHOLDS};
const PHONE_BREAKPOINT = {name: 'xs', width: 360, thresholds: THRESHOLDS};

/**
 * Loads agenda/main.js with the portal globals it reads at import time, runs
 * its init(), and returns the root component options it handed to
 * Vue.createApp.
 *
 * The options object returned is the very one the running application uses,
 * so the assertions are made against the shipped computed rather than a copy
 * of it — a copy would keep passing if main.js were changed back.
 *
 * @returns {Promise<Object>} the captured root component options
 */
async function loadRootOptions() {
  let captured = null;

  global.extensionRegistry = {
    loadComponents: () => [],
  };
  global.Vuetify = function Vuetify() {};
  global.Vue = {
    component: () => {},
    use: () => {},
    createApp: options => captured = options,
    prototype: {
      $utils: {
        includeExtensions: () => {},
      },
    },
  };
  global.exoi18n = {
    loadLanguageAsync: () => Promise.resolve({}),
  };
  global.eXo.env.portal.language = 'en';
  global.eXo.env.portal.vuetifyPreset = {};
  global.eXo.env.portal.spaceId = null;

  jest.resetModules();
  require(MAIN_JS).init();
  // let the i18n promise chain settle so createApp has been called
  await new Promise(resolve => setTimeout(resolve, 0));
  return captured;
}

/**
 * Evaluates the shipped isMobile computed against a given container width and
 * viewport breakpoint.
 *
 * @param {Object} rootOptions the root component options captured from main.js
 * @param {Number} bodyElementWidth the container width the observer reported,
 *          0 meaning it has not reported anything yet
 * @param {Object} breakpoint the Vuetify breakpoint object
 * @returns {Boolean} what the application would decide
 */
function isMobile(rootOptions, bodyElementWidth, breakpoint) {
  return rootOptions.computed.isMobile.call({
    bodyElementWidth,
    $vuetify: {breakpoint},
  });
}

describe('agenda root layout decision', () => {
  let rootOptions;

  beforeAll(async () => {
    rootOptions = await loadRootOptions();
  });

  it('starts with a container width that has not been measured yet', () => {
    // the premise of the whole regression: mounted() is what fills this in
    expect(rootOptions.data().bodyElementWidth).toBe(0);
  });

  /*
   * THE load-bearing assertion. Pre-fix this returned true, because 0 < 600,
   * and everything else in EXO-89791 followed from it.
   */
  it('is not mobile on a desktop viewport while the container is unmeasured', () => {
    expect(isMobile(rootOptions, 0, DESKTOP_BREAKPOINT)).toBe(false);
  });

  it('is still mobile on a phone viewport while the container is unmeasured', () => {
    expect(isMobile(rootOptions, 0, PHONE_BREAKPOINT)).toBe(true);
  });

  it('is mobile once the container is measured narrower than sm', () => {
    // a desktop viewport, but the portlet was dropped into a narrow column
    expect(isMobile(rootOptions, 400, DESKTOP_BREAKPOINT)).toBe(true);
  });

  it('is not mobile once the container is measured wider than sm', () => {
    expect(isMobile(rootOptions, 1200, DESKTOP_BREAKPOINT)).toBe(false);
  });
});

describe('agenda event page size across a layout change', () => {
  it('watches the root mobile flag at all', () => {
    // without this watcher the reset below cannot happen, whatever its body
    expect(typeof Agenda.watch['$root.isMobile']).toBe('function');
  });

  /*
   * The decoupling. Only the mobile timeline paginates; the desktop grid must
   * query the whole period. A limit inherited from a mobile phase capped the
   * grid at one page until the page was reloaded.
   */
  it('drops the mobile page size when leaving the mobile layout', () => {
    const vm = {limit: 10};
    Agenda.watch['$root.isMobile'].call(vm, false, true);
    expect(vm.limit).toBe(0);
  });

  it('leaves the page size alone when entering the mobile layout', () => {
    // the timeline's created() hook is what sets it, and it must not be undone
    const vm = {limit: 10};
    Agenda.watch['$root.isMobile'].call(vm, true, false);
    expect(vm.limit).toBe(10);
  });
});
