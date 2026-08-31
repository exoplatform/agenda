import * as availabilityService from '../../main/webapp/vue-app/agenda-common/js/AvailabilityService.js';

/*
 * EXO-89850: the request the browser actually makes.
 *
 * The endpoint was built as `${eXo.env.portal.context}/agenda/rest/...`, which
 * the browser resolved to /portal/agenda/rest/availability — a path the PORTAL
 * webapp does not serve, answered with a 302 to the login page. The feature
 * then reported, correctly, that the participants' busy time "could not be
 * read". It never rendered.
 *
 * NOTHING IN THE SUITE COULD SEE IT. Every component spec stubs
 * $availabilityService, so the URL this module builds was exercised by no test
 * at all — the fifth time in this delivery that a green check turned out to be
 * asking a question the real caller never asks. These pins call the SHIPPED
 * module against a stubbed fetch and assert the URL itself, so a wrong prefix
 * fails here instead of on a screen.
 *
 * The correct base is derived, not copied:
 *   /agenda  the agenda WAR's own servlet context, pinned by the addon
 *            assembly's <outputFileNameMapping>agenda.war
 *   /rest    spring.mvc.servlet.path, from the core's shared
 *            application-common.properties
 * Neither part comes from eXo.env.portal.*, which describes a different WAR.
 */

const WINDOW_START = '2026-07-20T00:00:00+02:00';

const WINDOW_END = '2026-07-27T00:00:00+02:00';

/**
 * Captures the request the module makes and answers it.
 *
 * @param {Object} response what fetch resolves with
 * @returns {Object} `{calls}`, the recorded requests
 */
function captureFetch(response) {
  const calls = [];
  global.fetch = (url, options) => {
    calls.push({url, options});
    return Promise.resolve(response || {ok: true, json: () => Promise.resolve([])});
  };
  return {calls};
}

/**
 * The path of the request, without its query string.
 *
 * @param {String} url the requested url
 * @returns {String} the path
 */
function pathOf(url) {
  return String(url).split('?')[0];
}

/**
 * The query parameters of the request.
 *
 * @param {String} url the requested url
 * @returns {Object} the decoded parameters
 */
function queryOf(url) {
  const query = {};
  String(url).split('?').slice(1).join('?').split('&').filter(pair => !!pair)
    .forEach(pair => {
      const separator = pair.indexOf('=');
      query[pair.substring(0, separator)] = decodeURIComponent(pair.substring(separator + 1));
    });
  return query;
}

describe('The availability endpoint is asked at the path the server serves', () => {
  /*
   * The base is a module-level constant holding a literal, so it is fixed at
   * import time and cannot be steered from a test body. That is itself part of
   * what is being pinned: a base interpolating `eXo.env.*` would be frozen at
   * module-load time too, which is a second way to get it wrong on a portal
   * whose globals are not yet populated. The assertions therefore state the
   * path outright rather than deriving an expectation from the environment.
   */

  it('asks the agenda webapp directly, not through the portal context', () => {
    const captured = captureFetch();

    return availabilityService.getBusyTime(['400', '700'], WINDOW_START, WINDOW_END).then(() => {
      expect(captured.calls).toHaveLength(1);
      expect(pathOf(captured.calls[0].url)).toBe('/agenda/rest/availability');
    });
  });

  it('does not carry the portal context or the legacy JAX-RS mount', () => {
    const captured = captureFetch();

    return availabilityService.getBusyTime(['400'], WINDOW_START, WINDOW_END).then(() => {
      const url = String(captured.calls[0].url);
      // The two spellings the sibling services use, both wrong for a Spring
      // MVC controller living in this add-on's own WAR. Asserted as "starts at
      // the add-on's own context", which no portal-prefixed variant satisfies
      // whatever container name that portal happens to carry.
      expect(url.startsWith('/agenda/')).toBe(true);
      expect(url).not.toContain('/rest/v1/');
      expect(url).not.toContain(String(eXo.env.portal.context));
    });
  });

  it('carries every parameter the endpoint requires, and no identity of its own', () => {
    const captured = captureFetch();

    return availabilityService.getBusyTime(['400', '700'], WINDOW_START, WINDOW_END).then(() => {
      const query = queryOf(captured.calls[0].url);
      expect(query.identityIds).toBe('400,700');
      expect(query.start).toBe(WINDOW_START);
      expect(query.end).toBe(WINDOW_END);
      expect(query.timeZoneId).toBeTruthy();
      // The asking user is the session's, never a parameter: the 400 the
      // server answers a bare request with is what proved the path was real.
      expect(Object.keys(query)).toEqual(['identityIds', 'start', 'end', 'timeZoneId']);
    });
  });

  it('sends the session cookie, or the endpoint cannot know who is asking', () => {
    const captured = captureFetch();

    return availabilityService.getBusyTime(['400'], WINDOW_START, WINDOW_END).then(() => {
      expect(captured.calls[0].options.credentials).toBe('include');
    });
  });

  it('treats a redirect to the login page as a failure, never as an empty answer', () => {
    // What the wrong path actually produced: a 302, which fetch follows and
    // reports as ok:false on the login page. An empty array here would have
    // drawn every participant free.
    const captured = captureFetch({ok: false, status: 302, json: () => Promise.resolve([])});

    return availabilityService.getBusyTime(['400'], WINDOW_START, WINDOW_END)
      .then(() => {
        throw new Error('a redirect must not resolve');
      })
      .catch(error => {
        expect(error.message).toContain('server error');
        expect(captured.calls).toHaveLength(1);
      });
  });
});
