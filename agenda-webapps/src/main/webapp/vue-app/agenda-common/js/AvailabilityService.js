import {USER_TIMEZONE_ID} from './AgendaUtils.js';

/**
 * Reads the busy time of the people a meeting is being scheduled with.
 *
 * <p>
 * The asking user is never a parameter here: the server takes it from the
 * authenticated session. Anything this module could send would be a claim, and
 * the endpoint would stop being an authorisation check.
 */

/**
 * Where a Spring MVC controller of THIS add-on is mounted, built from the two
 * things that decide it and nothing else:
 *
 * <ul>
 *   <li>`/agenda` — the agenda WAR's own servlet context. Tomcat takes it from
 *       the war file name, which the addon assembly pins
 *       (`agenda-packaging/src/main/assemblies/assembly.xml`,
 *       `<outputFileNameMapping>agenda.war`). It is the same on every
 *       deployment;</li>
 *   <li>`/rest` — `spring.mvc.servlet.path`, set once in the core's shared
 *       `application-common.properties` and inherited by every add-on.</li>
 * </ul>
 *
 * <p>
 * <strong>Neither `eXo.env.portal.context` nor `eXo.env.portal.rest` belongs
 * here, and using them is what broke this.</strong> Both describe the PORTAL
 * webapp — a different WAR. `eXo.env.portal.context` is that webapp's context
 * (`/portal`), so prefixing it asks the portal for a path it does not serve
 * and the request is answered with a 302 to the login page;
 * `eXo.env.portal.rest` is the legacy JAX-RS mount inside it, which is where
 * the sibling `EventService` and `SettingsService` resources genuinely live
 * (`/portal/rest/v1/agenda/...`) and where a Spring MVC controller never does.
 * Copying either sibling was the mistake.
 *
 * <p>
 * The path is root-relative, so it follows the origin the page was served
 * from, and it is unaffected by the portal being deployed under a
 * non-default container name — that name only ever moves `/portal`, never this
 * add-on's own context. Same construction as the agenda add-on's other Spring
 * MVC call (`agenda-base-extension/main.js` → `/agenda/rest/timezone`) and as
 * the CalDAV add-on's (`/caldav/rest/...`).
 */
const AGENDA_REST_BASE = '/agenda/rest';

/**
 * Asks the server for each named participant's busy time over a window.
 *
 * <p>
 * The answer is one record per participant, `{identityId, disclosure, busy}`,
 * where `disclosure` is `disclosed`, `not_disclosed` or `failed` and only the
 * first carries `busy`. This function does not flatten that: a caller has to
 * go through `splitBusyTimeResults` to get anything drawable, which is what
 * keeps "did not share" from quietly becoming "has nothing on".
 *
 * <p>
 * A rejected promise means the whole read failed, so nothing is known about
 * anybody — not that nobody is busy. The caller turns that into a failure for
 * every participant it asked about.
 *
 * @param {Array} identityIds technical identifiers of the participants
 * @param {String} start window start, ISO-8601
 * @param {String} end window end, ISO-8601
 * @returns {Promise} resolves with the array of per-participant records
 */
export function getBusyTime(identityIds, start, end) {
  const ids = (identityIds || []).join(',');
  // Same `timeZoneId` the events resource takes: the ranges come back in the
  // reader's own zone, so a busy block lands on the grid at the same
  // wall-clock hour as every other event drawn beside it.
  return fetch(`${AGENDA_REST_BASE}/availability?identityIds=${encodeURIComponent(ids)}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}&timeZoneId=${encodeURIComponent(USER_TIMEZONE_ID)}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error');
    }
    return resp.json();
  });
}
