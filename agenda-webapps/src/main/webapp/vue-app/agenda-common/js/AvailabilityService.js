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
  return fetch(`${eXo.env.portal.context}/agenda/rest/availability?identityIds=${encodeURIComponent(ids)}&start=${encodeURIComponent(start)}&end=${encodeURIComponent(end)}&timeZoneId=${encodeURIComponent(USER_TIMEZONE_ID)}`, {
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
